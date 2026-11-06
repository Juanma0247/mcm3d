package cc.juanma.mcm3d.core.route

import cc.juanma.mcm3d.core.geo.bearingDegrees
import cc.juanma.mcm3d.core.geo.distanceMeters
import cc.juanma.mcm3d.core.geo.signedAngleDifferenceDegrees
import kotlin.math.abs

enum class TurnDirection { SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, U_TURN }

/** A change of direction along the route; positive angles turn right (clockwise). */
class Turn(val alongM: Double, val angleDeg: Double) {
  val direction: TurnDirection
    get() {
      val a = abs(angleDeg)
      val right = angleDeg > 0
      return when {
        a >= 160 -> TurnDirection.U_TURN
        a >= 115 -> if (right) TurnDirection.SHARP_RIGHT else TurnDirection.SHARP_LEFT
        a >= 60 -> if (right) TurnDirection.RIGHT else TurnDirection.LEFT
        else -> if (right) TurnDirection.SLIGHT_RIGHT else TurnDirection.SLIGHT_LEFT
      }
    }
}

/**
 * Finds turns by comparing the direction [windowM] before and after each sampled point, so GNSS
 * jitter in recorded routes does not look like turning. Near turns keep only the sharpest one.
 */
object TurnDetector {
  fun detect(
    tracker: RouteProgressTracker,
    windowM: Double = 20.0,
    minAngleDeg: Double = 40.0,
    spacingM: Double = 5.0,
    suppressM: Double = 35.0,
  ): List<Turn> {
    val total = tracker.totalM
    if (total < 2 * windowM) return emptyList()
    val candidates = ArrayList<Turn>()
    var s = windowM
    while (s <= total - windowM) {
      val (aLat, aLon) = tracker.pointAt(s - windowM)
      val (bLat, bLon) = tracker.pointAt(s)
      val (cLat, cLon) = tracker.pointAt(s + windowM)
      val incoming = bearingDegrees(aLat, aLon, bLat, bLon)
      val outgoing = bearingDegrees(bLat, bLon, cLat, cLon)
      val angle = signedAngleDifferenceDegrees(outgoing, incoming)
      if (abs(angle) >= minAngleDeg) candidates.add(Turn(s, angle))
      s += spacingM
    }
    val kept = ArrayList<Turn>()
    for (turn in candidates.sortedByDescending { abs(it.angleDeg) }) {
      if (kept.none { abs(it.alongM - turn.alongM) < suppressM }) kept.add(turn)
    }
    return kept.sortedBy { it.alongM }
  }
}

/** What the walker should do now. [bearingDeg] is the direction to walk, clockwise from true north. */
sealed interface Instruction {
  val bearingDeg: Double

  data class GoToStart(val distanceM: Double, override val bearingDeg: Double) : Instruction

  data class Rejoin(val distanceM: Double, override val bearingDeg: Double) : Instruction

  data class TurnAhead(val direction: TurnDirection, val distanceM: Double, override val bearingDeg: Double) : Instruction

  data class Continue(val distanceM: Double, override val bearingDeg: Double) : Instruction

  data class Arrived(override val bearingDeg: Double = Double.NaN) : Instruction
}

class Guidance(val progress: RouteProgress, val offRoute: Boolean, val instruction: Instruction)

/**
 * Turns positions into instructions: first how to reach the start, then the next turn or the
 * distance to keep walking, how to come back after leaving the route, and the arrival.
 */
class RouteGuide(lats: DoubleArray, lons: DoubleArray) {
  val tracker = RouteProgressTracker(lats, lons)
  val turns: List<Turn> = TurnDetector.detect(tracker)
  private val offRouteDetector = OffRouteDetector()
  private var started = false

  fun update(lat: Double, lon: Double, accuracyM: Double): Guidance {
    val progress = tracker.update(lat, lon)
    val off = offRouteDetector.update(progress.offRouteM, accuracyM)
    if (!off) started = true
    val instruction = when {
      !started -> {
        val (startLat, startLon) = tracker.pointAt(0.0)
        Instruction.GoToStart(distanceMeters(lat, lon, startLat, startLon), bearingDegrees(lat, lon, startLat, startLon))
      }
      off -> {
        val (nearLat, nearLon) = tracker.pointAt(progress.alongM)
        Instruction.Rejoin(progress.offRouteM, bearingDegrees(lat, lon, nearLat, nearLon))
      }
      progress.remainingM <= ARRIVAL_M -> Instruction.Arrived()
      else -> {
        val (aheadLat, aheadLon) = tracker.pointAt(progress.alongM + LOOK_AHEAD_M)
        val bearing = bearingDegrees(lat, lon, aheadLat, aheadLon)
        val next = turns.firstOrNull { it.alongM > progress.alongM + PASSED_TURN_M }
        val toNext = (next?.alongM ?: tracker.totalM) - progress.alongM
        if (next != null && toNext <= TURN_NOTICE_M) {
          Instruction.TurnAhead(next.direction, toNext, bearing)
        } else {
          Instruction.Continue(toNext, bearing)
        }
      }
    }
    return Guidance(progress, off, instruction)
  }

  private companion object {
    const val ARRIVAL_M = 25.0
    const val LOOK_AHEAD_M = 30.0
    const val PASSED_TURN_M = 5.0
    const val TURN_NOTICE_M = 120.0
  }
}
