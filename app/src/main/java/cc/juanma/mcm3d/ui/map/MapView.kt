package cc.juanma.mcm3d.ui.map

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.juanma.mcm3d.appGraph
import cc.juanma.mcm3d.core.route.Bounds
import cc.juanma.mcm3d.core.zone.WayClass
import cc.juanma.mcm3d.core.zone.ZoneGrid
import cc.juanma.mcm3d.ui.formatDistance
import cc.juanma.mcm3d.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

private const val METERS_PER_DEGREE = 111_195.0
private const val MIN_METERS_PER_DP = 0.25f
private const val MAX_METERS_PER_DP = 80f
private val SCALE_STEPS_M = doubleArrayOf(10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1_000.0, 2_000.0, 5_000.0, 10_000.0, 20_000.0)

/** A line in its own local frame (meters from its first point), built once and moved by the camera transform. */
class MapPolyline(
  val lats: DoubleArray,
  val lons: DoubleArray,
  val size: Int,
  val color: Color,
  val widthDp: Float,
  val dashed: Boolean = false,
  val halo: Boolean = true,
) {
  internal val originLat = lats[0]
  internal val originLon = lons[0]
  internal val path: Path by lazy { localPath(lats, lons, 0, size, originLat, originLon) }
}

enum class MarkerKind { START, END, WAYPOINT, TURN }

class MapMarker(val lat: Double, val lon: Double, val kind: MarkerKind)

class MapPosition(val lat: Double, val lon: Double, val accuracyM: Float?, val headingDeg: Float?)

/**
 * Camera of a [MapView]: center, zoom (meters per dp) and the bearing shown at the top of the
 * screen. [following] keeps the camera on the user until a gesture moves it.
 */
@Stable
class MapState(lat: Double, lon: Double, metersPerDp: Float = 1.5f) {
  var centerLat by mutableDoubleStateOf(lat)
  var centerLon by mutableDoubleStateOf(lon)
  var metersPerDp by mutableFloatStateOf(metersPerDp)
  var rotationDeg by mutableFloatStateOf(0f)
  var following by mutableStateOf(true)

  internal var widthPx by mutableIntStateOf(0)
  internal var heightPx by mutableIntStateOf(0)
  internal var density = 1f
  private var pendingFit: Bounds? = null

  fun centerOn(lat: Double, lon: Double) {
    centerLat = lat
    centerLon = lon
  }

  fun fit(bounds: Bounds) {
    if (widthPx == 0 || heightPx == 0) {
      pendingFit = bounds
      return
    }
    val cosLat = cos(Math.toRadians(bounds.centerLat))
    val widthM = max((bounds.maxLon - bounds.minLon) * cosLat * METERS_PER_DEGREE, 100.0)
    val heightM = max((bounds.maxLat - bounds.minLat) * METERS_PER_DEGREE, 100.0)
    val padding = 32 * density
    val metersPerPx = max(widthM / (widthPx - 2 * padding), heightM / (heightPx - 2 * padding))
    metersPerDp = (metersPerPx * density).toFloat().coerceIn(MIN_METERS_PER_DP, MAX_METERS_PER_DP)
    rotationDeg = 0f
    centerOn(bounds.centerLat, bounds.centerLon)
  }

  fun zoomBy(factor: Float) {
    metersPerDp = (metersPerDp / factor).coerceIn(MIN_METERS_PER_DP, MAX_METERS_PER_DP)
  }

  internal fun onSize(width: Int, height: Int, density: Float) {
    widthPx = width
    heightPx = height
    this.density = density
    pendingFit?.let {
      pendingFit = null
      fit(it)
    }
  }

  internal fun panByPixels(dx: Float, dy: Float) {
    val metersPerPx = metersPerDp / density
    val rx = -dx * metersPerPx
    val ry = dy * metersPerPx
    val r = Math.toRadians(rotationDeg.toDouble())
    val east = rx * cos(r) + ry * sin(r)
    val north = -rx * sin(r) + ry * cos(r)
    centerLat += north / METERS_PER_DEGREE
    centerLon += east / (METERS_PER_DEGREE * cos(Math.toRadians(centerLat)))
  }
}

@Composable
fun rememberMapState(lat: Double, lon: Double, metersPerDp: Float = 1.5f): MapState =
  remember { MapState(lat, lon, metersPerDp) }

private class BaseMap(val originLat: Double, val originLon: Double, val paths: Map<WayClass, Path>)

private data class Window(val centerLat: Double, val centerLon: Double, val radiusM: Double)

/**
 * 2D map drawn without a tile server: OpenStreetMap highways from the offline zone store, then
 * the given lines, markers and the user's position. [anchorY] places the center vertically
 * (below the middle when guiding, so more of the way ahead is visible).
 */
@Composable
fun MapView(
  state: MapState,
  modifier: Modifier = Modifier,
  lines: List<MapPolyline> = emptyList(),
  markers: List<MapMarker> = emptyList(),
  position: MapPosition? = null,
  anchorY: Float = 0.5f,
  interactive: Boolean = true,
  overlay: @Composable BoxScope.() -> Unit = {},
) {
  val zones = LocalContext.current.appGraph.zones
  val zoneVersion by zones.version.collectAsStateWithLifecycle()
  val density = LocalDensity.current.density
  val measurer = rememberTextMeasurer()
  val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Palette.OnSurfaceVariant)
  val attribution = remember(measurer) { measurer.measure("© OpenStreetMap", labelStyle) }

  val window by remember(state) {
    derivedStateOf {
      val viewRadius = state.metersPerDp / max(density, 1f) * hypot(state.widthPx.toDouble(), state.heightPx.toDouble()) / 2
      // A power-of-two radius and a quarter-radius grid: the base map is rebuilt only when the
      // view gets near the edge of what was built or the zoom changes a lot.
      val radius = max(800.0, Math.pow(2.0, Math.ceil(ln(max(viewRadius * 3, 1.0)) / ln(2.0))))
      val step = radius / 4 / METERS_PER_DEGREE
      Window(floor(state.centerLat / step) * step, floor(state.centerLon / step) * step, radius)
    }
  }
  val base by produceState<BaseMap?>(null, window, zoneVersion) {
    val bounds = Bounds(window.centerLat, window.centerLon, window.centerLat, window.centerLon).expandedBy(window.radiusM)
    value = withContext(Dispatchers.IO) {
      val ways = ZoneGrid.cellsIn(bounds).flatMap { zones.load(it).orEmpty() }.filter { it.intersects(bounds) }
      withContext(Dispatchers.Default) { buildBase(ways, window.centerLat, window.centerLon) }
    }
  }

  val gestures = if (interactive) {
    Modifier.pointerInput(state) {
      detectTransformGestures { _, pan, zoom, _ ->
        if (pan.getDistance() > 0.5f || zoom != 1f) state.following = false
        state.zoomBy(zoom)
        state.panByPixels(pan.x, pan.y)
      }
    }
  } else {
    Modifier
  }

  Box(
    modifier
      .clipToBounds()
      .onSizeChanged { state.onSize(it.width, it.height, density) }
      .then(gestures),
  ) {
    Box(
      Modifier
        .fillMaxSize()
        .drawBehind {
          drawRect(Palette.MapBackground)
          val camera = Camera(state, size.width / 2f, size.height * anchorY)
          base?.let { drawBase(it, camera) }
          for (line in lines) drawLine(line, camera)
          for (marker in markers) drawMarker(marker, camera.screen(marker.lat, marker.lon))
          position?.let { drawPosition(it, camera) }
          drawScale(camera, measurer, labelStyle)
          drawText(attribution, topLeft = Offset(size.width - attribution.size.width - 8.dp.toPx(), size.height - attribution.size.height - 6.dp.toPx()))
        },
    )
    overlay()
  }
}

private class Camera(state: MapState, val anchorX: Float, val anchorY: Float) {
  val lat = state.centerLat
  val lon = state.centerLon
  val cosLat = cos(Math.toRadians(lat))
  val rotation = state.rotationDeg
  val pxPerMeter = (state.density / state.metersPerDp).toDouble()
  private val r = Math.toRadians(rotation.toDouble())
  private val cosR = cos(r)
  private val sinR = sin(r)

  fun screen(pointLat: Double, pointLon: Double): Offset {
    val dx = (pointLon - lon) * cosLat * METERS_PER_DEGREE
    val dy = (pointLat - lat) * METERS_PER_DEGREE
    val rx = dx * cosR - dy * sinR
    val ry = dx * sinR + dy * cosR
    return Offset((anchorX + rx * pxPerMeter).toFloat(), (anchorY - ry * pxPerMeter).toFloat())
  }
}

private inline fun DrawScope.inWorld(camera: Camera, originLat: Double, originLon: Double, block: DrawScope.(scale: Float) -> Unit) {
  val s = camera.pxPerMeter.toFloat()
  val offsetX = ((originLon - camera.lon) * camera.cosLat * METERS_PER_DEGREE).toFloat()
  val offsetY = ((originLat - camera.lat) * METERS_PER_DEGREE).toFloat()
  withTransform({
    translate(camera.anchorX, camera.anchorY)
    rotate(-camera.rotation, pivot = Offset.Zero)
    scale(s, -s, pivot = Offset.Zero)
    translate(offsetX, offsetY)
  }) { block(s) }
}

private fun DrawScope.drawBase(base: BaseMap, camera: Camera) {
  inWorld(camera, base.originLat, base.originLon) { s ->
    for (wayClass in WayClass.entries) {
      val path = base.paths[wayClass] ?: continue
      val style = WAY_STYLES.getValue(wayClass)
      val width = style.widthDp.dp.toPx() / s
      val effect = if (style.dashed) PathEffect.dashPathEffect(floatArrayOf(width * 2.2f, width * 1.6f)) else null
      drawPath(path, style.color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    }
  }
}

private fun DrawScope.drawLine(line: MapPolyline, camera: Camera) {
  if (line.size < 2) return
  inWorld(camera, line.originLat, line.originLon) { s ->
    val width = line.widthDp.dp.toPx() / s
    if (line.halo) {
      drawPath(line.path, Palette.Halo, style = Stroke(width + 3.dp.toPx() / s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    val effect = if (line.dashed) PathEffect.dashPathEffect(floatArrayOf(width * 2.5f, width * 2f)) else null
    drawPath(line.path, line.color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
  }
}

private fun DrawScope.drawMarker(marker: MapMarker, at: Offset) {
  val outline = Palette.Halo
  when (marker.kind) {
    MarkerKind.START -> {
      drawCircle(outline, radius = 9.dp.toPx(), center = at)
      drawCircle(Palette.Good, radius = 7.dp.toPx(), center = at)
    }
    MarkerKind.END -> {
      drawCircle(outline, radius = 9.dp.toPx(), center = at)
      drawCircle(Palette.OnSurface, radius = 7.dp.toPx(), center = at)
      drawCircle(outline, radius = 3.dp.toPx(), center = at)
    }
    MarkerKind.WAYPOINT, MarkerKind.TURN -> {
      val r = (if (marker.kind == MarkerKind.TURN) 8 else 7).dp.toPx()
      val diamond = Path().apply {
        moveTo(at.x, at.y - r)
        lineTo(at.x + r, at.y)
        lineTo(at.x, at.y + r)
        lineTo(at.x - r, at.y)
        close()
      }
      drawPath(diamond, outline, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
      drawPath(diamond, if (marker.kind == MarkerKind.TURN) Palette.Accent else Palette.Info)
    }
  }
}

private fun DrawScope.drawPosition(position: MapPosition, camera: Camera) {
  val at = camera.screen(position.lat, position.lon)
  position.accuracyM?.let {
    val radius = (it * camera.pxPerMeter).toFloat()
    if (radius > 10.dp.toPx()) drawCircle(Palette.Info.copy(alpha = 0.12f), radius = radius, center = at)
  }
  position.headingDeg?.let { heading ->
    val angle = Math.toRadians((heading - camera.rotation).toDouble())
    val length = 46.dp.toPx()
    val spread = Math.toRadians(28.0)
    val cone = Path().apply {
      moveTo(at.x, at.y)
      lineTo(at.x + (length * sin(angle - spread)).toFloat(), at.y - (length * cos(angle - spread)).toFloat())
      lineTo(at.x + (length * sin(angle + spread)).toFloat(), at.y - (length * cos(angle + spread)).toFloat())
      close()
    }
    drawPath(cone, Palette.Info.copy(alpha = 0.35f))
  }
  drawCircle(Palette.Halo, radius = 10.dp.toPx(), center = at)
  drawCircle(Color.White, radius = 8.dp.toPx(), center = at)
  drawCircle(Palette.Info, radius = 6.dp.toPx(), center = at)
}

private fun DrawScope.drawScale(camera: Camera, measurer: androidx.compose.ui.text.TextMeasurer, style: androidx.compose.ui.text.TextStyle) {
  val metersPerPx = 1 / camera.pxPerMeter
  val maxMeters = metersPerPx * size.width * 0.28
  val meters = SCALE_STEPS_M.lastOrNull { it <= maxMeters } ?: return
  val length = (meters / metersPerPx).toFloat()
  val margin = 12.dp.toPx()
  val y = size.height - margin
  val stroke = 2.dp.toPx()
  drawLine(Palette.OnSurfaceVariant, Offset(margin, y), Offset(margin + length, y), stroke)
  drawLine(Palette.OnSurfaceVariant, Offset(margin, y), Offset(margin, y - 5.dp.toPx()), stroke)
  drawLine(Palette.OnSurfaceVariant, Offset(margin + length, y), Offset(margin + length, y - 5.dp.toPx()), stroke)
  val label = measurer.measure(formatDistance(meters), style)
  drawText(label, topLeft = Offset(margin, y - 6.dp.toPx() - label.size.height))
}

private class WayStyle(val color: Color, val widthDp: Float, val dashed: Boolean)

private val WAY_STYLES = mapOf(
  WayClass.MAJOR to WayStyle(Color(0xFF56626F), 6f, false),
  WayClass.MINOR to WayStyle(Color(0xFF3A4551), 3.5f, false),
  WayClass.TRACK to WayStyle(Color(0xFF8C7A5B), 2.5f, true),
  WayClass.PATH to WayStyle(Color(0xFFB59B6E), 2f, true),
)

private fun buildBase(ways: List<cc.juanma.mcm3d.core.zone.OsmWay>, originLat: Double, originLon: Double): BaseMap {
  val paths = HashMap<WayClass, Path>()
  for (way in ways) {
    val path = paths.getOrPut(way.wayClass) { Path() }
    appendLocal(path, way.lats, way.lons, way.lats.size, originLat, originLon)
  }
  return BaseMap(originLat, originLon, paths)
}

internal fun localPath(lats: DoubleArray, lons: DoubleArray, from: Int, to: Int, originLat: Double, originLon: Double): Path {
  val path = Path()
  if (to - from < 2) return path
  val cosLat = cos(Math.toRadians(originLat))
  path.moveTo(((lons[from] - originLon) * cosLat * METERS_PER_DEGREE).toFloat(), ((lats[from] - originLat) * METERS_PER_DEGREE).toFloat())
  for (i in from + 1 until to) {
    path.lineTo(((lons[i] - originLon) * cosLat * METERS_PER_DEGREE).toFloat(), ((lats[i] - originLat) * METERS_PER_DEGREE).toFloat())
  }
  return path
}

private fun appendLocal(path: Path, lats: DoubleArray, lons: DoubleArray, size: Int, originLat: Double, originLon: Double) {
  val cosLat = cos(Math.toRadians(originLat))
  path.moveTo(((lons[0] - originLon) * cosLat * METERS_PER_DEGREE).toFloat(), ((lats[0] - originLat) * METERS_PER_DEGREE).toFloat())
  for (i in 1 until size) {
    path.lineTo(((lons[i] - originLon) * cosLat * METERS_PER_DEGREE).toFloat(), ((lats[i] - originLat) * METERS_PER_DEGREE).toFloat())
  }
}

/** Splits a route at a vertex index into the walked part and the part ahead. */
fun splitLine(lats: DoubleArray, lons: DoubleArray, splitIndex: Int, walked: Color, ahead: Color, widthDp: Float): List<MapPolyline> {
  val index = splitIndex.coerceIn(0, lats.size - 1)
  return buildList {
    if (index >= 1) add(MapPolyline(lats.copyOfRange(0, index + 1), lons.copyOfRange(0, index + 1), index + 1, walked, widthDp - 1))
    if (lats.size - index >= 2) {
      add(MapPolyline(lats.copyOfRange(index, lats.size), lons.copyOfRange(index, lons.size), lats.size - index, ahead, widthDp))
    }
  }
}
