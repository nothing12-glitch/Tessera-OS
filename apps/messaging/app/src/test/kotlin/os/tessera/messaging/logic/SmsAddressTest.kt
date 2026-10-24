package os.tessera.messaging.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsAddressTest {

    @Test
    fun `ключ лишає цифри і провідний плюс`() {
        assertEquals("+380671234567", SmsAddress.key("+380 (067) 123-45-67"))
        assertEquals("0671234567", SmsAddress.key("067 123 45 67"))
    }

    @Test
    fun `національний і міжнародний запис одного номера — та сама нитка`() {
        assertTrue(SmsAddress.sameThread("+380671234567", "0671234567"))
        assertTrue(SmsAddress.sameThread("067 123 45 67", "(067) 123-45-67"))
    }

    @Test
    fun `різні номери не злипаються`() {
        assertFalse(SmsAddress.sameThread("0671234567", "0501234567"))
        assertFalse(SmsAddress.sameThread("100", "112"))
    }

    @Test
    fun `міжнародний номер групує з правого краю по три цифри`() {
        assertEquals("+380 671 234 567", SmsAddress.display("+380671234567"))
    }

    @Test
    fun `нотація з нулем у дужках — той самий номер, що й без нуля`() {
        // «+380 (067) …» так пишуть у контактах; мережа віддає канонічний запис.
        assertEquals("+380671234567", SmsAddress.key("+380 (067) 123-45-67"))
        assertEquals("+380 671 234 567", SmsAddress.display("+380 (067) 123-45-67"))
        assertTrue(SmsAddress.sameThread("+380 (067) 123-45-67", "0671234567"))
    }

    @Test
    fun `британський нуль-магістраль з двоцифровим кодом теж зникає`() {
        assertEquals("+442079460018", SmsAddress.key("+44 (020) 7946 0018"))
    }

    @Test
    fun `канонічний номер без дужок не спрощується`() {
        // «380» — код країни, а не нуль-магістраль: без дужок чіпати нічого.
        assertEquals("+380671234567", SmsAddress.key("+380671234567"))
        assertTrue(SmsAddress.sameThread("+380671234567", "+380 (067) 123-45-67"))
    }

    @Test
    fun `десятицифровий локальний номер групує як 067 123 45 67`() {
        assertEquals("067 123 45 67", SmsAddress.display("0671234567"))
    }

    @Test
    fun `короткий службовий номер лишається як є`() {
        assertEquals("112", SmsAddress.display("112"))
    }
}
