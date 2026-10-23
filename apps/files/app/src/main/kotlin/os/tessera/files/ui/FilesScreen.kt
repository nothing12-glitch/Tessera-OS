package os.tessera.files.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import os.tessera.files.FileNotice
import os.tessera.files.FilesUiState
import os.tessera.files.R
import os.tessera.files.logic.EntryKind
import os.tessera.files.logic.FileNode
import os.tessera.files.logic.Formatters
import os.tessera.files.logic.SortMode

@Composable
fun FilesScreen(
    state: FilesUiState,
    onPickTree: () -> Unit,
    onEnter: (FileNode) -> Unit,
    onUp: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onCycleSort: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRename: (FileNode, String) -> Unit,
    onDelete: (FileNode) -> Unit,
    onNoticeShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val host = remember { SnackbarHostState() }
    var folderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }

    val noticeText = state.notice?.let { noticeMessage(context, it) }
    LaunchedEffect(noticeText) {
        if (noticeText != null) {
            host.showSnackbar(noticeText)
            onNoticeShown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Header(
            canGoUp = state.canGoUp,
            title = state.currentName,
            sortMode = state.sortMode,
            query = state.query,
            onUp = onUp,
            onPickTree = onPickTree,
            onCycleSort = onCycleSort,
            onSearch = onSearch,
        )

        Breadcrumbs(state, onJumpTo)

        when {
            state.tree == null -> NeedsFolder(onPickTree, Modifier.weight(1f))
            state.isLoading && state.allEntries.isEmpty() ->
                StatusLine(R.string.loading, Modifier.weight(1f))
            state.visible.isEmpty() -> StatusLine(
                messageRes = if (state.query.isBlank()) R.string.folder_empty else R.string.nothing_found,
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(state.visible, key = { it.documentId }) { node ->
                    EntryRow(
                        node = node,
                        onOpen = { onEnter(node) },
                        onRename = { renameTarget = node },
                        onDelete = { onDelete(node) },
                    )
                }
            }
        }

        if (state.tree != null) {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.action_new_folder)) },
                icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
                onClick = { folderDialog = true },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 10.dp),
            )
        }

        SnackbarHost(host)
    }

    if (folderDialog) {
        NameDialog(
            title = stringResource(R.string.dialog_new_folder),
            initial = "",
            onDismiss = { folderDialog = false },
            onConfirm = { name ->
                folderDialog = false
                if (name.isNotBlank()) onCreateFolder(name.trim())
            },
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = stringResource(R.string.dialog_rename),
            initial = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                if (name.isNotBlank() && name != target.name) onRename(target, name.trim())
            },
        )
    }
}

@Composable
private fun Header(
    canGoUp: Boolean,
    title: String,
    sortMode: SortMode,
    query: String,
    onUp: () -> Unit,
    onPickTree: () -> Unit,
    onCycleSort: () -> Unit,
    onSearch: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoUp) {
            IconButton(onClick = onUp) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_up))
            }
        }
        Text(
            text = title.ifBlank { stringResource(R.string.app_name) },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        FilterChip(selected = true, onClick = onCycleSort, label = { Text(sortLabel(sortMode)) })
        AssistChip(
            onClick = onPickTree,
            label = { Text(stringResource(R.string.action_change_folder), style = MaterialTheme.typography.labelMedium) },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onSearch("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_search))
                    }
                }
            },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
    }
}

@Composable
private fun Breadcrumbs(state: FilesUiState, onJumpTo: (Int) -> Unit) {
    if (state.crumbs.size < 2) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        state.crumbs.forEachIndexed { index, crumb ->
            AssistChip(
                onClick = { onJumpTo(index) },
                label = { Text(text = crumb.name, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
            )
            if (index != state.crumbs.lastIndex) {
                Text("›", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(node: FileNode, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val kind = EntryKind.of(node.mimeType, node.isDirectory, node.extension)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindBadge(kind)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = node.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                text = summaryLine(node),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (!node.isWriteable) {
            Text(
                text = stringResource(R.string.read_only),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        EntryMenu(
            expanded = menu,
            isDirectory = node.isDirectory,
            onDismiss = { menu = false },
            onRename = onRename,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun summaryLine(node: FileNode): String {
    val modified = Formatters.relative(node.lastModifiedMillis, System.currentTimeMillis())
    if (node.isDirectory) return modified
    val size = Formatters.sizeOrUnknown(node.sizeBytes)
    return "$size · $modified"
}

@Composable
private fun KindBadge(kind: EntryKind) {
    val scheme = MaterialTheme.colorScheme
    val container = if (kind.isFolder) scheme.primaryContainer else scheme.surfaceContainerHighest
    val onContainer = if (kind.isFolder) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = kind.letter, style = MaterialTheme.typography.labelLarge, color = onContainer)
    }
}

@Composable
private fun EntryMenu(
    expanded: Boolean,
    isDirectory: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (!isDirectory) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_open)) }, onClick = onDismiss)
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_rename)) },
            onClick = {
                onDismiss()
                onRename()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}

@Composable
private fun NeedsFolder(onPickTree: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.needs_folder_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.needs_folder_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        ExtendedFloatingActionButton(onClick = onPickTree) {
            Text(stringResource(R.string.pick_folder))
        }
    }
}

@Composable
private fun StatusLine(messageRes: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun sortLabel(mode: SortMode): String = stringResource(
    when (mode) {
        SortMode.NameAsc -> R.string.sort_name
        SortMode.NameDesc -> R.string.sort_name_desc
        SortMode.ModifiedDesc -> R.string.sort_date
        SortMode.SizeDesc -> R.string.sort_size
    },
)

private fun noticeMessage(context: Context, notice: FileNotice): String = context.getString(
    when (notice) {
        FileNotice.Created -> R.string.notice_created
        FileNotice.CreateFailed -> R.string.notice_create_failed
        FileNotice.Renamed -> R.string.notice_renamed
        FileNotice.RenameFailed -> R.string.notice_rename_failed
        FileNotice.Deleted -> R.string.notice_deleted
        FileNotice.DeleteFailed -> R.string.notice_delete_failed
    },
)
