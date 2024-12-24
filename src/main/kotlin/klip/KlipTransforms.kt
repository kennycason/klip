package klip

import io.ktor.http.Parameters

data class KlipTransforms(
    val path: String,
    val width: Int,
    val height: Int,
    val grayscale: Boolean = false,
    val crop: Boolean = false,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val dither: Boolean = false,
    val rotate: Float? = null,
    val quality: Int? = null,
    val sharpen: Float? = null,
    val colors: Int? = null,
    val blurRadius: Float? = null,
    val blurSigma: Float? = null
) {

    fun verify() {
        require(width > 0 && height > 0) { "Invalid width or height: ${width}x${height}" }
        require(quality == null || (quality in 1..100)) { "quality must be between 1 and 100." }
        require(sharpen == null || sharpen >= 0) { "sharpen must be >= 0." }
        require(colors == null || colors in 2..256) { "colors must be between 2 and 256." }
        require(blurRadius == null || blurRadius >= 0) { "blur radius must be >= 0." }
        require(blurSigma == null || blurSigma >= 0) { "blur sigma must be >= 0." }
    }

    companion object {
        fun from(parameters: Parameters): KlipTransforms {
            // extract size
            val size = parameters["size"] ?: ""
            val match = Regex("(\\d+)x(\\d+)").matchEntire(size)
            val width = match?.groups?.get(1)?.value?.toIntOrNull()
            val height = match?.groups?.get(2)?.value?.toIntOrNull()

            val path = parameters.getAll("path")?.joinToString("/") ?: ""

            // parse boolean flags
            val grayscale = isParamTrue("grayscale", parameters)
            val crop = isParamTrue("crop", parameters)
            val flipH = isParamTrue("flipH", parameters)
            val flipV = isParamTrue("flipV", parameters)
            val dither = isParamTrue("dither", parameters)

            // parse numeric parameters
            val rotate = parameters["rotate"]?.toFloatOrNull()
            val quality = parameters["quality"]?.toIntOrNull()
            val sharpen = parameters["sharpen"]?.toFloatOrNull()
            val colors = parameters["colors"]?.toIntOrNull()

            // handle blur (supports "radius" or "{radius}x{sigma}")
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
            ).apply { verify() } // Validate on creation
        }

        /**
         * Parse blur input (supports single and compound formats).
         */
        private fun parseBlur(blur: String?): Pair<Float?, Float?> {
            if (blur.isNullOrEmpty()) return null to null
            val parts = blur.split("x")

            return when (parts.size) {
                2 -> parts[0].toFloatOrNull() to parts[1].toFloatOrNull()
                1 -> {
                    val radius = parts[0].toFloatOrNull()
                    radius?.let { Pair(it, it * 0.5f) } ?: (null to null) // Default to nulls if parsing fails
                }
                else -> null to null
            }
        }

        /**
         * Check if parameter is a valid boolean flag.
         */
        private fun isParamTrue(key: String, parameters: Parameters): Boolean {
            val value = parameters[key]
            return (key in parameters && parameters[key] == null) ||    // 0-arity parameters like ?flipV
                (value == "1" || value == "true")                       // 1-arity parameters like?flipV=1 or ?flipV=true
        }
    }
}
