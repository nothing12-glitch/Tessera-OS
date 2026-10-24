package os.tessera.messaging.logic

/**
 * Адреса співрозмовника: ключ нитки й показ у списку.
 *
 * Нитка в `Telephony.Sms` живе за рядком адреси, але той самий номер провайдер
 * може віддати і як `+380671234567`, і як `0671234567`, і з пробілами. Тому
 * порівнюємо не рядки, а ключі: цифри з можливим провідним `+`.
 */
object SmsAddress {

    /**
     * Найкоротший запис, за яким ще можна впізнати номер.
     *
     * Хвіст коротший за сім цифр занадто легко збігається випадково, а
     * порівнювати «останні N» замість «довший закінчується на коротший»
     * не можна: в українському мобільному номері перші чотири цифри — код
     * оператора, тож `0671234567` і `0501234567` мають однаковий сімцифровий
     * хвіст, але це різні люди.
     */
    private const val MIN_MATCH_DIGITS = 7

    /** `+380 (067)…`: код країни (1-3 цифри), пропуск і дужка, що починається з нуля. */
    private val TRUNK_IN_PARENS = Regex("""(\+\s*\d{1,3})\s*\(0""")

    /**
     * Лишає цифри й `+` на початку; решта символів — форматування, його не є.
     *
     * Окремий випадок — запис `+380 (067) 123-45-67`. Нуль у дужках — це
     * «дзвоніть удома»-нотація: він належить національному набору й зникає,
     * коли номер дивляться з-за кордону (так само `+44 (020) 7946 0018`).
     * Контакти так пишуть часто, а мережа — ні, тож без спрощення
     * «+380 (067)…» і `+380671234567` лишилися б двома різними нитками.
     *
     * Скидаємо нуль лише тоді, коли його взято в дужки. Спершу пробували
     * вгадувати код країни за довжиною (1-3 цифри) — правило погризло й
     * канонічний `+380671234567`, бо «380» і «067» відрізнити один від одного
     * без довідника кодів неможливо. Дужки — єдиний надійний сигнал.
     */
    fun key(raw: String): String {
        val trimmed = raw.trimStart()
        val canonical = TRUNK_IN_PARENS.replaceFirst(trimmed, "$1(")
        val digits = canonical.filter { it.isDigit() }
        return if (trimmed.startsWith("+") && digits.isNotEmpty()) "+$digits" else digits
    }

    /**
     * Чи це той самий співрозмовник.
     *
     * Рівність ключів — очевидний випадок. Другий — коли один записано з кодом
     * країни, а інший без: тоді перевіряємо, чи довший закінчується на коротший
     * (саме так `+38067…` і `067…` співпадають, не плутаючи операторів).
     */
    fun sameThread(a: String, b: String): Boolean {
        val ka = key(a).trimStart('+')
        val kb = key(b).trimStart('+')
        if (ka == kb) return true
        val (longer, shorter) = if (ka.length >= kb.length) ka to kb else kb to ka
        return shorter.length >= MIN_MATCH_DIGITS && longer.endsWith(shorter)
    }

    /**
     * Номер для показу: `+380 671 234 567` і `067 123 45 67`.
     *
     * Перші три цифри міжнародного запису вважаємо кодом країни — евристика, та
     * сама, що в «Телефоні» (див. `PhoneNumber.display`). Групування тут своє,
     * а не спільне: застосунки збираються як окремі APK й не мають спільного
     * модуля, тож кілька повторених рядків — свідомана ціна за те, що кожен
     * застосунок лишається збирабельним наодинці.
     */
    fun display(address: String): String {
        // Спершу нормалізуємо тим самим ключем, щоб `+380 (067) 123-45-67`
        // показувався як `+380 671 234 567`, а не як «380 0671 2345 67».
        val canonical = key(address)
        val international = canonical.startsWith("+")
        val digits = canonical.filter { it.isDigit() }
        return when {
            international && digits.length > 6 ->
                "+${digits.take(3)} ${groupFromRight(digits.drop(3))}"
            !international && digits.length == 10 ->
                "${digits.take(3)} ${digits.substr(3, 3)} ${digits.substr(6, 2)} ${digits.substr(8, 2)}"
            else -> address
        }
    }

    private fun groupFromRight(digits: String): String =
        digits.reversed().chunked(3).joinToString(" ").reversed()

    private fun String.substr(from: Int, count: Int): String = substring(from, from + count)
}
