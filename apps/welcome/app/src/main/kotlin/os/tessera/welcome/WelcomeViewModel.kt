package os.tessera.welcome

import android.app.Application
import android.app.UiModeManager
import android.content.Intent
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
import os.tessera.welcome.data.SystemFlags
import os.tessera.welcome.logic.AccountRoute
import os.tessera.welcome.logic.ThemeChoice
import os.tessera.welcome.logic.WelcomeFlow
import os.tessera.welcome.logic.WelcomeState
import os.tessera.welcome.logic.WelcomeStep
import os.tessera.welcome.logic.accountRoute
import os.tessera.welcome.logic.nightMode

/** Що бачить екран: машина кроків + результат пошуку GmsCore + одноразове попередження. */
data class WelcomeUiState(
    val flow: WelcomeState = WelcomeState(),
    val account: AccountRoute = AccountRoute.UNAVAILABLE,
    val buildLabel: String = "",
    /** Чи справді системний перемикач теми зрушився з місця. */
    val themeApplied: Boolean = false,
    /** Ідентифікатор рядка: VM не знає текстів, текст знає UI. */
    val notice: Int? = null,
    val finished: Boolean = false,
)

/**
 * Android-шар вітальні.
 *
 * Тут усе, чого не може логіка: перевірити наявність GmsCore, відкрити
 * екран додавання акаунта, і — головне — писати системні прапорці. Порядок
 * кроків звідси не вирішується — він у [WelcomeFlow].
 */
class WelcomeViewModel(application: Application) : AndroidViewModel(application) {

    private val flags = SystemFlags(application)

    private val _state = MutableStateFlow(
        WelcomeUiState(
            flow = WelcomeState(theme = currentTheme()),
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
     * Вибір теми. Пишемо `Settings.Secure.ui_night_mode` — те саме значення,
     * яке змінює перемикач у Налаштуваннях, тож ефект бачить увесь пристрій.
     *
     * Дозвіл рівня системи, тому виклик може кинути `SecurityException`: тоді
     * кажемо про це прямим текстом на екрані, а не робимо вигляд, що вийшло.
     */
    fun onTheme(choice: ThemeChoice) {
        _state.update { it.copy(flow = WelcomeFlow.chooseTheme(it.flow, choice)) }
        val mode = choice.nightMode(
            auto = UiModeManager.MODE_NIGHT_AUTO,
            light = UiModeManager.MODE_NIGHT_NO,
            dark = UiModeManager.MODE_NIGHT_YES,
        )
        _state.update { it.copy(themeApplied = writeNightMode(mode)) }
    }

    fun onNext() {
        if (_state.value.flow.step == WelcomeStep.FINISH) {
            onFinish()
        } else {
            _state.update { it.copy(flow = WelcomeFlow.advance(it.flow)) }
        }
    }

    fun onBack() {
        _state.update { it.copy(flow = WelcomeFlow.back(it.flow)) }
    }

    fun onSkipAccount() {
        _state.update { it.copy(flow = WelcomeFlow.skipAccount(it.flow)) }
    }

    /** Відкрити системний екран додавання акаунта; якщо він не знаходиться — сказати правду. */
    fun onOpenAccount() {
        val intent = Intent(Settings.ACTION_ADD_ACCOUNT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startActivitySafely(intent)) _state.update { it.copy(notice = R.string.welcome_account_failed) }
    }

    /**
     * Завершити перший запуск.
     *
     * `markProvisioned()` — той рядок, заради якого весь цей модуль
     * привілейований: після нього система випускає лаунчер і більше ніколи не
     * кличе майстра. Не вийде записати — не мовчимо: попередження на екрані, і
     * далі прапорець підніме хтось інший.
     */
    fun onFinish() {
        if (!flags.markProvisionedSafely()) {
            _state.update { it.copy(notice = R.string.welcome_provision_failed) }
        }
        _state.update { it.copy(flow = WelcomeFlow.advance(it.flow), finished = true) }
    }

    private fun currentTheme(): ThemeChoice = try {
        when (flags.nightMode()) {
            UiModeManager.MODE_NIGHT_NO -> ThemeChoice.LIGHT
            UiModeManager.MODE_NIGHT_YES -> ThemeChoice.DARK
            else -> ThemeChoice.AUTO
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e else ThemeChoice.AUTO
    }

    private fun writeNightMode(mode: Int): Boolean = try {
        flags.setNightMode(mode)
        true
    } catch (e: Exception) {
        if (e is CancellationException) throw e else false
    }

    private fun hasPackage(name: String): Boolean = try {
        getApplication<Application>().packageManager.getPackageInfo(name, 0) != null
    } catch (e: Exception) {
        // NameNotFoundException і будь-який інший збій пошуку пакунка означають
        // для нас одне й того самого: постачальника немає — і всього.
        if (e is CancellationException) throw e else false
    }

    private fun startActivitySafely(intent: Intent): Boolean = try {
        getApplication<Application>().startActivity(intent)
        true
    } catch (e: Exception) {
        if (e is CancellationException) throw e else false
    }

    companion object {
        /** Пакет, який дає Google-автентифікацію — microG чи справжній GMS, нам однаково. */
        const val GMS_CORE_PACKAGE = "com.google.android.gms"
    }
}
