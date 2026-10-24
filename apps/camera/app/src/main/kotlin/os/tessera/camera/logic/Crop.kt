package os.tessera.camera.logic

/**
 * Пропорція обрізання. `num`/`den` дорівнюють 0 — «лишити кадр як є».
 *
 * Пропорції свідомо раціональні (два цілих), а не `Float`: так перевірка
 * «квадрат чи ні» в юніт-тесті не залежить від округлення плаваючої коми.
 */
enum class CropAspect(val num: Int, val den: Int) {
    Original(0, 0),
    Square(1, 1),
    Portrait(3, 4),
    Wide(16, 9),
}

/** Прямокутник обрізання в координатах вже повернутого зображення. */
data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

/** Габарити зображення; власний тип, щоб не тягнути `android.*` у чисту логіку. */
data class Bounds(val width: Int, val height: Int)
