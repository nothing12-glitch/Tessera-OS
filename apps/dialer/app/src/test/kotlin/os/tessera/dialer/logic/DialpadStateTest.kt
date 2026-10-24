package os.tessera.dialer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class DialpadStateTest {

    @Test
    fun `набирання додає символи по одному`() {
        val s = DialpadState().append('0').append('6').append('7')
        assertEquals("067", s.text)
        assertEquals("067", s.digitsOnly)
    }

    @Test
    fun `плюс приймається лише на порожньому полі`() {
        assertEquals("+", DialpadState().append('+').text)
        assertEquals("+1", DialpadState("+").append('1').append('+').text)
    }

    @Test
    fun `«*», «#» та паузи допустимі`() {
        val s = DialpadState().append('*').append('#').append(';')
        assertEquals("*#;", s.text)
        assertFalse(s.canCall)
    }

    @Test
    fun `літери та пробіли клавіатура не надає і вони ігноруються`() {
        assertEquals("", DialpadState().append('a').append(' ').append('-').text)
    }

    @Test
    fun `backspace знімає останній символ і не чіпає порожнє поле`() {
        assertEquals("06", DialpadState("067").backspace().text)
        assertEquals("", DialpadState("").backspace().text)
    }

    @Test
    fun `максимальна довжина зупиняє введення`() {
        val long = DialpadState("1".repeat(DialpadState.MAX_LENGTH))
        assertEquals(DialpadState.MAX_LENGTH, long.append('2').text.length)
    }

    @Test
    fun `дзвінок можливий лише з хоча б одною цифрою`() {
        assertFalse(DialpadState("").canCall)
        assertFalse(DialpadState("*").canCall)
        assertTrue(DialpadState("1").canCall)
        assertTrue(DialpadState("+380671234567").canCall)
    }

    @Test
    fun `показ форми рахує PhoneNumber`() {
        assertEquals("067 123 45 67", DialpadState("0671234567").formatted())
    }

    @Test
    fun `мапа літер відповідає iso-клавіатурі`() {
        assertEquals("ABC", DialpadKeys.lettersFor("2"))
        assertEquals("PQRS", DialpadKeys.lettersFor("7"))
        assertEquals("WXYZ", DialpadKeys.lettersFor("9"))
        assertEquals("", DialpadKeys.lettersFor("1"))
        assertEquals("", DialpadKeys.lettersFor("0"))
        assertEquals('9', DialpadKeys.digitForLetter('W'))
        assertEquals('9', DialpadKeys.digitForLetter('w'))
        assertNull(DialpadKeys.digitForLetter('1'))
    }
}
