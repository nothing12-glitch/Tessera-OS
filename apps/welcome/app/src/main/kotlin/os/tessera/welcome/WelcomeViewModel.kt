package os.tessera.welcome

import android.app.Application
import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.tessera.welcome.data.CompletionCue
import os.tessera.welcome.data.ModulePrefs
import os.tessera.welcome.data.SystemFlags
import os.tessera.welcome.logic.AccountRoute
import os.tessera.welcome.logic.AccentColor
import os.tessera.welcome.logic.FontScaleChoice
import os.tessera.welcome.logic.LanguageChoice
import os.tessera.welcome.logic.MacPolicyChoice
import os.tessera.welcome.logic.NavigationChoice
import os.tessera.welcome.logic.PowerProfileChoice
import os.tessera.welcome.logic.RegionChoice
import os.tessera.welcome.logic.SystemScreen
import os.tessera.welcome.logic.ThemeChoice
import os.tessera.welcome.logic.WelcomeFlow
import os.tessera.welcome.logic.WelcomeState
import os.tessera.welcome.logic.WelcomeStep
import os.tessera.welcome.logic.WriteState
import os.tessera.welcome.logic.WriteTarget
import os.tessera.welcome.logic.accountRoute

/**
 * Що бачить екран: машина кроків + результат пошуку GmsCore + одноразове
 * попередження. Стан будь-якого системного запису читається через [writeState],
 * а не тримається окремим полем на кожен випадок.
 */
data class WelcomeUiState(
    val flow: WelcomeState = WelcomeState(),
    val account: AccountRoute = AccountRoute.UNAVAILABLE,
    val buildLabel: String = "",
    val notice: Int? = null,
    val finished: Boolean = false,
) {
    fun writeState(target: WriteTarget): WriteState = WelcomeFlow.writeState(flow, target)
}

/**
 * Android-шар вітальні.
 *
 * Тут усе, чого не може логіка: прочитати систему, спробувати записати
 * прапорець, відкрити чужий екран, зіграти завершення. Порядок кроків звідси
 * не вирішується — він у [WelcomeFlow].
 *
 * Правило чесності одне: `WRITE_SECURE_SETTINGS` покриває три записи
 * ([WriteTarget]), і кожен з них матиме стан `NOT_ATTEMPTED`, доки ми його
 * справді не пробували. Решту виборів зберігаємо у [ModulePrefs] — екран каже
 * про це прямим текстом, а не удаваним успіхом.
 */
class WelcomeViewModel(application: Application) : AndroidViewModel(application) {

    private val flags = SystemFlags(application)
    private val prefs = ModulePrefs(application)
    private val cues = CompletionCue(application)

    private val _state = MutableStateFlow(
        WelcomeUiState(
            flow = WelcomeFlow.start(
                prefs.load().copy(theme = currentTheme(), fontScale = currentFontScale()),
            ),
            buildLabel = Build.DISPLAY ?: "",
        ),
    )
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()

    /**
     * Чи закриватися, не показавши нічого. Це системний гачок: коли образ уже
     * налаштовано (`device_provisioned == 1`), майстер не існує для
     * користувача й не має право перекривати йому екран.
     */
    fun skipWhenAlreadyPassed(): Boolean = flags.isProvisioned()

    init {
        viewModelScope.launch {
            val found = listOf(GMS_CORE_PACKAGE).filter { hasPackage(it) }
            _state.update { it.copy(account = accountRoute(found, GMS_CORE_PACKAGE)) }
        }
    }

    /**
     * Тема. Керує нею `UiModeManager`: він і застосовує режим наживо, і пише той
     * самий рядок `ui_night_mode`, що й перемикач у Налаштуваннях.
     */
    fun onTheme(choice: ThemeChoice) {
        choose { WelcomeFlow.chooseTheme(it, choice) }
        val mode = choice.nightMode(
            light = UiModeManager.MODE_NIGHT_NO,
            dark = UiModeManager.MODE_NIGHT_YES,
        )
        record(WriteTarget.NIGHT_MODE, attempt {
            flags.setNightMode(mode)
            // Значення в базі ще не означає темного телефону. Живий прогін
            // 26.10.2026 виміряв, що сирий запис у Secure лишає сервіс на
            // старому режимі до перезавантаження — тому стан «застосовано»
            // вирішує застосований режим, а не відсутність винятку.
            val applied = flags.appliedNightMode()
            check(applied == mode) {
                "Сервіс лишився на режимі $applied, хоч запитано $mode"
            }
        })
    }

    /**
     * Масштаб шрифта. Той самий рядок `font_scale`, що й повзунок у
     * Налаштуваннях → Спеціальні можливості; без дозволу — `BLOCKED` на екрані.
     */
    fun onFontScale(choice: FontScaleChoice) {
        choose { WelcomeFlow.chooseFontScale(it, choice) }
        record(WriteTarget.FONT_SCALE, attempt { flags.setFontScale(choice.scale) })
    }

    /** Акцент видно на цьому вікні одразу; для всієї системи його дає лише Monet-оверлей. */
    fun onAccent(color: AccentColor) {
        choose { WelcomeFlow.chooseAccent(it, color) }
    }

    fun onLanguage(language: LanguageChoice) {
        choose { WelcomeFlow.chooseLanguage(it, language) }
    }

    fun onRegion(region: RegionChoice) {
        choose { WelcomeFlow.chooseRegion(it, region) }
    }

    fun onHighContrast(enabled: Boolean) {
        choose { WelcomeFlow.chooseHighContrast(it, enabled) }
    }

    fun onNavigation(choice: NavigationChoice) {
        choose { WelcomeFlow.chooseNavigation(it, choice) }
    }

    fun onPower(choice: PowerProfileChoice) {
        choose { WelcomeFlow.choosePower(it, choice) }
    }

    fun onMacPolicy(choice: MacPolicyChoice) {
        choose { WelcomeFlow.chooseMacPolicy(it, choice) }
    }

    fun onNext() {
        if (_state.value.flow.step == WelcomeStep.FINISH) {
            onFinish()
            return
        }
        _state.update { it.copy(flow = WelcomeFlow.advance(it.flow), notice = null) }
        // Завершення граємо на вході в крок «Готово», а не після останньої
        // кнопки: тоді вікно ще живе й звук справді чутно.
        if (_state.value.flow.step == WelcomeStep.FINISH) {
            viewModelScope.launch { cues.play() }
        }
    }

    fun onBack() {
        _state.update { it.copy(flow = WelcomeFlow.back(it.flow), notice = null) }
    }

    fun onSkip() {
        _state.update { it.copy(flow = WelcomeFlow.skip(it.flow), notice = null) }
    }

    /** Відкрити системний екран додавання акаунта; якщо не знаходиться — сказати правду. */
    fun onOpenAccount() {
        val intent = Intent(Settings.ACTION_ADD_ACCOUNT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startActivitySafely(intent)) _state.update { it.copy(notice = R.string.welcome_account_failed) }
    }

    /**
     * Системний екран, який робить те, чого цей модуль не може: мова,
     * доступність, мережа. Формулювання на кроці — «відкриваємо», а не
     * «застосували».
     */
    fun onOpenScreen(screen: SystemScreen) {
        val intent = Intent(actionOf(screen)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startActivitySafely(intent)) _state.update { it.copy(notice = R.string.welcome_screen_failed) }
    }

    /**
     * Рядки інтентів, а не константи `Settings.ACTION_*`: ці екрани дає
     * застосунок налаштувань бази, і значення ми беремо дослівно, щоб не
     * залежати від того, який із них SDK показує як публічний.
     */
    private fun actionOf(screen: SystemScreen): String = when (screen) {
        SystemScreen.LOCALE -> "android.settings.LOCALE_SETTINGS"
        SystemScreen.ACCESSIBILITY -> "android.settings.ACCESSIBILITY_SETTINGS"
        SystemScreen.NETWORK -> "android.settings.WIFI_SETTINGS"
        SystemScreen.SETTINGS -> "android.settings.SETTINGS"
    }

    /**
     * Завершити перший запуск.
     *
     * `markProvisionedSafely()` — той рядок, заради якого весь цей модуль
     * привілейований: після нього система випускає домашній екран і більше
     * ніколи не кличе майстра. Не вийде записати — не мовчимо й не зникаємо:
     * вікно лишається відкритим із прямим текстом про відмову, а кнопка
     * працює як повторна спроба. Закрити екран із повідомленням «нічого не
     * вийшло» було б гірше за помилку — користувач залишився б без нього.
     */
    fun onFinish() {
        val provisioned = flags.markProvisionedSafely()
        record(WriteTarget.PROVISION, provisioned)
        _state.update {
            if (provisioned) {
                it.copy(flow = WelcomeFlow.advance(it.flow), finished = true)
            } else {
                it.copy(notice = R.string.welcome_provision_failed)
            }
        }
    }

    /** Вибір + збереження в файл модуля; про систему тут нічого не вирішується. */
    private fun choose(change: (WelcomeState) -> WelcomeState) {
        _state.update { it.copy(flow = change(it.flow)) }
        prefs.save(_state.value.flow)
    }

    private fun record(target: WriteTarget, succeeded: Boolean) {
        _state.update { it.copy(flow = WelcomeFlow.record(it.flow, target, succeeded)) }
    }

    /** Масштаб шрифта крока — справжній системний, а не замовчування з повітря. */
    private fun currentFontScale(): FontScaleChoice = FontScaleChoice.nearest(flags.fontScale())

    private fun currentTheme(): ThemeChoice = try {
        when (flags.nightMode()) {
            UiModeManager.MODE_NIGHT_YES -> ThemeChoice.DARK
            UiModeManager.MODE_NIGHT_NO -> ThemeChoice.LIGHT
            // Система на автоперемиканні, якого в майстра більше немає: беремо
            // фактичну ніч із конфігурації, а не вгадуємо тему.
            else -> if (systemPrefersDark()) ThemeChoice.DARK else ThemeChoice.LIGHT
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e else ThemeChoice.LIGHT
    }

    private fun systemPrefersDark(): Boolean {
        val night = getApplication<Application>().resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }

    private inline fun attempt(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: Exception) {
        if (e is CancellationException) throw e else false
    }

    private fun hasPackage(name: String): Boolean = try {
        getApplication<Application>().packageManager.getPackageInfo(name, 0) != null
    } catch (e: Exception) {
        // NameNotFoundException і будь-який інший збій пошуку пакунка означають
        // для нас одне й те саме: постачальника немає — і всього.
        if (e is CancellationException) throw e else false
    }

    private fun startActivitySafely(intent: Intent): Boolean = try {
        getApplication<Application>().startActivity(intent)
        true
    } catch (e: Exception) {
        if (e is CancellationException) throw e else false
    }

    companion object {
        /**
         * Пакет, який дає Google-автентифікацію — microG чи справжній GMS, нам
         * однаково.
         */
        const val GMS_CORE_PACKAGE = "com.google.android.gms"
    }
}
