package klip

import aws.sdk.kotlin.runtime.auth.credentials.DefaultChainCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import klip.Routes.setup
import klip.S3.checkCache
import klip.S3.generateCacheKey
import klip.S3.writeToCache
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.kotlin.logger
import org.apache.logging.log4j.Level
import java.io.File
import java.util.UUID

private val logger = logger("Klip")

data class Env(
    val logLevel: String = System.getenv("KLIP_LOG_LEVEL") ?: "info",
    val http: Http = Http(),
    val aws: Aws = Aws(),
    val cache: Cache = Cache()
) {
    data class Http(val port: Int = System.getenv("KLIP_HTTP_PORT")?.toInt() ?: 8080)

    data class Aws(
        val region: String = System.getenv("KLIP_AWS_REGION"),
        val s3Bucket: String = System.getenv("KLIP_S3_BUCKET"),
    )

    data class Cache(
        val enabled: Boolean = System.getenv("KLIP_CACHE_ENABLED")?.toBoolean() ?: true,
        // optional, defaults to source bucket
        val cacheBucket: String = System.getenv("KLIP_CACHE_BUCKET").ifBlank { System.getenv("KLIP_S3_BUCKET") },
        // prefix for cached files
        val cachePrefix: String = System.getenv("KLIP_CACHE_FOLDER").ifBlank { "_cache/" }
    )
}

@Serializable
data class KlipHealth(val status: String = "UP")

data class KlipImage(
    val data: ByteArray,
    val contentType: ContentType
) {
    val extension = contentType.contentSubtype
}

fun main() {
    logger.info { "Starting Klip" }

    val env = Env()
    Configurator.setRootLevel(Level.getLevel(env.logLevel.uppercase()))

    logger.info(env)

    embeddedServer(CIO, port = env.http.port) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                encodeDefaults = true
            })
        }
        setup(env)
    }.start(wait = true)
}

object Routes {

    fun Application.setup(env: Env) {
        val s3Client = S3Client {
            region = env.aws.region
            credentialsProvider = DefaultChainCredentialsProvider()
            if (System.getenv("AWS_PROFILE") == "default") { // for running local
                credentialsProvider = ProfileCredentialsProvider(profileName = "default")
            }
        }

        routing {
            get("/img/{size}/{path...}") {
                call.handleImageRequest(s3Client, env)
            }

            get("/img/{path...}") {
                call.handleRawImageRequest(s3Client, env)
            }

            get("/health") {
                call.respond(KlipHealth())
            }

            get("/version") {
                call.respondText("1.0.0", ContentType.Text.Plain)
            }
        }

        monitor.subscribe(ApplicationStopped) {
            s3Client.close()
        }
    }

    private suspend fun ApplicationCall.handleImageRequest(s3Client: S3Client, env: Env) {
        val transforms = KlipTransforms.from(parameters)

        val cacheKey =
            generateCacheKey(transforms)
        logger.info("Cache Key: $cacheKey")

        // check cache
        if (env.cache.enabled) {
            val cachedImage = checkCache(s3Client, env, cacheKey)
            if (cachedImage != null) {
                logger.info("Cache hit: $cacheKey")
                response.headers.append("Content-Type", cachedImage.contentType.toString())
                respondBytes(cachedImage.data)
                return
            }
        }

        val s3Image = S3.readFile(s3Client, env.aws.s3Bucket, transforms.path)
        if (s3Image == null) {
            respond(HttpStatusCode.NotFound, "File not found: $transforms.path")
            return
        }
        try {
            val processedImage = GraphicsMagickImageProcessor.processImage(s3Image, transforms)

            if (env.cache.enabled) {
                writeToCache(s3Client, env, cacheKey, processedImage)
            }

            response.headers.append("Content-Type", s3Image.contentType.toString())
            respondBytes(processedImage)
        } catch (e: Exception) {
            logger.error("Error processing image: ${e.message}", e)
            respond(HttpStatusCode.InternalServerError, "Error processing image")
        }
    }

    private suspend fun ApplicationCall.handleRawImageRequest(s3Client: S3Client, env: Env) {
        val path = parameters.getAll("path")?.joinToString("/") ?: ""
        val s3Object = S3.readFile(s3Client, env.aws.s3Bucket, path)
        if (s3Object != null) {
            response.headers.append("Content-Type", s3Object.contentType.toString())
            respondBytes(s3Object.data)
        } else {
            respond(HttpStatusCode.NotFound, "File not found: $path")
        }
    }

}

object S3 {
    suspend fun readFile(s3Client: S3Client, bucket: String, key: String): KlipImage? {
        return try {
            val request = GetObjectRequest {
                this.bucket = bucket
                this.key = key
            }
            val extension = getFileExtension(key)
            s3Client.getObject(request) {
                val bytes = (it.body as ByteStream).toByteArray()
                val contentType = getContentTypeByExtension(extension)
                KlipImage(bytes, contentType)
            }
        } catch (e: Exception) {
            logger.error("Error fetching file from S3: ${e.message}", e)
            null
        }
    }

    suspend fun checkCache(
        s3Client: S3Client,
        env: Env,
        cacheKey: String
    ): KlipImage? {
        val cacheBucket = env.cache.cacheBucket ?: env.aws.s3Bucket
        val cachePath = "${env.cache.cachePrefix}$cacheKey"

        return try {
            val request = GetObjectRequest {
                this.bucket = cacheBucket
                this.key = cachePath
            }
            val extension = getFileExtension(cacheKey) // <-- implement
            s3Client.getObject(request) {
                val bytes = (it.body as ByteStream).toByteArray()
                val contentType = getContentTypeByExtension(extension)
                KlipImage(bytes, contentType)
            }
        } catch (e: Exception) {
            logger.debug { "cache miss: $cacheKey" }
            null // cache miss
        }
    }

    suspend fun writeToCache(
        s3Client: S3Client,
        env: Env,
        cacheKey: String,
        data: ByteArray
    ) {
        val cacheBucket = env.cache.cacheBucket ?: env.aws.s3Bucket
        val cachePath = "${env.cache.cachePrefix}$cacheKey"

        val extension = getFileExtension(cacheKey) // <-- implement
        val request = aws.sdk.kotlin.services.s3.model.PutObjectRequest {
            bucket = cacheBucket
            key = cachePath
            body = ByteStream.fromBytes(data)
            contentType = "image/$extension"
        }
        s3Client.putObject(request)
    }

    fun generateCacheKey(t: KlipTransforms): String {
        val baseName = t.path.substringBeforeLast('.') // remove the extension
        val extension = getFileExtension(t.path)

        // only include active transforms in key
        val params = mutableListOf("${t.width}x${t.height}")
        if (t.grayscale) params.add("g1")
        if (t.crop) params.add("c1")
        if (t.rotate != null && t.rotate != 0f) params.add("r${t.rotate.toInt()}")
        if (t.flipH) params.add("h1")
        if (t.flipV) params.add("v1")
        if (t.dither) params.add("d1")
        if (t.quality != null) params.add("q${t.quality}")
        if (t.sharpen != null) params.add("sharpen${t.quality}")
        if (t.colors != null) params.add("colors${t.quality}")
        if (t.blurRadius != null && t.blurSigma != null) params.add("blur${t.blurRadius}x${t.blurSigma}")

        return "${baseName}-${params.joinToString("")}.$extension"
    }
}

data class KlipTransforms(
    val path: String,
    val width: Int,
    val height: Int,
    val grayscale: Boolean = false,
    val crop: Boolean = false,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val dither: Boolean = false,
    val rotate: Float? = null,
    val quality: Int? = null,
    val sharpen: Float? = null,
    val colors: Int? = null,
    val blurRadius: Double? = null,
    val blurSigma: Double? = null
) {

    fun verify() {
        require(width > 0 && height > 0) { "Invalid width or height: ${width}x${height}" }
        require(quality == null || (quality in 1..100)) { "quality must be between 1 and 100." }
        require(sharpen == null || sharpen >= 0) { "sharpen must be >= 0." }
        require(colors == null || colors in 2..256) { "colors must be between 2 and 256." }
        require(blurRadius == null || blurRadius >= 0) { "blur radius must be >= 0." }
        require(blurSigma == null || blurSigma >= 0) { "blur sigma must be >= 0." }
    }

    companion object {
        fun from(parameters: Parameters): KlipTransforms {
            // extract size
            val size = parameters["size"] ?: ""
            val match = Regex("(\\d+)x(\\d+)").matchEntire(size)
            val width = match?.groups?.get(1)?.value?.toIntOrNull()
            val height = match?.groups?.get(2)?.value?.toIntOrNull()

            val path = parameters.getAll("path")?.joinToString("/") ?: ""

            // parse boolean flags
            val grayscale = isParamTrue(parameters["grayscale"])
            val crop = isParamTrue(parameters["crop"])
            val flipH = isParamTrue(parameters["flipH"])
            val flipV = isParamTrue(parameters["flipV"])
            val dither = isParamTrue(parameters["dither"])

            // parse numeric parameters
            val rotate = parameters["rotate"]?.toFloatOrNull()
            val quality = parameters["quality"]?.toIntOrNull()
            val sharpen = parameters["sharpen"]?.toFloatOrNull()
            val colors = parameters["colors"]?.toIntOrNull()

            // handle blur (supports "radius" or "{radius}x{sigma}")
            val blur = parameters["blur"]
            val (blurRadius, blurSigma) = parseBlur(blur)

            requireNotNull(width) { "Missing width in size parameter." }
            requireNotNull(height) { "Missing height in size parameter." }

            return KlipTransforms(
                path = path,
                width = width,
                height = height,
                grayscale = grayscale,
                crop = crop,
                flipH = flipH,
                flipV = flipV,
                dither = dither,
                rotate = rotate,
                quality = quality,
                sharpen = sharpen,
                colors = colors,
                blurRadius = blurRadius,
                blurSigma = blurSigma
            ).apply { verify() } // Validate on creation
        }

        /**
         * Parse blur input (supports single and compound formats).
         */
        private fun parseBlur(blur: String?): Pair<Double?, Double?> {
            if (blur.isNullOrEmpty()) return null to null
            val parts = blur.split("x")

            return when (parts.size) {
                2 -> parts[0].toDoubleOrNull() to parts[1].toDoubleOrNull()
                1 -> {
                    val radius = parts[0].toDoubleOrNull()
                    radius?.let { Pair(it, it * 0.5) } ?: (null to null) // Default to nulls if parsing fails
                }
                else -> null to null
            }
        }

        /**
         * Check if parameter is a valid boolean flag.
         */
        private fun isParamTrue(value: String?): Boolean {
            return value != null && (value == "1" || value == "true" || value.isEmpty())
        }
    }
}

object GraphicsMagickImageProcessor {

    fun processImage(
        image: KlipImage,
        t: KlipTransforms
    ): ByteArray {
        val tempDir = File("/tmp")
        val inputFile = File(tempDir, "gm_input_${UUID.randomUUID()}.${image.extension}")
        val outputFile = File(tempDir, "gm_output_${UUID.randomUUID()}.${image.extension}")
        logger.debug { "Input file: ${inputFile.absolutePath}, Output file: ${outputFile.absolutePath}" }

        inputFile.writeBytes(image.data)

        try {
            val command = mutableListOf("gm", "convert")
            command.add(inputFile.absolutePath)

            // crop before resize
            if (t.crop) {
                command.add("-gravity")
                command.add("center")
                command.add("-crop")
                command.add("${t.width}x${t.height}+0+0")
            }

            command.add("-resize")
            command.add("${t.width}x${t.height}")

            if (t.grayscale) {
                command.add("-colorspace")
                command.add("Gray")
            }

            if (t.flipH) command.add("-flop")
            if (t.flipV) command.add("-flip")

            if (t.rotate != null) {
                command.add("-rotate")
                command.add(t.rotate.toString())
            }

            if (t.blurRadius != null && t.blurSigma != null) {
                command.add("-blur")
                command.add("${t.blurRadius}x${t.blurSigma}") // Format: 0x<sigma>
            }

            if (t.sharpen != null) {
                command.add("-sharpen")
                command.add("0x${t.sharpen}") // Format: 0x<radius>
            }

            if (t.colors != null) {
                command.add("-colors")
                command.add(t.colors.toString())
            }

            if (t.dither) {
                command.add("-dither")
            }

            if (t.quality != null) {
                command.add("-quality")
                command.add(t.quality.toString())
            }

            command.add(outputFile.absolutePath)

            logger.debug { "GraphicsMagick cmd: $command" }

            val process = ProcessBuilder(command).redirectErrorStream(true).start()

            // capture errors for debugging
            val errorOutput = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { errorOutput.appendLine(it) }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.error("GraphicsMagick failed: $errorOutput")
                throw RuntimeException("GraphicsMagick failed: $errorOutput")
            }

            return outputFile.readBytes()

        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }
}

fun getFileExtension(filename: String, default: String = ""): String {
    val name = filename.substringAfterLast('/') // get last segment after path separator
    val lastDotIndex = name.lastIndexOf('.')

    // no dot or the dot is the first character (hidden file without extension)
    if (lastDotIndex <= 0) return default

    return name.substring(lastDotIndex + 1).lowercase()
}

fun getContentTypeByExtension(extension: String): ContentType {
    return when (extension.lowercase()) {
        "bmp" -> ContentType("image", "bmp")
        "gif" -> ContentType.Image.GIF
        "jpeg", "jpg" -> ContentType.Image.JPEG
        "png" -> ContentType.Image.PNG
        "webp" -> ContentType("image", "webp")
        "svg" -> ContentType.Image.SVG
        "ico" -> ContentType("image", "x-icon")
        else -> ContentType.Application.OctetStream
    }
}
