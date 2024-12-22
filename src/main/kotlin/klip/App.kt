package klip

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.kotlin.logger
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private val logger = logger("Klip")

data class Env(
    val http: Http = Http(),
    val aws: Aws = Aws()
) {
    data class Http(
        val port: Int = System.getenv("KLIP_HTTP_PORT")?.toInt() ?: 8080
    )

    data class Aws(
        val region: String = System.getenv("KLIP_AWS_REGION"),
        val s3Bucket: String = System.getenv("KLIP_S3_BUCKET"),
        val accessKey: String = "",
        val secretKey: String = ""
    )
}

val BMP: ContentType = ContentType("image", "bmp")
val WEBP: ContentType = ContentType("image", "webp")

@Serializable
data class KlipHealth(val status: String = "UP")

fun main() {
    val env = Env(
        aws = Env.Aws()
    )

    logger.info(env)

    embeddedServer(CIO, port = env.http.port) {
        install(ContentNegotiation) {
            json(Json { prettyPrint = true })
        }
        apiRoutes(env)
    }.start(wait = true)
}

fun Application.apiRoutes(env: Env) {
    val s3Client = S3Client {
        region = env.aws.region
        credentialsProvider = ProfileCredentialsProvider()
    }

    routing {

        get("/img/{size}/{path...}") {
            val size = call.parameters["size"] ?: ""
            val match = Regex("(\\d+)x(\\d+)").matchEntire(size)

            val width = match?.groups?.get(1)?.value?.toIntOrNull()
            val height = match?.groups?.get(2)?.value?.toIntOrNull()
            val path = call.parameters.getAll("path")?.joinToString("/") ?: ""

            val grayscale = call.request.queryParameters["grayscale"]?.toBoolean() ?: false
            val crop = call.request.queryParameters["crop"]?.toBoolean() ?: false
            val rotate = call.request.queryParameters["rotate"]?.toFloatOrNull()

            if (width == null || height == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid width or height")
                return@get
            }

            logger.info { "Params - Width: $width, Height: $height, Path: $path, Grayscale: $grayscale, Crop: $crop, Rotate: $rotate" }


            val s3Object = getFileFromS3(s3Client, env.aws.s3Bucket, path)
            if (s3Object != null) {
                try {
                    // Apply image processing
                    val processedImage = processImage(s3Object.data, width, height, grayscale, crop, rotate)

                    call.response.headers.append("Content-Type", s3Object.contentType.toString())
                    call.respondBytes(processedImage)
                } catch (e: Exception) {
                    logger.error("Error processing image: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, "Error processing image")
                }
            } else {
                call.respond(HttpStatusCode.NotFound, "File not found: $path")
            }
        }

        get("/img/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
            logger.info { "Fetching file $path" }
            val s3Object = getFileFromS3(s3Client, env.aws.s3Bucket, path)

            if (s3Object != null) {
                call.response.headers.append("Content-Type", s3Object.contentType.toString())
                call.respondBytes(s3Object.data)
            } else {
                call.respond(HttpStatusCode.NotFound, "File not found: $path")
            }
        }

        get("/health") {
            call.respond(KlipHealth())
        }

        get("/version") {
            call.respondText("1.0.0", ContentType.Text.Plain)
        }
    }

    environment.monitor.subscribe(ApplicationStopped) {
        s3Client.close()
    }
}


suspend fun getFileFromS3(s3Client: S3Client, bucket: String, key: String): FileDataAndType? {
    return try {
        val request = GetObjectRequest {
            this.bucket = bucket
            this.key = key
        }
        return s3Client.getObject(request) {
            val byteStream = it.body as ByteStream
            val bytes = byteStream.toByteArray()
            val contentType = getImageContentType(key.substringAfterLast('.', "octet-stream"))
            FileDataAndType(bytes, contentType)
        }
    } catch (e: Exception) {
        logger.error("Error fetching file from S3: ${e.message}", e)
        null
    }
}

data class FileDataAndType(
    val data: ByteArray,
    val contentType: ContentType
)

fun getImageContentType(extension: String): ContentType {
    return when (extension.lowercase()) {
        "bmp" -> BMP
        "gif" -> ContentType.Image.GIF
        "jpeg", "jpg" -> ContentType.Image.JPEG
        "png" -> ContentType.Image.PNG
        "svg" -> ContentType.Image.SVG
        "webp" -> WEBP
        "ico" -> ContentType("image", "x-icon") // Explicit MIME type for ICO
        else -> ContentType.Application.OctetStream // Fallback for unknown types
    }
}


fun cropImage(
    imageData: ByteArray,
    width: Int,
    height: Int
): ByteArray? {
    return try {
        val inputStream = ByteArrayInputStream(imageData)
        val originalImage: BufferedImage = ImageIO.read(inputStream)

        val croppedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = croppedImage.createGraphics()

        // Center crop logic
        val xOffset = (originalImage.width - width) / 2
        val yOffset = (originalImage.height - height) / 2
        g.drawImage(originalImage, 0, 0, width, height, xOffset, yOffset, xOffset + width, yOffset + height, null)
        g.dispose()

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(croppedImage, "png", outputStream)
        outputStream.toByteArray()
    } catch (e: Exception) {
        logger.error("Failed to crop image: ${e.message}", e)
        null
    }
}

fun processImage(
    imageData: ByteArray,
    width: Int,
    height: Int,
    grayscale: Boolean,
    crop: Boolean,
    rotate: Float?
): ByteArray {
    val inputImage = ImageIO.read(ByteArrayInputStream(imageData))

    // Resize image
    var outputImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = outputImage.createGraphics()
    graphics.drawImage(
        inputImage.getScaledInstance(width, height, Image.SCALE_SMOOTH),
        0,
        0,
        null
    )
    graphics.dispose()

    // Apply grayscale
    if (grayscale) {
        outputImage = applyGrayscale(outputImage)
    }

    // Crop image (center crop)
    if (crop) {
        outputImage = applyCrop(outputImage, width, height)
    }

    // Rotate image
    if (rotate != null) {
        outputImage = applyRotation(outputImage, rotate)
    }

    // Write output image to byte array
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(outputImage, "png", outputStream)
    return outputStream.toByteArray()
}

// Grayscale filter
fun applyGrayscale(image: BufferedImage): BufferedImage {
    val grayImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
    val graphics = grayImage.createGraphics()
    graphics.drawImage(image, 0, 0, null)
    graphics.dispose()
    return grayImage
}

// Crop filter (center crop)
fun applyCrop(image: BufferedImage, width: Int, height: Int): BufferedImage {
    val startX = (image.width - width) / 2
    val startY = (image.height - height) / 2
    return image.getSubimage(startX, startY, width, height)
}

// Rotate image
fun applyRotation(image: BufferedImage, angle: Float): BufferedImage {
    val radians = Math.toRadians(angle.toDouble())
    val sin = Math.abs(Math.sin(radians))
    val cos = Math.abs(Math.cos(radians))
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
