package os.tessera.welcome.data

import android.app.UiModeManager
import android.content.Context
import android.provider.Settings

/**
 * Системні прапорці, які перемальовують не це вікно, а весь телефон.
 *
 * Два різні привілеї, і плутати їх не варто: масштаб і завершення першого
 * запуску — це `Settings.Secure`/`Settings.Global`, тобто `WRITE_SECURE_SETTINGS`;
 * тема — це виклик у `UiModeManager`, тобто `MODIFY_DAY_NIGHT_MODE`. Обидва
 * `signature|privileged`, тому модуль живе в `system_ext/priv-app` із дозвільним
 * списком `privapp-permissions`.
 *
 * Клас тільки робить виклик і нічого не вгадує: чи вийшло, чи ні — вирішує
 * той, хто викликає, і чесно показує це на екрані. З тієї ж причини для теми
 * тут є два окремих читання: `nightMode()` — що система ЗАПИСАНА як бажане,
 * `appliedNightMode()` — який режим сервіс ЗАСТОСОВУЄ просто зараз. Різниця між
 * ними не академічна: сирий запис у Secure дає перше без другого (див. README).
 */
class SystemFlags(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /**
     * Чи система вже вважається налаштованою. Це єдиний прапорець, який
     * вирішує показувати вітальню: власного запису «пройдено» більше немає.
     */
    fun isProvisioned(): Boolean =
        Settings.Global.getInt(resolver, Settings.Global.DEVICE_PROVISIONED, 0) == 1

    /**
     * Тема. Іде через `UiModeManager`, а не через сирий запис у `Secure`.
     *
     * Живий прогін 2026-10-26 на `sdk_gphone64_x86_64-userdebug` (Android 15)
     * виміряв: `Settings.Secure.putInt("ui_night_mode", 2)` значення пише
     * (`settings get secure ui_night_mode` віддає 2), виклик не падає — але
     * `dumpsys uimode` лишається на `mNightMode=1 (no)` / `mCurUiMode=0x11`, тобто
     * телефон НЕ темніє; новий режим з'являється лише після перезавантаження.
     * Керує нічним режимом сам сервіс: після його виклику `mCurUiMode=0x21`
     * стоїть уже в ту ж секунду.
     *
     * Чого сервіс — на відміну від рядка в базі — НЕ обіцяє: на тому ж образі вибрана
     * тема не пережила перезавантаження — рядок у Secure лишився `2`, а сервіс
     * піднявся на `mNightMode=1 (no)`. Тому цей клас нічого не каже про
     * «наступне завантаження»: він каже про поточний стан, і саме його
     * перевіряє [appliedNightMode].
     *
     * Значення збігаються з константами сервісу (`MODE_NIGHT_NO`=1,
     * `MODE_NIGHT_YES`=2, `MODE_NIGHT_AUTO`=0), тому ніякого маппінгу тут немає.
     */
    fun setNightMode(mode: Int) {
        uiModeManager()?.setNightMode(mode)
            ?: throw UnsupportedOperationException("сервіс UiModeManager недоступний")
    }

    /** Поточний системний перемикач теми — звідси й початковий вибір на кроці. */
    fun nightMode(): Int = Settings.Secure.getInt(resolver, UI_NIGHT_MODE, 0)

    /**
     * Який режим сервіс ЗАСТОСОВУЄ просто зараз — синхронно, в тому ж виклику.
     *
     * Оракул саме цей, а не `resources.configuration`: конфігурація доходить до
     * нашого процесу асинхронно, і перевірка «має бути темним» відразу після
     * запису іноді бачила б ще стару тему — тож стан «записували, але не
     * застосовано» міг би зʼявитися на порожньому місці.
     */
    fun appliedNightMode(): Int = uiModeManager()?.nightMode ?: UI_MODE_UNAVAILABLE

    private fun uiModeManager(): UiModeManager? =
        appContext.getSystemService(UiModeManager::class.java)

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
        /**
         * Позначка «сервісу немає» для [appliedNightMode]. Жоден із режимів
         * UiModeManager (0, 1, 2) з ним не збігається, тому перевірка
         * «записане == застосоване» на такому значенні чесно дасть хибну, а не
         * випадково істинну відповідь.
         */
        const val UI_MODE_UNAVAILABLE = -1

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
