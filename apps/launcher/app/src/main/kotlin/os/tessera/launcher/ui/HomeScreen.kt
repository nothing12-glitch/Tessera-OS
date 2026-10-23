package os.tessera.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import os.tessera.launcher.LauncherState
import os.tessera.launcher.R
import os.tessera.launcher.data.LauncherApp
import os.tessera.launcher.layout.GridSpec
import os.tessera.launcher.layout.HomeLayout

/** Відстань свайпу вгору, після якої відкриваємо шухляду. */
private const val DRAWER_SWIPE_THRESHOLD_DP = 96f

/**
 * Головний екран: рядок «дата + погода», сітка 5×6, пошукова пігулка й док
 * із 5 слотів.
 *
 * Геометрію не вигадуємо — [GridSpec.LARGE_PHONE] це профіль `5_by_6` з бази,
 * який ми робимо дефолтним build-time підміною (docs/PIXEL-PARITY.md).
 */
@Composable
fun HomeScreen(
    state: LauncherState,
    actions: AppActions,
    onOpenDrawer: () -> Unit,
    grid: GridSpec = LocalGridSpec.current,
    modifier: Modifier = Modifier,
) {
    val appById = remember(state.apps) { state.apps.associateBy { it.id } }
    val cells = remember(state.homePins, grid.homeCapacity) {
        HomeLayout.place(state.homePins, grid.homeCapacity)
    }
    val dockApps = remember(state.dock, appById, grid.dockSlots) {
        state.dock.mapNotNull(appById::get).take(grid.dockSlots)
    }

    var dragY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val threshold = DRAWER_SWIPE_THRESHOLD_DP * density
                detectVerticalDragGestures(
                    onDragStart = { dragY = 0f },
                    onDragCancel = { dragY = 0f },
                    onVerticalDrag = { _, dy -> dragY += dy },
                    onDragEnd = {
                        if (dragY < -threshold) onOpenDrawer()
                        dragY = 0f
                    },
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        GlanceRow(modifier = Modifier.fillMaxWidth())

        HomeGrid(
            cells = cells,
            appById = appById,
            actions = actions,
            grid = grid,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        SearchPill(onOpenDrawer = onOpenDrawer, modifier = Modifier.align(Alignment.CenterHorizontally))

        DockBar(
            apps = dockApps,
            actions = actions,
            grid = grid,
            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
        )
    }
}

/** Сітка рядків на стовпців; порожні комірки лишаються на місці. */
@Composable
private fun HomeGrid(
    cells: List<String?>,
    appById: Map<String, LauncherApp>,
    actions: AppActions,
    grid: GridSpec,
    modifier: Modifier = Modifier,
) {
    val homePins = cells.filterNotNull().toSet()
    Column(modifier) {
        repeat(grid.rows) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                repeat(grid.columns) { column ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val app = appById[cells[row * grid.columns + column]]
                        if (app == null) {
                            EmptyCell()
                        } else {
                            AppCell(
                                app = app,
                                actions = actions,
                                iconSize = grid.iconSizeDp,
                                labelTextSp = grid.labelTextSp,
                                pinned = app.id in homePins,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Пошукова пігулка над доком.
 *
 * Відкриваємо шухляду з полем пошуку: власний пошук по встановлених
 * застосунках. Пошук Google як у Pixel вимагав би власницьких сервісів,
 * тому його тут свідомо немає.
 */
@Composable
private fun SearchPill(onOpenDrawer: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .clickable(onClick = onOpenDrawer)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.home_search),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Док із [GridSpec.dockSlots] слотами; підписів під іконками немає, як на стоці. */
@Composable
private fun DockBar(
    apps: List<LauncherApp>,
    actions: AppActions,
    grid: GridSpec,
    modifier: Modifier = Modifier,
) {
    val slots = List(grid.dockSlots) { apps.getOrNull(it) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (app in slots) {
            Box(
                modifier = Modifier.width((grid.iconSizeDp + 14).dp),
                contentAlignment = Alignment.Center,
            ) {
                if (app == null) {
                    EmptyCell()
                } else {
                    AppCell(
                        app = app,
                        actions = actions,
                        iconSize = grid.iconSizeDp,
                        labelTextSp = grid.labelTextSp,
                        docked = true,
                        showLabel = false,
                    )
                }
            }
        }
    }
}
