package klip

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
import klip.image.toByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.kotlin.logger
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val logger = logger("Klip")

data class Env(
    val http: Http = Http(),
    val aws: Aws = Aws()
) {
    data class Http(val port: Int = System.getenv("KLIP_HTTP_PORT")?.toInt() ?: 8080)

    data class Aws(
        val region: String = System.getenv("KLIP_AWS_REGION"),
        val s3Bucket: String = System.getenv("KLIP_S3_BUCKET"),
    )
}

@Serializable
data class KlipHealth(val status: String = "UP")

data class KlipImage(
    val data: ByteArray,
    val contentType: ContentType
)

fun main() {
    val env = Env()
    logger.info(env)

    embeddedServer(CIO, port = env.http.port) {
        install(ContentNegotiation) {
            json(Json { prettyPrint = true })
        }
        setup(env)
    }.start(wait = true)
}


object Routes {

    fun Application.setup(env: Env) {
        val s3Client = S3Client {
            region = env.aws.region
            credentialsProvider = ProfileCredentialsProvider()
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

        environment.monitor.subscribe(ApplicationStopped) {
            s3Client.close()
        }
    }

    suspend fun ApplicationCall.handleImageRequest(s3Client: S3Client, env: Env) {
        val size = parameters["size"] ?: ""
        val match = Regex("(\\d+)x(\\d+)").matchEntire(size)

        val width = match?.groups?.get(1)?.value?.toIntOrNull()
        val height = match?.groups?.get(2)?.value?.toIntOrNull()
        val path = parameters.getAll("path")?.joinToString("/") ?: ""

        val grayscale = request.queryParameters["grayscale"]?.toBoolean() ?: false
        val crop = request.queryParameters["crop"]?.toBoolean() ?: false
        val rotate = request.queryParameters["rotate"]?.toFloatOrNull()

        if (width == null || height == null) {
            respond(HttpStatusCode.BadRequest, "Invalid width or height")
            return
        }

        logger.info("Processing image: Path=$path, Size=${width}x$height, Grayscale=$grayscale, Crop=$crop, Rotate=$rotate")

        val s3Image = ImageFetcher.fetchFromS3(s3Client, env.aws.s3Bucket, path)
        if (s3Image != null) {
            try {
                val processedImage = ImageProcessor.processImage(s3Image, width, height, grayscale, crop, rotate)
                response.headers.append("Content-Type", s3Image.contentType.toString())
                respondBytes(processedImage)
            } catch (e: Exception) {
                logger.error("Error processing image: ${e.message}", e)
                respond(HttpStatusCode.InternalServerError, "Error processing image")
            }
        } else {
            respond(HttpStatusCode.NotFound, "File not found: $path")
        }
    }

    suspend fun ApplicationCall.handleRawImageRequest(s3Client: S3Client, env: Env) {
        val path = parameters.getAll("path")?.joinToString("/") ?: ""
        val s3Object = ImageFetcher.fetchFromS3(s3Client, env.aws.s3Bucket, path)
        if (s3Object != null) {
            response.headers.append("Content-Type", s3Object.contentType.toString())
            respondBytes(s3Object.data)
        } else {
            respond(HttpStatusCode.NotFound, "File not found: $path")
        }
    }
}

object ImageFetcher {

    suspend fun fetchFromS3(s3Client: S3Client, bucket: String, key: String): KlipImage? {
        return try {
            val request = GetObjectRequest {
                this.bucket = bucket
                this.key = key
            }
            s3Client.getObject(request) {
                val bytes = (it.body as ByteStream).toByteArray()
                val contentType = getContentType(key.substringAfterLast('.', "octet-stream"))
                KlipImage(bytes, contentType)
            }
        } catch (e: Exception) {
            logger.error("Error fetching file from S3: ${e.message}", e)
            null
        }
    }

    private fun getContentType(ext: String) = ContentType.parse("image/$ext")
}

object ImageProcessor {

    fun processImage(
        image: KlipImage,
        width: Int,
        height: Int,
        grayscale: Boolean,
        crop: Boolean,
        rotate: Float?
    ): ByteArray {
        val inputImage: BufferedImage = ImageIO.read(ByteArrayInputStream(image.data))

        // crop first
        var bufferedImage = if (crop) cropImage(inputImage, width, height) else inputImage
        bufferedImage = resizeImage(bufferedImage, width, height)
        if (grayscale) {
            bufferedImage = applyGrayscale(bufferedImage)
        }
        if (rotate != null) {
            bufferedImage = applyRotation(bufferedImage, rotate)
        }

        return bufferedImage.toByteArray(format = image.contentType.contentSubtype)
    }

    private fun resizeImage(image: BufferedImage, width: Int, height: Int): BufferedImage {
        val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.drawImage(image.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null)
        g.dispose()
        return resized
    }

    private fun cropImage(image: BufferedImage, width: Int, height: Int): BufferedImage {
        val startX = (image.width - width) / 2
        val startY = (image.height - height) / 2
        return image.getSubimage(startX, startY, width, height)
    }

    private fun applyGrayscale(image: BufferedImage): BufferedImage {
        val grayImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        val g = grayImage.createGraphics()
        g.drawImage(image, 0, 0, null)
        g.dispose()
        return grayImage
    }

    private fun applyRotation(image: BufferedImage, angle: Float): BufferedImage {
        val radians = Math.toRadians(angle.toDouble())
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
}
