import klip.*
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class KlipTransformsTest {

    @Test
    fun `lenient mode applies default values when custom rule fails`() {
        val transforms = KlipTransforms(
            path = "test.png", width = 5, height = 5
        ).validate(
            mode = ValidationMode.LENIENT,
            customRules = listOf(
                ValidationRule(
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
                    ValidationRule(
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
                ValidationRule(
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
                    ValidationRule(
                        isValid = { it.width > 10 && it.height > 10 },
                        errorMessage = { "Dimensions must be > 10. Got: ${it.width}x${it.height}" },
                        clear = {
                            it.width = 1
                            it.height = 1
                        }
                    ),
                    ValidationRule(
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
//            "Dimensions must be > 10. Got: 5x5, Quality must be between 1 and 100. Got: 120"
            "Quality must be between 1 and 100. Got: 120, Dimensions must be > 10. Got: 5x5, Quality must be between 1 and 100. Got: 120"
        )
    }
}
