package os.tessera.halo

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import os.tessera.halo.logic.GlowPattern

/**
 * Керує вікном краю: саме тут вирішується, скільки пам'яті екран тримає
 * підсвіченим. Вікно не бере дотиків і не бере фокус — користувач не
 * помічає, що щось стоїть поверх його гри чи клавіатури.
 *
 * `FLAG_NOT_TOUCHABLE` лишаємо обов'язково: без нього оверлей перехопив би
 * кожен дотик. Платформа через нього зрізає `alpha` вікна до 0.80, тому
 * яскравість краю компенсовано в самому малюнку поверхні, а не тут.
 */
class HaloOverlay(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var surface: HaloGlowSurface? = null

    val isShowing: Boolean get() = surface != null

    fun show(color: Int, pattern: GlowPattern, intensity: Float) {
        val existing = surface
        if (existing != null) {
            // Друге сповіщення під час світла: повторно використовуємо той
            // самий край, щоб не блимнуло новим вікном.
            existing.configure(color, pattern, intensity)
            existing.start()
            return
        }
        val fresh = HaloGlowSurface(appContext).apply {
            configure(color, pattern, intensity)
            onFinished = { hide() }
        }
        runCatching { windowManager.addView(fresh, params()) }
            .onFailure { runCatching { windowManager.removeViewImmediate(fresh) }; return }
        surface = fresh
        fresh.start()
    }

    fun hide() {
        val current = surface ?: return
        surface = null
        current.onFinished = null
        current.stop()
        runCatching { windowManager.removeViewImmediate(current) }
    }

    private fun params(): WindowManager.LayoutParams {
        val size = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds
        } else {
            val metrics = appContext.resources.displayMetrics
            android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        return WindowManager.LayoutParams(
            size.width(),
            size.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }
}
