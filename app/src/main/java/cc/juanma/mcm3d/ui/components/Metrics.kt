package cc.juanma.mcm3d.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.juanma.mcm3d.ui.theme.MetricValueStyle
import cc.juanma.mcm3d.ui.theme.Palette
import cc.juanma.mcm3d.ui.theme.SectionTitleStyle

enum class StatusLevel(val color: Color) {
  GOOD(Palette.Good),
  WARNING(Palette.Warning),
  CRITICAL(Palette.Critical),
  NEUTRAL(Palette.OnSurfaceVariant),
}

@Composable
fun SectionCard(
  title: String,
  modifier: Modifier = Modifier,
  badge: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = title.uppercase(),
          style = SectionTitleStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        badge?.invoke()
      }
      Spacer(Modifier.height(6.dp))
      content()
    }
  }
}

@Composable
fun MetricRow(
  label: String,
  value: String,
  supporting: String? = null,
  valueColor: Color = Color.Unspecified,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Column(
      Modifier
        .weight(1f)
        .padding(end = 12.dp),
    ) {
      Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (supporting != null) {
        Text(
          supporting,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
      }
    }
    Text(
      text = value,
      style = MetricValueStyle,
      color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
      textAlign = TextAlign.End,
    )
  }
}

@Composable
fun StatusBadge(text: String, level: StatusLevel) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(50))
      .background(level.color.copy(alpha = 0.14f))
      .padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(level.color),
    )
    Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = level.color)
  }
}

@Composable
fun SectionNote(text: String, color: Color = Color.Unspecified) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
    modifier = Modifier.padding(vertical = 6.dp),
  )
}

fun thresholdLevel(value: Double?, goodAtMost: Double, warningAtMost: Double): StatusLevel? = when {
  value == null -> null
  value <= goodAtMost -> StatusLevel.GOOD
  value <= warningAtMost -> StatusLevel.WARNING
  else -> StatusLevel.CRITICAL
}
