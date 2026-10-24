package os.tessera.messaging.logic

/** Алфавіт, у якому піде повідомлення. Від нього міститься ліміт частини. */
enum class SmsAlphabet { Gsm7, Ucs2 }

/**
 * Кошторис повідомлення: скільки одиниць займе текст і на скільки частин
 * розіб'ється.
 *
 * `capacity` — це місткість *саме такого* набору частин, тому `capacity - units`
 * чесно показує, скільки ще влізе, перш ніж знадобиться наступна частина.
 */
data class SmsBudget(
    val alphabet: SmsAlphabet,
    val units: Int,
    val parts: Int,
    val capacity: Int,
) {
    val remaining: Int get() = capacity - units
    val isConcatenated: Boolean get() = parts > 1
}

/**
 * Розбиття тексту на SMS-частини (3GPP 23.038).
 *
 * Лічильник «2 частини · 245/306» у полі введення — це те, за чим люди
 * вирізають текст, тому арифметика має бути правильна, а не «приблизна».
 *
 * Один свідомий компроміс: набір GSM-7 визначений консервативно. У базі лежить
 * лише друкована ASCII плюс сім символів розширеної таблиці, які коштують
 * двох септет; усе інше — кирилиця, латиниця з діакритикою, емодзі —
 * відноситься до UCS-2. Наслідок: для рідкісних текстів частин може бути
 * показано більше, ніж насправді візьме модем. Недооцінки не буває, а отже
 * користувач ніколи не втратить повідомлення мовчки.
 *
 * Останнє — причина, чому «ä» і «Ñ» не вважаються базовими: помилка вліво
 * коштувала б втраченим текстом, помилка вправо — лише зайвим рядом лічильника.
 */
object SmsParts {

    const val GSM7_SINGLE = 160
    const val GSM7_PART = 153
    const val UCS2_SINGLE = 70
    const val UCS2_PART = 67

    /**
     * Символи GSM-7, які коштують двох септет, бо лежать у розширеній таблиці
     * (shift-посилання). `€` — той самий випадок: один символ, два септети.
     */
    private const val GSM7_EXTENDED = "^{}[]~|\\\u20AC"

    /**
     * Базовий набір: друкована ASCII, окрім дев'яти символів, які в базі не
     * живуть (` ^ ~ | \ [ ] { }` і гравіс — останнього в GSM-7 немає взагалі).
     *
     * Літер з діакритикою (`ä`, `Ñ`, `Æ`) тут навмисно немає. Таблицю 23.038
     * важливо не перебільшувати: якщо зарахувати символ до бази, а модем його
     * там не знайде, повідомлення тихенько перепишеться в UCS-2 і частин
     * стане більше, ніж показував лічильник. Навпаки — коли сумнівний символ
     * іде в UCS-2, ми лише переоцінюємо кошторис, а користувач ніколи не
     * втрачає текст.
     */
    private val GSM7_BASIC: Set<Char> = buildSet {
        add(' ')
        addAll('!'..'~')
        removeAll(setOf('^', '~', '|', '\\', '[', ']', '{', '}', '`'))
    }

    fun alphabetOf(text: String): SmsAlphabet =
        if (text.all { it in GSM7_BASIC || it in GSM7_EXTENDED }) SmsAlphabet.Gsm7
        else SmsAlphabet.Ucs2

    /**
     * Одиниці виміру: септети для GSM-7, 16-бітні одиниці для UCS-2.
     *
     * Для UCS-2 це дослівно `length`: PDU несе 16-бітні слова UTF-16, тому
     * емодзі (сурогатна пара) справді коштує двох символів ліміту.
     */
    fun unitsOf(text: String, alphabet: SmsAlphabet = alphabetOf(text)): Int = when (alphabet) {
        SmsAlphabet.Gsm7 -> text.count { it in GSM7_EXTENDED } + text.length
        SmsAlphabet.Ucs2 -> text.length
    }

    fun budget(text: String): SmsBudget {
        if (text.isEmpty()) return SmsBudget(SmsAlphabet.Gsm7, 0, 0, GSM7_SINGLE)
        val alphabet = alphabetOf(text)
        val units = unitsOf(text, alphabet)
        val (single, perPart) = when (alphabet) {
            SmsAlphabet.Gsm7 -> GSM7_SINGLE to GSM7_PART
            SmsAlphabet.Ucs2 -> UCS2_SINGLE to UCS2_PART
        }
        val parts = if (units <= single) 1 else (units + perPart - 1) / perPart
        // У одиночної частини заголовок UDH не займає місця, тому її місткість —
        // повні 160/70, а не 153/67. Версія «у першої частини теж 153» давала
        // відʼємний залишок рівно на 160-му символі.
        return SmsBudget(alphabet, units, parts, if (parts == 1) single else parts * perPart)
    }
}
