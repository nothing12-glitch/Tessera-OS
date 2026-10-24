package os.tessera.camera.logic

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Імена файлів і лічильник запису — те, що користувач бачить у галереї. */
class ShootingTest {

    @Test
    fun `under an hour the counter is minutes and seconds`() {
        assertEquals("0:00", formatRecordingDuration(0))
        assertEquals("0:09", formatRecordingDuration(9))
        assertEquals("1:05", formatRecordingDuration(65))
        assertEquals("59:59", formatRecordingDuration(59 * 60 + 59))
    }

    @Test
    fun `past an hour the hours appear`() {
        assertEquals("1:00:00", formatRecordingDuration(3600))
        assertEquals("2:03:07", formatRecordingDuration(2 * 3600 + 3 * 60 + 7))
    }

    @Test
    fun `a timer started a hair early shows zero and not minus`() {
        assertEquals("0:00", formatRecordingDuration(-3))
    }

    @Test
    fun `photo names look like every other gallery expects`() {
        val kyiv = ZoneId.of("Europe/Kyiv")
        val moment = java.time.LocalDateTime.of(2026, 10, 23, 14, 15, 30)
            .atZone(kyiv)
            .toInstant()
            .toEpochMilli()
        assertEquals("IMG_20261023_141530.jpg", MediaNames.photoName(moment, kyiv))
        assertEquals("VID_20261023_141530.mp4", MediaNames.videoName(moment, kyiv))
    }

    @Test
    fun `shots land in our own folders`() {
        assertEquals("DCIM/Tessera", MediaNames.PHOTO_FOLDER)
        assertEquals("Movies/Tessera", MediaNames.VIDEO_FOLDER)
        assertEquals("image/jpeg", MediaNames.PHOTO_MIME)
        assertEquals("video/mp4", MediaNames.VIDEO_MIME)
    }
}
