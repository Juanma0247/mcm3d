package cc.juanma.mcm3d.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
  primary = Palette.Accent,
  onPrimary = Palette.OnAccent,
  secondary = Palette.Info,
  onSecondary = Palette.OnInfo,
  background = Palette.Background,
  onBackground = Palette.OnSurface,
  surface = Palette.Surface,
  onSurface = Palette.OnSurface,
  surfaceVariant = Palette.SurfaceContainerHigh,
  onSurfaceVariant = Palette.OnSurfaceVariant,
  surfaceContainerLowest = Palette.Background,
  surfaceContainerLow = Palette.SurfaceContainerLow,
  surfaceContainer = Palette.SurfaceContainer,
  surfaceContainerHigh = Palette.SurfaceContainerHigh,
  surfaceContainerHighest = Palette.SurfaceContainerHighest,
  outline = Palette.Outline,
  outlineVariant = Palette.OutlineVariant,
  error = Palette.Critical,
  onError = Palette.OnCritical,
)

@Composable
fun Mcm3dTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = DarkColors, typography = AppTypography, content = content)
}
