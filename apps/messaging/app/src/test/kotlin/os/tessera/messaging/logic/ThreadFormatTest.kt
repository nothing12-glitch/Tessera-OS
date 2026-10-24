package os.tessera.messaging.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Форматування списку розмов: мітки часу, витяжки, пошук і лічильник
 * непрочитаного.
 *
 * Чисті функції, тому тести без Android-контексту — саме на цих перетвореннях
 * ламаються «сьогодні/учора» поблизу півночі.
 */
class ThreadFormatTest {

    private val midnightUtcDay = 1_730_000_000_000L / DAY * DAY
    private val today = ThreadGrouping.epochDay(midnightUtcDay, 0)
    private val labels = DayLabels("Сьогодні", "Учора")

    @Test
    fun `час доби рахується в межах доби`() {
        assertEquals("00:00", ThreadFormat.timeOfDay(midnightUtcDay, 0))
        assertEquals("23:59", ThreadFormat.timeOfDay(midnightUtcDay + 86_340_000L, 0))
    }

    @Test
    fun `час доби бере місцевий зсув`() {
        val instant = midnightUtcDay + 90 * MINUTE
        assertEquals("03:30", ThreadFormat.timeOfDay(instant, 120))
        // зсув можна закласти в сам момент — час доби лишається той самий,
        // отже, зсув застосовується рівно один раз
        assertEquals("03:30", ThreadFormat.timeOfDay(instant + 2 * HOUR, 0))
    }

    @Test
    fun `мітка рядка — сьогодні показує час, учора — слово`() {
        assertEquals(
            "09:15",
            ThreadFormat.listLabel(midnightUtcDay + 555 * MINUTE, today, 0, "Учора") { "—" },
        )
        assertEquals(
            "Учора",
            ThreadFormat.listLabel(midnightUtcDay - DAY + 555 * MINUTE, today, 0, "Учора") { "—" },
        )
        assertEquals(
            "18 жовт.",
            ThreadFormat.listLabel(midnightUtcDay - 6 * DAY, today, 0, "Учора") { "18 жовт." },
        )
    }

    @Test
    fun `заголовок дня розмови відрізняється від мітки рядка`() {
        assertEquals("Сьогодні", ThreadFormat.dayLabel(0L, labels) { "—" })
        assertEquals("Учора", ThreadFormat.dayLabel(1L, labels) { "—" })
        assertEquals("3 жовт. 2023", ThreadFormat.dayLabel(9L, labels) { "3 жовт. 2023" })
    }

    @Test
    fun `витяжка згортає пробіли й обрізає довге`() {
        assertEquals("Привіт світ", ThreadFormat.snippet("Привіт \n\t світ "))
        assertEquals("a".repeat(96) + "…", ThreadFormat.snippet("a".repeat(400)))
        assertEquals("", ThreadFormat.snippet("   \n\t  "))
    }

    @Test
    fun `пошук ловить контакт, цифри номера та текст`() {
        val threads = listOf(
            summary(1L, "+380671234567", "Тарас", "Завтра о десятій"),
            summary(2L, "+380501110000", "Olga", "Ок"),
        )
        assertEquals(listOf(1L), ThreadFormat.filter("тара", threads).ids())
        assertEquals(listOf(1L), ThreadFormat.filter("067 123", threads).ids())
        assertEquals(listOf(2L), ThreadFormat.filter("olga", threads).ids())
        assertEquals(listOf(1L), ThreadFormat.filter("десятій", threads).ids())
        assertTrue(ThreadFormat.filter("", threads).isNotEmpty())
        assertFalse(ThreadFormat.filter("кодування немає", threads).any { it.threadId == 2L })
    }

    @Test
    fun `непрочитаними вважаються лише вхідні непрочитані`() {
        val messages = listOf(
            message(1L, "+380671234567", "від мене", fromMe = true, read = false, threadId = 5L),
            message(2L, "+380671234567", "не прочитано", fromMe = false, read = false, threadId = 5L),
            message(3L, "+380671234567", "прочитано", fromMe = false, read = true, threadId = 5L),
            message(4L, "+380501110000", "чернетка без нитки", threadId = NO_THREAD),
        )
        val summaries = ThreadSummaries.of(messages)
        assertEquals(1, summaries.single().unread)
        assertEquals(5L, summaries.single().threadId)
    }

    @Test
    fun `розмова бере назву контакту, а список — порядок від нового`() {
        val messages = listOf(
            message(1L, "+380671234567", "старе", timestamp = 1_000L, threadId = 1L),
            message(2L, "+380501110000", "свіже", timestamp = 9_000L, threadId = 2L),
        )
        val summaries = ThreadSummaries.of(messages) {
            if (it.contains("050")) "Оля" else null
        }
        assertEquals(listOf(2L, 1L), summaries.ids())
        assertEquals("Оля", summaries.first().title)
        assertEquals("+380 671 234 567", summaries[1].title)
        assertEquals("свіже", summaries.first().snippet)
    }

    @Test
    fun `сума непрочитаного для значка в лаунчері`() {
        val threads = listOf(
            summary(1L, "+380671234567", null, "раз", unread = 2),
            summary(2L, "+380501110000", null, "два", unread = 3),
        )
        assertEquals(5, ThreadFormat.unreadTotal(threads))
        assertFalse(threads[0].hasUnread.not())
    }

    private fun List<ThreadSummary>.ids(): List<Long> = map { it.threadId }

    private fun summary(
        threadId: Long,
        address: String,
        name: String?,
        snippet: String,
        unread: Int = 0,
    ) = ThreadSummary(threadId, address, name, snippet, timestampMillis = 1L, unread = unread)

    private fun message(
        id: Long,
        address: String,
        body: String,
        timestamp: Long = id * 1_000L,
        fromMe: Boolean = false,
        read: Boolean = true,
        threadId: Long = id,
    ) = Message(id, address, body, timestamp, fromMe, read = read, threadId = threadId)

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
    }
}
