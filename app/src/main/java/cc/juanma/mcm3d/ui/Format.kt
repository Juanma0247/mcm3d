package cc.juanma.mcm3d.ui

import java.util.Locale

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
