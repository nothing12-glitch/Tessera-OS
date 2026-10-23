package os.tessera.clock.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class CountDownTest {

    private var t = 0L
    private fun newTimer(): CountDown {
        t = 0L
        return CountDown { t }
    }

    @Test
    fun `у спокої дорівнює нулю`() {
        val cd = newTimer()
        assertEquals(TimerStatus.Idle, cd.status)
        assertEquals(0L, cd.remaining())
    }

    @Test
    fun `старт на нулі нічого не робить`() {
        val cd = newTimer()
        cd.start()
        assertEquals(TimerStatus.Idle, cd.status)
    }

    @Test
    fun `відлік іде лише коли запущено`() {
        val cd = newTimer()
        cd.add(60_000)
        assertEquals(60_000L, cd.remaining())
        cd.start()
        t = 10_000
        assertEquals(50_000L, cd.remaining())
        t = 59_999
        assertEquals(1L, cd.remaining())
    }

    @Test
    fun `додавання під час роботи продовжує а не перезапускає`() {
        val cd = newTimer()
        cd.add(60_000)
        cd.start()          // ціль = 60_000
        t = 10_000
        assertEquals(50_000L, cd.remaining())
        cd.add(30_000)      // ціль = 90_000, база відліку не чіпається
        assertEquals(80_000L, cd.remaining())
        assertEquals(TimerStatus.Running, cd.status)
    }

    @Test
    fun `забрання під час роботи скорочує`() {
        val cd = newTimer()
        cd.add(120_000)
        cd.start()
        t = 10_000
        cd.subtract(30_000) // ціль = 90_000
        assertEquals(80_000L, cd.remaining())
    }

    @Test
    fun `на нулі переходить у Finished`() {
        val cd = newTimer()
        cd.add(5_000)
        cd.start()
        t = 5_000
        assertEquals(0L, cd.remaining())
        assertEquals(TimerStatus.Finished, cd.status)
        t = 8_000
        assertEquals(TimerStatus.Finished, cd.status)
    }

    @Test
    fun `пауза заморожує залишок`() {
        val cd = newTimer()
        cd.add(100_000)
        cd.start()
        t = 40_000
        cd.pause()
        assertEquals(TimerStatus.Paused, cd.status)
        assertEquals(60_000L, cd.remaining())
        t = 90_000 // час тече, але на паузі залишок не рухається
        assertEquals(60_000L, cd.remaining())
    }

    @Test
    fun `відновлення після паузи бере решту а не початок`() {
        val cd = newTimer()
        cd.add(100_000)
        cd.start()
        t = 40_000
        cd.pause()               // залишок 60_000
        t = 1_000                // «теперішній» момент для відновлення
        cd.start()               // ціль = 1_000 + 60_000 = 61_000
        t = 21_000
        assertEquals(40_000L, cd.remaining())
    }

    @Test
    fun `додавання після завершення готує новий відлік`() {
        val cd = newTimer()
        cd.add(2_000)
        cd.start()
        t = 2_000
        assertEquals(TimerStatus.Finished, cd.status)
        cd.add(30_000)
        assertEquals(TimerStatus.Idle, cd.status)
        assertEquals(30_000L, cd.remaining())
    }

    @Test
    fun `скидання повертає у Idle з нулем`() {
        val cd = newTimer()
        cd.add(45_000)
        cd.start()
        t = 5_000
        cd.reset()
        assertEquals(TimerStatus.Idle, cd.status)
        assertEquals(0L, cd.remaining())
    }

    @Test
    fun `не можна піти у відємний залишок`() {
        val cd = newTimer()
        cd.add(10_000)
        cd.subtract(99_000)
        assertEquals(0L, cd.remaining())
    }
}
