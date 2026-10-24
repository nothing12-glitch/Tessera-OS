package os.tessera.messaging

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.telephony.SmsManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.tessera.messaging.data.SmsStore
import os.tessera.messaging.logic.DayLabels
import os.tessera.messaging.logic.Message
import os.tessera.messaging.logic.MessageGroup
import os.tessera.messaging.logic.MessageStatus
import os.tessera.messaging.logic.SmsAddress
import os.tessera.messaging.logic.SmsBudget
import os.tessera.messaging.logic.SmsParts
import os.tessera.messaging.logic.ThreadFormat
import os.tessera.messaging.logic.ThreadGrouping
import os.tessera.messaging.logic.ThreadSummary
import os.tessera.messaging.logic.ThreadSummaries
import os.tessera.messaging.receive.SendStatusReceiver

/** Екран, який бачить користувач. */
enum class MessagingScreen { Threads, Conversation, NewConversation }

/** Рядок списку розмов. */
data class ThreadRow(
    val threadId: Long,
    val title: String,
    val snippet: String,
    val timeLabel: String,
    val unread: Int,
)

/** Одна бульбашка в тому вигляді, в якому її малює Compose. */
data class BubbleRow(
    val id: Long,
    val text: String,
    val mine: Boolean,
    val timeLabel: String,
    val pending: Boolean,
    val failed: Boolean,
)

/** Повідомлення одного боку, показані одним блоком. */
data class BubbleGroup(val bubbles: List<BubbleRow>)

/** Бульбашки одного дня: заголовок зверху, знизу — групи бульбашок. */
data class DaySection(val label: String, val groups: List<BubbleGroup>)

data class MessagingUiState(
    val screen: MessagingScreen = MessagingScreen.Threads,
    val rows: List<ThreadRow> = emptyList(),
    val query: String = "",
    val header: String = "",
    val daySections: List<DaySection> = emptyList(),
    val draft: String = "",
    val budget: SmsBudget = SmsParts.budget(""),
    val openAddress: String = "",
    val isDefaultApp: Boolean = true,
    val smsReadable: Boolean = true,
    val notice: String? = null,
) {
    val canSend: Boolean get() = draft.isNotBlank()
    val showComposer: Boolean get() = screen != MessagingScreen.Threads
}

/**
 * Стан «Повідомлень».
 *
 * Архітектура та сама, що в «Телефоні»: арифметика, формати й розкладання на
 * групи живуть у чистих модулях `logic` (накриті юніт-тестами), а сюди доходять
 * лише сирі рядки з провайдера. Список розмов і вся нитка рахуються з одного
 * читання, тож відкриття розмови — це фільтрація в памʼяті, а не другий похід у
 * SQL; завдяки цьому «нова розмова» з довільного номера не потребує нитки,
 * якої в базі ще немає.
 *
 * Надсилання робить сам VM: на відміну від «Телефона», де дзвонить активність
 * (бо в неї є вікно й запит дозволу), `SmsManager` не потребує нічого з
 * контекста активності. Запит ролі — виняток: його активність, тому намір
 * виходить у [roleRequests].
 */
class MessagingViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SmsStore(app)

    private val _state = MutableStateFlow(MessagingUiState())
    val state: StateFlow<MessagingUiState> = _state.asStateFlow()

    private val _roleRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val roleRequests: SharedFlow<Unit> = _roleRequests.asSharedFlow()

    private var all: List<Message> = emptyList()

    /** Імена контактів: ключ — `SmsAddress.key`, бо адреси в рядках різні. */
    private var names: Map<String, String> = emptyMap()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val messages = runCatching { withContext(Dispatchers.IO) { store.recentMessages() } }
                .getOrNull()
            _state.update { it.copy(smsReadable = messages != null) }
            val rows = messages.orEmpty()
            all = rows
            names = names + lookupNames(rows.map { it.address }.distinct().take(MAX_NAMES))
            publishThreads()
            if (_state.value.screen == MessagingScreen.Conversation) publishThread()
        }
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        publishThreads()
    }

    fun openThread(threadId: Long) {
        val summary = summaries().firstOrNull { it.threadId == threadId } ?: return
        _state.update {
            it.copy(
                screen = MessagingScreen.Conversation,
                openAddress = summary.address,
                header = summary.title,
            )
        }
        publishThread()
        if (summary.hasUnread) markRead(summary.threadId)
    }

    fun startNewConversation() =
        _state.update { it.copy(screen = MessagingScreen.NewConversation, openAddress = "") }

    fun setAddress(address: String) = _state.update { it.copy(openAddress = address) }

    fun confirmAddress() {
        val key = SmsAddress.key(_state.value.openAddress)
        if (key.filter { it.isDigit() }.length < MIN_NUMBER_DIGITS) return
        _state.update {
            it.copy(
                screen = MessagingScreen.Conversation,
                openAddress = key,
                header = nameOf(key) ?: SmsAddress.display(key),
            )
        }
        publishThread()
    }

    fun back() {
        _state.update {
            it.copy(
                screen = MessagingScreen.Threads,
                openAddress = "",
                header = "",
                daySections = emptyList(),
                draft = "",
                budget = SmsParts.budget(""),
            )
        }
        publishThreads()
    }

    fun setDraft(text: String) =
        _state.update { it.copy(draft = text, budget = SmsParts.budget(text)) }

    fun setIsDefault(isDefault: Boolean) {
        if (_state.value.isDefaultApp == isDefault) return
        _state.update { it.copy(isDefaultApp = isDefault) }
        reload()
    }

    fun requestRole() = _roleRequests.tryEmit(Unit)

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    fun deleteOpenThread() {
        val address = _state.value.openAddress
        if (address.isEmpty()) return
        viewModelScope.launch {
            val threadIds = messagesOf(address).map { it.threadId }.distinct()
            val removed = withContext(Dispatchers.IO) { threadIds.sumOf { store.deleteThread(it) } }
            notice(
                if (removed > 0) R.string.thread_deleted else R.string.no_sms_permission,
            )
            back()
        }
    }

    fun send() {
        val body = _state.value.draft.trim()
        val address = _state.value.openAddress
        if (body.isEmpty() || address.isEmpty()) return
        if (!_state.value.isDefaultApp) {
            requestRole()
            return
        }
        // Оптимістично чистимо поле: інакше користувач бачив би свій текст,
        // поки модем підтверджує частини, і міг натиснути «надіслати» двічі.
        _state.update { it.copy(draft = "", budget = SmsParts.budget("")) }
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val id = store.insertSent(address, body, System.currentTimeMillis())
            val manager = context.getSystemService(SmsManager::class.java)
            if (manager == null) {
                store.updateStatus(id, MessageStatus.Failed)
                notice(R.string.send_failed)
                return@launch
            }
            // Частини рахує НЕ наш лічильник: `divideMessage` — той самий код,
            // що й у модема, тому показаний кошторис і справжні PDU не можуть
            // розійтися так, щоб частина тексту зникла.
            val parts = runCatching { manager.divideMessage(body) }.getOrDefault(listOf(body))
            val sent = List(parts.size) { statusIntent(SendStatusReceiver.ACTION_SENT, id) }
            val outcome = runCatching {
                if (parts.size == 1) {
                    manager.sendTextMessage(address, null, body, sent[0], null)
                } else {
                    manager.sendMultipartTextMessage(address, null, ArrayList(parts), ArrayList(sent), null)
                }
            }
            if (outcome.isFailure) {
                store.updateStatus(id, MessageStatus.Failed)
                notice(R.string.send_failed)
            }
        }
    }

    // --- службове ---------------------------------------------------------

    /**
     * PendingIntent.result йде в `SendStatusReceiver`; рядок передаємо екстрою,
     * бо код запиту в PendingIntent обмежений 32 бітами, а `_id` провайдера — long.
     */
    private fun statusIntent(action: String, rowId: Long): PendingIntent {
        val context = getApplication<Application>()
        val intent = Intent(action).apply {
            setPackage(context.packageName)
            putExtra(SendStatusReceiver.EXTRA_ROW_ID, rowId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(rowId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Код запиту має різнити рядки: інакше результат однієї sms-ки перезаписав би іншу. */
    private fun requestCode(rowId: Long): Int = (rowId xor (rowId ushr 32)).toInt()

    private suspend fun lookupNames(addresses: List<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            addresses.mapNotNull { address ->
                val name = runCatching { store.contactName(address) }.getOrNull() ?: return@mapNotNull null
                SmsAddress.key(address) to name
            }.toMap()
        }

    private fun nameOf(key: String): String? = names[key]

    private fun messagesOf(address: String): List<Message> =
        all.filter { SmsAddress.sameThread(it.address, address) }.sortedBy { it.timestampMillis }

    private fun summaries(): List<ThreadSummary> =
        ThreadSummaries.of(all) { nameOf(SmsAddress.key(it)) }

    private fun publishThreads() {
        val context = getApplication<Application>()
        val offset = offsetMinutes()
        val today = ThreadGrouping.epochDay(System.currentTimeMillis(), offset)
        val yesterday = context.getString(R.string.yesterday)
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
        val rows = ThreadFormat.filter(_state.value.query, summaries()).map { summary ->
            ThreadRow(
                threadId = summary.threadId,
                title = summary.title,
                snippet = summary.snippet,
                timeLabel = ThreadFormat.listLabel(
                    summary.timestampMillis,
                    today,
                    offset,
                    yesterday,
                ) { dateFormat.format(Date(summary.timestampMillis)) },
                unread = summary.unread,
            )
        }
        _state.update { it.copy(rows = rows) }
    }

    private fun publishThread() {
        val address = _state.value.openAddress
        if (address.isEmpty()) return
        val context = getApplication<Application>()
        val offset = offsetMinutes()
        val today = ThreadGrouping.epochDay(System.currentTimeMillis(), offset)
        val labels = DayLabels(
            today = context.getString(R.string.today),
            yesterday = context.getString(R.string.yesterday),
        )
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())

        val sections = ArrayList<DaySection>()
        var currentDay = Long.MIN_VALUE
        for (group in ThreadGrouping.groups(messagesOf(address), offset)) {
            val dayDiff = today - group.dayKey
            if (dayDiff != currentDay) {
                currentDay = dayDiff
                sections.add(
                    DaySection(
                        ThreadFormat.dayLabel(dayDiff, labels) {
                            dateFormat.format(Date(group.timestampMillis))
                        },
                        emptyList(),
                    ),
                )
            }
            val index = sections.lastIndex
            sections[index] = sections[index].let {
                it.copy(groups = it.groups + group.toBubbleGroup(offset))
            }
        }
        _state.update { it.copy(daySections = sections) }
    }

    /**
     * Рядки для Compose.
     *
     * `timeLabel` рахуємо для кожного повідомлення, хоча малюємо лише в
     * останнього: так UI не мусить знати про зсув часу й арифметику доби.
     */
    private fun MessageGroup.toBubbleGroup(offsetMinutes: Int) = BubbleGroup(
        messages.map { message ->
            BubbleRow(
                id = message.id,
                text = message.body,
                mine = message.fromMe,
                timeLabel = ThreadFormat.timeOfDay(message.timestampMillis, offsetMinutes),
                pending = message.status == MessageStatus.Pending,
                failed = message.status == MessageStatus.Failed,
            )
        },
    )

    private fun markRead(threadId: Long) {
        viewModelScope.launch(Dispatchers.IO) { store.markThreadRead(threadId) }
    }

    private fun notice(res: Int) {
        val text = getApplication<Application>().getString(res)
        _state.update { it.copy(notice = text) }
    }

    private fun offsetMinutes(): Int =
        TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000

    private companion object {
        /** Імен скількох ниток шукаємо за один reload: більше за екраном не видно. */
        const val MAX_NAMES = 40
        const val MIN_NUMBER_DIGITS = 7
    }
}
