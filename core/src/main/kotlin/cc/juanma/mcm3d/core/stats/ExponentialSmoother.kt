package cc.juanma.mcm3d.core.stats

class ExponentialSmoother(private val alpha: Double) {
  init {
    require(alpha > 0.0 && alpha <= 1.0) { "Alpha must be in (0, 1]" }
  }

  var value = 0.0
    private set

  var isInitialized = false
    private set

  fun add(sample: Double): Double {
    value = if (isInitialized) value + alpha * (sample - value) else sample
    isInitialized = true
    return value
  }

  fun reset() {
    value = 0.0
    isInitialized = false
  }
}
