package os.tessera.clock.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockFormatTest {

    @Test
    fun `секундомір до години показує mm ss d`() {
        assertEquals("00:00.0", ClockFormat.stopwatch(0))
        assertEquals("00:01.2", ClockFormat.stopwatch(1234))
        assertEquals("00:59.9", ClockFormat.stopwatch(59_999))
        assertEquals("01:05.0", ClockFormat.stopwatch(65_000))
    }

    @Test
    fun `секундомір дробову округлює вниз`() {
        // 1999 мс — це ще не 1.9, а 1.9? Ні: 1999/100=19, %10=9 → «00:01.9»
        assertEquals("00:01.9", ClockFormat.stopwatch(1999))
        assertEquals("00:02.0", ClockFormat.stopwatch(2000))
    }

    @Test
    fun `секундомір після години переходить на h mm ss d`() {
        assertEquals("1:00:00.0", ClockFormat.stopwatch(3_600_000))
        assertEquals("1:02:03.4", ClockFormat.stopwatch(3_723_400))
        assertEquals("2:59:59.9", ClockFormat.stopwatch(10_799_999))
    }

    @Test
    fun `секундомір не показує відємний час`() {
        assertEquals("00:00.0", ClockFormat.stopwatch(-5_000))
    }

    @Test
    fun `таймер завжди з годинами і круглить секунди вгору`() {
        assertEquals("0:00:00", ClockFormat.countdown(0))
        assertEquals("0:00:01", ClockFormat.countdown(1))
        assertEquals("0:01:30", ClockFormat.countdown(90_000))
        // 299999 мс ще не «4:59», а рівно «5:00»
        assertEquals("0:05:00", ClockFormat.countdown(299_999))
        assertEquals("0:05:00", ClockFormat.countdown(300_000))
        assertEquals("1:01:01", ClockFormat.countdown(3_661_000))
    }

    @Test
    fun `час доби з двома нулями`() {
        assertEquals("07:05", ClockFormat.timeOfDay(7, 5))
        assertEquals("00:00", ClockFormat.timeOfDay(0, 0))
        assertEquals("23:59", ClockFormat.timeOfDay(23, 59))
    }

    @Test
    fun `людський рядок через скільки`() {
        assertEquals("менше хвилини", ClockFormat.until(30_000))
        assertEquals("12 хв", ClockFormat.until(12 * 60_000))
        assertEquals("8 год 12 хв", ClockFormat.until(8 * 3_600_000 + 12 * 60_000))
        assertEquals("2 год", ClockFormat.until(2 * 3_600_000))
    }
}
