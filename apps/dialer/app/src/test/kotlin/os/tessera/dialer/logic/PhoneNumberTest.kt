package os.tessera.dialer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberTest {

    @Test
    fun `нормалізація лишатиме лише цифри, плюс і паузи`() {
        assertEquals("+380671234567", PhoneNumber.normalize("+380 (067) 123-45-67"))
        assertEquals("+380671234567", PhoneNumber.normalize("+380 (0) 67 123-45-67"))
        assertEquals("0671234567;1234", PhoneNumber.normalize("067 123-45-67;1234"))
    }

    @Test
    fun `національний запис не втрачає нуль префіксу`() {
        // «(0)» відкидаємо лише біля міжнародного «+»: без нього 0 — частина номера
        assertEquals("0671234567", PhoneNumber.normalize("(067) 123-45-67"))
        assertEquals("0", PhoneNumber.normalize("(0)"))
    }

    @Test
    fun `локальний номер групує як 067 123 45 67`() {
        assertEquals("067 123 45 67", PhoneNumber.display("0671234567"))
    }

    @Test
    fun `міжнародний номер групує з правого краю по три цифри`() {
        assertEquals("+380 671 234 567", PhoneNumber.display("+380671234567"))
    }

    @Test
    fun `короткі службові номери лишаються без групп`() {
        assertEquals("100", PhoneNumber.display("100"))
        assertEquals("112", PhoneNumber.display("112"))
        assertEquals("1234", PhoneNumber.display("1234"))
    }

    @Test
    fun `пауза розбиває номер на окремі групи`() {
        assertEquals("067 123 45 67;1234", PhoneNumber.display("0671234567;1234"))
    }

    @Test
    fun `останні цифри для звірення з контактом`() {
        assertEquals("1234567", PhoneNumber.lastDigits("+380671234567"))
        assertEquals("12", PhoneNumber.lastDigits("12", 7))
    }

    @Test
    fun `т9 перетворює літери імені на цифри клавіатури`() {
        assertEquals("8377372", PhoneNumber.t9("Tessera"))
        assertEquals("2", PhoneNumber.t9("A"))
        assertEquals("", PhoneNumber.t9("123"))
    }

    @Test
    fun `т9 пошук знаходить контакт за набором цифр`() {
        assertTrue(PhoneNumber.matchesT9("Tessera", "8377"))
        assertTrue(PhoneNumber.matchesT9("Tessera", "8377372"))
        assertFalse(PhoneNumber.matchesT9("Tessera", "9999"))
        // літер кирилиці на клавіатурі набору немає: такий запит не може
        // збігатися з цифровим рядом імені, інакше фільтр віддавав би все
        assertFalse(PhoneNumber.matchesT9("Tessera", "Тет"))
    }

    @Test
    fun `придатність до дзвінка вирішує одна цифра`() {
        assertTrue(PhoneNumber.isCallable("+380"))
        assertFalse(PhoneNumber.isCallable(""))
        assertFalse(PhoneNumber.isCallable("*#"))
    }
}
