package os.tessera.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Після перезавантаження система знімає всі setAlarmClock. Цей приймач
 * пере-ставить активні будильники, інакше «ввімкнений» пережив би перезапуск
 * лише на папері.
 *
 * BOOT_COMPLETED надсилає система (чужий uid), тому цей приймач — єдиний
 * експортований компонент застосунку.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AlarmScheduler.rescheduleAll(context)
    }
}
