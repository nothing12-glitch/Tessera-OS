package os.tessera.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import os.tessera.clock.logic.AlarmSpec
import os.tessera.clock.logic.ClockFormat

/**
 * Сповіщення будильника. Канал створюємо заздалегідь (перед першим post),
 * інакше на Android 8+ повідомлення просто не покажеться.
 */
object Notifications {

    private const val CHANNEL_ID = "alarms"
    private const val CHANNEL_NAME = "Будильники"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Час спрацювання будильників"
            setBypassDnd(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun notify(context: Context, alarm: AlarmSpec) {
        ensureChannel(context)
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            Intent(context, ClockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = ClockFormat.timeOfDay(alarm.hour, alarm.minute)
        val text = alarm.label.ifBlank { context.getString(R.string.app_name) }
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(showIntent)
            .setDeleteIntent(dismissIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        val manager = NotificationManagerCompat.from(context)
        // Без дозволу на сповіщення notify() мовчки нічого не покаже; сам дозвіл
        // запитуємо в активності, тут лише захищаємось від SecurityException.
        runCatching { manager.notify(AlarmReceiver.notificationId(alarm.id), notification) }
    }
}
