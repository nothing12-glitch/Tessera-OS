package os.tessera.welcome.data

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.delay

/**
 * Звук і вібрація на завершення першого запуску.
 *
 * `ToneGenerator` не потребує жодного дозволу, вібрація — `VIBRATE` (нормальний
 * дозвіл, оголошений у маніфесті). Немає динаміка чи мотора — мовчки
 * розводимо руками: жодного «звук відтворено» на екрані цей крок не каже.
 */
class CompletionCue(context: Context) {

    private val appContext = context.applicationContext

    private val vibrator: Vibrator? = runCatching {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
            ?.takeIf { it.hasVibrator() }
    }.getOrNull()

    /** Два короткі тони й два поштовхи: «налаштування скінчилося». */
    suspend fun play() {
        val tone = runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
        }.getOrNull()
        try {
            if (tone != null) {
                tone.startTone(ToneGenerator.TONE_PROP_ACK, TONE_MS)
                delay(TONE_MS.toLong())
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_MS)
                delay(TONE_MS.toLong())
            }
            buzz()
            delay(PULSE_MS.toLong())
            buzz()
        } finally {
            runCatching { tone?.release() }
        }
    }

    private fun buzz() {
        runCatching {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(PULSE_MS.toLong(), VibrationEffect.DEFAULT_AMPLITUDE),
            )
        }
    }

    private companion object {
        const val TONE_VOLUME = 70
        const val TONE_MS = 180
        const val PULSE_MS = 90
    }
}
