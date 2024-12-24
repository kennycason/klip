package klip

import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.util.UUID

object GraphicsMagickImageProcessor {

    fun processImage(
        image: KlipImage,
        t: KlipTransforms
    ): ByteArray {
        val tempDir = File("/tmp")
        val inputFile = File(tempDir, "gm_input_${UUID.randomUUID()}.${image.extension}")
        val outputFile = File(tempDir, "gm_output_${UUID.randomUUID()}.${image.extension}")
        logger.debug { "Input file: ${inputFile.absolutePath}, Output file: ${outputFile.absolutePath}" }

        inputFile.writeBytes(image.data)

        try {
            val command = mutableListOf("gm", "convert")
            command.add(inputFile.absolutePath)

            // crop before resize
            if (t.crop) {
                command.add("-gravity")
                command.add("center")
                command.add("-crop")
                command.add("${t.width}x${t.height}+0+0")
            }

            command.add("-resize")
            command.add("${t.width}x${t.height}")

            if (t.grayscale) {
                command.add("-colorspace")
                command.add("Gray")
            }

            if (t.flipH) command.add("-flop")
            if (t.flipV) command.add("-flip")

            if (t.rotate != null) {
                command.add("-rotate")
                command.add(t.rotate.toString())
            }

            if (t.blurRadius != null && t.blurSigma != null) {
                command.add("-blur")
                command.add("${t.blurRadius}x${t.blurSigma}") // Format: 0x<sigma>
            }

            if (t.sharpen != null) {
                command.add("-sharpen")
                command.add("0x${t.sharpen}") // Format: 0x<radius>
            }

            if (t.colors != null) {
                command.add("-colors")
                command.add(t.colors.toString())
            }

            if (t.dither) {
                command.add("-dither")
            }

            if (t.quality != null) {
                command.add("-quality")
                command.add(t.quality.toString())
            }

            command.add(outputFile.absolutePath)

            logger.debug { "GraphicsMagick cmd: $command" }

            val process = ProcessBuilder(command).redirectErrorStream(true).start()

            // capture errors for debugging
            val errorOutput = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { errorOutput.appendLine(it) }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.error("GraphicsMagick failed: $errorOutput")
                throw RuntimeException("GraphicsMagick failed: $errorOutput")
            }

            return outputFile.readBytes()

        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }
}
