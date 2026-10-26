package os.tessera.welcome.data

import android.content.Context
import os.tessera.welcome.logic.AccentColor
import os.tessera.welcome.logic.FontScaleChoice
import os.tessera.welcome.logic.LanguageChoice
import os.tessera.welcome.logic.MacPolicyChoice
import os.tessera.welcome.logic.NavigationChoice
import os.tessera.welcome.logic.PowerProfileChoice
import os.tessera.welcome.logic.RegionChoice
import os.tessera.welcome.logic.ThemeChoice
import os.tessera.welcome.logic.WelcomeState

/**
 * Те, що майстер вирішив, але чого `WRITE_SECURE_SETTINGS` не покриває: мова,
 * навігація, профіль живлення, політика MAC, акцент і контраст. Лежить у
 * власному файлі `tessera_welcome` — це намір на майбутнє, а не обіцянка,
 * яку система не знає.
 *
 * «Проходив чи ні майстер» тут не зберігається: єдиний критерій — системний
 * `device_provisioned`.
 */
class ModulePrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Початковий стан: незнайоме чи відсутнє значення падає на замовчування кроку. */
    fun load(): WelcomeState = WelcomeState(
        theme = ThemeChoice.byName(prefs.getString(KEY_THEME, null)),
        accent = AccentColor.byName(prefs.getString(KEY_ACCENT, null)),
        language = LanguageChoice.byTag(prefs.getString(KEY_LANGUAGE, null)),
        region = RegionChoice.byCode(prefs.getString(KEY_REGION, null)),
        fontScale = FontScaleChoice.byName(prefs.getString(KEY_FONT_SCALE, null)),
        highContrast = prefs.getBoolean(KEY_CONTRAST, false),
        navigation = NavigationChoice.byName(prefs.getString(KEY_NAVIGATION, null)),
        power = PowerProfileChoice.byName(prefs.getString(KEY_POWER, null)),
        macPolicy = MacPolicyChoice.byName(prefs.getString(KEY_MAC, null)),
    )

    fun save(state: WelcomeState) {
        prefs.edit()
            .putString(KEY_THEME, state.theme.name)
            .putString(KEY_ACCENT, state.accent.name)
            .putString(KEY_LANGUAGE, state.language.tag)
            .putString(KEY_REGION, state.region.code)
            .putString(KEY_FONT_SCALE, state.fontScale.name)
            .putBoolean(KEY_CONTRAST, state.highContrast)
            .putString(KEY_NAVIGATION, state.navigation.name)
            .putString(KEY_POWER, state.power.name)
            .putString(KEY_MAC, state.macPolicy.name)
            .apply()
    }

    private companion object {
        const val FILE = "tessera_welcome"
        const val KEY_THEME = "theme"
        const val KEY_ACCENT = "accent"
        const val KEY_LANGUAGE = "language"
        const val KEY_REGION = "region"
        const val KEY_FONT_SCALE = "font_scale_choice"
        const val KEY_CONTRAST = "high_contrast"
        const val KEY_NAVIGATION = "navigation"
        const val KEY_POWER = "power_profile"
        const val KEY_MAC = "mac_policy"
    }
}
