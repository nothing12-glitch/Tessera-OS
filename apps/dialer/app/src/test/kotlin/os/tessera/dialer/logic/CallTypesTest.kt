package os.tessera.dialer.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Маппінг типів журналу.
 *
 * Числа тут продубльовані з `android.provider.CallLog.Calls` свідомо: тест
 * має ловити саме зміну значення, а не помилку імені константи.
 */
class CallTypesTest {

    @Test
    fun `тип журналу стає напрямком`() {
        assertEquals(CallDirection.Incoming, CallTypes.directionOf(1))
        assertEquals(CallDirection.Outgoing, CallTypes.directionOf(2))
        assertEquals(CallDirection.Missed, CallTypes.directionOf(3))
        assertEquals(CallDirection.Voicemail, CallTypes.directionOf(4))
        assertEquals(CallDirection.Rejected, CallTypes.directionOf(5))
        assertEquals(CallDirection.Blocked, CallTypes.directionOf(6))
    }

    @Test
    fun `дзвінок піднятий на іншому пристрої — теж пропущений для нас`() {
        assertEquals(CallDirection.Missed, CallTypes.directionOf(CallTypes.ANSWERED_EXTERNALLY))
        assertEquals(7, CallTypes.ANSWERED_EXTERNALLY)
    }

    @Test
    fun `невідомий тип не ламає рядок`() {
        // Нові типи зʼявляються у свіжих релізах Framework; рядок має лишитись придатним
        assertEquals(CallDirection.Outgoing, CallTypes.directionOf(999))
    }

    @Test
    fun `пропущені позначаються без тривалості`() {
        assertEquals(true, CallTypes.isMissed(CallTypes.MISSED))
        assertEquals(true, CallTypes.isMissed(CallTypes.ANSWERED_EXTERNALLY))
        assertEquals(false, CallTypes.isMissed(CallTypes.INCOMING))
        assertEquals(false, CallTypes.isMissed(CallTypes.REJECTED))
    }
}
