package com.quizlock.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Notification bar ka "Kids Lock" tile. Parent phone bachche ko dene se pehle
 * tap kare to chune hue apps lock. Band karna sirf app mein PIN se hota hai,
 * isliye chalu tile par tap karne se app khulta hai.
 */
class KidsTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val store = Store(this)
        val ready = store.premium && store.hasPin() && store.kidsApps.isNotEmpty()
        if (!store.kidsModeOn && ready) {
            store.kidsModeOn = true
            LockService.start(this)
            refresh()
        } else {
            openApp()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_OPEN, "kids")
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 3, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = Store(this).kidsModeOn
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) "On" else "Off"
        tile.updateTile()
    }
}
