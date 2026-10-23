package os.tessera.files.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class EntrySortTest {

    private fun file(name: String, size: Long = 10, modified: Long = 1000, dir: Boolean = false) =
        FileNode(
            documentId = name,
            name = name,
            mimeType = if (dir) FileNode.DIR_MIME else "text/plain",
            sizeBytes = if (dir) 0 else size,
            lastModifiedMillis = modified,
            isDirectory = dir,
            isWriteable = true,
        )

    @Test
    fun `папки завжди попереду файлів`() {
        val sorted = EntrySort.sort(
            listOf(file("zzz.txt"), file("Арка", dir = true), file("aaa.txt")),
            SortMode.NameAsc,
        )
        assertEquals(listOf("Арка", "aaa.txt", "zzz.txt"), sorted.map { it.name })
    }

    @Test
    fun `назва сортується без урахування регістру`() {
        val sorted = EntrySort.sort(
            listOf(file("beta.txt"), file("Alpha.md"), file("gamma.kt")),
            SortMode.NameAsc,
        )
        assertEquals(listOf("Alpha.md", "beta.txt", "gamma.kt"), sorted.map { it.name })
    }

    @Test
    fun `зворотний порядок назв`() {
        val sorted = EntrySort.sort(
            listOf(file("a.txt"), file("b.txt"), file("c.txt")),
            SortMode.NameDesc,
        )
        assertEquals(listOf("c.txt", "b.txt", "a.txt"), sorted.map { it.name })
    }

    @Test
    fun `за розміром спадно`() {
        val sorted = EntrySort.sort(
            listOf(file("small", size = 5), file("big", size = 500), file("mid", size = 50)),
            SortMode.SizeDesc,
        )
        assertEquals(listOf("big", "mid", "small"), sorted.map { it.name })
    }

    @Test
    fun `за датою спадно`() {
        val sorted = EntrySort.sort(
            listOf(
                file("давній", modified = 1_000),
                file("свіжий", modified = 9_000),
                file("середній", modified = 5_000),
            ),
            SortMode.ModifiedDesc,
        )
        assertEquals(listOf("свіжий", "середній", "давній"), sorted.map { it.name })
    }

    @Test
    fun `фільтр ігнорує регістр`() {
        val nodes = listOf(file("Фото 1.jpg"), file("звіт.odt"), file("Костян.txt"))
        assertEquals(listOf("Фото 1.jpg"), EntrySort.sort(nodes, SortMode.NameAsc, "фото").map { it.name })
        assertEquals(listOf("Костян.txt"), EntrySort.sort(nodes, SortMode.NameAsc, "костян").map { it.name })
        // літера «о» є у «Фото» й «Костян»; у «звіт.odt» вона латинкова, тож не потрапляє
        assertEquals(2, EntrySort.sort(nodes, SortMode.NameAsc, "о").size)
    }

    @Test
    fun `порожній фільтр не чіпає списку`() {
        val nodes = listOf(file("a"), file("b"))
        assertEquals(2, EntrySort.sort(nodes, SortMode.NameAsc, "   ").size)
    }
}
