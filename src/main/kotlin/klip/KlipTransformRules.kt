package klip

enum class ValidationMode {
    STRICT,  // throw errors on violations
    LENIENT  // nullify or reset invalid fields
}

data class ValidationRule(
    val isValid: (t: KlipTransforms) -> Boolean,
    val errorMessage: (t: KlipTransforms) -> String,
    val clear: (t: KlipTransforms) -> Unit
)

object KlipTransformRules {

    val dimensionsGteZero = ValidationRule(
        isValid = { it.width > 0 && it.height > 0 },
        errorMessage = { "Dimensions must be > 0. Got: ${it.width}x${it.height}" },
        clear = {
            it.width = 1
            it.height = 1
        }
    )

    fun allowedDimensions(allowed: Set<Pair<Int, Int>>) = ValidationRule(
        isValid = { (it.width to it.height) in allowed },
        errorMessage = { "Allowed dimensions are $allowed. Got: ${it.width}x${it.height}" },
        clear = {
            val default = allowed.firstOrNull() ?: (1 to 1)
            it.width = default.first
            it.height = default.second
        }
    )

    fun allowedQuality(allowed: Set<Int>) = ValidationRule(
        isValid = { it.quality == null || it.quality in allowed },
        errorMessage = { "Allowed qualities are $allowed. Got: ${it.quality}" },
        clear = {
            it.quality = null
        }
    )

    fun allowedColors(allowed: Set<Int>) = ValidationRule(
        isValid = { it.colors == null || it.colors in allowed },
        errorMessage = { "Allowed colors are $allowed. Got: ${it.colors}" },
        clear = {
            it.colors = null
        }
    )

    fun allowedRotate(allowed: Set<Float>) = ValidationRule(
        isValid = { it.rotate == null || it.rotate in allowed },
        errorMessage = { "Allowed rotations are $allowed. Got: ${it.rotate}" },
        clear = {
            it.rotate = null
        }
    )

    fun allowSharpen(allowed: Set<Float>) = ValidationRule(
        isValid = { it.sharpen == null || it.sharpen in allowed },
        errorMessage = { "Allowed sharpen values are $allowed. Got: ${it.sharpen}" },
        clear = {
            it.sharpen = null
        }
    )

    fun allowedBlurRadius(allowedRadii: Set<Float>) = ValidationRule(
        isValid = { it.blurRadius == null || it.blurRadius in allowedRadii },
        errorMessage = { "Allowed blur radii are $allowedRadii. Got: ${it.blurRadius}" },
        clear = {
            it.blurRadius = null
            it.blurSigma = null
        }
    )

    fun allowedBlurSigma(allowedSigmas: Set<Float>) = ValidationRule(
        isValid = { it.blurSigma == null || it.blurSigma in allowedSigmas },
        errorMessage = { "Allowed blur sigmas are $allowedSigmas. Got: ${it.blurSigma}" },
        clear = {
            it.blurRadius = null
            it.blurSigma = null
        }
    )

    fun allowedGrayscale(allowed: Boolean) = ValidationRule(
        isValid = { allowed || !it.grayscale },
        errorMessage = { "Grayscale is not allowed." },
        clear = { it.grayscale = false }
    )

    fun allowedCrop(allowed: Boolean) = ValidationRule(
        isValid = { allowed || !it.crop },
        errorMessage = { "Crop is not allowed." },
        clear = { it.crop = false }
    )

    fun allowedFlipH(allowed: Boolean) = ValidationRule(
        isValid = { allowed || !it.flipH },
        errorMessage = { "Horizontal flip is not allowed." },
        clear = { it.flipH = false }
    )

    fun allowedFlipV(allowed: Boolean) = ValidationRule(
        isValid = { allowed || !it.flipV },
        errorMessage = { "Vertical flip is not allowed." },
        clear = { it.flipV = false }
    )

    fun allowedDither(allowed: Boolean) = ValidationRule(
        isValid = { allowed || !it.dither },
        errorMessage = { "Dither is not allowed." },
        clear = { it.dither = false }
    )
}
