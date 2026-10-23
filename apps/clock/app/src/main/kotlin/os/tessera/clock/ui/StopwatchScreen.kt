package os.tessera.clock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import os.tessera.clock.ClockUiState
import os.tessera.clock.ClockViewModel
import os.tessera.clock.LapRow
import os.tessera.clock.R

@Composable
fun StopwatchScreen(
    state: ClockUiState,
    viewModel: ClockViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(24.dp))
        val lapsCaption = if (state.laps.isNotEmpty()) {
            stringResource(R.string.laps_count, state.laps.size)
        } else {
            null
        }
        BigTime(text = state.stopwatchText, caption = lapsCaption)
        Spacer(Modifier.height(16.dp))

        if (state.laps.isEmpty()) {
            Box(Modifier.weight(1f))
        } else {
            // найновіше коло — угорі
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                items(state.laps.reversed(), key = { it.index }) { lap ->
                    LapLine(lap)
                }
            }
        }

        ActionButtonRow {
            if (state.stopwatchRunning) {
                ActionButton(stringResource(R.string.action_lap), viewModel::stopwatchLap, Tone.Tonal)
                ActionButton(stringResource(R.string.action_stop), viewModel::stopwatchStop, Tone.Primary)
            } else {
                val canReset = state.stopwatchText != "00:00.0" || state.laps.isNotEmpty()
                ActionButton(
                    stringResource(R.string.action_reset),
                    viewModel::stopwatchReset,
                    Tone.Neutral,
                    enabled = canReset,
                )
                ActionButton(stringResource(R.string.action_start), viewModel::stopwatchStart, Tone.Primary)
            }
        }
    }
}

@Composable
private fun LapLine(lap: LapRow) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#${lap.index}",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = lap.splitText,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurface,
            )
            Text(
                text = stringResource(R.string.lap_total, lap.totalText),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
