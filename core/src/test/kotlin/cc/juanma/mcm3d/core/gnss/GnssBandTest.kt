package cc.juanma.mcm3d.core.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssBandTest {
  @Test
  fun classifiesCommonCarriers() {
    assertEquals(GnssBand.L1_E1, GnssBand.fromCarrierHz(1575.42e6))
    assertEquals(GnssBand.L5_E5A, GnssBand.fromCarrierHz(1176.45e6))
    assertEquals(GnssBand.B1I, GnssBand.fromCarrierHz(1561.098e6))
    assertEquals(GnssBand.G1, GnssBand.fromCarrierHz(1602.5625e6))
    assertEquals(GnssBand.UNKNOWN, GnssBand.fromCarrierHz(1000e6))
  }

  @Test
  fun marksSecondFrequencyBands() {
    assertTrue(GnssBand.L5_E5A.isSecondFrequency)
    assertFalse(GnssBand.L1_E1.isSecondFrequency)
  }
}
