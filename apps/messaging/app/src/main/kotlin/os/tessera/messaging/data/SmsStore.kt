package os.tessera.messaging.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import os.tessera.messaging.logic.Message
import os.tessera.messaging.logic.MessageStatus
import os.tessera.messaging.logic.NO_THREAD

/**
 * Памʼять SMS — єдине місце, де застосунок торкається провайдера.
 *
 * Ми не тримаємо власної бази: `Telephony.Sms` — і так сховище, і той самий
 * вміст, який бачать інші застосунки та резервні копії. Ціна — більшість
 * запитів залежить від ролі: провайдер пише лише основному застосунку SMS,
 * тому кожен виклик обгорнутий у `runCatching` і повертає порожнє/−1 замість
 * падіння (користувач міг не надати роль).
 */
class SmsStore(private val context: Context) {

    /** Усі свіжі повідомлення одним запитом — список розмов будує `ThreadSummaries`. */
    fun recentMessages(limit: Int = ROWS): List<Message> = query(
        uri = Telephony.Sms.CONTENT_URI,
        projection = PROJECTION,
        selection = null,
        args = null,
        // LIMIT прямо в sortOrder: окремий API для обмеження рядків у
        // CursorQuery не має, а тягнути всю історію за екраном не треба
        sortOrder = Telephony.Sms.DATE + " DESC LIMIT " + limit.coerceIn(1, MAX_ROWS),
    )

    /**
     * Вихідне повідомлення.
     *
     * Платформа не записує надіслане за нас: коли застосунок — основний SMS,
     * автоматичне дописування вимкнено, бо інакше не можна було б контролювати
     * стан (pending/failed) і порядок частин. Тому спочатку рядок із
     * `STATUS_PENDING`, потім оновлення за результатом PendingIntent.
     */
    fun insertSent(address: String, body: String, timestampMillis: Long, threadId: Long = NO_THREAD): Long {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestampMillis)
            put(Telephony.Sms.DATE_SENT, timestampMillis)
            put(Telephony.Sms.TYPE, Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT)
            put(Telephony.Sms.STATUS, Telephony.TextBasedSmsColumns.STATUS_PENDING)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            if (threadId != NO_THREAD) put(Telephony.Sms.THREAD_ID, threadId)
        }
        val uri = runCatching { context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values) }
            .getOrNull()
        return uri?.lastPathSegment?.toLongOrNull() ?: NO_THREAD
    }

    /** Вхідне повідомлення, яке платформа доставила нам як основному застосунку. */
    fun insertInbox(address: String, body: String, timestampMillis: Long, subject: String? = null): Long {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestampMillis)
            put(Telephony.Sms.TYPE, Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.SEEN, 0)
            // READ=0 — і є «непрочитане»: список показує цифру саме звідси
            put(Telephony.Sms.READ, 0)
            subject?.let { put(Telephony.Sms.SUBJECT, it) }
        }
        val uri = runCatching { context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }
            .getOrNull()
        return uri?.lastPathSegment?.toLongOrNull() ?: NO_THREAD
    }

    fun updateStatus(id: Long, status: MessageStatus, errorCode: Int = 0): Boolean {
        if (id == NO_THREAD) return false
        val values = ContentValues().apply {
            put(
                Telephony.Sms.STATUS,
                when (status) {
                    MessageStatus.Pending -> Telephony.TextBasedSmsColumns.STATUS_PENDING
                    MessageStatus.Sent -> Telephony.TextBasedSmsColumns.STATUS_COMPLETE
                    MessageStatus.Failed -> Telephony.TextBasedSmsColumns.STATUS_FAILED
                },
            )
            put(Telephony.Sms.ERROR_CODE, errorCode)
        }
        return update(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), values)
    }

    fun markThreadRead(threadId: Long): Boolean = update(
        Telephony.Sms.CONTENT_URI,
        ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) },
        selection = "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
        args = arrayOf(threadId.toString()),
    )

    fun deleteThread(threadId: Long): Int = runCatching {
        context.contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
        )
    }.getOrDefault(0)

    /**
     * Імʼя контакту за адресою.
     *
     * `PhoneLookup.CONTENT_FILTER_URI` нормалізує номер сам (через
     * `PhoneNumberUtils` платформи), тому нам не доводиться вгадувати код
     * країни — достатньо віддати те, що лежить у рядку SMS.
     */
    fun contactName(address: String): String? {
        val digits = address.filter { it.isDigit() }
        if (digits.length < MIN_LOOKUP_DIGITS) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(digits),
        )
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun update(uri: Uri, values: ContentValues): Boolean = update(uri, values, null, null)

    private fun update(
        uri: Uri,
        values: ContentValues,
        selection: String?,
        args: Array<String>?,
    ): Boolean = runCatching {
        context.contentResolver.update(uri, values, selection, args) > 0
    }.getOrDefault(false)

    private fun query(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
        sortOrder: String,
    ): List<Message> = runCatching {
        val out = ArrayList<Message>()
        context.contentResolver.query(uri, projection, selection, args, sortOrder)?.use { c ->
            while (c.moveToNext()) out.add(c.toMessage())
        }
        out
    }.getOrDefault(emptyList())

    private fun Cursor.toMessage(): Message {
        val type = getInt(COLUMN_TYPE)
        val fromMe = type != Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX
        val status = when {
            !fromMe -> MessageStatus.Sent
            getInt(COLUMN_STATUS) == Telephony.TextBasedSmsColumns.STATUS_FAILED -> MessageStatus.Failed
            getInt(COLUMN_STATUS) == Telephony.TextBasedSmsColumns.STATUS_PENDING -> MessageStatus.Pending
            else -> MessageStatus.Sent
        }
        return Message(
            id = getLong(COLUMN_ID),
            address = getString(COLUMN_ADDRESS).orEmpty(),
            body = getString(COLUMN_BODY).orEmpty(),
            timestampMillis = getLong(COLUMN_DATE),
            fromMe = fromMe,
            status = status,
            read = getInt(COLUMN_READ) != 0 || fromMe,
            threadId = getLong(COLUMN_THREAD),
        )
    }

    private companion object {
        /** Рядків на екрані списку завжди менше; стеля захищає від усієї історії. */
        const val ROWS = 600
        const val MAX_ROWS = 1000

        /** Коротші за сім цифр номери PhoneLookup плутає з десятками контактів. */
        const val MIN_LOOKUP_DIGITS = 7

        const val COLUMN_ID = 0
        const val COLUMN_THREAD = 1
        const val COLUMN_ADDRESS = 2
        const val COLUMN_BODY = 3
        const val COLUMN_DATE = 4
        const val COLUMN_TYPE = 5
        const val COLUMN_STATUS = 6
        const val COLUMN_READ = 7

        val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS,
            Telephony.Sms.READ,
        )
    }
}
