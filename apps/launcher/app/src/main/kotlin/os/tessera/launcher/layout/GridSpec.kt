package os.tessera.launcher.layout

/**
 * Геометрія головного екрана.
 *
 * Значення взяті з профілю `5_by_6` у `res/xml/device_profiles.xml` бази
 * LineageOS 22.2 — той самий профіль, який ми робимо дефолтним build-time
 * підміною (див. docs/PIXEL-PARITY.md). Тримаємо їх одним обʼєктом, щоб
 * макет і поведінка не розʼїжджалися.
 */
data class GridSpec(
    val columns: Int,
    val rows: Int,
    val dockSlots: Int,
    val iconSizeDp: Int,
    val labelTextSp: Float,
    val drawerCellHeightDp: Int,
    val drawerBorderSpaceDp: Int,
) {
    val homeCapacity: Int get() = columns * rows

    companion object {
        val LARGE_PHONE = GridSpec(
            columns = 5,
            rows = 6,
            dockSlots = 5,
            iconSizeDp = 56,
            labelTextSp = 12f,
            drawerCellHeightDp = 104,
            drawerBorderSpaceDp = 16,
        )
    }
}

object HomeLayout {

    /**
     * Розкладає закріплені id по комірках row-major.
     * Повертає рівно [capacity] елементів; порожні комірки — null, вони
     * тримають місце в сітці, як на стоковому лаунчері.
     */
    fun place(pins: List<String>, capacity: Int): List<String?> {
        if (capacity <= 0) return emptyList()
        val taken = pins.take(capacity)
        return taken + List(capacity - taken.size) { null }
    }
}
