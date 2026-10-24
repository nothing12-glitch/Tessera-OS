package os.tessera.messaging.logic

/** Нитка в тому вигляді, в якому її показує список розмов. */
data class ThreadSummary(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val timestampMillis: Long,
    val unread: Int,
) {
    /** Що бачить користувач замість номера: імʼя з телефонної книги, якщо є. */
    val title: String get() = contactName?.takeIf { it.isNotBlank() } ?: SmsAddress.display(address)
    val hasUnread: Boolean get() = unread > 0
}

/**
 * Формати списку розмов.
 *
 * Усе тут — чиста арифметика без `Calendar` і без `android.*`, бо саме ці
 * перетворення найлегше перевірити на справжніх межах (опівніч, 7 днів,
 * порожній текст). Дні рахуємо від зсуву, а не від UTC: інакше «Учора»
 * ламалося б на будь-якому пристрої східніше третього годинника.
 */
object ThreadFormat {

    private const val MINUTE_MILLIS = 60_000L
    private const val MINUTES_PER_DAY = 1440L
    private const val SNIPPET_MAX = 96

    /** Час доби у форматі 24 години для заданого зсуву від UTC. */
    fun timeOfDay(epochMillis: Long, offsetMinutes: Int): String {
        // floorMod, а не %: відʼємний момент (до епохи) інакше дав би «-1:59»
        val minutesOfDay = Math.floorMod(
            Math.floorDiv(epochMillis + offsetMinutes * MINUTE_MILLIS, MINUTE_MILLIS),
            MINUTES_PER_DAY,
        )
        return "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)
    }

    /**
     * Мітка у правому кінці рядка: сьогодні — час, учора — «Учора», далі —
     * абсолютна дата, яку вималює викликаний `absolute`.
     */
    fun listLabel(
        epochMillis: Long,
        todayDay: Long,
        offsetMinutes: Int,
        yesterday: String,
        absolute: () -> String,
    ): String {
        val day = ThreadGrouping.epochDay(epochMillis, offsetMinutes)
        return when (todayDay - day) {
            0L -> timeOfDay(epochMillis, offsetMinutes)
            1L -> yesterday
            else -> absolute()
        }
    }

    /**
     * Заголовок дня всередині розмови. Тут «сьогодні» показуємо текстом, а не
     * часом: навігатор по нитці має відповідати на «що це за день», а не «котра».
     *
     * `absolute` — лямбда, а не `Date`, бо функція лишається pure-функцією й
     * тестується без локалі та календаря.
     */
    fun dayLabel(dayDiff: Long, labels: DayLabels, absolute: () -> String): String =
        when (dayDiff) {
            0L -> labels.today
            1L -> labels.yesterday
            else -> absolute()
        }

    /**
     * Однорядкова витяжка з тіла повідомлення.
     *
     * Перенесення табуляції та послідовності пробілів зводимо до одного
     * пробілу: інакше «\n\n» на початку смс-ки давала б порожній рядок у
     * списку, а 200-символьне повідомлення розтягувало б рядок на три висоти.
     */
    fun snippet(body: String): String {
        val oneLine = body.trim().replace(WHITESPACE_RUN, " ")
        return if (oneLine.length <= SNIPPET_MAX) oneLine
        else oneLine.take(SNIPPET_MAX).trimEnd() + "…"
    }

    /**
     * Пошук по списку: імʼя контакту, номер (цифрами, без форматування) і
     * текст останнього повідомлення.
     *
     * Пошук за тілом — це те, чого від SMS-клієнта очікують: «де я писав код
     * бронювання». Номер шукаємо за цифрами, щоб `067` знаходив і
     * `+380671234567`, і `067 123 45 67`.
     */
    fun filter(query: String, threads: List<ThreadSummary>): List<ThreadSummary> {
        val q = query.trim()
        if (q.isEmpty()) return threads
        val digits = q.filter { it.isDigit() }
        return threads.filter { thread ->
            thread.contactName?.contains(q, ignoreCase = true) == true ||
                (digits.isNotEmpty() && SmsAddress.key(thread.address).contains(digits)) ||
                thread.snippet.contains(q, ignoreCase = true)
        }
    }

    /** Сума непрочитаних — для значка на піктограмі застосунку в лаунчері. */
    fun unreadTotal(threads: List<ThreadSummary>): Int = threads.sumOf { it.unread }

    private val WHITESPACE_RUN = Regex("[ \\t\\r\\n\\u00A0]+")
}

/**
 * Зібрання списку розмов із плоского списку повідомлень.
 *
 * Робимо це тут, а не в `Telephony.Sms.Conversations`, з двох причин: по-перше,
 * у погляді провайдера `READ` стосується останнього рядка нитки, а не кількості
 * непрочитаних, і цифра вийшла б нечесною; по-друге, чиста функція на вході з
 * `List<Message>` має юніт-тести, а SQL — ні.
 */
object ThreadSummaries {

    fun of(messages: List<Message>, nameFor: (String) -> String? = { null }): List<ThreadSummary> =
        messages.filter { it.threadId != NO_THREAD }
            .groupBy { it.threadId }
            .map { (threadId, thread) ->
                val newest = thread.maxByOrNull { it.timestampMillis } ?: thread.last()
                ThreadSummary(
                    threadId = threadId,
                    address = newest.address,
                    contactName = nameFor(newest.address),
                    snippet = ThreadFormat.snippet(newest.body),
                    timestampMillis = newest.timestampMillis,
                    unread = thread.count { !it.fromMe && !it.read },
                )
            }
            .sortedByDescending { it.timestampMillis }
}

data class DayLabels(val today: String, val yesterday: String)
