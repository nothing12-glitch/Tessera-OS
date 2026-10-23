package os.tessera.files.logic

/**
 * Візуальний рід запису. Малюємо власні позначки, а не беремо іконки
 * з чужих застосунків: інакше образ порушив би ліцензійну політику.
 */
enum class EntryKind(val letter: String, val isFolder: Boolean) {
    Folder("П", true),
    Image("З", false),
    Audio("А", false),
    Video("В", false),
    Archive("П", false),
    Document("Д", false),
    Sheet("Т", false),
    Slide("С", false),
    Pdf("PDF", false),
    Code("К", false),
    Package("APK", false),
    Font("Ш", false),
    Text("Т", false),
    Generic("·", false),
    ;

    companion object {
        fun of(mimeType: String, isDirectory: Boolean, extension: String): EntryKind {
            if (isDirectory) return Folder
            val mime = mimeType.lowercase()
            val ext = extension.lowercase()
            return when {
                mime.startsWith("image/") -> Image
                mime.startsWith("audio/") -> Audio
                mime.startsWith("video/") -> Video
                mime == "application/pdf" -> Pdf
                mime.startsWith("text/") || ext in TEXT_EXT -> Text
                mime == "application/vnd.android.package-archive" || ext == "apk" -> Package
                mime.contains("font") || ext in FONT_EXT -> Font
                mime.contains("zip") || mime.contains("compress") || mime.contains("tar") || ext in ARCHIVE_EXT -> Archive
                mime.contains("presentation") || ext in SLIDE_EXT -> Slide
                mime.contains("spreadsheet") || ext in SHEET_EXT -> Sheet
                mime.contains("document") || mime.contains("word") || ext in DOC_EXT -> Document
                mime.contains("javascript") || mime.contains("json") || mime.contains("xml") || ext in CODE_EXT -> Code
                else -> Generic
            }
        }

        private val TEXT_EXT = setOf("txt", "md", "srt", "vtt", "ass", "csv", "log")
        private val CODE_EXT = setOf("kt", "kts", "java", "py", "sh", "go", "rs", "ts", "js", "html", "css", "gradle")
        private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "xz", "bz2", "iso")
        private val DOC_EXT = setOf("doc", "docx", "odt", "rtf", "pages")
        private val SHEET_EXT = setOf("xls", "xlsx", "ods", "numbers")
        private val SLIDE_EXT = setOf("ppt", "pptx", "odp", "key")
        private val FONT_EXT = setOf("ttf", "otf", "woff", "woff2")
    }
}
