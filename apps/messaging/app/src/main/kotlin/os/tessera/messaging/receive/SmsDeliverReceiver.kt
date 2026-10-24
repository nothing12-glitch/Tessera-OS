package os.tessera.messaging.receive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import os.tessera.messaging.data.SmsStore

/**
 * Доставлення вхідної SMS.
 *
 * Поки ми — основний застосунок повідомлень, платформа не пише вхідне в
 * провайдера сама: вона лише надсилає цей broadcast, а запис — наш обовʼязок.
 * Якщо його не зробити, sms-ка просто зникне: інші застосунки її не побачать,
 * бо `SMS_RECEIVED` платформа для основного застосунку не розсилає.
 *
 * Частини багато-частинного повідомлення зʼєднуємо в порядку, в якому їх віддав
 * `getMessagesFromIntent`: послідовність UDH у відкритому SDK не читається
 * (`SmsMessage` не показує номера частини), а порядок у списку збігається з
 * порядком PDU. Наслідок: рідкісний випадок, коли модем приніс частини
 * переплутаними, дав би переплутаний текст замість втраченого — свідомо лишаємо
 * так, бо без UDH справжній порядок не відновити.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                store(context, intent)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun store(context: Context, intent: Intent) {
        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull()
            .orEmpty()
            .filterNotNull()
        if (parts.isEmpty()) return

        val first = parts.first()
        // Індикатор голосової пошти (class 0 / MWI) — не текст для користувача:
        // писати таке в розмови означало б смітити в них службовими рядками.
        if (first.isMWISetMessage || first.isMwiDontStore) return

        val address = first.originatingAddress?.takeIf { it.isNotBlank() } ?: return
        val body = parts.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) return

        SmsStore(context).insertInbox(
            address = address,
            body = body,
            timestampMillis = if (first.timestampMillis > 0) first.timestampMillis else System.currentTimeMillis(),
        )
    }
}
