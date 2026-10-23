package os.tessera.clock.logic

/**
 * Розділювальний-безпечне кодування списку будильників у один рядок для
 * SharedPreferences ( і навпаки).
 *
 * Підпис користувача може містити будь-який текст, тому поля й записи
 * розділяємо ASCII-керуючими символами, які неможливо набрати зі звичайної
 * клавіатури:
 *  * U+001F (Unit Separator) — між полями одного запису;
 *  * U+001E (Record Separator) — між записами.
 *
 * На всяк випадок викидаємо обидва символи з кожного текстового поля перед
 * записом: навіть якесь «дуже розумне» джерело тексту не зіпсує формат, і
 * декодування завжди поверне стільки ж записів, скільки закодували.
 */
object AlarmCodec {

    private const val FIELD = "\u001F"
    private const val RECORD = "\u001E"

    private fun clean(value: String): String = value.replace(FIELD, "").replace(RECORD, "")

    fun encode(alarms: List<AlarmSpec>): String =
        alarms.joinToString(RECORD) { a ->
            listOf(
                a.id.toString(),
                a.hour.toString(),
                a.minute.toString(),
                clean(a.label),
                if (a.repeatDaily) "1" else "0",
                if (a.enabled) "1" else "0",
            ).joinToString(FIELD)
        }

    /**
     * Повертає щось розбірливе; зламаний або чужий запис просто пропускаємо,
     * а не падаємо — бекап міг потрапити зі старшої версії.
     */
    fun decode(text: String): List<AlarmSpec> {
        if (text.isBlank()) return emptyList()
        return text.split(RECORD).mapNotNull { record ->
            val f = record.split(FIELD)
            if (f.size < 6) return@mapNotNull null
            val id = f[0].toLongOrNull() ?: return@mapNotNull null
            val hour = f[1].toIntOrNull() ?: return@mapNotNull null
            val minute = f[2].toIntOrNull() ?: return@mapNotNull null
            if (hour !in 0..23 || minute !in 0..59) return@mapNotNull null
            AlarmSpec(
                id = id,
                hour = hour,
                minute = minute,
                label = f[3],
                repeatDaily = f[4] == "1",
                enabled = f[5] == "1",
            )
        }
    }
}
