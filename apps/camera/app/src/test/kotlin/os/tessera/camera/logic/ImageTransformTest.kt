package os.tessera.camera.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Геометрія редактора — те, що користувач бачить як «обрізати й повернути».
 *
 * Тестуємо саме ці числа, а не малювання: помилка в пропорції ламає кожен
 * збережений кадр, а перевіряти її на живому пристрої довго.
 */
class ImageTransformTest {

    @Test
    fun `turns wrap around a full circle`() {
        assertEquals(0, ImageTransform.normalizeTurns(0))
        assertEquals(1, ImageTransform.normalizeTurns(1))
        assertEquals(0, ImageTransform.normalizeTurns(4))
        assertEquals(3, ImageTransform.normalizeTurns(-1))
        assertEquals(2, ImageTransform.normalizeTurns(10))
    }

    @Test
    fun `quarter turn swaps the sides`() {
        assertEquals(Bounds(90, 160), ImageTransform.rotatedBounds(160, 90, 1))
        assertEquals(Bounds(160, 90), ImageTransform.rotatedBounds(160, 90, 2))
        assertEquals(Bounds(160, 90), ImageTransform.rotatedBounds(160, 90, 0))
    }

    @Test
    fun `original aspect keeps the whole frame`() {
        val rect = ImageTransform.cropRect(400, 300, CropAspect.Original)
        assertEquals(CropRect(0, 0, 400, 300), rect)
    }

    @Test
    fun `square crops the sides and stays centred`() {
        val rect = ImageTransform.cropRect(400, 300, CropAspect.Square)
        assertEquals(300, rect.width)
        assertEquals(300, rect.height)
        assertEquals(50, rect.left)
        assertEquals(0, rect.top)
        assertEquals(350, rect.right)
        assertEquals(300, rect.bottom)
    }

    @Test
    fun `wide crop cuts the height on a portrait frame`() {
        // 300x400: за висотою 16:9 попросило б 711 px ширини — обрізаємо по вертикалі.
        val rect = ImageTransform.cropRect(300, 400, CropAspect.Wide)
        assertEquals(300, rect.width)
        assertEquals(168, rect.height)
        assertEquals(0, rect.left)
        assertEquals(116, rect.top)
    }

    @Test
    fun `portrait crop fits a wide frame`() {
        val rect = ImageTransform.cropRect(640, 480, CropAspect.Portrait)
        assertEquals(360, rect.width)
        assertEquals(480, rect.height)
        assertEquals(140, rect.left)
        assertEquals(0, rect.top)
    }

    @Test
    fun `crop never exceeds the source`() {
        // надвузький кадр проти квадрата: прямокутник не може вилізти за краї
        val rect = ImageTransform.cropRect(10, 1000, CropAspect.Square)
        assertTrue(rect.width <= 10)
        assertTrue(rect.height <= 1000)
        assertTrue(rect.width >= 1 && rect.height >= 1)
    }

    @Test
    fun `result applies the turn before the crop`() {
        // 400x300 + 90° = 300x400, квадрат по висоті дає 300x300
        val bounds = ImageTransform.resultBounds(400, 300, EditParams(quarterTurns = 1, aspect = CropAspect.Square))
        assertEquals(Bounds(300, 300), bounds)
    }

    @Test
    fun `matrix scales around mid grey`() {
        val matrix = ImageTransform.colorMatrixValues(brightness = 0f, contrast = 1f)
        assertEquals(2f, matrix[0], 0f)
        // (in - 127.5)·2 + 127.5 → зсув 127.5·(1-2) = -127.5
        assertEquals(-ImageTransform.MID_GREY, matrix[4], 0f)
        assertEquals(1f, matrix[18], 0f)
        assertEquals(0f, matrix[1], 0f)
    }

    @Test
    fun `brightness shifts without touching the scale`() {
        val matrix = ImageTransform.colorMatrixValues(brightness = 0.5f, contrast = 0f)
        assertEquals(1f, matrix[0], 0f)
        assertEquals(0.5f * ImageTransform.FULL_SCALE, matrix[4], 0.001f)
    }

    @Test
    fun `values outside the sliders are clamped`() {
        val matrix = ImageTransform.colorMatrixValues(brightness = 9f, contrast = 9f)
        val capped = ImageTransform.colorMatrixValues(brightness = 1f, contrast = 1f)
        assertEquals(capped[0], matrix[0], 0f)
        assertEquals(capped[4], matrix[4], 0f)
    }

    @Test
    fun `no edits means no re-encode`() {
        assertTrue(EditParams().untouched)
        assertTrue(EditParams(quarterTurns = 4).untouched)
        assertFalse(EditParams(quarterTurns = 1).untouched)
        assertFalse(EditParams(aspect = CropAspect.Square).untouched)
        assertFalse(EditParams(brightness = 0.1f).untouched)
        assertFalse(EditParams(contrast = -0.1f).untouched)
    }

    @Test
    fun `preview sample size never enlarges`() {
        assertEquals(1, ImageTransform.previewSampleSize(1200, 900, longEdge = 2000))
        assertEquals(2, ImageTransform.previewSampleSize(4000, 3000, longEdge = 2000))
        assertEquals(4, ImageTransform.previewSampleSize(8000, 6000, longEdge = 2000))
    }
}
