package klip

import io.ktor.server.plugins.BadRequestException
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

object GraphicsMagickImageProcessor {
    private lateinit var config: Env.GraphicsMagick

    fun initialize(config: Env.GraphicsMagick) {
        this.config = config
    }

    suspend fun processImage(
        image: KlipImage,
        t: KlipTransforms
    ): ByteArray {
        return GraphicsMagickPool.withGraphicsMagick {
            processImageUnsafe(image, t)
        }
    }

    private fun processImageUnsafe(
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

            // Add safety limits
            command.addAll(listOf("-limit", "memory", config.memoryLimit))
            command.addAll(listOf("-limit", "map", config.mapLimit))
            command.addAll(listOf("-limit", "disk", config.diskLimit))

            // Single-threaded mode for writing
            command.addAll(listOf("-define", "thread:mode=single"))

            command.add(inputFile.absolutePath)

            // crop before resize
            if (t.crop) {
                command.add("-gravity")
                command.add("center")
                command.add("-crop")
                command.add("${t.width?:""}x${t.height?:""}+0+0")
            }

            // apply resize logic with fit modes
            applyResize(command, t)

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

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            // capture errors for debugging
            val errorOutput = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { errorOutput.appendLine(it) }
            }

            // add timeout
            val completed =  process.waitFor(config.timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                throw RuntimeException("GraphicsMagick operation timed out after ${config.timeoutSeconds} seconds")
            }

            if (process.exitValue() != 0) {
                logger.error("GraphicsMagick failed: $errorOutput")
                throw RuntimeException("GraphicsMagick failed: $errorOutput")
            }

            return outputFile.readBytes()

        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }

    /**
     * apply resize transformations with "fit" options.
     * ?w=800&h=600 (null fit, both dimensions) → FILL behavior (-resize 800x600!)
     * ?w=800 (null fit, one dimension) → CONTAIN behavior (-resize 800x)
     * ?w=800&fit=contain → CONTAIN behavior
     * ?w=800&h=600&fit=fill → FILL behavior
     * ?w=800&h=600&fit=cover → COVER behavior
     */
    private fun applyResize(command: MutableList<String>, t: KlipTransforms) {
        // ensure at least one dimension is specified
        if (t.width == null && t.height == null) {
            if (t.fit != null) {
                throw BadRequestException("fit=${t.fit} requires at least one dimension: w or h.")
            }
            return // skip resize if neither dimension is set and fit is null
        }

        when (t.fit) {
            Fit.CONTAIN -> {
                command.add("-resize")
                command.add("${t.width ?: ""}x${t.height ?: ""}")
            }
            Fit.COVER -> {
                if (t.width == null || t.height == null) {
                    throw BadRequestException("Cover mode requires both width and height")
                }
                command.add("-resize")
                command.add("${t.width}x${t.height}^")
                command.add("-gravity")
                command.add("center")
                command.add("-extent")
                command.add("${t.width}x${t.height}")
            }
            Fit.FILL -> {
                if (t.width == null || t.height == null) {
                    throw BadRequestException("Fill mode requires both width and height")
                }
                command.add("-resize")
                command.add("${t.width}x${t.height}!")
            }
            null -> {
                // If both dimensions specified, use FILL behavior
                if (t.width != null && t.height != null) {
                    command.add("-resize")
                    command.add("${t.width}x${t.height}!")
                } else {
                    // If single dimension, use CONTAIN behavior
                    command.add("-resize")
                    command.add("${t.width ?: ""}x${t.height ?: ""}")
                }
            }
        }
    }



    suspend fun createPlaceholder(
        width: Int,
        height: Int,
        text: String? = null,
        bgColor: String = "gray",
        textColor: String = "white",
        textSize: Int = 20
    ): ByteArray {
        return GraphicsMagickPool.withGraphicsMagick {
            createPlaceholderUnsafe(width, height, text, bgColor, textColor, textSize)
        }
    }

    private fun createPlaceholderUnsafe(
        width: Int,
        height: Int,
        text: String?,
        bgColor: String,
        textColor: String,
        textSize: Int
    ): ByteArray {
        val tempDir = File("/tmp")
        val outputFile = File(tempDir, "gm_placeholder_${UUID.randomUUID()}.png")

        try {
            val command = mutableListOf("gm", "convert")

            // Create blank canvas with background color
            command.add("-size")
            command.add("${width}x${height}")
            command.add("xc:$bgColor")

            // If text is provided, add it
            if (!text.isNullOrBlank()) {
                command.add("-gravity")
                command.add("center")
                command.add("-font")
                command.add("Arial") // or another available font
                command.add("-pointsize")
                command.add(textSize.toString())
                command.add("-fill")
                command.add(textColor)
                command.add("-draw")
                command.add("text 0,0 '$text'")  // Changed from -annotate to -draw
            }

            // Add safety limits from config
            command.addAll(listOf("-limit", "memory", config.memoryLimit))
            command.addAll(listOf("-limit", "map", config.mapLimit))
            command.addAll(listOf("-limit", "disk", config.diskLimit))

            command.add(outputFile.absolutePath)

            logger.debug { "GraphicsMagick cmd: $command" }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            // Capture errors
            val errorOutput = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { errorOutput.appendLine(it) }
            }

            val completed = process.waitFor(config.timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                throw RuntimeException("GraphicsMagick operation timed out after ${config.timeoutSeconds} seconds")
            }

            if (process.exitValue() != 0) {
                throw RuntimeException("GraphicsMagick failed: $errorOutput")
            }

            return outputFile.readBytes()
        } finally {
            outputFile.delete()
        }
    }
}
