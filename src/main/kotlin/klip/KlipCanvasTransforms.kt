package klip

import io.ktor.http.Parameters
import io.ktor.server.plugins.BadRequestException

data class KlipCanvasTransforms(
    // Canvas-specific properties
    val width: Int,
    val height: Int,
    val bgColor: String = "gray",     // Background color or gradient
    val text: String? = null,         // Text to display
    val textColor: String = "white",  // Text color
    val textSize: Int = 20,          // Font size

    // New canvas-specific properties
    val pattern: String? = null,      // e.g., "grid", "check", "stripe"
    val patternSize: Int? = null,     // Size of pattern elements
    val gradient: String? = null,     // e.g., "blue,red" or "45,#ff0000,#0000ff"
    val font: String? = null,         // Font family
    val textAlign: String? = null,    // e.g., "center", "top-left", etc.
    val border: Int? = null,          // Border width
    val borderColor: String? = null,  // Border color
    val radius: Int? = null,          // Corner radius for rounded rectangles

    // Shared properties from KlipTransforms that make sense for canvas
    var grayscale: Boolean = false,
    var flipH: Boolean = false,
    var flipV: Boolean = false,
    var rotate: Float? = null,
    var quality: Int? = null,
    var sharpen: Float? = null,
    var colors: Int? = null,
    var blurRadius: Float? = null,
    var blurSigma: Float? = null
) {
    companion object {
        fun from(parameters: Parameters): KlipCanvasTransforms {
            // Parse dimensions from path parameter
            val dimensions = parameters["dimensions"] ?: throw BadRequestException("Dimensions required")
            if (!dimensions.matches(Regex("^\\d+x\\d+$"))) {
                throw BadRequestException("Invalid dimensions format. Expected: {width}x{height}")
            }
            val (width, height) = dimensions.split("x").map { it.toInt() }

            val blur = parameters["blur"]
            val (blurRadius, blurSigma) = KlipTransforms.parseBlur(blur)

            // Parse canvas-specific parameters
            return KlipCanvasTransforms(
                width = width,
                height = height,
                text = parameters["text"],
                bgColor = parameters["bgColor"] ?: "gray",
                textColor = parameters["textColor"] ?: "white",
                textSize = parameters["textSize"]?.toIntOrNull() ?: 20,
                pattern = parameters["pattern"],
                patternSize = parameters["patternSize"]?.toIntOrNull(),
                gradient = parameters["gradient"],
                font = parameters["font"],
                textAlign = parameters["align"],
                border = parameters["border"]?.toIntOrNull(),
                borderColor = parameters["borderColor"],
                radius = parameters["radius"]?.toIntOrNull(),

                // Shared transforms
                grayscale = KlipTransforms.isParamTrue("grayscale", parameters),
                flipH = KlipTransforms.isParamTrue("flipH", parameters),
                flipV = KlipTransforms.isParamTrue("flipV", parameters),
                rotate = KlipTransforms.parseFloat("rotate", parameters),
                quality = KlipTransforms.parseInt("quality", parameters),
                sharpen = KlipTransforms.parseFloat("sharpen", parameters),
                colors = KlipTransforms.parseInt("colors", parameters),
                blurRadius = blurRadius,
                blurSigma = blurSigma
            )
        }
    }
}
