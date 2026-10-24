package os.tessera.dialer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.tessera.dialer.CallGroup
import os.tessera.dialer.CallRow
import os.tessera.dialer.DialUiState
import os.tessera.dialer.R

/**
 * Журнал викликів.
 *
 * Пошук набирається на тій самій клавіатурі, тому поле просить телефонний
 * розклад: цифри працюють і як частина номера, і як Т9 імені.
 */
@Composable
internal fun CallLogScreen(
    state: DialUiState,
    onQueryChange: (String) -> Unit,
    onCall: (String) -> Unit,
    onDelete: (CallRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = state.groups
    Column(modifier = modifier.fillMaxSize()) {
        SearchField(state.query, onQueryChange)
        when {
            !state.callLogReadable -> EmptyState(
                title = stringResource(R.string.call_log_locked_title),
                hint = stringResource(R.string.call_log_locked_hint),
                modifier = Modifier.padding(top = 48.dp),
            )

            groups.isEmpty() && state.query.isNotBlank() -> EmptyState(
                title = stringResource(R.string.no_results),
                hint = stringResource(R.string.no_results_hint),
                modifier = Modifier.padding(top = 48.dp),
            )

            groups.isEmpty() -> EmptyState(
                title = stringResource(R.string.no_calls),
                hint = stringResource(R.string.no_calls_hint),
                modifier = Modifier.padding(top = 48.dp),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                groups.forEach { group ->
                    item(key = "h-${group.label}") { GroupHeader(group) }
                    items(group.rows, key = { "c-${it.id}" }) { row ->
                        CallRowItem(
                            row = row,
                            onCall = { onCall(row.number) },
                            onDelete = { onDelete(row) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.search_calls)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_backspace),
                        contentDescription = stringResource(R.string.delete),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outlineVariant,
            focusedContainerColor = scheme.surfaceContainerLow,
            unfocusedContainerColor = scheme.surfaceContainerLow,
        ),
    )
}

@Composable
private fun GroupHeader(group: CallGroup) {
    Text(
        text = group.label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRowItem(row: CallRow, onCall: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = tintFor(row.direction)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onCall, onLongClick = onDelete)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (row.missed) scheme.errorContainer else scheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconFor(row.direction)),
                contentDescription = stringResource(directionLabel(row.direction)),
                tint = if (row.missed) scheme.onErrorContainer else accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (row.missed) scheme.error else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOf(row.timeText, row.durationText)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_call),
            contentDescription = stringResource(R.string.call),
            tint = scheme.primary,
        )
    }
}
