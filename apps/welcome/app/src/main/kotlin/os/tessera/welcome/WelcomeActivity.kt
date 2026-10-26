package os.tessera.welcome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.tessera.welcome.ui.TesseraWelcomeTheme
import os.tessera.welcome.ui.WelcomeActions
import os.tessera.welcome.ui.WelcomeScreen

/**
 * Єдине вікно майстра першого запуску.
 *
 * Виклик `finish()` у [onCreate] — не декорація: система кликає нас на кожен
 * старт, поки образ не налаштовано. Як тільки `device_provisioned` піднято,
 * показувати кроки ще раз немає сенсу, і ми звільняємо екран для лаунчера.
 */
class WelcomeActivity : ComponentActivity() {

    private val viewModel: WelcomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (viewModel.skipWhenAlreadyPassed()) {
            finish()
            return
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val actions = remember(viewModel) {
                WelcomeActions(
                    onNext = viewModel::onNext,
                    onBack = viewModel::onBack,
                    onSkip = viewModel::onSkip,
                    onTheme = viewModel::onTheme,
                    onAccent = viewModel::onAccent,
                    onLanguage = viewModel::onLanguage,
                    onRegion = viewModel::onRegion,
                    onFontScale = viewModel::onFontScale,
                    onHighContrast = viewModel::onHighContrast,
                    onNavigation = viewModel::onNavigation,
                    onPower = viewModel::onPower,
                    onMacPolicy = viewModel::onMacPolicy,
                    onOpenAccount = viewModel::onOpenAccount,
                    onOpenScreen = viewModel::onOpenScreen,
                )
            }
            TesseraWelcomeTheme(choice = state.flow.theme, accent = state.flow.accent) {
                WelcomeScreen(state = state, actions = actions)
                LaunchedEffect(state.finished) {
                    if (state.finished) finish()
                }
            }
        }
    }
}
