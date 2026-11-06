package cc.juanma.mcm3d.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import cc.juanma.mcm3d.ui.formatDistance
import cc.juanma.mcm3d.ui.formatElevation
import cc.juanma.mcm3d.ui.theme.Palette
import kotlin.math.max

private const val MIN_RANGE_M = 30.0
private const val MAX_CHART_POINTS = 1_500

/** Elevation against distance, with an optional marker for the current position along the route. */
@Composable
fun ElevationChart(
  distances: DoubleArray,
  elevations: DoubleArray,
  modifier: Modifier = Modifier,
  markerAlongM: Double? = null,
) {
  val measurer = rememberTextMeasurer()
  val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Palette.OnSurfaceVariant, fontFeatureSettings = "tnum")
  val gridColor = MaterialTheme.colorScheme.outline
  Box(
    modifier.drawWithCache {
      var low = Double.MAX_VALUE
      var high = -Double.MAX_VALUE
      for (e in elevations) {
        if (e.isNaN()) continue
        low = minOf(low, e)
        high = maxOf(high, e)
      }
      val total = distances.lastOrNull() ?: 0.0
      val valid = low <= high && total > 0
      if (high - low < MIN_RANGE_M) {
        val mid = (high + low) / 2
        low = mid - MIN_RANGE_M / 2
        high = mid + MIN_RANGE_M / 2
      }
      val highLabel = measurer.measure(formatElevation(high), labelStyle)
      val lowLabel = measurer.measure(formatElevation(low), labelStyle)
      val endLabel = measurer.measure(formatDistance(total), labelStyle)
      val left = max(highLabel.size.width, lowLabel.size.width) + 8.dp.toPx()
      val bottom = size.height - endLabel.size.height - 4.dp.toPx()
      val top = highLabel.size.height / 2f
      val width = size.width - left
      fun x(d: Double) = (left + d / total * width).toFloat()
      fun y(e: Double) = (bottom - (e - low) / (high - low) * (bottom - top)).toFloat()

      val line = Path()
      val fill = Path()
      if (valid) {
        val step = max(1, distances.size / MAX_CHART_POINTS)
        var started = false
        var lastX = left
        var i = 0
        while (i < distances.size) {
          val e = elevations[i]
          if (!e.isNaN()) {
            val px = x(distances[i])
            val py = y(e)
            if (!started) {
              line.moveTo(px, py)
              fill.moveTo(px, bottom)
              fill.lineTo(px, py)
              started = true
            } else {
              line.lineTo(px, py)
              fill.lineTo(px, py)
            }
            lastX = px
          }
          i = if (i + step >= distances.size && i != distances.size - 1) distances.size - 1 else i + step
        }
        fill.lineTo(lastX, bottom)
        fill.close()
      }
      val gradient = Brush.verticalGradient(
        listOf(Palette.Accent.copy(alpha = 0.35f), Palette.Accent.copy(alpha = 0.02f)),
        startY = top,
        endY = bottom,
      )
      val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
      onDrawBehind {
        drawLine(gridColor, Offset(left, top), Offset(size.width, top), 1.dp.toPx(), pathEffect = dash)
        drawLine(gridColor, Offset(left, bottom), Offset(size.width, bottom), 1.dp.toPx())
        drawText(highLabel, topLeft = Offset(0f, 0f))
        drawText(lowLabel, topLeft = Offset(0f, bottom - lowLabel.size.height / 2f))
        drawText(endLabel, topLeft = Offset(size.width - endLabel.size.width, bottom + 4.dp.toPx()))
        if (!valid) return@onDrawBehind
        drawPath(fill, gradient)
        drawPath(line, Palette.Accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (markerAlongM != null && markerAlongM in 0.0..total) {
          val mx = x(markerAlongM)
          drawLine(Palette.Info, Offset(mx, top), Offset(mx, bottom), 2.dp.toPx())
        }
      }
    },
  )
}
