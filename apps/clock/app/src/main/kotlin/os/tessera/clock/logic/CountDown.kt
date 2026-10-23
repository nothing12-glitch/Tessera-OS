package os.tessera.clock.logic

/** Стан таймера зворотного відліку. */
enum class TimerStatus { Idle, Running, Paused, Finished }

/**
 * Машина станів таймера. Як і [StopWatch], живиться виключно інʼєктованим
 * поставачем моменту [now] (`SystemClock.elapsedRealtime` у застосунку), тож
 * стінковий годинник на нього не впливає.
 *
 * Ключова властивість, заради якої писали тест: «+ хвилина» під час РОБОТИ
 * продовжує відлік (зсуває цільовий момент), а НЕ перезапускає його з нуля.
 *
 * Перехід у [TimerStatus.Finished] лінивий: стан синхронізується з годинником
 * при кожному читанні [status] чи [remaining], тож показ ніколи не «зависає»
 * у Running після того, як час сплив.
 */
class CountDown(private val now: () -> Long) {

    private var currentStatus = TimerStatus.Idle
    private var storedRemaining = 0L
    private var finishAt = 0L

    val status: TimerStatus
        get() {
            syncWithClock()
            return currentStatus
        }

    fun isRunning(): Boolean = status == TimerStatus.Running

    /** Поточний залишок у мс. */
    fun remaining(): Long {
        syncWithClock()
        return when (currentStatus) {
            TimerStatus.Running -> finishAt - now()
            TimerStatus.Finished -> 0L
            else -> storedRemaining
        }
    }

    /** Додати (або, відʼємним числом, забрати) тривалість. */
    fun add(deltaMs: Long) {
        syncWithClock()
        when (currentStatus) {
            TimerStatus.Running -> finishAt += deltaMs // продовжуємо, не перезапускаємо
            TimerStatus.Paused, TimerStatus.Idle ->
                storedRemaining = (storedRemaining + deltaMs).coerceAtLeast(0L)
            TimerStatus.Finished ->
                if (deltaMs > 0L) {
                    currentStatus = TimerStatus.Idle
                    storedRemaining = deltaMs
                }
        }
    }

    /** Забрати тривалість; відʼємну величину сприймаємо як додатну. */
    fun subtract(deltaMs: Long) = add(-deltaMs.coerceAtLeast(0L))

    /** Старт із Idle або продовження з Paused; на нулі чи Finished — без дії. */
    fun start() {
        syncWithClock()
        if (currentStatus == TimerStatus.Running) return
        if (storedRemaining <= 0L) return
        finishAt = now() + storedRemaining
        currentStatus = TimerStatus.Running
    }

    fun pause() {
        syncWithClock()
        if (currentStatus != TimerStatus.Running) return
        val left = finishAt - now()
        if (left <= 0L) {
            currentStatus = TimerStatus.Finished
            storedRemaining = 0L
        } else {
            storedRemaining = left
            currentStatus = TimerStatus.Paused
        }
    }

    /** «Скинути»: назад у Idle з нулем. */
    fun reset() {
        currentStatus = TimerStatus.Idle
        storedRemaining = 0L
        finishAt = 0L
    }

    private fun syncWithClock() {
        if (currentStatus == TimerStatus.Running && finishAt - now() <= 0L) {
            currentStatus = TimerStatus.Finished
            storedRemaining = 0L
        }
    }
}
