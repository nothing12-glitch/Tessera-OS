package os.tessera.dialer.logic

/**
 * Символи клавіатури набору.
 *
 * Літери під цифрами беруться з стандарту ISO/IEC 9995-8 (те саме, що на
 * апаратних телефонах): 7 і 9 мають по чотири літери, решта — по три. 1 і 0
 * літер не мають взагалі, зате 0 утримує «+».
 */
object DialpadKeys {

    val DIGITS: List<String> = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9",
        "*", "0", "#",
    )

    private val LETTERS = mapOf(
        "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL",
        "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    )

    fun lettersFor(digit: String): String = LETTERS[digit].orEmpty()

    /** Літера з ISO-клавіатури → цифра. Реєстр не важливий, не-літери повертають null. */
    fun digitForLetter(letter: Char): Char? =
        LETTERS.entries.firstOrNull { letter.uppercaseChar() in it.value }?.key?.single()

    /** Символ паузи GSM: `,` = 2 с, `;` = пауза з підтвердженням. */
    val SEPARATORS = charArrayOf(',', ';', 'w', 'W', 'p', 'P')
}
