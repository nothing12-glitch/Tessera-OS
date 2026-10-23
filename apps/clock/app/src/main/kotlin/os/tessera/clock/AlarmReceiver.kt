package os.tessera.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import os.tessera.clock.data.AlarmStore

/**
 * Приймач спрацювання будильника. Реєстрований статично в маніфесті, бо
 * спрацьовувати мусить, навіть коли застосунок закритий.
 *
 * Експорт свідомо «false»: PendingIntent будуємо явно на наш клас у межах
 * власного uid, тож сторонній застосунок сюди не потрапить.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: ACTION_FIRE
        if (action == ACTION_DISMISS) return // користувач прибрав сповіщення

        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0L) return
        val store = AlarmStore(context)
        val alarm = store.find(id) ?: return

        Notifications.notify(context, alarm)

        if (alarm.repeatDaily) {
            // щоденний: ставимо наступний пік (поточний щойно минув → завтра)
            AlarmScheduler.schedule(context, alarm)
        } else {
            // одноразовий: вимикаємо після одного сигналу
            val updated = store.all().map { if (it.id == id) it.copy(enabled = false) else it }
            store.replaceAll(updated)
            AlarmScheduler.cancel(context, id)
        }
    }

    companion object {
        const val ACTION_FIRE = "os.tessera.clock.action.ALARM_FIRE"
        const val ACTION_DISMISS = "os.tessera.clock.action.ALARM_DISMISS"
        const val EXTRA_ID = "alarm_id"

        fun notificationId(alarmId: Long): Int = (alarmId % Int.MAX_VALUE).toInt()
    }
}
