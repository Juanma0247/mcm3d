package cc.juanma.mcm3d.diagnostics

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.juanma.mcm3d.R
import cc.juanma.mcm3d.recording.RecordingMode
import cc.juanma.mcm3d.recording.RecordingState
import cc.juanma.mcm3d.ui.components.MetricRow
import cc.juanma.mcm3d.ui.components.SectionCard
import cc.juanma.mcm3d.ui.components.SectionNote
import cc.juanma.mcm3d.ui.components.StatusBadge
import cc.juanma.mcm3d.ui.components.StatusLevel
import cc.juanma.mcm3d.ui.formatNumber
import kotlinx.coroutines.delay

@Composable
internal fun RecordingSection(
  recording: RecordingState,
  onStart: () -> Unit,
  onMark: () -> Unit,
  onStop: () -> Unit,
) {
  val (statusText, statusLevel) = when {
    !recording.active -> stringResource(R.string.recording_idle) to StatusLevel.NEUTRAL
    recording.mode == RecordingMode.STILL -> stringResource(R.string.recording_still_badge) to StatusLevel.WARNING
    else -> stringResource(R.string.recording_moving_badge) to StatusLevel.GOOD
  }
  SectionCard(
    title = stringResource(R.string.section_recording),
    badge = { StatusBadge(statusText, statusLevel) },
  ) {
    if (recording.active) {
      val elapsedMs by produceState(0L, recording.startElapsedMs) {
        while (true) {
          value = SystemClock.elapsedRealtime() - recording.startElapsedMs
          delay(1_000L)
        }
      }
      MetricRow(stringResource(R.string.recording_duration), formatDuration(elapsedMs))
      MetricRow(
        stringResource(R.string.recording_mode),
        stringResource(
          if (recording.mode == RecordingMode.STILL) R.string.recording_mode_still else R.string.recording_mode_moving,
        ),
      )
      MetricRow(
        stringResource(R.string.recording_still_time),
        formatDuration((recording.stillMinutes * 60_000).toLong()),
      )
      MetricRow(stringResource(R.string.recording_fixes), recording.fixesWritten.toString())
      MetricRow(
        stringResource(R.string.recording_lines),
        "${recording.linesWritten} / ${formatNumber(recording.bytesWritten / 1024.0, 0)} KB",
      )
      MetricRow(stringResource(R.string.recording_marks), recording.marks.toString())
      recording.fileName?.let { SectionNote(it) }
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onMark) { Text(stringResource(R.string.recording_mark)) }
        Button(
          onClick = onStop,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text(stringResource(R.string.recording_stop))
        }
      }
    } else {
      SectionNote(stringResource(R.string.recording_hint))
      Spacer(Modifier.height(8.dp))
      Button(onClick = onStart) { Text(stringResource(R.string.recording_start)) }
    }
  }
}

private fun formatDuration(ms: Long): String {
  val totalSeconds = (ms / 1000).coerceAtLeast(0)
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return "%d:%02d:%02d".format(hours, minutes, seconds)
}
