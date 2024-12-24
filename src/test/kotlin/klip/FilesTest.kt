package klip

import klip.Files.getFileExtension
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import kotlin.test.Test

class FilesTest {
    @Test
    fun `test getFileExtension`() {
        // basic cases
        expectThat(getFileExtension("image.png")).isEqualTo("png")
        expectThat(getFileExtension("image.jpeg")).isEqualTo("jpeg")
        expectThat(getFileExtension("image.JPEG")).isEqualTo("jpeg") // case-insensitive

        // no extension
        expectThat(getFileExtension("folder/image")).isEqualTo("") // no extension, no default
        expectThat(getFileExtension("folder/image", "octet-stream")).isEqualTo("octet-stream") // default extension

        // hidden files without extensions
        expectThat(getFileExtension(".hiddenfile")).isEqualTo("") // no extension
        expectThat(getFileExtension(".hiddenfile", "octet-stream")).isEqualTo("octet-stream") // default for hidden file
        expectThat(getFileExtension("folder/.hiddenfile")).isEqualTo("") // no extension in folder
        expectThat(getFileExtension("folder/.hiddenfile", "octet-stream")).isEqualTo("octet-stream")

        // hidden files with extensions
        expectThat(getFileExtension(".hiddenfile.png")).isEqualTo("png") // hidden file with extension
        expectThat(getFileExtension("folder/.hiddenfile.png")).isEqualTo("png") // hidden file with extension in folder
    }
}
