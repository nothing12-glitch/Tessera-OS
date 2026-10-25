package os.tessera.halo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import os.tessera.halo.data.HaloPrefs
import os.tessera.halo.logic.GlowColor
import os.tessera.halo.logic.GlowPattern
import os.tessera.halo.logic.HaloConfig

data class HaloUiState(
    val config: HaloConfig = HaloConfig(),
    val canDrawOverlays: Boolean = false,
    val listenerGranted: Boolean = false,
    val canPostNotifications: Boolean = false,
    val accentArgb: Int = 0,
    val notice: String? = null,
)

/**
 * Стан екрана Halo. Тут немає ніякої власної думки про світло: екран лише
 * перераховує те, що дозволила система, і тому не може показати «увімкнено»,
 * коли система нас не пускає.
 */
class HaloViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = HaloPrefs(application)
    private val _state = MutableStateFlow(HaloUiState(config = prefs.config()))
    val state: StateFlow<HaloUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val context = getApplication<Application>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        _state.value = _state.value.copy(
            config = prefs.config(),
            canDrawOverlays = Settings.canDrawOverlays(context),
            listenerGranted = HaloListenerService.isListenerEnabled(context),
            canPostNotifications = manager.areNotificationsEnabled(),
            accentArgb = HaloColors.monetAccent(context),
        )
    }

    fun onEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }
    fun onPattern(pattern: GlowPattern) = update { it.copy(pattern = pattern) }
    fun onColor(color: GlowColor) = update { it.copy(color = color) }
    fun onDuration(ms: Long) = update { it.copy(durationMs = ms) }
    fun onIntensity(value: Float) = update { it.copy(intensity = value) }
    fun onOnlyLoud(onlyLoud: Boolean) = update { it.copy(onlyLoud = onlyLoud) }

    fun onOpenOverlayGrant() {
        val context = getApplication<Application>()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.startSafely(intent)) {
            say(context.getString(R.string.halo_notice_overlay))
        }
    }

    fun onOpenListenerSettings() {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!context.startSafely(intent)) {
            say(context.getString(R.string.halo_notice_no_listener_screen))
        }
    }

    /**
     * Перевірка і демо одночасно: власне сповіщення, яке бачить наш слухач,
     * тож край світиться без очікування на «справжнє» сповіщення від
     * іншого застосунку.
     */
    fun onTest() {
        val context = getApplication<Application>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        if (!manager.areNotificationsEnabled()) {
            say(context.getString(R.string.halo_notice_post_denied))
            return
        }
        if (!HaloListenerService.isListenerEnabled(context)) {
            say(context.getString(R.string.halo_notice_need_listener))
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            say(context.getString(R.string.halo_notice_need_overlay))
            return
        }
        val accent = HaloColors.monetAccent(context)
        val notification = NotificationCompat.Builder(context, HaloPrefs.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_halo_glyph)
            .setContentTitle(context.getString(R.string.halo_test_title))
            .setContentText(context.getString(R.string.halo_test_body))
            .setColor(accent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(TEST_ID, notification)
        refresh()
    }

    private fun update(change: (HaloConfig) -> HaloConfig) {
        val saved = change(prefs.config()).normalized()
        prefs.save(saved)
        _state.value = _state.value.copy(config = saved)
    }

    private fun say(message: String) {
        _state.value = _state.value.copy(notice = message)
    }

    private fun ensureChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(
                HaloPrefs.CHANNEL_ID,
                getApplication<Application>().getString(R.string.halo_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getApplication<Application>().getString(R.string.halo_channel_body)
            },
        )
    }

    private fun Context.startSafely(intent: Intent): Boolean =
        runCatching { startActivity(intent) }.isSuccess

    companion object {
        private const val TEST_ID = 4242
    }
}
