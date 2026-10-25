package os.tessera.halo

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import os.tessera.halo.data.HaloPrefs
import os.tessera.halo.logic.ColorBloom
import os.tessera.halo.logic.GlowColor
import os.tessera.halo.logic.HaloConfig
import os.tessera.halo.logic.HaloPlan
import os.tessera.halo.logic.PostedNotification

/**
 * Процес, який тримає край. Роль `NotificationListenerService` обрана
 * свідомо: система сама підтримує його живим, поки користувач не вимкнув
 * доступ до сповіщень, тому нам не потрібен foreground-сервіс із постійним
 * сповіщенням-податком на увагу.
 */
class HaloListenerService : NotificationListenerService() {

    private lateinit var prefs: HaloPrefs
    private var overlay: HaloOverlay? = null
    private val pending = LinkedHashMap<String, Int>()
    private var lastColor = 0
    private var lastGlowStartedAt = 0L
    private var receiversRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) glowOnWake()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = HaloPrefs(this)
        overlay = HaloOverlay(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (receiversRegistered) return
        registerReceiver(
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            RECEIVER_NOT_EXPORTED,
        )
        receiversRegistered = true
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val config = prefs.config()
        val posted = describe(sbn)
        if (!HaloPlan.shouldGlow(posted, config, isZenActive())) return

        pending[posted.key] = resolveColor(sbn, config)
        light(pending.getValue(posted.key), config)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        pending.remove(key(sbn))
        // Користувач прибрав сповіщення руками — світлу більше не чому горіти.
        if (pending.isEmpty()) overlay?.hide()
    }

    override fun onDestroy() {
        if (receiversRegistered) runCatching { unregisterReceiver(screenReceiver) }
        receiversRegistered = false
        overlay?.hide()
        overlay = null
        super.onDestroy()
    }

    /** Дістав телефон — показуємо те, що накопичилось, якщо край уже згас. */
    private fun glowOnWake() {
        val config = prefs.config()
        if (!config.enabled || pending.isEmpty()) return
        if (!HaloPlan.allowReGlow(SystemClock.elapsedRealtime(), lastGlowStartedAt, config)) return
        val color = pending.values.lastOrNull()?.takeIf { it != 0 } ?: lastColor
        if (color != 0) light(color, config)
    }

    private fun light(color: Int, config: HaloConfig) {
        if (!Settings.canDrawOverlays(this)) return
        val bright = ColorBloom.readableOnDark(color)
        lastColor = bright
        lastGlowStartedAt = SystemClock.elapsedRealtime()
        prefs.saveLastColor(bright)
        overlay?.show(
            color = bright and 0x00FFFFFF,
            pattern = config.pattern,
            durationMs = config.durationMs,
            intensity = config.intensity,
        )
    }

    private fun resolveColor(sbn: StatusBarNotification, config: HaloConfig): Int =
        if (config.color == GlowColor.MONET) {
            HaloColors.monetAccent(this)
        } else {
            runCatching { HaloColors.fromNotification(sbn, this) }.getOrNull()
                ?: HaloColors.monetAccent(this)
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

    /**
     * «Не турбувати». Публічного геттера в framework немає (`isInZenMode`
     * прихований), тому читаємо той самий global-ключ, який тримає систему:
     * 0 — режим вимкнено. Не читається — теж вважаємо 0 і світимось як зазвичай.
     */
    private fun isZenActive(): Boolean =
        runCatching { Settings.Global.getInt(contentResolver, "zen_mode", ZEN_OFF) }
            .getOrDefault(ZEN_OFF) != ZEN_OFF

    companion object {
        /** Значення `zen_mode`, коли «Не турбувати» вимкнено. */
        private const val ZEN_OFF = 0

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
