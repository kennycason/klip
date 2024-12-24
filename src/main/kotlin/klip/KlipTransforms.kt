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
        customRules: List<ValidationRule> = emptyList()
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
        if (!Rules.dimensionRule.isValid(this)) {
            if (mode == ValidationMode.LENIENT) { // Default to minimum valid size
                width = 1
                height = 1
            }
            errors.add(Rules.dimensionRule.errorMessage(this))
        }

        if (!Rules.qualityRule.isValid(this)) {
            if (mode == ValidationMode.LENIENT) quality = null
            errors.add(Rules.qualityRule.errorMessage(this))
        }

        if (!Rules.blurRule.isValid(this)) {
            if (mode == ValidationMode.LENIENT) {
                blurRadius = null
                blurSigma = null
            }
            errors.add(Rules.blurRule.errorMessage(this))
        }
    }

    companion object {
        fun from(parameters: Parameters): KlipTransforms {
            val size = parameters["size"] ?: ""
            val match = Regex("(\\d+)x(\\d+)").matchEntire(size)
            val width = match?.groups?.get(1)?.value?.toIntOrNull()
            val height = match?.groups?.get(2)?.value?.toIntOrNull()

            val path = parameters.getAll("path")?.joinToString("/") ?: ""

            val grayscale = isParamTrue("grayscale", parameters)
            val crop = isParamTrue("crop", parameters)
            val flipH = isParamTrue("flipH", parameters)
            val flipV = isParamTrue("flipV", parameters)
            val dither = isParamTrue("dither", parameters)

            val rotate = parameters["rotate"]?.toFloatOrNull()
            val quality = parameters["quality"]?.toIntOrNull()
            val sharpen = parameters["sharpen"]?.toFloatOrNull()
            val colors = parameters["colors"]?.toIntOrNull()

            val blur = parameters["blur"]
            val (blurRadius, blurSigma) = parseBlur(blur)

            requireNotNull(width) { "Missing width in size parameter." }
            requireNotNull(height) { "Missing height in size parameter." }

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
            )
        }

        private fun parseBlur(blur: String?): Pair<Float?, Float?> {
            if (blur.isNullOrEmpty()) return null to null
            val parts = blur.split("x")
            return when (parts.size) {
                2 -> parts[0].toFloatOrNull() to parts[1].toFloatOrNull()
                1 -> {
                    val radius = parts[0].toFloatOrNull()
                    radius?.let { it to it * 0.5f } ?: (null to null)
                }

                else -> null to null
            }
        }

        private fun isParamTrue(key: String, parameters: Parameters): Boolean {
            val value = parameters[key]
            return (key in parameters && parameters[key] == null) ||
                (value == "1" || value == "true")
        }
    }
}

enum class ValidationMode {
    STRICT,  // throw errors on violations
    LENIENT  // nullify or reset invalid fields
}

data class ValidationRule(
    val isValid: (t: KlipTransforms) -> Boolean,
    val errorMessage: (t: KlipTransforms) -> String,
    val clear: (t: KlipTransforms) -> Unit
)

object Rules {

    val dimensionRule = ValidationRule(
        isValid = { it.width > 0 && it.height > 0 },
        errorMessage = { "Dimensions must be > 0. Got: ${it.width}x${it.height}" },
        clear = {
            it.width = 1
            it.height = 1
        }
    )

    val qualityRule = ValidationRule(
        isValid = { it.quality == null || (it.quality in 1..100) },
        errorMessage = { "Quality must be between 1 and 100. Got: ${it.quality}" },
        clear = {
            it.quality = null
        }
    )

    val blurRule = ValidationRule(
        isValid = {
            (it.blurRadius == null || it.blurRadius!! >= 0) &&
                (it.blurSigma == null || it.blurSigma!! >= 0)
        },
        errorMessage = { "Blur must have radius >= 0 and sigma >= 0. Got: ${it.blurRadius}x${it.blurSigma}" },
        clear = {
            it.blurRadius = null
            it.blurSigma = null
        }
    )
}
