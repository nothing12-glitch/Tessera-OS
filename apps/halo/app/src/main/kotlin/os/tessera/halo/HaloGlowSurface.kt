package os.tessera.halo

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.SystemClock
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import os.tessera.halo.logic.ColorBloom
import os.tessera.halo.logic.GlowPattern
import os.tessera.halo.logic.HaloConfig
import os.tessera.halo.logic.HaloMotion

/**
 * Світловий край на окремій поверхні, а не на анімованій `View`.
 *
 * Поверхня малює рівно те, що порахувала чиста логіка (`HaloMotion` дає
 * огину й дихання, `HaloPlan` — чи й як світити), а сама нічого не
 * вирішує: тут лише подача кадрів на `Canvas` поверхні. М'який широкий
 * край зроблено трьома розмитими прошарками без чіткої межі; альфа кожного
 * прошарку компенсує зріз `WindowManager` до 0.80.
 */
class HaloGlowSurface(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val bandPx = 12f / 25.4f * context.resources.displayMetrics.densityDpi.toFloat()
    private val cornerPx = bandPx * 1.7f

    private val frame = Path()
    private val bounds = RectF()
    private val measure = PathMeasure()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var perimeter = 0f
    private var builtW = 0
    private var builtH = 0

    private var colorRgb = ColorBloom.TEAL and 0x00FFFFFF
    private var pattern = GlowPattern.WAVE
    private var intensity = HaloConfig.DEFAULT_INTENSITY

    private var startUptimeMs = 0L
    private var running = false
    private var surfaceReady = false
    private var generation = 0

    var onFinished: (() -> Unit)? = null

    init {
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        holder.addCallback(this)
    }

    fun configure(glowColor: Int, glowPattern: GlowPattern, strength: Float) {
        colorRgb = glowColor and 0x00FFFFFF
        pattern = glowPattern
        intensity = strength.coerceIn(HaloConfig.MIN_INTENSITY, 1f)
    }

    fun start() {
        val mine = ++generation
        startUptimeMs = SystemClock.uptimeMillis()
        running = true
        if (surfaceReady && mine == generation) scheduleFrame()
    }

    fun stop() {
        generation++
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (running) scheduleFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
    }

    private val frameCallback = Choreographer.FrameCallback {
        if (!running) return@FrameCallback
        val mine = generation
        renderFrame()
        if (running && mine == generation) scheduleFrame()
    }

    private fun scheduleFrame() {
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun renderFrame() {
        if (!surfaceReady) return
        val progress = (SystemClock.uptimeMillis() - startUptimeMs).toFloat() /
            HaloConfig.IMPULSE_MS
        val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (progress >= 1f) {
            runCatching { holder.unlockCanvasAndPost(canvas) }
            finish()
            return
        }
        drawEdge(canvas, progress.coerceIn(0f, 1f))
        runCatching { holder.unlockCanvasAndPost(canvas) }
    }

    private fun finish() {
        generation++
        running = false
        onFinished?.invoke()
    }

    private fun drawEdge(canvas: Canvas, progress: Float) {
        val w = canvas.width
        val h = canvas.height
        if (w <= 0 || h <= 0) return
        if (w != builtW || h != builtH) rebuildFrame(w.toFloat(), h.toFloat())

        val envelope = HaloMotion.envelope(progress)
        val strength = envelope * intensity
        if (strength <= 0.002f) return

        val dash = if (pattern == GlowPattern.WAVE) {
            val on = perimeter * 0.17f
            val off = perimeter * 0.33f
            val phase = -HaloMotion.travel(progress, laps = 2f) * perimeter
            DashPathEffect(floatArrayOf(on, off), phase)
        } else {
            null
        }
        val breathe = if (pattern == GlowPattern.PULSE) {
            0.4f + 0.6f * HaloMotion.breathe(progress, cycles = 2)
        } else {
            1f
        }

        // Три розмиті прошарки від широкої блідої aureолі до вужчого ядра:
        // межі між ними теж розмиті, тому чіткого контуру смуги не видно.
        pass(canvas, colorRgb, strength * breathe, bandPx, 0.30f, bandPx * 0.7f, dash)
        pass(canvas, colorRgb, strength * breathe, bandPx * 0.62f, 0.55f, bandPx * 0.4f, dash)
        pass(canvas, colorRgb, strength * breathe, bandPx * 0.34f, 0.85f, bandPx * 0.22f, dash)

        strokePaint.maskFilter = null
        strokePaint.pathEffect = null
    }

    private fun pass(
        canvas: Canvas,
        rgb: Int,
        strength: Float,
        width: Float,
        alphaFactor: Float,
        blur: Float,
        effect: DashPathEffect?,
    ) {
        val alpha = ColorBloom.compensateAlpha((strength * 255f * alphaFactor).toInt())
        strokePaint.color = Color.argb(alpha, (rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
        strokePaint.strokeWidth = width
        strokePaint.pathEffect = effect
        strokePaint.maskFilter = BlurMaskFilter(blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(frame, strokePaint)
    }

    private fun rebuildFrame(w: Float, h: Float) {
        builtW = w.toInt()
        builtH = h.toInt()
        val inset = bandPx * 0.35f
        bounds.set(inset, inset, w - inset, h - inset)
        frame.reset()
        frame.addRoundRect(bounds, cornerPx, cornerPx, Path.Direction.CW)
        measure.setPath(frame, false)
        perimeter = measure.length.coerceAtLeast(1f)
    }
}
