package os.tessera.welcome

import android.content.Intent
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
 * Єдине вікно вітальні.
 *
 * Виклик `finish()` у [onCreate] — не декорація: якщо система стартує нас
 * повторно після проходження, показувати старі кроки ще раз немає сенсу.
 * Запуск із іконки в лаунчері навмисно цю перевірку пропускає: користувач сам
 * вибрав подивитися ще раз.
 */
class WelcomeActivity : ComponentActivity() {

    private val viewModel: WelcomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val fromLauncher = intent?.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
        if (viewModel.skipWhenAlreadyPassed(fromLauncher)) {
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
                    onOpenDisplaySettings = viewModel::onOpenDisplaySettings,
                )
                LaunchedEffect(state.finished) {
                    if (state.finished) finish()
                }
            }
        }
    }
}
