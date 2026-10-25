package os.tessera.halo

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import os.tessera.halo.logic.GlowPattern
import os.tessera.halo.logic.HaloMotion

/**
 * Світловий край: округлений прямокутник по периметру, дві обвідки — широка
 * напівпрозора (саме світло) і вузька яскрава (осердя смуги).
 *
 * Вікно живе лише час світла, а не постійно: overlay на весь екран — це
 * найдорожча річ у цій функції, тому тримати його «на всяк випадок» нечесно
 * щодо батареї.
 */
class HaloGlowView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val bandPx = 20f * density
    private val cornerPx = 34f * density

    private val frame = Path()
    private val bounds = RectF()
    private val measure = PathMeasure()
    private var perimeter = 0f

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var color = Color.WHITE
    private var pattern = GlowPattern.WAVE
    private var durationMs = 2400L
    private var intensity = 0.85f
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var generation = 0

    var onFinished: (() -> Unit)? = null

    fun configure(glowColor: Int, glowPattern: GlowPattern, ms: Long, strength: Float) {
        color = glowColor or (0xFF shl 24)
        pattern = glowPattern
        durationMs = ms
        intensity = strength
    }

    fun start() {
        val mine = ++generation
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                // Скасований аніматор — це нове світло поверх старого або
                // hide(); його кінець не повинен закривати вікно, яке щойно
                // відкрили знову.
                override fun onAnimationEnd(animation: Animator) {
                    if (mine == generation) onFinished?.invoke()
                }
            })
            start()
        }
    }

    fun stop() {
        generation++
        animator?.cancel()
        animator = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildFrame(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (frame.isEmpty) rebuildFrame(w, h)

        val strength = HaloMotion.envelope(progress) * intensity
        if (strength <= 0.002f) return

        val rgb = color and 0x00FFFFFF
        when (pattern) {
            GlowPattern.PULSE -> {
                val breathe = 0.35f + 0.65f * HaloMotion.breathe(progress, 2)
                drawFrame(canvas, rgb, strength * breathe, wave = false)
            }

            GlowPattern.SOLID -> drawFrame(canvas, rgb, strength, wave = false)

            GlowPattern.WAVE -> {
                // Один обід світла за захід: дві смуги біжать периметром.
                val on = perimeter * 0.17f
                val off = perimeter * 0.33f
                val phase = -HaloMotion.travel(progress, laps = 2f) * perimeter
                val effect = DashPathEffect(floatArrayOf(on, off), phase)
                haloPaint.pathEffect = effect
                corePaint.pathEffect = effect
                drawFrame(canvas, rgb, strength, wave = true)
                haloPaint.pathEffect = null
                corePaint.pathEffect = null
            }
        }
    }

    private fun drawFrame(canvas: Canvas, rgb: Int, strength: Float, wave: Boolean) {
        haloPaint.color = rgb
        haloPaint.alpha = (strength * 74).toInt().coerceIn(0, 255)
        haloPaint.strokeWidth = bandPx

        corePaint.color = rgb
        corePaint.alpha = (strength * 235).toInt().coerceIn(0, 255)
        corePaint.strokeWidth = if (wave) bandPx * 0.26f else bandPx * 0.34f

        canvas.drawPath(frame, haloPaint)
        canvas.drawPath(frame, corePaint)
    }

    private fun rebuildFrame(w: Float, h: Float) {
        val inset = bandPx / 2f + 2f * density
        bounds.set(inset, inset, w - inset, h - inset)
        frame.reset()
        frame.addRoundRect(bounds, cornerPx, cornerPx, Path.Direction.CW)
        measure.setPath(frame, false)
        perimeter = measure.length.coerceAtLeast(1f)
    }
}
