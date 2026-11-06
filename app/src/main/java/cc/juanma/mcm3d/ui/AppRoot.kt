package cc.juanma.mcm3d.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import cc.juanma.mcm3d.AppViewModel
import cc.juanma.mcm3d.R
import cc.juanma.mcm3d.Screen
import cc.juanma.mcm3d.diagnostics.DiagnosticsScreen
import cc.juanma.mcm3d.outing.OutingScreen
import cc.juanma.mcm3d.recording.RecordingService
import cc.juanma.mcm3d.routes.RouteDetailScreen
import cc.juanma.mcm3d.home.HomeScreen
import cc.juanma.mcm3d.sessions.SessionImportScreen

@Composable
fun AppRoot(app: AppViewModel) {
  val snackbar = remember { SnackbarHostState() }
  val resources = LocalResources.current
  LaunchedEffect(app, resources) {
    app.messages.collect { snackbar.showSnackbar(resources.getString(it.text)) }
  }
  val startOuting = rememberOutingStarter(app)

  NavDisplay(
    backStack = app.backStack,
    onBack = { app.back() },
    entryDecorators = listOf(
      rememberSaveableStateHolderNavEntryDecorator(),
      rememberViewModelStoreNavEntryDecorator(),
    ),
    entryProvider = entryProvider {
      entry<Screen.Routes> {
        HomeScreen(
          app = app,
          snackbar = snackbar,
          onNewOuting = { startOuting(null) },
        )
      }
      entry<Screen.RouteDetail> { key ->
        RouteDetailScreen(
          routeId = key.routeId,
          app = app,
          snackbar = snackbar,
          onFollow = { startOuting(key.routeId) },
        )
      }
      entry<Screen.Outing> { OutingScreen(app = app, snackbar = snackbar) }
      entry<Screen.Diagnostics> { DiagnosticsScreen(onBack = app::back) }
      entry<Screen.SessionImport> { SessionImportScreen(app = app, snackbar = snackbar) }
    },
  )
}

/** Asks for the permissions an outing needs and starts it; precise location is the only mandatory one. */
@Composable
private fun rememberOutingStarter(app: AppViewModel): (followRouteId: Long?) -> Unit {
  val context = LocalContext.current
  val pendingFollow = remember { mutableLongStateOf(NO_FOLLOW) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED
    if (granted) {
      RecordingService.startOuting(context, pendingFollow.longValue.takeIf { it != NO_FOLLOW })
      app.openOuting()
    } else {
      app.show(R.string.outing_permission_needed)
    }
  }
  return { follow ->
    pendingFollow.longValue = follow ?: NO_FOLLOW
    launcher.launch(outingPermissions())
  }
}

private const val NO_FOLLOW = -1L

private fun outingPermissions(): Array<String> = buildList {
  add(Manifest.permission.ACCESS_FINE_LOCATION)
  add(Manifest.permission.ACCESS_COARSE_LOCATION)
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
}.toTypedArray()
