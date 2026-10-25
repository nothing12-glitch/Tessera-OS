package os.tessera.welcome.data

import android.content.Context
import androidx.core.content.edit
import os.tessera.welcome.logic.ThemeChoice

/**
 * Те, що переживає закриття екрана: вибір теми й те, чи вітальню вже пройдено.
 *
 * Зберігаємо у власному файлі налаштувань, а не в системних: писати в
 * `Settings.Secure` (наприклад `ui_night_mode`) звичайний застосунок не може, і
 * мовчки робити вигляд, що може, — той самий клас брехні, від якого захищає
 * `check-app-integration.sh`.
 */
class WelcomePrefs(context: Context) {

    private val store = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var theme: ThemeChoice
        get() = ThemeChoice.byName(store.getString(KEY_THEME, null))
        set(value) = store.edit { putString(KEY_THEME, value.name) }

    var acknowledgedVersion: Int
        get() = store.getInt(KEY_ACKNOWLEDGED, 0)
        set(value) = store.edit { putInt(KEY_ACKNOWLEDGED, value) }

    companion object {
        private const val FILE_NAME = "tessera_welcome"
        private const val KEY_THEME = "theme"
        private const val KEY_ACKNOWLEDGED = "acknowledged_version"

        /**
         * Версія вітальні. Підняти — єдиний спосіб показати кроки знову всім,
         * хто вже пройшов попередню (див. `WelcomeFlow.shouldShow`).
         */
        const val ONBOARDING_VERSION = 1
    }
}
