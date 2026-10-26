package os.tessera.welcome.logic

/**
 * Рух кроків у числах — без Compose і без Android. Довжина вітання, межі
 * появи кожного елемента й цикл демо краю рахуються тут, тому «анімація триває
 * три секунди» перевіряється тестом, а не на око.
 */

/** Вітання живе рівно стільки мілісекунд. */
const val GREETING_MS: Long = 3000L

/** Час у межах анімації, коли елемент починає і закінчує з'являтися. */
data class Appearance(val fromMs: Float, val toMs: Float)

/** Черговість появи вітання: спершу знак, потім назва, далі текст і збірка. */
object GreetingStage {
    val MARK = Appearance(0f, 900f)
    val TITLE = Appearance(800f, 1700f)
    val BODY = Appearance(1500f, 2300f)
    val HINT = Appearance(1900f, 2600f)
    val BUILD = Appearance(2400f, GREETING_MS.toFloat())
}

/** Частка появи елемента: 0 до свого сегмента, 1 після нього, між — лінійно. */
fun stageOf(elapsedMs: Float, appearance: Appearance): Float {
    val span = appearance.toMs - appearance.fromMs
    if (span <= 0f) return 1f
    return ((elapsedMs - appearance.fromMs) / span).coerceIn(0f, 1f)
}

/** Елемент не лише проступає, а й добирає розмір — теж за часткою появи. */
fun scaleOf(stage: Float, from: Float, to: Float): Float = from + (to - from) * stage

/** Одна хвиля світла в демо краю триває рівно стільки. */
const val EDGE_DEMO_CYCLE_MS: Int = 1800

/** Смуга світла проходить по периметру за дві хвилі — довше виглядає застигло. */
const val EDGE_DEMO_WAVES: Int = 2
