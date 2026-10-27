package cc.juanma.mcm3d.core.stats

class RateMeter(private val windowNanos: Long = 2_000_000_000L) {
  private var windowStartNanos = -1L
  private var count = 0

  var rateHz = 0.0
    private set

  fun onEvent(timestampNanos: Long) {
    if (windowStartNanos < 0) {
      windowStartNanos = timestampNanos
      count = 1
      return
    }
    count++
    val elapsed = timestampNanos - windowStartNanos
    if (elapsed >= windowNanos) {
      rateHz = (count - 1) * 1e9 / elapsed
      windowStartNanos = timestampNanos
      count = 1
    }
  }

  fun reset() {
    windowStartNanos = -1L
    count = 0
    rateHz = 0.0
  }
}
