package os.tessera.calculator.logic

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/**
 * Показ чисел у результаті.
 *
 * Символи задаємо самі, а не беремо з default locale: тест має однаково
 * працювати на збірці з LAUNCH_LOCALE=en і на пристрої з українською.
 */
object ResultFormat {

    private const val NBSP = "\u00A0"
    private const val MIN_PLAIN = "1E-10"
    private const val MAX_PLAIN = "1E21"

    private val symbols = DecimalFormatSymbols().apply {
        groupingSeparator = NBSP.single()
        decimalSeparator = ','
    }

    private val plain = DecimalFormat().apply {
        decimalFormatSymbols = symbols
        isGroupingUsed = true
        groupingSize = 3
        maximumFractionDigits = 10
    }

    private val scientific = DecimalFormat(
        "0.##########E0",
        DecimalFormatSymbols().apply {
            decimalSeparator = '.'
            // без цього на пристрої з українською виходить «1Є-12», і перевірити
            // рядок числом неможливо; в UI нам потрібен стабільний «E»
            exponentSeparator = "E"
        },
    )

    fun format(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        val abs = stripped.abs()
        val tiny = abs.signum() != 0 && abs.compareTo(BigDecimal(MIN_PLAIN)) < 0
        val huge = abs.compareTo(BigDecimal(MAX_PLAIN)) >= 0
        // Окремо крапка: у stripTrailingZeros() масштаб стає відʼємним
        // (1E-12 має scale = -12), тож «scale > 0» пропускало б малі числа.
        return if (tiny || huge) scientific.format(stripped) else plain.format(stripped)
    }

    /** Вираз із клавіатури: ASCII-оператори показуємо математичними символами. */
    fun prettyExpression(expression: String): String = buildString(expression.length) {
        for (c in expression) {
            append(
                when (c) {
                    '*' -> '×'
                    '/' -> '÷'
                    '-' -> '−'
                    else -> c
                }
            )
        }
    }
}
