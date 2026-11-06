package cc.juanma.mcm3d.recording

import cc.juanma.mcm3d.core.route.Instruction
import cc.juanma.mcm3d.core.route.RouteProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OutingState(
  val active: Boolean = false,
  val starting: Boolean = false,
  val routeId: Long = NO_ROUTE,
  val followRouteId: Long? = null,
  val followRouteName: String? = null,
  val startElapsedMs: Long = 0L,
  val mode: RecordingMode = RecordingMode.MOVING,
  val hasFix: Boolean = false,
  val accuracyM: Double? = null,
  val satellitesUsed: Int = 0,
  val altitudeM: Double? = null,
  val distanceM: Double = 0.0,
  val ascentM: Double = 0.0,
  val descentM: Double = 0.0,
  val points: Int = 0,
  val marks: Int = 0,
  val progress: RouteProgress? = null,
  val offRoute: Boolean = false,
  val instruction: Instruction? = null,
  val positionLat: Double? = null,
  val positionLon: Double? = null,
) {
  companion object {
    const val NO_ROUTE = -1L
  }
}

/**
 * The recorded line so far. The arrays only grow beyond [size] (a full array is replaced, never
 * rewritten), so a published trace stays valid without copying.
 */
class Trace(val lats: DoubleArray, val lons: DoubleArray, val size: Int) {
  companion object {
    val EMPTY = Trace(DoubleArray(0), DoubleArray(0), 0)
  }
}

object OutingStatus {
  private val _state = MutableStateFlow(OutingState())
  val state: StateFlow<OutingState> = _state.asStateFlow()

  private val _trace = MutableStateFlow(Trace.EMPTY)
  val trace: StateFlow<Trace> = _trace.asStateFlow()

  internal fun update(state: OutingState) {
    _state.value = state
  }

  internal fun updateTrace(trace: Trace) {
    _trace.value = trace
  }
}
