package os.tessera.welcome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.tessera.welcome.ui.TesseraWelcomeTheme
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (viewModel.skipWhenAlreadyPassed()) {
            finish()
            return
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            TesseraWelcomeTheme(choice = state.flow.theme) {
                WelcomeScreen(
                    state = state,
                    onTheme = viewModel::onTheme,
                    onNext = viewModel::onNext,
                    onBack = viewModel::onBack,
                    onSkipAccount = viewModel::onSkipAccount,
                    onOpenAccount = viewModel::onOpenAccount,
                )
                LaunchedEffect(state.finished) {
                    if (state.finished) finish()
                }
            }
        }
    }
}
