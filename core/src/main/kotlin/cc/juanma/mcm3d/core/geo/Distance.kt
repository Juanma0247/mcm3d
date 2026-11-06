package cc.juanma.mcm3d.core.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

const val EARTH_RADIUS_M = 6_371_008.8

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
  val dLat = Math.toRadians(lat2 - lat1)
  val dLon = Math.toRadians(lon2 - lon1)
  val a = sin(dLat / 2).let { it * it } +
    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
  return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/** Initial great-circle bearing from the first point to the second, in degrees clockwise from true north. */
fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
  val p1 = Math.toRadians(lat1)
  val p2 = Math.toRadians(lat2)
  val dLon = Math.toRadians(lon2 - lon1)
  val y = sin(dLon) * cos(p2)
  val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
  return normalizeDegrees(Math.toDegrees(kotlin.math.atan2(y, x)))
}
