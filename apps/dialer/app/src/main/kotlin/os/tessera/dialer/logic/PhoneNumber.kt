package os.tessera.dialer.logic

/**
 * Перетворення номера між формами: набраний, збережений, показаний.
 *
 * Форматування — евристика, а не libphonenumber: повний валідатор нумераційних
 * планів важить кілька мегабайтів і тягне метадані для кожного коду країни, а
 * для показу в журналі та в полі набору достатньо сталих груп. Правила
 * зафіксовані тестами, щоб форматування не поїхало непомітно.
 */
object PhoneNumber {

    /** Символи форматування, які користувач міг вставити з буфера обміну. */
    private val NOISE = charArrayOf(' ', '-', '(', ')', '.', '–', '—')

    /** Префікс міжнародного запису, у якому нуль у дужках зайвий. */
    private const val TRUNK_MARK = "+"

    /**
     * Усе, крім форматування: цифри, `+` на початку, паузи, `*` і `#`.
     *
     * Один виняток — нуль у дужках у міжнародному записі. За E.164 у
     * «+380 (067) 123-45-67» і «+380 (0) 67 123-45-67» перша цифра в дужках —
     * національний префікс, який при міжнародному наборі відкидають; лишивши
     * його, ми відправили б хибний номер. У національному записі
     * «(067) 123-45-67» той самий нуль потрібний, тому правило діє лише коли
     * рядок починається з `+`.
     */
    fun normalize(raw: String): String {
        val source = if (raw.trimStart().startsWith(TRUNK_MARK)) dropTrunkZero(raw) else raw
        return buildString {
            for (c in source) {
                when {
                    c.isDigit() -> append(c)
                    c == '+' -> append(c)
                    c in DialpadKeys.SEPARATORS -> append(c)
                    c == '*' || c == '#' -> append(c)
                }
            }
        }
    }

    /** Прибирає першу цифру `0` всередині перших дужок; решту не чіпає. */
    private fun dropTrunkZero(raw: String): String {
        val open = raw.indexOf('(')
        if (open < 0) return raw
        var index = open + 1
        while (index < raw.length && raw[index] == ' ') index++
        if (index >= raw.length || raw[index] != '0') return raw
        return raw.substring(0, index) + raw.substring(index + 1)
    }

    /** Лише цифри — для пошуку за частиною номера. */
    fun digits(raw: String): String = raw.filter { it.isDigit() }

    /**
     * Показова форма: `+380 67 123 45 67`.
     *
     * Символи паузи лишаються на місці, бо вони частина номера, а не
     * форматування: `0671234567;1234` означає «подзвони, потім набери 1234».
     */
    fun display(raw: String): String {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return ""
        return buildString {
            val run = StringBuilder()
            for (c in normalized) {
                if (c.isDigit()) run.append(c) else {
                    if (run.isNotEmpty()) { append(group(run.toString())); run.setLength(0) }
                    append(c)
                    if (c != '+' && c !in DialpadKeys.SEPARATORS) append(' ')
                }
            }
            if (run.isNotEmpty()) append(group(run.toString()))
        }
    }

    private fun group(digits: String): String = when {
        digits.length <= 4 -> digits
        digits.length == 10 && digits.startsWith("0") ->
            listOf(digits.substring(0, 3), digits.substring(3, 6),
                   digits.substring(6, 8), digits.substring(8)).joinToString(" ")
        else -> digits.reversed().chunked(3).map { it.reversed() }.reversed().joinToString(" ")
    }

    /** Останні [count] цифр — база для звірення з іменем контакту. */
    fun lastDigits(raw: String, count: Int = 7): String {
        val d = digits(raw)
        return if (d.length <= count) d else d.takeLast(count)
    }

    /**
     * Т9: імʼя контакту → послідовність цифр. `Tessera` → `8377372`.
     *
     * Пошук у журналі набирається на клавіатурі набору, тому порівнюємо цифри,
     * а не літери.
     */
    fun t9(name: String): String = buildString {
        for (c in name) DialpadKeys.digitForLetter(c)?.let { append(it) }
    }

    /**
     * Лише цифри — для пошуку за частиною номера.
     *
     * Порожній запит дає `false`: без цієї угоди пошук «Тет» (літери, яких
     * немає на ISO-клавіатурі) не перетворювався б на «нічого не набрано» і не
     * ловив би ввесь журнал.
     */
    fun matchesT9(name: String, typed: String): Boolean {
        val typedDigits = digits(typed)
        if (typedDigits.isEmpty()) return false
        return t9(name).contains(typedDigits) || digits(name).contains(typedDigits)
    }

    /** Номер придатний до дзвінка: хоча б одна цифра. */
    fun isCallable(raw: String): Boolean = digits(raw).isNotEmpty()

    /** Для збереження в історії викликів: усе, крім цифр і префіксів. */
    fun forStorage(raw: String): String = normalize(raw).filterNot { it in NOISE }
}
