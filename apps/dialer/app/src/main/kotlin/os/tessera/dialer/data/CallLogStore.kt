package os.tessera.dialer.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import os.tessera.dialer.logic.CallRecord
import os.tessera.dialer.logic.CallTypes
import os.tessera.dialer.logic.PhoneNumber

/** Рядок-підказка над клавіатурою набору: контакт, чиє імʼя чи номер збігається з набраним. */
data class Suggestion(val name: String, val number: String, val label: String)

/**
 * Журнал викликів і контакти — єдине місце, де застосунок торкається
 * `ContentProvider` системи.
 *
 * Усе читання тут залежить дозволів, яких може не бути: без `READ_CALL_LOG`
 * провайдер кидає `SecurityException`, а без `READ_CONTACTS` імені контакту
 * просто немає. Обидва випадки нормальні — користувач міг відмовити, — тому
 * повертаємо порожній список замість падіння, а рядок тоді показує номер.
 */
class CallLogStore(private val context: Context) {

    /** Свіжі записи журналу, від нового до старого. */
    fun recent(limit: Int): List<CallRecord> {
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
        )
        val rows = forEach(
            CallLog.Calls.CONTENT_URI,
            projection,
            sortOrder = CallLog.Calls.DATE + " DESC LIMIT " + limit.coerceIn(1, MAX_ROWS),
        ) { c ->
            val type = c.getInt(3)
            CallRecord(
                id = c.getLong(0),
                number = c.getString(1).orEmpty(),
                contactName = c.getString(2)?.takeIf { it.isNotBlank() },
                direction = CallTypes.directionOf(type),
                epochMillis = c.getLong(4),
                // Для пропущених і відхилених Framework пише -1: це не «0
                // секунд», а «розмови не було», і оформлення рядка на це спирається.
                durationSec = if (CallTypes.isMissed(type)) -1L else c.getLong(5),
            )
        }
        // Сортування в провайдері — лише перша спроба: частина реалізацій його
        // ігнорує, тому порядок доводимо на своєму боці
        return rows.sortedByDescending { it.epochMillis }
    }

    /**
     * Контакти, що відповідають набраному.
     *
     * `PhoneLookup.CONTENT_FILTER_URI` робить і Т9, і пошук за частиною номера
     * на боці провайдера — саме тому не треба тримати в памʼяті всю телефонну
     * книгу й вигадувати власний кириличний розклад клавіатури.
     */
    fun suggest(query: String): Suggestion? {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(cleaned),
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.NUMBER,
            ContactsContract.PhoneLookup.LABEL,
        )
        return forEach(uri, projection) { c ->
            Suggestion(
                name = c.getString(0).orEmpty(),
                number = PhoneNumber.normalize(c.getString(1).orEmpty()),
                label = c.getString(2).orEmpty(),
            )
        }.firstOrNull { it.number.isNotEmpty() }
    }

    /**
     * Видалення запису з журналу. Потребує `WRITE_CALL_LOG` — права, якого на
     * старті не просимо, тому false можливий і через відмову користувача.
     */
    fun delete(id: Long): Boolean = runCatching {
        context.contentResolver.delete(
            Uri.withAppendedPath(CallLog.Calls.CONTENT_URI, id.toString()),
            null,
            null,
        ) > 0
    }.getOrDefault(false)

    private inline fun <T> forEach(
        uri: Uri,
        projection: Array<String>,
        sortOrder: String? = null,
        map: (Cursor) -> T,
    ): List<T> = runCatching {
        val out = ArrayList<T>()
        context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { c ->
            while (c.moveToNext()) out.add(map(c))
        }
        out
    }.getOrDefault(emptyList())

    private companion object {
        /** Стеля для LIMIT у запиті до журналу: більше рядків за екраном не треба. */
        const val MAX_ROWS = 200
    }
}
