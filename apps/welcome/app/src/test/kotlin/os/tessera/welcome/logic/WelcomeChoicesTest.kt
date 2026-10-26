package os.tessera.welcome.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Мапічі виборів, колір і рух — усе це чисті функції, тому перевіряється тут,
 * а не скріншотом: автоперемикання не має повернутися зі старим файлом
 * налаштувань, а акцент — опинитися без контрастного тексту.
 */
class WelcomeChoicesTest {

    @Test
    fun `theme is manual only`() {
        assertEquals(listOf(ThemeChoice.LIGHT, ThemeChoice.DARK), ThemeChoice.entries.toList())
        assertFalse(ThemeChoice.LIGHT.isDark)
        assertTrue(ThemeChoice.DARK.isDark)
    }

    @Test
    fun `stale auto choice from an older build falls back to light`() {
        // Старий образ міг залишити в файлі «AUTO» — воно не має нічого вмикати.
        assertEquals(ThemeChoice.LIGHT, ThemeChoice.byName("AUTO"))
        assertEquals(ThemeChoice.LIGHT, ThemeChoice.byName(null))
        assertEquals(ThemeChoice.DARK, ThemeChoice.byName("DARK"))
    }

    @Test
    fun `night mode maps to the values the caller supplies`() {
        // 1 і 2 — зовнішні константи UiModeManager; логіка їх не знає.
        assertEquals(1, ThemeChoice.LIGHT.nightMode(light = 1, dark = 2))
        assertEquals(2, ThemeChoice.DARK.nightMode(light = 1, dark = 2))
    }

    @Test
    fun `only dynamic accent has no own color`() {
        assertTrue(AccentColor.DYNAMIC.isDynamic)
        AccentColor.entries.filterNot { it.isDynamic }.forEach { accent ->
            assertEquals(0xFF, (accent.argb shr 24) and 0xFF)
            assertNotEqualsZero(accent.argb and 0x00FFFFFF)
        }
        assertEquals(AccentColor.DYNAMIC, AccentColor.byName("НЕМАЄ_ТАКОЇ"))
        assertEquals(AccentColor.DYNAMIC, AccentColor.byName(null))
        assertEquals(AccentColor.VERDIGRIS, AccentColor.byName("VERDIGRIS"))
    }

    @Test
    fun `accent contrast comes from luminance, not from guesswork`() {
        assertEquals(1f, relativeLuminance(0xFFFFFFFF.toInt()), 0.001f)
        assertEquals(0f, relativeLuminance(0xFF000000.toInt()), 0.001f)
        // Бурштин світлий — текст на ньому темний; індиго темний — текст світлий.
        assertEquals(DARK_INK, onAccentArgb(AccentColor.AMBER.argb))
        assertEquals(LIGHT_INK, onAccentArgb(AccentColor.INDIGO.argb))
    }

    @Test
    fun `mixing stops at both ends and lifts a dark accent`() {
        assertEquals(AccentColor.INDIGO.argb, mixArgb(AccentColor.INDIGO.argb, LIGHT_INK, 0f))
        assertEquals(LIGHT_INK, mixArgb(AccentColor.INDIGO.argb, LIGHT_INK, 1f))
        val lifted = readableOnDark(AccentColor.NIGHT.argb)
        assertTrue(relativeLuminance(lifted) > relativeLuminance(AccentColor.NIGHT.argb))
        assertEquals(0xFF, (lifted shr 24) and 0xFF)
    }

    @Test
    fun `locale tag keeps region optional`() {
        assertEquals("uk-UA", localeTag(LanguageChoice.UKRAINIAN, RegionChoice.UKRAINE))
        assertEquals("en", localeTag(LanguageChoice.ENGLISH, RegionChoice.NONE))
        assertEquals(LanguageChoice.GERMAN, LanguageChoice.byTag("de"))
        assertEquals(LanguageChoice.UKRAINIAN, LanguageChoice.byTag(null))
        assertEquals(RegionChoice.UKRAINE, RegionChoice.byCode(null))
        assertEquals(RegionChoice.UKRAINE, RegionChoice.byCode("ZZ"))
    }

    @Test
    fun `font scale has one normal value and rounds the rest to the nearest`() {
        assertEquals(1.0f, FontScaleChoice.NORMAL.scale, 0.0001f)
        assertEquals(FontScaleChoice.NORMAL, FontScaleChoice.byName("НЕМАЄ"))
        assertEquals(FontScaleChoice.LARGEST, FontScaleChoice.byName("LARGEST"))
        assertEquals(FontScaleChoice.NORMAL, FontScaleChoice.nearest(1.05f))
        assertEquals(FontScaleChoice.LARGE, FontScaleChoice.nearest(1.15f))
        assertEquals(FontScaleChoice.LARGEST, FontScaleChoice.nearest(1.35f))
    }

    @Test
    fun `stored-only choices keep their documented defaults`() {
        assertEquals(NavigationChoice.GESTURES, NavigationChoice.byName(null))
        assertEquals(NavigationChoice.BUTTONS, NavigationChoice.byName("BUTTONS"))
        assertEquals(PowerProfileChoice.BALANCED, PowerProfileChoice.byName(null))
        assertEquals(PowerProfileChoice.ECONOMY, PowerProfileChoice.byName("ECONOMY"))
        assertEquals(MacPolicyChoice.RANDOM, MacPolicyChoice.byName(null))
        assertEquals(MacPolicyChoice.STABLE, MacPolicyChoice.byName("STABLE"))
        assertEquals(3, PowerProfileChoice.entries.size)
        assertEquals(4, SystemScreen.entries.size)
    }

    @Test
    fun `account route needs exactly the gms core package`() {
        assertEquals(
            AccountRoute.SETTINGS,
            accountRoute(listOf("os.tessera.welcome", "com.google.android.gms"), "com.google.android.gms"),
        )
        assertEquals(
            AccountRoute.UNAVAILABLE,
            accountRoute(listOf("os.tessera.welcome"), "com.google.android.gms"),
        )
        assertEquals(AccountRoute.UNAVAILABLE, accountRoute(emptyList(), "com.google.android.gms"))
    }

    @Test
    fun `greeting lasts three seconds and its stages never overlap backwards`() {
        assertEquals(3000L, GREETING_MS)
        val stages = listOf(
            GreetingStage.MARK,
            GreetingStage.TITLE,
            GreetingStage.BODY,
            GreetingStage.HINT,
            GreetingStage.BUILD,
        )
        assertEquals(0f, stages.first().fromMs, 0.0001f)
        assertEquals(GREETING_MS.toFloat(), stages.last().toMs, 0.0001f)
        stages.zip(stages.drop(1)).forEach { (earlier, later) ->
            assertTrue(later.fromMs >= earlier.fromMs)
            assertTrue(later.toMs >= earlier.toMs)
        }
    }

    @Test
    fun `stage grows monotonically and stays inside zero to one`() {
        val mark = GreetingStage.MARK
        assertEquals(0f, stageOf(0f, mark), 0.0001f)
        assertEquals(0.5f, stageOf(450f, mark), 0.0001f)
        assertEquals(1f, stageOf(900f, mark), 0.0001f)
        // Після свого сегмента елемент уже повністю видно, до — повністю нічого.
        assertEquals(1f, stageOf(2600f, mark), 0.0001f)
        assertEquals(0f, stageOf(700f, GreetingStage.BUILD), 0.0001f)
        var previous = -1f
        (0L..GREETING_MS step 100L).forEach { ms ->
            val current = stageOf(ms.toFloat(), GreetingStage.TITLE)
            assertTrue(current >= previous)
            previous = current
        }
    }

    @Test
    fun `scale travels between the bounds the stage gives`() {
        assertEquals(0.6f, scaleOf(0f, 0.6f, 1f), 0.0001f)
        assertEquals(1f, scaleOf(1f, 0.6f, 1f), 0.0001f)
        assertEquals(0.8f, scaleOf(0.5f, 0.6f, 1f), 0.0001f)
    }

    @Test
    fun `edge demo cycles with a positive period and whole waves`() {
        assertTrue(EDGE_DEMO_CYCLE_MS > 0)
        assertTrue(EDGE_DEMO_WAVES >= 1)
    }

    private fun assertNotEqualsZero(value: Int) {
        assertTrue(value != 0)
    }
}
