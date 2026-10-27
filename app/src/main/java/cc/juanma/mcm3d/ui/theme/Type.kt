package cc.juanma.mcm3d.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val AppTypography = Typography()

internal val MetricValueStyle = TextStyle(
  fontSize = 15.sp,
  lineHeight = 20.sp,
  fontWeight = FontWeight.Medium,
  fontFeatureSettings = "tnum",
)

internal val SectionTitleStyle = TextStyle(
  fontSize = 12.sp,
  lineHeight = 16.sp,
  fontWeight = FontWeight.SemiBold,
  letterSpacing = 1.2.sp,
)
