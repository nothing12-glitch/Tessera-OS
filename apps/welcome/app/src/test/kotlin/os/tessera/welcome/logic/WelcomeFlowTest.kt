package os.tessera.welcome.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тести кроків, а не пікселів: OOBE ламається саме на порядку — «Назад» із
 * останнього, пропуск не на тому екрані, крок, який бреше про запис до спроби.
 */
class WelcomeFlowTest {

    @Test
    fun `start begins at greeting`() {
        assertEquals(WelcomeStep.GREETING, WelcomeFlow.start().step)
    }

    @Test
    fun `start keeps saved choices but resets place and finish`() {
        val saved = WelcomeState(
            step = WelcomeStep.NETWORK,
            theme = ThemeChoice.DARK,
            power = PowerProfileChoice.PERFORMANCE,
            finished = true,
        )
        val state = WelcomeFlow.start(saved)
        assertEquals(WelcomeStep.GREETING, state.step)
        assertFalse(state.finished)
        assertEquals(ThemeChoice.DARK, state.theme)
        assertEquals(PowerProfileChoice.PERFORMANCE, state.power)
    }

    @Test
    fun `advance walks all ten steps in order`() {
        var state = WelcomeFlow.start()
        val visited = mutableListOf(state.step)
        repeat(WelcomeFlow.steps.size - 1) {
            state = WelcomeFlow.advance(state)
            visited += state.step
        }
        assertEquals(
            listOf(
                WelcomeStep.GREETING,
                WelcomeStep.LANGUAGE,
                WelcomeStep.ACCESSIBILITY,
                WelcomeStep.APPEARANCE,
                WelcomeStep.NAVIGATION,
                WelcomeStep.POWER,
                WelcomeStep.NETWORK,
                WelcomeStep.ACCOUNT,
                WelcomeStep.EDGE_DEMO,
                WelcomeStep.FINISH,
            ),
            visited,
        )
        assertEquals(10, visited.size)
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
    fun `back returns exactly one step from every step but the first`() {
        WelcomeFlow.steps.drop(1).forEach { step ->
            val state = WelcomeState(step = step)
            val previous = WelcomeFlow.steps[step.ordinal - 1]
            assertEquals(previous, WelcomeFlow.back(state).step)
            assertTrue(WelcomeFlow.canGoBack(state))
        }
    }

    @Test
    fun `back after finish changes nothing`() {
        val done = WelcomeState(step = WelcomeStep.ACCOUNT, finished = true)
        assertEquals(done, WelcomeFlow.back(done))
        assertFalse(WelcomeFlow.canGoBack(done))
    }

    @Test
    fun `skip is offered exactly where a skip truly exists`() {
        val skippable = setOf(
            WelcomeStep.POWER,
            WelcomeStep.NETWORK,
            WelcomeStep.ACCOUNT,
            WelcomeStep.EDGE_DEMO,
        )
        WelcomeFlow.steps.forEach { step ->
            assertEquals(skippable.contains(step), step.skippable)
            assertEquals(skippable.contains(step), WelcomeFlow.canSkip(WelcomeState(step = step)))
        }
        // Перші кроки й «Готово» пропустити немає куди.
        assertFalse(WelcomeStep.GREETING.skippable)
        assertFalse(WelcomeStep.LANGUAGE.skippable)
        assertFalse(WelcomeStep.ACCESSIBILITY.skippable)
        assertFalse(WelcomeStep.APPEARANCE.skippable)
        assertFalse(WelcomeStep.NAVIGATION.skippable)
        assertFalse(WelcomeStep.FINISH.skippable)
    }

    @Test
    fun `skip marks the step and moves forward`() {
        val onAccount = WelcomeState(step = WelcomeStep.ACCOUNT)
        val after = WelcomeFlow.skip(onAccount)
        assertTrue(WelcomeFlow.isSkipped(after, WelcomeStep.ACCOUNT))
        assertEquals(WelcomeStep.EDGE_DEMO, after.step)
    }

    @Test
    fun `skip on a step without skip changes nothing`() {
        val onTheme = WelcomeState(step = WelcomeStep.APPEARANCE)
        assertEquals(onTheme, WelcomeFlow.skip(onTheme))
        assertFalse(WelcomeFlow.isSkipped(WelcomeFlow.skip(onTheme), WelcomeStep.APPEARANCE))
    }

    @Test
    fun `skip after finish changes nothing`() {
        val done = WelcomeState(step = WelcomeStep.POWER, finished = true)
        assertEquals(done, WelcomeFlow.skip(done))
    }

    @Test
    fun `back from after a skip returns to the skipped step`() {
        val skipped = WelcomeFlow.skip(WelcomeState(step = WelcomeStep.POWER))
        assertEquals(WelcomeStep.POWER, WelcomeFlow.back(skipped).step)
        assertTrue(WelcomeFlow.isSkipped(WelcomeFlow.back(skipped), WelcomeStep.POWER))
    }

    @Test
    fun `only three steps write to the system, and they cover all targets`() {
        assertEquals(
            setOf(WelcomeStep.ACCESSIBILITY, WelcomeStep.APPEARANCE, WelcomeStep.FINISH),
            WelcomeFlow.writingSteps,
        )
        val covered = WelcomeFlow.steps.flatMap { it.writes }.toSet()
        assertEquals(WriteTarget.entries.toSet(), covered)
    }

    @Test
    fun `the steps we cannot write stay honest about it`() {
        val storingOnly = setOf(
            WelcomeStep.GREETING,
            WelcomeStep.LANGUAGE,
            WelcomeStep.NAVIGATION,
            WelcomeStep.POWER,
            WelcomeStep.NETWORK,
            WelcomeStep.ACCOUNT,
            WelcomeStep.EDGE_DEMO,
        )
        storingOnly.forEach { step -> assertTrue(step.writes.isEmpty()) }
        assertEquals(3, WriteTarget.entries.size)
    }

    @Test
    fun `write state stays untouched until we actually try`() {
        val state = WelcomeState(step = WelcomeStep.APPEARANCE)
        WriteTarget.entries.forEach { target ->
            assertEquals(WriteState.NOT_ATTEMPTED, WelcomeFlow.writeState(state, target))
        }
    }

    @Test
    fun `record distinguishes success from a permission failure`() {
        val applied = WelcomeFlow.record(WelcomeState(), WriteTarget.NIGHT_MODE, succeeded = true)
        assertEquals(WriteState.APPLIED, WelcomeFlow.writeState(applied, WriteTarget.NIGHT_MODE))

        val blocked = WelcomeFlow.record(WelcomeState(), WriteTarget.NIGHT_MODE, succeeded = false)
        assertEquals(WriteState.BLOCKED, WelcomeFlow.writeState(blocked, WriteTarget.NIGHT_MODE))
    }

    @Test
    fun `recording one target leaves the others untouched`() {
        val state = WelcomeFlow.record(
            WelcomeFlow.record(WelcomeState(), WriteTarget.NIGHT_MODE, succeeded = true),
            WriteTarget.FONT_SCALE,
            succeeded = false,
        )
        assertEquals(WriteState.APPLIED, WelcomeFlow.writeState(state, WriteTarget.NIGHT_MODE))
        assertEquals(WriteState.BLOCKED, WelcomeFlow.writeState(state, WriteTarget.FONT_SCALE))
        assertEquals(WriteState.NOT_ATTEMPTED, WelcomeFlow.writeState(state, WriteTarget.PROVISION))
    }

    @Test
    fun `choices keep the current step and never move the screen`() {
        val atAppearance = WelcomeState(step = WelcomeStep.APPEARANCE)
        assertEquals(
            WelcomeStep.APPEARANCE,
            WelcomeFlow.chooseTheme(atAppearance, ThemeChoice.DARK).step,
        )
        assertEquals(
            ThemeChoice.DARK,
            WelcomeFlow.chooseTheme(atAppearance, ThemeChoice.DARK).theme,
        )
        assertEquals(
            AccentColor.AMBER,
            WelcomeFlow.chooseAccent(atAppearance, AccentColor.AMBER).accent,
        )

        val atLanguage = WelcomeState(step = WelcomeStep.LANGUAGE)
        val chosen = WelcomeFlow.chooseRegion(
            WelcomeFlow.chooseLanguage(atLanguage, LanguageChoice.POLISH),
            RegionChoice.NONE,
        )
        assertEquals(LanguageChoice.POLISH, chosen.language)
        assertEquals(RegionChoice.NONE, chosen.region)
        assertEquals(WelcomeStep.LANGUAGE, chosen.step)

        val atAccess = WelcomeState(step = WelcomeStep.ACCESSIBILITY)
        val scaled = WelcomeFlow.chooseHighContrast(
            WelcomeFlow.chooseFontScale(atAccess, FontScaleChoice.LARGEST),
            enabled = true,
        )
        assertEquals(FontScaleChoice.LARGEST, scaled.fontScale)
        assertTrue(scaled.highContrast)
        assertEquals(WelcomeStep.ACCESSIBILITY, scaled.step)

        val atPower = WelcomeState(step = WelcomeStep.POWER)
        assertEquals(
            MacPolicyChoice.STABLE,
            WelcomeFlow.chooseMacPolicy(
                WelcomeFlow.choosePower(atPower, PowerProfileChoice.ECONOMY),
                MacPolicyChoice.STABLE,
            ).macPolicy,
        )
        assertEquals(
            NavigationChoice.BUTTONS,
            WelcomeFlow.chooseNavigation(atPower, NavigationChoice.BUTTONS).navigation,
        )
    }

    @Test
    fun `dots have exactly one active index per step`() {
        assertEquals(10, WelcomeFlow.dotCount())
        WelcomeFlow.steps.forEach { step ->
            val state = WelcomeState(step = step)
            assertEquals(step.ordinal, WelcomeFlow.dotIndex(state))
        }
        assertEquals(0, WelcomeFlow.dotIndex(WelcomeFlow.start()))
    }

    @Test
    fun `skipped steps accumulate instead of overwriting each other`() {
        var state = WelcomeFlow.skip(WelcomeState(step = WelcomeStep.POWER))
        state = WelcomeFlow.skip(state)
        state = WelcomeFlow.skip(state)
        assertEquals(
            setOf(WelcomeStep.POWER, WelcomeStep.NETWORK, WelcomeStep.ACCOUNT),
            state.skipped,
        )
        assertNotEquals(emptySet<WelcomeStep>(), state.skipped)
    }

    @Test
    fun `the wizard shows exactly while the system is unprovisioned`() {
        assertTrue(WelcomeFlow.shouldShow(deviceProvisioned = false))
        assertFalse(WelcomeFlow.shouldShow(deviceProvisioned = true))
    }
}
