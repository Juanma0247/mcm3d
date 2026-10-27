package cc.juanma.mcm3d.core.baro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarometricTest {
  @Test
  fun standardSeaLevelIsZero() {
    assertEquals(0.0, pressureAltitudeMeters(STANDARD_SEA_LEVEL_HPA), 1e-9)
  }

  @Test
  fun manizalesPressureIsAboutTwoKilometers() {
    assertEquals(2000.0, pressureAltitudeMeters(795.0), 5.0)
  }

  @Test
  fun lowerPressureMeansHigher() {
    assertTrue(relativeAltitudeMeters(794.0, 795.0) > 0.0)
    assertEquals(0.0, relativeAltitudeMeters(795.0, 795.0), 1e-9)
  }
}
