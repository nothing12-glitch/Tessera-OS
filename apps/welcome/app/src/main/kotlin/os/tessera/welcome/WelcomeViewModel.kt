package os.tessera.welcome

import android.app.Application
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
import os.tessera.welcome.data.WelcomePrefs
import os.tessera.welcome.logic.AccountRoute
import os.tessera.welcome.logic.ThemeChoice
import os.tessera.welcome.logic.WelcomeFlow
import os.tessera.welcome.logic.WelcomeState
import os.tessera.welcome.logic.WelcomeStep
import os.tessera.welcome.logic.accountRoute

/** Що бачить екран: машина кроків + результат пошуку GmsCore + одноразове попередження. */
data class WelcomeUiState(
    val flow: WelcomeState = WelcomeState(),
    val account: AccountRoute = AccountRoute.UNAVAILABLE,
    val buildLabel: String = "",
    /** Ідентифікатор рядка: VM не знає текстів, текст знає UI. */
    val notice: Int? = null,
    val finished: Boolean = false,
)

/**
 * Android-шар вітальні.
 *
 * Тут усе, чого не може логіка: перевірити наявність GmsCore, відкрити
 * Налаштування, зберегти вибір між запусками. Порядок кроків звідси не
 * вирішується — він у [WelcomeFlow].
 */
class WelcomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = WelcomePrefs(application)

    private val _state = MutableStateFlow(
        WelcomeUiState(
            flow = WelcomeState(theme = prefs.theme),
            buildLabel = Build.DISPLAY ?: "",
        ),
    )
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()

    /**
     * Чи закриватися, не показавши нічого. Працює лише для старту «від системи»:
     * людина, яка сама відкрила застосунок з лаунчера, бачить кроки завжди.
     */
    fun skipWhenAlreadyPassed(fromLauncher: Boolean): Boolean {
        if (fromLauncher) return false
        val acknowledged = prefs.acknowledgedVersion
        return !WelcomeFlow.shouldShow(
            finished = acknowledged > 0,
            acknowledgedVersion = acknowledged,
            currentVersion = WelcomePrefs.ONBOARDING_VERSION,
        )
    }

    init {
        viewModelScope.launch {
            val found = listOf(GMS_CORE_PACKAGE).filter { hasPackage(it) }
            _state.update { it.copy(account = accountRoute(found, GMS_CORE_PACKAGE)) }
        }
    }

    fun onTheme(choice: ThemeChoice) {
        _state.update { it.copy(flow = WelcomeFlow.chooseTheme(it.flow, choice)) }
        prefs.theme = choice
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
     * Відкрити Налаштування → Дисплей. Лише цей шлях існує насправді:
     * системну тему з власного вікна по-іншому не переключити, бо публічного
     * виклику для цього немає (README пояснює чому).
     */
    fun onOpenDisplaySettings() {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startActivitySafely(intent)) _state.update { it.copy(notice = R.string.welcome_theme_failed) }
    }

    fun onFinish() {
        prefs.acknowledgedVersion = WelcomePrefs.ONBOARDING_VERSION
        _state.update { it.copy(flow = WelcomeFlow.advance(it.flow), finished = true) }
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
