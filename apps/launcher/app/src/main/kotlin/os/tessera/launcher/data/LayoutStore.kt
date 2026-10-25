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

    /**
     * Розклад для першого запуску: док із зателефонувати/написати/зняти, решта —
     * на головному екрані. Без цього новий користувач бачив порожню сітку й
     * пустий док, поки сам нічого не прикріпив.
     *
     * [KEY_SEEDED] — окремий прапорець, а не «списки порожні»: користувач має
     * право прибрати всі іконки, і тоді розклад не повинен повертатися.
     */
    fun seedFirstLayout(apps: List<LauncherApp>, homeCapacity: Int, dockSlots: Int) {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        val byPackage = apps.associateBy { it.component.packageName }
        val dock = DOCK_PACKAGES.mapNotNull { byPackage[it]?.id }.take(dockSlots)
        val rest = apps.map { it.id }.filterNot { it in dock }
        write(KEY_DOCK, dock)
        // Наші застосунки попереду: без цього першу сторінку повністю
        // заповнюють назвні додатки системи, відсортовані за підписом.
        val ours = rest.filter { it.startsWith(OWN_PREFIX) }
        write(KEY_HOME, (ours + rest.filterNot { it in ours }).take(homeCapacity))
        // прапорець — останнім: якщо процес обірветься посередині, наступного
        // запуску розклад допишеться, а не зникне назавжди
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

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
        const val KEY_SEEDED = "seeded"
        const val SEPARATOR = ","

        /** Наші застосунки пізнаються за префіксом id — вони попереду на сітці. */
        const val OWN_PREFIX = "os.tessera."

        /** Діал-панель, SMS, фото, файли й годинник — те, що на Pixel у доку. */
        val DOCK_PACKAGES = listOf(
            "os.tessera.dialer",
            "os.tessera.messaging",
            "os.tessera.camera",
            "os.tessera.files",
            "os.tessera.clock",
        )
    }
}
