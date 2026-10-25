package os.tessera.halo.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правила світла. Тут сидить уся чесність функції: що світиться, а що ні.
 */
class HaloPlanTest {

    private val base = HaloConfig()

    private fun posted(
        priority: Int = 0,
        category: String? = null,
        ongoing: Boolean = false,
        key: String = "a/tag/1",
    ) = PostedNotification(
        key = key,
        priority = priority,
        category = category,
        ongoing = ongoing,
        colorArgb = 0,
        hasPixelSource = false,
    )

    @Test
    fun `увімкнений край світить на звичайне сповіщення`() {
        assertTrue(HaloPlan.shouldGlow(posted(), base, zenActive = false))
    }

    @Test
    fun `вимкнений край не світить ні на що`() {
        assertFalse(HaloPlan.shouldGlow(posted(), base.copy(enabled = false), false))
    }

    @Test
    fun `постійне сповіщення служби не світиться`() {
        assertFalse(HaloPlan.shouldGlow(posted(ongoing = true), base, false))
    }

    @Test
    fun `тихе сповіщення не світиться, якщо обрано лише помітні`() {
        val quiet = posted(priority = -1)
        assertTrue(base.onlyLoud)
        assertFalse(HaloPlan.shouldGlow(quiet, base, false))
        assertTrue(HaloPlan.shouldGlow(quiet, base.copy(onlyLoud = false), false))
    }

    @Test
    fun `під не турбувати проходить лише будильник і дзвінок`() {
        assertFalse(HaloPlan.shouldGlow(posted(category = "msg"), base, zenActive = true))
        assertTrue(HaloPlan.shouldGlow(posted(category = "alarm"), base, zenActive = true))
        assertTrue(HaloPlan.shouldGlow(posted(category = "call"), base, zenActive = true))
    }

    @Test
    fun `тривалість і яскравість завжди в межах`() {
        val wild = HaloConfig(durationMs = 999_999L, intensity = 4f).normalized()
        assertEquals(HaloConfig.MAX_DURATION_MS, wild.durationMs)
        assertEquals(1.0, wild.intensity.toDouble(), 0.0001)

        val tiny = HaloConfig(durationMs = 1L, intensity = 0f).normalized()
        assertEquals(HaloConfig.MIN_DURATION_MS, tiny.durationMs)
        assertEquals(0.2, tiny.intensity.toDouble(), 0.0001)
    }

    @Test
    fun `друге світло можливе щойно перше згасло`() {
        val config = HaloConfig(durationMs = 2000L)
        assertFalse(HaloPlan.allowReGlow(nowElapsed = 2800L, lastGlowElapsed = 1000L, config))
        assertTrue(HaloPlan.allowReGlow(nowElapsed = 3800L, lastGlowElapsed = 1000L, config))
    }

    @Test
    fun `зникле сповіщення прибирає свій ключ`() {
        val active = setOf("a//1", "b//2")
        assertEquals(setOf("b//2"), HaloPlan.keysAfterDismiss(active, "a//1"))
        assertTrue(HaloPlan.keysAfterDismiss(active, "нічого").contains("a//1"))
    }

    @Test
    fun `назви збережених правил повертаються, сміття — ні`() {
        assertEquals(GlowPattern.PULSE, GlowPattern.byName("PULSE"))
        assertEquals(GlowPattern.WAVE, GlowPattern.byName("хвиля"))
        assertEquals(GlowPattern.WAVE, GlowPattern.byName(null))
        assertEquals(GlowColor.MONET, GlowColor.byName("ХИБА"))
        assertEquals(GlowColor.NOTIFICATION, GlowColor.byName("NOTIFICATION"))
    }

    @Test
    fun `обгортання світла починається і кінчається нулем`() {
        assertEquals(0.0, HaloMotion.envelope(0f).toDouble(), 0.0001)
        assertEquals(0.0, HaloMotion.envelope(1f).toDouble(), 0.0001)
        assertEquals(1.0, HaloMotion.envelope(0.5f).toDouble(), 0.0001)
    }

    @Test
    fun `дихання не виходить за межі і робить рівно стільки циклів`() {
        assertEquals(0.0, HaloMotion.breathe(0f, 2).toDouble(), 0.0001)
        assertEquals(1.0, HaloMotion.breathe(0.25f, 2).toDouble(), 0.0001)
        assertEquals(0.0, HaloMotion.breathe(0.5f, 2).toDouble(), 0.0001)
        assertEquals(1.0, HaloMotion.breathe(1f, 0).toDouble(), 0.0001)
    }

    @Test
    fun `за пробіг часу світло робить рівно стільки обідав`() {
        assertEquals(0.0, HaloMotion.travel(0f, 2f).toDouble(), 0.0001)
        assertEquals(2.0, HaloMotion.travel(1f, 2f).toDouble(), 0.0001)
        assertEquals(2.0, HaloMotion.travel(5f, 2f).toDouble(), 0.0001)
    }

    @Test
    fun `згладження ріже все, що поза межами нуля й одиниці`() {
        assertEquals(0.0, HaloMotion.smooth(-3f).toDouble(), 0.0001)
        assertEquals(1.0, HaloMotion.smooth(9f).toDouble(), 0.0001)
    }

    @Test
    fun `домінує насичений колір, а не білий фон іконки`() {
        val white = List(120) { 0xFFFFFFFF.toInt() }
        val blue = List(40) { 0xFF0B57D0.toInt() }
        val frame = (white + blue).toIntArray()
        val color = ColorBloom.dominant(frame)
        assertTrue(color != null)
        val rgb = color!! and 0x00FFFFFF
        val b = rgb and 0xFF
        val r = (rgb shr 16) and 0xFF
        assertTrue("синій має лишитись синім: $rgb", b > r)
    }

    @Test
    fun `порожній і повністю прозорий кадр не дають кольору`() {
        assertNull(ColorBloom.dominant(IntArray(0)))
        assertNull(ColorBloom.dominant(intArrayOf(0x00FFFFFF.toInt(), 0x00123456)))
    }

    @Test
    fun `на темному склі темний колір стає яскравішим, не змінюючи відтінку`() {
        val darkGreen = 0xFF00_50_00.toInt()
        val bright = ColorBloom.readableOnDark(darkGreen)
        assertTrue(ColorBloom.luminance(bright) > ColorBloom.luminance(darkGreen))
        val g = (bright shr 8) and 0xFF
        assertTrue("зелений має лишитись найсильнішим: $bright", g > (bright and 0xFF))
        assertEquals(0xFFFFFFFF.toInt(), ColorBloom.readableOnDark(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), ColorBloom.readableOnDark(0xFF000000.toInt()))
    }

    @Test
    fun `змішування і альфа рахуються як очікується`() {
        val from = 0xFF000000.toInt()
        val to = 0xFFFFFFFF.toInt()
        assertEquals(from, ColorBloom.mix(from, to, 0f))
        assertEquals(to, ColorBloom.mix(from, to, 1f))
        val half = ColorBloom.mix(from, to, 0.5f) and 0x00FFFFFF
        assertTrue("середина близька до сірого: $half", half in 0x7A7A7A..0x808080)
        assertEquals(0x40_12_34_56, ColorBloom.withAlpha(0xFF123456.toInt(), 0x40))
    }
}
