package cc.juanma.mcm3d.outing

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
import cc.juanma.mcm3d.data.RouteGeometry
import cc.juanma.mcm3d.data.RouteRepository
import cc.juanma.mcm3d.recording.OutingState
import cc.juanma.mcm3d.recording.OutingStatus
import cc.juanma.mcm3d.recording.RecordingMode
import cc.juanma.mcm3d.recording.RecordingService
import cc.juanma.mcm3d.recording.Trace
import cc.juanma.mcm3d.ui.components.AppIcon
import cc.juanma.mcm3d.ui.components.BackButton
import cc.juanma.mcm3d.ui.components.BottomActions
import cc.juanma.mcm3d.ui.components.ElevationChart
import cc.juanma.mcm3d.ui.components.LargeButtonHeight
import cc.juanma.mcm3d.ui.components.MapLine
import cc.juanma.mcm3d.ui.components.MapMarker
import cc.juanma.mcm3d.ui.components.MarkerKind
import cc.juanma.mcm3d.ui.components.RouteMap
import cc.juanma.mcm3d.ui.components.SectionCard
import cc.juanma.mcm3d.ui.components.SectionNote
import cc.juanma.mcm3d.ui.components.StatGrid
import cc.juanma.mcm3d.ui.components.StatusBadge
import cc.juanma.mcm3d.ui.components.StatusLevel
import cc.juanma.mcm3d.ui.formatAscent
import cc.juanma.mcm3d.ui.formatClock
import cc.juanma.mcm3d.ui.formatDistance
import cc.juanma.mcm3d.ui.formatElevation
import cc.juanma.mcm3d.ui.formatNumber
import cc.juanma.mcm3d.ui.theme.HeroValueStyle
import cc.juanma.mcm3d.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class OutingViewModel(routes: RouteRepository) : ViewModel() {
  val follow: StateFlow<RouteGeometry?> = OutingStatus.state
    .map { it.followRouteId }
    .distinctUntilChanged()
    .map { id -> id?.let { routes.loadGeometry(it) } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutingScreen(app: AppViewModel, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val viewModel: OutingViewModel = viewModel(
    factory = viewModelFactory { initializer { OutingViewModel(context.appGraph.routes) } },
  )
  val state by OutingStatus.state.collectAsStateWithLifecycle()
  val trace by OutingStatus.trace.collectAsStateWithLifecycle()
  val follow by viewModel.follow.collectAsStateWithLifecycle()
  var confirmStop by rememberSaveable { mutableStateOf(false) }
  val recordedRouteId = remember { mutableLongStateOf(OutingState.NO_ROUTE) }

  // When the outing ends (here or from the notification) show what was recorded.
  LaunchedEffect(state.active, state.routeId) {
    if (state.active) {
      if (state.routeId != OutingState.NO_ROUTE) recordedRouteId.longValue = state.routeId
    } else if (recordedRouteId.longValue != OutingState.NO_ROUTE) {
      app.replace(Screen.Outing, Screen.RouteDetail(recordedRouteId.longValue))
    } else {
      app.close(Screen.Outing)
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbar) },
    topBar = {
      TopAppBar(
        navigationIcon = { BackButton(app::back) },
        title = {
          Column {
            Text(stringResource(R.string.outing_title), style = MaterialTheme.typography.titleLarge)
            Text(
              state.followRouteName?.let { stringResource(R.string.outing_following, it) } ?: stringResource(R.string.outing_free),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    bottomBar = {
      BottomActions {
        OutlinedButton(
          onClick = { RecordingService.mark(context) },
          enabled = state.active && !state.starting,
          modifier = Modifier
            .weight(1f)
            .height(LargeButtonHeight),
        ) {
          AppIcon(R.drawable.ic_flag, null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.recording_mark))
        }
        Button(
          onClick = { confirmStop = true },
          enabled = state.active,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier
            .weight(1f)
            .height(LargeButtonHeight),
        ) {
          AppIcon(R.drawable.ic_stop, null, tint = MaterialTheme.colorScheme.onError)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.outing_finish))
        }
      }
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "status") { StatusRow(state) }
      if (state.starting) return@LazyColumn
      state.progress?.let { progress ->
        item(key = "progress") {
          SectionCard(stringResource(R.string.outing_remaining)) {
            Text(formatDistance(progress.remainingM), style = HeroValueStyle)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
              progress = { (progress.alongM / progress.totalM).toFloat().coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                stringResource(R.string.outing_progress, formatDistance(progress.alongM), formatDistance(progress.totalM)),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
              )
              if (state.offRoute) {
                StatusBadge(stringResource(R.string.outing_off_route, formatDistance(progress.offRouteM)), StatusLevel.CRITICAL)
              } else {
                StatusBadge(stringResource(R.string.outing_on_route), StatusLevel.GOOD)
              }
            }
          }
        }
      }
      item(key = "stats") {
        val elapsed by produceState(0L, state.startElapsedMs) {
          while (true) {
            value = SystemClock.elapsedRealtime() - state.startElapsedMs
            delay(1_000L)
          }
        }
        val na = stringResource(R.string.not_available)
        SectionCard(stringResource(R.string.section_map)) {
          StatGrid(
            listOf(
              stringResource(R.string.outing_time) to formatClock(elapsed),
              stringResource(R.string.outing_distance) to formatDistance(state.distanceM),
              stringResource(R.string.outing_altitude) to (state.altitudeM?.let(::formatElevation) ?: na),
              stringResource(R.string.outing_ascent) to formatAscent(state.ascentM),
            ),
          )
          if (state.marks > 0) SectionNote(stringResource(R.string.outing_marks, state.marks))
        }
      }
      item(key = "map") {
        RouteMap(
          lines = mapLines(follow, trace),
          markers = buildList {
            follow?.let { add(MapMarker(it.lats.first(), it.lons.first(), MarkerKind.START)) }
            val lat = state.lastLat
            val lon = state.lastLon
            if (lat != null && lon != null) add(MapMarker(lat, lon, MarkerKind.POSITION))
          },
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f),
        )
      }
      val shape = follow
      if (shape != null && shape.hasElevation) {
        item(key = "profile") {
          SectionCard(stringResource(R.string.section_profile)) {
            ElevationChart(
              distances = shape.distances,
              elevations = shape.elevations,
              markerAlongM = state.progress?.alongM,
              modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(vertical = 8.dp),
            )
          }
        }
      }
    }
  }

  if (confirmStop) {
    AlertDialog(
      onDismissRequest = { confirmStop = false },
      title = { Text(stringResource(R.string.outing_finish_title)) },
      text = { Text(stringResource(R.string.outing_finish_body)) },
      confirmButton = {
        Button(
          onClick = {
            confirmStop = false
            RecordingService.stopOuting(context)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.outing_finish)) }
      },
      dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun StatusRow(state: OutingState) {
  val (text, level) = when {
    state.starting -> stringResource(R.string.outing_starting) to StatusLevel.NEUTRAL
    state.mode == RecordingMode.STILL -> stringResource(R.string.outing_status_paused) to StatusLevel.WARNING
    state.hasFix && state.accuracyM != null -> pluralStringResource(
      R.plurals.outing_status_fix,
      state.satellitesUsed,
      "${formatNumber(state.accuracyM, 0)} m",
      state.satellitesUsed,
    ) to StatusLevel.GOOD
    else -> stringResource(R.string.outing_status_searching) to StatusLevel.WARNING
  }
  Column {
    StatusBadge(text, level)
    if (state.mode == RecordingMode.STILL) SectionNote(stringResource(R.string.outing_paused_hint))
  }
}

private fun mapLines(follow: RouteGeometry?, trace: Trace): List<MapLine> = buildList {
  follow?.let { add(MapLine(it.lats, it.lons, it.size, Palette.Accent.copy(alpha = 0.85f), 4.dp)) }
  if (trace.size >= 2) add(MapLine(trace.lats, trace.lons, trace.size, Palette.Trace, 3.dp))
}
