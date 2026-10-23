package os.tessera.calculator.logic

/**
 * Правила набору рядка-виразу. Окремо від Compose, щоб їх можна було
 * перевірити тестом: саме тут калькулятор «думає» за користувача,
 * наприклад замінює останній оператор, а не дозволяє «3++4».
 */
object InputEditor {

    private val OPERATORS = charArrayOf('+', '-', '*', '/')

    fun lastNumber(expr: String): String {
        var i = expr.length - 1
        while (i >= 0 && (expr[i].isDigit() || expr[i] == '.' || expr[i] == ',')) i -= 1
        return expr.substring(i + 1)
    }

    private fun endsWith(expr: String, vararg chars: Char): Boolean =
        expr.isNotEmpty() && expr.last() in chars

    fun pressDigit(expr: String, digit: Char): String = expr + digit

    fun pressDot(expr: String): String {
        if (lastNumber(expr).contains('.')) return expr
        return if (expr.isEmpty() || endsWith(expr, *OPERATORS) || expr.last() == '(') {
            expr + "0."
        } else {
            expr + "."
        }
    }

    fun pressOperator(expr: String, op: Char): String {
        require(op in OPERATORS) { "not an operator: $op" }
        if (expr.isEmpty()) return if (op == '-') "-" else expr
        if (endsWith(expr, '(', '%')) return expr
        val last = expr.last()
        if (last in OPERATORS) {
            // «3 + *» стає «3 *»: останній оператор замінюємо, а не додаємо
            return expr.substring(0, expr.length - 1) + op
        }
        return expr + op
    }

    fun pressPercent(expr: String): String {
        val last = expr.lastOrNull() ?: return expr
        return if (last.isDigit() || last == ')') expr + "%" else expr
    }

    fun pressOpenParen(expr: String): String {
        val last = expr.lastOrNull()
        val afterValue = last != null && (last.isDigit() || last == ')' || last == '%')
        return when {
            expr.isEmpty() -> "("
            afterValue -> expr + "*("
            else -> expr + "("
        }
    }

    fun pressCloseParen(expr: String): String {
        if (expr.isEmpty()) return expr
        val open = expr.count { it == '(' }
        val close = expr.count { it == ')' }
        val last = expr.last()
        return if (open > close && (last.isDigit() || last == '%')) expr + ")" else expr
    }

    fun backspace(expr: String): String = if (expr.isEmpty()) expr else expr.dropLast(1)

    /** Відкритих дужок більше, ніж закритих — «=» добиваємо закриттями. */
    fun complete(expr: String): String {
        val missing = expr.count { it == '(' } - expr.count { it == ')' }
        return if (missing > 0) expr + ")".repeat(missing) else expr
    }
}
