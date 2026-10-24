package os.tessera.dialer.logic

/**
 * Чисові типи записів журналу.
 *
 * Константи `android.provider.CallLog.Calls` навмисно продубльовані числами, а
 * не імпортовані: маппінг лишається чистою функцією, яку видно в юніт-тесті без
 * апарата. Значення зафіксовані у ProviderContract і не змінювались з часів
 * API 1, тож дубль безпечний — він лише перестає бути правдою, якби Google
 * коли-небудь їх перенумерував.
 */
object CallTypes {
    const val INCOMING = 1
    const val OUTGOING = 2
    const val MISSED = 3
    const val VOICEMAIL = 4
    const val REJECTED = 5
    const val BLOCKED = 6

    /** Дзвінок підняли на іншому пристрої того самого акаунту — для нас це пропущений. */
    const val ANSWERED_EXTERNALLY = 7

    fun directionOf(type: Int): CallDirection = when (type) {
        INCOMING -> CallDirection.Incoming
        OUTGOING -> CallDirection.Outgoing
        MISSED -> CallDirection.Missed
        VOICEMAIL -> CallDirection.Voicemail
        REJECTED -> CallDirection.Rejected
        BLOCKED -> CallDirection.Blocked
        ANSWERED_EXTERNALLY -> CallDirection.Missed
        else -> CallDirection.Outgoing
    }

    /** Пропущені рядки оформлюються інакше: без тривалості й з акцентом. */
    fun isMissed(type: Int): Boolean = type == MISSED || type == ANSWERED_EXTERNALLY
}
