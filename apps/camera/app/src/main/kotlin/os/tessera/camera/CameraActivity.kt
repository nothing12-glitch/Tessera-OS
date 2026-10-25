package os.tessera.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import os.tessera.camera.capture.CameraRig
import os.tessera.camera.logic.CaptureMode
import os.tessera.camera.logic.Flash
import os.tessera.camera.logic.Lens
import os.tessera.camera.ui.CameraScreen
import os.tessera.camera.ui.TesseraCameraTheme

/**
 * Екран камери.
 *
 * Активність тримає все, що потребує вікна: `PreviewView`, дозвіл на камеру,
 * привʼязку CameraX і поворот кадру. `ViewModel` знає лише стан на екрані,
 * бо provider живе рівно стільки, скільки живе вікно.
 */
class CameraActivity : ComponentActivity() {

    private val viewModel: CameraViewModel by viewModels()

    /** Створюємо ліниво: до дозволу показувати превʼю немає сенсу. */
    private val previewView: PreviewView by lazy {
        PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    private var rig: CameraRig? = null
    private var boundLens: Lens? = null

    private var permissionMissing by mutableStateOf(true)
    private var zoomLimits by mutableStateOf(1f..1f)

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionMissing = !granted
            if (granted) prepareCamera()
        }

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // без мікрофона не відмовляємо: пишемо німе відео й пояснюємо це
            rig?.let { startRecording(it, withAudio = granted) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Камера тримає екран увімкненим сама: ніхто не хоче, щоб гас кадр у момент знімка.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            TesseraCameraTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                CameraScreen(
                    state = state,
                    previewView = previewView,
                    permissionMissing = permissionMissing,
                    zoomLimits = zoomLimits,
                    onMode = viewModel::selectMode,
                    onShutter = ::onShutter,
                    onFlip = ::flipLens,
                    onFlash = viewModel::cycleFlash,
                    onZoom = viewModel::setZoom,
                    onOpenLast = ::openLast,
                    onAskPermission = ::askForCamera,
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { apply(it) }
            }
        }

        // Прапцем керує лише колбек запиту дозволу, а він не спрацьовує, коли
        // дозвіл уже виданий: без цього «Камера вимкнена» висіла б на кожному
        // запуску після першого.
        permissionMissing = !granted(Manifest.permission.CAMERA)
        if (!permissionMissing) prepareCamera() else askForCamera()
    }

    /** Поворот у нас у `configChanges`, тож перепривʼязуємо самі: інакше затвор знає старий кут. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        previewView.post { bindNow() }
    }

    override fun onDestroy() {
        rig?.release()
        rig = null
        boundLens = null
        super.onDestroy()
    }

    // --- камера -----------------------------------------------------------

    private fun askForCamera() {
        requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun prepareCamera() {
        val rig = rig ?: CameraRig(this).also { this.rig = it }
        // Provider віддається асинхронно: до цього моменту кнопки затвора не працюватимуть.
        rig.onProviderReady {
            viewModel.setFrontAvailable(rig.hasFront())
            bindNow()
        }
    }

    private fun bindNow() {
        val rig = rig ?: return
        val lens = viewModel.lens
        val bound = rig.bind(previewView, lens, currentRotation())
        boundLens = if (bound) lens else null
        viewModel.setCameraMissing(!bound)
        if (bound) zoomLimits = rig.zoomLimits()
    }

    /** Кут, у якому зараз тримають пристрій; без привʼязаного дисплея — як є. */
    private fun currentRotation(): Int =
        previewView.display?.rotation ?: display?.rotation ?: Surface.ROTATION_0

    private fun flipLens() {
        if (!viewModel.state.value.frontAvailable) return
        viewModel.cycleLens()
    }

    /** Усе, що стан екрана каже пристрою: спалах, ліхтарик, зум і новий обʼєктив. */
    private fun apply(state: CameraUiState) {
        val rig = rig ?: return
        rig.setFlash(state.flash)
        // У відео спалах неможливий, зате ліхтарик світить увесь запис.
        rig.setTorch(state.mode == CaptureMode.Video && state.flash == Flash.On)
        rig.setZoom(state.zoom)
        if (state.lens != boundLens) bindNow()
    }

    // --- затвор -----------------------------------------------------------

    private fun onShutter() {
        val rig = rig ?: return
        if (viewModel.state.value.recording) {
            rig.stopRecording()
            return
        }
        when (viewModel.mode) {
            CaptureMode.Photo -> takePhoto(rig)
            CaptureMode.Video -> recordWithAudioCheck(rig)
        }
    }

    private fun takePhoto(rig: CameraRig) {
        val started = rig.takePhoto(
            onSaved = { uri ->
                viewModel.saved(uri)
                viewModel.notify(R.string.saved_photo)
            },
            onFailure = { error ->
                Log.w(TAG, "photo failed", error)
                viewModel.fail(R.string.capture_failed)
            },
        )
        if (!started) viewModel.fail(R.string.capture_failed)
    }

    private fun recordWithAudioCheck(rig: CameraRig) {
        if (granted(Manifest.permission.RECORD_AUDIO)) {
            startRecording(rig, withAudio = true)
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording(rig: CameraRig, withAudio: Boolean) {
        val started = rig.startRecording(
            withAudio = withAudio,
            onStart = { viewModel.beginRecording() },
            onFinish = ::finishRecording,
        )
        when {
            !started -> viewModel.fail(R.string.capture_failed)
            !withAudio -> viewModel.notify(R.string.audio_denied)
        }
    }

    private fun finishRecording(outcome: CameraRig.RecordingOutcome) {
        viewModel.endRecording()
        when (outcome) {
            is CameraRig.RecordingOutcome.Saved -> {
                viewModel.saved(outcome.uri)
                viewModel.notify(R.string.saved_video)
            }

            is CameraRig.RecordingOutcome.Failed -> {
                Log.w(TAG, "video failed: ${outcome.error}", outcome.cause)
                viewModel.fail(R.string.record_failed)
            }
        }
    }

    // --- останній знімок --------------------------------------------------

    private fun openLast() {
        val uri = viewModel.state.value.lastShot ?: return
        val editor = Intent(this, EditorActivity::class.java)
            .putExtra(EditorActivity.EXTRA_URI, uri.toString())
        // Відео редактор фото не показує — для нього віддаємо системний перегляд.
        val intent = if (isVideo(uri)) Intent(Intent.ACTION_VIEW, uri) else editor
        runCatching { startActivity(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            .onFailure { Log.w(TAG, "cannot open $uri", it) }
    }

    private fun isVideo(uri: Uri): Boolean =
        contentResolver.getType(uri)?.startsWith("video/") == true

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "TesseraCamera"
    }
}
