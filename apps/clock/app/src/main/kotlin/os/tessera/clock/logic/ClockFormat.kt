package os.tessera.clock.logic

import java.util.Locale

/**
 * Формати показу часу. Чистий Kotlin: жодних android-імпортів, тож кожен
 * рядок можна перевірити юніт-тестом на JVM.
 *
 * Разделювачі (крапка, двокрапка) фіксовані ASCII, а не беруться з поточної
 * локалі — так само, як у калькуляторі, щоб тест однаково проходив і на
 * збірці з LAUNCH_LOCALE=en, і на пристрої з українською.
 */
object ClockFormat {

    private const val SEC = 1000L
    private const val MIN = 60 * SEC

    /** Фіксований locale: тест має однаково виходити на збірці з LAUNCH_LOCALE=en. */
    private fun fmt(pattern: String, vararg args: Any): String =
        String.format(Locale.ROOT, pattern, *args)

    /**
     * Секундомір: `mm:ss.d` до години і `h:mm:ss.d` після. Дробову частку
     * округлюємо ВНИЗ — секундомір рахує «скільки вже минуло», тож 1234 мс
     * показуємо як `00:01.2`, а не `00:01.3`.
     */
    fun stopwatch(ms: Long): String {
        val clamped = if (ms < 0) 0 else ms
        val tenth = (clamped / 100) % 10
        val totalSeconds = clamped / SEC
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            fmt("%d:%02d:%02d.%d", h, m, s, tenth)
        } else {
            fmt("%02d:%02d.%d", m, s, tenth)
        }
    }

    /**
     * Зворотний відлік таймера: завжди `h:mm:ss`. Секунди округлюємо ВГОРУ,
     * бо відлік показує «скільки залишилось»: 299999 мс ще не «4:59», а рівно
     * «5:00» — інакше цифра стрибнула б вниз раніше, ніж справді минув час.
     */
    fun countdown(ms: Long): String {
        val clamped = if (ms < 0) 0 else ms
        val totalSeconds = (clamped + SEC - 1) / SEC // округлення до цілої секунди вгору
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return fmt("%d:%02d:%02d", h, m, s)
    }

    /** Час доби для списку будильників: «07:05» з обидвома нулями. */
    fun timeOfDay(hour: Int, minute: Int): String {
        val h = if (hour in 0..23) hour else 0
        val m = if (minute in 0..59) minute else 0
        return fmt("%02d:%02d", h, m)
    }

    /**
     * Людський рядок «через скільки»: «8 год 12 хв», «12 хв», «3 год».
     * Скорочення одиниць («год», «хв») не відмінюємо — так само, як показує
     * системний годинник, і приклад із завдання («8 год 12 хв»).
     */
    fun until(ms: Long): String {
        if (ms < MIN) return "менше хвилини"
        val totalMinutes = ms / MIN
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "$h год $m хв"
            h > 0 -> "$h год"
            else -> "$m хв"
        }
    }
}
