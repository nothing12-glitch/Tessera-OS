package os.tessera.dialer.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import os.tessera.dialer.InCallActivity

/**
 * Сервіс, який telecom піднімає для всіх дзвінків, коли «Телефон» призначено
 * основним набором (роль `ROLE_DIALER`).
 *
 * Сервіс нічого не малює — він лише тримає живий [Call] у [CallStateHolder] і
 * показує [InCallActivity]. Мелодію дзвінка грає система: поки ми відповідаємо
 * на `onCallAdded` і не викликали `setSilenceRinger`, викликаний нумераційний
 * план сам дає рингтон, тому власного Ringtone у застосунку свідомо немає.
 */
class TesseraInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallStateHolder.attach(applicationContext, this, call)
        showInCallUi()
    }

    override fun onCallRemoved(call: Call) {
        CallStateHolder.detach(call)
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(state: CallAudioState?) {
        state?.let { CallStateHolder.publishAudio(it.isMuted, it.route) }
    }

    override fun onMuteStateChanged(muted: Boolean) {
        // викликається тоді, коли стан мікрофона змінив хтось інший
        // (гарнітура, системна клавіша) — інакше екран залишився б із хибним прапорцем
        callAudioState?.let { CallStateHolder.publishAudio(muted, it.route) }
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        CallStateHolder.setDialpadVisible(showDialpad)
        showInCallUi()
    }

    private fun showInCallUi() {
        val intent = Intent(this, InCallActivity::class.java)
            // сервіс стартує активність із фону, а без нового таску це заборонено
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { startActivity(intent) }
    }
}
