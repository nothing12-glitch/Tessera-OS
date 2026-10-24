package os.tessera.messaging.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsPartsTest {

    @Test
    fun `ascii в межах 160 септет іде однією частиною`() {
        val budget = SmsParts.budget("a".repeat(160))
        assertEquals(SmsAlphabet.Gsm7, budget.alphabet)
        assertEquals(160, budget.units)
        assertEquals(1, budget.parts)
        assertEquals(0, budget.remaining)
    }

    @Test
    fun `161-й символ потребує другої частини і місткість стає 306`() {
        val budget = SmsParts.budget("a".repeat(161))
        assertEquals(2, budget.parts)
        assertEquals(306, budget.capacity)
        assertEquals(145, budget.remaining)
    }

    @Test
    fun `символи розширеної таблиці коштують двох септетів`() {
        // 80 «€» — це 160 септет: рівно одна частина, а не 80.
        assertEquals(160, SmsParts.unitsOf("€".repeat(80), SmsAlphabet.Gsm7))
        val budget = SmsParts.budget("€".repeat(81))
        assertEquals(SmsAlphabet.Gsm7, budget.alphabet)
        assertEquals(2, budget.parts)
    }

    @Test
    fun `гравіс не вміщується в gsm-7 взагалі`() {
        assertEquals(SmsAlphabet.Ucs2, SmsParts.alphabetOf("`"))
    }

    @Test
    fun `латиниця з діакритикою свідомо йде в ucs-2`() {
        // Символи базової таблиці 23.038 важливо не перебільшувати:
        // переоцінити кошторис — безпечно, недооцінити — втрачений текст.
        assertEquals(SmsAlphabet.Ucs2, SmsParts.alphabetOf("ä"))
        assertEquals(SmsAlphabet.Ucs2, SmsParts.alphabetOf("Ñ"))
    }

    @Test
    fun `кирилиця йде в ucs-2 з лімітами 70 і 67`() {
        val single = SmsParts.budget("привіт".repeat(11))
        assertEquals(SmsAlphabet.Ucs2, single.alphabet)
        assertEquals(66, single.units)
        assertEquals(1, single.parts)
        assertEquals(4, single.remaining)

        val twice = SmsParts.budget("привіт".repeat(12))
        assertEquals(72, twice.units)
        assertEquals(2, twice.parts)
        assertEquals(134, twice.capacity)
    }

    @Test
    fun `емодзі поза базовою площиною рахується двома одиницями`() {
        assertEquals(2, SmsParts.unitsOf("\uD83D\uDE00", SmsAlphabet.Ucs2))
    }

    @Test
    fun `порожнє повідомлення не має частин`() {
        val budget = SmsParts.budget("")
        assertEquals(0, budget.units)
        assertEquals(0, budget.parts)
        assertEquals(false, budget.isConcatenated)
    }
}
