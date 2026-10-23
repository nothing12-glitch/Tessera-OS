package os.tessera.files.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    @Test
    fun `байти до кілобайта лишаються байтами`() {
        assertEquals("0\u00A0Б", Formatters.size(0))
        assertEquals("512\u00A0Б", Formatters.size(512))
        assertEquals("1023\u00A0Б", Formatters.size(1023))
    }

    @Test
    fun `одиниці двійкові й від’ємна кома`() {
        assertEquals("1,0\u00A0КіБ", Formatters.size(1024))
        assertEquals("1,5\u00A0КіБ", Formatters.size(1536))
        assertEquals("2,0\u00A0МіБ", Formatters.size(2L * 1024 * 1024))
        assertEquals("3,0\u00A0ГіБ", Formatters.size(3L * 1024 * 1024 * 1024))
    }

    @Test
    fun `великі значення округлюються до цілих`() {
        assertEquals("120\u00A0МіБ", Formatters.size(120L * 1024 * 1024 + 1024))
    }

    @Test
    fun `невідомий розмір показуємо прочерком`() {
        assertEquals("—", Formatters.sizeOrUnknown(0))
        assertEquals("—", Formatters.sizeOrUnknown(-1))
        assertEquals("1,0\u00A0КіБ", Formatters.sizeOrUnknown(1024))
    }

    @Test
    fun `відносні дати за українськими множинами`() {
        assertEquals("сьогодні", Formatters.relative(now - hour(3), now))
        assertEquals("учора", Formatters.relative(now - day, now))
        assertEquals("2 дні тому", Formatters.relative(now - 2 * day, now))
        assertEquals("5 днів тому", Formatters.relative(now - 5 * day, now))
        assertEquals("21 день тому", Formatters.relative(now - 21 * day, now))
    }

    @Test
    fun `давнє старше місяця показуємо датою`() {
        val label = Formatters.relative(now - 60 * day, now)
        assertTrue("має бути дата, а не «тому»: $label", !label.contains("тому"))
        assertTrue(label.isNotBlank())
    }

    @Test
    fun `множина українська`() {
        assertEquals("день", Formatters.plural(1, "день", "дні", "днів"))
        assertEquals("дні", Formatters.plural(3, "день", "дні", "днів"))
        assertEquals("днів", Formatters.plural(5, "день", "дні", "днів"))
        assertEquals("днів", Formatters.plural(11, "день", "дні", "днів"))
        assertEquals("день", Formatters.plural(21, "день", "дні", "днів"))
        assertEquals("днів", Formatters.plural(111, "день", "дні", "днів"))
    }

    private fun hour(minutes: Int) = minutes * 60L * 60 * 1000
}
