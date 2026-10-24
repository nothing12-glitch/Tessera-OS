package os.tessera.camera.render

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import java.time.ZoneId
import os.tessera.camera.logic.CropRect
import os.tessera.camera.logic.EditParams
import os.tessera.camera.logic.ImageTransform
import os.tessera.camera.logic.MediaNames

/** Кадр, який редактор тримає в памʼяті: посилання на файл і сам бітмап. */
data class Decoded(val uri: Uri, val bitmap: Bitmap) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
}

/**
 * Перекодування знімка за правками користувача.
 *
 * Порядок той самий, що рахує чиста логіка у [ImageTransform]: поворот →
 * обрізання → тон. Розійдуться — користувач побачить одне, а збережеться
 * інше, тому обидві частини читають один [EditParams].
 *
 * Проміжних бітмапів не `recycle()`: з API 26 вони на загальній купі, а
 * примусове знищення ламає превʼю, яке ще малюється цим кадром.
 */
object ImageBake {

    /**
     * Декодуємо через [ImageDecoder]: він сам застосовує EXIF-поворот, тож
     * JPEG із телефона приходить у редактор уже «вертикальним».
     *
     * `sampleSize` — ступінь двійки: лише так декодер робить мінімум роботи й
     * не зʼїдає памʼять на великих оригіналах.
     */
    fun decode(context: Context, uri: Uri, longEdge: Int): Decoded {
        val drawable = ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(context.contentResolver, uri)
        ) { decoder, info, _ ->
            val size = info.size
            decoder.setTargetSampleSize(powerOfTwoSample(maxOf(size.width, size.height), longEdge))
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return Decoded(uri, bitmap)
    }

    /** 1, 2, 4, 8… — найменший ступінь двійки, за якого кадр влізає в `longEdge`. */
    fun powerOfTwoSample(longestEdge: Int, longEdge: Int): Int {
        if (longestEdge <= longEdge || longEdge <= 0) return 1
        var sample = 1
        while (longestEdge / (sample * 2) >= longEdge) sample *= 2
        return sample
    }

    /** Готовий кадр з усіма правками; оригінал на диску лишається недоторканим. */
    fun apply(source: Bitmap, params: EditParams): Bitmap {
        val rotated = rotate(source, params.turns)
        val rect = ImageTransform.cropRect(rotated.width, rotated.height, params.aspect)
        return tone(crop(rotated, rect), params)
    }

    private fun rotate(source: Bitmap, turns: Int): Bitmap {
        val degrees = (turns * 90).toFloat()
        if (degrees == 0f) return source
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private fun crop(source: Bitmap, rect: CropRect): Bitmap {
        if (rect.left == 0 && rect.top == 0 && rect.width == source.width && rect.height == source.height) {
            return source
        }
        return Bitmap.createBitmap(source, rect.left, rect.top, rect.width, rect.height)
    }

    private fun tone(source: Bitmap, params: EditParams): Bitmap {
        if (params.brightness == 0f && params.contrast == 0f) return source
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ImageTransform.colorMatrixValues(params))
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /** Живий фільтр для превʼю: повзунки яскравості малюються без перекодування. */
    fun toneFilter(params: EditParams): ColorFilter =
        ColorFilter.colorMatrix(ColorMatrix(ImageTransform.colorMatrixValues(params)))

    /**
     * Збереження відредагованої копії.
     *
     * Тека `Pictures/Tessera`, а не `DCIM/Tessera`: це вже не знімок з камери,
     * і галерея має розрізняти оригінали й виправлені копії.
     */
    fun saveEdited(context: Context, bitmap: Bitmap, moment: Long): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, MediaNames.photoName(moment, ZoneId.systemDefault()))
            put(MediaStore.MediaColumns.MIME_TYPE, MediaNames.PHOTO_MIME)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Tessera/",
            )
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        val stream = context.contentResolver.openOutputStream(uri) ?: return null
        val written = stream.use { bitmap.compress(Bitmap.CompressFormat.JPEG, ImageTransform.JPEG_QUALITY, it) }
        if (!written) context.contentResolver.delete(uri, null, null)
        return if (written) uri else null
    }
}
