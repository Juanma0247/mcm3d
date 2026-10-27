package cc.juanma.mcm3d.core.geo

fun normalizeDegrees(degrees: Double): Double {
  val r = degrees % 360.0
  if (r >= 0.0) return r
  val wrapped = r + 360.0
  return if (wrapped >= 360.0) 0.0 else wrapped
}

fun signedAngleDifferenceDegrees(a: Double, b: Double): Double {
  val d = normalizeDegrees(a - b)
  return if (d >= 180.0) d - 360.0 else d
}
