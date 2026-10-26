package os.tessera.welcome.logic

import kotlin.math.abs
import kotlin.math.pow

/**
 * Усі вибори користувача у одному файлі й без `android.*`. Зовнішні значення
 * (`UiModeManager`, рядки `Settings`, назви екранів) підставляє Android-шар —
 * тут лишаються лише імена та мапічі, які перевіряються тестом на JVM.
 */

/** Тема лише вручну: автоперемикання скасовано, тому варіанта «Авто» немає. */
enum class ThemeChoice {
    LIGHT,
    DARK,
    ;

    val isDark: Boolean get() = this == DARK

    /** Константи `UiModeManager` дає Android-шар — логіка їх не знає. */
    fun nightMode(light: Int, dark: Int): Int = if (this == LIGHT) light else dark

    companion object {
        fun byName(name: String?): ThemeChoice =
            entries.firstOrNull { it.name == name } ?: LIGHT
    }
}

/**
 * Акцентний колір. Основи — тільки маркові сиди з `brand/PALETTE.md`, бо
 * кольору «на око» в коді застосунків не існує. `DYNAMIC` лишає Monet, який
 * система рахує від шпалери.
 */
enum class AccentColor(val argb: Int) {
    DYNAMIC(0),
    INDIGO(0xFF3350C8.toInt()),
    SKY(0xFF8FB0FF.toInt()),
    VERDIGRIS(0xFF1F6E5C.toInt()),
    AMBER(0xFFE8B23A.toInt()),
    EMBER(0xFFB1452E.toInt()),
    NIGHT(0xFF0B0E14.toInt()),
    ;

    /** Monet не має власного ARGB: нуль читається як «колір дає система». */
    val isDynamic: Boolean get() = argb == 0

    companion object {
        fun byName(name: String?): AccentColor =
            entries.firstOrNull { it.name == name } ?: DYNAMIC
    }
}

/** Фарба тексту на акценті: темне «тло» чи світле «молоко» — за яскравістю. */
fun onAccentArgb(argb: Int): Int = if (relativeLuminance(argb) > LUMINANCE_SPLIT) DARK_INK else LIGHT_INK

val DARK_INK: Int = 0xFF0B0E14.toInt()
val LIGHT_INK: Int = 0xFFC8D8FF.toInt()

/** Нижче цього порогу акцент вважається темним. */
const val LUMINANCE_SPLIT: Float = 0.35f

/** Частка, до якої світла змішуємо акцент на темному тлі — інакше індиго зникає. */
const val DARK_LIFT: Float = 0.45f

/**
 * Лінійне змішування двох ARGB. Воно тут замість «Material-розрахунку» tones:
 * своєї бібліотеки кольорів у нас немає, а мікс каналів — єдина операція,
 * результат якої можна передбачити тестом.
 */
fun mixArgb(from: Int, to: Int, weight: Float): Int {
    val w = weight.coerceIn(0f, 1f)
    val a = channel(from, 24) + ((channel(to, 24) - channel(from, 24)) * w).toInt()
    val r = channel(from, 16) + ((channel(to, 16) - channel(from, 16)) * w).toInt()
    val g = channel(from, 8) + ((channel(to, 8) - channel(from, 8)) * w).toInt()
    val b = channel(from, 0) + ((channel(to, 0) - channel(from, 0)) * w).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/** Акцент, який видно на темному тлі: підсвічений білим «молоком» бренда. */
fun readableOnDark(argb: Int): Int = mixArgb(argb, LIGHT_INK, DARK_LIFT)

private fun channel(argb: Int, shift: Int): Int = (argb shr shift) and 0xFF

/** Відносна яскравість за Rec. 709 на лінеаризованих sRGB-каналах. */
fun relativeLuminance(argb: Int): Float {
    val r = srgbToLinear((argb shr 16) and 0xFF)
    val g = srgbToLinear((argb shr 8) and 0xFF)
    val b = srgbToLinear(argb and 0xFF)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

private fun srgbToLinear(channel: Int): Float {
    val s = channel / 255f
    return if (s <= 0.04045f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f)
}

/**
 * Короткий список мов для кроку. Повний перелік дає лише системний екран
 * мови: саму мову цей модуль змінити не може.
 */
enum class LanguageChoice(val tag: String) {
    UKRAINIAN("uk"),
    ENGLISH("en"),
    POLISH("pl"),
    GERMAN("de"),
    FRENCH("fr"),
    SPANISH("es"),
    ;

    companion object {
        fun byTag(tag: String?): LanguageChoice =
            entries.firstOrNull { it.tag == tag } ?: UKRAINIAN
    }
}

/** Регіон із порожнім значенням: мова без регіону — чесний стан, а не помилка. */
enum class RegionChoice(val code: String) {
    NONE(""),
    UKRAINE("UA"),
    UNITED_STATES("US"),
    UNITED_KINGDOM("GB"),
    POLAND("PL"),
    GERMANY("DE"),
    FRANCE("FR"),
    SPAIN("ES"),
    ;

    companion object {
        fun byCode(code: String?): RegionChoice =
            entries.firstOrNull { it.code == code } ?: UKRAINE
    }
}

/** Те, що збережеться для системного екрана мови: `uk`, `uk-UA`, `en-GB`… */
fun localeTag(language: LanguageChoice, region: RegionChoice): String =
    if (region.code.isEmpty()) language.tag else "${language.tag}-${region.code}"

/** Системний масштаб шрифта — значення рядка `font_scale`, а не власна уява. */
enum class FontScaleChoice(val scale: Float) {
    NORMAL(1.0f),
    LARGE(1.2f),
    LARGEST(1.4f),
    ;

    companion object {
        fun byName(name: String?): FontScaleChoice =
            entries.firstOrNull { it.name == name } ?: NORMAL

        /** Прочитаний із системи масштаб приводиться до найближчого варіанта. */
        fun nearest(scale: Float): FontScaleChoice =
            entries.minByOrNull { abs(it.scale - scale) } ?: NORMAL
    }
}

enum class NavigationChoice {
    GESTURES,
    BUTTONS,
    ;

    companion object {
        fun byName(name: String?): NavigationChoice =
            entries.firstOrNull { it.name == name } ?: GESTURES
    }
}

enum class PowerProfileChoice {
    ECONOMY,
    BALANCED,
    PERFORMANCE,
    ;

    companion object {
        fun byName(name: String?): PowerProfileChoice =
            entries.firstOrNull { it.name == name } ?: BALANCED
    }
}

/**
 * Політика MAC-адреси Wi-Fi. `RANDOM` — щойно з коробки, `STABLE` — коли
 * мережа прив'язує пристрій до адреси.
 */
enum class MacPolicyChoice {
    RANDOM,
    STABLE,
    ;

    companion object {
        fun byName(name: String?): MacPolicyChoice =
            entries.firstOrNull { it.name == name } ?: RANDOM
    }
}

/** Який системний екран просимо відкрити; рядки інтентів знає Android-шар. */
enum class SystemScreen {
    LOCALE,
    ACCESSIBILITY,
    NETWORK,
    SETTINGS,
}

/**
 * Куди веде кнопка «Додати акаунт».
 *
 * `com.google.android.gms` означає лише те, що якийсь постачальник відкриває
 * GmsCore-API — microG користується тим самим іменем пакета. За цим фактом ми
 * не розрізняємо microG і справжній GMS і нічого про це не заявляємо.
 */
enum class AccountRoute {
    SETTINGS,
    UNAVAILABLE
}

/** Маршрут кроку з обліковим записом. `gmsCore` — пакет постачальника API. */
fun accountRoute(installedPackages: List<String>, gmsCore: String): AccountRoute =
    if (installedPackages.any { it == gmsCore }) AccountRoute.SETTINGS else AccountRoute.UNAVAILABLE
