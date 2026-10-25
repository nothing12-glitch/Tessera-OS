package os.tessera.welcome.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тести кроків, а не пікселів: OOBE ламається саме на порядку — «Назад» із
 * останнього, пропуск не на тому екрані, смуга, яка раптом показує нуль на
 * першому кроці.
 */
class WelcomeFlowTest {

    @Test
    fun `start begins at greeting`() {
        assertEquals(WelcomeStep.GREETING, WelcomeFlow.start().step)
    }

    @Test
    fun `advance walks the four steps in order`() {
        var state = WelcomeFlow.start()
        val visited = mutableListOf(state.step)
        repeat(WelcomeFlow.steps.size - 1) {
            state = WelcomeFlow.advance(state)
            visited += state.step
        }
        assertEquals(
            listOf(WelcomeStep.GREETING, WelcomeStep.THEME, WelcomeStep.ACCOUNT, WelcomeStep.FINISH),
            visited,
        )
    }

    @Test
    fun `advance on last step finishes instead of wrapping`() {
        val last = WelcomeState(step = WelcomeStep.FINISH)
        val next = WelcomeFlow.advance(last)
        assertTrue(next.finished)
        assertEquals(WelcomeStep.FINISH, next.step)
    }

    @Test
    fun `advance after finish changes nothing`() {
        val done = WelcomeState(step = WelcomeStep.FINISH, finished = true)
        assertEquals(done, WelcomeFlow.advance(done))
    }

    @Test
    fun `back from first step stays put`() {
        val first = WelcomeFlow.start()
        assertEquals(first, WelcomeFlow.back(first))
        assertFalse(WelcomeFlow.canGoBack(first))
    }

    @Test
    fun `back returns one step`() {
        val state = WelcomeState(step = WelcomeStep.ACCOUNT)
        assertEquals(WelcomeStep.THEME, WelcomeFlow.back(state).step)
        assertTrue(WelcomeFlow.canGoBack(state))
    }

    @Test
    fun `back after finish changes nothing`() {
        val done = WelcomeState(step = WelcomeStep.ACCOUNT, finished = true)
        assertEquals(done, WelcomeFlow.back(done))
        assertFalse(WelcomeFlow.canGoBack(done))
    }

    @Test
    fun `skip account only counts on the account step`() {
        val elsewhere = WelcomeFlow.skipAccount(WelcomeState(step = WelcomeStep.THEME))
        assertFalse(elsewhere.accountSkipped)

        val onAccount = WelcomeFlow.skipAccount(WelcomeState(step = WelcomeStep.ACCOUNT))
        assertTrue(onAccount.accountSkipped)
    }

    @Test
    fun `choose theme keeps the step`() {
        val state = WelcomeState(step = WelcomeStep.THEME)
        val chosen = WelcomeFlow.chooseTheme(state, ThemeChoice.DARK)
        assertEquals(ThemeChoice.DARK, chosen.theme)
        assertEquals(WelcomeStep.THEME, chosen.step)
    }

    @Test
    fun `progress is never zero on the first step`() {
        assertEquals(0.25, WelcomeFlow.progress(WelcomeFlow.start()).toDouble(), 0.0001)
        assertEquals(1.0, WelcomeFlow.progress(WelcomeState(step = WelcomeStep.FINISH)).toDouble(), 0.0001)
    }

    @Test
    fun `unknown or missing stored theme falls back to auto`() {
        assertEquals(ThemeChoice.AUTO, ThemeChoice.byName(null))
        assertEquals(ThemeChoice.AUTO, ThemeChoice.byName("НЕМАЄ_ТАКОЇ"))
        assertEquals(ThemeChoice.DARK, ThemeChoice.byName("DARK"))
    }

    @Test
    fun `auto follows the system, the rest do not`() {
        assertTrue(ThemeChoice.AUTO.isDark(systemPrefersDark = true))
        assertFalse(ThemeChoice.AUTO.isDark(systemPrefersDark = false))
        assertTrue(ThemeChoice.DARK.isDark(systemPrefersDark = false))
        assertFalse(ThemeChoice.LIGHT.isDark(systemPrefersDark = true))
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
    fun `the wizard shows exactly while the system is unprovisioned`() {
        assertTrue(WelcomeFlow.shouldShow(deviceProvisioned = false))
        assertFalse(WelcomeFlow.shouldShow(deviceProvisioned = true))
    }
}
