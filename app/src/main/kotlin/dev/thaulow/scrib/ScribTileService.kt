package dev.thaulow.scrib

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

class ScribTileService : TileService() {
  override fun onClick() {
    val intent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(MainActivity.EXTRA_TILE_LAUNCH, true)
      }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val pending =
        PendingIntent.getActivity(
          this,
          0,
          intent,
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
      startActivityAndCollapse(pending)
    } else {
      @Suppress("DEPRECATION")
      startActivityAndCollapse(intent)
    }
  }
}
