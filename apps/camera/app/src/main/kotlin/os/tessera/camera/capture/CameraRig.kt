package os.tessera.camera.capture

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import java.time.ZoneId
import java.util.concurrent.Executor
import os.tessera.camera.logic.Flash
import os.tessera.camera.logic.Lens
import os.tessera.camera.logic.MediaNames

/**
 * Камера як пристрій: привʼязка CameraX, спуск затвора, старт і зупинка запису.
 *
 * Живе в активності, а не у `ViewModel`, бо `ProcessCameraProvider`
 * привʼязаний до життєвого циклу вікна: пережити вікно він не може, і
 * привʼязка збоку виявилась би мертвою.
 */
class CameraRig(private val activity: ComponentActivity) {

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(activity)
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null
    private var recording: Recording? = null
    private var flash: Flash = Flash.Off

    /** Результат запису відео: URI готового файла або причина, чому не вийшло. */
    sealed interface RecordingOutcome {
        data class Saved(val uri: Uri?) : RecordingOutcome
        data class Failed(val error: Int, val cause: Throwable?) : RecordingOutcome
    }

    /** `false` до того, як система віддала provider — тоді кнопки не активні. */
    val ready: Boolean get() = provider != null

    fun onProviderReady(block: () -> Unit) {
        val future = ProcessCameraProvider.getInstance(activity)
        if (future.isDone) {
            attach(future)
            block()
        } else {
            future.addListener({
                attach(future)
                block()
            }, mainExecutor)
        }
    }

    private fun attach(future: ListenableFuture<ProcessCameraProvider>) {
        provider = runCatching { future.get() }.getOrNull()
    }

    fun hasFront(): Boolean =
        provider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true

    /**
     * Привʼязує превʼю, затвор і записник до одного обʼєктива.
     *
     * Повертає `false`, якщо такого обʼєктива в системі немає — на VM без
     * камери це звична ситуація, і активність має показати пояснення,
     * а не падати.
     */
    fun bind(view: PreviewView, lens: Lens, rotation: Int): Boolean {
        val provider = provider ?: return false
        val selector = when (lens) {
            Lens.Back -> CameraSelector.DEFAULT_BACK_CAMERA
            Lens.Front -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
        if (!provider.hasCamera(selector)) return false

        val preview = Preview.Builder().build().apply {
            surfaceProvider = view.surfaceProvider
        }
        val capture = ImageCapture.Builder()
            // Mінімальна затримка: у кишені швидше сфотографувати, ніж отримати
            // ідеальну шумопридатність на нічному кадрі.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(rotation)
            .build()
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build()
        val video = VideoCapture.withOutput(recorder).apply { targetRotation = rotation }

        provider.unbindAll()
        var boundCamera = runCatching { provider.bindToLifecycle(activity, selector, preview, capture, video) }
        val withVideo = boundCamera.isSuccess
        if (!withVideo) {
            // Камери, які не тримають фото й відео водночас (камера емулятора,
            // частина бюджетних сенсорів), — це не «камери немає»: привʼязуємо
            // без відео, а запис чесно відмовляє окремим повідомленням.
            Log.w(TAG, "фото+відео не привʼязались, пробуємо лише фото", boundCamera.exceptionOrNull())
            provider.unbindAll()
            boundCamera = runCatching { provider.bindToLifecycle(activity, selector, preview, capture) }
        }
        val bound = boundCamera.getOrElse {
            Log.w(TAG, "навіть фото+превʼю не привʼязались", it)
            return false
        }

        imageCapture = capture
        videoCapture = if (withVideo) video else null
        camera = bound
        setFlash(flash)
        return true
    }

    fun setFlash(flash: Flash) {
        this.flash = flash
        imageCapture?.flashMode = when (flash) {
            Flash.Off -> ImageCapture.FLASH_MODE_OFF
            Flash.Auto -> ImageCapture.FLASH_MODE_AUTO
            Flash.On -> ImageCapture.FLASH_MODE_ON
        }
    }

    fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    /** Межі зуму реальної камери — щоб жести не просили 40x на 4x. */
    fun zoomLimits(): ClosedFloatingPointRange<Float> {
        val state = camera?.cameraInfo?.zoomState?.value ?: return 1f..1f
        return state.minZoomRatio..state.maxZoomRatio
    }

    /** Ліхтарик під відео: спалах у записі не працює, torch — так. */
    fun setTorch(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
    }

    fun takePhoto(onSaved: (Uri?) -> Unit, onFailure: (Throwable) -> Unit): Boolean {
        val capture = imageCapture ?: return false
        val now = System.currentTimeMillis()
        val values = photoValues(now)
        val options = ImageCapture.OutputFileOptions.Builder(
            activity.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).build()
        capture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) = onSaved(results.savedUri)

            override fun onError(exception: ImageCaptureException) = onFailure(exception)
        })
        return true
    }

    fun startRecording(
        withAudio: Boolean,
        onStart: () -> Unit,
        onFinish: (RecordingOutcome) -> Unit,
    ): Boolean {
        val recorder = videoCapture?.output ?: return false
        if (recording != null) return false
        val values = videoValues(System.currentTimeMillis())
        val options = MediaStoreOutputOptions.Builder(
            activity.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).build()

        val pending = runCatching {
            recorder.prepareRecording(activity, options)
                .let { if (withAudio) it.withAudioEnabled() else it }
        }.getOrNull() ?: return false

        recording = runCatching {
            pending.start(mainExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> onStart()
                    is VideoRecordEvent.Finalize -> onFinish(
                        if (event.hasError()) {
                            RecordingOutcome.Failed(event.error, event.cause)
                        } else {
                            RecordingOutcome.Saved(runCatching { event.outputResults.outputUri }.getOrNull())
                        },
                    )
                    // Pause/Resume/Status нам не потрібні: лічильник веде активність.
                    else -> Unit
                }
            }
        }.getOrNull()
        return recording != null
    }

    fun stopRecording() {
        recording?.stop()
    }

    /** Викликаємо на `onDestroy`: інакше запис у фоні лишає в MediaStore незакритий рядок. */
    fun release() {
        recording?.let { runCatching { it.stop() } }
        recording = null
        camera = null
        imageCapture = null
        videoCapture = null
        provider?.unbindAll()
    }

    private fun photoValues(moment: Long): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, MediaNames.photoName(moment, ZoneId.systemDefault()))
        put(MediaStore.MediaColumns.MIME_TYPE, MediaNames.PHOTO_MIME)
        put(MediaStore.MediaColumns.RELATIVE_PATH, folder(MediaNames.PHOTO_FOLDER))
    }

    private fun videoValues(moment: Long): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, MediaNames.videoName(moment, ZoneId.systemDefault()))
        put(MediaStore.MediaColumns.MIME_TYPE, MediaNames.VIDEO_MIME)
        put(MediaStore.MediaColumns.RELATIVE_PATH, folder(MediaNames.VIDEO_FOLDER))
    }

    /**
     * `RELATIVE_PATH` очікує «DCIM/Tessera» з системним роздільником.
     *
     * Теки беремо з `Environment`, а не рядком із константи, щоб на англомовній
     * і на україномовній системі галерея однаково знаходила наші знімки.
     */
    private fun folder(path: String): String {
        val parts = path.split('/')
        val root = when (parts.first()) {
            "DCIM" -> Environment.DIRECTORY_DCIM
            else -> Environment.DIRECTORY_MOVIES
        }
        return (listOf(root) + parts.drop(1)).joinToString("/") + "/"
    }

    /** Поворот екрана для `targetRotation`; без дисплея — як є. */
    fun displayRotation(view: PreviewView): Int =
        view.display?.rotation ?: Surface.ROTATION_0

    private companion object {
        const val TAG = "TesseraCamera"
    }
}
