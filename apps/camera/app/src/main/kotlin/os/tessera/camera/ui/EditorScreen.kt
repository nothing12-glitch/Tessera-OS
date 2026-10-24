package os.tessera.camera.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.tessera.camera.R
import os.tessera.camera.EditorState
import os.tessera.camera.logic.CropAspect
import os.tessera.camera.render.ImageBake

@Composable
fun EditorScreen(
    state: EditorState,
    onAspect: (CropAspect) -> Unit,
    onRotate: () -> Unit,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val params = state.params
    val preview = state.preview

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleAction(
                icon = R.drawable.ic_close,
                description = stringResource(R.string.editor_close),
                onClick = onClose,
                size = 44.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.editor_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onReset, enabled = !params.untouched) {
                Text(stringResource(R.string.editor_reset))
            }
            Spacer(Modifier.width(6.dp))
            Button(onClick = onSave, enabled = preview != null && !state.saving && !params.untouched) {
                Text(stringResource(if (state.saving) R.string.editor_saving else R.string.editor_save))
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(preview.width.toFloat() / preview.height.toFloat()),
                    colorFilter = ImageBake.toneFilter(params),
                    contentScale = ContentScale.Crop,
                )
            } else {
                CenteredNotice(
                    title = stringResource(
                        if (state.failed) R.string.editor_failed else R.string.editor_title,
                    ),
                    body = "",
                    busy = !state.failed,
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CropAspect.values().forEach { aspect ->
                    ModeChip(
                        label = stringResource(labelFor(aspect)),
                        selected = params.aspect == aspect,
                        onClick = { onAspect(aspect) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleAction(
                    icon = R.drawable.ic_rotate_right,
                    description = stringResource(R.string.editor_rotate),
                    onClick = onRotate,
                    size = 44.dp,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.editor_crop),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(6.dp))
            ToneRow(
                label = stringResource(R.string.editor_brightness),
                value = params.brightness,
                onValue = onBrightness,
            )
            ToneRow(
                label = stringResource(R.string.editor_contrast),
                value = params.contrast,
                onValue = onContrast,
            )
        }
    }
}

/** Один повзунок тону з підписом: значення в підписі не дублюємо — воно й так видно. */
@Composable
private fun ToneRow(
    label: String,
    value: Float,
    onValue: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp),
        )
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = -1f..1f,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun labelFor(aspect: CropAspect): Int = when (aspect) {
    CropAspect.Original -> R.string.aspect_original
    CropAspect.Square -> R.string.aspect_square
    CropAspect.Portrait -> R.string.aspect_portrait
    CropAspect.Wide -> R.string.aspect_wide
}
