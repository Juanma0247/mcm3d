package cc.juanma.mcm3d.routes

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cc.juanma.mcm3d.AppViewModel
import cc.juanma.mcm3d.R
import cc.juanma.mcm3d.Screen
import cc.juanma.mcm3d.appGraph
import cc.juanma.mcm3d.data.ElevationSource
import cc.juanma.mcm3d.data.RouteEntity
import cc.juanma.mcm3d.data.RouteGeometry
import cc.juanma.mcm3d.data.RouteSource
import cc.juanma.mcm3d.recording.OutingStatus
import cc.juanma.mcm3d.ui.components.AppIcon
import cc.juanma.mcm3d.ui.components.BackButton
import cc.juanma.mcm3d.ui.components.BottomActions
import cc.juanma.mcm3d.ui.components.ElevationChart
import cc.juanma.mcm3d.ui.components.LargeButtonHeight
import cc.juanma.mcm3d.ui.components.MapLine
import cc.juanma.mcm3d.ui.components.MapMarker
import cc.juanma.mcm3d.ui.components.MarkerKind
import cc.juanma.mcm3d.ui.components.MetricRow
import cc.juanma.mcm3d.ui.components.RouteMap
import cc.juanma.mcm3d.ui.components.SectionCard
import cc.juanma.mcm3d.ui.components.SectionNote
import cc.juanma.mcm3d.ui.components.StatGrid
import cc.juanma.mcm3d.ui.formatAscent
import cc.juanma.mcm3d.ui.formatDate
import cc.juanma.mcm3d.ui.formatDescent
import cc.juanma.mcm3d.ui.formatDistance
import cc.juanma.mcm3d.ui.formatElevation
import cc.juanma.mcm3d.ui.formatHoursMinutes
import cc.juanma.mcm3d.ui.theme.Palette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(routeId: Long, app: AppViewModel, snackbar: SnackbarHostState, onFollow: () -> Unit) {
  val context = LocalContext.current
  val viewModel: RouteDetailViewModel = viewModel(
    factory = viewModelFactory { initializer { RouteDetailViewModel(routeId, context.appGraph.routes) } },
  )
  val state by viewModel.route.collectAsStateWithLifecycle()
  val geometry by viewModel.geometry.collectAsStateWithLifecycle()
  val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
  val outing by OutingStatus.state.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  var menuOpen by remember { mutableStateOf(false) }
  var renaming by rememberSaveable { mutableStateOf(false) }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  var deleted by remember { mutableStateOf(false) }

  LaunchedEffect(state) {
    if (state is DetailRoute.Missing) {
      if (!deleted) app.show(R.string.route_missing)
      app.close(Screen.RouteDetail(routeId))
    }
  }
  val route = (state as? DetailRoute.Loaded)?.route
  val chooserTitle = stringResource(R.string.detail_export_chooser)

  fun share() {
    scope.launch {
      val file = viewModel.exportGpx()
      if (file == null) {
        app.show(R.string.detail_export_failed)
        return@launch
      }
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
      val send = Intent(Intent.ACTION_SEND)
        .setType("application/gpx+xml")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      send.clipData = ClipData.newRawUri(file.name, uri)
      context.startActivity(Intent.createChooser(send, chooserTitle))
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbar) },
    topBar = {
      TopAppBar(
        navigationIcon = { BackButton(app::back) },
        title = { Text(route?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
          if (route != null && !route.recording) {
            IconButton(onClick = ::share) { AppIcon(R.drawable.ic_share, stringResource(R.string.detail_export)) }
            IconButton(onClick = { menuOpen = true }) { AppIcon(R.drawable.ic_more, stringResource(R.string.action_more)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_rename)) },
                leadingIcon = { AppIcon(R.drawable.ic_edit, null) },
                onClick = {
                  menuOpen = false
                  renaming = true
                },
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_delete)) },
                leadingIcon = { AppIcon(R.drawable.ic_delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                  menuOpen = false
                  confirmDelete = true
                },
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    bottomBar = {
      if (route != null && !route.recording) {
        BottomActions {
          if (outing.active) {
            OutlinedButton(
              onClick = app::openOuting,
              modifier = Modifier
                .weight(1f)
                .height(LargeButtonHeight),
            ) { Text(stringResource(R.string.detail_view_outing)) }
          } else {
            Button(
              onClick = onFollow,
              modifier = Modifier
                .weight(1f)
                .height(LargeButtonHeight),
            ) {
              AppIcon(R.drawable.ic_play, null, tint = MaterialTheme.colorScheme.onPrimary)
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.detail_follow))
            }
          }
        }
      }
    },
  ) { padding ->
    if (route == null) return@Scaffold
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (route.recording) item(key = "recording") { SectionNote(stringResource(R.string.detail_recording_note)) }
      geometry?.let { shape ->
        item(key = "map") {
          RouteMap(
            lines = listOf(MapLine(shape.lats, shape.lons, shape.size, Palette.Accent, 4.dp)),
            markers = routeMarkers(shape, waypoints.map { it.waypoint.lat to it.waypoint.lon }),
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(1.15f),
          )
        }
      }
      item(key = "stats") { SectionCard(stringResource(R.string.section_map)) { StatGrid(statItems(route, waypoints.size)) } }
      item(key = "profile") { ProfileSection(route, geometry) }
      if (waypoints.isNotEmpty()) {
        item(key = "waypoints") {
          SectionCard(stringResource(R.string.section_waypoints)) {
            waypoints.forEach { row ->
              val location = row.location
              val supporting = when {
                location == null -> null
                location.offRouteM > OFF_ROUTE_NOTE_M -> stringResource(
                  R.string.waypoint_off_route,
                  formatDistance(location.alongM),
                  formatDistance(location.offRouteM),
                )
                else -> stringResource(R.string.waypoint_along, formatDistance(location.alongM))
              }
              MetricRow(
                label = row.waypoint.name.ifBlank { stringResource(R.string.waypoint_unnamed) },
                value = row.waypoint.elevationM?.let(::formatElevation) ?: "",
                supporting = supporting,
              )
            }
          }
        }
      }
      item(key = "info") {
        SectionCard(stringResource(R.string.section_info)) {
          MetricRow(
            stringResource(R.string.info_source),
            if (route.source == RouteSource.IMPORTED) {
              stringResource(R.string.info_source_imported, route.fileFormat.orEmpty())
            } else {
              stringResource(R.string.info_source_recorded)
            },
          )
          MetricRow(stringResource(R.string.info_created), formatDate(route.createdAtMs))
          MetricRow(stringResource(R.string.info_points), route.pointCount.toString())
          if (route.elevationSource == ElevationSource.MODEL) SectionNote(stringResource(R.string.about_elevation_attribution))
        }
      }
    }
  }

  if (renaming && route != null) {
    var name by rememberSaveable { mutableStateOf(route.name) }
    AlertDialog(
      onDismissRequest = { renaming = false },
      title = { Text(stringResource(R.string.detail_rename)) },
      text = {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it.take(120) },
          label = { Text(stringResource(R.string.detail_rename_label)) },
          singleLine = true,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.rename(name)
            renaming = false
          },
          enabled = name.isNotBlank(),
        ) { Text(stringResource(R.string.action_save)) }
      },
      dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(stringResource(R.string.detail_delete_title)) },
      text = { Text(stringResource(R.string.detail_delete_body)) },
      confirmButton = {
        Button(
          onClick = {
            confirmDelete = false
            deleted = true
            viewModel.delete {}
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.action_delete)) }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun ProfileSection(route: RouteEntity, geometry: RouteGeometry?) {
  val note = stringResource(
    when (route.elevationSource) {
      ElevationSource.PENDING -> R.string.elevation_note_pending
      ElevationSource.MODEL -> R.string.elevation_note_model
      ElevationSource.FILE -> R.string.elevation_note_file
      ElevationSource.RECORDED -> R.string.elevation_note_recorded
      ElevationSource.NONE -> R.string.elevation_note_none
    },
  )
  SectionCard(stringResource(R.string.section_profile)) {
    if (geometry != null && geometry.hasElevation) {
      ElevationChart(
        distances = geometry.distances,
        elevations = geometry.elevations,
        modifier = Modifier
          .fillMaxWidth()
          .height(170.dp)
          .padding(vertical = 8.dp),
      )
    }
    SectionNote(note, color = if (route.elevationSource == ElevationSource.PENDING) Palette.Warning else androidx.compose.ui.graphics.Color.Unspecified)
  }
}

@Composable
private fun statItems(route: RouteEntity, waypointCount: Int): List<Pair<String, String>> = buildList {
  val na = stringResource(R.string.not_available)
  add(stringResource(R.string.stat_distance) to formatDistance(route.distanceM))
  if (route.source == RouteSource.RECORDED) {
    add(stringResource(R.string.stat_duration) to (route.durationMs?.let(::formatHoursMinutes) ?: na))
  }
  add(stringResource(R.string.stat_ascent) to (route.ascentM?.let(::formatAscent) ?: na))
  add(stringResource(R.string.stat_descent) to (route.descentM?.let(::formatDescent) ?: na))
  add(stringResource(R.string.stat_max_elevation) to (route.maxElevationM?.let(::formatElevation) ?: na))
  add(stringResource(R.string.stat_min_elevation) to (route.minElevationM?.let(::formatElevation) ?: na))
  if (waypointCount > 0) add(stringResource(R.string.stat_waypoints) to waypointCount.toString())
}

private fun routeMarkers(shape: RouteGeometry, waypoints: List<Pair<Double, Double>>): List<MapMarker> = buildList {
  waypoints.forEach { (lat, lon) -> add(MapMarker(lat, lon, MarkerKind.WAYPOINT)) }
  add(MapMarker(shape.lats.last(), shape.lons.last(), MarkerKind.END))
  add(MapMarker(shape.lats.first(), shape.lons.first(), MarkerKind.START))
}

private const val OFF_ROUTE_NOTE_M = 50.0
