package os.tessera.halo

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import android.app.KeyguardManager
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import os.tessera.halo.data.HaloPrefs
import os.tessera.halo.logic.ColorBloom
import os.tessera.halo.logic.GlowContext
import os.tessera.halo.logic.HaloConfig
import os.tessera.halo.logic.HaloPlan
import os.tessera.halo.logic.PostedNotification

/**
 * Процес, який тримає край. Роль `NotificationListenerService` обрана
 * свідомо: система сама підтримує його живим, поки користувач не вимкнув
 * доступ до сповіщень, тому нам не потрібен foreground-сервіс із постійним
 * сповіщенням-податком на увагу.
 *
 * Стан «не турбувати» беремо зі слухача (`currentInterruptionFilter`), а не з
 * `Settings.Global` — це дає той самий висновок без `ACCESS_NOTIFICATION_POLICY`.
 */
class HaloListenerService : NotificationListenerService() {

    private lateinit var prefs: HaloPrefs
    private var overlay: HaloOverlay? = null
    private val pending = LinkedHashMap<String, Int>()
    private var lastGlowStartedAt = 0L
    private var receiversRegistered = false

    // Кеш режиму переривань: `onInterruptionFilterChanged` дає його без дозволу.
    private var interruptionFilter = INTERRUPTION_FILTER_ALL

    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Екран згас — край не має чого світити в порожнину.
            if (intent?.action == Intent.ACTION_SCREEN_OFF) overlay?.hide()
        }
    }

    // Плитка Quick Settings вимкнула край, коли той ще горить: гасимо відразу.
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STATE_CHANGED && !prefs.isEnabled()) overlay?.hide()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = HaloPrefs(this)
        overlay = HaloOverlay(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        interruptionFilter = try { currentInterruptionFilter } catch (_: Exception) { INTERRUPTION_FILTER_ALL }
        if (receiversRegistered) return
        registerReceiver(
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            RECEIVER_NOT_EXPORTED,
        )
        registerReceiver(
            stateReceiver,
            IntentFilter(ACTION_STATE_CHANGED),
            RECEIVER_NOT_EXPORTED,
        )
        receiversRegistered = true
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val config = prefs.config()
        val posted = describe(sbn)
        if (!HaloPlan.shouldGlow(posted, config, currentContext())) return

        pending[posted.key] = resolveColor(sbn)
        light(pending.getValue(posted.key), config)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        pending.remove(key(sbn))
        // Користувач прибрав сповіщення руками — світлу більше не чому горіти.
        if (pending.isEmpty()) overlay?.hide()
    }

    /** Режим «не турбувати» змінився: оновлюємо кеш і гасимо край, якщо увімкнули DND. */
    override fun onInterruptionFilterChanged(filter: Int) {
        super.onInterruptionFilterChanged(filter)
        interruptionFilter = filter
        if (zenActive()) overlay?.hide()
    }

    override fun onDestroy() {
        if (receiversRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            runCatching { unregisterReceiver(stateReceiver) }
        }
        receiversRegistered = false
        overlay?.hide()
        overlay = null
        super.onDestroy()
    }

    /**
     * Дістав телефон — край не світить у порожнину: реєструємо лише
     * `ACTION_SCREEN_OFF` і гасимо оверлей. Повторне світло на пробудженні
     * свідомо прибрано — воно суперечило б «не показувати на блокуванні».
     */

    private fun light(color: Int, config: HaloConfig) {
        if (!Settings.canDrawOverlays(this)) return
        val bright = ColorBloom.readableOnDark(color)
        lastGlowStartedAt = SystemClock.elapsedRealtime()
        prefs.saveLastColor(bright)
        overlay?.show(
            color = bright and 0x00FFFFFF,
            pattern = config.pattern,
            intensity = HaloPlan.edgeBrightness(config, currentContext()),
        )
    }

    /** Колір: спершу той, що дав застосунок, інакше бірюзовий резерв. */
    private fun resolveColor(sbn: StatusBarNotification): Int {
        val appColor = runCatching { HaloColors.fromNotification(sbn, this) }.getOrNull() ?: 0
        return HaloPlan.pickColor(appColor, ColorBloom.TEAL) and 0x00FFFFFF
    }

    private fun describe(sbn: StatusBarNotification) = PostedNotification(
        key = key(sbn),
        priority = sbn.notification.priority,
        category = sbn.notification.category,
        ongoing = sbn.isOngoing,
        colorArgb = sbn.notification.color,
        hasPixelSource = sbn.notification.getLargeIcon() != null ||
            sbn.notification.smallIcon != null,
    )

    private fun key(sbn: StatusBarNotification) = "${sbn.packageName}/${sbn.tag}/${sbn.id}"

    private fun currentContext(): GlowContext {
        val (level, charging) = readBattery()
        return GlowContext(
            zenActive = zenActive(),
            inCall = callActive(),
            interactive = powerManager.isInteractive,
            keyguardLocked = runCatching { keyguardManager.isKeyguardLocked }.getOrDefault(false),
            batteryPercent = level,
            charging = charging,
        )
    }

    private fun zenActive(): Boolean = interruptionFilter != INTERRUPTION_FILTER_ALL

    /**
     * Дзвінок визначаємо через `AudioManager.mode` — це публічний стан без
     * `READ_PHONE_STATE`. Вхідний дзвінок піднімає режим у `MODE_RINGING`.
     */
    private fun callActive(): Boolean =
        audioManager.mode != AudioManager.MODE_NORMAL

    /** Sticky-інтент батареї: дає рівень і зарядку без жодного дозволу. */
    private fun readBattery(): Pair<Int, Boolean> {
        val intent = runCatching {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return -1 to false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return percent to (plugged != 0)
    }

    companion object {
        private const val INTERRUPTION_FILTER_ALL = 1

        /** Приватний всередині застосунку: плитка просить слухача перечитати стан. */
        const val ACTION_STATE_CHANGED = "os.tessera.halo.action.STATE_CHANGED"

        // `Settings.Secure.ENABLED_NOTIFICATION_LISTENERS` — приховане поле,
        // тому назву рядка пишемо дослівно, як це роблять усі слухачі сповіщень.
        private const val LISTENERS_SETTING = "enabled_notification_listeners"

        /**
         * Чи бачить система нас чинним слухачем сповіщень — те саме, що
         * показує наш екран у розділі «Доступ до сповіщень».
         */
        fun isListenerEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                LISTENERS_SETTING,
            ) ?: return false
            val mine = ComponentName(context, HaloListenerService::class.java).flattenToString()
            return flat.split(":").any { it == mine }
        }
    }
}
