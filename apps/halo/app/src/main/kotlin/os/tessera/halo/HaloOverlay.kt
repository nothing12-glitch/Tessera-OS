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
 */
class HaloOverlay(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: HaloGlowView? = null

    val isShowing: Boolean get() = view != null

    fun show(color: Int, pattern: GlowPattern, durationMs: Long, intensity: Float) {
        val existing = view
        if (existing != null) {
            // Друге сповіщення під час світла: повторно використовуємо той
            // самий край, щоб не блимнуло новим вікном.
            existing.configure(color, pattern, durationMs, intensity)
            windowManager.updateViewLayout(existing, params())
            existing.start()
            return
        }
        val fresh = HaloGlowView(appContext).apply {
            configure(color, pattern, durationMs, intensity)
            onFinished = { hide() }
        }
        runCatching { windowManager.addView(fresh, params()) }
            .onFailure { windowManager.removeViewImmediate(fresh); return }
        view = fresh
        fresh.start()
    }

    fun hide() {
        val current = view ?: return
        view = null
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
