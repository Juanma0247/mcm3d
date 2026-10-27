package cc.juanma.mcm3d.core.orientation

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class AttitudeTest {
  private fun matrixFromAxes(x: DoubleArray, y: DoubleArray, z: DoubleArray): FloatArray = floatArrayOf(
    x[0].toFloat(), y[0].toFloat(), z[0].toFloat(),
    x[1].toFloat(), y[1].toFloat(), z[1].toFloat(),
    x[2].toFloat(), y[2].toFloat(), z[2].toFloat(),
  )

  @Test
  fun uprightFacingNorth() {
    val attitude = Attitude.fromRotationMatrix(
      matrixFromAxes(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(0.0, -1.0, 0.0)),
    )
    assertEquals(0.0, attitude.cameraHeadingDeg, 1e-4)
    assertEquals(0.0, attitude.cameraElevationDeg, 1e-4)
    assertEquals(0.0, attitude.rollDeg, 1e-4)
  }

  @Test
  fun uprightFacingEast() {
    val attitude = Attitude.fromRotationMatrix(
      matrixFromAxes(doubleArrayOf(0.0, -1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(-1.0, 0.0, 0.0)),
    )
    assertEquals(90.0, attitude.cameraHeadingDeg, 1e-4)
    assertEquals(0.0, attitude.cameraElevationDeg, 1e-4)
  }

  @Test
  fun cameraTiltedUp() {
    val t = Math.toRadians(30.0)
    val attitude = Attitude.fromRotationMatrix(
      matrixFromAxes(
        doubleArrayOf(1.0, 0.0, 0.0),
        doubleArrayOf(0.0, -sin(t), cos(t)),
        doubleArrayOf(0.0, -cos(t), -sin(t)),
      ),
    )
    assertEquals(0.0, attitude.cameraHeadingDeg, 1e-4)
    assertEquals(30.0, attitude.cameraElevationDeg, 1e-4)
  }

  @Test
  fun rolledClockwise() {
    val p = Math.toRadians(20.0)
    val attitude = Attitude.fromRotationMatrix(
      matrixFromAxes(
        doubleArrayOf(cos(p), 0.0, -sin(p)),
        doubleArrayOf(sin(p), 0.0, cos(p)),
        doubleArrayOf(0.0, -1.0, 0.0),
      ),
    )
    assertEquals(20.0, attitude.rollDeg, 1e-4)
    assertEquals(0.0, attitude.cameraHeadingDeg, 1e-4)
  }

  @Test
  fun flatScreenUpTopTowardNorth() {
    val attitude = Attitude.fromRotationMatrix(
      matrixFromAxes(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0)),
    )
    assertEquals(0.0, attitude.topHeadingDeg, 1e-4)
    assertEquals(-90.0, attitude.cameraElevationDeg, 1e-4)
  }

  @Test
  fun referenceHeadingSwitchesToTopEdgeWhenFlat() {
    val upright = Attitude(cameraHeadingDeg = 90.0, cameraElevationDeg = 10.0, rollDeg = 0.0, topHeadingDeg = 0.0)
    val flat = upright.copy(cameraElevationDeg = -85.0)
    assertEquals(90.0, upright.referenceHeadingDeg, 0.0)
    assertEquals(0.0, flat.referenceHeadingDeg, 0.0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsFourByFourMatrix() {
    Attitude.fromRotationMatrix(FloatArray(16))
  }
}
