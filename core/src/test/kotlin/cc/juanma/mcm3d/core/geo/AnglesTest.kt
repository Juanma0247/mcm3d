package cc.juanma.mcm3d.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnglesTest {
  @Test
  fun normalizesIntoZeroTo360() {
    assertEquals(10.0, normalizeDegrees(370.0), 1e-9)
    assertEquals(350.0, normalizeDegrees(-10.0), 1e-9)
    assertEquals(0.0, normalizeDegrees(360.0), 1e-9)
    assertEquals(0.0, normalizeDegrees(-720.0), 1e-9)
  }

  @Test
  fun tinyNegativeNeverReturns360() {
    val value = normalizeDegrees(-1e-15)
    assertTrue(value >= 0.0 && value < 360.0)
  }

  @Test
  fun signedDifferenceTakesShortestWay() {
    assertEquals(20.0, signedAngleDifferenceDegrees(10.0, 350.0), 1e-9)
    assertEquals(-20.0, signedAngleDifferenceDegrees(350.0, 10.0), 1e-9)
    assertEquals(90.0, signedAngleDifferenceDegrees(90.0, 0.0), 1e-9)
    assertEquals(-180.0, signedAngleDifferenceDegrees(180.0, 0.0), 1e-9)
  }
}
