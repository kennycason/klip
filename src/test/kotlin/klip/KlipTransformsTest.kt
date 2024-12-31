package klip


import io.ktor.http.parametersOf
import io.ktor.server.plugins.BadRequestException
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import strikt.assertions.message
import kotlin.test.Test

class KlipTransformsTest {

    @Test
    fun `parse arity-0 parameters (flags)`() {
        val params = parametersOf(
            "d" to listOf("20x20"),
            "flipV" to listOf(),
            "grayscale" to listOf(),
            "crop" to listOf(),
            "flipH" to listOf(),
            "dither" to listOf()
        )

        val transforms = KlipTransforms.from(params)

        expectThat(transforms.path).isEqualTo("")
        expectThat(transforms.width).isEqualTo(20)
        expectThat(transforms.height).isEqualTo(20)
        expectThat(transforms.flipV).isEqualTo(true)
        expectThat(transforms.grayscale).isEqualTo(true)
        expectThat(transforms.crop).isEqualTo(true)
        expectThat(transforms.flipH).isEqualTo(true)
        expectThat(transforms.dither).isEqualTo(true)
    }

    @Test
    fun `parse arity-1 parameters`() {
        val params = parametersOf(
            "d" to listOf("40x50"),
            "rotate" to listOf("90"),
            "quality" to listOf("80"),
            "sharpen" to listOf("1.5"),
            "colors" to listOf("128"),
            "blur" to listOf("2x1")
        )

        val transforms = KlipTransforms.from(params)

        expectThat(transforms.path).isEqualTo("")
        expectThat(transforms.width).isEqualTo(40)
        expectThat(transforms.height).isEqualTo(50)
        expectThat(transforms.rotate).isEqualTo(90f)
        expectThat(transforms.quality).isEqualTo(80)
        expectThat(transforms.sharpen).isEqualTo(1.5f)
        expectThat(transforms.colors).isEqualTo(128)
        expectThat(transforms.blurRadius).isEqualTo(2.0f)
        expectThat(transforms.blurSigma).isEqualTo(1.0f)
    }

    @Test
    fun `parse mixed arity-0 and arity-1 parameters`() {
        val params = parametersOf(
            "w" to listOf("100"),
            "h" to listOf("200"),
            "flipV" to listOf(),
            "rotate" to listOf("45"),
            "sharpen" to listOf("2.0"),
            "grayscale" to listOf()
        )

        val transforms = KlipTransforms.from(params)

        expectThat(transforms.path).isEqualTo("")
        expectThat(transforms.width).isEqualTo(100)
        expectThat(transforms.height).isEqualTo(200)
        expectThat(transforms.flipV).isEqualTo(true)
        expectThat(transforms.rotate).isEqualTo(45f)
        expectThat(transforms.sharpen).isEqualTo(2.0f)
        expectThat(transforms.grayscale).isEqualTo(true)
    }

    @Test
    fun `parse parameters with defaults`() {
        val params = parametersOf(
            "d" to listOf("30x40")
        )

        val transforms = KlipTransforms.from(params)

        expectThat(transforms.path).isEqualTo("")
        expectThat(transforms.width).isEqualTo(30)
        expectThat(transforms.height).isEqualTo(40)
        expectThat(transforms.grayscale).isEqualTo(false)
        expectThat(transforms.crop).isEqualTo(false)
        expectThat(transforms.flipH).isEqualTo(false)
        expectThat(transforms.flipV).isEqualTo(false)
        expectThat(transforms.dither).isEqualTo(false)
        expectThat(transforms.rotate).isEqualTo(null)
        expectThat(transforms.quality).isEqualTo(null)
        expectThat(transforms.sharpen).isEqualTo(null)
        expectThat(transforms.colors).isEqualTo(null)
        expectThat(transforms.blurRadius).isEqualTo(null)
        expectThat(transforms.blurSigma).isEqualTo(null)
    }

    @Test
    fun `parse dim parameter throws exception for missing dimensions`() {
        val params = parametersOf(
            "d" to listOf("40x") // Invalid dimension
        )

        val exception = expectThrows<BadRequestException> {
            KlipTransforms.from(params)
        }

        expectThat(exception.message.subject)
            .isEqualTo("Invalid dimension format, expected {width}x{height}")
    }

    @Test
    fun `parse boolean flag variations (true or 1)`() {
        val params = parametersOf(
            "w" to listOf("640"),
            "h" to listOf("480"),
            "flipV" to listOf("true"),
            "flipH" to listOf("1"),
            "grayscale" to listOf("1"),
            "crop" to listOf("true"),
            "dither" to listOf("false") // should evaluate to false
        )

        val transforms = KlipTransforms.from(params)

        expectThat(transforms.width).isEqualTo(640)
        expectThat(transforms.height).isEqualTo(480)
        expectThat(transforms.flipV).isEqualTo(true)
        expectThat(transforms.flipH).isEqualTo(true)
        expectThat(transforms.grayscale).isEqualTo(true)
        expectThat(transforms.crop).isEqualTo(true)
        expectThat(transforms.dither).isEqualTo(false)
    }

    @Test
    fun `parse invalid rotate throws exception`() {
        val params = parametersOf(
            "d" to listOf("50x50"),
            "rotate" to listOf("invalid") // Invalid rotation value
        )

        val exception = expectThrows<BadRequestException> {
            KlipTransforms.from(params)
        }

        expectThat(exception.message.subject)
            .isEqualTo("Failed to parse decimal for key rotate=invalid")
    }

    @Test
    fun `parse invalid quality throws exception`() {
        val params = parametersOf(
            "d" to listOf("50x50"),
            "quality" to listOf("invalid") // Invalid quality value
        )

        val exception = expectThrows<BadRequestException> {
            KlipTransforms.from(params)
        }

        expectThat(exception.message.subject)
            .isEqualTo("Failed to parse integer for key quality=invalid")
    }

    @Test
    fun `parse path with multiple segments`() {
        val params = parametersOf(
            "d" to listOf("50x50"),
            "path" to listOf("folder/subfolder/image.png")
        )

        val transforms = KlipTransforms.from(params)

        expectThat(transforms.path).isEqualTo("folder/subfolder/image.png")
    }
}
