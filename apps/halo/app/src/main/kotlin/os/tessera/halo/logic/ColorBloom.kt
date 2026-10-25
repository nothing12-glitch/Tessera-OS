package os.tessera.halo.logic

/**
 * Колір світла з пікселів. Чиста математика на `IntArray`: ніяких
 * Android-класів, тож кожне правило накриті тестом на JVM, а не скріншотом.
 */
object ColorBloom {

    private const val BIN_SHIFT = 4 // 8 біт каналу -> 4 біт кошика (16^3 = 4096 кошиків)
    private const val BINS = 1 shl (BIN_SHIFT * 3)
    private const val MIN_ALPHA = 40

    /**
     * Домінуючий колір кадру. Вага насиченості тримає баланс: іконка з
     * білим тлом і синім знаком дає синій, а не білий.
     *
     * @return 0xAARRGGBB або null, якщо кадр порожній чи повністю прозорий.
     */
    fun dominant(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null
        val count = IntArray(BINS)
        val rSum = LongArray(BINS)
        val gSum = LongArray(BINS)
        val bSum = LongArray(BINS)
        var seen = 0

        for (pixel in pixels) {
            val a = (pixel ushr 24) and 0xFF
            if (a < MIN_ALPHA) continue
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val bin = ((r shr BIN_SHIFT) shl 8) or ((g shr BIN_SHIFT) shl 4) or (b shr BIN_SHIFT)
            count[bin]++
            rSum[bin] += r
            gSum[bin] += g
            bSum[bin] += b
            seen++
        }
        if (seen == 0) return null

        var best = -1
        var bestScore = -1.0
        for (bin in count.indices) {
            val n = count[bin]
            if (n == 0) continue
            val r = (rSum[bin] / n).toInt()
            val g = (gSum[bin] / n).toInt()
            val b = (bSum[bin] / n).toInt()
            val score = n * (0.3 + saturation(r, g, b))
            if (score > bestScore) {
                bestScore = score
                best = bin
            }
        }
        if (best < 0) return null
        val n = count[best]
        return rgb((rSum[best] / n).toInt(), (gSum[best] / n).toInt(), (bSum[best] / n).toInt())
    }

    fun saturation(r: Int, g: Int, b: Int): Double {
        val max = maxOf(r, g, b)
        if (max == 0) return 0.0
        return (max - minOf(r, g, b)).toDouble() / max
    }

    /** Відносна яскравість у 0..1 (Rec. 709). */
    fun luminance(color: Int): Double {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
    }

    /**
     * Край світиться на темному склі, тому темний колір не видно. Підсилюємо
     * яскравість, не чіпаючи відтінку: коефіцієнт на всі канали, з стелею 255.
     */
    fun readableOnDark(color: Int, minLuminance: Double = 0.45): Int {
        val lum = luminance(color)
        if (lum >= minLuminance) return color
        if (lum <= 0.001) return rgb(255, 255, 255)
        val boost = minLuminance / lum
        return rgb(
            scale((color shr 16) and 0xFF, boost),
            scale((color shr 8) and 0xFF, boost),
            scale(color and 0xFF, boost),
        )
    }

    /** Лінійне змішування двох кольорів; `t` = 0 -> від, 1 -> до. */
    fun mix(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return rgb(
            lerp((from shr 16) and 0xFF, (to shr 16) and 0xFF, k),
            lerp((from shr 8) and 0xFF, (to shr 8) and 0xFF, k),
            lerp(from and 0xFF, to and 0xFF, k),
        )
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        ((alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF))

    fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun scale(channel: Int, boost: Double): Int =
        (channel * boost).toInt().coerceIn(0, 255)

    private fun lerp(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).toInt().coerceIn(0, 255)
}
