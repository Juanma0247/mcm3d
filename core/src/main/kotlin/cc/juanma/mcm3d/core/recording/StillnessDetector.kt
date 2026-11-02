package cc.juanma.mcm3d.core.recording

import cc.juanma.mcm3d.core.geo.distanceMeters
import kotlin.math.max

/**
 * Decides when the user has stayed in one place long enough to pause GNSS and motion sensors.
 * Stillness needs [windowMs] without leaving a radius around an anchor fix and with fewer than
 * [maxSteps] steps; indoors, where fixes stop arriving, the step count alone decides.
 */
class StillnessDetector(
  private val windowMs: Long = 180_000L,
  private val minRadiusM: Double = 30.0,
  private val maxSteps: Long = 40L,
) {
  private var anchorTimeMs = Long.MIN_VALUE
  private var anchorSteps = 0L
  private var hasAnchorFix = false
  private var anchorLat = 0.0
  private var anchorLon = 0.0

  fun restart(timeMs: Long, steps: Long) {
    anchorTimeMs = timeMs
    anchorSteps = steps
    hasAnchorFix = false
  }

  fun onFix(timeMs: Long, lat: Double, lon: Double, accuracyM: Double, steps: Long) {
    if (anchorTimeMs == Long.MIN_VALUE) restart(timeMs, steps)
    if (!hasAnchorFix) {
      setAnchorFix(lat, lon)
      return
    }
    val radius = max(minRadiusM, if (accuracyM.isNaN()) 0.0 else accuracyM * 1.5)
    if (distanceMeters(anchorLat, anchorLon, lat, lon) > radius) {
      restart(timeMs, steps)
      setAnchorFix(lat, lon)
    }
  }

  fun isStill(timeMs: Long, steps: Long): Boolean {
    if (anchorTimeMs == Long.MIN_VALUE) {
      restart(timeMs, steps)
      return false
    }
    if (steps - anchorSteps >= maxSteps) {
      restart(timeMs, steps)
      return false
    }
    return timeMs - anchorTimeMs >= windowMs
  }

  private fun setAnchorFix(lat: Double, lon: Double) {
    hasAnchorFix = true
    anchorLat = lat
    anchorLon = lon
  }
}
