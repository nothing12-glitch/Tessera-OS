package os.tessera.files.logic

/**
 * Запис у списку файлів. Модель навмисно без `android.net.Uri`:
 * тоді сортування, розміри й дати можна перевірити звичайним JVM-тестом.
 */
data class FileNode(
    val documentId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val isDirectory: Boolean,
    val isWriteable: Boolean,
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()

    companion object {
        const val DIR_MIME = "vnd.android.document/directory"

        fun isDirectoryMime(mime: String): Boolean =
            mime == DIR_MIME || mime.endsWith("/directory")
    }
}
