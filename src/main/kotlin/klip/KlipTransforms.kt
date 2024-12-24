package klip

import io.ktor.http.Parameters

data class KlipTransforms(
    val path: String,
    var width: Int,
    var height: Int,
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

    fun validate(
        mode: ValidationMode,
        customRules: List<KlipTransformRule> = emptyList()
    ): KlipTransforms {
        val errors = mutableListOf<String>()

        // validate base (non-overrideable) rules
        validateBaseRules(errors, mode)

        // validate custom user rules
        for (rule in customRules) {
            if (!rule.isValid(this)) {
                if (mode == ValidationMode.STRICT) {
                    errors.add(rule.errorMessage(this))
                } else {
                    rule.clear(this) // nullify invalid values in LENIENT mode
                }
            }
        }

        // throw all errors in STRICT mode
        if (errors.isNotEmpty() && mode == ValidationMode.STRICT) {
            throw IllegalArgumentException(errors.joinToString(", "))
        }

        return this
    }

    private fun validateBaseRules(errors: MutableList<String>, mode: ValidationMode) {
        if (!KlipTransformRules.dimensionsGteZero.isValid(this)) {
            if (mode == ValidationMode.LENIENT) { // Default to minimum valid size
                width = 1
                height = 1
            }
            errors.add(KlipTransformRules.dimensionsGteZero.errorMessage(this))
        }
    }

    companion object {
        /**
         * Create KlipTransforms from parameters with optional rules and validation mode.
         */
        fun from(
            parameters: Parameters,
            mode: ValidationMode = ValidationMode.STRICT,
            rules: List<KlipTransformRule> = emptyList()
        ): KlipTransforms {
            // parse dimensions
            val size = parameters["size"] ?: ""
            val match = Regex("(\\d+)x(\\d+)").matchEntire(size)
            val width = match?.groups?.get(1)?.value?.toIntOrNull()
            val height = match?.groups?.get(2)?.value?.toIntOrNull()
            if (width == null || height == null) {
                throw IllegalArgumentException("Invalid dimension format, expected {width}x{height}")
            }

            val path = parameters.getAll("path")?.joinToString("/") ?: ""

            // parse flags (boolean parameters)
            val grayscale = isParamTrue("grayscale", parameters)
            val crop = isParamTrue("crop", parameters)
            val flipH = isParamTrue("flipH", parameters)
            val flipV = isParamTrue("flipV", parameters)
            val dither = isParamTrue("dither", parameters)

            // parse numeric values with strict validation
            val rotate = parseFloat("rotate", parameters, mode)
            val quality = parseInt("quality", parameters, mode)
            val sharpen = parseFloat("sharpen", parameters, mode)
            val colors = parseInt("colors", parameters, mode)

            // parse blur (supports "radius" or "{radius}x{sigma}")
            val blur = parameters["blur"]
            val (blurRadius, blurSigma) = parseBlur(blur, mode)

            // create and validate transforms
            return KlipTransforms(
                path = path,
                width = width,
                height = height,
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
            ).validate(mode, rules)
        }

        /**
         * Helper to parse float values with strict or lenient handling.
         */
        private fun parseFloat(key: String, parameters: Parameters, mode: ValidationMode): Float? {
            val value = parameters[key]
            return try {
                value?.toFloat()
            } catch (e: NumberFormatException) {
                if (mode == ValidationMode.STRICT) throw e
                null // nullify in lenient mode
            }
        }

        /**
         * pelper to parse integer values with strict or lenient handling.
         */
        private fun parseInt(key: String, parameters: Parameters, mode: ValidationMode): Int? {
            val value = parameters[key]
            return try {
                value?.toInt()
            } catch (e: NumberFormatException) {
                if (mode == ValidationMode.STRICT) throw e
                null // nullify in lenient mode
            }
        }

        /**
         * parse blur input (supports single and compound formats) with mode handling.
         */
        private fun parseBlur(blur: String?, mode: ValidationMode): Pair<Float?, Float?> {
            if (blur.isNullOrEmpty()) return null to null
            val parts = blur.split("x")

            return try {
                when (parts.size) {
                    2 -> parts[0].toFloat() to parts[1].toFloat()
                    1 -> {
                        val radius = parts[0].toFloat()
                        radius.let { it to it * 0.5f }
                    }

                    else -> null to null
                }
            } catch (e: NumberFormatException) {
                if (mode == ValidationMode.STRICT) throw e
                null to null // nullify in lenient mode
            }
        }

        /**
         * helper for boolean flags.
         */
        private fun isParamTrue(key: String, parameters: Parameters): Boolean {
            val value = parameters[key]
            return (key in parameters && parameters[key] == null) || // 0-arity parameters like ?flipV
                (value == "1" || value == "true")                   // 1-arity parameters like ?flipV=1 or ?flipV=true
        }
    }
}
