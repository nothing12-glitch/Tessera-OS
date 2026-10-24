package os.tessera.dialer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.tessera.dialer.data.CallLogStore
import os.tessera.dialer.data.Suggestion
import os.tessera.dialer.logic.CallDirection
import os.tessera.dialer.logic.DayLabels
import os.tessera.dialer.logic.CallFormat
import os.tessera.dialer.logic.CallRecord
import os.tessera.dialer.logic.DialpadState
import os.tessera.dialer.logic.PhoneNumber

const val TAB_DIALPAD = 0
const val TAB_RECENTS = 1

/** Один рядок журналу в тому вигляді, в якому його малює Compose. */
data class CallRow(
    val id: Long,
    val title: String,
    val number: String,
    val timeText: String,
    val durationText: String,
    val direction: CallDirection,
    val missed: Boolean,
)

/** Заголовок дня й рядки під ним. */
data class CallGroup(val label: String, val rows: List<CallRow>)

data class DialUiState(
    val tab: Int = TAB_DIALPAD,
    val dialpad: DialpadState = DialpadState(),
    val suggestion: Suggestion? = null,
    val groups: List<CallGroup> = emptyList(),
    val query: String = "",
    val callLogReadable: Boolean = true,
    val showRoleBanner: Boolean = false,
) {
    val canCall: Boolean get() = dialpad.canCall
}

/**
 * Стан обох екранів «Телефона».
 *
 * Набір і форматування — у чистих класах з [os.tessera.dialer.logic], вони
 * накриті юніт-тестами; сюди входить лише обгортка над ними й читання журналу
 * з контактами на окремому IO-потоці. Дзвінок навмисно робить не VM, а активність:
 * саме в неї є вікно, дозвіл `CALL_PHONE` і системний запит права, тому VM
 * публікує намір у [dialRequests].
 */
class DialViewModel(app: Application) : AndroidViewModel(app) {

    private val store = CallLogStore(app)

    private val _state = MutableStateFlow(DialUiState())
    val state: StateFlow<DialUiState> = _state.asStateFlow()

    private val _dialRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val dialRequests: SharedFlow<String> = _dialRequests.asSharedFlow()

    private var records: List<CallRecord> = emptyList()
    private var suggestionJob: Job? = null

    init {
        reload()
    }

    fun selectTab(tab: Int) = _state.update { it.copy(tab = tab) }

    fun press(symbol: Char) {
        val next = _state.value.dialpad.append(symbol)
        if (next == _state.value.dialpad) return
        _state.update { it.copy(dialpad = next) }
        suggest(next.text)
    }

    fun backspace() = _state.update { it.copy(dialpad = it.dialpad.backspace()) }

    fun clearDialpad() = _state.update { it.copy(dialpad = DialpadState(), suggestion = null) }

    fun callFromDialpad() {
        val suggestion = _state.value.suggestion
        // тап по «Подзвонити» викликає саме того контакту, чиє імʼя набране
        // цифрами: «Tessera» → виклик його номеру, а не випадкового збігу
        val number = suggestion?.number?.takeIf { it.isNotBlank() } ?: _state.value.dialpad.text
        if (!PhoneNumber.isCallable(number)) return
        emit(number)
    }

    fun callNumber(number: String) = emit(PhoneNumber.normalize(number))

    fun deleteRow(id: Long) = viewModelScope.launch {
        runCatching { store.delete(id) }
        reload()
    }

    fun setShowRoleBanner(show: Boolean) = _state.update { it.copy(showRoleBanner = show) }

    fun setCallLogReadable(readable: Boolean) =
        _state.update { it.copy(callLogReadable = readable) }

    fun reload() {
        viewModelScope.launch {
            records = readLog()
            publishGroups()
        }
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        val cleaned = query.trim()
        publishGroups(if (cleaned.isEmpty()) records else CallFormat.filter(cleaned, records))
    }

    // --- службове ---------------------------------------------------------

    private fun emit(number: String) {
        val cleaned = PhoneNumber.normalize(number)
        if (cleaned.isEmpty()) return
        _dialRequests.tryEmit(cleaned)
    }

    private fun suggest(text: String) {
        suggestionJob?.cancel()
        if (text.length < MIN_QUERY) {
            _state.update { it.copy(suggestion = null) }
            return
        }
        suggestionJob = viewModelScope.launch {
            // маленький debounce: PhoneLookup — похід у провайдер, а клавіші
            // тиснуть швидше, ніж він встигає відповісти
            delay(SUGGEST_DEBOUNCE_MS)
            val found = runCatching { store.suggest(text) }.getOrNull()
            _state.update { it.copy(suggestion = found) }
        }
    }

    private suspend fun readLog(): List<CallRecord> = withContext(Dispatchers.IO) {
        runCatching { store.recent(ROWS) }.getOrDefault(emptyList())
    }

    private fun publishGroups(source: List<CallRecord> = records) {
        val context = getApplication<Application>()
        val tz = TimeZone.getDefault()
        val offsetMinutes = tz.getOffset(System.currentTimeMillis()) / 60_000
        val today = CallFormat.epochDay(System.currentTimeMillis(), offsetMinutes)
        val labels = DayLabels(
            today = context.getString(R.string.today),
            yesterday = context.getString(R.string.yesterday),
        )
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())

        val groups = ArrayList<CallGroup>()
        var currentDay = Long.MIN_VALUE
        for (record in source) {
            val dayDiff = CallFormat.dayDiff(
                CallFormat.epochDay(record.epochMillis, offsetMinutes),
                today,
            )
            if (dayDiff != currentDay) {
                currentDay = dayDiff
                groups.add(CallGroup(CallFormat.groupLabel(dayDiff, labels) {
                    dateFormat.format(Date(record.epochMillis))
                }, emptyList()))
            }
            val index = groups.lastIndex
            groups[index] = groups[index].let { it.copy(rows = it.rows + record.toRow(offsetMinutes)) }
        }
        _state.update { it.copy(groups = groups) }
    }

    private fun CallRecord.toRow(offsetMinutes: Int): CallRow = CallRow(
        id = id,
        title = who,
        number = PhoneNumber.display(number),
        timeText = CallFormat.timeOfDay(epochMillis, offsetMinutes),
        // -1 у провайдері означає «розмови не було», тому показуємо порожньо
        durationText = if (durationSec < 0) "" else CallFormat.duration(durationSec),
        direction = direction,
        missed = direction == CallDirection.Missed || direction == CallDirection.Rejected,
    )

    private companion object {
        const val ROWS = 100
        const val MIN_QUERY = 2
        const val SUGGEST_DEBOUNCE_MS = 140L
    }
}
