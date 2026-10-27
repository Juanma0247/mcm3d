package cc.juanma.mcm3d.core.power

import kotlin.math.abs

object PowerMath {
  private const val MICRO_AMP_THRESHOLD = 20_000L
  private val PLAUSIBLE_CAPACITY_MAH = 500.0..20_000.0

  // Some vendors report BATTERY_PROPERTY_CURRENT_NOW in mA instead of the documented µA.
  fun currentToMilliAmps(raw: Long): Double =
    if (abs(raw) >= MICRO_AMP_THRESHOLD) raw / 1000.0 else raw.toDouble()

  fun estimateCapacityMah(chargeCounterMicroAmpHours: Long, levelPercent: Int): Double? {
    if (chargeCounterMicroAmpHours <= 0 || levelPercent !in 1..100) return null
    val capacity = chargeCounterMicroAmpHours / 1000.0 / (levelPercent / 100.0)
    return capacity.takeIf { it in PLAUSIBLE_CAPACITY_MAH }
  }

  fun drainPercentPerHour(drainMilliAmps: Double, capacityMah: Double): Double =
    drainMilliAmps / capacityMah * 100.0
}
