package os.tessera.clock.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import os.tessera.clock.AlarmDraft
import os.tessera.clock.AlarmRow
import os.tessera.clock.ClockUiState
import os.tessera.clock.ClockViewModel
import os.tessera.clock.R
import os.tessera.clock.logic.ClockFormat

@Composable
fun AlarmsScreen(
    state: ClockUiState,
    viewModel: ClockViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.showNoExactWarning) {
            WarningBanner(
                text = stringResource(R.string.exact_alarm_warning),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = viewModel::openExactAlarmSettings,
                onDismiss = viewModel::acknowledgeExactWarning,
            )
        }
        if (!state.notificationsGranted) {
            InfoBanner(stringResource(R.string.notifications_needed))
        }

        val draft = state.draft
        if (draft != null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AlarmEditor(draft, viewModel)
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AlarmList(state.alarms, viewModel)
            }
        }
    }
}

@Composable
private fun AlarmList(alarms: List<AlarmRow>, viewModel: ClockViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (alarms.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.alarms_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(alarms, key = { it.id }) { row ->
                    AlarmListRow(row, viewModel)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            ExtendedFloatingActionButton(
                onClick = viewModel::startNewAlarm,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.alarm_new)) },
            )
        }
    }
}

@Composable
private fun AlarmListRow(row: AlarmRow, viewModel: ClockViewModel) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerLow)
            .clickable { viewModel.editAlarm(row) }
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.timeText,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                color = if (row.enabled) scheme.onSurface else scheme.onSurfaceVariant,
            )
            if (row.label.isNotBlank()) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
            }
            Text(
                text = stringResource(if (row.repeatDaily) R.string.alarm_repeat else R.string.alarm_once),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = nextLine(row),
                style = MaterialTheme.typography.bodySmall,
                color = if (row.enabled) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = row.enabled,
            onCheckedChange = { viewModel.toggleAlarm(row.id, it) },
        )
        IconButton(onClick = { viewModel.deleteAlarm(row.id) }) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.alarm_delete),
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun nextLine(row: AlarmRow): String = when {
    !row.enabled -> stringResource(R.string.alarm_off)
    row.nextDeltaMs != null -> stringResource(R.string.alarm_next, ClockFormat.until(row.nextDeltaMs))
    else -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(draft: AlarmDraft, viewModel: ClockViewModel) {
    val picker = rememberTimePickerState(
        initialHour = draft.hour,
        initialMinute = draft.minute,
        is24Hour = true,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(
                    state = picker,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = draft.label,
            onValueChange = viewModel::updateDraftLabel,
            label = { Text(stringResource(R.string.alarm_label_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.alarm_repeat),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = draft.repeatDaily,
                onCheckedChange = viewModel::updateDraftRepeat,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = viewModel::cancelDraft) {
                Text(stringResource(R.string.alarm_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { viewModel.saveDraft(picker.hour, picker.minute) }) {
                Text(stringResource(R.string.alarm_save))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WarningBanner(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.errorContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction) {
            Text(actionLabel, color = scheme.onErrorContainer)
        }
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.alarm_cancel), color = scheme.onErrorContainer)
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
