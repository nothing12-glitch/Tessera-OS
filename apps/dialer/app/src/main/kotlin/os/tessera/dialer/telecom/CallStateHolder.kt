package os.tessera.dialer.telecom

import android.content.Context
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.tessera.dialer.R
import os.tessera.dialer.logic.DialpadState
import os.tessera.dialer.logic.PhoneNumber

/** Усе, що бачить екран розмови. Один незмінний стан, щоб Compose міг просто читати. */
data class InCallUiState(
    val visible: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val statusText: String = "",
    val durationSec: Long = 0,
    val ringing: Boolean = false,
    val onHold: Boolean = false,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val canHold: Boolean = false,
    val dialpadVisible: Boolean = false,
    val dialpadText: String = "",
)

/**
 * Місток між `InCallService` і екраном розмови.
 *
 * Обʼєкт [Call] не можна передати у `ViewModel` активності: він належить
 * telecom-звʼязку сервісу й стає недійсним, коли дзвінок закінчується. Тому
 * сервіс тримає call тут, публікує з нього звичайний незмінний стан, а
 * активність лише читає `StateFlow` і викликає дії.
 *
 * Telecom тримає в процесі рівно один екземпляр сервісу, тож глобальний
 * `object` — не костиль-синглетон, а чесне відображення одного живого дзвінка.
 */
object CallStateHolder {

    private val _state = MutableStateFlow(InCallUiState())
    val state: StateFlow<InCallUiState> = _state

    private var service: InCallService? = null
    private var call: Call? = null
    private var app: Context? = null
    private var connectedAtElapsed = -1L
    private var ticker: Job? = null
    private var scope: CoroutineScope? = null
    private var currentDialpad = DialpadState()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish(state)

        override fun onDetailsChanged(call: Call, details: Call.Details) = publish(call.state)
    }

    fun attach(context: Context, inCallService: InCallService, newCall: Call) {
        app = context.applicationContext
        service = inCallService
        call?.unregisterCallback(callback)
        call = newCall
        newCall.registerCallback(callback)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        currentDialpad = DialpadState()
        if (newCall.state == Call.STATE_ACTIVE) connectedAtElapsed = SystemClock.elapsedRealtime()
        publish(newCall.state)
    }

    fun detach(removed: Call) {
        if (call !== removed) return
        removed.unregisterCallback(callback)
        call = null
        service = null
        connectedAtElapsed = -1L
        currentDialpad = DialpadState()
        stopTicking()
        scope?.cancel()
        scope = null
        // контекст лишаємо: наступний дзвінок прийде в той самий процес
        _state.value = InCallUiState()
    }

    /** Сервіс викликає це при привʼязці й на кожній зміні аудіо — звідси прапорці мікрофона й динаміка. */
    fun publishAudio(muted: Boolean, route: Int) {
        _state.update {
            it.copy(muted = muted, speaker = route == CallAudioState.ROUTE_SPEAKER)
        }
    }

    // --- дії екрана -------------------------------------------------------

    fun answer() {
        // 0 = без прапорців: відео й hands-free на цьому екрані немає
        call?.answer(0)
    }

    fun hangup() {
        val c = call ?: return
        // дзвінок, що ще не піднятий, telecom просить reject, а не disconnect
        if (c.state == Call.STATE_RINGING) c.reject(false, null) else c.disconnect()
    }

    fun toggleMute() {
        service?.setMuted(!_state.value.muted)
    }

    fun toggleSpeaker() {
        val route = if (_state.value.speaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE
        else CallAudioState.ROUTE_SPEAKER
        service?.setAudioRoute(route)
    }

    fun toggleHold() {
        val c = call ?: return
        when (c.state) {
            Call.STATE_ACTIVE -> c.hold()
            Call.STATE_HOLDING -> c.unhold()
            else -> Unit
        }
    }

    fun setDialpadVisible(visible: Boolean) =
        _state.update { it.copy(dialpadVisible = visible, dialpadText = if (visible) it.dialpadText else "") }

    fun pressKey(symbol: Char) {
        currentDialpad = currentDialpad.append(symbol)
        _state.update { it.copy(dialpadText = currentDialpad.formatted()) }
        if (!DialpadState.isDialable(symbol) || symbol == '+') return
        val c = call ?: return
        runCatching { c.playDtmfTone(symbol) }
        // тон треба зупинити самому: без stopDtmfTone наступні символи злились би в один
        scope?.launch {
            delay(DTMF_TONE_MS)
            runCatching { c.stopDtmfTone() }
        }
    }

    // --- службове ---------------------------------------------------------

    private fun publish(state: Int) {
        val context = app
        val c = call
        if (context == null || c == null) {
            _state.value = InCallUiState()
            return
        }
        if (state == Call.STATE_ACTIVE && connectedAtElapsed < 0) {
            connectedAtElapsed = SystemClock.elapsedRealtime()
        }
        val details = runCatching { c.details }.getOrNull()
        val handle = details?.handle?.schemeSpecificPart.orEmpty()
        val displayName = details?.callerDisplayName.orEmpty().trim()
        val numberText = PhoneNumber.display(handle)
        val title = displayName.ifBlank { numberText.ifBlank { context.getString(R.string.unknown_number) } }
        val active = state == Call.STATE_ACTIVE

        _state.update {
            it.copy(
                visible = true,
                title = title,
                subtitle = if (displayName.isBlank()) "" else numberText,
                statusText = context.getString(statusOf(state)),
                durationSec = if (active && connectedAtElapsed >= 0) {
                    ((SystemClock.elapsedRealtime() - connectedAtElapsed) / 1000L).coerceAtLeast(0)
                } else {
                    0
                },
                ringing = state == Call.STATE_RINGING,
                onHold = state == Call.STATE_HOLDING || state == Call.STATE_AUDIO_PROCESSING,
                canHold = runCatching {
                    details?.can(Call.Details.CAPABILITY_SUPPORT_HOLD) == true ||
                        details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true
                }.getOrDefault(false),
            )
        }
        if (active) startTicking() else stopTicking()
    }

    private fun statusOf(state: Int): Int = when (state) {
        Call.STATE_RINGING -> R.string.call_incoming
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT,
        Call.STATE_NEW, Call.STATE_PULLING_CALL, Call.STATE_DISCONNECTING -> R.string.call_connecting
        Call.STATE_HOLDING, Call.STATE_AUDIO_PROCESSING -> R.string.call_on_hold
        else -> R.string.call_active
    }

    private fun startTicking() {
        if (ticker?.isActive == true) return
        ticker = scope?.launch {
            while (true) {
                delay(1000L)
                val c = call ?: break
                val seconds = if (c.state == Call.STATE_ACTIVE && connectedAtElapsed >= 0) {
                    ((SystemClock.elapsedRealtime() - connectedAtElapsed) / 1000L).coerceAtLeast(0)
                } else {
                    0
                }
                _state.update { if (it.durationSec == seconds) it else it.copy(durationSec = seconds) }
            }
        }
    }

    private fun stopTicking() {
        ticker?.cancel()
        ticker = null
    }

    private const val DTMF_TONE_MS = 120L
}
