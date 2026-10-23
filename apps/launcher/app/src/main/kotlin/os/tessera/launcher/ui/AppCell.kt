package os.tessera.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import os.tessera.launcher.R
import os.tessera.launcher.data.LauncherApp

/**
 * Дії над застосунком, спільні для головного екрана й шухляди.
 *
 * Тримаємо одним обʼєктом, щоб обидва екрани не множили шість однакових
 * колбеків у сигнатурах.
 */
data class AppActions(
    val onLaunch: (LauncherApp) -> Unit,
    val onPin: (LauncherApp) -> Unit,
    val onUnpin: (LauncherApp) -> Unit,
    val onDock: (LauncherApp) -> Unit,
    val onUndock: (LauncherApp) -> Unit,
    val onAppInfo: (LauncherApp) -> Unit,
)

/**
 * Одна комірка застосунку: іконка (+ підпис) і контекстне меню довгим натиском.
 *
 * Меню — єдиний спосіб керувати розміщенням, поки немає перетягування.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    app: LauncherApp,
    actions: AppActions,
    iconSize: Int,
    labelTextSp: Float,
    pinned: Boolean = false,
    docked: Boolean = false,
    showLabel: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width((iconSize + 20).dp)
            .combinedClickable(
                onClick = { actions.onLaunch(app) },
                onLongClick = { menuOpen = true },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon(icon = app.icon, label = app.label, size = iconSize.dp)
        if (showLabel) AppLabel(label = app.label, textSize = labelTextSp.sp)

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuItem(
                textRes = if (pinned) R.string.action_unpin else R.string.action_pin,
                onClick = {
                    menuOpen = false
                    if (pinned) actions.onUnpin(app) else actions.onPin(app)
                },
            )
            MenuItem(
                textRes = if (docked) R.string.action_undock else R.string.action_dock,
                onClick = {
                    menuOpen = false
                    if (docked) actions.onUndock(app) else actions.onDock(app)
                },
            )
            MenuItem(
                textRes = R.string.action_app_info,
                onClick = {
                    menuOpen = false
                    actions.onAppInfo(app)
                },
            )
        }
    }
}

@Composable
private fun MenuItem(textRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(textRes)) }, onClick = onClick)
}

/** Порожня комірка: тримає місце в сітці, нічого не показує. */
@Composable
fun EmptyCell(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(0.dp))
}
