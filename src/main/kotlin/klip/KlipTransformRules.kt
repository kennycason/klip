package klip

enum class ValidationMode {
    STRICT,  // throw errors on violations
    LENIENT  // nullify or reset invalid fields
}

data class KlipTransformRule(
    val name: String,
    val isValid: (t: KlipTransforms) -> Boolean,
    val errorMessage: (t: KlipTransforms) -> String,
    val clear: (t: KlipTransforms) -> Unit
) {
    override fun toString(): String = name
}

object KlipTransformRules {

    val dimensionsGteZero = KlipTransformRule(
        name = "dim gte0",
        isValid = { it.width > 0 && it.height > 0 },
        errorMessage = { "Dimensions must be > 0. Got: ${it.width}x${it.height}" },
        clear = {
            it.width = 1
            it.height = 1
        }
    )

    fun allowedDimensions(allowed: Set<Pair<Int, Int>>) = KlipTransformRule(
        name = "dim ${allowed.joinToString(" ") { "${it.first}x${it.second}" }}",
        isValid = { (it.width to it.height) in allowed },
        errorMessage = { "Allowed dimensions are $allowed. Got: ${it.width}x${it.height}" },
        clear = {
            val default = allowed.firstOrNull() ?: (1 to 1)
            it.width = default.first
            it.height = default.second
        }
    )

    fun allowedQuality(allowed: Set<Int>) = KlipTransformRule(
        name = "quality ${allowed.joinToString(" ")}",
        isValid = { it.quality == null || it.quality in allowed },
        errorMessage = { "Allowed qualities are $allowed. Got: ${it.quality}" },
        clear = {
            it.quality = null
        }
    )

    fun allowedColors(allowed: Set<Int>) = KlipTransformRule(
        name = "colors ${allowed.joinToString(" ")}",
        isValid = { it.colors == null || it.colors in allowed },
        errorMessage = { "Allowed colors are $allowed. Got: ${it.colors}" },
        clear = {
            it.colors = null
        }
    )

    fun allowedRotate(allowed: Set<Float>) = KlipTransformRule(
        name = "rotate ${allowed.joinToString(" ")}",
        isValid = { it.rotate == null || it.rotate in allowed },
        errorMessage = { "Allowed rotations are $allowed. Got: ${it.rotate}" },
        clear = {
            it.rotate = null
        }
    )

    fun allowedSharpen(allowed: Set<Float>) = KlipTransformRule(
        name = "sharpen ${allowed.joinToString(" ")}",
        isValid = { it.sharpen == null || it.sharpen in allowed },
        errorMessage = { "Allowed sharpen values are $allowed. Got: ${it.sharpen}" },
        clear = {
            it.sharpen = null
        }
    )

    fun allowedBlurRadius(allowed: Set<Float>) = KlipTransformRule(
        name = "blurRadius ${allowed.joinToString(" ")}",
        isValid = { it.blurRadius == null || it.blurRadius in allowed },
        errorMessage = { "Allowed blur radii are $allowed. Got: ${it.blurRadius}" },
        clear = {
            it.blurRadius = null
            it.blurSigma = null
        }
    )

    fun allowedBlurSigma(allowed: Set<Float>) = KlipTransformRule(
        name = "blurSigma ${allowed.joinToString(" ")}",
        isValid = { it.blurSigma == null || it.blurSigma in allowed },
        errorMessage = { "Allowed blur sigmas are $allowed. Got: ${it.blurSigma}" },
        clear = {
            it.blurRadius = null
            it.blurSigma = null
        }
    )

    fun allowedGrayscale(allowed: Boolean) = KlipTransformRule(
        name = if (allowed) "+grayscale" else "-grayscale",
        isValid = { allowed || !it.grayscale },
        errorMessage = { "Grayscale is not allowed." },
        clear = { it.grayscale = false }
    )

    fun allowedCrop(allowed: Boolean) = KlipTransformRule(
        name = if (allowed) "+crop" else "-crop",
        isValid = { allowed || !it.crop },
        errorMessage = { "Crop is not allowed." },
        clear = { it.crop = false }
    )

    fun allowedFlipH(allowed: Boolean) = KlipTransformRule(
        name = if (allowed) "+flipH" else "-flipH",
        isValid = { allowed || !it.flipH },
        errorMessage = { "Horizontal flip is not allowed." },
        clear = { it.flipH = false }
    )

    fun allowedFlipV(allowed: Boolean) = KlipTransformRule(
        name = if (allowed) "+flipV" else "-flipV",
        isValid = { allowed || !it.flipV },
        errorMessage = { "Vertical flip is not allowed." },
        clear = { it.flipV = false }
    )

    fun allowedDither(allowed: Boolean) = KlipTransformRule(
        name = if (allowed) "+dither" else "-dither",
        isValid = { allowed || !it.dither },
        errorMessage = { "Dither is not allowed." },
        clear = { it.dither = false }
    )

    fun parseRules(rulesConfig: String): List<KlipTransformRule> {
        val rules = mutableListOf<KlipTransformRule>()
        val allowedDimensions = mutableSetOf<Pair<Int, Int>>()
        val allowedBlur = mutableSetOf<Float>()
        val allowedQuality = mutableSetOf<Int>()
        val allowedRotate = mutableSetOf<Float>()
        val allowedSharpen = mutableSetOf<Float>()

        rulesConfig
            .replace("\n", ";")
            .split(";")
            .filter { it.isNotBlank() }
            .filter { !it.startsWith("#") } // allow comments
            .forEach { rule ->
                val trimmedRule = rule.trim()
                when {
                    // allow or disallow boolean features
                    trimmedRule == "+flipV" -> rules.add(allowedFlipV(true))
                    trimmedRule == "-flipV" -> rules.add(allowedFlipV(false))
                    trimmedRule == "+flipH" -> rules.add(allowedFlipH(true))
                    trimmedRule == "-flipH" -> rules.add(allowedFlipH(false))
                    trimmedRule == "+crop" -> rules.add(allowedCrop(true))
                    trimmedRule == "-crop" -> rules.add(allowedCrop(false))
                    trimmedRule == "+grayscale" -> rules.add(allowedGrayscale(true))
                    trimmedRule == "-grayscale" -> rules.add(allowedGrayscale(false))
                    trimmedRule == "+dither" -> rules.add(allowedDither(true))
                    trimmedRule == "-dither" -> rules.add(allowedDither(false))

                    // dimensions rule
                    trimmedRule.startsWith("dim") -> {
                        val dimensions = trimmedRule.removePrefix("dim").trim().split(" ")
                        dimensions.forEach {
                            val (w, h) = it.split("x").map { it.toInt() }
                            allowedDimensions.add(w to h)
                        }
                        rules.add(allowedDimensions(allowedDimensions))
                    }

                    // blur rule
                    trimmedRule.startsWith("blur") -> {
                        val radii = trimmedRule.removePrefix("blur").trim().split(" ").map { it.toFloat() }
                        allowedBlur.addAll(radii)
                        rules.add(allowedBlurRadius(allowedBlur))
                    }

                    // quality rule
                    trimmedRule.startsWith("quality") -> {
                        val qualities = trimmedRule.removePrefix("quality").trim().split(" ").map { it.toInt() }
                        allowedQuality.addAll(qualities)
                        rules.add(allowedQuality(allowedQuality))
                    }

                    // rotate rule
                    trimmedRule.startsWith("rotate") -> {
                        val angles = trimmedRule.removePrefix("rotate").trim().split(" ").map { it.toFloat() }
                        allowedRotate.addAll(angles)
                        rules.add(allowedRotate(allowedRotate))
                    }

                    // sharpen rule
                    trimmedRule.startsWith("sharpen") -> {
                        val sharpens = trimmedRule.removePrefix("sharpen").trim().split(" ").map { it.toFloat() }
                        allowedSharpen.addAll(sharpens)
                        rules.add(allowedSharpen(allowedSharpen))
                    }
                }
            }
        return rules
    }
}
