package os.tessera.welcome.data

import android.content.Context
import android.provider.Settings

/**
 * Системні прапорці, які перемальовують не це вікно, а весь телефон.
 *
 * Усе це `Settings.Secure`/`Settings.Global`, тому кожен запис потребує
 * `WRITE_SECURE_SETTINGS` (`signature|privileged`). Звичайний застосунок
 * дістає `SecurityException`; саме через цей клас модуль живе в
 * `system_ext/priv-app` із дозвільним списком `privapp-permissions`.
 *
 * Клас тільки робить виклик і нічого не вгадує: чи вийшло, чи ні — вирішує
 * той, хто викликає, і чесно показує це на екрані.
 */
class SystemFlags(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    /**
     * Чи система вже вважається налаштованою. Це єдиний прапорець, який
     * вирішує показувати вітальню: власного запису «пройдено» більше немає.
     */
    fun isProvisioned(): Boolean =
        Settings.Global.getInt(resolver, Settings.Global.DEVICE_PROVISIONED, 0) == 1

    /**
     * Те саме значення, яке пише перемикач у Налаштуваннях → Дисплей, — тож
     * результат бачить увесь пристрій, а не одне вікно.
     */
    fun setNightMode(mode: Int) {
        Settings.Secure.putInt(resolver, UI_NIGHT_MODE, mode)
    }

    /** Поточний системний перемикач теми — звідси й початковий вибір на кроці. */
    fun nightMode(): Int = Settings.Secure.getInt(resolver, UI_NIGHT_MODE, 0)

    /**
     * Масштаб шрифта для всієї системи. Рядок `font_scale` читає
     * AccessibilityManagerService — запис тут не імітація; чи справді зростає
     * текст, перевіряється на живому образі (див. README).
     */
    fun setFontScale(scale: Float) {
        Settings.Secure.putFloat(resolver, FONT_SCALE, scale)
    }

    /** Поточний системний масштаб шрифта; 1.0 — якщо прочитати не вдалося. */
    fun fontScale(): Float = runCatching {
        Settings.Secure.getFloat(resolver, FONT_SCALE, 1f)
    }.getOrDefault(1f)

    /**
     * Завершити перший запуск: `device_provisioned` знімає блокування лаунчера,
     * `user_setup_complete` сповіщає решту застосунків, що налаштування сталося.
     *
     * Повертає `false`, коли дозволу немає. Мовчки «зробити вигляд» тут було б
     * гірше за помилку: користувач дізнався б про це лише з того, що телефон
     * кличе майстра ще раз.
     */
    fun markProvisionedSafely(): Boolean = try {
        Settings.Global.putInt(resolver, Settings.Global.DEVICE_PROVISIONED, 1)
        Settings.Secure.putInt(resolver, USER_SETUP_COMPLETE, 1)
        true
    } catch (e: SecurityException) {
        false
    }

    private companion object {
        /** Публічних констант для цих рядків у `Settings.Secure` немає. */
        const val UI_NIGHT_MODE = "ui_night_mode"

        /**
         * Публічний `Settings.Secure.FONT_SCALE` позначений застарілим, тому
         * рядок тримаємо тут — значення те саме, ніякого нового ключа не вводить.
         */
        const val FONT_SCALE = "font_scale"

        /**
         * `Settings.Secure.USER_SETUP_COMPLETE` існує в платформі, але з-під
         * публічного SDK недоступне — тому рядок тут, а не константа.
         */
        const val USER_SETUP_COMPLETE = "user_setup_complete"
    }
}
