package os.tessera.welcome.data

import android.content.Context
import android.provider.Settings

/**
 * Системні прапорці, які перемальовують не це вікно, а весь телефон.
 *
 * Обидва — `Settings.Secure`/`Settings.Global`, тому кожен виклик потребує
 * `WRITE_SECURE_SETTINGS` (`signature|privileged`). Звичайний застосунок
 * дістає `SecurityException`; саме через цей клас модуль живе в
 * `system_ext/priv-app` із дозвільним списком `privapp-permissions`.
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
        /** Публічних констант для цього рядка в `Settings.Secure` немає. */
        const val UI_NIGHT_MODE = "ui_night_mode"

        /**
         * `Settings.Secure.USER_SETUP_COMPLETE` існує в платформі, але з-під
         * публічного SDK недоступне — тому рядок тут, а не константа.
         */
        const val USER_SETUP_COMPLETE = "user_setup_complete"
    }
}
