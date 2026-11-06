package cc.juanma.mcm3d.core.recording

import cc.juanma.mcm3d.core.geo.distanceMeters
import kotlin.math.max

/**
 * Decides when the user has stayed in one place long enough to pause GNSS and motion sensors.
 * Stillness needs [windowMs] without leaving a radius around an anchor fix and with fewer than
 * [maxSteps] steps; indoors, where fixes stop arriving, the step count alone decides.
 *
 * After a wake-up without evidence of walking (significant motion with no steps) the detector
 * starts in probation: [probationMs] are enough to go back to still. In the 2026-11-03 field test
 * 15 of 24 wake-ups were the phone being handled, and each kept GNSS on for the full window.
 */
class StillnessDetector(
  private val windowMs: Long = 180_000L,
  private val minRadiusM: Double = 30.0,
  private val maxSteps: Long = 40L,
  private val probationMs: Long = 60_000L,
  private val probationMaxSteps: Long = 12L,
) {
  private var anchorTimeMs = Long.MIN_VALUE
  private var anchorSteps = 0L
  private var hasAnchorFix = false
  private var anchorLat = 0.0
  private var anchorLon = 0.0

  var inProbation = false
    private set

  fun restart(timeMs: Long, steps: Long, probation: Boolean = false) {
    anchorTimeMs = timeMs
    anchorSteps = steps
    hasAnchorFix = false
    inProbation = probation
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
    val walked = steps - anchorSteps
    if (inProbation && walked >= probationMaxSteps) {
      restart(timeMs, steps)
      return false
    }
    if (walked >= maxSteps) {
      restart(timeMs, steps)
      return false
    }
    return timeMs - anchorTimeMs >= if (inProbation) probationMs else windowMs
  }

  private fun setAnchorFix(lat: Double, lon: Double) {
    hasAnchorFix = true
    anchorLat = lat
    anchorLon = lon
  }
}
