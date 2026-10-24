package os.tessera.dialer.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.tessera.dialer.R
import os.tessera.dialer.logic.CallFormat
import os.tessera.dialer.logic.DialpadKeys
import os.tessera.dialer.telecom.InCallUiState

/**
 * Екран розмови.
 *
 * Він показує те, що дав telecom: напрямок стану, імʼя з `Call.Details` і
 * тривалість, яку рахуємо з моменту переходу в `STATE_ACTIVE`. Кнопки лише
 * просять у тримача стану дію — сам `Call` сюди не потрапляє.
 */
@Composable
internal fun InCallScreen(
    state: InCallUiState,
    onAnswer: () -> Unit,
    onHangup: () -> Unit,
    onMute: () -> Unit,
    onHold: () -> Unit,
    onSpeaker: () -> Unit,
    onToggleDialpad: () -> Unit,
    onPressKey: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        CallerBadge(state.title)
        Spacer(Modifier.height(16.dp))
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (state.subtitle.isNotBlank()) {
            Text(
                text = state.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (state.durationSec > 0) {
                "${state.statusText} · ${CallFormat.duration(state.durationSec)}"
            } else {
                state.statusText
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.onHold) scheme.error else scheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.weight(1f))

        if (state.dialpadVisible) {
            if (state.dialpadText.isNotBlank()) {
                Text(
                    text = state.dialpadText,
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in 0..3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    ) {
                        for (column in 0..2) {
                            val key = DialpadKeys.DIGITS[row * 3 + column]
                            DialpadKey(
                                digit = key,
                                letters = DialpadKeys.lettersFor(key),
                                onClick = { onPressKey(key.single()) },
                                onLongPress = null,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            ) {
                LabeledAction(
                    icon = if (state.muted) R.drawable.ic_mic_off else R.drawable.ic_mic,
                    label = stringResource(if (state.muted) R.string.call_unmute else R.string.call_mute),
                    description = stringResource(R.string.call_mute),
                    selected = state.muted,
                    onClick = onMute,
                )
                LabeledAction(
                    icon = R.drawable.ic_hold,
                    label = stringResource(R.string.call_hold),
                    description = stringResource(R.string.call_hold),
                    selected = state.onHold,
                    enabled = state.canHold,
                    onClick = onHold,
                )
                LabeledAction(
                    icon = R.drawable.ic_speaker,
                    label = stringResource(R.string.call_speaker),
                    description = stringResource(R.string.call_speaker),
                    selected = state.speaker,
                    onClick = onSpeaker,
                )
                LabeledAction(
                    icon = R.drawable.ic_dialpad,
                    label = stringResource(R.string.call_keypad),
                    description = stringResource(R.string.call_keypad),
                    selected = state.dialpadVisible,
                    onClick = onToggleDialpad,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.ringing) {
                CircleAction(
                    icon = R.drawable.ic_call,
                    description = stringResource(R.string.call_answer),
                    onClick = onAnswer,
                    size = 76.dp,
                    container = scheme.primary,
                    onContainer = scheme.onPrimary,
                )
            }
            CircleAction(
                icon = R.drawable.ic_call_end,
                description = stringResource(R.string.call_end),
                onClick = onHangup,
                size = 76.dp,
                container = scheme.error,
                onContainer = scheme.onError,
            )
        }
    }
}

@Composable
private fun LabeledAction(
    @DrawableRes icon: Int,
    label: String,
    description: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircleAction(
            icon = icon,
            description = description,
            onClick = onClick,
            selected = selected,
            enabled = enabled,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 1,
        )
    }
}

/**
 * Круглий «аватар» абонента.
 *
 * Перша літера імені — те, що дає і Material-годинник, і системні контакти:
 * без фото це чесний заповнювач, а не обіцянка фото, якого в нас немає.
 */
@Composable
private fun CallerBadge(title: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.firstOrNull { !it.isWhitespace() }?.uppercase() ?: "#",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
            color = scheme.onSurfaceVariant,
        )
    }
}
