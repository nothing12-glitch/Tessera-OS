package os.tessera.files

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.tessera.files.ui.FilesScreen
import os.tessera.files.ui.TesseraFilesTheme

class FilesActivity : ComponentActivity() {

    private val viewModel: FilesViewModel by viewModels()

    private val pickTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.openTree(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TesseraFilesTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                BackHandler(enabled = state.canGoUp, onBack = viewModel::goUp)
                FilesScreen(
                    state = state,
                    onPickTree = { pickTree.launch(null) },
                    onEnter = viewModel::enter,
                    onUp = viewModel::goUp,
                    onJumpTo = viewModel::jumpTo,
                    onSearch = viewModel::search,
                    onCycleSort = viewModel::cycleSort,
                    onCreateFolder = viewModel::createFolder,
                    onRename = viewModel::rename,
                    onDelete = viewModel::delete,
                    onNoticeShown = viewModel::dismissNotice,
                )
            }
        }
    }
}
