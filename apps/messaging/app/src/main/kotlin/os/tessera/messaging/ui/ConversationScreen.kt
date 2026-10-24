package os.tessera.messaging.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import os.tessera.messaging.BubbleGroup
import os.tessera.messaging.BubbleRow
import os.tessera.messaging.DaySection
import os.tessera.messaging.R
import os.tessera.messaging.logic.SmsBudget

/**
 * Розмова: бульбашки, розкладені по днях.
 *
 * Радіус бульбашки — 20 dp, але кут з боку «хвоста» залишаємо гострішим (4 dp)
 * і лише в останньої бульбашки групи. Це те, чим група відрізняється від просто
 * стовпчика однакових бульбашок: Material 3 радить показувати належність до
 * групи формою, а не відстанню.
 *
 * Час стоїть під останньою бульбашкою групи, а не під кожною: шість швидких
 * відповідей з шістьма «12:03» читалися б як таблиця.
 */
@Composable
internal fun ConversationScreen(
    sections: List<DaySection>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(sections.size) {
        // відкриваємо свіжу сторінку розмови, а не її початок
        if (sections.isNotEmpty()) listState.animateScrollToItem(sections.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        sections.forEach { section ->
            item(key = "day-${section.label}") { DayHeader(section.label) }
            items(section.groups, key = { "g-${it.bubbles.first().id}" }) { group ->
                BubbleGroupRow(group)
            }
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BubbleGroupRow(group: BubbleGroup) {
    val mine = group.bubbles.first().mine
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        group.bubbles.forEachIndexed { index, bubble ->
            val last = index == group.bubbles.lastIndex
            Bubble(
                row = bubble,
                tail = last,
                timeLabel = if (last) bubble.timeLabel else "",
            )
        }
    }
}

@Composable
private fun Bubble(row: BubbleRow, tail: Boolean, timeLabel: String) {
    val shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (row.mine || !tail) 20.dp else 4.dp,
        bottomEnd = if (!row.mine || !tail) 20.dp else 4.dp,
    )
    val container = when {
        row.failed -> MaterialTheme.colorScheme.errorContainer
        row.mine -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val onContainer = when {
        row.failed -> MaterialTheme.colorScheme.onErrorContainer
        row.mine -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (row.mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(container)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(text = row.text, style = MaterialTheme.typography.bodyLarge, color = onContainer)
            val caption = if (row.pending) {
                stringResource(R.string.status_pending)
            } else if (row.failed) {
                stringResource(R.string.status_failed)
            } else {
                timeLabel
            }
            if (caption.isNotEmpty()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer.copy(alpha = 0.75f),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp),
                )
            }
        }
    }
}

/**
 * Поле введення з лічильником частин.
 *
 * Кошторис (`2 · 245/306`) показуємо лише коли частин більше за одну: одним
 * повідомленням користувач не ризикує, а цифри в полі лише заважали б.
 */
@Composable
internal fun Composer(
    value: String,
    budget: SmsBudget,
    canSend: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(max = 140.dp),
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text(stringResource(R.string.composer_hint)) },
            supportingText = {
                if (budget.isConcatenated) {
                    Text(
                        stringResource(
                            R.string.parts_count,
                            budget.parts,
                            budget.units,
                            budget.capacity,
                        ),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            maxLines = 4,
        )
        Spacer(Modifier.size(8.dp))
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (canSend) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = stringResource(R.string.cd_send),
                tint = if (canSend) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}
