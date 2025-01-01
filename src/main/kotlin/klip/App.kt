package klip

import aws.sdk.kotlin.runtime.auth.credentials.DefaultChainCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
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
import kotlin.system.exitProcess

private val logger = logger("Klip")

data class Env(
    val features: Features = Features(),
    val security: Security = Security(),
    val aws: Aws = Aws(),
    val cache: Cache = Cache(),
    val rules: List<KlipTransformRule> = loadRules(),
    val canvasRules: List<KlipTransformRule> = loadCanvasRules(),
    val gm: GraphicsMagick = GraphicsMagick(),
    val http: Http = Http(),
    val logLevel: String = System.getenv("KLIP_LOG_LEVEL") ?: "info",
) {
    data class Features(
        val klipEnabled: Boolean = System.getenv("KLIP_ENABLED")?.toBoolean() ?: true,
        val canvasEnabled: Boolean = System.getenv("KLIP_CANVAS_ENABLED")?.toBoolean() ?: true,
        val adminEnabled: Boolean = System.getenv("KLIP_ADMIN_ENABLED")?.toBoolean() ?: false
    )

    data class Security(
        val adminApiKey: String = System.getenv("KLIP_ADMIN_API_KEY") ?: ""
    )

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

    data class GraphicsMagick(
        val timeoutSeconds: Long = System.getenv("KLIP_GM_TIMEOUT_SECONDS")?.toLongOrNull() ?: 30L,
        val memoryLimit: String = System.getenv("KLIP_GM_MEMORY_LIMIT") ?: "256MB",
        val mapLimit: String = System.getenv("KLIP_GM_MAP_LIMIT") ?: "512MB",
        val diskLimit: String = System.getenv("KLIP_GM_DISK_LIMIT") ?: "1GB",
        val poolSize: Int = System.getenv("KLIP_GM_POOL_SIZE")?.toIntOrNull() ?: Runtime.getRuntime().availableProcessors()
    )

    data class Http(
        val port: Int = System.getenv("KLIP_HTTP_PORT")?.toInt() ?: 8080
    )

    companion object {
        private fun loadRules(): List<KlipTransformRule> {
            val rulesEnv = System.getenv("KLIP_RULES")
            val rulesFile = System.getenv("KLIP_RULES_FILE")

            val rulesConfig = when {
                rulesEnv?.isNotEmpty() == true -> rulesEnv
                rulesFile?.isNotEmpty() == true -> File(rulesFile).readText()
                else -> ""
            }
            return KlipTransformRules.parseRules(rulesConfig)
        }

        private fun loadCanvasRules(): List<KlipTransformRule> {
            val rulesEnv = System.getenv("KLIP_CANVAS_RULES")
            val rulesFile = System.getenv("KLIP_CANVAS_RULES_FILE")

            val rulesConfig = when {
                rulesEnv?.isNotEmpty() == true -> rulesEnv
                rulesFile?.isNotEmpty() == true -> File(rulesFile).readText()
                else -> ""
            }
            return KlipTransformRules.parseRules(rulesConfig)
        }
    }
}

fun main() {
    logger.info { "Starting Klip" }
    val env = Env()
    logger.info(env)

    Configurator.setRootLevel(Level.getLevel(env.logLevel.uppercase()))

    GraphicsMagickPool.initialize(env.gm)
    GraphicsMagickImageProcessor.initialize(env.gm)

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
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                mapOf("error" to (cause.message ?: "Invalid input"))
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                mapOf("error" to (cause.message ?: "Invalid input"))
            )
        }
        exception<NumberFormatException> { call, cause ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                mapOf("error" to (cause.message ?: "Invalid input"))
            )
        }

        exception<NoSuchKey> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to cause.message)
            )
        }

        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to (cause.message ?: "Resource not found"))
            )
        }

        exception<GraphicsMagickException> { call, cause ->
            logger.error(cause) { "GraphicsMagick operation failed" }
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                mapOf("error" to (cause.message ?: "Image processing failed"))
            )
        }

        // catch-all for unexpected errors
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unexpected error" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "An unexpected error occurred")
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
            if (env.features.klipEnabled) {
                get("/klip/{path...}") {
                    call.handleImageRequest(s3Client, env)
                }
            }

            if (env.features.canvasEnabled) {
                get("/canvas/{dimensions}") {
                    call.handleCanvasRequest(env)
                }
            }

            get("/health") {
                call.respond(KlipHealth())
            }

            get("/version") {
                call.respondText("1.0.0", ContentType.Text.Plain)
            }
        }

        adminEndpoints(env)

        monitor.subscribe(ApplicationStopped) {
            s3Client.close()
        }
    }

    private suspend fun ApplicationCall.handleImageRequest(s3Client: S3Client, env: Env) {
        Counters.incrementRequests()

        val transforms = KlipTransforms
            .from(parameters)
            .validate(env.rules)

        if (transforms.isEmpty()) {
            return handleRawImageRequest(s3Client, env)
        }

        // check cache
        if (env.cache.enabled) {
            val cacheKey = generateCacheKey(transforms)
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
            ?: throw NotFoundException("File not found: ${transforms.path}")
        val processedImage = GraphicsMagickImageProcessor.processImage(s3Image, transforms)

        if (env.cache.enabled) {
            val cacheKey = generateCacheKey(transforms)
            writeToCache(s3Client, env, cacheKey, processedImage)
        }

        response.headers.append("Content-Type", s3Image.contentType.toString())
        respondBytes(processedImage)
    }

    private suspend fun ApplicationCall.handleRawImageRequest(s3Client: S3Client, env: Env) {
        val path = parameters.getAll("path")?.joinToString("/") ?: ""
        val s3Object = S3.readFile(s3Client, env.aws.s3Bucket, path)
        s3Object ?: throw NotFoundException("File not found: $path")
        response.headers.append("Content-Type", s3Object.contentType.toString())
        respondBytes(s3Object.data)
    }

    private suspend fun ApplicationCall.handleCanvasRequest(env: Env) {
        Counters.incrementCanvasRequests()
        val transforms = KlipCanvasTransforms
            .from(parameters)
            .validate(env.canvasRules)

        val image = GraphicsMagickImageProcessor.createCanvas(transforms)
        response.headers.append("Content-Type", ContentType.Image.PNG.toString())
        respondBytes(image)
    }

    private fun Application.adminEndpoints(env: Env) {
        if (!env.features.adminEnabled) return

        if (env.features.adminEnabled && env.security.adminApiKey.isBlank()) {
            logger.error("Admin endpoints enabled but KLIP_ADMIN_API_KEY not set")
            exitProcess(1)
        }

        if (env.security.adminApiKey.isBlank()) {
            logger.error("Admin endpoints enabled but KLIP_ADMIN_API_KEY not set")
            return
        }

        routing {
            route("/admin") {
                intercept(ApplicationCallPipeline.Call) {
                    val authHeader = call.request.header("Authorization")
                    if (authHeader == null || !authHeader.startsWith("ApiKey ")) {
                        call.respond(HttpStatusCode.Unauthorized, "Missing or invalid Authorization header")
                        return@intercept finish()
                    }

                    val providedKey = authHeader.removePrefix("ApiKey ").trim()
                    if (providedKey != env.security.adminApiKey) {
                        call.respond(HttpStatusCode.Unauthorized, "Invalid API key")
                        return@intercept finish()
                    }
                }

                get("/status") {
                    call.respond(
                        KlipStatus(
                            totalRequests = Counters.getRequests(),
                            cacheHits = Counters.getCacheHits(),
                            cacheHitRate = Counters.getCacheHitRate(),
                            canvasRequests = Counters.getCanvasRequests(),
                            pool = GraphicsMagickPool.getStats()
                        )
                    )
                }
            }
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
    val cacheHitRate: Float,
    val canvasRequests: Int,
    val pool: PoolStats,
)

data class KlipImage(
    val data: ByteArray,
    val contentType: ContentType
) {
    val extension = contentType.contentSubtype
}
