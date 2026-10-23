package os.tessera.launcher.ui

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import os.tessera.launcher.layout.GridSpec

/** Актуальна геометрія головного екрана (профіль сітки). */
val LocalGridSpec = staticCompositionLocalOf { GridSpec.LARGE_PHONE }

/**
 * Палітра лаунчера — динамічна (Monet), тобто народжена з кольору шпалер.
 *
 * Це той самий механізм, що й на Pixel, тільки з відкритого API Compose
 * Material 3: нічого не копіюється, колір рахує система.
 */
@Composable
fun TesseraLauncherTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()

    val scheme = runCatching {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }.getOrElse {
        if (dark) darkColorScheme() else lightColorScheme()
    }

    // Лаунчер лежить поверх шпалер, тому піктограми системних панелів мусять
    // мати контраст: у світлій темі — темні, у темній — світлі.
    // Працюємо через WindowInsetsControllerCompat: `View.isLightStatusBar`
    // — прихований системний API, він не лінкується проти публічного android.jar.
    val view = LocalView.current
    SideEffect {
        val controller = view.context.findActivity()?.window
            ?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
    }

    CompositionLocalProvider(LocalGridSpec provides GridSpec.LARGE_PHONE) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Compose обгортує контекст у ContextWrapper — активність шукаємо в ланцюжку. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
