import io.ktor.server.plugins.BadRequestException
import klip.*
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class KlipTransformsRulesTest {

    @Test
    fun `strict mode throws error when custom rule fails`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 5, height = 5
        )

        // capture the exception to verify the message
        val exception = assertFailsWith<BadRequestException> {
            transforms.validate(
                rules = listOf(
                    KlipTransformRule(
                        name = "dim gte 10",
                        isValid = { it.width!! > 10 && it.height!! > 10 },
                        errorMessage = { "Dimensions must be > 10. Got: ${it.width}x${it.height}" }
                    )
                )
            )
        }

        // verify the error message
        expectThat(exception.message).isEqualTo("Dimensions must be > 10. Got: 5x5")
    }

    @Test
    fun `passes custom rule validation`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 15, height = 15
        ).validate(
            rules = listOf(
                KlipTransformRule(
                    name = "dim gte 10",
                    isValid = { it.width!! > 10 && it.height!! > 10 },
                    errorMessage = { "Dimensions must be > 10. Got: ${it.width}x${it.height}" }
                )
            )
        )

        // expect valid dimensions to pass without changes
        expectThat(transforms.width).isEqualTo(15)
        expectThat(transforms.height).isEqualTo(15)
    }

    @Test
    fun `multiple rules with different errors`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 5, height = 5, quality = 120
        )

        val exception = assertFailsWith<BadRequestException> {
            transforms.validate(
                rules = listOf(
                    KlipTransformRule(
                        name = "dim gte 10",
                        isValid = { it.width!! > 10 && it.height!! > 10 },
                        errorMessage = { "Dimensions must be > 10. Got: ${it.width}x${it.height}" }
                    ),
                    KlipTransformRule(
                        name = "quality [1, 100]",
                        isValid = { it.quality == null || it.quality in 1..100 },
                        errorMessage = { "Quality must be between 1 and 100. Got: ${it.quality}" }
                    )
                )
            )
        }

        expectThat(exception.message).isEqualTo(
            "Dimensions must be > 10. Got: 5x5, Quality must be between 1 and 100. Got: 120"
        )
    }


    @Test
    fun `allowed dimensions strict mode - fails invalid dimensions`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 50, height = 50
        )

        val allowed = listOf(100 to 100, 200 to 200)

        val exception = assertFailsWith<BadRequestException> {
            transforms.validate(
                rules = listOf(KlipTransformRules.allowedDimensions(allowed))
            )
        }

        expectThat(exception.message).isEqualTo("Allowed dim: 100x100 200x200. Got: 50x50")
    }

    @Test
    fun `allowed dimensions - passes valid dimensions`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100
        ).validate(
            rules = listOf(KlipTransformRules.allowedDimensions(listOf(100 to 100, 200 to 200)))
        )

        // expect dimensions to remain unchanged
        expectThat(transforms.width).isEqualTo(100)
        expectThat(transforms.height).isEqualTo(100)
    }

    @Test
    fun `allowed quality - fails invalid quality`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100, quality = 101
        )

        val allowed = listOf(50, 75, 100)

        val exception = assertFailsWith<BadRequestException> {
            transforms.validate(
                rules = listOf(KlipTransformRules.allowedQuality(allowed))
            )
        }

        expectThat(exception.message).isEqualTo("Allowed quality: $allowed. Got: 101")
    }

    @Test
    fun `allowed rotate - fails invalid rotation`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100, rotate = 45f
        )

        val allowed = listOf(0f, 90f, 180f, 270f)

        val exception = assertFailsWith<BadRequestException> {
            transforms.validate(
                rules = listOf(KlipTransformRules.allowedRotate(allowed))
            )
        }

        expectThat(exception.message).isEqualTo("Allowed rotation: $allowed. Got: 45.0")
    }

    @Test
    fun `allowed blur radius - fails invalid radius`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100, blurRadius = 5.0f
        )

        val allowed = listOf(1.0f, 2.0f, 3.0f)

        val exception = assertFailsWith<BadRequestException> {
            transforms.validate(
                rules = listOf(KlipTransformRules.allowedBlurRadius(allowed))
            )
        }

        expectThat(exception.message).isEqualTo("Allowed blurRadius: $allowed. Got: 5.0")
    }

    @Test
    fun `boolean flag grayscale strict mode - fails when not allowed`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100, grayscale = true
        )

        val exception = assertFailsWith<BadRequestException> {
            transforms.validate(
                rules = listOf(KlipTransformRules.allowedGrayscale(false))
            )
        }

        expectThat(exception.message).isEqualTo("Grayscale is not allowed.")
    }

}
