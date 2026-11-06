package cc.juanma.mcm3d.home

import android.Manifest
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cc.juanma.mcm3d.AppViewModel
import cc.juanma.mcm3d.R
import cc.juanma.mcm3d.Screen
import cc.juanma.mcm3d.appGraph
import cc.juanma.mcm3d.core.route.Bounds
import cc.juanma.mcm3d.data.ElevationSource
import cc.juanma.mcm3d.data.RouteEntity
import cc.juanma.mcm3d.data.RouteGeometry
import cc.juanma.mcm3d.data.RouteRepository
import cc.juanma.mcm3d.data.RouteSource
import cc.juanma.mcm3d.recording.OutingState
import cc.juanma.mcm3d.recording.OutingStatus
import cc.juanma.mcm3d.sensing.hasFineLocation
import cc.juanma.mcm3d.sensing.rememberHeading
import cc.juanma.mcm3d.sensing.rememberUserLocation
import cc.juanma.mcm3d.ui.components.AppIcon
import cc.juanma.mcm3d.ui.components.LargeButtonHeight
import cc.juanma.mcm3d.ui.components.StatusBadge
import cc.juanma.mcm3d.ui.components.StatusLevel
import cc.juanma.mcm3d.ui.formatAscent
import cc.juanma.mcm3d.ui.formatClock
import cc.juanma.mcm3d.ui.formatDate
import cc.juanma.mcm3d.ui.formatDistance
import cc.juanma.mcm3d.ui.map.MapPolyline
import cc.juanma.mcm3d.ui.map.MapPosition
import cc.juanma.mcm3d.ui.map.MapView
import cc.juanma.mcm3d.ui.map.rememberMapState
import cc.juanma.mcm3d.ui.theme.Palette
import cc.juanma.mcm3d.zone.ZoneWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(private val routes: RouteRepository) : ViewModel() {
  val list: StateFlow<List<RouteEntity>?> =
    routes.observeRoutes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** Shapes of the most recent routes for the map, reloaded when any track changes. */
  val shapes: StateFlow<List<RouteGeometry>> = routes.observeRoutes()
    .map { all -> all.filter { !it.recording }.take(MAX_SHAPES).mapNotNull { routes.loadGeometry(it.id) } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private companion object {
    const val MAX_SHAPES = 40
  }
}

private val DEFAULT_CENTER = Pair(5.0689, -75.5174)

private val IMPORT_TYPES = arrayOf(
  "application/gpx+xml",
  "application/vnd.google-earth.kml+xml",
  "application/vnd.google-earth.kmz",
  "application/octet-stream",
  "application/xml",
  "text/xml",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: AppViewModel, snackbar: SnackbarHostState, onNewOuting: () -> Unit) {
  val context = LocalContext.current
  val viewModel: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(context.appGraph.routes) } })
  val routes by viewModel.list.collectAsStateWithLifecycle()
  val shapes by viewModel.shapes.collectAsStateWithLifecycle()
  val importing by app.importing.collectAsStateWithLifecycle()
  val outing by OutingStatus.state.collectAsStateWithLifecycle()

  var permission by remember { mutableStateOf(hasFineLocation(context)) }
  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    permission = hasFineLocation(context)
  }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(app::importRoute) }

  val location by rememberUserLocation(permission)
  val heading by rememberHeading(location)
  val mapState = rememberMapState(DEFAULT_CENTER.first, DEFAULT_CENTER.second, 4f)
  var centeredOnUser by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(location) {
    val here = location ?: return@LaunchedEffect
    if (!centeredOnUser) {
      centeredOnUser = true
      mapState.metersPerDp = 1.5f
      mapState.following = true
      ZoneWorker.ensureAround(context, here.latitude, here.longitude)
    }
    if (mapState.following) mapState.centerOn(here.latitude, here.longitude)
  }
  LaunchedEffect(shapes, permission) {
    if (!permission && !centeredOnUser && shapes.isNotEmpty()) mapState.fit(boundsOf(shapes))
  }

  val sheetState = rememberBottomSheetScaffoldState()
  BottomSheetScaffold(
    scaffoldState = sheetState,
    sheetPeekHeight = 236.dp,
    sheetContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    snackbarHost = { SnackbarHost(snackbar) },
    sheetContent = {
      RoutesSheet(
        routes = routes,
        importing = importing,
        outingActive = outing.active,
        onNewOuting = onNewOuting,
        onImportFile = { picker.launch(IMPORT_TYPES) },
        onFromSession = { app.open(Screen.SessionImport) },
        onOpen = { app.open(Screen.RouteDetail(it)) },
      )
    },
  ) {
    Box(Modifier.fillMaxSize()) {
      MapView(
        state = mapState,
        modifier = Modifier.fillMaxSize(),
        lines = remember(shapes) { shapes.map { MapPolyline(it.lats, it.lons, it.size, Palette.Accent, 4f) } },
        position = location?.toMapPosition(heading),
      )
      Column(
        Modifier
          .statusBarsPadding()
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f), shape = RoundedCornerShape(50)) {
            Text(
              "MCM3D",
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
          }
          Spacer(Modifier.weight(1f))
          if (location != null && !mapState.following) {
            MapButton(R.drawable.ic_locate, stringResource(R.string.map_recenter)) {
              mapState.following = true
              location?.let { mapState.centerOn(it.latitude, it.longitude) }
            }
            Spacer(Modifier.width(8.dp))
          }
          MapButton(R.drawable.ic_diagnostics, stringResource(R.string.routes_diagnostics)) { app.open(Screen.Diagnostics) }
        }
        if (outing.active) OutingBanner(outing, onOpen = app::openOuting)
        if (!permission) {
          Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(14.dp)) {
              Text(stringResource(R.string.home_location_needed), style = MaterialTheme.typography.bodyMedium)
              TextButton(onClick = {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
              }) { Text(stringResource(R.string.home_location_button)) }
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun MapButton(icon: Int, description: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
  FilledTonalIconButton(
    onClick = onClick,
    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
    shape = CircleShape,
  ) { AppIcon(icon, description, tint = tint) }
}

internal fun Location.toMapPosition(heading: Float?) =
  MapPosition(latitude, longitude, if (hasAccuracy()) accuracy else null, heading)

private fun boundsOf(shapes: List<RouteGeometry>): Bounds {
  var minLat = Double.MAX_VALUE
  var minLon = Double.MAX_VALUE
  var maxLat = -Double.MAX_VALUE
  var maxLon = -Double.MAX_VALUE
  for (shape in shapes) for (i in 0 until shape.size) {
    minLat = minOf(minLat, shape.lats[i])
    maxLat = maxOf(maxLat, shape.lats[i])
    minLon = minOf(minLon, shape.lons[i])
    maxLon = maxOf(maxLon, shape.lons[i])
  }
  return Bounds(minLat, minLon, maxLat, maxLon)
}

@Composable
private fun RoutesSheet(
  routes: List<RouteEntity>?,
  importing: Boolean,
  outingActive: Boolean,
  onNewOuting: () -> Unit,
  onImportFile: () -> Unit,
  onFromSession: () -> Unit,
  onOpen: (Long) -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth()) {
    Row(
      Modifier.padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Button(
        onClick = onNewOuting,
        enabled = !outingActive,
        modifier = Modifier
          .weight(1f)
          .height(LargeButtonHeight),
      ) {
        AppIcon(R.drawable.ic_play, null, tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.routes_new_outing))
      }
      Box(Modifier.weight(1f)) {
        OutlinedButton(
          onClick = { menuOpen = true },
          enabled = !importing,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
          modifier = Modifier
            .fillMaxWidth()
            .height(LargeButtonHeight),
        ) {
          AppIcon(R.drawable.ic_import, null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.home_add_route))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.home_add_file)) },
            onClick = {
              menuOpen = false
              onImportFile()
            },
          )
          DropdownMenuItem(
            text = { Text(stringResource(R.string.home_add_session)) },
            onClick = {
              menuOpen = false
              onFromSession()
            },
          )
        }
      }
    }
    if (importing) {
      LinearProgressIndicator(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
      )
    }
    Text(
      stringResource(R.string.routes_title),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
    val list = routes.orEmpty()
    if (routes != null && list.isEmpty()) {
      Text(
        stringResource(R.string.home_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
      )
    }
    LazyColumn(
      contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
      modifier = Modifier.height(420.dp),
    ) {
      items(list, key = { it.id }) { route -> RouteRow(route) { onOpen(route.id) } }
    }
  }
}

@Composable
private fun RouteRow(route: RouteEntity, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      AppIcon(
        if (route.source == RouteSource.RECORDED) R.drawable.ic_record else R.drawable.ic_terrain,
        null,
        tint = Palette.Accent,
      )
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(route.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val details = buildList {
          add(formatDistance(route.distanceM))
          route.ascentM?.takeIf { it >= 1 }?.let { add(formatAscent(it)) }
          add(formatDate(route.createdAtMs))
        }
        Text(
          details.joinToString(" · "),
          style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      when {
        route.recording -> StatusBadge(stringResource(R.string.route_badge_recording), StatusLevel.GOOD)
        route.elevationSource == ElevationSource.PENDING -> StatusBadge(stringResource(R.string.route_badge_elevation_pending), StatusLevel.WARNING)
      }
    }
  }
}

@Composable
private fun OutingBanner(outing: OutingState, onOpen: () -> Unit) {
  val elapsed by produceState(0L, outing.startElapsedMs) {
    while (true) {
      value = android.os.SystemClock.elapsedRealtime() - outing.startElapsedMs
      delay(1_000L)
    }
  }
  Surface(
    onClick = onOpen,
    color = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(stringResource(R.string.outing_banner_title), style = MaterialTheme.typography.titleSmall)
        Text(
          "${formatClock(elapsed)} · ${formatDistance(outing.distanceM)}",
          style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        )
      }
      Text(stringResource(R.string.outing_banner_open), style = MaterialTheme.typography.labelLarge)
    }
  }
}
