package os.tessera.welcome.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
        if (dark) darkColorScheme() else lightColorScheme()
    }.let { if (accent.isDynamic) it else it.withAccent(accent.argb, dark) }

    val view = LocalView.current
    SideEffect {
        val controller = view.context.findActivity()?.window
            ?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
    }

    MaterialTheme(colorScheme = scheme, content = content)
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
