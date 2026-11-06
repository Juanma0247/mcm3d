package cc.juanma.mcm3d.core.baro

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Complementary filter for altitude. The barometer follows height changes within decimeters but
 * drifts with the weather: in the 2026-11-03 field test the pressure fell 3.5 hPa at the same spot
 * (about 37 m of false climb, 5 to 7 m per hour). GNSS altitude is noisy (about 3 m) but does not
 * drift. The filter keeps barometric altitude plus a slowly adapting offset that the GNSS
 * altitude pulls toward the truth with a time constant of [timeConstantS].
 *
 * Without a barometer the GNSS altitude alone is smoothed with [gnssOnlyTimeConstantS].
 */
class AltitudeFusion(
  private val timeConstantS: Double = 300.0,
  private val gnssOnlyTimeConstantS: Double = 15.0,
  private val referenceAccuracyM: Double = 2.5,
  private val driftMemoryS: Double = 1_800.0,
) {
  private var baroAltitude = Double.NaN
  private var offset = Double.NaN
  private var offsetUsesBarometer = false
  private var memoryS = 0.0
  private var lastGnssMs = Long.MIN_VALUE

  val hasEstimate: Boolean get() = !offset.isNaN()

  fun onPressure(pressureHpa: Double) {
    if (pressureHpa.isFinite() && pressureHpa > 0) baroAltitude = pressureAltitudeMeters(pressureHpa)
  }

  fun onGnss(timeMs: Long, altitudeM: Double, verticalAccuracyM: Double) {
    if (!altitudeM.isFinite()) return
    val usesBarometer = !baroAltitude.isNaN()
    if (usesBarometer != offsetUsesBarometer) {
      offset = Double.NaN
      memoryS = 0.0
      offsetUsesBarometer = usesBarometer
    }
    val base = if (usesBarometer) baroAltitude else 0.0
    val measured = altitudeM - base
    val dt = if (lastGnssMs == Long.MIN_VALUE) 1.0 else ((timeMs - lastGnssMs) / 1000.0).coerceIn(0.0, MAX_STEP_S)
    lastGnssMs = timeMs
    val accuracy = if (verticalAccuracyM.isFinite() && verticalAccuracyM > 0) verticalAccuracyM else referenceAccuracyM * 2
    val ratio = referenceAccuracyM / max(accuracy, referenceAccuracyM)
    val weight = dt * ratio * ratio
    if (weight <= 0.0) return
    val tau = if (usesBarometer) timeConstantS else gnssOnlyTimeConstantS
    memoryS = min(memoryS + weight, tau)
    offset = if (offset.isNaN()) measured else offset + weight / memoryS * (measured - offset)
  }

  /** After a pause the barometric offset is stale by the drift in between, so older GNSS evidence counts less. */
  fun relax(gapMs: Long) {
    memoryS *= exp(-gapMs / 1000.0 / driftMemoryS)
  }

  fun altitude(): Double = when {
    offset.isNaN() -> Double.NaN
    offsetUsesBarometer -> baroAltitude + offset
    else -> offset
  }

  private companion object {
    const val MAX_STEP_S = 10.0
  }
}
