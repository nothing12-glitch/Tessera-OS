package os.tessera.halo.data

import android.content.Context
import os.tessera.halo.logic.GlowColor
import os.tessera.halo.logic.GlowPattern
import os.tessera.halo.logic.HaloConfig

/**
 * Налаштування краю. Один файл `tessera_halo`, щоб інспекція
 * `adb shell run-as`/`shared_prefs` показувала рівно те, що бачить користувач.
 */
class HaloPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun config(): HaloConfig = HaloConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        pattern = GlowPattern.byName(prefs.getString(KEY_PATTERN, null)),
        color = GlowColor.byName(prefs.getString(KEY_COLOR, null)),
        durationMs = prefs.getLong(KEY_DURATION, HaloConfig.DEFAULT_DURATION_MS),
        intensity = prefs.getFloat(KEY_INTENSITY, 0.85f),
        onlyLoud = prefs.getBoolean(KEY_ONLY_LOUD, true),
    ).normalized()

    fun save(config: HaloConfig) {
        val normalized = config.normalized()
        prefs.edit()
            .putBoolean(KEY_ENABLED, normalized.enabled)
            .putString(KEY_PATTERN, normalized.pattern.name)
            .putString(KEY_COLOR, normalized.color.name)
            .putLong(KEY_DURATION, normalized.durationMs)
            .putFloat(KEY_INTENSITY, normalized.intensity)
            .putBoolean(KEY_ONLY_LOUD, normalized.onlyLoud)
            .apply()
    }

    /** Останній колір, який край показував, — для світла на ввімкнення екрана. */
    fun lastColor(): Int = prefs.getInt(KEY_LAST_COLOR, 0)

    fun saveLastColor(color: Int) = prefs.edit().putInt(KEY_LAST_COLOR, color).apply()

    companion object {
        const val FILE = "tessera_halo"
        const val KEY_ENABLED = "enabled"
        const val KEY_PATTERN = "pattern"
        const val KEY_COLOR = "color"
        const val KEY_DURATION = "duration_ms"
        const val KEY_INTENSITY = "intensity"
        const val KEY_ONLY_LOUD = "only_loud"
        const val KEY_LAST_COLOR = "last_color"
        const val CHANNEL_ID = "halo_test"
    }
}
