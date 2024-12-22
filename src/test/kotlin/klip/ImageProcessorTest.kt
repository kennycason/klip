package klip

import io.ktor.http.ContentType
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test

class ImageProcessorTest {

    @Test
    fun `should resize image correctly`() {
        val testImage = KlipImage(createTestImage(), ContentType.Image.PNG)

        val result = ImageProcessor.processImage(
            image = testImage,
            width = 50,
            height = 50,
            grayscale = false,
            crop = false,
            angle = null
        )

        val outputImage = ImageIO.read(result.inputStream())

        expectThat(outputImage.width).isEqualTo(50)
        expectThat(outputImage.height).isEqualTo(50)
    }

    @Test
    fun `should apply grayscale filter`() {
        val testImage = KlipImage(createTestImage(), ContentType.Image.PNG)

        val result = ImageProcessor.processImage(
            image = testImage,
            width = 100,
            height = 100,
            grayscale = true,
            crop = false,
            angle = null
        )

        val outputImage = ImageIO.read(result.inputStream())

        expectThat(outputImage.colorModel.numColorComponents).isEqualTo(1) // grayscale has 1 color component
    }

    @Test
    fun `should rotate image correctly`() {
        val width = 200
        val height = 100
        val testImage = KlipImage(createTestImage(width, height), ContentType.Image.PNG)

        val result = ImageProcessor.processImage(
            image = testImage,
            width = width,
            height = height,
            grayscale = false,
            crop = false,
            angle = 90f
        )

        val outputImage = ImageIO.read(result.inputStream())

        // rotating by 90 degrees swaps width and height
        expectThat(outputImage.width).isEqualTo(height)
        expectThat(outputImage.height).isEqualTo(width)
    }

    private fun createTestImage(width: Int = 100, height: Int = 100, format: String = "png"): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.fillRect(0, 0, width, height)
        g.dispose()
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, format, outputStream)
        return outputStream.toByteArray()
    }
}
