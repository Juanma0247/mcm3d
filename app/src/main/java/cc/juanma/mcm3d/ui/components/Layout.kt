package cc.juanma.mcm3d.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.juanma.mcm3d.R
import cc.juanma.mcm3d.ui.theme.StatValueStyle

@Composable
fun AppIcon(@DrawableRes id: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
  Icon(
    painter = painterResource(id),
    contentDescription = contentDescription,
    modifier = modifier.size(24.dp),
    tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint,
  )
}

@Composable
fun BackButton(onBack: () -> Unit) {
  IconButton(onClick = onBack) { AppIcon(R.drawable.ic_back, stringResource(R.string.action_back)) }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
  Column(modifier.padding(vertical = 8.dp)) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    Text(
      value,
      style = StatValueStyle,
      color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** Two tiles per row: label above, large tabular value below. */
@Composable
fun StatGrid(items: List<Pair<String, String>>) {
  Column(Modifier.fillMaxWidth()) {
    items.chunked(2).forEach { row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        row.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
        if (row.size == 1) Column(Modifier.weight(1f)) {}
      }
    }
  }
}

/** Primary actions pinned above the navigation bar, with large targets for one-handed use outdoors. */
@Composable
fun BottomActions(content: @Composable RowScope.() -> Unit) {
  Surface(color = MaterialTheme.colorScheme.background) {
    Column {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
      )
    }
  }
}

val LargeButtonHeight = 56.dp
