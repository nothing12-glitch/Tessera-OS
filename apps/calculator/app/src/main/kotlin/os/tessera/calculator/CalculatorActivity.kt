package os.tessera.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.tessera.calculator.ui.CalculatorScreen
import os.tessera.calculator.ui.TesseraCalculatorTheme

class CalculatorActivity : ComponentActivity() {

    private val viewModel: CalculatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TesseraCalculatorTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                CalculatorScreen(
                    state = state,
                    onDigit = viewModel::digit,
                    onDot = viewModel::dot,
                    onOperator = viewModel::operator,
                    onPercent = viewModel::percent,
                    onToggleParen = viewModel::toggleParen,
                    onEquals = viewModel::equals,
                    onBackspace = viewModel::backspace,
                    onClear = viewModel::clear,
                )
            }
        }
    }
}
