package cc.juanma.mcm3d

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import cc.juanma.mcm3d.ui.AppRoot
import cc.juanma.mcm3d.ui.theme.Mcm3dTheme

class MainActivity : ComponentActivity() {
  private val appViewModel: AppViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    if (savedInstanceState == null) handleIntent(intent)
    setContent {
      Mcm3dTheme {
        AppRoot(appViewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    when (intent?.action) {
      Intent.ACTION_VIEW -> intent.data?.let(appViewModel::importRoute)
      Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        ?.let(appViewModel::importRoute)
      ACTION_OPEN_OUTING -> appViewModel.openOuting()
    }
  }

  companion object {
    const val ACTION_OPEN_OUTING = "cc.juanma.mcm3d.OPEN_OUTING"
  }
}
