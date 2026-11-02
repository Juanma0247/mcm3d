package cc.juanma.mcm3d.core.recording

import cc.juanma.mcm3d.core.geo.distanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingFiltersTest {
  private val lat = 5.068
  private val lon = -75.517
  private val metersPerDegree = 111_195.0

  @Test
  fun distanceMatchesOneDegreeOfLatitude() {
    assertEquals(metersPerDegree, distanceMeters(0.0, 0.0, 1.0, 0.0), 5.0)
  }

  @Test
  fun fixFilterDropsJitterAndKeepsDisplacement() {
    val filter = FixFilter()
    assertTrue(filter.accept(0, lat, lon, 5.0, Double.NaN))
    assertFalse(filter.accept(1_000, lat + 2 / metersPerDegree, lon, 5.0, Double.NaN))
    assertTrue(filter.accept(2_000, lat + 6 / metersPerDegree, lon, 5.0, Double.NaN))
  }

  @Test
  fun fixFilterKeepsHeartbeatAndTurns() {
    val filter = FixFilter()
    filter.accept(0, lat, lon, 5.0, 0.0)
    assertTrue(filter.accept(60_000, lat, lon, 5.0, 0.0))
    assertTrue(filter.accept(61_000, lat + 3 / metersPerDegree, lon, 5.0, 90.0))
  }

  @Test
  fun changeFilterIgnoresSmallChanges() {
    val filter = ChangeFilter(minChange = 0.05, heartbeatMs = 60_000)
    assertTrue(filter.accept(0, 795.00))
    assertFalse(filter.accept(1_000, 795.02))
    assertTrue(filter.accept(2_000, 795.06))
    assertTrue(filter.accept(62_000, 795.06))
  }

  @Test
  fun becomesStillAfterWindowInsideRadius() {
    val detector = StillnessDetector(windowMs = 180_000)
    detector.onFix(0, lat, lon, 8.0, 0)
    detector.onFix(100_000, lat + 10 / metersPerDegree, lon, 8.0, 5)
    assertFalse(detector.isStill(100_000, 5))
    assertTrue(detector.isStill(181_000, 10))
  }

  @Test
  fun leavingRadiusOrWalkingRestartsWindow() {
    val detector = StillnessDetector(windowMs = 180_000)
    detector.onFix(0, lat, lon, 8.0, 0)
    detector.onFix(150_000, lat + 50 / metersPerDegree, lon, 8.0, 0)
    assertFalse(detector.isStill(200_000, 0))
    assertFalse(detector.isStill(400_000, 100))
    assertTrue(detector.isStill(581_000, 110))
  }

  @Test
  fun indoorsWithoutFixesStepsDecide() {
    val detector = StillnessDetector(windowMs = 180_000)
    detector.restart(0, 0)
    assertTrue(detector.isStill(180_000, 10))
  }
}
