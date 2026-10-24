package os.tessera.dialer.logic

/**
 * Номер у полі набору.
 *
 * Рядок зберігається саме в тому вигляді, в якому його приймає telecom: цифри,
 * `+` на початку, символи паузи з [DialpadKeys.SEPARATORS] і `*`/`#`, без яких
 * не працюють USSD-коди операторів. Тому валідуюмо набір дозволених символів,
 * а не «тільки цифри».
 */
data class DialpadState(val text: String = "") {

    val digitsOnly: String get() = text.filter { it.isDigit() }

    val canCall: Boolean get() = digitsOnly.isNotEmpty() && text.length <= MAX_LENGTH

    fun append(symbol: Char): DialpadState {
        if (!isDialable(symbol)) return this
        if (symbol == '+' && text.isNotEmpty()) return this
        if (text.length >= MAX_LENGTH) return this
        return DialpadState(text + symbol)
    }

    fun backspace(): DialpadState =
        if (text.isEmpty()) this else DialpadState(text.dropLast(1))

    fun clear(): DialpadState = DialpadState("")

    fun formatted(): String = PhoneNumber.display(text)

    companion object {
        const val MAX_LENGTH = 24

        fun isDialable(symbol: Char): Boolean =
            symbol.isDigit() || symbol == '+' || symbol == '*' || symbol == '#' ||
                symbol in DialpadKeys.SEPARATORS
    }
}
