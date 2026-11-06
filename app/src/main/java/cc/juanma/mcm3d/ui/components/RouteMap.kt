package cc.juanma.mcm3d.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.juanma.mcm3d.ui.formatDistance
import cc.juanma.mcm3d.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

class MapLine(
  val lats: DoubleArray,
  val lons: DoubleArray,
  val size: Int,
  val color: Color,
  val width: Dp,
  val dashed: Boolean = false,
)

enum class MarkerKind { START, END, WAYPOINT, POSITION }

class MapMarker(val lat: Double, val lon: Double, val kind: MarkerKind)

private const val METERS_PER_DEGREE = 111_195.0
private const val MIN_SPAN_DEGREES = 0.002
private const val MAX_DRAWN_POINTS = 3_000
private val SCALE_STEPS_M = doubleArrayOf(10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1_000.0, 2_000.0, 5_000.0, 10_000.0, 20_000.0, 50_000.0)

/**
 * North-up 2D view of routes drawn without a base map. Every line has a dark halo so it reads on any
 * background. This is a lightweight preview; the navigation map will share the OpenGL renderer.
 */
@Composable
fun RouteMap(lines: List<MapLine>, markers: List<MapMarker>, modifier: Modifier = Modifier) {
  val measurer = rememberTextMeasurer()
  val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Palette.OnSurfaceVariant)
  val background = MaterialTheme.colorScheme.surfaceContainerHigh
  Box(
    modifier
      .clip(RoundedCornerShape(12.dp))
      .drawWithCache {
        val projection = Projection.fit(lines, markers, size, 20.dp.toPx())
        val paths = lines.map { line -> line to projection?.path(line) }
        onDrawBehind {
          drawRect(background)
          if (projection == null) return@onDrawBehind
          for ((line, path) in paths) {
            if (path == null) continue
            val width = line.width.toPx()
            val effect = if (line.dashed) PathEffect.dashPathEffect(floatArrayOf(width * 2.5f, width * 2f)) else null
            drawPath(path, Palette.Halo, style = Stroke(width + 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, line.color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
          }
          for (marker in markers) drawMarker(marker, projection.point(marker.lat, marker.lon))
          drawNorth(measurer, labelStyle)
          drawScaleBar(projection, measurer, labelStyle)
        }
      },
  )
}

private class Projection(
  private val minLat: Double,
  private val maxLat: Double,
  private val minLon: Double,
  private val cosLat: Double,
  private val scale: Double,
  private val offsetX: Double,
  private val offsetY: Double,
) {
  val metersPerPixel: Double get() = METERS_PER_DEGREE / scale

  fun point(lat: Double, lon: Double) = Offset(
    (offsetX + (lon - minLon) * cosLat * scale).toFloat(),
    (offsetY + (maxLat - lat) * scale).toFloat(),
  )

  fun path(line: MapLine): Path? {
    if (line.size < 2) return null
    val step = max(1, line.size / MAX_DRAWN_POINTS)
    val path = Path()
    var i = 0
    while (i < line.size) {
      val p = point(line.lats[i], line.lons[i])
      if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
      i += step
    }
    val last = point(line.lats[line.size - 1], line.lons[line.size - 1])
    path.lineTo(last.x, last.y)
    return path
  }

  companion object {
    fun fit(lines: List<MapLine>, markers: List<MapMarker>, size: Size, padding: Float): Projection? {
      var minLat = Double.MAX_VALUE
      var maxLat = -Double.MAX_VALUE
      var minLon = Double.MAX_VALUE
      var maxLon = -Double.MAX_VALUE
      var any = false
      fun include(lat: Double, lon: Double) {
        any = true
        minLat = min(minLat, lat)
        maxLat = max(maxLat, lat)
        minLon = min(minLon, lon)
        maxLon = max(maxLon, lon)
      }
      for (line in lines) for (i in 0 until line.size) include(line.lats[i], line.lons[i])
      for (marker in markers) include(marker.lat, marker.lon)
      if (!any || size.width <= 2 * padding || size.height <= 2 * padding) return null
      val cosLat = cos(Math.toRadians((minLat + maxLat) / 2))
      if (maxLat - minLat < MIN_SPAN_DEGREES) {
        val c = (minLat + maxLat) / 2
        minLat = c - MIN_SPAN_DEGREES / 2
        maxLat = c + MIN_SPAN_DEGREES / 2
      }
      if ((maxLon - minLon) * cosLat < MIN_SPAN_DEGREES) {
        val c = (minLon + maxLon) / 2
        minLon = c - MIN_SPAN_DEGREES / 2 / cosLat
        maxLon = c + MIN_SPAN_DEGREES / 2 / cosLat
      }
      val spanX = (maxLon - minLon) * cosLat
      val spanY = maxLat - minLat
      val scale = min((size.width - 2 * padding) / spanX, (size.height - 2 * padding) / spanY)
      return Projection(
        minLat = minLat,
        maxLat = maxLat,
        minLon = minLon,
        cosLat = cosLat,
        scale = scale,
        offsetX = (size.width - spanX * scale) / 2,
        offsetY = (size.height - spanY * scale) / 2,
      )
    }
  }
}

private fun DrawScope.drawMarker(marker: MapMarker, at: Offset) {
  val outline = Palette.Halo
  when (marker.kind) {
    MarkerKind.START -> {
      drawCircle(outline, radius = 8.dp.toPx(), center = at)
      drawCircle(Palette.Accent, radius = 6.dp.toPx(), center = at)
    }
    MarkerKind.END -> {
      val half = 6.dp.toPx()
      drawRect(outline, topLeft = Offset(at.x - half - 2.dp.toPx(), at.y - half - 2.dp.toPx()), size = Size(2 * half + 4.dp.toPx(), 2 * half + 4.dp.toPx()))
      drawRect(Palette.OnSurface, topLeft = Offset(at.x - half, at.y - half), size = Size(2 * half, 2 * half))
    }
    MarkerKind.WAYPOINT -> {
      val r = 7.dp.toPx()
      val diamond = Path().apply {
        moveTo(at.x, at.y - r)
        lineTo(at.x + r, at.y)
        lineTo(at.x, at.y + r)
        lineTo(at.x - r, at.y)
        close()
      }
      drawPath(diamond, outline, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
      drawPath(diamond, Palette.Info)
    }
    MarkerKind.POSITION -> {
      drawCircle(Palette.Info.copy(alpha = 0.25f), radius = 14.dp.toPx(), center = at)
      drawCircle(outline, radius = 8.dp.toPx(), center = at)
      drawCircle(Color.White, radius = 6.dp.toPx(), center = at)
      drawCircle(Palette.Info, radius = 4.dp.toPx(), center = at)
    }
  }
}

private fun DrawScope.drawNorth(measurer: TextMeasurer, style: TextStyle) {
  val margin = 12.dp.toPx()
  val tip = Offset(size.width - margin - 6.dp.toPx(), margin)
  val arrow = Path().apply {
    moveTo(tip.x, tip.y)
    lineTo(tip.x + 5.dp.toPx(), tip.y + 12.dp.toPx())
    lineTo(tip.x, tip.y + 9.dp.toPx())
    lineTo(tip.x - 5.dp.toPx(), tip.y + 12.dp.toPx())
    close()
  }
  drawPath(arrow, Palette.OnSurfaceVariant)
  val label = measurer.measure("N", style)
  drawText(label, topLeft = Offset(tip.x - label.size.width / 2f, tip.y + 13.dp.toPx()))
}

private fun DrawScope.drawScaleBar(projection: Projection, measurer: TextMeasurer, style: TextStyle) {
  val maxMeters = projection.metersPerPixel * size.width * 0.3
  val meters = SCALE_STEPS_M.lastOrNull { it <= maxMeters } ?: return
  val length = (meters / projection.metersPerPixel).toFloat()
  val margin = 12.dp.toPx()
  val y = size.height - margin
  val stroke = 2.dp.toPx()
  drawLine(Palette.OnSurfaceVariant, Offset(margin, y), Offset(margin + length, y), stroke)
  drawLine(Palette.OnSurfaceVariant, Offset(margin, y), Offset(margin, y - 5.dp.toPx()), stroke)
  drawLine(Palette.OnSurfaceVariant, Offset(margin + length, y), Offset(margin + length, y - 5.dp.toPx()), stroke)
  val label = measurer.measure(formatDistance(meters), style)
  drawText(label, topLeft = Offset(margin, y - 6.dp.toPx() - label.size.height))
}
