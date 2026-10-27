package cc.juanma.mcm3d.core.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {
  @Test
  fun rateMeterMeasuresEventFrequency() {
    val meter = RateMeter(windowNanos = 1_000_000_000L)
    var t = 0L
    repeat(301) {
      meter.onEvent(t)
      t += 10_000_000L
    }
    assertEquals(100.0, meter.rateHz, 0.5)
  }

  @Test
  fun rateMeterIsZeroBeforeFirstWindow() {
    val meter = RateMeter(windowNanos = 1_000_000_000L)
    meter.onEvent(0L)
    meter.onEvent(10_000_000L)
    assertEquals(0.0, meter.rateHz, 0.0)
  }

  @Test
  fun windowedMeanKeepsLastValues() {
    val mean = WindowedMean(3)
    assertNull(mean.mean)
    mean.add(1.0)
    mean.add(2.0)
    mean.add(3.0)
    assertEquals(2.0, mean.mean!!, 1e-9)
    mean.add(4.0)
    assertEquals(3.0, mean.mean!!, 1e-9)
    assertEquals(3, mean.count)
  }

  @Test
  fun exponentialSmootherStartsAtFirstSample() {
    val smoother = ExponentialSmoother(0.5)
    assertFalse(smoother.isInitialized)
    assertEquals(10.0, smoother.add(10.0), 1e-9)
    assertEquals(15.0, smoother.add(20.0), 1e-9)
    smoother.reset()
    assertEquals(4.0, smoother.add(4.0), 1e-9)
  }
}
