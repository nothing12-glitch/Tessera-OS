package os.tessera.welcome.logic

/**
 * Кроки першого запуску. Порядок один і той самий незалежно від того, звідки
 * застосунок стартував, — тому він тут, а не в UI.
 */
enum class WelcomeStep {
    GREETING,
    THEME,
    ACCOUNT,
    FINISH;

    val isLast: Boolean get() = this == FINISH
}

/** Вибір теми; значення UiModeManager зовнішні — логіка не тягне Android. */
enum class ThemeChoice {
    AUTO,
    LIGHT,
    DARK;

    companion object {
        fun byName(name: String?): ThemeChoice =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * Куди веде кнопка «Додати акаунт».
 *
 * Увага до деталей: `com.google.android.gms` означає лише те, що якийсь
 * постачальник відкриває GmsCore-API — microG користується тим самим іменем
 * пакета. За цим фактом ми не розрізняємо microG і справжній GMS і нічого про
 * це не заявляємо.
 */
enum class AccountRoute {
    SETTINGS,
    UNAVAILABLE
}

/** Стан машини. Незмінний: кожен крок повертає новий екземпляр. */
data class WelcomeState(
    val step: WelcomeStep = WelcomeStep.GREETING,
    val theme: ThemeChoice = ThemeChoice.AUTO,
    val accountSkipped: Boolean = false,
    val finished: Boolean = false,
)

/**
 * Машина першого запуску: послідовність кроків, пропуск акаунта, завершення.
 *
 * Ця машина не знає про Android навмисно — те, що ламається в OOBE зазвичай і
 * є порядок кроків («Назад» із останнього, пропуск не на тому екрані), а не
 * пікселі.
 */
object WelcomeFlow {

    val steps: List<WelcomeStep> = WelcomeStep.entries.toList()

    fun start(step: WelcomeStep = WelcomeStep.GREETING) = WelcomeState(step = step)

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

    fun chooseTheme(state: WelcomeState, choice: ThemeChoice) = state.copy(theme = choice)

    fun skipAccount(state: WelcomeState) =
        if (state.step == WelcomeStep.ACCOUNT) state.copy(accountSkipped = true) else state

    fun canGoBack(state: WelcomeState) = !state.finished && state.step != WelcomeStep.GREETING

    /** Заповнений відсоток смуги прогресу: перший крок — не нуль. */
    fun progress(state: WelcomeState): Float =
        (state.step.ordinal + 1f) / steps.size

    fun stepNumber(state: WelcomeState) = state.step.ordinal + 1

    /**
     * Показувати чи вітальню знову. Після завершення — ні, аж доки проєкт не
     * змінить ONBOARDING_VERSION (тоді нові кроки побачать усі).
     */
    fun shouldShow(finished: Boolean, acknowledgedVersion: Int, currentVersion: Int) =
        !finished || acknowledgedVersion != currentVersion
}

/**
 * Чи темна палітра для вибраної теми. `AUTO` не вигадує власного правила:
 * віддає те, що каже система (`systemPrefersDark`).
 */
fun ThemeChoice.isDark(systemPrefersDark: Boolean): Boolean = when (this) {
    ThemeChoice.AUTO -> systemPrefersDark
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
}

/** Мапіч вибору теми на константи UiModeManager — самі значення дає Android-шар. */
fun ThemeChoice.nightMode(auto: Int, light: Int, dark: Int): Int = when (this) {
    ThemeChoice.AUTO -> auto
    ThemeChoice.LIGHT -> light
    ThemeChoice.DARK -> dark
}

/**
 * Маршрут кроку з обліковим записом. `gmsCore` — пакет, який дає Google-автентифікацію
 * (microG або справжній GMS — для нас однаково, див. AccountRoute).
 */
fun accountRoute(installedPackages: List<String>, gmsCore: String): AccountRoute =
    if (installedPackages.any { it == gmsCore }) AccountRoute.SETTINGS else AccountRoute.UNAVAILABLE
