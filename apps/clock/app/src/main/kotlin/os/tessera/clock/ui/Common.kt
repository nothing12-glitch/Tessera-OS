package os.tessera.clock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Тональні ролі Material 3 — як у калькуляторі: без жодного фіксованого кольору. */
internal enum class Tone { Primary, Tonal, Neutral, Danger }

/** Великий показ часу по центру екрана; caption — другий рядок (на кшталт «Час вийшов»). */
@Composable
internal fun BigTime(
    text: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
    caption: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val color = when {
        emphasize -> scheme.primary
        caption != null -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            ),
            color = color,
            maxLines = 1,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
internal fun ActionButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun RowScope.ActionButton(
    label: String,
    onClick: () -> Unit,
    tone: Tone = Tone.Tonal,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer) = when {
        !enabled -> scheme.surfaceContainerLow.copy(alpha = 0.5f) to
            scheme.onSurface.copy(alpha = 0.38f)
        tone == Tone.Primary -> scheme.primary to scheme.onPrimary
        tone == Tone.Tonal -> scheme.secondaryContainer to scheme.onSecondaryContainer
        tone == Tone.Danger -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = onContainer,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
