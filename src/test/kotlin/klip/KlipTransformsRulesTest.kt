import klip.*
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class KlipTransformsRulesTest {

    @Test
    fun `lenient mode applies default values when custom rule fails`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 5, height = 5
        ).validate(
            mode = ValidationMode.LENIENT,
            customRules = listOf(
                KlipTransformRule(
                    name = "dim gte 10",
                    isValid = { it.width > 10 && it.height > 10 },
                    errorMessage = { "Dimensions must be > 10. Got: ${it.width}x${it.height}" },
                    clear = {
                        it.width = 1
                        it.height = 1
                    }
                )
            )
        )

        // expect width and height to default to 1 in lenient mode
        expectThat(transforms.width).isEqualTo(1)
        expectThat(transforms.height).isEqualTo(1)
    }

    @Test
    fun `strict mode throws error when custom rule fails`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 5, height = 5
        )

        // capture the exception to verify the message
        val exception = assertFailsWith<IllegalArgumentException> {
            transforms.validate(
                mode = ValidationMode.STRICT,
                customRules = listOf(
                    KlipTransformRule(
                        name = "dim gte 10",
                        isValid = { it.width > 10 && it.height > 10 },
                        errorMessage = { "Dimensions must be > 10. Got: ${it.width}x${it.height}" },
                        clear = {
                            it.width = 1
                            it.height = 1
                        }
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
            mode = ValidationMode.STRICT,
            customRules = listOf(
                KlipTransformRule(
                    name = "dim gte 10",
                    isValid = { it.width > 10 && it.height > 10 },
                    errorMessage = { "Dimensions must be > 10. Got: ${it.width}x${it.height}" },
                    clear = {
                        it.width = 1
                        it.height = 1
                    }
                )
            )
        )

        // expect valid dimensions to pass without changes
        expectThat(transforms.width).isEqualTo(15)
        expectThat(transforms.height).isEqualTo(15)
    }

    @Test
    fun `multiple rules with different errors in strict mode`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 5, height = 5, quality = 120
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            transforms.validate(
                mode = ValidationMode.STRICT,
                customRules = listOf(
                    KlipTransformRule(
                        name = "dim gte 10",
                        isValid = { it.width > 10 && it.height > 10 },
                        errorMessage = { "Dimensions must be > 10. Got: ${it.width}x${it.height}" },
                        clear = {
                            it.width = 1
                            it.height = 1
                        }
                    ),
                    KlipTransformRule(
                        name = "quality [1, 100]",
                        isValid = { it.quality == null || it.quality in 1..100 },
                        errorMessage = { "Quality must be between 1 and 100. Got: ${it.quality}" },
                        clear = {
                            it.quality = null
                        }
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

        val allowed = setOf(100 to 100, 200 to 200)

        val exception = assertFailsWith<IllegalArgumentException> {
            transforms.validate(
                mode = ValidationMode.STRICT,
                customRules = listOf(KlipTransformRules.allowedDimensions(allowed))
            )
        }

        expectThat(exception.message).isEqualTo("Allowed dimensions are 100x100 200x200. Got: 50x50")
    }

    @Test
    fun `allowed dimensions lenient mode - defaults to first allowed`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 50, height = 50
        ).validate(
            mode = ValidationMode.LENIENT,
            customRules = listOf(KlipTransformRules.allowedDimensions(setOf(100 to 100, 200 to 200)))
        )

        // expect dimensions to default to first allowed value
        expectThat(transforms.width).isEqualTo(100)
        expectThat(transforms.height).isEqualTo(100)
    }

    @Test
    fun `allowed dimensions strict mode - passes valid dimensions`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100
        ).validate(
            mode = ValidationMode.STRICT,
            customRules = listOf(KlipTransformRules.allowedDimensions(setOf(100 to 100, 200 to 200)))
        )

        // expect dimensions to remain unchanged
        expectThat(transforms.width).isEqualTo(100)
        expectThat(transforms.height).isEqualTo(100)
    }

    @Test
    fun `allowed quality strict mode - fails invalid quality`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100, quality = 101
        )

        val allowed = setOf(50, 75, 100)

        val exception = assertFailsWith<IllegalArgumentException> {
            transforms.validate(
                mode = ValidationMode.STRICT,
                customRules = listOf(KlipTransformRules.allowedQuality(allowed))
            )
        }

        expectThat(exception.message).isEqualTo("Allowed qualities are $allowed. Got: 101")
    }

    @Test
    fun `allowed quality lenient mode - resets invalid quality`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100, quality = 101
        ).validate(
            mode = ValidationMode.LENIENT,
            customRules = listOf(KlipTransformRules.allowedQuality(setOf(50, 75, 100)))
        )

        // expect quality to default to null
        expectThat(transforms.quality).isEqualTo(null)
    }

    @Test
    fun `allowed rotate strict mode - fails invalid rotation`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100, rotate = 45f
        )

        val allowed = setOf(0f, 90f, 180f, 270f)

        val exception = assertFailsWith<IllegalArgumentException> {
            transforms.validate(
                mode = ValidationMode.STRICT,
                customRules = listOf(KlipTransformRules.allowedRotate(allowed))
            )
        }

        expectThat(exception.message).isEqualTo("Allowed rotations are $allowed. Got: 45.0")
    }

    @Test
    fun `allowed rotate lenient mode - resets invalid rotation`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100, rotate = 45f
        ).validate(
            mode = ValidationMode.LENIENT,
            customRules = listOf(KlipTransformRules.allowedRotate(setOf(0f, 90f, 180f, 270f)))
        )

        // expect rotation to default to null
        expectThat(transforms.rotate).isEqualTo(null)
    }

    @Test
    fun `allowed blur radius strict mode - fails invalid radius`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100, blurRadius = 5.0f
        )

        val allowed = setOf(1.0f, 2.0f, 3.0f)

        val exception = assertFailsWith<IllegalArgumentException> {
            transforms.validate(
                mode = ValidationMode.STRICT,
                customRules = listOf(KlipTransformRules.allowedBlurRadius(allowed))
            )
        }

        expectThat(exception.message).isEqualTo("Allowed blur radii are $allowed. Got: 5.0")
    }

    @Test
    fun `allowed blur radius lenient mode - resets invalid radius`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100, blurRadius = 5.0f
        ).validate(
            mode = ValidationMode.LENIENT,
            customRules = listOf(KlipTransformRules.allowedBlurRadius(setOf(1.0f, 2.0f, 3.0f)))
        )

        // expect blur radius to default to null
        expectThat(transforms.blurRadius).isEqualTo(null)
        expectThat(transforms.blurSigma).isEqualTo(null)
    }

    @Test
    fun `boolean flag grayscale strict mode - fails when not allowed`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100, grayscale = true
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            transforms.validate(
                mode = ValidationMode.STRICT,
                customRules = listOf(KlipTransformRules.allowedGrayscale(false))
            )
        }

        expectThat(exception.message).isEqualTo("Grayscale is not allowed.")
    }

    @Test
    fun `boolean flag grayscale lenient mode - resets grayscale`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 100, height = 100, grayscale = true
        ).validate(
            mode = ValidationMode.LENIENT,
            customRules = listOf(KlipTransformRules.allowedGrayscale(false))
        )

        // expect grayscale to be reset to false
        expectThat(transforms.grayscale).isEqualTo(false)
    }

}
