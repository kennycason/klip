package klip

import aws.sdk.kotlin.runtime.auth.credentials.DefaultChainCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
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

fun main() {
    logger.info { "Starting Klip" }

    val env = Env()
    Configurator.setRootLevel(Level.getLevel(env.logLevel.uppercase()))

    logger.info(env)

    embeddedServer(CIO, port = env.http.port) {
        configureService(this)
        setup(env)
    }.start(wait = true)
}

fun configureService(app: Application) {
    app.install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            encodeDefaults = true
        })
    }
    app.install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                mapOf("error" to (cause.message ?: "Invalid input"))
            )
        }
    }
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

            get("/admin/status") {
                call.respond(
                    KlipStatus(
                        totalRequests = Counters.getRequests(),
                        cacheHits = Counters.getCacheHits(),
                        cacheHitRate = Counters.getCacheHitRate()
                    )
                )
            }
        }

        monitor.subscribe(ApplicationStopped) {
            s3Client.close()
        }
    }

    private suspend fun ApplicationCall.handleImageRequest(s3Client: S3Client, env: Env) {
        Counters.incrementRequests()
        val transforms = KlipTransforms.from(parameters)
            .validate(ValidationMode.STRICT,
                customRules = listOf(
                    ValidationRule( // sample test rule
                        isValid = { it.width > 10 && it.height > 10 },
                        errorMessage = { "Dimensions must be > 10. Got: ${it.width}x${it.height}" },
                        clear = {
                            it.width = 1
                            it.height = 1
                        }
                    )
                )
            )

        val cacheKey = generateCacheKey(transforms)
        logger.info("Cache Key: $cacheKey")

        // check cache
        if (env.cache.enabled) {
            val cachedImage = checkCache(s3Client, env, cacheKey)
            if (cachedImage != null) {
                logger.info("Cache hit: $cacheKey")
                Counters.incrementCacheHits()
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
        Counters.incrementRequests()
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

@Serializable
data class KlipHealth(
    val status: String = "UP"
)

@Serializable
data class KlipStatus(
    val totalRequests: Int,
    val cacheHits: Int,
    val cacheHitRate: Float
)

data class KlipImage(
    val data: ByteArray,
    val contentType: ContentType
) {
    val extension = contentType.contentSubtype
}
