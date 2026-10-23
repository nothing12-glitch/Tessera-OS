package os.tessera.clock.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.tessera.clock.ClockUiState
import os.tessera.clock.ClockViewModel
import os.tessera.clock.R

@Composable
fun TimerScreen(
    state: ClockUiState,
    viewModel: ClockViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(48.dp))
        val caption = when {
            state.timerFinished -> stringResource(R.string.timer_done)
            !state.timerRunning && !state.timerHasTime -> stringResource(R.string.timer_idle)
            else -> null
        }
        BigTime(text = state.timerText, emphasize = state.timerFinished, caption = caption)
        Spacer(Modifier.height(32.dp))

        // коригування тривалості: додавання під час роботи продовжує відлік
        ActionButtonRow {
            ActionButton(stringResource(R.string.action_sub_minute), viewModel::timerSubMinute, Tone.Neutral)
            ActionButton(stringResource(R.string.action_add_minute), viewModel::timerAddMinute, Tone.Tonal)
        }
        ActionButtonRow {
            ActionButton(stringResource(R.string.action_sub_hour), viewModel::timerSubHour, Tone.Neutral)
            ActionButton(stringResource(R.string.action_add_hour), viewModel::timerAddHour, Tone.Tonal)
        }

        Spacer(Modifier.height(16.dp))
        ActionButtonRow {
            val canReset = state.timerHasTime || state.timerRunning || state.timerFinished
            ActionButton(
                stringResource(R.string.action_reset),
                viewModel::timerReset,
                Tone.Neutral,
                enabled = canReset,
            )
            if (state.timerRunning) {
                ActionButton(stringResource(R.string.action_stop), viewModel::timerPause, Tone.Primary)
            } else {
                ActionButton(
                    stringResource(R.string.action_start),
                    viewModel::timerStart,
                    Tone.Primary,
                    enabled = state.timerHasTime,
                )
            }
        }
    }
}
