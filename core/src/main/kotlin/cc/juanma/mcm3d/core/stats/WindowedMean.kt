package cc.juanma.mcm3d.core.stats

class WindowedMean(private val capacity: Int) {
  init {
    require(capacity > 0) { "Capacity must be positive" }
  }

  private val values = DoubleArray(capacity)
  private var size = 0
  private var next = 0
  private var sum = 0.0

  val count: Int get() = size

  val mean: Double? get() = if (size == 0) null else sum / size

  fun add(value: Double) {
    if (size == capacity) sum -= values[next] else size++
    values[next] = value
    sum += value
    next = (next + 1) % capacity
  }

  fun clear() {
    size = 0
    next = 0
    sum = 0.0
  }
}
