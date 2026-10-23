package os.tessera.files

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.tessera.files.data.DocumentSource
import os.tessera.files.logic.EntrySort
import os.tessera.files.logic.FileNode
import os.tessera.files.logic.SortMode

data class Crumb(val documentId: String, val name: String)

enum class FileNotice { Created, CreateFailed, Renamed, RenameFailed, Deleted, DeleteFailed }

data class FilesUiState(
    val tree: Uri? = null,
    val crumbs: List<Crumb> = emptyList(),
    val allEntries: List<FileNode> = emptyList(),
    val query: String = "",
    val sortMode: SortMode = SortMode.NameAsc,
    val isLoading: Boolean = false,
    val notice: FileNotice? = null,
) {
    val visible: List<FileNode> get() = EntrySort.sort(allEntries, sortMode, query)
    val currentName: String get() = crumbs.lastOrNull()?.name.orEmpty()
    val canGoUp: Boolean get() = crumbs.size > 1
}

/**
 * Навігація по дозволеним дереву документів.
 *
 * Уся робота з ContentResolver — на Dispatchers.IO: велика папка з
 * тисячею файлів інакше підвісить перший кадр Compose.
 */
class FilesViewModel(app: Application) : AndroidViewModel(app) {

    private val source = DocumentSource(app.contentResolver)

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state

    init {
        restore()
    }

    /** Повертаємо останню дозволену папку після перезапуску застосунку. */
    private fun restore() {
        val granted = source.grantedTrees().maxByOrNull { it.persistedTime } ?: return
        openTree(granted.uri)
    }

    fun openTree(uri: Uri) {
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        if (treeId == null) {
            _state.update { it.copy(notice = FileNotice.CreateFailed) }
            return
        }
        source.grant(uri)
        // Дозвіл на попередню теку знімаємо: інакше їх накопичується скільки завгодно.
        _state.value.tree?.takeIf { it != uri }?.let { source.release(it) }
        val rootName = File(treeId).name.ifEmpty { uri.lastPathSegment.orEmpty() }
        _state.update {
            it.copy(
                tree = uri,
                crumbs = listOf(Crumb(treeId, rootName)),
                allEntries = emptyList(),
                notice = null,
            )
        }
        loadCurrent()
    }

    fun enter(node: FileNode) {
        if (!node.isDirectory) {
            openFile(node)
            return
        }
        _state.update { it.copy(crumbs = it.crumbs + Crumb(node.documentId, node.name)) }
        loadCurrent()
    }

    fun goUp() {
        if (!_state.value.canGoUp) return
        _state.update { it.copy(crumbs = it.crumbs.dropLast(1)) }
        loadCurrent()
    }

    fun jumpTo(index: Int) {
        val crumbs = _state.value.crumbs
        if (index !in crumbs.indices) return
        _state.update { it.copy(crumbs = crumbs.take(index + 1)) }
        loadCurrent()
    }

    fun search(text: String) = _state.update { it.copy(query = text) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun cycleSort() {
        val modes = SortMode.entries
        _state.update { state ->
            val next = modes[(modes.indexOf(state.sortMode) + 1) % modes.size]
            state.copy(sortMode = next)
        }
    }

    fun createFolder(name: String) {
        val tree = _state.value.tree ?: return
        val parent = _state.value.crumbs.last()
        viewModelScope.launch {
            val created = withIo { source.createFolder(tree, parent.documentId, name) }
            _state.update { it.copy(notice = if (created != null) FileNotice.Created else FileNotice.CreateFailed) }
            if (created != null) loadCurrent()
        }
    }

    fun rename(node: FileNode, newName: String) {
        val tree = _state.value.tree ?: return
        viewModelScope.launch {
            val ok = withIo { source.rename(tree, node.documentId, newName) }
            _state.update { it.copy(notice = if (ok) FileNotice.Renamed else FileNotice.RenameFailed) }
            if (ok) loadCurrent()
        }
    }

    fun delete(node: FileNode) {
        val tree = _state.value.tree ?: return
        viewModelScope.launch {
            val ok = withIo { source.delete(tree, node.documentId) }
            _state.update { it.copy(notice = if (ok) FileNotice.Deleted else FileNotice.DeleteFailed) }
            if (ok) loadCurrent()
        }
    }

    /** Файл відкриваємо чужим застосунком: власних декодерів у нас немає. */
    private fun openFile(node: FileNode) {
        val tree = _state.value.tree ?: return
        val uri = source.documentUri(tree, node.documentId)
        val intent = source.viewIntent(uri, node.mimeType)
        runCatching {
            getApplication<Application>().startActivity(
                Intent.createChooser(intent, node.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun loadCurrent() {
        val tree = _state.value.tree ?: return
        val crumb = _state.value.crumbs.lastOrNull() ?: return
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val entries = withIo { source.children(tree, crumb.documentId) }
            _state.update { it.copy(allEntries = entries, isLoading = false) }
        }
    }

    private suspend fun <T> withIo(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
