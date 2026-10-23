package os.tessera.launcher.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.core.graphics.drawable.toBitmap

/**
 * Іконка застосунку.
 *
 * Форму дає параметр теми системи ([round]): наш OS-оверлей робить adaptive-іконки
 * круглими (`config_useRoundIcon`), тому за замовчуванням клипаємо коло.
 * Іконки беремо з `PackageManager` — нічого не вбудовуємо й не копіюємо.
 */
@Composable
fun AppIcon(
    icon: Drawable?,
    label: String,
    size: Dp,
    round: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shape = if (round) CircleShape else RoundedCornerShape(percent = 30)
    // Рахуємо бітмапу під фізичний розмір комірки: в adaptive-іконок
    // intrinsicWidth зазвичай -1, і інакше довелось би вгадувати константу.
    val density = LocalDensity.current
    val sizePx = remember(size, density) { with(density) { size.toPx() }.toInt().coerceAtLeast(1) }
    val painter = icon?.let { rememberDrawablePainter(it, sizePx) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        } else {
            // Немає іконки — не підставляємо чужу: монограма з тоналності теми.
            Box(
                modifier = Modifier
                    .size(size)
                    .background(MaterialTheme.colorScheme.secondaryContainer, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun rememberDrawablePainter(drawable: Drawable, sizePx: Int) =
    remember(drawable, sizePx) {
        BitmapPainter(drawable.toBitmap(sizePx, sizePx, null).asImageBitmap())
    }

/** Підпис під іконкою: 12 sp за профілем `5_by_6`, одна рядка, решта — тріє. */
@Composable
fun AppLabel(label: String, textSize: TextUnit, modifier: Modifier = Modifier) {
    Text(
        text = label,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = textSize),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
