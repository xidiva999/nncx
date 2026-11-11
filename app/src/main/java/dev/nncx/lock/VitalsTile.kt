package dev.nncx.lock

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.nncx.devtools.Perf
import dev.nncx.settings.Settings

/**
 * The other surface the lock screen grants: the shade pulls down over the
 * keyguard, so a tile is reachable without unlocking. It shows the headline
 * number and toggles the readout.
 */
class VitalsTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val settings = Settings.get(this)
        settings.vitalsOnLock = !settings.vitalsOnLock
        VitalsService.sync(this)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = Settings.get(this).vitalsOnLock
        val perf = runCatching { Perf.sample(this) }.getOrNull()

        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "NNCx"
        tile.subtitle = perf?.let { "${it.batteryPct}% · ${it.thermal}" } ?: "vitals"
        tile.updateTile()
    }
}
