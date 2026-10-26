package os.tessera.halo.logic

/** Візерунок світлового краю. Їх рівно три — редактора сцен навмисно немає. */
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

/** Те, що застосунок думає про сповіщення, без Android-класів. */
data class PostedNotification(
    val key: String,
    val priority: Int,
    val category: String?,
    val ongoing: Boolean,
    val colorArgb: Int,
    val hasPixelSource: Boolean,
)

/**
 * Зовнішні умови на момент світла. Усі вони зібрані зі станів, які даються
 * прочитати без зайвих дозволів: DND — зі слухача, дзвінок — з `AudioManager`,
 * екран і батарея — із sticky-інтентів. Невідоме значення позначене так,
 * щоб не вимикати край через власну неповну картину.
 */
data class GlowContext(
    /** Режим «не турбувати» — вимикає край повністю. */
    val zenActive: Boolean = false,
    /** Іде розмова або вхідний дзвінок: край про сповіщення, а не про дзвінки. */
    val inCall: Boolean = false,
    /** Екран активний (`PowerManager.isInteractive`). Вимкнений — не світим. */
    val interactive: Boolean = true,
    /** Екран заблокований: на лок-скрині краю немає. */
    val keyguardLocked: Boolean = false,
    /** Рівень заряду 0..100, -1 — непрочитано. */
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
)

data class HaloConfig(
    val enabled: Boolean = true,
    val pattern: GlowPattern = GlowPattern.WAVE,
    val intensity: Float = 0.85f,
    /** Світити лише на те, що й так показалося б користувачеві. */
    val onlyLoud: Boolean = true,
) {
    fun normalized(): HaloConfig = copy(
        intensity = intensity.coerceIn(MIN_INTENSITY, 1f),
    )

    companion object {
        const val MIN_INTENSITY = 0.2f
        const val DEFAULT_INTENSITY = 0.85f

        /** Імпульс краю триває рівно стільки. */
        const val IMPULSE_MS = 3000L

        /** Повторне світло за цей час — лише якщо край уже згас. */
        const val RE_GLOW_COOLDOWN_MS = 700L

        /** Нижче цього заряду край вимикається: DecorView не варто споживати
         *  рештку батареї заради ефекту. */
        const val MIN_BATTERY_PERCENT = 20
    }
}

/**
 * Вирішує, чи світити краєм і з якою яскравістю. Чисті функції: весь
 * Android-шум залишається поза ними, тому кожне правило перевіряється тестом
 * на JVM, а не скріншотом.
 */
object HaloPlan {

    /** `Notification.PRIORITY_DEFAULT` — від нього й вище вважаємо «гучним». */
    const val PRIORITY_LOUD = 0

    /** Категорія вхідного/активного дзвінка — край про неї не світить. */
    const val CATEGORY_CALL = "call"

    /** Яскравість на межі порогу батареї: тьмяно, але ще видно. */
    const val LOW_BATTERY_FACTOR = 0.6f

    /**
     * Чи засвітити край на це сповіщення в цих умовах. Порядок — від
     * «заборонено глобально» до «це конкретне сповіщення».
     */
    fun shouldGlow(n: PostedNotification, cfg: HaloConfig, ctx: GlowContext): Boolean {
        val state = cfg.normalized()
        if (!state.enabled) return false
        if (!ctx.interactive) return false
        if (ctx.keyguardLocked) return false
        if (ctx.zenActive) return false
        if (ctx.inCall) return false
        if (!batteryAllows(ctx)) return false
        if (n.ongoing) return false
        if (n.category == CATEGORY_CALL) return false
        if (state.onlyLoud && n.priority < PRIORITY_LOUD) return false
        return true
    }

    /**
     * Порог батареї. Невідомий рівень (-1) не блокує світло — інакше крах
     * читання стану означав би «край мертвий», а це нечесно.
     */
    fun batteryAllows(ctx: GlowContext): Boolean {
        if (ctx.charging) return true
        if (ctx.batteryPercent < 0) return true
        return ctx.batteryPercent >= HaloConfig.MIN_BATTERY_PERCENT
    }

    /**
     * Фактична інтенсивність краю: ручна яскравість помножена на множник
     * за станом батареї. На зарядці — повна, на розряді плавно тьмяніє
     * аж до порогу. Це власна інтенсивність малюнка, а не системна
     * яскравість екрана.
     */
    fun edgeBrightness(cfg: HaloConfig, ctx: GlowContext): Float {
        val manual = cfg.normalized().intensity
        return (manual * batteryFactor(ctx)).coerceIn(HaloConfig.MIN_INTENSITY, 1f)
    }

    /** Множник яскравості 0.6..1.0 за зарядом; непрочитаний заряд — без тіні. */
    fun batteryFactor(ctx: GlowContext): Float {
        if (ctx.charging) return 1f
        val level = ctx.batteryPercent
        if (level < 0) return 1f
        val low = HaloConfig.MIN_BATTERY_PERCENT
        if (level <= low) return LOW_BATTERY_FACTOR
        val span = (100 - low).coerceAtLeast(1)
        val ramp = (level - low).toFloat() / span
        return LOW_BATTERY_FACTOR + (1f - LOW_BATTERY_FACTOR) * ramp.coerceIn(0f, 1f)
    }

    /**
     * Колір краю: спершу той, що дав застосунок у сповіщенні, — і лише якщо
     * він справді заданий (не прозорий 0). Немає — бірюзовий резерв.
     */
    fun pickColor(notificationArgb: Int, fallbackArgb: Int): Int {
        val hasColor = (notificationArgb ushr 24) and 0xFF != 0 &&
            notificationArgb and 0x00FFFFFF != 0
        return if (hasColor) notificationArgb else fallbackArgb
    }

    /**
     * Чи встиг край згаснути від останнього світла. Екран, увімкнений
     * через пів секунди після сповіщення, не повинен вмикати другий захід.
     */
    fun allowReGlow(nowElapsed: Long, lastGlowElapsed: Long): Boolean =
        nowElapsed - lastGlowElapsed >=
            HaloConfig.IMPULSE_MS + HaloConfig.RE_GLOW_COOLDOWN_MS

    /** Ключі, що лишились, після того як сповіщення зникло. */
    fun keysAfterDismiss(active: Set<String>, dismissedKey: String): Set<String> =
        (active - dismissedKey).toSet()
}
