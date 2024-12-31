package klip

data class KlipTransformRule(
    val name: String,
    val isValid: (t: KlipTransforms) -> Boolean,
    val errorMessage: (t: KlipTransforms) -> String,
) {
    override fun toString(): String = name
}

object KlipTransformRules {

    val dimensionGteZero = KlipTransformRule(
        name = "dim gte0",
        isValid = {
            (it.width != null && it.width!! > 0) && (it.height != null && it.height!! > 0)
        },
        errorMessage = { "Dimensions must be > 0. Got: ${it.width}x${it.height}" }
    )

    fun allowedDimensions(allowed: List<Pair<Int, Int>>) = KlipTransformRule(
        name = "dim ${allowed.joinToString(" ") { "${it.first}x${it.second}" }}",
        isValid = { (it.width to it.height) in allowed },
        errorMessage = { "Allowed dim: ${allowed.joinToString(" ") { "${it.first}x${it.second}" }}. Got: ${it.width}x${it.height}" }
    )

    fun allowedWidth(allowed: List<Int>) = KlipTransformRule(
        name = "width ${allowed.joinToString(" ")}",
        isValid = { it.width in allowed },
        errorMessage = { "Allowed width: $allowed. Got: ${it.width}" }
    )

    fun allowedHeight(allowed: List<Int>) = KlipTransformRule(
        name = "height ${allowed.joinToString(" ")}",
        isValid = { it.height in allowed },
        errorMessage = { "Allowed height: $allowed. Got: ${it.height}" }
    )

    fun allowedQuality(allowed: List<Int>) = KlipTransformRule(
        name = "quality ${allowed.joinToString(" ")}",
        isValid = { it.quality == null || it.quality in allowed },
        errorMessage = { "Allowed quality: $allowed. Got: ${it.quality}" }
    )

    fun allowedColor(allowed: List<Int>) = KlipTransformRule(
        name = "colors ${allowed.joinToString(" ")}",
        isValid = { it.colors == null || it.colors in allowed },
        errorMessage = { "Allowed color: $allowed. Got: ${it.colors}" }
    )

    fun allowedRotate(allowed: List<Float>) = KlipTransformRule(
        name = "rotate ${allowed.joinToString(" ")}",
        isValid = { it.rotate == null || it.rotate in allowed },
        errorMessage = { "Allowed rotation: $allowed. Got: ${it.rotate}" }
    )

    fun allowedSharpen(allowed: List<Float>) = KlipTransformRule(
        name = "sharpen ${allowed.joinToString(" ")}",
        isValid = { it.sharpen == null || it.sharpen in allowed },
        errorMessage = { "Allowed sharpen: $allowed. Got: ${it.sharpen}" },
    )

    fun allowedBlurRadius(allowed: List<Float>) = KlipTransformRule(
        name = "blurRadius ${allowed.joinToString(" ")}",
        isValid = { it.blurRadius == null || it.blurRadius in allowed },
        errorMessage = { "Allowed blurRadius: $allowed. Got: ${it.blurRadius}" }
    )

    fun allowedBlurSigma(allowed: List<Float>) = KlipTransformRule(
        name = "blurSigma ${allowed.joinToString(" ")}",
        isValid = { it.blurSigma == null || it.blurSigma in allowed },
        errorMessage = { "Allowed blurSigma: $allowed. Got: ${it.blurSigma}" }
    )

    fun allowedGrayscale(allowed: Boolean) = KlipTransformRule(
        name = if (allowed) "+grayscale" else "-grayscale",
        isValid = { allowed || !it.grayscale },
        errorMessage = { "Grayscale is not allowed." }
    )

    fun allowedCrop(allowed: Boolean) = KlipTransformRule(
        name = if (allowed) "+crop" else "-crop",
        isValid = { allowed || !it.crop },
        errorMessage = { "Crop is not allowed." }
    )

    fun allowedFit(allowed: List<Fit>) = KlipTransformRule(
        name =  "fit ${allowed.joinToString(" ")}",
        isValid = { it.fit == null || it.fit in allowed },
        errorMessage = { "Crop is not allowed." }
    )

    fun allowedFlipH(allowed: Boolean) = KlipTransformRule(
        name = if (allowed) "+flipH" else "-flipH",
        isValid = { allowed || !it.flipH },
        errorMessage = { "Horizontal flip is not allowed." },
    )

    fun allowedFlipV(allowed: Boolean) = KlipTransformRule(
        name = if (allowed) "+flipV" else "-flipV",
        isValid = { allowed || !it.flipV },
        errorMessage = { "Vertical flip is not allowed." }
    )

    fun allowedDither(allowed: Boolean) = KlipTransformRule(
        name = if (allowed) "+dither" else "-dither",
        isValid = { allowed || !it.dither },
        errorMessage = { "Dither is not allowed." },
    )

    fun parseRules(rulesConfig: String): List<KlipTransformRule> {
        val rules = mutableListOf<KlipTransformRule>()

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
                        val allowedDimensions = mutableListOf<Pair<Int, Int>>()
                        val dimensions = trimmedRule
                            .removePrefix("dim")
                            .trim()
                            .split(" ")

                        dimensions.forEach {
                            val (w, h) = it.split("x").map { it.toInt() }
                            allowedDimensions.add(w to h)
                        }
                        rules.add(allowedDimensions(allowedDimensions))
                    }

                    // width rule
                    trimmedRule.startsWith("width") -> {
                        rules.add(allowedWidth(parseInts(trimmedRule, prefix = "width")))
                    }

                    // height rule
                    trimmedRule.startsWith("height") -> {
                        rules.add(allowedHeight(parseInts(trimmedRule, prefix = "height")))
                    }

                    // blur rule
                    trimmedRule.startsWith("blur") -> {
                        rules.add(allowedBlurRadius(parseFloats(trimmedRule, prefix = "blur")))
                    }

                    // quality rule
                    trimmedRule.startsWith("quality") -> {
                        rules.add(allowedQuality(parseInts(trimmedRule, "quality")))
                    }

                    // fit rule
                    trimmedRule.startsWith("fit") -> {
                        rules.add(allowedFit(parseFit(trimmedRule)))
                    }

                    // rotate rule
                    trimmedRule.startsWith("rotate") -> {
                        rules.add(allowedRotate(parseFloats(trimmedRule, prefix = "rotate")))
                    }

                    // sharpen rule
                    trimmedRule.startsWith("sharpen") -> {
                        rules.add(allowedSharpen(parseFloats(trimmedRule, prefix = "sharpen")))
                    }
                }
            }
        return rules
    }

    private fun parseFit(rule: String): List<Fit> {
        return rule.removePrefix("fit").trim().split(" ").map { Fit.valueOf(it) }
    }

    private fun parseFloats(rule: String, prefix: String): List<Float> {
        return rule.removePrefix(prefix).trim().split(" ").map { it.toFloat() }
    }

    private fun parseInts(rule: String, prefix: String): List<Int> {
        return rule.removePrefix(prefix).trim().split(" ").map { it.toInt() }
    }

}
