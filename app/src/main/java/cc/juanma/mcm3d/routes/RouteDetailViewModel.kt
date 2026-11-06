package cc.juanma.mcm3d.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.juanma.mcm3d.core.route.RouteProgress
import cc.juanma.mcm3d.core.route.RouteProgressTracker
import cc.juanma.mcm3d.data.RouteEntity
import cc.juanma.mcm3d.data.RouteGeometry
import cc.juanma.mcm3d.data.RouteRepository
import cc.juanma.mcm3d.data.WaypointEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface DetailRoute {
  data object Loading : DetailRoute
  data object Missing : DetailRoute
  data class Loaded(val route: RouteEntity) : DetailRoute
}

class WaypointRow(val waypoint: WaypointEntity, val location: RouteProgress?)

class RouteDetailViewModel(private val routeId: Long, private val routes: RouteRepository) : ViewModel() {
  private val source = routes.observeRoute(routeId)

  val route: StateFlow<DetailRoute> = source
    .map { if (it == null) DetailRoute.Missing else DetailRoute.Loaded(it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailRoute.Loading)

  val geometry: StateFlow<RouteGeometry?> = source
    .map { it?.trackUpdatedAtMs }
    .distinctUntilChanged()
    .map { stamp -> if (stamp == null) null else routes.loadGeometry(routeId) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val waypoints: StateFlow<List<WaypointRow>> = combine(routes.observeWaypoints(routeId), geometry) { list, shape ->
    val tracker = shape?.let { RouteProgressTracker(it.lats, it.lons) }
    list.map { WaypointRow(it, tracker?.locate(it.lat, it.lon)) }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun rename(name: String) {
    viewModelScope.launch { routes.rename(routeId, name) }
  }

  fun delete(onDeleted: () -> Unit) {
    viewModelScope.launch {
      routes.delete(routeId)
      onDeleted()
    }
  }

  suspend fun exportGpx(): File? = routes.exportGpx(routeId)
}
