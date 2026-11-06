package cc.juanma.mcm3d.core.baro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

class AltitudeFusionTest {
  private fun pressureFor(altitude: Double): Double =
    STANDARD_SEA_LEVEL_HPA * Math.pow(1.0 - altitude / 44330.0, 5.255)

  @Test
  fun barometerDriftIsRemovedByGnss() {
    val fusion = AltitudeFusion()
    val random = Random(7)
    val truth = 2000.0
    var worst = 0.0
    for (s in 0..7_200) {
      val driftM = 7.0 * s / 3600.0
      fusion.onPressure(pressureFor(truth + driftM))
      fusion.onGnss(s * 1000L, truth + random.nextGaussian() * 3.0, 2.5)
      if (s > 600) worst = maxOf(worst, abs(fusion.altitude() - truth))
    }
    assertTrue("worst error $worst m", worst < 2.0)
  }

  @Test
  fun barometerTracksFastClimbs() {
    val fusion = AltitudeFusion()
    for (s in 0..600) {
      fusion.onPressure(pressureFor(2000.0))
      fusion.onGnss(s * 1000L, 2000.0, 2.5)
    }
    val random = Random(3)
    for (s in 601..660) {
      val altitude = 2000.0 + (s - 600) / 3.0
      fusion.onPressure(pressureFor(altitude))
      fusion.onGnss(s * 1000L, altitude + random.nextGaussian() * 3.0, 2.5)
    }
    assertEquals(2020.0, fusion.altitude(), 0.5)
  }

  @Test
  fun worksWithoutBarometer() {
    val fusion = AltitudeFusion()
    assertTrue(fusion.altitude().isNaN())
    for (s in 0..120) fusion.onGnss(s * 1000L, if (s % 2 == 0) 1996.0 else 2004.0, 3.0)
    assertEquals(2000.0, fusion.altitude(), 1.5)
  }
}
