package cc.juanma.mcm3d.core.route

import cc.juanma.mcm3d.core.geo.EARTH_RADIUS_M
import cc.juanma.mcm3d.core.geo.distanceMeters
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

data class Bounds(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
  val centerLat: Double get() = (minLat + maxLat) / 2
  val centerLon: Double get() = (minLon + maxLon) / 2

  fun expandedBy(meters: Double): Bounds {
    val dLat = Math.toDegrees(meters / EARTH_RADIUS_M)
    val dLon = dLat / max(cos(Math.toRadians(centerLat)), 0.01)
    return Bounds(
      minLat = (minLat - dLat).coerceAtLeast(-85.0),
      minLon = (minLon - dLon).coerceAtLeast(-180.0),
      maxLat = (maxLat + dLat).coerceAtMost(85.0),
      maxLon = (maxLon + dLon).coerceAtMost(180.0),
    )
  }

  companion object {
    fun of(points: List<TrackPoint>): Bounds? {
      if (points.isEmpty()) return null
      var minLat = Double.MAX_VALUE
      var minLon = Double.MAX_VALUE
      var maxLat = -Double.MAX_VALUE
      var maxLon = -Double.MAX_VALUE
      for (p in points) {
        minLat = min(minLat, p.lat)
        minLon = min(minLon, p.lon)
        maxLat = max(maxLat, p.lat)
        maxLon = max(maxLon, p.lon)
      }
      return Bounds(minLat, minLon, maxLat, maxLon)
    }
  }
}

fun cumulativeDistances(points: List<TrackPoint>): DoubleArray {
  val out = DoubleArray(points.size)
  for (i in 1 until points.size) {
    val a = points[i - 1]
    val b = points[i]
    out[i] = out[i - 1] + distanceMeters(a.lat, a.lon, b.lat, b.lon)
  }
  return out
}

/**
 * Inserts points so that no segment is longer than [maxSpacingM] and drops repeated points. The
 * shape does not change; the extra points let the elevation model follow the terrain between the
 * sparse vertices of planned routes (Komoot leaves gaps of more than 250 m).
 */
fun densify(points: List<TrackPoint>, maxSpacingM: Double): List<TrackPoint> {
  if (points.isEmpty()) return points
  val out = ArrayList<TrackPoint>(points.size * 2)
  out.add(points[0])
  for (i in 1 until points.size) {
    val a = out.last()
    val b = points[i]
    val d = distanceMeters(a.lat, a.lon, b.lat, b.lon)
    if (d < MIN_SEGMENT_M) continue
    val pieces = ceil(d / maxSpacingM).toInt()
    for (k in 1 until pieces) {
      val f = k.toDouble() / pieces
      out.add(
        TrackPoint(
          lat = a.lat + (b.lat - a.lat) * f,
          lon = a.lon + (b.lon - a.lon) * f,
          elevation = interpolate(a.elevation, b.elevation, f),
        ),
      )
    }
    out.add(b)
  }
  return out
}

private fun interpolate(a: Double, b: Double, f: Double): Double = when {
  a.isNaN() -> b
  b.isNaN() -> a
  else -> a + (b - a) * f
}

private const val MIN_SEGMENT_M = 0.05
