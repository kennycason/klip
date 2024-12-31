package klip

import io.ktor.http.parametersOf
import io.ktor.server.plugins.BadRequestException
import strikt.api.expectThat
import strikt.assertions.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith

class KlipTransformsParsingTest {

    @Test
    fun `parse valid parameters with arity 0`() {
        val params = parametersOf(
            "d" to listOf("100x200"),
            "flipV" to listOf(), // arity 0
            "flipH" to listOf(),
            "grayscale" to listOf(),
            "dither" to listOf()
        )

        val transforms = KlipTransforms.from(params)

        expectThat(transforms).and {
            get { width }.isEqualTo(100)
            get { height }.isEqualTo(200)
            get { flipV }.isTrue()
            get { flipH }.isTrue()
            get { grayscale }.isTrue()
            get { dither }.isTrue()
        }
    }

    @Test
    fun `parse valid parameters with arity 1`() {
        val params = parametersOf(
            "d" to listOf("150x250"),
            "flipV" to listOf("1"), // arity 1
            "flipH" to listOf("true"),
            "grayscale" to listOf("1"),
            "dither" to listOf("true")
        )

        val transforms = KlipTransforms.from(params)

        expectThat(transforms).and {
            get { width }.isEqualTo(150)
            get { height }.isEqualTo(250)
            get { flipV }.isTrue()
            get { flipH }.isTrue()
            get { grayscale }.isTrue()
            get { dither }.isTrue()
        }
    }

    @Test
    fun `conflicting w h and d filters size throws exception`() {
        val params = parametersOf(
            "w" to listOf("100"),
            "d" to listOf("100x100"),
        )

        val exception = assertFailsWith<BadRequestException> {
            KlipTransforms.from(params)
        }

        expectThat(exception).get { message }.isEqualTo("Conflicting dimensions specified. Use either 'w'/'h' or 'd', not both.")
    }

    @Test
    fun `parse invalid width or height throws exception`() {
        val params = parametersOf(
            "d" to listOf("abcx100") // Invalid width
        )

        val exception = assertFailsWith<BadRequestException> {
            KlipTransforms.from(params)
        }

        expectThat(exception).get { message }.isEqualTo("Invalid dimension format, expected {width}x{height}")
    }

    @Test
    fun `parse invalid rotate throws exception`() {
        val params = parametersOf(
            "d" to listOf("50x50"),
            "rotate" to listOf("invalid") // Invalid rotation
        )

        val exception = assertFailsWith<BadRequestException> {
            KlipTransforms.from(params)
        }

        expectThat(exception).get { message }.isEqualTo("Failed to parse decimal for key rotate=invalid")
    }

    @Test
    fun `parse blur with radius only`() {
        val params = parametersOf(
            "d" to listOf("200x200"),
            "blur" to listOf("2.5") // Radius only
        )

        val transforms = KlipTransforms.from(params)

        expectThat(transforms).and {
            get { blurRadius }.isEqualTo(2.5f)
            get { blurSigma }.isEqualTo(1.25f) // Default sigma = radius * 0.5
        }
    }

    @Test
    fun `parse blur with radius and sigma`() {
        val params = parametersOf(
            "d" to listOf("200x200"),
            "blur" to listOf("3x1.5") // Radius and sigma
        )

        val transforms = KlipTransforms.from(params)

        expectThat(transforms).and {
            get { blurRadius }.isEqualTo(3.0f)
            get { blurSigma }.isEqualTo(1.5f)
        }
    }

    @Test
    fun `parse invalid blur throws exception`() {
        val params = parametersOf(
            "d" to listOf("200x200"),
            "blur" to listOf("invalid") // Invalid format
        )

        val exception = assertFailsWith<BadRequestException> {
            KlipTransforms.from(params)
        }

        expectThat(exception).get { message }.isEqualTo("Failed to parse blur. blur=[1,10] or blur={radius}x{sigma}")
    }

    @Test
    fun `parse colors within valid range`() {
        val params = parametersOf(
            "d" to listOf("200x200"),
            "colors" to listOf("256") // Max valid value
        )

        val transforms = KlipTransforms.from(params)

        expectThat(transforms.colors).isEqualTo(256)
    }

    @Test
    fun `parse colors outside valid range throws exception`() {
        val params = parametersOf(
            "d" to listOf("200x200"),
            "colors" to listOf("300") // Invalid value
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            KlipTransforms.from(
                params,
                rules = listOf(KlipTransformRules.allowedColor(listOf(16, 32, 64, 256)))
            )
        }

        expectThat(exception).get { message }
            .isEqualTo("Allowed color: [16, 32, 64, 256]. Got: 300")
    }

    @Test
    fun `parse missing optional parameters`() {
        val params = parametersOf(
            "d" to listOf("100x100")
        )

        val transforms = KlipTransforms.from(params)

        expectThat(transforms).and {
            get { rotate }.isNull()
            get { quality }.isNull()
            get { sharpen }.isNull()
            get { colors }.isNull()
            get { blurRadius }.isNull()
            get { blurSigma }.isNull()
            get { flipH }.isFalse()
            get { flipV }.isFalse()
            get { grayscale }.isFalse()
            get { dither }.isFalse()
        }
    }

    @Test
    fun `validate rules parsed from file and applied correctly`() {
        val rulesFile = File("src/test/resources/rules_file_test.txt")
        val rules = KlipTransformRules.parseRules(rulesFile.readText())

        val params = parametersOf(
            "d" to listOf("128x128"),
            "quality" to listOf("50"),
            "rotate" to listOf("45"),
//            "blur" to listOf("5x2.5"),
            "grayscale" to listOf("1"),
            "flipH" to listOf("1"), // disallowed
            "dither" to listOf("1") // disallowed
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            KlipTransforms.from(
                parameters = params,
                rules = rules
            )
        }

        expectThat(exception.message?.contains("Horizontal flip is not allowed.")).isTrue()
        expectThat(exception.message?.contains("Dither is not allowed.")).isTrue()
    }
}
