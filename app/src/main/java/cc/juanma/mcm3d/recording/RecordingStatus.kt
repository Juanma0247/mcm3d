package cc.juanma.mcm3d.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RecordingMode { MOVING, STILL }

data class RecordingState(
  val active: Boolean,
  val fileName: String?,
  val startElapsedMs: Long,
  val mode: RecordingMode,
  val fixesWritten: Int,
  val linesWritten: Int,
  val bytesWritten: Long,
  val marks: Int,
  val stillMinutes: Double,
) {
  companion object {
    val IDLE = RecordingState(
      active = false,
      fileName = null,
      startElapsedMs = 0L,
      mode = RecordingMode.MOVING,
      fixesWritten = 0,
      linesWritten = 0,
      bytesWritten = 0L,
      marks = 0,
      stillMinutes = 0.0,
    )
  }
}

object RecordingStatus {
  private val _state = MutableStateFlow(RecordingState.IDLE)
  val state: StateFlow<RecordingState> = _state.asStateFlow()

  internal fun update(state: RecordingState) {
    _state.value = state
  }
}
