package os.tessera.clock.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopWatchTest {

    // ручний «годинник» — те саме, що SystemClock.elapsedRealtime дає на пристрої,
    // але в тесті ми рухаємо його самі й знаємо точні значення
    private var t = 0L
    private fun newWatch(): StopWatch {
        t = 0L
        return StopWatch { t }
    }

    @Test
    fun `на старті дорівнює нулю й не рухається`() {
        val sw = newWatch()
        assertEquals(0L, sw.elapsed())
        assertFalse(sw.isRunning())
    }

    @Test
    fun `рахує лише коли запущено`() {
        val sw = newWatch()
        sw.start()
        assertTrue(sw.isRunning())
        t = 5_000
        assertEquals(5_000L, sw.elapsed())
        t = 10_000
        assertEquals(10_000L, sw.elapsed())
    }

    @Test
    fun `стоп заморожує накопичений час`() {
        val sw = newWatch()
        sw.start()
        t = 7_000
        sw.stop()
        assertFalse(sw.isRunning())
        val frozen = sw.elapsed()
        t = 99_000
        assertEquals(frozen, sw.elapsed())
    }

    @Test
    fun `кола зберігають розбивку і загальний час`() {
        val sw = newWatch()
        sw.start()
        t = 10_000
        sw.lap() // коло 1: всього 10 с
        t = 15_000
        sw.lap() // коло 2: +5 с, всього 15 с
        t = 17_000
        sw.stop()
        val laps = sw.laps()
        assertEquals(2, laps.size)
        assertEquals(1, laps[0].index)
        assertEquals(10_000L, laps[0].splitMs)
        assertEquals(10_000L, laps[0].totalMs)
        assertEquals(2, laps[1].index)
        assertEquals(5_000L, laps[1].splitMs)
        assertEquals(15_000L, laps[1].totalMs)
    }

    @Test
    fun `коло на зупиненому не записується`() {
        val sw = newWatch()
        sw.start()
        t = 3_000
        sw.stop()
        sw.lap()
        assertEquals(0, sw.laps().size)
    }

    @Test
    fun `скидання чистить таймер і чергу кіл`() {
        val sw = newWatch()
        sw.start()
        t = 4_000
        sw.lap()
        t = 8_000
        sw.reset()
        assertEquals(0L, sw.elapsed())
        assertEquals(0, sw.laps().size)
        assertFalse(sw.isRunning())
    }

    @Test
    fun `повторний старт не зсуває базу відліку`() {
        val sw = newWatch()
        sw.start()
        t = 5_000
        sw.start() // ігнорується, бо вже рухається
        t = 8_000
        sw.stop()
        assertEquals(8_000L, sw.elapsed())
    }

    @Test
    fun `різниця фаз накопичується через паузи`() {
        val sw = newWatch()
        sw.start(); t = 3_000; sw.stop()   // 3 с
        sw.start(); t = 10_000; sw.stop()  // ще 7 с → всього 10 с
        assertEquals(10_000L, sw.elapsed())
    }
}
