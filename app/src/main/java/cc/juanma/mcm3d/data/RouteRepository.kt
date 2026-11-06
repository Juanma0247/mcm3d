package cc.juanma.mcm3d.data

import android.content.Context
import cc.juanma.mcm3d.BuildConfig
import cc.juanma.mcm3d.core.elevation.ElevationSampler
import cc.juanma.mcm3d.core.elevation.Terrarium
import cc.juanma.mcm3d.core.elevation.TileId
import cc.juanma.mcm3d.core.route.Bounds
import cc.juanma.mcm3d.core.route.GpxWriter
import cc.juanma.mcm3d.core.route.ParsedRoute
import cc.juanma.mcm3d.core.route.RouteStats
import cc.juanma.mcm3d.core.route.TrackPoint
import cc.juanma.mcm3d.core.route.Waypoint
import cc.juanma.mcm3d.core.route.cumulativeDistances
import cc.juanma.mcm3d.core.route.densify
import cc.juanma.mcm3d.core.route.smoothAlongDistance
import cc.juanma.mcm3d.elevation.ElevationWorker
import cc.juanma.mcm3d.elevation.TerrariumTileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** Geometry ready for drawing: parallel arrays plus the distance along the route. */
class RouteGeometry(
  val lats: DoubleArray,
  val lons: DoubleArray,
  val elevations: DoubleArray,
  val distances: DoubleArray,
) {
  val size: Int get() = lats.size
  val hasElevation: Boolean = elevations.count { !it.isNaN() } >= size / 2

  companion object {
    fun of(points: List<TrackPoint>) = RouteGeometry(
      lats = DoubleArray(points.size) { points[it].lat },
      lons = DoubleArray(points.size) { points[it].lon },
      elevations = DoubleArray(points.size) { points[it].elevation },
      distances = cumulativeDistances(points),
    )
  }
}

class RouteRepository(
  private val context: Context,
  private val dao: RouteDao,
  private val tracks: TrackStore,
  private val tiles: TerrariumTileStore,
) {
  fun observeRoutes(): Flow<List<RouteEntity>> = dao.observeAll()

  fun observeRoute(id: Long): Flow<RouteEntity?> = dao.observe(id)

  fun observeWaypoints(id: Long): Flow<List<WaypointEntity>> = dao.observeWaypoints(id)

  suspend fun route(id: Long): RouteEntity? = dao.get(id)

  suspend fun loadGeometry(id: Long): RouteGeometry? = withContext(Dispatchers.IO) {
    tracks.read(id)?.points?.takeIf { it.size >= 2 }?.let(RouteGeometry::of)
  }

  suspend fun importRoute(parsed: ParsedRoute, fallbackName: String): Long = withContext(Dispatchers.IO) {
    val points = densify(parsed.points, DENSIFY_SPACING_M)
    val stats = RouteStats.of(points)
    val bounds = Bounds.of(points)!!
    val now = System.currentTimeMillis()
    val route = RouteEntity(
      name = parsed.name?.takeIf { it.isNotBlank() }?.take(MAX_NAME_LENGTH) ?: fallbackName,
      source = RouteSource.IMPORTED,
      fileFormat = parsed.format.name,
      createdAtMs = now,
      trackUpdatedAtMs = now,
      pointCount = points.size,
      elevationSource = ElevationSource.PENDING,
    ).withStats(stats).withBounds(bounds)
    val waypoints = parsed.waypoints.mapIndexed { index, w ->
      WaypointEntity(
        routeId = 0,
        position = index,
        name = w.name.take(MAX_NAME_LENGTH),
        lat = w.lat,
        lon = w.lon,
        elevationM = w.elevation.takeUnless { it.isNaN() },
        description = w.description,
      )
    }
    val id = dao.insertWithWaypoints(route, waypoints)
    try {
      tracks.write(id, TrackPoint.NO_TIME, points)
    } catch (error: java.io.IOException) {
      dao.delete(id)
      throw error
    }
    ElevationWorker.schedule(context, id)
    id
  }

  suspend fun rename(id: Long, name: String) {
    val trimmed = name.trim().take(MAX_NAME_LENGTH)
    if (trimmed.isNotEmpty()) dao.rename(id, trimmed)
  }

  suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
    ElevationWorker.cancel(context, id)
    dao.delete(id)
    tracks.delete(id)
  }

  suspend fun createRecording(name: String, startTimeMs: Long): Long = dao.insertRoute(
    RouteEntity(
      name = name,
      source = RouteSource.RECORDED,
      createdAtMs = startTimeMs,
      trackUpdatedAtMs = startTimeMs,
      recording = true,
      elevationSource = ElevationSource.RECORDED,
    ),
  )

  fun openAppender(id: Long, startTimeMs: Long): TrackAppender = tracks.openAppender(id, startTimeMs)

  suspend fun addWaypoint(routeId: Long, waypoint: Waypoint) {
    dao.appendWaypoint(
      WaypointEntity(
        routeId = routeId,
        position = 0,
        name = waypoint.name,
        lat = waypoint.lat,
        lon = waypoint.lon,
        elevationM = waypoint.elevation.takeUnless { it.isNaN() },
        description = null,
      ),
    )
  }

  /** Computes the final statistics of a recorded outing. Outings with less than two points are discarded. */
  suspend fun finishRecording(id: Long): Boolean = withContext(Dispatchers.IO) {
    val route = dao.get(id) ?: return@withContext false
    val points = tracks.read(id)?.points.orEmpty()
    if (points.size < 2) {
      dao.delete(id)
      tracks.delete(id)
      return@withContext false
    }
    val stats = RouteStats.of(points)
    dao.update(
      route.copy(
        recording = false,
        pointCount = points.size,
        trackUpdatedAtMs = System.currentTimeMillis(),
        elevationSource = if (stats.ascentM != null) ElevationSource.RECORDED else ElevationSource.NONE,
      ).withStats(stats).withBounds(Bounds.of(points)!!),
    )
    true
  }

  /** Finishes outings left open by a crash or a killed process. */
  suspend fun recoverInterruptedRecordings(activeRouteId: Long?) {
    for (route in dao.recording()) {
      if (route.id != activeRouteId) finishRecording(route.id)
    }
  }

  /**
   * Downloads the elevation tiles around the route (with a margin, so the zone is available offline
   * later) and replaces the file's elevations with smoothed model values.
   * Throws [java.io.IOException] when the tiles cannot be downloaded.
   */
  suspend fun applyModelElevations(id: Long) = withContext(Dispatchers.IO) {
    val route = dao.get(id) ?: return@withContext
    if (route.elevationSource != ElevationSource.PENDING) return@withContext
    val points = tracks.read(id)?.points ?: return@withContext
    val needed = zoneTiles(points)
    tiles.missing(needed).forEach(tiles::download)

    val sampler = ElevationSampler { tiles.pixels(it) }
    val raw = DoubleArray(points.size) { sampler.elevationAt(points[it].lat, points[it].lon) }
    if (raw.count { it.isNaN() } > points.size / 10) throw java.io.IOException("Elevation tiles incomplete")
    val distances = cumulativeDistances(points)
    val smooth = smoothAlongDistance(distances, raw, ELEVATION_SMOOTHING_SIGMA_M)
    val updated = points.mapIndexed { i, p -> p.copy(elevation = if (smooth[i].isNaN()) p.elevation else smooth[i]) }
    tracks.write(id, TrackPoint.NO_TIME, updated)
    val waypoints = dao.waypoints(id).map { w ->
      val modelled = sampler.elevationAt(w.lat, w.lon)
      if (modelled.isNaN()) w else w.copy(elevationM = modelled)
    }
    if (waypoints.isNotEmpty()) dao.updateWaypoints(waypoints)

    val current = dao.get(id) ?: return@withContext
    dao.update(
      current.copy(elevationSource = ElevationSource.MODEL, trackUpdatedAtMs = System.currentTimeMillis())
        .withStats(RouteStats.of(updated, distances)),
    )
  }

  suspend fun keepFileElevations(id: Long) {
    val route = dao.get(id) ?: return
    if (route.elevationSource == ElevationSource.PENDING) dao.update(route.copy(elevationSource = ElevationSource.FILE))
  }

  suspend fun exportGpx(id: Long): File? = withContext(Dispatchers.IO) {
    val route = dao.get(id) ?: return@withContext null
    val track = tracks.read(id) ?: return@withContext null
    val waypoints = dao.waypoints(id).map {
      Waypoint(it.name, it.lat, it.lon, it.elevationM ?: Double.NaN, it.description)
    }
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, safeFileName(route.name) + ".gpx")
    file.bufferedWriter().use { out ->
      GpxWriter.write(out, route.name, track.points, waypoints, "MCM3D ${BuildConfig.VERSION_NAME}")
    }
    file
  }

  private fun zoneTiles(points: List<TrackPoint>): Set<TileId> {
    val out = LinkedHashSet<TileId>()
    val step = (points.size / MAX_ZONE_SAMPLES).coerceAtLeast(1)
    for (i in points.indices step step) {
      val p = points[i]
      out += Terrarium.tilesCovering(Bounds(p.lat, p.lon, p.lat, p.lon).expandedBy(ZONE_MARGIN_M))
    }
    val last = points.last()
    out += Terrarium.tilesCovering(Bounds(last.lat, last.lon, last.lat, last.lon).expandedBy(ZONE_MARGIN_M))
    return out
  }

  private fun safeFileName(name: String): String =
    name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().take(60).ifEmpty { "ruta" }

  private fun RouteEntity.withStats(stats: RouteStats) = copy(
    distanceM = stats.distanceM,
    ascentM = stats.ascentM,
    descentM = stats.descentM,
    minElevationM = stats.minElevationM,
    maxElevationM = stats.maxElevationM,
    durationMs = stats.durationMs ?: durationMs,
  )

  private fun RouteEntity.withBounds(bounds: Bounds) = copy(
    minLat = bounds.minLat,
    minLon = bounds.minLon,
    maxLat = bounds.maxLat,
    maxLon = bounds.maxLon,
  )

  private companion object {
    const val DENSIFY_SPACING_M = 10.0
    const val ELEVATION_SMOOTHING_SIGMA_M = 20.0
    const val ZONE_MARGIN_M = 1_000.0
    const val MAX_ZONE_SAMPLES = 2_000
    const val MAX_NAME_LENGTH = 120
  }
}
