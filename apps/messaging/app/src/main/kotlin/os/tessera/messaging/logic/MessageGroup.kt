package os.tessera.messaging.logic

/**
 * Стан вихідного повідомлення.
 *
 * «Доставлено» тут навмисно немає: провайдер публічно знає лише `STATUS`
 * (модем підтвердив чи ні), а колонка `delivery` для звітів GSM 03.40 в SDK
 * прихована. Тож обіцянку «прочитано у тебе» показували б неправду.
 */
enum class MessageStatus { Pending, Sent, Failed }

/**
 * Один рядок розмови.
 *
 * `threadId` бере з провайдера: саме він, а не наш `key()`, вирішує, які
 * повідомлення належать одній особі, — платформа нормалізує адреси й розводить
 * нитки самостійно. Наш ключ потрібен для показу, пошуку та імені контакту.
 */
data class Message(
    val id: Long,
    val address: String,
    val body: String,
    val timestampMillis: Long,
    val fromMe: Boolean,
    val status: MessageStatus = MessageStatus.Sent,
    val read: Boolean = true,
    val threadId: Long = NO_THREAD,
)

/** Нитка, якої провайдер не призначив (рядку ще немає в базі). */
const val NO_THREAD = -1L

/**
 * Група бульбашок: кілька повідомлень від одного боку, показані як блок.
 *
 * Час показуємо один раз на групу (`timestampMillis` — найсвіжіше), а не під
 * кожною бульбашкою: інакше шість швидких відповідей перетворюють розмову на
 * стовпчик однакових «12:03».
 */
data class MessageGroup(
    val dayKey: Long,
    val messages: List<Message>,
) {
    val timestampMillis: Long get() = messages.last().timestampMillis
    val fromMe: Boolean get() = messages.last().fromMe
}

/**
 * Розкладка нитки на групи за днями.
 *
 * Повідомлення в провайдері лежать у порядку надходження, а розмова — знизу
 * вгору, тому на вхід чекаємо хронологічний список, і такий самий віддаємо.
 */
object ThreadGrouping {

    private const val MINUTE_MILLIS = 60_000L
    private const val DAY_MILLIS = 24 * 60 * MINUTE_MILLIS

    /** Перерва довша за цю розриває групу: дві хвилини — типовий темп набору. */
    const val GROUP_WINDOW_MILLIS = 2 * MINUTE_MILLIS

    /** Номер доби в днях від епохи, з урахуванням місцевого зсуву. */
    fun epochDay(epochMillis: Long, offsetMinutes: Int): Long =
        Math.floorDiv(epochMillis + offsetMinutes * MINUTE_MILLIS, DAY_MILLIS)

    fun groups(messages: List<Message>, offsetMinutes: Int): List<MessageGroup> {
        val result = mutableListOf<MessageGroup>()
        for (message in messages) {
            val day = epochDay(message.timestampMillis, offsetMinutes)
            val last = result.lastOrNull()
            val continues = last != null &&
                last.fromMe == message.fromMe &&
                last.dayKey == day &&
                message.timestampMillis - last.timestampMillis <= GROUP_WINDOW_MILLIS
            if (continues && last != null) {
                result[result.lastIndex] = last.copy(messages = last.messages + message)
            } else {
                result += MessageGroup(day, listOf(message))
            }
        }
        return result
    }
}
