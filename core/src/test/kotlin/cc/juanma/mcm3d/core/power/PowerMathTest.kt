package cc.juanma.mcm3d.core.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerMathTest {
  @Test
  fun convertsMicroAmpsAndKeepsMilliAmps() {
    assertEquals(-450.0, PowerMath.currentToMilliAmps(-450_000L), 1e-9)
    assertEquals(-450.0, PowerMath.currentToMilliAmps(-450L), 1e-9)
  }

  @Test
  fun estimatesCapacityFromChargeCounter() {
    assertEquals(5000.0, PowerMath.estimateCapacityMah(2_500_000L, 50)!!, 1e-9)
    assertNull(PowerMath.estimateCapacityMah(0L, 50))
    assertNull(PowerMath.estimateCapacityMah(2_500L, 50))
  }

  @Test
  fun computesDrainPerHour() {
    assertEquals(5.0, PowerMath.drainPercentPerHour(250.0, 5000.0), 1e-9)
  }
}
