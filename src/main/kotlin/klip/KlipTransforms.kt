package klip

import io.ktor.http.Parameters
import io.ktor.server.plugins.BadRequestException

enum class Fit {
    FILL,      // Stretch to fill exact dimensions (default)
    CONTAIN,   // Fit within bounds while maintaining aspect ratio
    COVER      // Fill bounds and crop excess while maintaining aspect ratio
}

data class KlipTransforms(
    val path: String,
    var width: Int? = null,
    var height: Int? = null,
    var fit: Fit? = null,
    var grayscale: Boolean = false,
    var crop: Boolean = false,
    var flipH: Boolean = false,
    var flipV: Boolean = false,
    var dither: Boolean = false,
    var rotate: Float? = null,
    var quality: Int? = null,
    var sharpen: Float? = null,
    var colors: Int? = null,
    var blurRadius: Float? = null,
    var blurSigma: Float? = null
) {
    fun isEmpty(): Boolean {
        return width == null &&
            height == null &&
            fit == null &&
            !grayscale &&
            !crop &&
            !flipH &&
            !flipV &&
            !dither &&
            rotate == null &&
            quality == null &&
            sharpen == null &&
            colors == null &&
            blurRadius == null &&
            blurSigma == null
    }

    fun validate(rules: List<KlipTransformRule> = emptyList()): KlipTransforms {
        val errors = mutableListOf<String>()

        // basic validation
        if (width != null && width!! < 0) errors.add("Width must be >= 0")
        if (height != null && height!! < 0) errors.add("Height must be >= 0")

        // apply custom rules
        for (rule in rules) {
            if (!rule.isValid(this)) {
                errors.add(rule.errorMessage(this))
            }
        }

        if (errors.isNotEmpty()) {
            throw BadRequestException(errors.joinToString(", "))
        }
        return this
    }

    companion object {
        /**
         * Create KlipTransforms from parameters with optional rules.
         */
        fun from(
            parameters: Parameters
        ): KlipTransforms {
            val path = parameters.getAll("path")?.joinToString("/") ?: ""

            val (width, height) = parseDimensions(parameters)

            // fit parsing
            val fit = parameters["fit"]?.let {
                try {
                    Fit.valueOf(it.uppercase())
                } catch (e: IllegalArgumentException) {
                    throw BadRequestException("Fit must be [${Fit.entries.map { it.name.lowercase() }.joinToString(",")}]")
                }
            }

            val crop = isParamTrue("crop", parameters)
            if (crop && (width == null || height == null)) {
                throw BadRequestException("Crop requires both width and height.")
            }

            // parse flags (boolean parameters)
            val grayscale = isParamTrue("grayscale", parameters)
            val flipH = isParamTrue("flipH", parameters)
            val flipV = isParamTrue("flipV", parameters)
            val dither = isParamTrue("dither", parameters)

            // parse numeric values with strict validation
            val rotate = parseFloat("rotate", parameters)
            val quality = parseInt("quality", parameters)
            val sharpen = parseFloat("sharpen", parameters)
            val colors = parseInt("colors", parameters)

            // parse blur (supports "radius" or "{radius}x{sigma}")
            val blur = parameters["blur"]
            val (blurRadius, blurSigma) = parseBlur(blur)

            // create and validate transforms
            return KlipTransforms(
                path = path,
                width = width,
                height = height,
                fit = fit,
                grayscale = grayscale,
                crop = crop,
                flipH = flipH,
                flipV = flipV,
                dither = dither,
                rotate = rotate,
                quality = quality,
                sharpen = sharpen,
                colors = colors,
                blurRadius = blurRadius,
                blurSigma = blurSigma
            )
        }

        fun parseDimensions(parameters: Parameters): Pair<Int?, Int?> {
            // check for conflicting parameters first
            if (parameters.contains("d") && (parameters.contains("w") || parameters.contains("h"))) {
                throw BadRequestException("Conflicting dimensions specified. Use either 'w'/'h' or 'd', not both.")
            }

            // try parsing "d" first
            parameters["d"]?.let { dim ->
                val parts = dim.split("x").map { it.toIntOrNull() }
                if (parts.size == 2 && parts[0] != null && parts[1] != null) {
                    return parts[0]!! to parts[1]!!
                } else {
                    throw BadRequestException("Invalid dimension format, expected {width}x{height}")
                }
            }

            // try parsing "w" and "h"
            val width = parameters["w"]?.toIntOrNull()
            val height = parameters["h"]?.toIntOrNull()
            return width to height
        }

        /**
         * Helper to parse float values.
         */
        fun parseFloat(key: String, parameters: Parameters): Float? {
            val value = parameters[key]
            return try {
                value?.toFloat()
            } catch (e: NumberFormatException) {
                throw BadRequestException("Failed to parse decimal for key $key=$value")
            }
        }

        /**
         * Helper to parse integer values.
         */
        fun parseInt(key: String, parameters: Parameters): Int? {
            val value = parameters[key]
            return try {
                value?.toInt()
            } catch (e: NumberFormatException) {
                throw BadRequestException("Failed to parse integer for key $key=$value")
            }
        }

        /**
         * parse blur input (supports single and compound formats).
         */
        fun parseBlur(blur: String?): Pair<Float?, Float?> {
            if (blur.isNullOrEmpty()) return null to null
            val parts = blur.split("x")

            return try {
                when (parts.size) {
                    2 -> parts[0].toFloat() to parts[1].toFloat()
                    1 -> {
                        val radius = parts[0].toFloat()
                        radius.let { it to it * 0.5f }
                    }

                    else -> throw BadRequestException("Failed to parse blur. blur=[1,10] or blur={radius}x{sigma}")
                }
            } catch (e: NumberFormatException) {
                throw BadRequestException("Failed to parse blur. blur=[1,10] or blur={radius}x{sigma}")
            }
        }

        /**
         * helper for boolean flags.
         */
        fun isParamTrue(key: String, parameters: Parameters): Boolean {
            val value = parameters[key]
            return (key in parameters && parameters[key] == null) || // 0-arity parameters like ?flipV
                (value == "1" || value == "true")                    // 1-arity parameters like ?flipV=1 or ?flipV=true
        }
    }
}
