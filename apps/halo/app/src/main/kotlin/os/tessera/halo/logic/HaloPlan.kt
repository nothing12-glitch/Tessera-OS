package os.tessera.halo.logic

/** Візерунок світлового краю. */
enum class GlowPattern {
    /** Світлові сегменти йдуть по периметру. */
    WAVE,

    /** Уся смуга дихає. */
    PULSE,

    /** Смуга горить рівно, без руху. */
    SOLID,
    ;

    companion object {
        fun byName(name: String?): GlowPattern =
            entries.firstOrNull { it.name == name } ?: WAVE
    }
}

/** Звідки брати колір світла. */
enum class GlowColor {
    /** Акцент Monet — та сама палітра, що й тема TesseraOS. */
    MONET,

    /** Колір самого сповіщення, якщо його звідки взяти. */
    NOTIFICATION,
    ;

    companion object {
        fun byName(name: String?): GlowColor =
            entries.firstOrNull { it.name == name } ?: MONET
    }
}

/** Те, що застосунок думає про сповіщення, без Android-класів. */
data class PostedNotification(
    val key: String,
    val priority: Int,
    val category: String?,
    val ongoing: Boolean,
    val colorArgb: Int,
    val hasPixelSource: Boolean,
)

data class HaloConfig(
    val enabled: Boolean = true,
    val pattern: GlowPattern = GlowPattern.WAVE,
    val color: GlowColor = GlowColor.MONET,
    val durationMs: Long = DEFAULT_DURATION_MS,
    val intensity: Float = 0.85f,
    /** Світити лише на те, що й так показалося б користувачеві. */
    val onlyLoud: Boolean = true,
) {
    fun normalized(): HaloConfig = copy(
        durationMs = durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS),
        intensity = intensity.coerceIn(0.2f, 1f),
    )

    companion object {
        const val MIN_DURATION_MS = 400L
        const val MAX_DURATION_MS = 6000L
        const val DEFAULT_DURATION_MS = 2400L

        /** Повторне світло за цей час — лише якщо край уже згас. */
        const val RE_GLOW_COOLDOWN_MS = 700L
    }
}

/**
 * Вирішує, чи світити краєм. Чиста функція: весь Android-шум залишається
 * поза нею, тому правило перевіряється тестом на JVM.
 */
object HaloPlan {

    /** `Notification.PRIORITY_DEFAULT` — від нього й вище вважаємо «гучним». */
    const val PRIORITY_LOUD = 0

    /** Категорії, які пробираються крізь «Не турбувати», як у системі. */
    private val ZEN_ALLOWED = setOf("alarm", "call")

    fun shouldGlow(n: PostedNotification, cfg: HaloConfig, zenActive: Boolean): Boolean {
        val state = cfg.normalized()
        if (!state.enabled) return false
        if (n.ongoing) return false
        if (zenActive && n.category !in ZEN_ALLOWED) return false
        if (state.onlyLoud && n.priority < PRIORITY_LOUD) return false
        return true
    }

    /**
     * Чи встиг край згаснути від останнього світла. Екран, увімкнений
     * через пів секунди після сповіщення, не повинен вмикати другий захід.
     */
    fun allowReGlow(nowElapsed: Long, lastGlowElapsed: Long, cfg: HaloConfig): Boolean =
        nowElapsed - lastGlowElapsed >= cfg.normalized().durationMs + HaloConfig.RE_GLOW_COOLDOWN_MS

    /** Ключі, що лишились, після того як сповіщення зникло. */
    fun keysAfterDismiss(active: Set<String>, dismissedKey: String): Set<String> =
        (active - dismissedKey).toSet()
}
