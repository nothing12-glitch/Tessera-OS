package os.tessera.clock.data

import android.content.Context
import os.tessera.clock.logic.AlarmCodec
import os.tessera.clock.logic.AlarmSpec

/**
 * Схема будильників у SharedPreferences.
 *
 * Саме кодування (які символи розділяють поля й записи, чому це безпечно для
 * довільного підпису) живе в чистому [AlarmCodec] і накриті тестом; тут лише
 * читання/запис ключа.
 */
class AlarmStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun all(): List<AlarmSpec> = AlarmCodec.decode(prefs.getString(KEY, "").orEmpty())

    fun replaceAll(alarms: List<AlarmSpec>) {
        prefs.edit().putString(KEY, AlarmCodec.encode(alarms)).apply()
    }

    fun find(id: Long): AlarmSpec? = all().firstOrNull { it.id == id }

    companion object {
        private const val FILE = "tessera_clock_alarms"
        private const val KEY = "alarms"

        /**
         * Ідентифікатор для нового будильника. Значення лише зростає, тож
         * навіть після видалення не буде колізій у PendingIntent.
         */
        fun nextId(context: Context): Long {
            val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return prefs.getLong(KEY_SEQ, 0L) + 1L
        }

        fun commitId(context: Context, id: Long) {
            context.applicationContext
                .getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_SEQ, id)
                .apply()
        }

        private const val KEY_SEQ = "last_id"
    }
}
