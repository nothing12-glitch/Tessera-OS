package os.tessera.camera.ui

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import os.tessera.camera.R
import os.tessera.camera.logic.CaptureMode
import os.tessera.camera.logic.Flash
import os.tessera.camera.logic.Lens
import os.tessera.camera.logic.formatRecordingDuration
import os.tessera.camera.CameraUiState

@Composable
fun CameraScreen(
    state: CameraUiState,
    previewView: PreviewView,
    permissionMissing: Boolean,
    zoomLimits: ClosedFloatingPointRange<Float>,
    onMode: (CaptureMode) -> Unit,
    onShutter: () -> Unit,
    onFlip: () -> Unit,
    onFlash: () -> Unit,
    onZoom: (Float) -> Unit,
    onOpenLast: () -> Unit,
    onAskPermission: () -> Unit,
) {
    val blocked = permissionMissing || state.cameraMissing
    // Жест живе в корутині `pointerInput`, тому значення на момент події
    // беремо через `rememberUpdatedState`, а не зі складеного стану.
    val zoomNow = rememberUpdatedState(state.zoom)
    val emitZoom = rememberUpdatedState(onZoom)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!blocked) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(zoomLimits) {
                        detectTransformGestures { _, _, pan, _ ->
                            emitZoom.value((zoomNow.value * pan).coerceIn(zoomLimits))
                        }
                    },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleAction(
                    icon = when (state.flash) {
                        Flash.Off -> R.drawable.ic_flash_off
                        Flash.Auto -> R.drawable.ic_flash_auto
                        Flash.On -> R.drawable.ic_flash_on
                    },
                    description = stringResource(
                        when (state.flash) {
                            Flash.Off -> R.string.flash_off
                            Flash.Auto -> R.string.flash_auto
                            Flash.On -> R.string.flash_on
                        },
                    ),
                    onClick = onFlash,
                    selected = state.flash != Flash.Off,
                    // у відео спалах неможливий — там лише ліхтарик
                    enabled = !blocked && state.mode == CaptureMode.Photo,
                )
                Spacer(Modifier.weight(1f))
                if (state.recording) {
                    RecordingBadge(state.elapsedSeconds)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(
                        if (state.lens == Lens.Front) R.string.camera_front_short else R.string.camera_back_short,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.weight(1f))

            if (!blocked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    ModeChip(
                        label = stringResource(R.string.mode_photo),
                        selected = state.mode == CaptureMode.Photo,
                        onClick = { onMode(CaptureMode.Photo) },
                    )
                    Spacer(Modifier.width(10.dp))
                    ModeChip(
                        label = stringResource(R.string.mode_video),
                        selected = state.mode == CaptureMode.Video,
                        onClick = { onMode(CaptureMode.Video) },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LastShotThumb(uri = state.lastShot, onClick = onOpenLast)
                Spacer(Modifier.weight(1f))
                Shutter(
                    video = state.mode == CaptureMode.Video,
                    recording = state.recording,
                    enabled = !blocked,
                    onClick = onShutter,
                )
                Spacer(Modifier.weight(1f))
                CircleAction(
                    icon = R.drawable.ic_switch_camera,
                    description = stringResource(R.string.switch_camera),
                    onClick = onFlip,
                    enabled = !blocked && state.frontAvailable,
                )
            }

            state.message?.let { id ->
                Text(
                    text = stringResource(id),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (blocked) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xF2101418))) {
                if (permissionMissing) {
                    CenteredNotice(
                        title = stringResource(R.string.permission_title),
                        body = stringResource(R.string.permission_body),
                        action = stringResource(R.string.permission_action),
                        onAction = onAskPermission,
                    )
                } else {
                    CenteredNotice(
                        title = stringResource(R.string.no_camera_title),
                        body = stringResource(R.string.no_camera_body),
                    )
                }
            }
        }
    }
}

/** Великий затвор: у фото — біле коло, у відео — червоне, під записом — квадрат «стоп». */
@Composable
private fun Shutter(
    video: Boolean,
    recording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val ring = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    // Кнопка намальована кружечком без всякого тексту, тож для TalkBack (і для
    // зовнішніх тестів, що читають дамп екрана) імені вона не має взагалі.
    val label = stringResource(
        when {
            recording -> R.string.stop_recording
            video -> R.string.start_recording
            else -> R.string.shutter
        },
    )
    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(CircleShape)
            .border(width = 3.dp, color = ring, shape = CircleShape)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (recording) 28.dp else 58.dp)
                .clip(
                    if (recording) RoundedCornerShape(7.dp) else CircleShape,
                )
                .background(
                    when {
                        !enabled -> Color.White.copy(alpha = 0.25f)
                        video -> Color(0xFFF2555A)
                        else -> Color.White
                    },
                ),
        )
    }
}

/** Лічильник запису: червоний бік + час, щоб «чи пишеться» було видно з кишені. */
@Composable
private fun RecordingBadge(seconds: Long) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xB3000000))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFFF2555A)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatRecordingDuration(seconds),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

/** Кут останнього знімка: тиснеш — відкривається редактор. */
@Composable
private fun LastShotThumb(uri: Uri?, onClick: () -> Unit) {
    val thumb = rememberShotThumb(uri)
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = uri != null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = stringResource(R.string.open_last),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            PlaceholderIcon(R.drawable.ic_photo)
        }
    }
}
