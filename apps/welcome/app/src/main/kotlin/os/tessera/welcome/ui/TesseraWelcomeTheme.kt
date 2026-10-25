package os.tessera.welcome.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import os.tessera.welcome.logic.ThemeChoice
import os.tessera.welcome.logic.isDark

/**
 * Палітра вітального екрана.
 *
 * Динамічна (Monet) — як і решта наших застосунків, але з однією різницею: сюди
 * приходить вибір користувача з кроку «Тема». `AUTO` не вигадує правила —
 * віддає те, що каже система.
 *
 * Важливо про чесність: `isDark` тут керує лиш цим вікном. Системний день/ніч
 * звідси не перемикається — для цього немає перевіреного публічного виклику
 * (див. README застосунку), тому крок «Тема» показує попередження й веде в
 * Налаштування → Дисплей.
 */
@Composable
fun TesseraWelcomeTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = choice.isDark(systemDark)

    val scheme = runCatching {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }.getOrElse {
        if (dark) darkColorScheme() else lightColorScheme()
    }

    val view = LocalView.current
    SideEffect {
        val controller = view.context.findActivity()?.window
            ?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
    }

    MaterialTheme(colorScheme = scheme, content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
