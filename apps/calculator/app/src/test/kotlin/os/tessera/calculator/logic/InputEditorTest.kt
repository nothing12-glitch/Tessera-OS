package os.tessera.calculator.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class InputEditorTest {

    @Test
    fun `останній оператор замінюється, а не додається`() {
        assertEquals("3*", InputEditor.pressOperator("3+", '*'))
        assertEquals("3-", InputEditor.pressOperator("3*", '-'))
    }

    @Test
    fun `оператор на порожньому рядку допускає лише мінус`() {
        assertEquals("-", InputEditor.pressOperator("", '-'))
        assertEquals("", InputEditor.pressOperator("", '*'))
    }

    @Test
    fun `оператор не можна поставити після дужки чи відсотка`() {
        assertEquals("(", InputEditor.pressOperator("(", '+'))
        assertEquals("50%", InputEditor.pressOperator("50%", '+'))
    }

    @Test
    fun `крапка всередині числа один раз і з нулем на порожньому рядку`() {
        assertEquals("3.5", InputEditor.pressDot("3.5"))
        assertEquals("0.", InputEditor.pressDot(""))
        assertEquals("3+0.", InputEditor.pressDot("3+"))
        assertEquals("3.14", InputEditor.pressDot("3.14"))
    }

    @Test
    fun `відкрита дужка після числа множиться`() {
        assertEquals("3*(", InputEditor.pressOpenParen("3"))
        assertEquals("(", InputEditor.pressOpenParen(""))
        assertEquals("3+(", InputEditor.pressOpenParen("3+"))
    }

    @Test
    fun `закрита дужка лише коли є відкрита і рядок закінчується числом`() {
        assertEquals("(3)", InputEditor.pressCloseParen("(3"))
        assertEquals("(3+", InputEditor.pressCloseParen("(3+"))
        assertEquals("3", InputEditor.pressCloseParen("3"))
    }

    @Test
    fun `дорівнює добиває відкриті дужки`() {
        assertEquals("(3+4)", InputEditor.complete("(3+4"))
        assertEquals("((2+1)*3)", InputEditor.complete("((2+1)*3"))
        assertEquals("(3+4)", InputEditor.complete("(3+4)"))
    }

    @Test
    fun `стертя прибирає один символ`() {
        assertEquals("3+", InputEditor.backspace("3+2"))
        assertEquals("", InputEditor.backspace(""))
    }

    @Test
    fun `відсоток тільки після значення`() {
        assertEquals("50%", InputEditor.pressPercent("50"))
        assertEquals("(", InputEditor.pressPercent("("))
    }
}
