package os.tessera.messaging.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadGroupingTest {

    private fun message(id: Long, fromMe: Boolean, at: Long) =
        Message(id, "0671234567", "текст $id", at, fromMe)

    @Test
    fun `серія від одного боку за хвилину збирається в одну групу`() {
        val groups = ThreadGrouping.groups(
            listOf(message(1, true, 0), message(2, true, 30_000), message(3, true, 60_000)),
            offsetMinutes = 0,
        )
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].messages.size)
        // час показуємо найсвіжіший — він стоїть під останньою бульбашкою
        assertEquals(60_000L, groups[0].timestampMillis)
    }

    @Test
    fun `зміна боку розриває групу`() {
        val groups = ThreadGrouping.groups(
            listOf(message(1, true, 0), message(2, false, 10_000), message(3, true, 20_000)),
            offsetMinutes = 0,
        )
        assertEquals(3, groups.size)
    }

    @Test
    fun `перерва довша за вікно розриває групу, навіть якщо бік той самий`() {
        val groups = ThreadGrouping.groups(
            listOf(message(1, true, 0), message(2, true, ThreadGrouping.GROUP_WINDOW_MILLIS + 1)),
            offsetMinutes = 0,
        )
        assertEquals(2, groups.size)
    }

    @Test
    fun `група не переходить опівночі попри вікно`() {
        // Десять секунд до опівночі й двадцять після — інтервал удвічі коротший
        // за вікно групи, тож рве їх саме зміна доби.
        val midnight = 86_400_000L
        val groups = ThreadGrouping.groups(
            listOf(message(1, true, midnight - 10_000), message(2, true, midnight + 20_000)),
            offsetMinutes = 0,
        )
        assertEquals(2, groups.size)
        assertEquals(0L, groups[0].dayKey)
        assertEquals(1L, groups[1].dayKey)
    }

    @Test
    fun `місцевий зсув рахує добу, а не utc`() {
        // 23:30 UTC — це 01:30 наступної доби при +02:00.
        val at = 86_400_000L - 30 * 60_000L
        assertEquals(0L, ThreadGrouping.epochDay(at, 0))
        assertEquals(1L, ThreadGrouping.epochDay(at, 120))
    }

    @Test
    fun `порожня нитка не дає груп`() {
        assertEquals(0, ThreadGrouping.groups(emptyList(), offsetMinutes = 0).size)
    }
}
