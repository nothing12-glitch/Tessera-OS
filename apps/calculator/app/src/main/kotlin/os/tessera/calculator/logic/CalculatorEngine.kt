package os.tessera.calculator.logic

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Обчислювач арифметичних виразів.
 *
 * Синтаксис: expr := term (('+'|'-') term)*
 *            term := unary (('*'|'/') unary)*
 *            unary := ('-'|'+') unary | power
 *            power := postfix ('^' unary)?
 *            postfix := primary ('%')*
 *            primary := number | '(' expr ')'
 *
 * Соромно коротке «2+3*4» тут рахується з пріоритетом, а не ліворуч, бо саме
 * таку поведінку очікує користувач калькулятора в Pixel OS.
 */
object CalculatorEngine {

    private val math: MathContext = MathContext(20, RoundingMode.HALF_UP)
    private val percent: BigDecimal = BigDecimal(100)

    class CalcException(message: String) : Exception(message)

    fun evaluate(raw: String): BigDecimal {
        val tokens = tokenize(raw)
        if (tokens.isEmpty()) throw CalcException("empty")
        val parser = Parser(tokens)
        val result = parser.expression()
        parser.expectEnd()
        return result
    }

    /** Повертає null, якщо вираз ще не повний — так UI вирізняє «друкуємо далі». */
    fun tryEvaluate(raw: String): BigDecimal? = try {
        evaluate(raw)
    } catch (_: CalcException) {
        null
    }

    private fun tokenize(input: String): List<Token> {
        val out = ArrayList<Token>(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i += 1
                c.isDigit() || c == '.' -> {
                    val start = i
                    var dots = 0
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) {
                        if (input[i] == '.') dots += 1
                        i += 1
                    }
                    if (dots > 1) throw CalcException("double dot at $start")
                    val text = input.substring(start, i)
                    if (text == ".") throw CalcException("lone dot")
                    out += Token.Number(BigDecimal(text))
                }
                else -> {
                    val mapped = when (c) {
                        '×' -> '*'
                        '÷' -> '/'
                        '−', '–' -> '-'
                        '(', ')' -> c
                        else -> c
                    }
                    val kind = when (mapped) {
                        '+' -> Token.Op.ADD
                        '-' -> Token.Op.SUB
                        '*' -> Token.Op.MUL
                        '/' -> Token.Op.DIV
                        '^' -> Token.Op.POW
                        '%' -> Token.Op.PCT
                        '(' -> Token.Op.LPAREN
                        ')' -> Token.Op.RPAREN
                        else -> throw CalcException("bad char '$c'")
                    }
                    out += Token.Operator(kind)
                    i += 1
                }
            }
        }
        return out
    }

    private sealed class Token {
        data class Number(val value: BigDecimal) : Token()
        data class Operator(val op: Op) : Token()

        enum class Op { ADD, SUB, MUL, DIV, POW, PCT, LPAREN, RPAREN }
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun expectEnd() {
            if (pos != tokens.size) throw CalcException("trailing tokens")
        }

        private fun peekOp(): Token.Op? = (tokens.getOrNull(pos) as? Token.Operator)?.op

        private fun takeOp(vararg ops: Token.Op): Token.Op? {
            val op = peekOp() ?: return null
            if (op !in ops) return null
            pos += 1
            return op
        }

        fun expression(): BigDecimal {
            var left = term()
            while (true) {
                val op = takeOp(Token.Op.ADD, Token.Op.SUB) ?: return left
                val right = term()
                left = when (op) {
                    Token.Op.ADD -> left.add(right, math)
                    Token.Op.SUB -> left.subtract(right, math)
                    else -> error("unreachable")
                }
            }
        }

        private fun term(): BigDecimal {
            var left = unary()
            while (true) {
                val op = takeOp(Token.Op.MUL, Token.Op.DIV) ?: return left
                val right = unary()
                left = when (op) {
                    Token.Op.MUL -> left.multiply(right, math)
                    Token.Op.DIV -> divide(left, right)
                    else -> error("unreachable")
                }
            }
        }

        private fun unary(): BigDecimal {
            val op = takeOp(Token.Op.ADD, Token.Op.SUB)
            if (op != null) {
                val v = unary()
                return if (op == Token.Op.SUB) v.negate() else v
            }
            return power()
        }

        /** Степінь правосторонньо асоціативний: 2^3^2 = 2^(3^2), а не (2^3)^2. */
        private fun power(): BigDecimal {
            val base = postfix()
            if (takeOp(Token.Op.POW) == null) return base
            return pow(base, unary())
        }

        private fun postfix(): BigDecimal {
            var v = primary()
            while (takeOp(Token.Op.PCT) != null) {
                v = v.divide(percent, math)
            }
            return v
        }

        private fun primary(): BigDecimal {
            if (takeOp(Token.Op.LPAREN) != null) {
                val v = expression()
                if (takeOp(Token.Op.RPAREN) == null) throw CalcException("unclosed bracket")
                return v
            }
            val token = tokens.getOrNull(pos) as? Token.Number ?: throw CalcException("expected number")
            pos += 1
            return token.value
        }
    }

    private fun divide(a: BigDecimal, b: BigDecimal): BigDecimal = try {
        a.divide(b, math)
    } catch (_: ArithmeticException) {
        throw CalcException("division by zero")
    }

    private fun pow(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        val intExp = exponent.stripTrailingZeros()
        if (intExp.scale() > 0) {
            // дробовий степінь: лише через double, точність тут свідомо втрачається
            val asDouble = Math.pow(base.toDouble(), intExp.toDouble())
            if (asDouble.isNaN() || asDouble.isInfinite()) throw CalcException("undefined power")
            return BigDecimal(asDouble)
        }
        val n = intExp.toInt()
        if (n > 1024) throw CalcException("exponent too large")
        return try {
            if (n < 0) divide(BigDecimal.ONE, base.pow(-n, math)) else base.pow(n, math)
        } catch (_: ArithmeticException) {
            throw CalcException("undefined power")
        }
    }
}
