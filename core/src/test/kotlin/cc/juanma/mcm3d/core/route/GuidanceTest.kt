package cc.juanma.mcm3d.core.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class GuidanceTest {
  private val metersPerDegree = 111_195.0
  private val lat0 = 5.0
  private val lon0 = -75.4
  private val lonScale = metersPerDegree * cos(Math.toRadians(lat0))

  private fun at(north: Double, east: Double) = Pair(lat0 + north / metersPerDegree, lon0 + east / lonScale)

  /** 300 m north, then a right turn and 200 m east. */
  private fun lRoute(): RouteGuide {
    val points = (0..30).map { at(it * 10.0, 0.0) } + (1..20).map { at(300.0, it * 10.0) }
    return RouteGuide(points.map { it.first }.toDoubleArray(), points.map { it.second }.toDoubleArray())
  }

  @Test
  fun detectsTheRightTurn() {
    val turns = lRoute().turns
    assertEquals(1, turns.size)
    assertEquals(300.0, turns[0].alongM, 10.0)
    assertEquals(TurnDirection.RIGHT, turns[0].direction)
  }

  @Test
  fun leadsToTheStartThenAlongTheRoute() {
    val guide = lRoute()
    val (fLat, fLon) = at(-200.0, 0.0)
    val far = guide.update(fLat, fLon, 4.0).instruction
    assertTrue(far is Instruction.GoToStart)
    assertEquals(200.0, (far as Instruction.GoToStart).distanceM, 2.0)
    assertEquals(0.0, far.bearingDeg, 1.0)

    val (sLat, sLon) = at(10.0, 0.0)
    val walking = guide.update(sLat, sLon, 4.0).instruction
    assertTrue(walking is Instruction.Continue)
    assertEquals(290.0, (walking as Instruction.Continue).distanceM, 12.0)

    val (tLat, tLon) = at(220.0, 0.0)
    val turn = guide.update(tLat, tLon, 4.0).instruction
    assertTrue(turn is Instruction.TurnAhead)
    assertEquals(TurnDirection.RIGHT, (turn as Instruction.TurnAhead).direction)

    val (oLat, oLon) = at(300.0, 100.0 + 0.0).let { at(360.0, 100.0) }
    val off = guide.update(oLat, oLon, 4.0).instruction
    assertTrue(off is Instruction.Rejoin)
    assertEquals(180.0, off.bearingDeg, 2.0)

    val (eLat, eLon) = at(300.0, 190.0)
    assertTrue(guide.update(eLat, eLon, 4.0).instruction is Instruction.Arrived)
  }

  @Test
  fun straightJitterIsNotATurn() {
    val points = (0..60).map { at(it * 10.0, if (it % 2 == 0) 2.0 else -2.0) }
    val guide = RouteGuide(points.map { it.first }.toDoubleArray(), points.map { it.second }.toDoubleArray())
    assertTrue(guide.turns.isEmpty())
  }
}
