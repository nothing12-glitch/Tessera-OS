package os.tessera.clock

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.TimeZone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.tessera.clock.data.AlarmStore
import os.tessera.clock.logic.AlarmSpec
import os.tessera.clock.logic.ClockFormat
import os.tessera.clock.logic.CountDown
import os.tessera.clock.logic.StopWatch
import os.tessera.clock.logic.TimerStatus

data class LapRow(val index: Int, val splitText: String, val totalText: String)

data class AlarmRow(
    val id: Long,
    val timeText: String,
    val label: String,
    val repeatDaily: Boolean,
    val enabled: Boolean,
    val nextDeltaMs: Long?,
)

/** Чернетка вбудованої панелі додавання/редагування (не діалог). */
data class AlarmDraft(
    val id: Long?,
    val hour: Int,
    val minute: Int,
    val label: String,
    val repeatDaily: Boolean,
)

data class ClockUiState(
    val tab: Int = TAB_STOPWATCH,
    val stopwatchText: String = "00:00.0",
    val stopwatchRunning: Boolean = false,
    val laps: List<LapRow> = emptyList(),
    val timerText: String = "0:00:00",
    val timerRunning: Boolean = false,
    val timerFinished: Boolean = false,
    val timerHasTime: Boolean = false,
    val alarms: List<AlarmRow> = emptyList(),
    val draft: AlarmDraft? = null,
    val canScheduleExact: Boolean = true,
    val showNoExactWarning: Boolean = false,
    val notificationsGranted: Boolean = true,
) {
    val editingAlarm: Boolean get() = draft != null
}

const val TAB_STOPWATCH = 0
const val TAB_TIMER = 1
const val TAB_ALARMS = 2

/**
 * Стан трьох екранів годинника.
 *
 * Секундомір і таймер — це чисті машини станів із [logic], які живляться
 * `SystemClock.elapsedRealtime` (monotonic-годинник пристрою), тож ані зміна
 * дати, ані переключення поясу не рухають показ. Тікер лише переказує їхній
 * стан у Compose — самої логіки тут немає, тому вона вся в тестах.
 */
class ClockViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AlarmStore(app)

    // монотонний поставач моменту — те саме, на чому тримаються тести
    private val now: () -> Long = { SystemClock.elapsedRealtime() }
    private val stopwatch = StopWatch(now)
    private val timer = CountDown(now)

    private val _state = MutableStateFlow(ClockUiState())
    val state: StateFlow<ClockUiState> = _state

    private var tickJob: Job? = null

    init {
        refreshExactCapability()
        rebuildAlarms()
        publishDynamic()
    }

    fun selectTab(tab: Int) = _state.update { it.copy(tab = tab) }

    // --- Секундомір -------------------------------------------------------

    fun stopwatchStart() {
        stopwatch.start()
        startTickingIfNeeded()
        publishDynamic()
    }

    fun stopwatchStop() {
        stopwatch.stop()
        publishDynamic()
    }

    fun stopwatchLap() {
        stopwatch.lap()
        publishDynamic()
    }

    fun stopwatchReset() {
        stopwatch.reset()
        publishDynamic()
    }

    // --- Таймер -----------------------------------------------------------

    fun timerAddMinute() { timer.add(MINUTE_MS); publishDynamic() }
    fun timerSubMinute() { timer.subtract(MINUTE_MS); publishDynamic() }
    fun timerAddHour() { timer.add(HOUR_MS); publishDynamic() }
    fun timerSubHour() { timer.subtract(HOUR_MS); publishDynamic() }

    fun timerStart() {
        timer.start()
        startTickingIfNeeded()
        publishDynamic()
    }

    fun timerPause() {
        timer.pause()
        publishDynamic()
    }

    fun timerReset() {
        timer.reset()
        publishDynamic()
    }

    // --- Будильники -------------------------------------------------------

    fun startNewAlarm() {
        val tz = TimeZone.getDefault()
        val calendar = java.util.GregorianCalendar(tz)
        _state.update {
            it.copy(
                draft = AlarmDraft(
                    id = null,
                    hour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
                    minute = calendar.get(java.util.Calendar.MINUTE),
                    label = "",
                    repeatDaily = true,
                ),
            )
        }
    }

    fun editAlarm(row: AlarmRow) {
        val spec = store.find(row.id) ?: return
        _state.update {
            it.copy(
                draft = AlarmDraft(spec.id, spec.hour, spec.minute, spec.label, spec.repeatDaily),
            )
        }
    }

    fun cancelDraft() = _state.update { it.copy(draft = null) }

    fun updateDraftLabel(label: String) =
        _state.update { it.copy(draft = it.draft?.copy(label = label)) }

    fun updateDraftRepeat(repeat: Boolean) =
        _state.update { it.copy(draft = it.draft?.copy(repeatDaily = repeat)) }

    /**
     * Збереження. годину/хвилину передає Compose TimePicker — стан вибору
     * живе в UI, тож повертаємо його сюди натисканням «Зберегти».
     */
    fun saveDraft(hour: Int, minute: Int) {
        val draft = _state.value.draft ?: return
        val id = draft.id ?: AlarmStore.nextId(getApplication())
        val spec = AlarmSpec(
            id = id,
            hour = hour,
            minute = minute,
            label = draft.label.trim(),
            repeatDaily = draft.repeatDaily,
            enabled = true,
        )
        val list = store.all().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = spec
        } else {
            list.add(spec)
            AlarmStore.commitId(getApplication(), id)
        }
        store.replaceAll(list.sortedWith(compareBy({ it.hour }, { it.minute })))
        _state.update { it.copy(draft = null) }
        applyScheduling(spec)
        refreshExactCapability()
        rebuildAlarms()
    }

    fun toggleAlarm(id: Long, enabled: Boolean) {
        val list = store.all().map { if (it.id == id) it.copy(enabled = enabled) else it }
        store.replaceAll(list)
        val spec = list.firstOrNull { it.id == id } ?: return
        if (enabled) applyScheduling(spec) else AlarmScheduler.cancel(getApplication(), id)
        refreshExactCapability()
        rebuildAlarms()
    }

    fun deleteAlarm(id: Long) {
        store.replaceAll(store.all().filterNot { it.id == id })
        AlarmScheduler.cancel(getApplication(), id)
        rebuildAlarms()
    }

    fun acknowledgeExactWarning() = _state.update { it.copy(showNoExactWarning = false) }

    fun openExactAlarmSettings() {
        AlarmScheduler.openExactAlarmSettings(getApplication())
    }

    fun setNotificationsGranted(granted: Boolean) =
        _state.update { it.copy(notificationsGranted = granted) }

    /** Викликається з активності після повернення з налаштувань/дозволів. */
    fun refreshOnResume() {
        refreshExactCapability()
        rebuildAlarms()
    }

    // --- службове ---------------------------------------------------------

    private fun publishDynamic() {
        val laps = stopwatch.laps().map {
            LapRow(it.index, ClockFormat.stopwatch(it.splitMs), ClockFormat.stopwatch(it.totalMs))
        }
        val remaining = timer.remaining()
        _state.update {
            it.copy(
                stopwatchText = ClockFormat.stopwatch(stopwatch.elapsed()),
                stopwatchRunning = stopwatch.isRunning(),
                laps = laps,
                timerText = ClockFormat.countdown(remaining),
                timerRunning = timer.status == TimerStatus.Running,
                timerFinished = timer.status == TimerStatus.Finished,
                timerHasTime = remaining > 0L,
            )
        }
    }

    private fun startTickingIfNeeded() {
        if (tickJob?.isActive == true) return
        if (!stopwatch.isRunning() && !timer.isRunning()) return
        tickJob = viewModelScope.launch {
            while (true) {
                publishDynamic()
                val stillMoving = stopwatch.isRunning() || timer.isRunning()
                if (!stillMoving) break
                delay(TICK_MS)
            }
        }
    }

    private fun refreshExactCapability() {
        val allowed = AlarmScheduler.canScheduleExact(getApplication())
        _state.update { it.copy(canScheduleExact = allowed) }
    }

    private fun applyScheduling(alarm: AlarmSpec) {
        if (alarm.enabled && !AlarmScheduler.schedule(getApplication(), alarm)) {
            _state.update { it.copy(showNoExactWarning = true) }
        }
    }

    private fun rebuildAlarms() {
        val nowMs = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val rows = store.all().map { a ->
            val next = a.nextTriggerMillis(nowMs, tz)
            AlarmRow(
                id = a.id,
                timeText = ClockFormat.timeOfDay(a.hour, a.minute),
                label = a.label,
                repeatDaily = a.repeatDaily,
                enabled = a.enabled,
                nextDeltaMs = next?.let { it - nowMs },
            )
        }
        _state.update { it.copy(alarms = rows) }
    }

    private companion object {
        const val SECOND_MS = 1000L
        const val MINUTE_MS = 60 * SECOND_MS
        const val HOUR_MS = 60 * MINUTE_MS
        const val TICK_MS = 100L
    }
}
