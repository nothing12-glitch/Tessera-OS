package os.tessera.camera.logic

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Що саме знімаємо. Тримаємо окремо від CameraX, щоб перемикання тестувалось. */
enum class CaptureMode { Photo, Video }

/** Якою камерою. `Front` може бути недоступна — тоді перемикання не активне. */
enum class Lens { Back, Front }

/** Спалах; `Auto` має сенс лише для фото, у відео він перетворюється на ліхтарик. */
enum class Flash { Off, Auto, On }

/**
 * Як називаємо і куди кладемо знімки.
 *
 * Теки та імена — наші: «Tessera» замість стокового «Camera», щоб у галереї
 * одразу було видно, що зроблено цим застосунком. Рядки живуть у чистому
 * Kotlin, бо `ContentValues` з `android.*` у тесті не провереш.
 */
object MediaNames {
    const val PHOTO_FOLDER = "DCIM/Tessera"
    const val VIDEO_FOLDER = "Movies/Tessera"
    const val PHOTO_MIME = "image/jpeg"
    const val VIDEO_MIME = "video/mp4"
    const val PHOTO_PREFIX = "IMG_"
    const val VIDEO_PREFIX = "VID_"

    private val stamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)

    /** `IMG_20261023_141530.jpg` — формат, до якого звикла будь-яка галерея. */
    fun photoName(momentMillis: Long, zone: ZoneId): String =
        PHOTO_PREFIX + Instant.ofEpochMilli(momentMillis).atZone(zone).format(stamp) + ".jpg"

    fun videoName(momentMillis: Long, zone: ZoneId): String =
        VIDEO_PREFIX + Instant.ofEpochMilli(momentMillis).atZone(zone).format(stamp) + ".mp4"
}

/**
 * Лічильник запису у `mm:ss` / `h:mm:ss`.
 *
 * Відʼємні значення бувають, коли таймер стартує на мілісекунду раніше за
 * подію `Start` — показуємо нуль, а не «-1:59».
 */
fun formatRecordingDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours == 0L) {
        "%d:%02d".format(Locale.ROOT, minutes, rest)
    } else {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, rest)
    }
}
