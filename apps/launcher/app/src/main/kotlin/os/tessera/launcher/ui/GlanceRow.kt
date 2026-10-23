package os.tessera.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Рядок «дата + погода» вгорі головного екрана — те, що на Pixel робить
 * At a Glance.
 *
 * Дата — наша, без зовнішніх сервісів. Погода зʼявиться лише тоді, коли
 * хтось дасть рядок тексту: власницький віджет Google ми не вставляємо, тому
 * джерелом має стати або локальний застосунок, або відкрита служба, яку
 * обере користувач.
 */
@Composable
fun GlanceRow(
    date: LocalDate = LocalDate.now(),
    weatherLine: String? = null,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberFormatter()

    Row(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = date.format(formatter),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (weatherLine != null) {
            Text(
                text = weatherLine,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rememberFormatter() = remember {
    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("uk"))
}
