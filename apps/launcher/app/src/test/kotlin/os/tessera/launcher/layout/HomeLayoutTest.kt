package os.tessera.launcher.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Перевірка заповнення сітки.
 *
 * Це єдиний шар layouts, який можна перевірити без пристрою: решта — це
 * Compose й PackageManager, вони потребують образу (див. apps/launcher/README.md).
 */
class HomeLayoutTest {

    @Test
    fun `повертає рівно capacity комірок`() {
        val cells = HomeLayout.place(listOf("a", "b"), GridSpec.LARGE_PHONE.homeCapacity)
        assertEquals(30, cells.size)
    }

    @Test
    fun `порожні комірки заповнює null у кінці`() {
        val cells = HomeLayout.place(listOf("a", "b", "c"), 6)
        assertEquals(listOf("a", "b", "c", null, null, null), cells)
        assertNull(cells[3])
    }

    @Test
    @Suppress("KotlinConstantConditions")
    fun `надлишок пінів не виходить за межі сітки`() {
        val pins = (1..40).map { "id$it" }
        val cells = HomeLayout.place(pins, 30)
        assertEquals(30, cells.size)
        assertEquals(30, cells.count { it != null })
        assertEquals("id30", cells[29])
    }

    @Test
    fun `нульова або відʼємна місткість не роняє розкладку`() {
        assertEquals(emptyList<String?>(), HomeLayout.place(listOf("a"), 0))
        assertEquals(emptyList<String?>(), HomeLayout.place(listOf("a"), -1))
    }
}
