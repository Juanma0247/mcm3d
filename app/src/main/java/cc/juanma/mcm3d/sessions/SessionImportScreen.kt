package cc.juanma.mcm3d.sessions

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import cc.juanma.mcm3d.core.route.cumulativeDistances
import cc.juanma.mcm3d.core.session.SessionCsv
import cc.juanma.mcm3d.core.session.SessionTrack
import cc.juanma.mcm3d.data.RouteRepository
import cc.juanma.mcm3d.ui.components.BackButton
import cc.juanma.mcm3d.ui.components.LargeButtonHeight
import cc.juanma.mcm3d.ui.formatDistance
import cc.juanma.mcm3d.ui.formatHoursMinutes
import cc.juanma.mcm3d.ui.map.MapMarker
import cc.juanma.mcm3d.ui.map.MapPolyline
import cc.juanma.mcm3d.ui.map.MapView
import cc.juanma.mcm3d.ui.map.MarkerKind
import cc.juanma.mcm3d.ui.map.rememberMapState
import cc.juanma.mcm3d.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class SessionFile(val file: File, val label: String)

class LoadedSession(val source: SessionFile, val track: SessionTrack, val distances: DoubleArray) {
  val lats = DoubleArray(track.points.size) { track.points[it].lat }
  val lons = DoubleArray(track.points.size) { track.points[it].lon }
}

class SessionImportViewModel(context: Context, private val routes: RouteRepository) : ViewModel() {
  private val dir = File(context.getExternalFilesDir(null), "sessions")
  private val _sessions = MutableStateFlow<List<SessionFile>?>(null)
  val sessions: StateFlow<List<SessionFile>?> = _sessions.asStateFlow()
  private val _loaded = MutableStateFlow<LoadedSession?>(null)
  val loaded: StateFlow<LoadedSession?> = _loaded.asStateFlow()
  private val _loading = MutableStateFlow(false)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()

  init {
    viewModelScope.launch {
      _sessions.value = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.startsWith("session_") && f.name.endsWith(".csv") }.orEmpty()
          .sortedByDescending { it.name }
          .map { SessionFile(it, labelFor(it.name)) }
      }
    }
  }

  fun open(session: SessionFile, onError: () -> Unit) {
    _loading.value = true
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        try {
          session.file.bufferedReader().use(SessionCsv::read).takeIf { it.points.size >= 2 }
        } catch (error: IOException) {
          null
        }
      }
      _loading.value = false
      if (result == null) onError() else _loaded.value = LoadedSession(session, result, cumulativeDistances(result.points))
    }
  }

  fun close() {
    _loaded.value = null
  }

  suspend fun save(name: String, from: Int, to: Int): Long {
    val session = _loaded.value ?: error("No session")
    return routes.saveTrack(name, session.track.points.subList(from, to + 1))
  }

  private fun labelFor(name: String): String = try {
    val stamp = LocalDateTime.parse(name.removePrefix("session_").removeSuffix(".csv"), DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    stamp.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))
  } catch (error: RuntimeException) {
    name
  }
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

private fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(CLOCK)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionImportScreen(app: AppViewModel, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val viewModel: SessionImportViewModel = viewModel(
    factory = viewModelFactory { initializer { SessionImportViewModel(context.applicationContext, context.appGraph.routes) } },
  )
  val sessions by viewModel.sessions.collectAsStateWithLifecycle()
  val loaded by viewModel.loaded.collectAsStateWithLifecycle()
  val loading by viewModel.loading.collectAsStateWithLifecycle()

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbar) },
    topBar = {
      TopAppBar(
        navigationIcon = { BackButton { if (loaded != null) viewModel.close() else app.back() } },
        title = { Text(stringResource(if (loaded != null) R.string.session_trim_title else R.string.session_title)) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
  ) { padding ->
    val session = loaded
    Box(
      Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      when {
        loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        session != null -> TrimEditor(session, viewModel, app)
        else -> SessionList(sessions) { file -> viewModel.open(file) { app.show(R.string.session_unreadable) } }
      }
    }
  }
}

@Composable
private fun SessionList(sessions: List<SessionFile>?, onOpen: (SessionFile) -> Unit) {
  val list = sessions ?: return
  if (list.isEmpty()) {
    Text(
      stringResource(R.string.session_empty),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(24.dp),
    )
    return
  }
  LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    items(list, key = { it.file.name }) { session ->
      Surface(
        onClick = { onOpen(session) },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(16.dp)) {
          Text(stringResource(R.string.session_item, session.label), style = MaterialTheme.typography.titleMedium)
          Text(
            "${session.file.length() / 1024} KB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun TrimEditor(session: LoadedSession, viewModel: SessionImportViewModel, app: AppViewModel) {
  val scope = rememberCoroutineScope()
  val last = session.lats.size - 1
  var range by remember(session) { mutableStateOf(0f..last.toFloat()) }
  val from = range.start.toInt().coerceIn(0, last - 1)
  val to = range.endInclusive.toInt().coerceIn(from + 1, last)
  val points = session.track.points
  var name by rememberSaveable(session.source.file.name) {
    mutableStateOf(app.getApplication<android.app.Application>().getString(R.string.session_default_name, session.source.label))
  }
  var saving by remember { mutableStateOf(false) }

  val mapState = rememberMapState(session.lats[0], session.lons[0], 2f)
  LaunchedEffect(session) {
    mapState.following = false
    mapState.fit(Bounds.of(points)!!)
  }
  val full = remember(session) { MapPolyline(session.lats, session.lons, session.lats.size, Palette.Walked, 3f, halo = false) }
  val selected = remember(session, from, to) {
    MapPolyline(session.lats.copyOfRange(from, to + 1), session.lons.copyOfRange(from, to + 1), to - from + 1, Palette.Accent, 5f)
  }

  Column(Modifier.fillMaxSize()) {
    MapView(
      state = mapState,
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      lines = listOf(full, selected),
      markers = listOf(
        MapMarker(session.lats[to], session.lons[to], MarkerKind.END),
        MapMarker(session.lats[from], session.lons[from], MarkerKind.START),
      ),
    )
    Column(
      Modifier
        .navigationBarsPadding()
        .padding(16.dp),
    ) {
      Text(stringResource(R.string.session_trim_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      RangeSlider(value = range, onValueChange = { range = it }, valueRange = 0f..last.toFloat())
      val startTime = points[from].timeMs
      val endTime = points[to].timeMs
      Text(
        stringResource(
          R.string.session_range,
          clock(startTime),
          clock(endTime),
          formatDistance(session.distances[to] - session.distances[from]),
          formatHoursMinutes(endTime - startTime),
        ),
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
      )
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(120) },
        label = { Text(stringResource(R.string.session_name_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(12.dp))
      Button(
        onClick = {
          saving = true
          scope.launch {
            val id = viewModel.save(name.ifBlank { session.source.label }, from, to)
            app.replace(Screen.SessionImport, Screen.RouteDetail(id))
            app.show(R.string.session_saved)
          }
        },
        enabled = !saving,
        modifier = Modifier
          .fillMaxWidth()
          .height(LargeButtonHeight),
      ) { Text(stringResource(R.string.session_save)) }
    }
  }
}
