package cc.juanma.mcm3d.core.route

import cc.juanma.mcm3d.core.geo.EARTH_RADIUS_M
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

data class RouteProgress(
  val alongM: Double,
  val offRouteM: Double,
  val remainingM: Double,
  val totalM: Double,
)

/**
 * Projects positions onto a route. It searches near the last match first, so loops and circular
 * routes (where the start and the end overlap) resolve to the part being walked; when the
 * position is far from that window it falls back to the whole route.
 */
class RouteProgressTracker(private val lats: DoubleArray, private val lons: DoubleArray) {
  private val along = DoubleArray(lats.size)
  private val segmentDistance = DoubleArray(lats.size)
  private val segmentAlong = DoubleArray(lats.size)
  private var lastAlong = Double.NaN

  init {
    require(lats.size == lons.size && lats.size >= 2)
    for (i in 1 until lats.size) {
      along[i] = along[i - 1] + segmentLength(i - 1)
    }
  }

  val totalM: Double get() = along.last()

  fun update(lat: Double, lon: Double): RouteProgress {
    val local = if (lastAlong.isNaN()) {
      null
    } else {
      search(lat, lon, lastAlong - BACK_WINDOW_M, lastAlong + AHEAD_WINDOW_M, lastAlong - BACKTRACK_TOLERANCE_M)
    }
    val best = if (local != null && local.distance <= LOCAL_ACCEPT_M) local else search(lat, lon, -1.0, Double.MAX_VALUE)!!
    lastAlong = best.along
    return RouteProgress(best.along, best.distance, totalM - best.along, totalM)
  }

  /** Along-route distance of a point without moving the tracker, for placing waypoints. */
  fun locate(lat: Double, lon: Double): RouteProgress {
    val best = search(lat, lon, -1.0, Double.MAX_VALUE)!!
    return RouteProgress(best.along, best.distance, totalM - best.along, totalM)
  }

  fun reset() {
    lastAlong = Double.NaN
  }

  private class Match(val along: Double, val distance: Double)

  private fun search(
    lat: Double,
    lon: Double,
    fromAlong: Double,
    toAlong: Double,
    preferFromAlong: Double = -Double.MAX_VALUE,
  ): Match? {
    val cosLat = cos(Math.toRadians(lat))
    var minDistance = Double.MAX_VALUE
    var minAlong = Double.NaN
    for (i in 0 until lats.size - 1) {
      if (along[i + 1] < fromAlong || along[i] > toAlong) {
        segmentDistance[i] = Double.NaN
        continue
      }
      val ax = (lons[i] - lon) * cosLat
      val ay = lats[i] - lat
      val dx = (lons[i + 1] - lon) * cosLat - ax
      val dy = lats[i + 1] - lat - ay
      val lengthSquared = dx * dx + dy * dy
      val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
      segmentDistance[i] = Math.toRadians(hypot(ax + dx * t, ay + dy * t)) * EARTH_RADIUS_M
      segmentAlong[i] = along[i] + (along[i + 1] - along[i]) * t
      if (segmentDistance[i] < minDistance) {
        minDistance = segmentDistance[i]
        minAlong = segmentAlong[i]
      }
    }
    if (minDistance == Double.MAX_VALUE) return null
    // Near-equal matches on separate passes over the same ground: the earliest one not behind the
    // user wins. On a circular route the start beats the end; on an out-and-back stretch the way
    // back beats the way out. Neighbouring segments of one pass all resolve to the exact match.
    val closest = Match(minAlong, max(minDistance, 0.0))
    var fallback: Match? = null
    for (i in 0 until lats.size - 1) {
      val d = segmentDistance[i]
      if (d.isNaN() || d > minDistance + TIE_M) continue
      val match = if (abs(segmentAlong[i] - minAlong) < SAME_PASS_M) closest else Match(segmentAlong[i], max(d, 0.0))
      if (match.along >= preferFromAlong) return match
      if (fallback == null) fallback = match
    }
    return fallback
  }

  private fun segmentLength(i: Int): Double {
    val cosLat = cos(Math.toRadians((lats[i] + lats[i + 1]) / 2))
    val dx = (lons[i + 1] - lons[i]) * cosLat
    val dy = lats[i + 1] - lats[i]
    return Math.toRadians(hypot(dx, dy)) * EARTH_RADIUS_M
  }

  private companion object {
    const val BACK_WINDOW_M = 150.0
    const val AHEAD_WINDOW_M = 600.0
    const val LOCAL_ACCEPT_M = 60.0
    const val TIE_M = 3.0
    const val BACKTRACK_TOLERANCE_M = 10.0
    const val SAME_PASS_M = 50.0
  }
}

/** On/off route state with hysteresis, scaled by the GNSS accuracy so a poor fix does not raise false alarms. */
class OffRouteDetector(
  private val leaveM: Double = 35.0,
  private val returnM: Double = 20.0,
) {
  var offRoute = false
    private set

  fun update(offRouteM: Double, accuracyM: Double): Boolean {
    val accuracy = if (accuracyM.isFinite()) accuracyM else 10.0
    offRoute = if (offRoute) {
      offRouteM > max(returnM, accuracy * 1.5)
    } else {
      offRouteM > max(leaveM, accuracy * 2.5)
    }
    return offRoute
  }
}
