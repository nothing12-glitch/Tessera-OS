package os.tessera.files.logic

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Показ розмірів і дат. Одиниці двійкові (КіБ/МіБ), як у налаштуваннях
 * сховища Android, щоб «1,2 МБ» означало те саме, що й у системі.
 */
object Formatters {

    private const val KB = 1024L
    private const val MB = KB * 1024
    private const val GB = MB * 1024
    private const val TB = GB * 1024
    private const val NBSP = "\u00A0"

    private val dayFormat = SimpleDateFormat("d MMM yyyy", Locale.UK)

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun size(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < KB) return "$bytes$NBSP" + "Б"
        val value: Double
        val unit: String
        when {
            bytes < MB -> { value = bytes.toDouble() / KB; unit = "КіБ" }
            bytes < GB -> { value = bytes.toDouble() / MB; unit = "МіБ" }
            bytes < TB -> { value = bytes.toDouble() / GB; unit = "ГіБ" }
            else -> { value = bytes.toDouble() / TB; unit = "ТіБ" }
        }
        val text = if (value >= 100) "%.0f".format(value) else "%.1f".format(value)
        return text.replace('.', ',') + NBSP + unit
    }

    /** «-» замість «0 КіБ» для тек, де Android не повертає розмір. */
    fun sizeOrUnknown(bytes: Long): String = if (bytes <= 0) "—" else size(bytes)

    fun daysBetween(fromMillis: Long, toMillis: Long): Long =
        (toMillis - fromMillis) / DAY_MILLIS

    /** «вчора», «3 дні тому», далі — звичайна дата. */
    fun relative(millis: Long, nowMillis: Long): String {
        if (millis <= 0) return "—"
        val days = daysBetween(millis, nowMillis)
        return when {
            days <= 0 -> "сьогодні"
            days == 1L -> "учора"
            days < 30 -> "$days ${plural(days.toInt(), "день", "дні", "днів")} тому"
            else -> dayFormat.format(Date(millis))
        }
    }

    /** Українська: 1 день, 2 дні, 5 днів; для жіночого роду ті самі форми. */
    fun plural(count: Int, one: String, few: String, many: String): String {
        val mod100 = count % 100
        if (mod100 in 11..14) return many
        return when (val mod10 = count % 10) {
            1 -> one
            in 2..4 -> few
            else -> many
        }
    }
}
