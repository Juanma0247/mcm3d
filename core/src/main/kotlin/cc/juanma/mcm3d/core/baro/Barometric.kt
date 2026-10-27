package cc.juanma.mcm3d.core.baro

import kotlin.math.pow

const val STANDARD_SEA_LEVEL_HPA = 1013.25

fun pressureAltitudeMeters(pressureHpa: Double, seaLevelHpa: Double = STANDARD_SEA_LEVEL_HPA): Double =
  44330.0 * (1.0 - (pressureHpa / seaLevelHpa).pow(1.0 / 5.255))

fun relativeAltitudeMeters(pressureHpa: Double, referenceHpa: Double): Double =
  pressureAltitudeMeters(pressureHpa) - pressureAltitudeMeters(referenceHpa)
