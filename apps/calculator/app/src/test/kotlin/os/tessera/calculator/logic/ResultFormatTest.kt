package os.tessera.calculator.logic

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultFormatTest {

    private fun f(raw: String) = ResultFormat.format(BigDecimal(raw))

    @Test
    fun `цифри группуються нерозривним пробілом`() {
        assertEquals("1\u00A0000", f("1000"))
        assertEquals("12\u00A0345\u00A0678", f("12345678"))
    }

    @Test
    fun `десяткова кома і зайві нулі зникають`() {
        assertEquals("2,5", f("2.50"))
        assertEquals("0,5", f("0.500"))
        assertEquals("3", f("3.000"))
    }

    @Test
    fun `від’ємне число зберігає мінус`() {
        assertEquals("-2,5", f("-2.5"))
    }

    @Test
    fun `дуже мале й дуже велике йдуть у науковий запис`() {
        assertTrue(f("0.000000000001").contains("E"))
        assertTrue(f("10000000000000000000000").contains("E"))
    }

    @Test
    fun `вираз на екрані показує математичні символи`() {
        assertEquals("3×4−5÷2", ResultFormat.prettyExpression("3*4-5/2"))
    }
}
