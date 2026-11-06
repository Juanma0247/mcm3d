package cc.juanma.mcm3d.routes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import cc.juanma.mcm3d.data.ElevationSource
import cc.juanma.mcm3d.data.RouteEntity
import cc.juanma.mcm3d.data.RouteRepository
import cc.juanma.mcm3d.data.RouteSource
import cc.juanma.mcm3d.recording.OutingState
import cc.juanma.mcm3d.recording.OutingStatus
import cc.juanma.mcm3d.ui.components.AppIcon
import cc.juanma.mcm3d.ui.components.BottomActions
import cc.juanma.mcm3d.ui.components.LargeButtonHeight
import cc.juanma.mcm3d.ui.components.StatusBadge
import cc.juanma.mcm3d.ui.components.StatusLevel
import cc.juanma.mcm3d.ui.formatAscent
import cc.juanma.mcm3d.ui.formatClock
import cc.juanma.mcm3d.ui.formatDate
import cc.juanma.mcm3d.ui.formatDescent
import cc.juanma.mcm3d.ui.formatDistance
import cc.juanma.mcm3d.ui.formatHoursMinutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class RoutesViewModel(routes: RouteRepository) : ViewModel() {
  val routes: StateFlow<List<RouteEntity>?> =
    routes.observeRoutes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

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
fun RoutesScreen(app: AppViewModel, snackbar: SnackbarHostState, onNewOuting: () -> Unit) {
  val context = LocalContext.current
  val viewModel: RoutesViewModel = viewModel(
    factory = viewModelFactory { initializer { RoutesViewModel(context.appGraph.routes) } },
  )
  val routes by viewModel.routes.collectAsStateWithLifecycle()
  val importing by app.importing.collectAsStateWithLifecycle()
  val outing by OutingStatus.state.collectAsStateWithLifecycle()
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let(app::importRoute)
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbar) },
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.routes_title), style = MaterialTheme.typography.titleLarge) },
        actions = {
          IconButton(onClick = { app.open(Screen.Diagnostics) }) {
            AppIcon(R.drawable.ic_diagnostics, stringResource(R.string.routes_diagnostics))
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    bottomBar = {
      BottomActions {
        OutlinedButton(
          onClick = { picker.launch(IMPORT_TYPES) },
          enabled = !importing,
          modifier = Modifier
            .weight(1f)
            .height(LargeButtonHeight),
        ) {
          AppIcon(R.drawable.ic_import, null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.routes_import))
        }
        Button(
          onClick = onNewOuting,
          enabled = !outing.active,
          modifier = Modifier
            .weight(1f)
            .height(LargeButtonHeight),
        ) {
          AppIcon(R.drawable.ic_record, null, tint = MaterialTheme.colorScheme.onPrimary)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.routes_new_outing))
        }
      }
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (importing) {
        item(key = "importing") {
          Column {
            Text(stringResource(R.string.routes_importing), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
          }
        }
      }
      if (outing.active) {
        item(key = "outing") { OutingBanner(outing, onOpen = app::openOuting) }
      }
      val list = routes
      if (list != null && list.isEmpty() && !importing) {
        item(key = "empty") { EmptyState() }
      }
      if (list != null) {
        items(list, key = { it.id }) { route ->
          RouteCard(route, onClick = { app.open(Screen.RouteDetail(route.id)) })
        }
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
    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(stringResource(R.string.outing_banner_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(
          "${formatClock(elapsed)} · ${formatDistance(outing.distanceM)}",
          style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        )
      }
      TextButton(onClick = onOpen) { Text(stringResource(R.string.outing_banner_open)) }
    }
  }
}

@Composable
private fun EmptyState() {
  Column(
    Modifier
      .fillMaxWidth()
      .padding(top = 48.dp, start = 8.dp, end = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AppIcon(R.drawable.ic_terrain, null, Modifier, tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.routes_empty_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
      stringResource(R.string.routes_empty_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun RouteCard(route: RouteEntity, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
      Text(route.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(4.dp))
      val stats = buildList {
        add(formatDistance(route.distanceM))
        route.ascentM?.let { add(formatAscent(it)) }
        route.descentM?.let { add(formatDescent(it)) }
        if (route.source == RouteSource.RECORDED) route.durationMs?.let { add(formatHoursMinutes(it)) }
      }
      Text(
        stats.joinToString("  ·  "),
        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(Modifier.height(4.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        val origin = if (route.source == RouteSource.IMPORTED) {
          stringResource(R.string.route_source_imported, route.fileFormat.orEmpty())
        } else {
          stringResource(R.string.route_source_recorded)
        }
        Text(
          "$origin · ${formatDate(route.createdAtMs)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        when {
          route.recording -> StatusBadge(stringResource(R.string.route_badge_recording), StatusLevel.GOOD)
          route.elevationSource == ElevationSource.PENDING ->
            StatusBadge(stringResource(R.string.route_badge_elevation_pending), StatusLevel.WARNING)
        }
      }
    }
  }
}
