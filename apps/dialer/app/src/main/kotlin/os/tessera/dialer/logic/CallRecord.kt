package os.tessera.dialer.logic

/** Запис історії викликів у тому вигляді, в якому його дає `CallLog.Calls`. */
data class CallRecord(
    val id: Long,
    val number: String,
    val contactName: String?,
    val direction: CallDirection,
    val epochMillis: Long,
    val durationSec: Long,
) {
    val isMissed: Boolean get() = direction == CallDirection.Missed

    /** Імʼя контакту, якщо воно є; інакше — форматований номер. */
    val who: String
        get() = contactName?.takeIf { it.isNotBlank() }
            ?: PhoneNumber.display(number)
}

enum class CallDirection { Incoming, Outgoing, Missed, Rejected, Voicemail, Blocked }
