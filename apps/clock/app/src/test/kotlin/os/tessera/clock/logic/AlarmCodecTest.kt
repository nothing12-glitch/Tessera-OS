package os.tessera.clock.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmCodecTest {

    private val sample = listOf(
        AlarmSpec(1, 7, 5, "Ранок", repeatDaily = true, enabled = true),
        AlarmSpec(2, 23, 59, "Нічна зміна", repeatDaily = false, enabled = false),
        AlarmSpec(3, 0, 0, "", repeatDaily = true, enabled = false),
    )

    @Test
    fun `список проходить туди й назад без втрат`() {
        val back = AlarmCodec.decode(AlarmCodec.encode(sample))
        assertEquals(sample, back)
    }

    @Test
    fun `порожній список кодується в порожній рядок`() {
        assertEquals("", AlarmCodec.encode(emptyList()))
        assertTrue(AlarmCodec.decode("").isEmpty())
        assertTrue(AlarmCodec.decode("   ").isEmpty())
    }

    @Test
    fun `підпис із комками лапками й переносом не ламає формат`() {
        val lineBreak = "\n"
        val tricky = listOf(
            AlarmSpec(9, 6, 30, "Зустріч, «важлива»" + lineBreak + "другий рядок", true, true),
        )
        val back = AlarmCodec.decode(AlarmCodec.encode(tricky))
        assertEquals(tricky, back)
    }

    @Test
    fun `вбудовані роздільники викидаються і не створюють фантомний запис`() {
        val fieldSep = '\u001F'
        val recordSep = '\u001E'
        val nasty = "а" + fieldSep + "б" + recordSep + "в"
        val list = listOf(
            AlarmSpec(1, 6, 0, nasty, false, true),
            AlarmSpec(2, 7, 0, "сусід", true, false),
        )
        val back = AlarmCodec.decode(AlarmCodec.encode(list))
        assertEquals(2, back.size)
        assertEquals("абв", back[0].label)
        assertFalse(back[0].label.contains(fieldSep))
        assertFalse(back[0].label.contains(recordSep))
        assertEquals(1L, back[0].id)
        assertEquals("сусід", back[1].label)
        assertEquals(2L, back[1].id)
    }

    @Test
    fun `сміттєвий рядок не розпадається на частини`() {
        assertTrue(AlarmCodec.decode("це не формат").isEmpty())
        val oneBad = "x\u001Fy\u001Fz" // замало полів
        assertTrue(AlarmCodec.decode(oneBad).isEmpty())
    }

    @Test
    fun `некоректні години й хвилини відкидаються`() {
        val bad = "5\u001F25\u001F10\u001F\u001F1\u001F1" // hour=25
        assertTrue(AlarmCodec.decode(bad).isEmpty())
    }
}
