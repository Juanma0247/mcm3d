package cc.juanma.mcm3d.core.route

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Gaussian smoothing along the distance axis (not the index axis, so uneven spacing is fine).
 * Missing values are skipped and filled from their neighbours.
 */
fun smoothAlongDistance(distances: DoubleArray, values: DoubleArray, sigmaM: Double): DoubleArray {
  require(distances.size == values.size)
  val n = values.size
  val out = DoubleArray(n) { Double.NaN }
  if (n == 0 || sigmaM <= 0) return values.copyOf()
  val reach = 3 * sigmaM
  val inverse = 1.0 / (2 * sigmaM * sigmaM)
  var start = 0
  for (i in 0 until n) {
    val center = distances[i]
    while (distances[start] < center - reach) start++
    var weightSum = 0.0
    var sum = 0.0
    var j = start
    while (j < n && distances[j] <= center + reach) {
      val v = values[j]
      if (!v.isNaN()) {
        val d = distances[j] - center
        val w = exp(-d * d * inverse)
        weightSum += w
        sum += w * v
      }
      j++
    }
    if (weightSum > 0) out[i] = sum / weightSum
  }
  return out
}

/**
 * Ascent and descent with hysteresis: a change only counts once the altitude moves [thresholdM]
 * away from the last turning point, so GNSS and model noise does not inflate the totals.
 */
class GainAccumulator(private val thresholdM: Double = DEFAULT_GAIN_THRESHOLD_M) {
  var ascentM = 0.0
    private set
  var descentM = 0.0
    private set
  private var reference = Double.NaN

  fun add(altitude: Double) {
    if (altitude.isNaN()) return
    if (reference.isNaN()) {
      reference = altitude
      return
    }
    val delta = altitude - reference
    if (delta >= thresholdM) {
      ascentM += delta
      reference = altitude
    } else if (-delta >= thresholdM) {
      descentM -= delta
      reference = altitude
    }
  }
}

data class RouteStats(
  val distanceM: Double,
  val ascentM: Double?,
  val descentM: Double?,
  val minElevationM: Double?,
  val maxElevationM: Double?,
  val durationMs: Long?,
) {
  companion object {
    fun of(points: List<TrackPoint>, distances: DoubleArray = cumulativeDistances(points)): RouteStats {
      val gain = GainAccumulator()
      var low = Double.MAX_VALUE
      var high = -Double.MAX_VALUE
      var withElevation = 0
      for (p in points) {
        if (!p.hasElevation) continue
        withElevation++
        gain.add(p.elevation)
        low = min(low, p.elevation)
        high = max(high, p.elevation)
      }
      val hasElevation = withElevation >= max(2, points.size / 2)
      val first = points.firstOrNull { it.hasTime }
      val last = points.lastOrNull { it.hasTime }
      return RouteStats(
        distanceM = distances.lastOrNull() ?: 0.0,
        ascentM = gain.ascentM.takeIf { hasElevation },
        descentM = gain.descentM.takeIf { hasElevation },
        minElevationM = low.takeIf { hasElevation },
        maxElevationM = high.takeIf { hasElevation },
        durationMs = if (first != null && last != null && last !== first) last.timeMs - first.timeMs else null,
      )
    }
  }
}

const val DEFAULT_GAIN_THRESHOLD_M = 3.0
