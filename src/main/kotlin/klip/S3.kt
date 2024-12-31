package klip

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import io.ktor.http.ContentType
import klip.Files.getFileExtension
import org.apache.logging.log4j.kotlin.logger
import java.math.BigDecimal
import java.math.RoundingMode

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
        val cacheBucket = env.cache.cacheBucket
        val cachePath = "${env.cache.cachePrefix}$cacheKey"

        return try {
            val request = GetObjectRequest {
                this.bucket = cacheBucket
                this.key = cachePath
            }
            val extension = getFileExtension(cacheKey)
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
        val cacheBucket = env.cache.cacheBucket
        val cachePath = "${env.cache.cachePrefix}$cacheKey"

        logger.info { "Writing $cacheKey to cache."}

        val extension = getFileExtension(cacheKey)
        val request = aws.sdk.kotlin.services.s3.model.PutObjectRequest {
            bucket = cacheBucket
            key = cachePath
            body = ByteStream.fromBytes(data)
            contentType = "image/$extension"
        }
        s3Client.putObject(request)
    }

    /**
     * filter keys are stored in alphabetic order.
     */
    fun generateCacheKey(t: KlipTransforms): String {
        val baseName = t.path.substringBeforeLast('.') // remove the extension
        val extension = getFileExtension(t.path)

        // only include active transforms in key
        val params = mutableListOf<String>()
        if (t.width != null) params.add("w${t.width}")
        if (t.height != null) params.add("h${t.height}")
        if (t.blurRadius != null && t.blurSigma != null) params.add("b${ftos(t.blurRadius!!)}x${ftos(t.blurSigma!!)}")
        if (t.crop) params.add("c1")
        if (t.fit != null) params.add(t.fit.toString().lowercase())
        if (t.colors != null) params.add("c${t.colors}") // colors can never be 1, and will not collide with c1
        if (t.dither) params.add("d1")
        if (t.grayscale) params.add("g1")
        if (t.flipH) params.add("h1")
        if (t.quality != null) params.add("q${t.quality}")
        if (t.rotate != null && t.rotate != 0f) params.add("r${ftos(t.rotate!!)}")
        if (t.sharpen != null) params.add("s${ftos(t.sharpen!!)}")
        if (t.flipV) params.add("v1")

        return "${baseName}-${params.joinToString("")}.$extension"
    }

    private fun getContentTypeByExtension(extension: String): ContentType {
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



    private fun ftos(v: Float, digits: Int = 2): String {
        return BigDecimal(v.toString())
            .setScale(digits, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }
}
