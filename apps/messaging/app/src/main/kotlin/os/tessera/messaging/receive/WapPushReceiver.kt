package os.tessera.messaging.receive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * WAP push — тобто MMS.
 *
 * Компонент обовʼязковий: без нього застосунок не проходить перевірку ролі
 * `ROLE_SMS` у нашій базі (`roles.xml` вимагає receiver на
 * `WAP_PUSH_DELIVER` із типом `application/vnd.wap.mms-message`). Сам MMS ми
 * поки не вміємо — розбір PDU, завантаження з MMO-проксі й APN-конфіг це
 * окремий проєкт.
 *
 * Тож тут порожній `onReceive`. Це чесніший вибір, ніж імітувати: повідомлення
 * не зʼявиться в розмовах узагалі, а не зʼявиться обрізаним. Поки MMS потрібен
 * людині, в образі ROM має лишатися системний `MmsService` (див.
 * `build/tessera.mk`: ми його прибрали, бо в емулятора без модема все одно
 * немає чого слухати).
 */
class WapPushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Навмисно нічого: broadcast уже зобовʼязаний бути «зʼїденим»
        // основним застосунком SMS, інакше платформа логує його як невручений.
    }
}
