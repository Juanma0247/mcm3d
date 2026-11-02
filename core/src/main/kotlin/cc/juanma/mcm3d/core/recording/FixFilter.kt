package cc.juanma.mcm3d.core.recording

import cc.juanma.mcm3d.core.geo.distanceMeters
import cc.juanma.mcm3d.core.geo.signedAngleDifferenceDegrees
import kotlin.math.abs

/** Keeps a fix only when it adds information: displacement, a turn, a much better accuracy or a heartbeat. */
class FixFilter(
  private val minDistanceM: Double = 5.0,
  private val minTurnDistanceM: Double = 2.0,
  private val minTurnDeg: Double = 25.0,
  private val heartbeatMs: Long = 60_000L,
) {
  private var hasLast = false
  private var lastLat = 0.0
  private var lastLon = 0.0
  private var lastAccuracy = Double.NaN
  private var lastBearing = Double.NaN
  private var lastTimeMs = 0L

  fun accept(timeMs: Long, lat: Double, lon: Double, accuracyM: Double, bearingDeg: Double): Boolean {
    val keep = !hasLast || run {
      val distance = distanceMeters(lastLat, lastLon, lat, lon)
      val turned = !bearingDeg.isNaN() && !lastBearing.isNaN() &&
        abs(signedAngleDifferenceDegrees(bearingDeg, lastBearing)) >= minTurnDeg
      val muchBetter = !accuracyM.isNaN() && !lastAccuracy.isNaN() && accuracyM < lastAccuracy * 0.5
      distance >= minDistanceM ||
        (turned && distance >= minTurnDistanceM) ||
        muchBetter ||
        timeMs - lastTimeMs >= heartbeatMs
    }
    if (keep) {
      hasLast = true
      lastLat = lat
      lastLon = lon
      lastAccuracy = accuracyM
      lastBearing = bearingDeg
      lastTimeMs = timeMs
    }
    return keep
  }

  fun reset() {
    hasLast = false
  }
}

/** Keeps a scalar sample when it moved at least [minChange] from the last kept one, or after [heartbeatMs]. */
class ChangeFilter(private val minChange: Double, private val heartbeatMs: Long) {
  private var last = Double.NaN
  private var lastTimeMs = 0L

  fun accept(timeMs: Long, value: Double): Boolean {
    val keep = last.isNaN() || abs(value - last) >= minChange || timeMs - lastTimeMs >= heartbeatMs
    if (keep) {
      last = value
      lastTimeMs = timeMs
    }
    return keep
  }
}
