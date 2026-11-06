package cc.juanma.mcm3d.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

fun formatDistance(meters: Double): String = when {
  meters < 1_000 -> "${formatNumber(meters, 0)} m"
  meters < 10_000 -> "${formatNumber(meters / 1_000, 2)} km"
  else -> "${formatNumber(meters / 1_000, 1)} km"
}

fun formatElevation(meters: Double): String = "${formatNumber(meters, 0)} m"

fun formatAscent(meters: Double): String = "+${formatNumber(meters, 0)} m"

fun formatDescent(meters: Double): String = "−${formatNumber(abs(meters), 0)} m"

fun formatClock(ms: Long): String {
  val totalSeconds = (ms / 1000).coerceAtLeast(0)
  return "%d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
}

fun formatHoursMinutes(ms: Long): String {
  val totalMinutes = (ms / 60_000).coerceAtLeast(0)
  val hours = totalMinutes / 60
  val minutes = totalMinutes % 60
  return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

fun formatDate(epochMs: Long): String =
  DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun formatNumber(value: Double, decimals: Int): String =
  String.format(Locale.getDefault(), "%.${decimals}f", value)

fun formatSigned(value: Double, decimals: Int): String =
  String.format(Locale.getDefault(), "%+.${decimals}f", value)

fun Double?.formatOr(na: String, decimals: Int, unit: String = ""): String =
  this?.let { if (unit.isEmpty()) formatNumber(it, decimals) else "${formatNumber(it, decimals)} $unit" } ?: na

fun Double?.formatSignedOr(na: String, decimals: Int, unit: String = ""): String =
  this?.let { if (unit.isEmpty()) formatSigned(it, decimals) else "${formatSigned(it, decimals)} $unit" } ?: na

fun Double?.formatDegreesOr(na: String, decimals: Int = 0): String =
  this?.let { "${formatNumber(it, decimals)}°" } ?: na

fun Double?.formatSignedDegreesOr(na: String, decimals: Int = 1): String =
  this?.let { "${formatSigned(it, decimals)}°" } ?: na

fun Double?.formatAccuracyOr(na: String, decimals: Int, unit: String): String =
  this?.let { if (unit == "°") "±${formatNumber(it, decimals)}°" else "±${formatNumber(it, decimals)} $unit" } ?: na
