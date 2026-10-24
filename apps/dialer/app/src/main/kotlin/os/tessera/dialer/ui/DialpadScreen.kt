package os.tessera.dialer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import os.tessera.dialer.DialUiState
import os.tessera.dialer.R
import os.tessera.dialer.data.Suggestion
import os.tessera.dialer.logic.DialpadKeys
import os.tessera.dialer.logic.PhoneNumber

/**
 * Клавіатура набору.
 *
 * Кнопки — без тоналного кола: Material 3 для числової панелі радить великі
 * цифри на чистому фоні, а коло лишає для головної дії (виклик). Довге
 * натискання `0` дає `+` — той самий жест, що на апаратних телефонах.
 */
@Composable
internal fun DialpadScreen(
    state: DialUiState,
    onPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCall: () -> Unit,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatted = state.dialpad.formatted()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NumberLine(formatted, onBackspace)
        state.suggestion?.let { suggestion ->
            SuggestionRow(suggestion) { onSuggestion(suggestion.number) }
        }

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0..3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    for (column in 0..2) {
                        val key = DialpadKeys.DIGITS[row * 3 + column]
                        DialpadKey(
                            digit = key,
                            letters = DialpadKeys.lettersFor(key),
                            onClick = { onPress(key.single()) },
                            // «+» лише на нулі: це його другий символ на ISO-клавіатурі
                            onLongPress = if (key == "0") ({ onPress('+') }) else null,
                        )
                    }
                }
            }
        }

        FilledIconButton(
            onClick = onCall,
            enabled = state.canCall,
            modifier = Modifier
                .padding(top = 20.dp, bottom = 12.dp)
                .size(72.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_call),
                contentDescription = stringResource(R.string.call),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Рядок набору: номер праворуч, інакше хвіст зникає при перших цифрах. */
@Composable
private fun NumberLine(text: String, onBackspace: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tail(text),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp,
            ),
            color = if (text.isEmpty()) scheme.onSurfaceVariant.copy(alpha = 0.6f) else scheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 280.dp),
        )
        if (text.isNotEmpty()) {
            IconButton(
                onClick = onBackspace,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(44.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_backspace),
                    contentDescription = stringResource(R.string.delete),
                    tint = scheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: Suggestion, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = suggestion.name,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSecondaryContainer,
                maxLines = 1,
            )
            val subtitle = listOf(suggestion.label, PhoneNumber.display(suggestion.number))
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSecondaryContainer.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_call),
            contentDescription = stringResource(R.string.call),
            tint = scheme.onSecondaryContainer,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DialpadKey(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .size(width = 92.dp, height = 66.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.headlineMedium,
            color = scheme.onSurface,
        )
        Text(
            text = letters,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            letterSpacing = 1.5.sp,
        )
    }
}

/**
 * Хвіст номера.
 *
 * При 24 символах (стеля [os.tessera.dialer.logic.DialpadState.MAX_LENGTH])
 * номер не вміщується в рядок, а обрізання зліва зберігає саме те, що
 * користувач натиснув останнім.
 */
private fun tail(text: String): String =
    if (text.length <= 18) text else "…" + text.takeLast(18)
