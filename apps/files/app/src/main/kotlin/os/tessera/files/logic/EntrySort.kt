package os.tessera.files.logic

import java.text.Normalizer
import java.util.Locale

enum class SortMode { NameAsc, NameDesc, ModifiedDesc, SizeDesc }

/**
 * Сортування й фільтрація списку файлів. Окремо від Android, бо саме тут
 * губляться дрібниці: «я», «Ю», регістр і порядок «спочатку папки».
 */
object EntrySort {

    private fun byName(a: FileNode, b: FileNode): Int = a.name.compareTo(b.name, ignoreCase = true)

    /** Папки завжди попереду файлів — так само робить типовий файловий застосунок Android. */
    fun sort(nodes: List<FileNode>, mode: SortMode, query: String = ""): List<FileNode> {
        val visible = if (query.isBlank()) {
            nodes
        } else {
            val needle = Names.normalize(query)
            nodes.filter { Names.normalize(it.name).contains(needle) }
        }

        val secondary: (FileNode, FileNode) -> Int = when (mode) {
            SortMode.NameAsc -> ::byName
            SortMode.NameDesc -> { a, b -> -byName(a, b) }
            SortMode.ModifiedDesc -> { a, b -> b.lastModifiedMillis.compareTo(a.lastModifiedMillis) }
            SortMode.SizeDesc -> { a, b -> b.sizeBytes.compareTo(a.sizeBytes) }
        }

        return visible.sortedWith { a, b ->
            val dirsFirst = when {
                a.isDirectory == b.isDirectory -> 0
                a.isDirectory -> -1
                else -> 1
            }
            if (dirsFirst != 0) dirsFirst else secondary(a, b)
        }
    }
}

object Names {
    /** Пошук не має відрізняти регістр і діакритику: «файл» знаходить «Файл». */
    fun normalize(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    }
}
