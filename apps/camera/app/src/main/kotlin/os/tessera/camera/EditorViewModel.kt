package os.tessera.camera

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.tessera.camera.logic.CropAspect
import os.tessera.camera.logic.EditParams
import os.tessera.camera.logic.ImageTransform
import os.tessera.camera.render.Decoded
import os.tessera.camera.render.ImageBake

/**
 * Стан редактора й усе, що потребує диска.
 *
 * Превʼю перекодується лише коли змінюється геометрія (поворот або
 * пропорція). Повзунки яскравості й контрасту до матриці не доходять: їх
 * малює `ColorFilter` на готовому кадрі, тому вони рухаються без затримки.
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var source: Decoded? = null
    private var previewJob: Job? = null

    fun open(uri: Uri) {
        if (source?.uri == uri) return
        viewModelScope.launch(Dispatchers.IO) {
            val decoded = runCatching {
                ImageBake.decode(getApplication(), uri, ImageTransform.PREVIEW_LONG_EDGE)
            }.getOrNull()
            if (decoded == null) {
                _state.update { it.copy(failed = true) }
                return@launch
            }
            source = decoded
            _state.update { it.copy(source = uri, failed = false) }
            renderPreview()
        }
    }

    fun setAspect(aspect: CropAspect) {
        _state.update { it.copy(params = it.params.copy(aspect = aspect)) }
        renderPreview()
    }

    fun rotate() {
        _state.update {
            it.copy(params = it.params.copy(quarterTurns = it.params.turns + 1))
        }
        renderPreview()
    }

    fun setBrightness(value: Float) = edit { it.copy(brightness = value) }

    fun setContrast(value: Float) = edit { it.copy(contrast = value) }

    fun reset() {
        _state.update { it.copy(params = EditParams()) }
        renderPreview()
    }

    /**
     * Збереження копії.
     *
     * Орігінал декодуємо ще раз і в повну роздільність: превʼю зменшене, і
     * інакше «Зберегти» тихо зменшило б знімок у кілька разів.
     */
    fun save(onSaved: (Uri) -> Unit) {
        val uri = _state.value.source ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, saved = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val full = runCatching { ImageBake.decode(getApplication(), uri, Int.MAX_VALUE) }
                .getOrNull()
            val result = full?.let {
                val baked = ImageBake.apply(it.bitmap, _state.value.params)
                ImageBake.saveEdited(getApplication(), baked, System.currentTimeMillis())
            }
            _state.update { it.copy(saving = false, saved = result, failed = result == null) }
            if (result != null) onSaved(result)
        }
    }

    private fun edit(block: (EditParams) -> EditParams) =
        _state.update { it.copy(params = block(it.params)) }

    private fun renderPreview() {
        val decoded = source ?: return
        val params = _state.value.params
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            val baked = ImageBake.apply(decoded.bitmap, params.copy(brightness = 0f, contrast = 0f))
            _state.update { it.copy(preview = baked.asImageBitmap()) }
        }
    }
}

data class EditorState(
    val source: Uri? = null,
    val preview: ImageBitmap? = null,
    val params: EditParams = EditParams(),
    val saving: Boolean = false,
    val saved: Uri? = null,
    val failed: Boolean = false,
) {
    /** Чи є сенс натискати «Зберегти»: без правок копія була б зайвим дублем. */
    val pristine: Boolean get() = params.untouched
}
