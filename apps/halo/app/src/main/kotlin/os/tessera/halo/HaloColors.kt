package os.tessera.halo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import os.tessera.halo.logic.ColorBloom

/**
 * Звідки брати колір світла. Порядок навмисно «найнадійніше → найцікавіше»:
 * акцент Monet є завжди, а от іконка чужого застосунку — ні, бо з Android 11
 * пакет може бути нам невидимий.
 */
object HaloColors {

    /** Резерв — той самий синій із нашої айдентики, якщо Monet недоступний. */
    private const val BRAND_FALLBACK = "#8FB0FF"

    fun monetAccent(context: Context): Int = runCatching {
        context.getColor(android.R.color.system_accent1_400)
    }.getOrElse { Color.parseColor(BRAND_FALLBACK) }

    /**
     * Колір сповіщення: спершу той, який застосунок сам йому дав, потім
     * велика картинка, потім маленька іконка. `FLAG_COLORIZED` лишаємо за
     * бортом: він прихований у framework, а публічний `notification.color`
     * дає те саме.
     */
    fun fromNotification(sbn: StatusBarNotification, context: Context): Int? {
        val n = sbn.notification
        if (n.color != 0) return n.color

        val fromLarge = runCatching {
            n.getLargeIcon()?.loadDrawable(context)?.let { drawable ->
                pixels(drawable)?.let(ColorBloom::dominant)
            }
        }.getOrNull()
        if (fromLarge != null) return fromLarge

        return runCatching {
            n.smallIcon?.loadDrawable(context)?.let { drawable ->
                pixels(drawable)?.let(ColorBloom::dominant)
            }
        }.getOrNull()
    }

    /** Кадрує `drawable` у 32x32 і віддає пікселі; null — якщо малювати нічого. */
    fun pixels(drawable: Drawable?): IntArray? {
        if (drawable == null) return null
        var w = drawable.intrinsicWidth
        var h = drawable.intrinsicHeight
        if (w <= 0 || h <= 0) {
            w = 32
            h = 32
        }
        val side = minOf(32, maxOf(1, minOf(w, h)))
        val scale = side.toFloat() / maxOf(w, h)
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        val out = IntArray(side * side)
        bitmap.getPixels(out, 0, side, 0, 0, side, side)
        bitmap.recycle()
        return out
    }
}
