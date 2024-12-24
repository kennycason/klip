package klip

import klip.S3.generateCacheKey
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.fail

class CacheKeyTest {
    @Test
    fun `test cache key generation`() {
        // grayscale, crop, rotate
        expectThat(
            generateCacheKey(
                KlipTransforms(
                    path = "properties/1/0.png", width = 100, height = 100,
                    grayscale = true,
                    crop = true,
                    rotate = 90.0f
                )
            )
        ).isEqualTo("properties/1/0-100x100c1g1r90.png")

        // some parameters omitted
        expectThat(
            generateCacheKey(
                KlipTransforms(
                    path = "properties/1/0.jpeg", width = 200, height = 300,
                    grayscale = true
                )
            )
        ).isEqualTo("properties/1/0-200x300g1.jpeg")

        // flipH +flipV
        expectThat(
            generateCacheKey(
                KlipTransforms(
                    path = "properties/1/0.jpeg", width = 200, height = 300,
                    flipH = true,
                    flipV = true
                )
            )
        ).isEqualTo("properties/1/0-200x300h1v1.jpeg")

        // quality
        expectThat(
            generateCacheKey(
                KlipTransforms(
                    path = "properties/1/0.bmp", width = 50, height = 50,
                    quality = 64
                )
            )
        ).isEqualTo("properties/1/0-50x50q64.bmp")

        // blur
        expectThat(
            generateCacheKey(
                KlipTransforms(
                    path = "properties/1/0.bmp", width = 50, height = 50,
                    blurRadius = 1.0f,
                    blurSigma = 3.5f
                )
            )
        ).isEqualTo("properties/1/0-50x50b1x3.5.bmp")

        // combine sharpen, dither, colors.
        expectThat(
            generateCacheKey(
                KlipTransforms(
                    path = "properties/1/0.bmp", width = 50, height = 50,
                    sharpen = 2.0f,
                    dither = true,
                    colors = 16
                )
            )
        ).isEqualTo("properties/1/0-50x50c16d1s2.bmp")


        // no transformations
        expectThat(
            generateCacheKey(
                KlipTransforms(
                    path = "properties/1/0.bmp", width = 50, height = 50,
                )
            )
        ).isEqualTo("properties/1/0-50x50.bmp")
    }

    @Test
    fun `test cache key decimal truncation`() {
        expectThat(
            generateCacheKey(
                KlipTransforms(
                    path = "properties/1/0.png", width = 100, height = 100,
                    rotate = 90.3821f
                )
            )
        ).isEqualTo("properties/1/0-100x100r90.38.png")
    }
}
