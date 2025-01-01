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
                throw GraphicsMagickException("GraphicsMagick operation timed out after ${config.timeoutSeconds} seconds")
            }

            if (process.exitValue() != 0) {
                logger.error("GraphicsMagick failed: $errorOutput")
                throw GraphicsMagickException("GraphicsMagick failed: $errorOutput")
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


    suspend fun createCanvas(t: KlipCanvasTransforms): ByteArray {
        return GraphicsMagickPool.withGraphicsMagick {
            createCanvasUnsafe(t)
        }
    }

    private fun createCanvasUnsafe(t: KlipCanvasTransforms): ByteArray {
        val tempDir = File("/tmp")
        val outputFile = File(tempDir, "gm_canvas_${UUID.randomUUID()}.png")

        try {
            val command = mutableListOf("gm", "convert")

            // Add safety limits
            command.addAll(listOf("-limit", "memory", config.memoryLimit))
            command.addAll(listOf("-limit", "map", config.mapLimit))
            command.addAll(listOf("-limit", "disk", config.diskLimit))

            // Base canvas creation
            command.add("-size")
            command.add("${t.width}x${t.height}")

            // Handle background (solid color or gradient)
            when {
                t.gradient != null -> {
                    val parts = t.gradient.split(",")
                    when {
                        parts.size == 2 -> {
                            command.add("gradient:${parts[0]}-${parts[1]}")
                        }
                        parts.size >= 3 -> {
                            val angle = parts[0].toIntOrNull() ?: 0
                            val colors = parts.drop(1)
                            command.add("gradient:${colors.first()}-${colors.last()}")
                            if (angle != 0) {
                                command.add("-rotate")
                                command.add(angle.toString())
                            }
                        }
                        else -> command.add("xc:${t.bgColor}")
                    }
                }
                t.pattern != null -> {
                    when (t.pattern) {
                        "check" -> {
                            val size = t.patternSize ?: 20
                            command.add("pattern:checkerboard")
                            command.add("-scale")
                            command.add("${size}x${size}!")
                        }
                        "grid" -> {
                            val size = t.patternSize ?: 40
                            command.add("xc:${t.bgColor}")
                            command.add("-fill")
                            command.add("none")
                            command.add("-stroke")
                            command.add("black")
                            command.add("-draw")
                            val draws = mutableListOf<String>()
                            // Vertical lines
                            (size..t.width step size).forEach { x ->
                                draws.add("line $x,0 $x,${t.height}")
                            }
                            // Horizontal lines
                            (size..t.height step size).forEach { y ->
                                draws.add("line 0,$y ${t.width},$y")
                            }
                            command.add(draws.joinToString(" "))
                        }
                        "stripe" -> {
                            val size = t.patternSize ?: 20
                            command.add("xc:${t.bgColor}")
                            command.add("-fill")
                            command.add("black")
                            command.add("-draw")
                            val draws = mutableListOf<String>()
                            (0..t.width step size).forEach { x ->
                                draws.add("line $x,0 ${x + size},${t.height}")
                            }
                            command.add(draws.joinToString(" "))
                        }
                        else -> command.add("xc:${t.bgColor}")
                    }
                }
                else -> command.add("xc:${t.bgColor}")
            }

            // Apply border if specified
            if (t.border != null && t.borderColor != null) {
                command.add("-bordercolor")
                command.add(t.borderColor)
                command.add("-border")
                command.add(t.border.toString())
            }

            // Add text if specified
            if (!t.text.isNullOrBlank()) {
                command.add("-gravity")
                command.add(t.textAlign ?: "center")
                command.add("-font")
                command.add(t.font ?: "Arial")
                command.add("-pointsize")
                command.add(t.textSize.toString())
                command.add("-fill")
                command.add(t.textColor)
                command.add("-draw")
                command.add("text 0,0 '${t.text}'")
            }

            // Apply shared transforms
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
                command.add("${t.blurRadius}x${t.blurSigma}")
            }

            if (t.sharpen != null) {
                command.add("-sharpen")
                command.add("0x${t.sharpen}")
            }

            if (t.colors != null) {
                command.add("-colors")
                command.add(t.colors.toString())
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

            // Capture errors
            val errorOutput = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { errorOutput.appendLine(it) }
            }

            val completed = process.waitFor(config.timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                throw GraphicsMagickException("GraphicsMagick operation timed out after ${config.timeoutSeconds} seconds")
            }

            if (process.exitValue() != 0) {
                throw GraphicsMagickException("GraphicsMagick failed: $errorOutput")
            }

            // If rounded corners are needed, apply them as a separate operation
            if (t.radius != null && t.radius > 0) {
                val maskedFile = File(tempDir, "gm_masked_${UUID.randomUUID()}.png")
                try {
                    // Create a mask with rounded corners
                    val maskCommand = mutableListOf("gm", "convert")
                    maskCommand.add("-size")
                    maskCommand.add("${t.width}x${t.height}")
                    maskCommand.add("xc:white")
                    maskCommand.add("-draw")
                    maskCommand.add("circle ${t.width/2},${t.height/2} ${t.width/2},0")
                    maskCommand.add(maskedFile.absolutePath)

                    // Execute mask creation
                    val maskProcess = ProcessBuilder(maskCommand)
                        .redirectErrorStream(true)
                        .start()

                    maskProcess.inputStream.bufferedReader().use { reader ->
                        reader.forEachLine { errorOutput.appendLine(it) }
                    }

                    if (!maskProcess.waitFor(config.timeoutSeconds, TimeUnit.SECONDS) ||
                        maskProcess.exitValue() != 0) {
                        throw GraphicsMagickException("Failed to create corner mask: $errorOutput")
                    }

                    // Compose the original image with the mask
                    val composeCommand = mutableListOf("gm", "composite")
                    composeCommand.add("-compose")
                    composeCommand.add("CopyOpacity")
                    composeCommand.add(maskedFile.absolutePath)
                    composeCommand.add(outputFile.absolutePath)
                    composeCommand.add(outputFile.absolutePath)

                    val composeProcess = ProcessBuilder(composeCommand)
                        .redirectErrorStream(true)
                        .start()

                    composeProcess.inputStream.bufferedReader().use { reader ->
                        reader.forEachLine { errorOutput.appendLine(it) }
                    }

                    if (!composeProcess.waitFor(config.timeoutSeconds, TimeUnit.SECONDS) ||
                        composeProcess.exitValue() != 0) {
                        throw GraphicsMagickException("Failed to apply corner mask: $errorOutput")
                    }

                } finally {
                    maskedFile.delete()
                }
            }

            return outputFile.readBytes()
        } finally {
            outputFile.delete()
        }
    }

}

class GraphicsMagickException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
