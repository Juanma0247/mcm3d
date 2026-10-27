package cc.juanma.mcm3d.core.orientation

import cc.juanma.mcm3d.core.geo.normalizeDegrees
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

data class Attitude(
  val cameraHeadingDeg: Double,
  val cameraElevationDeg: Double,
  val rollDeg: Double,
  val topHeadingDeg: Double,
) {
  val usesCameraAxis: Boolean get() = abs(cameraElevationDeg) <= CAMERA_AXIS_LIMIT_DEG

  val referenceHeadingDeg: Double get() = if (usesCameraAxis) cameraHeadingDeg else topHeadingDeg

  companion object {
    const val CAMERA_AXIS_LIMIT_DEG = 60.0

    /**
     * [r] is the row-major 3x3 matrix from SensorManager.getRotationMatrixFromVector, mapping device
     * axes to the East-North-Up world frame. The back camera looks along the device -Z axis.
     */
    fun fromRotationMatrix(r: FloatArray): Attitude {
      require(r.size == 9) { "Expected a row-major 3x3 rotation matrix" }
      val east = -r[2].toDouble()
      val north = -r[5].toDouble()
      val up = (-r[8].toDouble()).coerceIn(-1.0, 1.0)
      return Attitude(
        cameraHeadingDeg = normalizeDegrees(Math.toDegrees(atan2(east, north))),
        cameraElevationDeg = Math.toDegrees(asin(up)),
        rollDeg = Math.toDegrees(atan2(-r[6].toDouble(), r[7].toDouble())),
        topHeadingDeg = normalizeDegrees(Math.toDegrees(atan2(r[1].toDouble(), r[4].toDouble()))),
      )
    }
  }
}
