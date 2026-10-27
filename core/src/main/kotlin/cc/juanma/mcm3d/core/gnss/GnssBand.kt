package cc.juanma.mcm3d.core.gnss

import kotlin.math.abs

enum class GnssBand(val isSecondFrequency: Boolean) {
  L1_E1(false),
  B1I(false),
  G1(false),
  L2(true),
  G2(true),
  L5_E5A(true),
  E5B(true),
  E6(true),
  UNKNOWN(false);

  companion object {
    private const val TOLERANCE_MHZ = 1.5

    fun fromCarrierHz(carrierHz: Double): GnssBand {
      val mhz = carrierHz / 1e6
      return when {
        near(mhz, 1575.42) -> L1_E1
        near(mhz, 1561.098) -> B1I
        mhz in 1597.0..1607.0 -> G1
        near(mhz, 1227.60) -> L2
        mhz in 1237.0..1255.0 -> G2
        near(mhz, 1176.45) -> L5_E5A
        near(mhz, 1207.14) -> E5B
        near(mhz, 1278.75) -> E6
        else -> UNKNOWN
      }
    }

    private fun near(mhz: Double, center: Double) = abs(mhz - center) <= TOLERANCE_MHZ
  }
}
