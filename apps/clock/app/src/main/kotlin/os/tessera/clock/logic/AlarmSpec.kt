package os.tessera.clock.logic

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Один будильник. Чисті дані плюс обчислення наступного спрацювання — без
 * Android, тож «коли саме наступний раз» можна перевірити тестом на фіксованому
 * часовому поясі.
 *
 * [repeatDaily] — «щодня»: після спрацювання лишається активним. Якщо хибно,
 * це одноразовий будильник: приймач вимикає його після одного сигналу.
 */
data class AlarmSpec(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String,
    val repeatDaily: Boolean,
    val enabled: Boolean,
) {

    /**
     * Наступний момент спрацювання в мілісекундах (epoch) або null.
     *
     *  * вимкнений → null;
     *  * сьогоднішні `hour:minute` у [timeZone]; якщо вже минули (або збігаються
     *    з «зараз») — переносимо на завтра.
     *
     * Для щоденного і одноразового це дає той самий наступний пік: різницю
     * робить приймач (одноразовий після сигналу стає `enabled = false`, і
     * наступний виклик уже повертає null). «Одноразовий у минулому котиться на
     * завтра лише якщо ввімкнений» — звідси й походить: без `enabled` ми
     * виходимо раніше з null.
     *
     * Межу трактуємо як «минуле»: спрацювання рівно в `now` не ставимо в
     * минуле, бо AlarmManager і так уже не встигне — тому переносимо на завтра.
     */
    fun nextTriggerMillis(nowMillis: Long, timeZone: TimeZone): Long? {
        if (!enabled) return null
        val calendar = GregorianCalendar(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= nowMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }
}
