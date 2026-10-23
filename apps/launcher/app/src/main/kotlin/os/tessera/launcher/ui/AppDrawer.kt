package os.tessera.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import os.tessera.launcher.R
import os.tessera.launcher.data.LauncherApp
import os.tessera.launcher.layout.GridSpec
import java.text.Normalizer

/**
 * Шухляда застосунків: поле пошуку й сітка з стільцями висотою 104 dp і
 * проміжком 16 dp — це `allAppsCellHeight` і `allAppsBorderSpace` з профілю
 * `5_by_6`. Профіль не задає розмір іконки в шухляді окремо, тому беремо
 * розмір із головного екрана (56 dp), а не вгадуємо пікселівський.
 */
@Composable
fun AppDrawer(
    apps: List<LauncherApp>,
    homePins: List<String>,
    actions: AppActions,
    onClose: () -> Unit,
    grid: GridSpec = LocalGridSpec.current,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val pinnedIds = remember(homePins) { homePins.toSet() }
    val shown = remember(apps, query.text) { apps.filter { matches(it.label, query.text) } }
    val focusRequester = remember { FocusRequester() }

    var dragY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragY = 0f },
                    onDragCancel = { dragY = 0f },
                    onVerticalDrag = { _, dy -> dragY += dy },
                    onDragEnd = {
                        if (dragY > 112f * density) onClose()
                        dragY = 0f
                    },
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onClose = onClose,
                focusRequester = focusRequester,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )

            if (shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.drawer_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(grid.columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(grid.drawerBorderSpaceDp.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown, key = { it.id }) { app ->
                        Box(
                            modifier = Modifier.height(grid.drawerCellHeightDp.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppCell(
                                app = app,
                                actions = actions,
                                iconSize = grid.iconSizeDp,
                                labelTextSp = grid.labelTextSp,
                                pinned = app.id in pinnedIds,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.drawer_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            Row {
                if (value.text.isNotEmpty()) {
                    IconButton(onClick = { onValueChange(TextFieldValue("")) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.drawer_clear),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.drawer_back),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * Пошук без чутливості до регістру й зайвих діакритик.
 *
 * `Normalizer` потрібен, щоб «Жовта» знаходилася за запитом «жовта», а
 * «київ» — за «Київ».
 */
private fun matches(label: String, query: String): Boolean {
    if (query.isBlank()) return true
    val needle = normalize(query)
    return normalize(label).contains(needle)
}

private fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
