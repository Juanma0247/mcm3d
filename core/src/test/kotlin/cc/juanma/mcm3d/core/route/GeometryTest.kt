package cc.juanma.mcm3d.core.route

import cc.juanma.mcm3d.core.geo.distanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class GeometryTest {
  private val metersPerDegree = 111_195.0

  private fun north(meters: Double, elevation: Double = Double.NaN) =
    TrackPoint(5.0 + meters / metersPerDegree, -75.4, elevation)

  @Test
  fun densifyLimitsSpacingAndKeepsVertices() {
    val points = listOf(north(0.0, 2000.0), north(0.0, 2000.0), north(95.0, 2095.0))
    val dense = densify(points, 10.0)
    assertEquals(11, dense.size)
    assertEquals(points.last(), dense.last())
    val distances = cumulativeDistances(dense)
    assertEquals(95.0, distances.last(), 0.5)
    for (i in 1 until dense.size) assertTrue(distances[i] - distances[i - 1] <= 10.0 + 1e-6)
    assertEquals(2047.5, dense[5].elevation, 1.0)
  }

  @Test
  fun boundsExpandByMeters() {
    val bounds = Bounds.of(listOf(north(0.0), north(1000.0)))!!
    val expanded = bounds.expandedBy(500.0)
    assertEquals(500.0, distanceMeters(bounds.minLat, bounds.minLon, expanded.minLat, bounds.minLon), 1.0)
    assertEquals(500.0, distanceMeters(bounds.minLat, bounds.minLon, bounds.minLat, expanded.minLon), 1.0)
  }

  @Test
  fun smoothingKeepsLevelAndRemovesNoise() {
    val n = 400
    val distances = DoubleArray(n) { it * 5.0 }
    val noisy = DoubleArray(n) { 2000.0 + if (it % 2 == 0) 4.0 else -4.0 }
    noisy[10] = Double.NaN
    val smooth = smoothAlongDistance(distances, noisy, 20.0)
    for (i in 20 until n - 20) assertEquals(2000.0, smooth[i], 0.5)
    assertTrue(!smooth[10].isNaN())
  }

  @Test
  fun gainIgnoresNoiseBelowThreshold() {
    val gain = GainAccumulator(3.0)
    listOf(100.0, 101.0, 99.5, 101.5, 100.0).forEach(gain::add)
    assertEquals(0.0, gain.ascentM, 1e-9)
    listOf(110.0, 108.0, 120.0, 100.0).forEach(gain::add)
    assertEquals(20.0, gain.ascentM, 1e-9)
    assertEquals(20.0, gain.descentM, 1e-9)
  }

  @Test
  fun statsNeedElevationOnMostPoints() {
    val flat = listOf(north(0.0), north(100.0), north(200.0, 2000.0))
    val stats = RouteStats.of(flat)
    assertEquals(200.0, stats.distanceM, 0.5)
    assertNull(stats.ascentM)
    val hill = (0..100).map { north(it * 10.0, 2000.0 + 50 * sin(it / 100.0 * Math.PI)) }
    val hillStats = RouteStats.of(hill)
    assertEquals(50.0, hillStats.ascentM!!, 3.0)
    assertEquals(2050.0, hillStats.maxElevationM!!, 0.1)
  }

  @Test
  fun trackCodecRoundTripsAndToleratesTruncation() {
    val start = 1_762_000_000_000
    val points = listOf(
      TrackPoint(5.0612345, -75.4987654, 2031.7, start + 1_000),
      TrackPoint(5.0612400, -75.4987600, Double.NaN, TrackPoint.NO_TIME),
    )
    val bytes = TrackCodec.encode(start, points)
    val track = TrackCodec.decode(bytes)
    assertEquals(start, track.startTimeMs)
    assertEquals(2, track.points.size)
    assertEquals(points[0].lat, track.points[0].lat, 1e-7)
    assertEquals(points[0].lon, track.points[0].lon, 1e-7)
    assertEquals(2031.7, track.points[0].elevation, 1e-3)
    assertEquals(start + 1_000, track.points[0].timeMs)
    assertTrue(track.points[1].elevation.isNaN())
    assertEquals(TrackPoint.NO_TIME, track.points[1].timeMs)
    val cut = TrackCodec.decode(bytes.copyOf(bytes.size - 5))
    assertEquals(1, cut.points.size)
  }

  @Test
  fun progressFollowsRouteAndMeasuresOffset() {
    val route = (0..100).map { north(it * 10.0) }
    val tracker = RouteProgressTracker(route.map { it.lat }.toDoubleArray(), route.map { it.lon }.toDoubleArray())
    assertEquals(1000.0, tracker.totalM, 1.0)
    val east = 20.0 / (metersPerDegree * kotlin.math.cos(Math.toRadians(5.0)))
    val progress = tracker.update(5.0 + 300.0 / metersPerDegree, -75.4 + east)
    assertEquals(300.0, progress.alongM, 1.0)
    assertEquals(20.0, progress.offRouteM, 0.5)
    assertEquals(700.0, progress.remainingM, 1.0)
  }

  @Test
  fun circularRouteStartsAtTheBeginningAndEndsAtTheEnd() {
    val out = (0..50).map { north(it * 10.0) }
    val loop = out + out.reversed().drop(1)
    val tracker = RouteProgressTracker(loop.map { it.lat }.toDoubleArray(), loop.map { it.lon }.toDoubleArray())
    assertEquals(0.0, tracker.update(5.0, -75.4).alongM, 1.0)
    for (m in listOf(100.0, 300.0, 500.0)) tracker.update(5.0 + m / metersPerDegree, -75.4)
    for (m in listOf(400.0, 200.0, 50.0)) {
      val p = tracker.update(5.0 + m / metersPerDegree, -75.4)
      assertTrue("along ${p.alongM} for $m", abs(p.alongM - (1000.0 - m)) < 2.0)
    }
  }

  @Test
  fun offRouteDetectorUsesHysteresisAndAccuracy() {
    val detector = OffRouteDetector(leaveM = 35.0, returnM = 20.0)
    assertTrue(!detector.update(30.0, 4.0))
    assertTrue(!detector.update(40.0, 20.0))
    assertTrue(detector.update(40.0, 4.0))
    assertTrue(detector.update(25.0, 4.0))
    assertTrue(!detector.update(15.0, 4.0))
  }
}
