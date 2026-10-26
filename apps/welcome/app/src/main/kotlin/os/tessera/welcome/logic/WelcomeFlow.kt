package os.tessera.welcome.logic

/**
 * Кроки першого запуску. Порядок один і той самий незалежно від того, звідки
 * застосунок стартував, — тому він тут, а не в UI.
 *
 * Дві речі, які крок знає про себе: `skippable` (чи тут справді є чого
 * пропускати) і `writes` (чи цей крок пише в систему, чи лише зберігає вибір).
 * Обидві перевіряються тестом, бо обидві — про порядок, а не про пікселі.
 */
enum class WelcomeStep(val skippable: Boolean, val writes: Set<WriteTarget>) {
    GREETING(false, emptySet()),
    LANGUAGE(false, emptySet()),
    ACCESSIBILITY(false, setOf(WriteTarget.FONT_SCALE)),
    APPEARANCE(false, setOf(WriteTarget.NIGHT_MODE)),
    NAVIGATION(false, emptySet()),
    POWER(true, emptySet()),
    NETWORK(true, emptySet()),
    ACCOUNT(true, emptySet()),
    EDGE_DEMO(true, emptySet()),
    FINISH(false, setOf(WriteTarget.PROVISION)),
    ;

    val isLast: Boolean get() = this == FINISH
}

/**
 * Системні записи, які майстер справді пробує зробити. Їх рівно три, і всі
 * три лежать у межах `WRITE_SECURE_SETTINGS`; решта виборів лише зберігається
 * у файл модуля й на екрані названа збереженим, а не застосованим.
 */
enum class WriteTarget {
    NIGHT_MODE,
    FONT_SCALE,
    PROVISION,
}

/**
 * Чесний стан одного запису. `NOT_ATTEMPTED` відрізняється від `BLOCKED`
 * тим, що ми ще навіть не пробували: крок не має права обіцяти успіх до
 * спроби, а потім мовчки ховати `SecurityException`.
 */
enum class WriteState {
    NOT_ATTEMPTED,
    APPLIED,
    BLOCKED,
}

/** Стан машини. Незмінний: кожен крок повертає новий екземпляр. */
data class WelcomeState(
    val step: WelcomeStep = WelcomeStep.GREETING,
    val theme: ThemeChoice = ThemeChoice.LIGHT,
    val accent: AccentColor = AccentColor.DYNAMIC,
    val language: LanguageChoice = LanguageChoice.UKRAINIAN,
    val region: RegionChoice = RegionChoice.UKRAINE,
    val fontScale: FontScaleChoice = FontScaleChoice.NORMAL,
    val highContrast: Boolean = false,
    val navigation: NavigationChoice = NavigationChoice.GESTURES,
    val power: PowerProfileChoice = PowerProfileChoice.BALANCED,
    val macPolicy: MacPolicyChoice = MacPolicyChoice.RANDOM,
    val skipped: Set<WelcomeStep> = emptySet(),
    val writes: Map<WriteTarget, WriteState> = emptyMap(),
    val finished: Boolean = false,
)

/**
 * Машина першого запуску: послідовність кроків, пропуск, реєстр записів.
 *
 * Ця машина не знає про Android навмисно — те, що ламається в OOBE зазвичай і
 * є порядок кроків («Назад» із останнього, пропуск не на тому екрані), а не
 * пікселі.
 */
object WelcomeFlow {

    val steps: List<WelcomeStep> = WelcomeStep.entries.toList()

    /** Кроки, які щось пишуть у систему. Решта лише зберігає вибір модуля. */
    val writingSteps: Set<WelcomeStep> = steps.filter { it.writes.isNotEmpty() }.toSet()

    fun start(saved: WelcomeState = WelcomeState()) =
        saved.copy(step = WelcomeStep.GREETING, finished = false)

    fun advance(state: WelcomeState): WelcomeState {
        if (state.finished) return state
        val current = state.step
        return if (current.isLast) state.copy(finished = true)
        else state.copy(step = steps[current.ordinal + 1])
    }

    /** «Назад» із першого кроку нікуди — не мотаємо екран і нічого не змінюємо. */
    fun back(state: WelcomeState): WelcomeState {
        if (state.finished) return state
        val index = state.step.ordinal
        return if (index == 0) state else state.copy(step = steps[index - 1])
    }

    /**
     * «Пропустити»: відмічаємо крок і йдемо далі. На кроці без пропуску не
     * робить нічого — кнопка там і не з'являється, але й стан не псуємо.
     */
    fun skip(state: WelcomeState): WelcomeState {
        if (state.finished || !state.step.skippable) return state
        return advance(state.copy(skipped = state.skipped + state.step))
    }

    fun canSkip(state: WelcomeState) = !state.finished && state.step.skippable

    fun canGoBack(state: WelcomeState) = !state.finished && state.step.ordinal > 0

    fun isSkipped(state: WelcomeState, step: WelcomeStep) = step in state.skipped

    fun chooseTheme(state: WelcomeState, choice: ThemeChoice) = state.copy(theme = choice)

    fun chooseAccent(state: WelcomeState, color: AccentColor) = state.copy(accent = color)

    fun chooseLanguage(state: WelcomeState, language: LanguageChoice) =
        state.copy(language = language)

    fun chooseRegion(state: WelcomeState, region: RegionChoice) = state.copy(region = region)

    fun chooseFontScale(state: WelcomeState, scale: FontScaleChoice) =
        state.copy(fontScale = scale)

    fun chooseHighContrast(state: WelcomeState, enabled: Boolean) =
        state.copy(highContrast = enabled)

    fun chooseNavigation(state: WelcomeState, choice: NavigationChoice) =
        state.copy(navigation = choice)

    fun choosePower(state: WelcomeState, choice: PowerProfileChoice) =
        state.copy(power = choice)

    fun chooseMacPolicy(state: WelcomeState, choice: MacPolicyChoice) =
        state.copy(macPolicy = choice)

    /** Стан запису до будь-якої спроби — `NOT_ATTEMPTED`, а не вгаданий успіх. */
    fun writeState(state: WelcomeState, target: WriteTarget): WriteState =
        state.writes[target] ?: WriteState.NOT_ATTEMPTED

    /** Єдине місце, де стан запису стає відомим UI: вийшло чи кинуло дозвіл. */
    fun record(state: WelcomeState, target: WriteTarget, succeeded: Boolean) =
        state.copy(writes = state.writes + (target to if (succeeded) WriteState.APPLIED else WriteState.BLOCKED))

    /** Індекси для крапок прогресу: по одній на крок, активний — поточний. */
    fun dotIndex(state: WelcomeState) = state.step.ordinal

    fun dotCount() = steps.size

    /**
     * Чи показувати вітальню. Один-єдиний критерій — системний прапорець
     * `device_provisioned`, а не власний запис «пройдено»: перший запуск,
     * якого не довели до кінця, має повернутися, а пройдений — ні.
     */
    fun shouldShow(deviceProvisioned: Boolean) = !deviceProvisioned
}
