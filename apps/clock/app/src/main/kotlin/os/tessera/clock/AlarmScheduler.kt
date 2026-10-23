package os.tessera.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.TimeZone
import os.tessera.clock.data.AlarmStore
import os.tessera.clock.logic.AlarmSpec

/**
 * Постановка будильників через [AlarmManager.setAlarmClock] — це єдиний спосіб
 * добитись точного (не делкованого Doze) спрацювання, коли екран вимкнено.
 *
 * setAlarmClock потребує дозвіл на точні будильники. Ключова чесність: якщо
 * [canScheduleExact] хибний, ми НЕ падаємо й не робимо видимого вибору —
 * повертаємо false, а UI показує пояснення й кнопку в налаштування.
 */
object AlarmScheduler {

    fun canScheduleExact(context: Context): Boolean =
        manager(context).canScheduleExactAlarms()

    fun openExactAlarmSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Ставить (оновлює) спрацювання. Повертає false, якщо будильник вимкнений
     * або точні дозволи немає — тоді жодного піта не реєструємо.
     */
    fun schedule(context: Context, alarm: AlarmSpec): Boolean {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return false
        }
        val trigger = alarm.nextTriggerMillis(System.currentTimeMillis(), TimeZone.getDefault())
        if (trigger == null) {
            cancel(context, alarm.id)
            return false
        }
        if (!canScheduleExact(context)) return false
        val manager = manager(context)
        manager.setAlarmClock(
            AlarmManager.AlarmClockInfo(trigger, showIntent(context, alarm.id)),
            triggerIntent(context, alarm),
        )
        return true
    }

    fun cancel(context: Context, id: Long) {
        manager(context).cancel(triggerIntent(context, id))
    }

    /** Після перезавантаження всі setAlarmClock зникають — пере-ставляємо активні. */
    fun rescheduleAll(context: Context): Boolean {
        val store = AlarmStore(context)
        var allowed = true
        store.all().forEach { alarm ->
            if (alarm.enabled && !schedule(context, alarm)) allowed = false
        }
        return allowed
    }

    private fun manager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun triggerIntent(context: Context, alarm: AlarmSpec): PendingIntent =
        triggerIntent(context, alarm.id)

    private fun triggerIntent(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra(AlarmReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun showIntent(context: Context, id: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, ClockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
