package os.tessera.messaging

import android.app.Service
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import os.tessera.messaging.data.SmsStore
import os.tessera.messaging.logic.MessageStatus
import os.tessera.messaging.receive.SendStatusReceiver

/**
 * Швидка відповідь текстом на вхідний дзвінок.
 *
 * Четвертий компонент, якого вимагає роль `ROLE_SMS`: телеком-сервіс запускає
 * цей service з `action=RESPOND_VIA_MESSAGE` і `data=smsto:<номер>`, коли
 * користувач на екрані дзвінка обирає «відповісти sms-кою». Без нього система не
 * вважає застосунок основним для повідомлень — навіть якби ми взагалі нічого
 * не надсижали.
 *
 * Текст відповіді приносить в екстрі. Її назва, `android.telecom.extra.MESSAGE`,
 * у відкритому SDK 35 не існує (константа лежить у прихованому `InCallAdapter`),
 * тому пробуємо обидва відомі ключі; якщо обох немає — мовчки завершуємо.
 * Краще не відповісти взагалі, ніж надіслати порожню sms-ку.
 */
class RespondViaMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) respond(intent)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun respond(intent: Intent) {
        val number = intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: return
        val body = intent.getStringExtra(EXTRA_TELECOM_MESSAGE)
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: return
        if (body.isBlank()) return

        val manager = getSystemService(SmsManager::class.java) ?: return
        val store = SmsStore(this)
        val id = store.insertSent(number, body, System.currentTimeMillis())
        val sent = PendingIntent.getBroadcast(
            this,
            requestCode(id),
            Intent(SendStatusReceiver.ACTION_SENT)
                .setPackage(packageName)
                .putExtra(SendStatusReceiver.EXTRA_ROW_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Без цього PendingIntent рядок лишився б «надсилається» вічно: екрана
        // розмови тут немає, і перечитати статус було б комусь нікому.
        runCatching { manager.sendTextMessage(number, null, body, sent, null) }
            .onFailure { store.updateStatus(id, MessageStatus.Failed) }
    }

    private fun requestCode(rowId: Long): Int = (rowId xor (rowId ushr 32)).toInt()

    private companion object {
        const val EXTRA_TELECOM_MESSAGE = "android.telecom.extra.MESSAGE"
    }
}
