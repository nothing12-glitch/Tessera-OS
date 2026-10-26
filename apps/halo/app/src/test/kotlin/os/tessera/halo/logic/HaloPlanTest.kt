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

    private fun ctx(
        zen: Boolean = false,
        call: Boolean = false,
        interactive: Boolean = true,
        locked: Boolean = false,
        battery: Int = 100,
        charging: Boolean = false,
    ) = GlowContext(
        zenActive = zen,
        inCall = call,
        interactive = interactive,
        keyguardLocked = locked,
        batteryPercent = battery,
        charging = charging,
    )

    @Test
    fun `увімкнений край світить на звичайне сповіщення`() {
        assertTrue(HaloPlan.shouldGlow(posted(), base, ctx()))
    }

    @Test
    fun `вимкнений край не світить ні на що`() {
        assertFalse(HaloPlan.shouldGlow(posted(), base.copy(enabled = false), ctx()))
    }

    @Test
    fun `постійне сповіщення служби не світиться`() {
        assertFalse(HaloPlan.shouldGlow(posted(ongoing = true), base, ctx()))
    }

    @Test
    fun `тихе сповіщення не світиться, якщо обрано лише помітні`() {
        val quiet = posted(priority = -1)
        assertTrue(base.onlyLoud)
        assertFalse(HaloPlan.shouldGlow(quiet, base, ctx()))
        assertTrue(HaloPlan.shouldGlow(quiet, base.copy(onlyLoud = false), ctx()))
    }

    @Test
    fun `під не турбувати край вимикається повністю`() {
        assertFalse(HaloPlan.shouldGlow(posted(), base, ctx(zen = true)))
        assertFalse(HaloPlan.shouldGlow(posted(category = "alarm"), base, ctx(zen = true)))
    }

    @Test
    fun `на вхідний дзвінок край не світить`() {
        assertFalse(HaloPlan.shouldGlow(posted(category = "call"), base, ctx()))
        assertFalse(HaloPlan.shouldGlow(posted(), base, ctx(call = true)))
    }

    @Test
    fun `при вимкненому екрані й на блокуванні краю немає`() {
        assertFalse(HaloPlan.shouldGlow(posted(), base, ctx(interactive = false)))
        assertFalse(HaloPlan.shouldGlow(posted(), base, ctx(locked = true)))
    }

    @Test
    fun `нижче порогу батареї край вимикається, на зарядці — ні`() {
        assertFalse(HaloPlan.shouldGlow(posted(), base, ctx(battery = 15)))
        assertTrue(HaloPlan.shouldGlow(posted(), base, ctx(battery = 15, charging = true)))
        assertTrue(HaloPlan.shouldGlow(posted(), base, ctx(battery = 20)))
        // непрочитаний заряд не вбиває світло чесно
        assertTrue(HaloPlan.shouldGlow(posted(), base, ctx(battery = -1)))
    }

    @Test
    fun `яскравість краю падає з розрядом батареї`() {
        val cfg = HaloConfig(intensity = 1f)
        assertEquals(1.0, HaloPlan.edgeBrightness(cfg, ctx(battery = 100)).toDouble(), 0.001)
        assertEquals(1.0, HaloPlan.edgeBrightness(cfg, ctx(battery = 40, charging = true)).toDouble(), 0.001)
        val low = HaloPlan.edgeBrightness(cfg, ctx(battery = HaloConfig.MIN_BATTERY_PERCENT))
        assertTrue("на межі порогу тьмяніше за повну: $low", low < 1f && low >= HaloConfig.MIN_INTENSITY)
        assertEquals(
            HaloPlan.batteryFactor(ctx(battery = HaloConfig.MIN_BATTERY_PERCENT)).toDouble(),
            HaloPlan.batteryFactor(ctx(battery = 5)).toDouble(),
            0.0001,
        )
    }

    @Test
    fun `яскравість завжди в межах`() {
        assertEquals(1.0, HaloConfig(intensity = 4f).normalized().intensity.toDouble(), 0.0001)
        assertEquals(
            HaloConfig.MIN_INTENSITY.toDouble(),
            HaloConfig(intensity = 0f).normalized().intensity.toDouble(),
            0.0001,
        )
    }

    @Test
    fun `колір бере колір застосунку, а без нього — бірюзовий резерв`() {
        val appColor = 0xFFFF1020.toInt()
        assertEquals(appColor, HaloPlan.pickColor(appColor, ColorBloom.TEAL))
        // прозорий (альфа 0) і нульовий — це «кольору немає»
        assertEquals(ColorBloom.TEAL, HaloPlan.pickColor(0x00FF1020, ColorBloom.TEAL))
        assertEquals(ColorBloom.TEAL, HaloPlan.pickColor(0, ColorBloom.TEAL))
    }

    @Test
    fun `компенсація зрізу альфи повертає яскравість до 1,00`() {
        // 0.80 → назад до 100%, але не вище стелі 255
        assertEquals(255, ColorBloom.compensateAlpha(204))
        assertEquals(100, ColorBloom.compensateAlpha(80))
        assertEquals(0, ColorBloom.compensateAlpha(0))
        assertEquals(0, ColorBloom.compensateAlpha(-5))
    }

    @Test
    fun `друге світло можливе щойно перше згасло`() {
        val impulse = HaloConfig.IMPULSE_MS
        assertFalse(HaloPlan.allowReGlow(nowElapsed = impulse, lastGlowElapsed = 0L))
        assertTrue(
            HaloPlan.allowReGlow(
                nowElapsed = impulse + HaloConfig.RE_GLOW_COOLDOWN_MS,
                lastGlowElapsed = 0L,
            ),
        )
    }

    @Test
    fun `імпульс триває рівно три секунди`() {
        assertEquals(3000L, HaloConfig.IMPULSE_MS)
    }

    @Test
    fun `зникле сповіщення прибирає свій ключ`() {
        val active = setOf("a//1", "b//2")
        assertEquals(setOf("b//2"), HaloPlan.keysAfterDismiss(active, "a//1"))
        assertTrue(HaloPlan.keysAfterDismiss(active, "нічого").contains("a//1"))
    }

    @Test
    fun `візерунків рівно три і сміття повертає хвилю`() {
        assertEquals(3, GlowPattern.entries.size)
        assertEquals(GlowPattern.PULSE, GlowPattern.byName("PULSE"))
        assertEquals(GlowPattern.WAVE, GlowPattern.byName("хвиля"))
        assertEquals(GlowPattern.WAVE, GlowPattern.byName(null))
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
