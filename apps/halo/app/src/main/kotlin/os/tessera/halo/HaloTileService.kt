package os.tessera.halo

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import os.tessera.halo.data.HaloPrefs

/**
 * Плитка Quick Settings для краю. Це другий, поруч із перемикачем в застосунку,
 * спосіб увімкнути/вимкнути Halo. Стан плитки — справжній, а не бажаний:
 * читаємо той самий `prefs`, з якого живе й слухач.
 */
class HaloTileService : TileService() {

    private val prefs by lazy { HaloPrefs(this) }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val newState = !prefs.isEnabled()
        prefs.setEnabled(newState)
        // Вимкнули — прохаємо слухача прибрати край, що ще горить, не чекаючи 3 с.
        if (!newState) {
            sendBroadcast(
                Intent(HaloListenerService.ACTION_STATE_CHANGED).setPackage(packageName),
            )
        }
        refresh()
    }

    private fun refresh() {
        val qs = qsTile ?: return
        qs.label = getString(R.string.halo_tile_label)
        qs.contentDescription = getString(R.string.halo_tile_description)
        qs.state = if (prefs.isEnabled()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qs.updateTile()
    }
}
