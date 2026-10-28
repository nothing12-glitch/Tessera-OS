package os.tessera.welcome.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import os.tessera.welcome.logic.AccentColor
import os.tessera.welcome.logic.ThemeChoice
import os.tessera.welcome.logic.onAccentArgb
import os.tessera.welcome.logic.readableOnDark

/**
 * Палітра вітального екрана.
 *
 * Динамічна (Monet) — як і решта наших застосунків, але сюди приходять два
 * вибори користувача з кроку «Вигляд»: тема (тільки вручну) і акцент.
 *
 * Важливо про чесність: усе, що тут змінюється, — це лиш це вікно. Системний
 * день/ніч звідси не перемикається: його рухає запис `ui_night_mode`, а не ця
 * функція, тому крок «Вигляд» показує результат саме того запису.
 */
@Composable
fun TesseraWelcomeTheme(
    choice: ThemeChoice,
    accent: AccentColor = AccentColor.DYNAMIC,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = choice.isDark

    val scheme = runCatching {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }.getOrElse {
        if (dark) {
            darkColorScheme(
                primary = Color(0xFFB7C4FF),
                onPrimary = Color(0xFF002478),
                primaryContainer = Color(0xFF1E2B4C),
                onPrimaryContainer = Color(0xFFDCE1FF),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF3350C8),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDCE1FF),
                onPrimaryContainer = Color(0xFF101C49),
            )
        }
    }.withSurfaces(dark)
        .let { if (accent.isDynamic) it else it.withAccent(accent.argb, dark) }

    val view = LocalView.current
    SideEffect {
        val controller = view.context.findActivity()?.window
            ?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
    }

    MaterialTheme(
        colorScheme = scheme,
        shapes = WelcomeShapes,
        typography = WelcomeTypography,
        content = content,
    )
}

private val WelcomeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val WelcomeTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
)

private fun ColorScheme.withSurfaces(dark: Boolean): ColorScheme = if (dark) {
    copy(
        background = Color(0xFF0B0F14),
        onBackground = Color(0xFFE0E3EA),
        surface = Color(0xFF0B0F14),
        onSurface = Color(0xFFE0E3EA),
        onSurfaceVariant = Color(0xFFA6ADBB),
        surfaceContainerLowest = Color(0xFF080B10),
        surfaceContainerLow = Color(0xFF131820),
        surfaceContainer = Color(0xFF171C25),
        surfaceContainerHigh = Color(0xFF202630),
        surfaceContainerHighest = Color(0xFF2A313D),
        outline = Color(0xFF747E90),
        outlineVariant = Color(0xFF303846),
    )
} else {
    copy(
        background = Color(0xFFF7F8FC),
        onBackground = Color(0xFF191C24),
        surface = Color(0xFFF7F8FC),
        onSurface = Color(0xFF191C24),
        onSurfaceVariant = Color(0xFF535D70),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF0F2F8),
        surfaceContainer = Color(0xFFECEEF5),
        surfaceContainerHigh = Color(0xFFE4E7F0),
        surfaceContainerHighest = Color(0xFFDDE1EB),
        outline = Color(0xFF737E92),
        outlineVariant = Color(0xFFCDD3DF),
    )
}

/**
 * Ручний акцент замість Monet: ролі `primary`/`secondary`/`tertiary` і текст на
 * них. Контейнери лишаємо динамічні — з одним лише пресетом тонів у нас
 * рахувати їх нічим, а вигаданий тон був би брехнею про палітру.
 */
private fun ColorScheme.withAccent(argb: Int, dark: Boolean): ColorScheme {
    val visible = if (dark) readableOnDark(argb) else argb
    val primary = Color(visible)
    val onPrimary = Color(onAccentArgb(visible))
    return copy(
        primary = primary,
        onPrimary = onPrimary,
        secondary = primary,
        onSecondary = onPrimary,
        tertiary = primary,
        onTertiary = onPrimary,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
