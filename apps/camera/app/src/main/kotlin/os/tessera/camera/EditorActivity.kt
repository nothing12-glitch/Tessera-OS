package os.tessera.camera

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.tessera.camera.ui.EditorScreen
import os.tessera.camera.ui.TesseraCameraTheme

/**
 * Редактор одного знімка: поворот, пропорція, яскравість і контраст.
 *
 * Викликаємо лише з «Камери» й лише на конкретний URI, тому `exported=false`.
 * Оригінал не чіпаємо: «Зберегти» кладе копію в `Pictures/Tessera`.
 */
class EditorActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val uri = resolveUri()
        if (uri == null) {
            finish()
            return
        }
        viewModel.open(uri)

        setContent {
            TesseraCameraTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                EditorScreen(
                    state = state,
                    onAspect = viewModel::setAspect,
                    onRotate = viewModel::rotate,
                    onBrightness = viewModel::setBrightness,
                    onContrast = viewModel::setContrast,
                    onReset = viewModel::reset,
                    onSave = { viewModel.save(::onSaved) },
                    onClose = ::finish,
                )
            }
        }
    }

    private fun onSaved(saved: Uri) {
        Log.i(TAG, "edited copy saved at $saved")
        finish()
    }

    /**
     * URI з «Камери» чи з будь-якого застосунку, що вміє `ACTION_EDIT`/`VIEW`.
     *
     * extra має пріоритет: власний виклик завжди надсилає рядок, а data може
     * бути порожньою.
     */
    private fun resolveUri(): Uri? {
        val fromExtra = intent?.getStringExtra(EXTRA_URI)?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        return fromExtra ?: intent?.data
    }

    companion object {
        const val EXTRA_URI = "os.tessera.camera.extra.URI"
        private const val TAG = "TesseraCameraEditor"
    }
}
