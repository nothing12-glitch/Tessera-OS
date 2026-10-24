package os.tessera.dialer.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import os.tessera.dialer.R
import os.tessera.dialer.logic.CallDirection

/** Іконка напрямку дзвінка; власні вектори, без чужих наборів. */
@DrawableRes
internal fun iconFor(direction: CallDirection): Int = when (direction) {
    CallDirection.Outgoing -> R.drawable.ic_call_out
    CallDirection.Incoming -> R.drawable.ic_call_in
    CallDirection.Missed -> R.drawable.ic_call_in
    CallDirection.Rejected -> R.drawable.ic_call_end
    CallDirection.Voicemail -> R.drawable.ic_voicemail
    CallDirection.Blocked -> R.drawable.ic_block
}

/** Тон рядка журналу: пропущені й відхилені — це те, на що дивляться першим. */
@Composable
internal fun tintFor(direction: CallDirection): Color = when (direction) {
    CallDirection.Missed, CallDirection.Rejected -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Напрямок дзвінка текстом — для озвучення екрану TalkBack, а не для рядка. */
@StringRes
internal fun directionLabel(direction: CallDirection): Int = when (direction) {
    CallDirection.Incoming -> R.string.direction_incoming
    CallDirection.Outgoing -> R.string.direction_outgoing
    CallDirection.Missed -> R.string.direction_missed
    CallDirection.Rejected -> R.string.direction_rejected
    CallDirection.Voicemail -> R.string.direction_voicemail
    CallDirection.Blocked -> R.string.direction_blocked
}

/**
 * Кругла кнопка-дія для панелі керування розмовою.
 *
 * Свій стан (мікрофон вимкнено, динамік увімкнено) показуємо тоном, а не
 * кольором: `primaryContainer` проти `surfaceContainerHighest` — саме те, що
 * радить Material 3 для перемикачів без тексту.
 */
@Composable
internal fun CircleAction(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 68.dp,
    container: Color? = null,
    onContainer: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val background = container ?: when {
        !enabled -> scheme.surfaceContainerHighest.copy(alpha = 0.4f)
        selected -> scheme.primaryContainer
        else -> scheme.surfaceContainerHighest
    }
    val foreground = onContainer ?: when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        selected -> scheme.onPrimaryContainer
        else -> scheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = foreground,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Порожній стан екрана: заголовок і пояснення під ним. */
@Composable
internal fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
