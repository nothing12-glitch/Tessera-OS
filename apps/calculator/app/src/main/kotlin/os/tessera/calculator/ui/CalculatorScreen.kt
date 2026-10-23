package os.tessera.calculator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import os.tessera.calculator.CalcUiState
import os.tessera.calculator.R

/** Тон клавіші за Material 3: цифри — тонувальна поверхня, оператори — secondaryContainer, «=» — primary. */
private enum class Tone { Digit, Operator, Utility, Equals }

private class Key(
    val label: String,
    val tone: Tone,
    val onClick: () -> Unit,
)

@Composable
fun CalculatorScreen(
    state: CalcUiState,
    onDigit: (Char) -> Unit,
    onDot: () -> Unit,
    onOperator: (Char) -> Unit,
    onPercent: () -> Unit,
    onToggleParen: () -> Unit,
    onEquals: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf(
            Key("AC", Tone.Utility, onClear),
            Key("( )", Tone.Utility, onToggleParen),
            Key("%", Tone.Utility, onPercent),
            Key("⌫", Tone.Utility, onBackspace),
        ),
        listOf(
            Key("7", Tone.Digit) { onDigit('7') },
            Key("8", Tone.Digit) { onDigit('8') },
            Key("9", Tone.Digit) { onDigit('9') },
            Key("÷", Tone.Operator) { onOperator('/') },
        ),
        listOf(
            Key("4", Tone.Digit) { onDigit('4') },
            Key("5", Tone.Digit) { onDigit('5') },
            Key("6", Tone.Digit) { onDigit('6') },
            Key("×", Tone.Operator) { onOperator('*') },
        ),
        listOf(
            Key("1", Tone.Digit) { onDigit('1') },
            Key("2", Tone.Digit) { onDigit('2') },
            Key("3", Tone.Digit) { onDigit('3') },
            Key("−", Tone.Operator) { onOperator('-') },
        ),
        listOf(
            Key("0", Tone.Digit) { onDigit('0') },
            Key(".", Tone.Digit) { onDot() },
            Key("+", Tone.Operator) { onOperator('+') },
            Key("=", Tone.Equals, onEquals),
        ),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Display(state)
        Spacer(Modifier.height(20.dp))
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { key ->
                    KeyCell(key = key, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Display(state: CalcUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = state.expression.ifEmpty { " " },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (state.showError) stringResource(R.string.error_bad_expression) else state.output,
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light),
            color = if (state.showError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun KeyCell(key: Key, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer) = when (key.tone) {
        Tone.Digit -> scheme.surfaceContainerHighest to scheme.onSurface
        Tone.Operator -> scheme.secondaryContainer to scheme.onSecondaryContainer
        Tone.Utility -> scheme.surfaceContainerLow to scheme.onSurfaceVariant
        Tone.Equals -> scheme.primary to scheme.onPrimary
    }
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(onClick = key.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.label,
            style = MaterialTheme.typography.headlineSmall,
            color = onContainer,
        )
    }
}
