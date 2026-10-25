package os.tessera.halo.logic

import kotlin.math.cos
import kotlin.math.PI

/**
 * Криві анімації краю. Окремо від `View`, бо саме тут трапляють «світло
 * мигнуло на один кадр» і «край не згасає».
 */
object HaloMotion {

    fun smooth(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Огина яскравості для `t` у 0..1: підрост за `rise`, потім, плавне
     * згасання за `fall`. На краях — рівно 0, інакше видно удари світла.
     */
    fun envelope(t: Float, rise: Float = 0.14f, fall: Float = 0.34f): Float {
        val x = t.coerceIn(0f, 1f)
        val up = smooth(if (rise <= 0f) 1f else x / rise)
        val down = smooth(if (fall <= 0f) 1f else (1f - x) / fall)
        return minOf(up, down)
    }

    /** Дихання: 0 на старті, максимум у середині, 0 на фініші. */
    fun breathe(t: Float, cycles: Int = 2): Float {
        val x = t.coerceIn(0f, 1f)
        if (cycles <= 0) return 1f
        val wave = 0.5 - 0.5 * cos(2.0 * PI * cycles * x)
        return wave.toFloat()
    }

    /** Фаза бігу світла вздовж периметру: за `t` 0..1 — рівно один обід. */
    fun travel(t: Float, laps: Float = 1.5f): Float = t.coerceIn(0f, 1f) * laps
}
