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
import klip.image.toByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.kotlin.logger
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val logger = logger("Klip")

data class Env(
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
)

fun main() {
    logger.info { "Starting Klip" }

    val env = Env()
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
        // extract size and path
        val size = parameters["size"] ?: ""
        val match = Regex("(\\d+)x(\\d+)").matchEntire(size)
        val width = match?.groups?.get(1)?.value?.toIntOrNull()
        val height = match?.groups?.get(2)?.value?.toIntOrNull()

        val path = parameters.getAll("path")?.joinToString("/") ?: ""

        // handle boolean values
        val grayscale = isParamTrue(request.queryParameters["grayscale"])
        val crop = isParamTrue(request.queryParameters["crop"])
        val flipH = isParamTrue(request.queryParameters["flipH"])
        val flipV = isParamTrue(request.queryParameters["flipV"])

        // handle float and int values
        val rotate = request.queryParameters["rotate"]?.toFloatOrNull()
        val quality = request.queryParameters["quality"]?.toIntOrNull()

        if (width == null || height == null) {
            respond(HttpStatusCode.BadRequest, "Invalid width or height")
            return
        }

        val cacheKey =
            generateCacheKey(
                path, width, height,
                grayscale = grayscale,
                crop = crop,
                flipH = flipH,
                flipV = flipV,
                rotate = rotate,
                quality = quality
            )
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

        val s3Image = S3.readFile(s3Client, env.aws.s3Bucket, path)
        if (s3Image == null) {
            respond(HttpStatusCode.NotFound, "File not found: $path")
            return
        }
        try {
            val processedImage = ImageProcessor.processImage(
                s3Image,
                width,
                height,
                grayscale = grayscale,
                crop = crop,
                flipH = flipH,
                flipV = flipV,
                rotate = rotate,
                quality = quality
            )

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

    private val TRUE_VALUES = setOf("1", "")
    private fun isParamTrue(value: String?): Boolean = value in TRUE_VALUES
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

    fun generateCacheKey(
        path: String,
        width: Int,
        height: Int,
        grayscale: Boolean,
        crop: Boolean,
        flipH: Boolean,
        flipV: Boolean,
        rotate: Float?,
        quality: Int?
    ): String {
        val baseName = path.substringBeforeLast('.') // remove the extension
        val extension = getFileExtension(path)

        // only include active transforms in key
        val params = mutableListOf("${width}x${height}")
        if (grayscale) params.add("g1")
        if (crop) params.add("c1")
        if (rotate != null && rotate != 0f) params.add("r${rotate.toInt()}")
        if (flipH) params.add("h1")
        if (flipV) params.add("v1")
        if (quality != null) params.add("q$quality")

        return "${baseName}-${params.joinToString("")}.$extension"
    }
}

object ImageProcessor {

    fun processImage(
        image: KlipImage,
        width: Int,
        height: Int,
        grayscale: Boolean,
        crop: Boolean,
        flipH: Boolean,
        flipV: Boolean,
        rotate: Float?,
        quality: Int?
    ): ByteArray {
        val inputImage: BufferedImage = ImageIO.read(ByteArrayInputStream(image.data))
        val processedImage = listOfNotNull<(BufferedImage) -> BufferedImage>(
            { img -> if (crop) cropImage(img, width, height) else img },
            { img -> resizeImage(img, width, height) },
            { img -> if (grayscale) applyGrayscale(img) else img },
            { img -> if (flipH) applyFlipH(img) else img },
            { img -> if (flipV) applyFlipV(img) else img },
            { img -> if (rotate != null) applyRotation(img, rotate) else img }
        ).fold(inputImage) { img, transform -> transform(img) }

        // note adding quality last as applyQuality returns ByteArray, not a BufferedImage
        return if (quality != null) {
            applyQuality(processedImage, quality, image.contentType.contentSubtype)
        } else {
            processedImage.toByteArray(format = image.contentType.contentSubtype)
        }
    }

    fun resizeImage(image: BufferedImage, width: Int, height: Int): BufferedImage {
        val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.drawImage(image.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null)
        g.dispose()
        return resized
    }

    fun cropImage(image: BufferedImage, width: Int, height: Int): BufferedImage {
        val startX = (image.width - width) / 2
        val startY = (image.height - height) / 2
        return image.getSubimage(startX, startY, width, height)
    }

    fun applyGrayscale(image: BufferedImage): BufferedImage {
        val grayImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        val g = grayImage.createGraphics()
        g.drawImage(image, 0, 0, null)
        g.dispose()
        return grayImage
    }

    fun applyFlipH(image: BufferedImage): BufferedImage {
        val flipped = BufferedImage(image.width, image.height, image.type)
        val graphics = flipped.createGraphics()
        graphics.drawImage(image, image.width, 0, 0, image.height, 0, 0, image.width, image.height, null)
        graphics.dispose()
        return flipped
    }

    fun applyFlipV(image: BufferedImage): BufferedImage {
        val flipped = BufferedImage(image.width, image.height, image.type)
        val graphics = flipped.createGraphics()
        graphics.drawImage(image, 0, image.height, image.width, 0, 0, 0, image.width, image.height, null)
        graphics.dispose()
        return flipped
    }

    fun applyRotation(image: BufferedImage, rotate: Float): BufferedImage {
        val radians = Math.toRadians(rotate.toDouble())
        val sin = abs(sin(radians))
        val cos = abs(cos(radians))
        val newWidth = (image.width * cos + image.height * sin).toInt()
        val newHeight = (image.width * sin + image.height * cos).toInt()
        val rotatedImage = BufferedImage(newWidth, newHeight, image.type)
        val graphics = rotatedImage.createGraphics()
        graphics.translate((newWidth - image.width) / 2, (newHeight - image.height) / 2)
        graphics.rotate(radians, (image.width / 2).toDouble(), (image.height / 2).toDouble())
        graphics.drawRenderedImage(image, null)
        graphics.dispose()
        return rotatedImage
    }

    fun applyQuality(image: BufferedImage, quality: Int, format: String): ByteArray {
        val adjustedQuality = quality.coerceIn(1, 100) / 100.0f
        logger.debug { "Format: $format, Quality: $quality, Adjusted: $adjustedQuality" }

        val outputStream = ByteArrayOutputStream()

        when (format.lowercase()) {
            "jpeg", "jpg" -> {
                // JPEG supports lossy compression
                val writer = ImageIO.getImageWritersByFormatName(format).next()
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = adjustedQuality
                }

                ImageIO.createImageOutputStream(outputStream).use { output ->
                    writer.output = output
                    writer.write(null, IIOImage(image, null, null), param)
                }
                writer.dispose()
            }
            "png" -> {
                // PNG uses lossless compression, so we set the deflate level instead.
                val writer = ImageIO.getImageWritersByFormatName(format).next()
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionType = "Deflate"
                    // Map quality [1-100] to deflate level [0-9] (max compression)
                    val deflateLevel = (9 * (1 - adjustedQuality)).toInt().coerceIn(0, 9)
                    setCompressionQuality(deflateLevel / 9.0f)
                }

                ImageIO.createImageOutputStream(outputStream).use { output ->
                    writer.output = output
                    writer.write(null, IIOImage(image, null, null), param)
                }
                writer.dispose()
            }
            else -> {
                // for other formats, just write with default settings
                ImageIO.write(image, format, outputStream)
            }
        }

        val finalBytes = outputStream.toByteArray()
        logger.debug { "Final image size: ${finalBytes.size} bytes" }

        return finalBytes
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
