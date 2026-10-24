package os.tessera.dialer.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class CallFormatTest {

    @Test
    fun `тривалість хвилини і секунди`() {
        assertEquals("0:00", CallFormat.duration(0))
        assertEquals("0:59", CallFormat.duration(59))
        assertEquals("1:00", CallFormat.duration(60))
        assertEquals("1:14", CallFormat.duration(74))
        assertEquals("10:07", CallFormat.duration(607))
    }

    @Test
    fun `тривалість понад годину має три частини`() {
        assertEquals("1:00:00", CallFormat.duration(3600))
        assertEquals("1:02:03", CallFormat.duration(3723))
        assertEquals("12:34:56", CallFormat.duration(45296))
    }

    @Test
    fun `відʼємна тривалість стає нульовою`() {
        // CallLog віддає -1 для пропущених і відхилених викликів.
        assertEquals("0:00", CallFormat.duration(-1))
    }

    @Test
    fun `час доби з урахуванням зсуву`() {
        // 21:47 UTC: 21*3600 + 47*60 = 78 420 секунд
        val twentyOneFortySevenUtc = 78_420_000L
        assertEquals("21:47", CallFormat.timeOfDay(twentyOneFortySevenUtc, 0))
        assertEquals("23:47", CallFormat.timeOfDay(twentyOneFortySevenUtc, 120))
    }

    @Test
    fun `північ переходить на попередній день коректно`() {
        assertEquals("23:59", CallFormat.timeOfDay(-60_000L, 0))
        assertEquals(-1L, CallFormat.epochDay(-60_000L, 0))
        assertEquals(0L, CallFormat.epochDay(0L, 120))
    }

    @Test
    fun `різниця діб визначає групу`() {
        assertEquals(2L, CallFormat.dayDiff(3L, 5L))
        val labels = DayLabels("Сьогодні", "Учора")
        assertEquals("Сьогодні", CallFormat.groupLabel(0, labels) { "помилка" })
        assertEquals("Учора", CallFormat.groupLabel(1, labels) { "помилка" })
        assertEquals("3 жовтня", CallFormat.groupLabel(2, labels) { "3 жовтня" })
    }

    @Test
    fun `пошук фільтрує за номером, іменем і т9`() {
        val records = listOf(
            CallRecord(1, "0671234567", "Тетяна", CallDirection.Incoming, 0L, 10),
            CallRecord(2, "0501112233", null, CallDirection.Missed, 0L, -1),
            CallRecord(3, "0930001122", "Tessera", CallDirection.Outgoing, 0L, 45),
        )
        assertEquals(3, CallFormat.filter("", records).size)
        assertEquals(listOf(1L), CallFormat.filter("1234567", records).map { it.id })
        assertEquals(listOf(2L), CallFormat.filter("1112233", records).map { it.id })
        assertEquals(listOf(1L), CallFormat.filter("Тет", records).map { it.id })
        // «8377» — це Tessera, набрана на клавіатурі набору.
        assertEquals(listOf(3L), CallFormat.filter("8377", records).map { it.id })
    }

    @Test
    fun `who бере ім,я контакту або форматує номер`() {
        val named = CallRecord(1, "0671234567", "Тетяна", CallDirection.Incoming, 0L, 10)
        val anonim = CallRecord(2, "0671234567", "   ", CallDirection.Outgoing, 0L, 10)
        val withoutName = CallRecord(3, "0671234567", null, CallDirection.Missed, 0L, 0)
        assertEquals("Тетяна", named.who)
        assertEquals("067 123 45 67", anonim.who)
        assertEquals("067 123 45 67", withoutName.who)
    }
}
