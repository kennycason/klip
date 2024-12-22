package klip.image

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun BufferedImage.toByteArray(format: String): ByteArray {
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(this, format, outputStream)
    return outputStream.toByteArray()
}
