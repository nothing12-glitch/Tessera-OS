package os.tessera.messaging.receive

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import os.tessera.messaging.data.SmsStore
import os.tessera.messaging.logic.MessageStatus

/**
 * Результат надсилання від модема.
 *
 * Окремий receiver, а не колбек в активності: поки частини йдуть у мережу,
 * користувач уже встиг вийти з екрана, а рядок у провайдері мусить отримати
 * справжній статус. Пишемо саме туда — і `ContentObserver` в активності сам
 * перечитає список, тому тут немає ніякого «штовхання» в UI.
 *
 * Дивимося лише на `resultCode`: точний `errorCode` (радіо вимкнено, немає
 * мережі) приходить у extras, які платформа дописує лише до MUTABLE
 * PendingIntent. Ми ж тримаємо IMMUTABLE — як радить Android 12+ для
 * PendingIntent, що їх бачить сторонній код, — і не хочемо заради деталей
 * тексту помилки відступати від цього.
 */
class SendStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val rowId = intent.getLongExtra(EXTRA_ROW_ID, -1L)
        if (rowId < 0) return
        val status = if (resultCode == Activity.RESULT_OK) MessageStatus.Sent else MessageStatus.Failed
        SmsStore(context).updateStatus(rowId, status)
    }

    companion object {
        /** Власна дія: з `setPackage` у PendingIntent її не перехопить інший застосунок. */
        const val ACTION_SENT = "os.tessera.messaging.SMS_SENT"

        /** `_id` рядка: у PendingIntent його не покласти (код запиту — 32 біти), тому екстра. */
        const val EXTRA_ROW_ID = "row_id"
    }
}
