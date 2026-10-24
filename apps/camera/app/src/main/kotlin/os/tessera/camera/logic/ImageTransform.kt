package os.tessera.camera.logic

import kotlin.math.roundToInt

/**
 * Усі три правки, які бачить користувач в редакторові, у вигляді чисел.
 *
 * Порядок застосування фіксовано: спершу поворот, потім обрізання вже
 * повернутого кадра, наприкінці тон. Інакше «обрізати до квадрата» після
 * повороту на 90° давало б інший прямокутник, і користувач бачив би не те,
 * що збережеться.
 */
data class EditParams(
    val quarterTurns: Int = 0,
    val aspect: CropAspect = CropAspect.Original,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
) {
    val turns: Int get() = ImageTransform.normalizeTurns(quarterTurns)

    /** Чи можна «Зберегти» проїхати без перекодування — якщо правок немає. */
    val untouched: Boolean
        get() = turns == 0 && aspect == CropAspect.Original &&
            brightness == 0f && contrast == 0f
}

object ImageTransform {
    /** Середина діапазону яскравості: навколо неї й «крутиться» контраст. */
    const val MID_GREY = 127.5f

    /** Повний діапазон одного каналу — стільки ж додає `brightness == 1f`. */
    const val FULL_SCALE = 255f

    /** Юніт-якість: 92 — це те, що дає видимих втрат менше, ніж видно. */
    const val JPEG_QUALITY = 92

    /** Зменшення до 2 000 px — стеля для живого перегляду; оригінал не чіпаємо. */
    const val PREVIEW_LONG_EDGE = 2000

    /** 5x4 матриця в порядку Android `ColorMatrix`: рядок за рядом. */
    private const val MATRIX_LENGTH = 20

    fun normalizeTurns(turns: Int): Int = ((turns % 4) + 4) % 4

    /** Габарити після повороту: на 90° і 270° ширина й висота міняються місцями. */
    fun rotatedBounds(width: Int, height: Int, turns: Int): Bounds {
        require(width > 0 && height > 0) { "розміри зображення мають бути додатні" }
        return if (normalizeTurns(turns) % 2 == 0) Bounds(width, height) else Bounds(height, width)
    }

    /**
     * Найбільший центральний прямокутник потрібної пропорції.
     *
     * Спершу пробуємо «висоту лишаємо як є» — якщо за нею ширина влізає в
     * кадр, обрізаємо лише по боках. Якщо ні — обмежуємо шириною й рахуємо
     * висоту. Ціла арифметика через `Long`, бо 4000·16 на Int уже переповнює.
     */
    fun cropRect(width: Int, height: Int, aspect: CropAspect): CropRect {
        require(width > 0 && height > 0) { "розміри зображення мають бути додатні" }
        if (aspect.num == 0) return CropRect(0, 0, width, height)

        val byHeight = (height.toLong() * aspect.num / aspect.den).toInt()
        return if (byHeight in 1..width) {
            centered(width, height, byHeight, height)
        } else {
            val byWidth = (width.toLong() * aspect.den / aspect.num).toInt()
                .coerceIn(1, height)
            centered(width, height, width, byWidth)
        }
    }

    private fun centered(width: Int, height: Int, rectWidth: Int, rectHeight: Int) =
        CropRect(
            left = (width - rectWidth) / 2,
            top = (height - rectHeight) / 2,
            width = rectWidth,
            height = rectHeight,
        )

    /** Габарити готового файла: спочатку поворот, потім обрізання. */
    fun resultBounds(sourceWidth: Int, sourceHeight: Int, params: EditParams): Bounds {
        val rotated = rotatedBounds(sourceWidth, sourceHeight, params.turns)
        val rect = cropRect(rotated.width, rotated.height, params.aspect)
        return Bounds(rect.width, rect.height)
    }

    /**
     * Яскравість і контраст однією матрицею.
     *
     * Контраст множимо навколо сірого, а не нуля: `out = (in - 127.5)·k + 127.5`.
     * Інакше «-100% контрасту» робила б чорне полотно замість однотонного
     * сірого, а «+100%» вибілювало б тіні в чистий шум.
     */
    fun colorMatrixValues(brightness: Float, contrast: Float): FloatArray {
        val b = brightness.coerceIn(-1f, 1f)
        val c = contrast.coerceIn(-1f, 1f)
        val scale = 1f + c
        val shift = MID_GREY * (1f - scale) + b * FULL_SCALE
        val matrix = FloatArray(MATRIX_LENGTH)
        matrix[0] = scale
        matrix[6] = scale
        matrix[12] = scale
        matrix[4] = shift
        matrix[9] = shift
        matrix[14] = shift
        matrix[18] = 1f
        return matrix
    }

    fun colorMatrixValues(params: EditParams): FloatArray =
        colorMatrixValues(params.brightness, params.contrast)

    /** Співвідношення сторін готового кадра — для рамки перегляду. */
    fun previewRatio(bounds: Bounds): Float = bounds.width.toFloat() / bounds.height

    /**
     * Масштаб зменшеного перегляду: довга сторона не більше `longEdge`,
     * збільшення ніколи — зайве навантаження на памʼять.
     */
    fun previewSampleSize(width: Int, height: Int, longEdge: Int = PREVIEW_LONG_EDGE): Int {
        require(width > 0 && height > 0) { "розміри зображення мають бути додатні" }
        val longest = maxOf(width, height)
        if (longest <= longEdge) return 1
        return (longest.toFloat() / longEdge).roundToInt().coerceAtLeast(1)
    }
}
