package klip

object Files {
    fun getFileExtension(filename: String, default: String = ""): String {
        val name = filename.substringAfterLast('/') // get last segment after path separator
        val lastDotIndex = name.lastIndexOf('.')

        // no dot or the dot is the first character (hidden file without extension)
        if (lastDotIndex <= 0) return default

        return name.substring(lastDotIndex + 1).lowercase()
    }
}
