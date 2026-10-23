package os.tessera.launcher.data

import android.content.Context

/**
 * Порядок іконок на головному екрані та в доці.
 *
 * Схоже на те, що стоковий Trebuchet тримає в базі даних Launcher3, але тут
 * свідомо мінімум: список id застосунків, заповнення сітки row-major. Таблиця
 * «який id в якій комірці» зʼявиться разом із перетягуванням.
 */
class LayoutStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun homePins(): List<String> = read(KEY_HOME)

    fun dock(): List<String> = read(KEY_DOCK)

    fun pinToHome(id: String, capacity: Int) = add(KEY_HOME, id, capacity)

    fun unpinFromHome(id: String) = remove(KEY_HOME, id)

    fun pinToDock(id: String, capacity: Int) {
        remove(KEY_HOME, id)
        add(KEY_DOCK, id, capacity)
    }

    fun unpinFromDock(id: String) = add(KEY_HOME, id, Int.MAX_VALUE, atFront = true)
        .also { remove(KEY_DOCK, id) }

    private fun read(key: String): List<String> =
        prefs.getString(key, "")?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()

    private fun write(key: String, value: List<String>) {
        prefs.edit().putString(key, value.joinToString(SEPARATOR)).apply()
    }

    private fun add(key: String, id: String, capacity: Int, atFront: Boolean = false) {
        val current = read(key)
        if (current.contains(id)) return
        val next = if (atFront) listOf(id) + current else current + id
        write(key, next.take(capacity))
    }

    private fun remove(key: String, id: String) {
        write(key, read(key).filterNot { it == id })
    }

    private companion object {
        const val PREFS = "tessera_launcher_layout"
        const val KEY_HOME = "home_pins"
        const val KEY_DOCK = "dock"
        const val SEPARATOR = ","
    }
}
