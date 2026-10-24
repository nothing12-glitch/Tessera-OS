package os.tessera.camera

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.tessera.camera.logic.CaptureMode
import os.tessera.camera.logic.Flash
import os.tessera.camera.logic.Lens

/**
 * Стан екрана камери.
 *
 * Камерою керує активність — `ProcessCameraProvider` привʼязаний до вікна, а
 * `ViewModel` його переживе, і привʼязка виявилась би мертвою. Тут лише те, що
 * бачить користувач: режим, обʼєктив, спалах, хвилина запису й останній знімок.
 */
class CameraViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    /** Запис рахуємо секундами: користувачеві важливіше «скільки», ніж «до чого». */
    private val recordStartedAt = AtomicLong(0L)
    private var ticker: kotlinx.coroutines.Job? = null
    private var messageJob: kotlinx.coroutines.Job? = null

    val mode: CaptureMode get() = _state.value.mode
    val lens: Lens get() = _state.value.lens
    val flash: Flash get() = _state.value.flash

    fun selectMode(mode: CaptureMode) {
        if (mode == _state.value.mode) return
        // спалах на передній камері не має сенсу, а в відеорежимі він ліхтарик
        _state.update { it.copy(mode = mode, message = null) }
    }

    fun cycleLens() {
        _state.update { it.copy(lens = if (it.lens == Lens.Back) Lens.Front else Lens.Back) }
    }

    fun cycleFlash() {
        _state.update {
            it.copy(flash = when (it.flash) {
                Flash.Off -> Flash.Auto
                Flash.Auto -> Flash.On
                Flash.On -> Flash.Off
            })
        }
    }

    fun setZoom(ratio: Float) = _state.update { it.copy(zoom = ratio) }

    fun setFrontAvailable(available: Boolean) = _state.update { it.copy(frontAvailable = available) }

    fun setCameraMissing(missing: Boolean) = _state.update { it.copy(cameraMissing = missing) }

    fun saved(uri: Uri?) {
        if (uri == null) return
        _state.update { it.copy(lastShot = uri, message = null) }
    }

    fun fail(@StringRes message: Int) = notify(message)

    /**
     * Плашка не повинна висіти над превʼю вічно: користувач встигає прочитати
     * підпис і повертається до кадру, тому ховаємо її самі за кілька секунд.
     */
    fun notify(@StringRes message: Int) {
        messageJob?.cancel()
        _state.update { it.copy(message = message) }
        messageJob = viewModelScope.launch {
            delay(MESSAGE_LINGER_MS)
            _state.update { it.copy(message = null) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Лічильник запису запускаємо саме тоді, коли система підтвердила старт. */
    fun beginRecording() {
        recordStartedAt.set(System.currentTimeMillis())
        _state.update { it.copy(recording = true, elapsedSeconds = 0) }
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                delay(500)
                val elapsed = (System.currentTimeMillis() - recordStartedAt.get()) / 1000
                _state.update { it.copy(elapsedSeconds = elapsed) }
            }
        }
    }

    fun endRecording() {
        ticker?.cancel()
        ticker = null
        _state.update { it.copy(recording = false, elapsedSeconds = 0) }
    }

    override fun onCleared() {
        ticker?.cancel()
        messageJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val MESSAGE_LINGER_MS = 2_600L
    }
}

data class CameraUiState(
    val mode: CaptureMode = CaptureMode.Photo,
    val lens: Lens = Lens.Back,
    val flash: Flash = Flash.Off,
    val zoom: Float = 1f,
    val recording: Boolean = false,
    val elapsedSeconds: Long = 0,
    val lastShot: Uri? = null,
    val frontAvailable: Boolean = true,
    val cameraMissing: Boolean = false,
    @StringRes val message: Int? = null,
)
