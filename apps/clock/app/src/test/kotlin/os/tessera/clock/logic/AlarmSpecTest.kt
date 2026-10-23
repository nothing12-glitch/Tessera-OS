package os.tessera.clock.logic

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSpecTest {

    // Фіксований зсув без переходу на літній час — так значення в тесті
    // визначені однозначно, незалежно від дати й локалі машини.
    private val tz = TimeZone.getTimeZone("GMT+02:00")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        GregorianCalendar(tz).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun alarm(hour: Int, minute: Int, enabled: Boolean = true, daily: Boolean = true) =
        AlarmSpec(id = 1L, hour = hour, minute = minute, label = "Підйом", repeatDaily = daily, enabled = enabled)

    @Test
    fun `ввімкнений щоденний дає сьогодні, якщо час ще попереду`() {
        val now = at(2026, Calendar.JANUARY, 10, 6, 0)
        val next = alarm(7, 5).nextTriggerMillis(now, tz)
        assertEquals(at(2026, Calendar.JANUARY, 10, 7, 5), next)
    }

    @Test
    fun `вже минулий час котиться на завтра`() {
        val now = at(2026, Calendar.JANUARY, 10, 8, 0)
        val next = alarm(7, 5).nextTriggerMillis(now, tz)
        assertEquals(at(2026, Calendar.JANUARY, 11, 7, 5), next)
    }

    @Test
    fun `рівно в now вважаємо минулим і беремо завтра`() {
        val now = at(2026, Calendar.JANUARY, 10, 7, 5)
        val next = alarm(7, 5).nextTriggerMillis(now, tz)
        assertEquals(at(2026, Calendar.JANUARY, 11, 7, 5), next)
    }

    @Test
    fun `вимкнений будильник не має наступного спрацювання`() {
        val now = at(2026, Calendar.JANUARY, 10, 6, 0)
        assertEquals(null, alarm(7, 5, enabled = false).nextTriggerMillis(now, tz))
    }

    @Test
    fun `одноразовий у минулому теж котиться на завтра поки ввімкнений`() {
        val now = at(2026, Calendar.JANUARY, 10, 9, 0)
        val oneShot = alarm(7, 5, daily = false)
        assertEquals(at(2026, Calendar.JANUARY, 11, 7, 5), oneShot.nextTriggerMillis(now, tz))
        // а після того, як приймач вимкнув одноразовий, — уже null
        assertEquals(null, oneShot.copy(enabled = false).nextTriggerMillis(now, tz))
    }

    @Test
    fun `перехід через кінець місяця`() {
        val now = at(2026, Calendar.JANUARY, 31, 23, 30)
        val next = alarm(7, 0).nextTriggerMillis(now, tz)
        assertEquals(at(2026, Calendar.FEBRUARY, 1, 7, 0), next)
    }

    @Test
    fun `різниця завжди не більше доби`() {
        val now = at(2026, Calendar.MARCH, 3, 12, 0)
        val next = alarm(7, 30).nextTriggerMillis(now, tz)!!
        val delta = next - now
        assertTrue(delta > 0 && delta <= 24 * 3_600_000L)
    }
}
