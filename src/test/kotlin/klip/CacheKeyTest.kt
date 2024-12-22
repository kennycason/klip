package klip

import klip.S3.generateCacheKey
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.fail

class CacheKeyTest {
    @Test
    fun `test cache key generation`() {
        // all parameters
        expectThat(generateCacheKey(
            "properties/1/0.png",
            width = 100,
            height = 100,
            grayscale = true,
            crop = true,
            flipH = false,
            flipV = false,
            rotate = 90f
        )).isEqualTo("properties/1/0-100x100g1c1r90.png")

        // some parameters omitted
        expectThat(generateCacheKey(
            "properties/1/0.jpeg",
            width = 200,
            height = 300,
            grayscale = true,
            crop = false,
            flipH = false,
            flipV = false,
            rotate = null
        )).isEqualTo("properties/1/0-200x300g1.jpeg")

        // flipH +flipV
        expectThat(generateCacheKey(
            "properties/1/0.jpeg",
            width = 200,
            height = 300,
            grayscale = false,
            crop = false,
            flipH = true,
            flipV = true,
            rotate = null
        )).isEqualTo("properties/1/0-200x300h1v1.jpeg")

        // no transformations
        expectThat(generateCacheKey(
            "properties/1/0.bmp",
            width = 50,
            height = 50,
            grayscale = false,
            crop = false,
            flipH = false,
            flipV = false,
            rotate = null
        )).isEqualTo("properties/1/0-50x50.bmp")
    }
}
