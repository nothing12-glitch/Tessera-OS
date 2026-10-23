package os.tessera.clock.logic

/**
 * Один записанний круг: [splitMs] — скільки минуло від попереднього кола,
 * [totalMs] — накопичений час від старту в момент запису.
 */
data class Lap(val index: Int, val splitMs: Long, val totalMs: Long)

/**
 * Машина станів секундоміра.
 *
 * Час беремо щиро з інʼєктованого поставача [now] (у застосунку це
 * `SystemClock.elapsedRealtime`). Ми рахуємо лише РІЗНИЦІ двох значень
 * поставача, тому:
 *  * зміна стінкового годинника (дата/час/часовий пояс) не рухає показ;
 *  * апарат не «дрейфує»: не тримаємо накопичений лічильник, який би
 *    залежав від того, скільки разів ми встигли тикнути.
 *
 * Клас не знає про Android і Coroutines — усе перевіряється тестом.
 */
class StopWatch(private val now: () -> Long) {

    private var running = false
    private var startedAt = 0L      // значення now() у момент старту поточної фази
    private var accumulated = 0L    // накопичений час усіх завершених фаз (мс)

    /** Накопичений від старту момент у мс на кожен записаний круг. */
    private val marks = mutableListOf<Long>()

    fun isRunning(): Boolean = running

    /** Сумарний час від першого старту (без «Скинути»), включно з поточною фазою. */
    fun elapsed(): Long = accumulated + if (running) now() - startedAt else 0L

    fun start() {
        if (running) return
        startedAt = now()
        running = true
    }

    fun stop() {
        if (!running) return
        accumulated += now() - startedAt
        running = false
    }

    /**
     * «Коло»: фіксуємо поточний накопичений час. Круг пишеться лише під час
     * руху — зупинений секундомір уже показує фінальне значення, додавати
     * до нього чергу сенсу немає.
     */
    fun lap() {
        if (!running) return
        marks.add(elapsed())
    }

    /**
     * «Скинути»: повний нуль — і таймер, і черга кругів.
     */
    fun reset() {
        running = false
        startedAt = 0L
        accumulated = 0L
        marks.clear()
    }

    /** Кола у порядку запису; UI показує найновіше першим (reversed). */
    fun laps(): List<Lap> = buildList(marks.size) {
        var previous = 0L
        marks.forEachIndexed { i, total ->
            add(Lap(index = i + 1, splitMs = total - previous, totalMs = total))
            previous = total
        }
    }
}
