package os.tessera.camera.ui

import android.net.Uri
import android.util.Size
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.tessera.camera.R

/**
 * Мініатюра знімка з MediaStore.
 *
 * Беремо саме `loadThumbnail`, а не повний кадр: інакше кутовий превʼю
 * застосунку коштував би декодування 48-мегапіксельного JPEG.
 */
@Composable
fun rememberShotThumb(uri: Uri?, edge: Int = 160): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, uri, edge) {
        val target = uri ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(target, Size(edge, edge), null).asImageBitmap()
            }.getOrNull()
        }
    }.value
}

/** Кругла кнопка-дія поверх превʼю: тон поверхні показує стан, як у решті наших застосунків. */
@Composable
internal fun CircleAction(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 52.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        !enabled -> scheme.surfaceContainerHighest.copy(alpha = 0.4f)
        selected -> scheme.primaryContainer
        else -> Color(0x66000000)
    }
    val content = when {
        !enabled -> Color.White.copy(alpha = 0.38f)
        selected -> scheme.onPrimaryContainer
        else -> Color.White
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = content,
            modifier = Modifier.size(size / 2.1f),
        )
    }
}

/** Прапорець режиму «Фото» / «Відео». */
@Composable
internal fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) scheme.primaryContainer else Color(0x66000000))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimaryContainer else Color.White,
        )
    }
}

/** Пояснення по центру: заголовок, подробиці й кнопка дії. */
@Composable
internal fun CenteredNotice(
    title: String,
    body: String,
    action: String? = null,
    onAction: () -> Unit = {},
    busy: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 22.dp, vertical = 11.dp),
            )
        }
    }
}

/** Значок замість превʼю, коли камери немає або дозвіл ще не видано. */
@Composable
internal fun PlaceholderIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.5f),
        modifier = modifier.size(30.dp),
    )
}
