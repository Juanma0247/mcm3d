package cc.juanma.mcm3d.outing

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import cc.juanma.mcm3d.core.route.Instruction
import cc.juanma.mcm3d.core.route.RouteProgressTracker
import cc.juanma.mcm3d.core.route.TurnDetector
import cc.juanma.mcm3d.data.RouteGeometry
import cc.juanma.mcm3d.data.RouteRepository
import cc.juanma.mcm3d.home.MapButton
import cc.juanma.mcm3d.recording.OutingState
import cc.juanma.mcm3d.recording.OutingStatus
import cc.juanma.mcm3d.recording.RecordingMode
import cc.juanma.mcm3d.recording.RecordingService
import cc.juanma.mcm3d.recording.Trace
import cc.juanma.mcm3d.sensing.rememberHeading
import cc.juanma.mcm3d.ui.components.AppIcon
import cc.juanma.mcm3d.ui.components.BackButton
import cc.juanma.mcm3d.ui.components.LargeButtonHeight
import cc.juanma.mcm3d.ui.components.StatTile
import cc.juanma.mcm3d.ui.formatClock
import cc.juanma.mcm3d.ui.formatDistance
import cc.juanma.mcm3d.ui.formatElevation
import cc.juanma.mcm3d.ui.formatNumber
import cc.juanma.mcm3d.ui.instructionText
import cc.juanma.mcm3d.ui.map.MapMarker
import cc.juanma.mcm3d.ui.map.MapPolyline
import cc.juanma.mcm3d.ui.map.MapPosition
import cc.juanma.mcm3d.ui.map.MapView
import cc.juanma.mcm3d.ui.map.MarkerKind
import cc.juanma.mcm3d.ui.map.rememberMapState
import cc.juanma.mcm3d.ui.map.splitLine
import cc.juanma.mcm3d.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The followed route prepared for drawing: geometry, vertex lookup by distance and turn positions. */
class FollowShape(val geometry: RouteGeometry, val tracker: RouteProgressTracker, val turnPoints: List<Pair<Double, Double>>)

class OutingViewModel(routes: RouteRepository) : ViewModel() {
  val follow: StateFlow<FollowShape?> = OutingStatus.state
    .map { it.followRouteId }
    .distinctUntilChanged()
    .map { id ->
      id?.let { routes.loadGeometry(it) }?.let { g ->
        val tracker = RouteProgressTracker(g.lats, g.lons)
        FollowShape(g, tracker, TurnDetector.detect(tracker).map { tracker.pointAt(it.alongM) })
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun OutingScreen(app: AppViewModel, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val viewModel: OutingViewModel = viewModel(factory = viewModelFactory { initializer { OutingViewModel(context.appGraph.routes) } })
  val state by OutingStatus.state.collectAsStateWithLifecycle()
  val trace by OutingStatus.trace.collectAsStateWithLifecycle()
  val follow by viewModel.follow.collectAsStateWithLifecycle()
  var confirmStop by rememberSaveable { mutableStateOf(false) }
  var headingUp by rememberSaveable { mutableStateOf(true) }
  val recordedRouteId = remember { mutableLongStateOf(OutingState.NO_ROUTE) }

  val positionLat = state.positionLat
  val positionLon = state.positionLon
  val here = remember(positionLat, positionLon) {
    if (positionLat != null && positionLon != null) {
      android.location.Location("outing").apply {
        latitude = positionLat
        longitude = positionLon
      }
    } else {
      null
    }
  }
  val heading by rememberHeading(here)
  val start = follow?.geometry
  val mapState = rememberMapState(
    positionLat ?: start?.lats?.first() ?: 5.0689,
    positionLon ?: start?.lons?.first() ?: -75.5174,
    1.2f,
  )

  LaunchedEffect(state.active, state.routeId) {
    if (state.active) {
      if (state.routeId != OutingState.NO_ROUTE) recordedRouteId.longValue = state.routeId
    } else if (recordedRouteId.longValue != OutingState.NO_ROUTE) {
      app.replace(Screen.Outing, Screen.RouteDetail(recordedRouteId.longValue))
    } else {
      app.close(Screen.Outing)
    }
  }
  LaunchedEffect(positionLat, positionLon) {
    if (positionLat != null && positionLon != null && mapState.following) mapState.centerOn(positionLat, positionLon)
  }
  LaunchedEffect(heading, headingUp) {
    mapState.rotationDeg = if (headingUp) heading ?: 0f else 0f
  }
  LaunchedEffect(start) {
    if (positionLat == null && start != null) mapState.centerOn(start.lats.first(), start.lons.first())
  }

  Box(Modifier.fillMaxSize()) {
    MapView(
      state = mapState,
      modifier = Modifier.fillMaxSize(),
      lines = routeLines(follow, state, trace),
      markers = routeMarkers(follow),
      position = if (positionLat != null && positionLon != null) {
        MapPosition(positionLat, positionLon, state.accuracyM?.toFloat(), heading)
      } else {
        null
      },
      anchorY = 0.6f,
    )

    Column(
      Modifier
        .statusBarsPadding()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.Top) {
        Surface(color = Palette.Halo, shape = RoundedCornerShape(50)) { BackButton(app::back) }
        Spacer(Modifier.width(8.dp))
        InstructionCard(state, heading, Modifier.weight(1f))
      }
      Row {
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          MapButton(
            R.drawable.ic_navigation,
            stringResource(if (headingUp) R.string.map_north_up else R.string.map_heading_up),
            tint = if (headingUp) Palette.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
          ) { headingUp = !headingUp }
          if (!mapState.following && positionLat != null && positionLon != null) {
            MapButton(R.drawable.ic_locate, stringResource(R.string.map_recenter)) {
              mapState.following = true
              mapState.centerOn(positionLat, positionLon)
            }
          }
        }
      }
    }

    Column(Modifier.align(Alignment.BottomCenter)) {
      SnackbarHost(snackbar)
      BottomPanel(state, onMark = { RecordingService.mark(context) }, onStop = { confirmStop = true })
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

/** The one thing to do now, in large type, with an arrow toward where to walk relative to the phone. */
@Composable
private fun InstructionCard(state: OutingState, heading: Float?, modifier: Modifier = Modifier) {
  val resources = LocalResources.current
  val instruction = state.instruction
  val (title, detail) = when {
    state.starting -> stringResource(R.string.outing_starting) to null
    state.mode == RecordingMode.STILL -> stringResource(R.string.outing_paused_title) to stringResource(R.string.outing_paused_hint)
    !state.hasFix -> stringResource(R.string.outing_searching_title) to stringResource(R.string.outing_searching_detail)
    instruction != null -> instructionText(resources, instruction)
    else -> stringResource(R.string.outing_free_title) to stringResource(R.string.outing_free_detail)
  }
  val searching = state.starting || (!state.hasFix && state.mode == RecordingMode.MOVING)
  val alert = state.offRoute
  Surface(
    color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = if (alert) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface,
    shape = RoundedCornerShape(16.dp),
    shadowElevation = 6.dp,
    modifier = modifier,
  ) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
      when {
        searching -> CircularProgressIndicator(Modifier.size(44.dp), strokeWidth = 4.dp)
        state.hasFix && instruction != null && !instruction.bearingDeg.isNaN() && state.mode == RecordingMode.MOVING ->
          DirectionArrow(instruction.bearingDeg - (heading ?: 0f), if (alert) MaterialTheme.colorScheme.onError else Palette.Accent)
        else -> AppIcon(R.drawable.ic_record, null, Modifier.size(44.dp), tint = Palette.Accent)
      }
      Spacer(Modifier.width(14.dp))
      Column {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (state.hasFix && state.accuracyM != null && state.mode == RecordingMode.MOVING) {
          Text(
            "GPS ±${formatNumber(state.accuracyM, 0)} m",
            style = MaterialTheme.typography.labelMedium,
            color = if (alert) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun DirectionArrow(angleDeg: Double, color: androidx.compose.ui.graphics.Color) {
  Canvas(Modifier.size(44.dp)) {
    rotate(angleDeg.toFloat()) {
      val w = size.width
      val h = size.height
      val arrow = Path().apply {
        moveTo(w / 2, 0f)
        lineTo(w * 0.92f, h * 0.9f)
        lineTo(w / 2, h * 0.68f)
        lineTo(w * 0.08f, h * 0.9f)
        close()
      }
      drawPath(arrow, color)
    }
  }
}

@Composable
private fun BottomPanel(state: OutingState, onMark: () -> Unit, onStop: () -> Unit) {
  val elapsed by produceState(0L, state.startElapsedMs) {
    while (true) {
      value = SystemClock.elapsedRealtime() - state.startElapsedMs
      delay(1_000L)
    }
  }
  val na = stringResource(R.string.not_available)
  Surface(
    color = MaterialTheme.colorScheme.background,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      Modifier
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(stringResource(R.string.outing_time), formatClock(elapsed), Modifier.weight(1f))
        StatTile(stringResource(R.string.outing_walked), formatDistance(state.distanceM), Modifier.weight(1f))
        val progress = state.progress
        if (progress != null) {
          StatTile(stringResource(R.string.outing_remaining), formatDistance(progress.remainingM), Modifier.weight(1f))
        } else {
          StatTile(stringResource(R.string.outing_altitude), state.altitudeM?.let(::formatElevation) ?: na, Modifier.weight(1f))
        }
      }
      if (state.marks > 0) {
        Text(
          stringResource(R.string.outing_marks, state.marks),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
          onClick = onMark,
          enabled = state.active && !state.starting,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
          modifier = Modifier
            .weight(1f)
            .height(LargeButtonHeight),
        ) {
          AppIcon(R.drawable.ic_flag, null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.recording_mark))
        }
        Button(
          onClick = onStop,
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
    }
  }
}

@Composable
private fun routeLines(follow: FollowShape?, state: OutingState, trace: Trace): List<MapPolyline> {
  val split = follow?.let { f -> state.progress?.let { f.tracker.indexAt(it.alongM) } ?: 0 } ?: 0
  val routeParts = remember(follow, split) {
    follow?.let { splitLine(it.geometry.lats, it.geometry.lons, split, Palette.Walked, Palette.Accent, 7f) }.orEmpty()
  }
  val traced = remember(trace) {
    if (trace.size >= 2) MapPolyline(trace.lats, trace.lons, trace.size, Palette.Trace, 3.5f) else null
  }
  return routeParts + listOfNotNull(traced)
}

private fun routeMarkers(follow: FollowShape?): List<MapMarker> {
  val shape = follow ?: return emptyList()
  val g = shape.geometry
  return buildList {
    shape.turnPoints.forEach { (lat, lon) -> add(MapMarker(lat, lon, MarkerKind.TURN)) }
    add(MapMarker(g.lats.last(), g.lons.last(), MarkerKind.END))
    add(MapMarker(g.lats.first(), g.lons.first(), MarkerKind.START))
  }
}
