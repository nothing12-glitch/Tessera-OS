package os.tessera.dialer.logic

/** Формати, які бачить користувач у журналі викликів. */
object CallFormat {

    private const val DAY_MILLIS = 86_400_000L
    private const val MINUTE_MILLIS = 60_000L
    private const val MINUTES_PER_DAY = 1440L

    /** Тривалість розмови: `0:00`, `2:14`, `1:05:09`. */
    fun duration(sec: Long): String {
        val total = if (sec < 0) 0 else sec
        val s = total % 60
        val m = (total / 60) % 60
        val h = total / 3600
        return if (h > 0) "$h:%02d:%02d".format(m, s) else "$m:%02d".format(s)
    }

    /** Час доби у форматі 24 години для заданого зсуву від UTC. */
    fun timeOfDay(epochMillis: Long, offsetMinutes: Int): String {
        // floorMod, а не %: відʼємний момент (до епохи) інакше дав би «-1:59»
        val minutesOfDay = Math.floorMod(
            Math.floorDiv(epochMillis + offsetMinutes * MINUTE_MILLIS, MINUTE_MILLIS),
            MINUTES_PER_DAY,
        )
        val h = minutesOfDay / 60
        val m = minutesOfDay % 60
        return "%02d:%02d".format(h, m)
    }

    /** Номер доби (у днях від епохи) з урахуванням місцевого зсуву. */
    fun epochDay(epochMillis: Long, offsetMinutes: Int): Long =
        Math.floorDiv(epochMillis + offsetMinutes * MINUTE_MILLIS, DAY_MILLIS)

    fun dayDiff(recordDay: Long, todayDay: Long): Long = todayDay - recordDay

    /**
     * Заголовок групи днів. Абсолютну дату вималює викликаний `absolute` —
     * так ця функція лишається чистою і тестується без календаря.
     */
    fun groupLabel(
        dayDiff: Long,
        labels: DayLabels,
        absolute: () -> String,
    ): String = when (dayDiff) {
        0L -> labels.today
        1L -> labels.yesterday
        else -> absolute()
    }

    /**
     * Пошук у журналі. Працює три способи: пряма частина імені чи номера,
     * цифри набраного номера та Т9 — цифри, які літери імені дають на
     * клавіатурі набору.
     *
     * Т9 покриває лише літери латиниці: кириличний розклад клавіатури
     * стандартом не визначений, а вигадувати власний і називати його
     * «звичним» не хочеться.
     */
    fun filter(query: String, records: List<CallRecord>): List<CallRecord> {
        val q = query.trim()
        if (q.isEmpty()) return records
        return records.filter { record ->
            matches(q, record.contactName.orEmpty()) || matches(q, record.number)
        }
    }

    private fun matches(query: String, value: String): Boolean =
        value.isNotEmpty() &&
            (value.contains(query, ignoreCase = true) || PhoneNumber.matchesT9(value, query))
}

data class DayLabels(val today: String, val yesterday: String)
