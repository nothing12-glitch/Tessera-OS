package os.tessera.calculator.logic

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {

    private fun value(expression: String): BigDecimal = CalculatorEngine.evaluate(expression)

    @Test
    fun `пріоритет множення над додаванням`() {
        assertEquals(0, BigDecimal("14").compareTo(value("2+3*4")))
    }

    @Test
    fun `дужки змінюють пріоритет`() {
        assertEquals(0, BigDecimal("20").compareTo(value("(2+3)*4")))
    }

    @Test
    fun `ділення повертає дріб`() {
        assertEquals(0, BigDecimal("2.5").compareTo(value("10/4")))
    }

    @Test
    fun `степінь правосторонньо асоціативний`() {
        assertEquals(0, BigDecimal("512").compareTo(value("2^3^2")))
    }

    @Test
    fun `відсоток ділить на сто`() {
        assertEquals(0, BigDecimal("0.5").compareTo(value("50%")))
        assertEquals(0, BigDecimal("20").compareTo(value("200*10%")))
    }

    @Test
    fun `унарний мінус`() {
        assertEquals(0, BigDecimal("-3").compareTo(value("-5+2")))
        assertEquals(0, BigDecimal("-6").compareTo(value("2*-3")))
    }

    @Test
    fun `українські символи операторів теж працюють`() {
        assertEquals(0, BigDecimal("12").compareTo(value("3×4")))
        assertEquals(0, BigDecimal("3").compareTo(value("12÷4")))
        assertEquals(0, BigDecimal("1").compareTo(value("5−4")))
    }

    @Test
    fun `неповний вираз не видає числа`() {
        assertEquals(null, CalculatorEngine.tryEvaluate("3+"))
    }

    @Test
    fun `ділення на нуль — помилка, а не виняток назовні`() {
        val crashed = runCatching { value("5/0") }
        assertTrue(crashed.exceptionOrNull() is CalculatorEngine.CalcException)
    }

    @Test
    fun `сміття в рядку — помилка`() {
        val crashed = runCatching { value("2++") }
        assertTrue(crashed.exceptionOrNull() is CalculatorEngine.CalcException)
    }

    @Test
    fun `точність не зникає там, де її не мають втрачати`() {
        assertEquals(0, BigDecimal("0.3").compareTo(value("0.1+0.2")))
    }
}
