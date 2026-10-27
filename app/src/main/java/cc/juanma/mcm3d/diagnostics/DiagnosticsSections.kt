package cc.juanma.mcm3d.diagnostics

import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.GnssStatus
import android.os.BatteryManager
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.juanma.mcm3d.R
import cc.juanma.mcm3d.sensing.EnvironmentSnapshot
import cc.juanma.mcm3d.sensing.GnssSnapshot
import cc.juanma.mcm3d.sensing.OrientationSnapshot
import cc.juanma.mcm3d.sensing.PowerSnapshot
import cc.juanma.mcm3d.sensing.SensorInfo
import cc.juanma.mcm3d.ui.components.MetricRow
import cc.juanma.mcm3d.ui.components.SectionCard
import cc.juanma.mcm3d.ui.components.SectionNote
import cc.juanma.mcm3d.ui.components.StatusBadge
import cc.juanma.mcm3d.ui.components.StatusLevel
import cc.juanma.mcm3d.ui.components.thresholdLevel
import cc.juanma.mcm3d.ui.formatAccuracyOr
import cc.juanma.mcm3d.ui.formatDegreesOr
import cc.juanma.mcm3d.ui.formatNumber
import cc.juanma.mcm3d.ui.formatOr
import cc.juanma.mcm3d.ui.formatSignedDegreesOr
import cc.juanma.mcm3d.ui.formatSignedOr
import kotlin.math.abs

private const val FIELD_DEVIATION_WARNING_PERCENT = 15.0

private fun levelColor(level: StatusLevel?): Color = level?.color ?: Color.Unspecified

@Composable
internal fun PermissionCard(showSettings: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
  SectionCard(
    title = stringResource(R.string.permission_title),
    badge = { StatusBadge(stringResource(R.string.gnss_status_no_permission), StatusLevel.WARNING) },
  ) {
    Text(
      stringResource(R.string.permission_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = onRequest) { Text(stringResource(R.string.permission_button)) }
    if (showSettings) {
      TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.permission_settings_button)) }
    }
  }
}

@Composable
internal fun GnssSection(gnss: GnssSnapshot, permission: Boolean) {
  val na = stringResource(R.string.not_available)
  val (statusText, statusLevel) = when {
    !permission -> stringResource(R.string.gnss_status_no_permission) to StatusLevel.NEUTRAL
    !gnss.providerEnabled -> stringResource(R.string.gnss_status_disabled) to StatusLevel.CRITICAL
    !gnss.hasRecentFix -> stringResource(R.string.gnss_status_searching) to StatusLevel.WARNING
    else -> stringResource(R.string.gnss_status_fix) to StatusLevel.GOOD
  }
  SectionCard(
    title = stringResource(R.string.section_gnss),
    badge = { StatusBadge(statusText, statusLevel) },
  ) {
    MetricRow(stringResource(R.string.gnss_latitude), gnss.latitude.formatOr(na, 6))
    MetricRow(stringResource(R.string.gnss_longitude), gnss.longitude.formatOr(na, 6))
    MetricRow(
      stringResource(R.string.gnss_horizontal_accuracy),
      gnss.horizontalAccuracyM.formatAccuracyOr(na, 1, "m"),
      valueColor = levelColor(thresholdLevel(gnss.horizontalAccuracyM, 5.0, 15.0)),
    )
    MetricRow(stringResource(R.string.gnss_altitude_msl), gnss.mslAltitudeM.formatOr(na, 1, "m"))
    MetricRow(stringResource(R.string.gnss_altitude_ellipsoid), gnss.ellipsoidAltitudeM.formatOr(na, 1, "m"))
    MetricRow(
      stringResource(R.string.gnss_vertical_accuracy),
      gnss.verticalAccuracyM.formatAccuracyOr(na, 1, "m"),
      valueColor = levelColor(thresholdLevel(gnss.verticalAccuracyM, 8.0, 20.0)),
    )
    MetricRow(stringResource(R.string.gnss_speed), gnss.speedMps?.times(3.6).formatOr(na, 1, "km/h"))
    MetricRow(stringResource(R.string.gnss_course), courseText(gnss, na))
    MetricRow(
      stringResource(R.string.gnss_fix_age),
      gnss.fixAgeSeconds?.let { stringResource(R.string.gnss_fix_age_value, formatNumber(it, 1)) } ?: na,
    )
    MetricRow(stringResource(R.string.gnss_satellites), "${gnss.satellitesUsed} / ${gnss.satellitesVisible}")
    MetricRow(
      stringResource(R.string.gnss_second_frequency),
      gnss.secondFrequencySignalsUsed.toString(),
      supporting = stringResource(R.string.gnss_second_frequency_hint),
      valueColor = if (gnss.secondFrequencySignalsUsed > 0) StatusLevel.GOOD.color else Color.Unspecified,
    )
    MetricRow(
      stringResource(R.string.gnss_signal),
      gnss.topCn0MeanDbHz.formatOr(na, 1, "dB-Hz"),
      supporting = stringResource(R.string.gnss_signal_hint),
    )
    MetricRow(
      stringResource(R.string.gnss_constellations),
      gnss.usedByConstellation.joinToString(" · ") { (type, count) -> "${constellationName(type)} $count" }
        .ifEmpty { na },
    )
  }
}

@Composable
internal fun OrientationSection(orientation: OrientationSnapshot, heading: HeadingReadout) {
  val na = stringResource(R.string.not_available)
  val attitude = orientation.attitude
  val needsCalibration = orientation.magneticAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
    orientation.magneticAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
  val interference = heading.fieldDeviationPercent?.let { abs(it) > FIELD_DEVIATION_WARNING_PERCENT } == true
  val (statusText, statusLevel) = when {
    !orientation.rotationAvailable -> stringResource(R.string.orientation_status_missing) to StatusLevel.CRITICAL
    attitude == null -> stringResource(R.string.orientation_status_waiting) to StatusLevel.NEUTRAL
    needsCalibration -> stringResource(R.string.orientation_status_calibrate) to StatusLevel.WARNING
    interference -> stringResource(R.string.orientation_status_interference) to StatusLevel.WARNING
    else -> stringResource(R.string.orientation_status_ok) to StatusLevel.GOOD
  }
  SectionCard(
    title = stringResource(R.string.section_orientation),
    badge = { StatusBadge(statusText, statusLevel) },
  ) {
    MetricRow(
      stringResource(R.string.orientation_true_heading),
      heading.trueHeadingDeg.formatDegreesOr(na),
      supporting = stringResource(
        if (heading.usesCameraAxis) R.string.orientation_axis_camera else R.string.orientation_axis_top,
      ),
    )
    MetricRow(stringResource(R.string.orientation_magnetic_heading), heading.magneticHeadingDeg.formatDegreesOr(na))
    MetricRow(
      stringResource(R.string.orientation_heading_accuracy),
      orientation.headingAccuracyDeg.formatAccuracyOr(na, 0, "°"),
      valueColor = levelColor(thresholdLevel(orientation.headingAccuracyDeg, 10.0, 25.0)),
    )
    MetricRow(stringResource(R.string.orientation_declination), heading.declinationDeg.formatSignedDegreesOr(na))
    MetricRow(stringResource(R.string.orientation_elevation), attitude?.cameraElevationDeg.formatSignedDegreesOr(na, 0))
    MetricRow(stringResource(R.string.orientation_roll), attitude?.rollDeg.formatSignedDegreesOr(na, 0))
    MetricRow(
      stringResource(R.string.orientation_compass_minus_gyro),
      heading.compassMinusGyroDeg.formatSignedDegreesOr(na),
      supporting = stringResource(R.string.orientation_compass_minus_gyro_hint),
    )
    MetricRow(
      stringResource(R.string.orientation_compass_vs_course),
      heading.compassVsCourseDeg.formatSignedDegreesOr(na),
      supporting = stringResource(R.string.orientation_compass_vs_course_hint),
      valueColor = levelColor(thresholdLevel(heading.compassVsCourseDeg?.let { abs(it) }, 10.0, 20.0)),
    )
    MetricRow(
      stringResource(R.string.orientation_field),
      "${orientation.magneticFieldMicroTesla.formatOr(na, 1)} / ${heading.expectedFieldMicroTesla.formatOr(na, 1)} µT",
      valueColor = if (interference) StatusLevel.WARNING.color else Color.Unspecified,
    )
    MetricRow(
      stringResource(R.string.orientation_compass_accuracy),
      stringResource(compassAccuracyLabel(orientation.magneticAccuracy)),
      valueColor = levelColor(compassAccuracyLevel(orientation.magneticAccuracy)),
    )
    if (needsCalibration) {
      SectionNote(stringResource(R.string.orientation_calibrate_hint), StatusLevel.WARNING.color)
    }
  }
}

@Composable
internal fun EnvironmentSection(environment: EnvironmentSnapshot, onResetRelativeAltitude: () -> Unit) {
  val na = stringResource(R.string.not_available)
  SectionCard(title = stringResource(R.string.section_environment)) {
    if (environment.barometerAvailable) {
      MetricRow(stringResource(R.string.environment_pressure), environment.pressureHpa.formatOr(na, 2, "hPa"))
      MetricRow(
        stringResource(R.string.environment_relative_altitude),
        environment.relativeAltitudeM.formatSignedOr(na, 1, "m"),
        supporting = stringResource(R.string.environment_relative_altitude_hint),
      )
      MetricRow(
        stringResource(R.string.environment_pressure_altitude),
        environment.pressureAltitudeM.formatOr(na, 0, "m"),
      )
      TextButton(onClick = onResetRelativeAltitude) { Text(stringResource(R.string.environment_reset)) }
    } else {
      SectionNote(stringResource(R.string.environment_no_barometer), StatusLevel.WARNING.color)
    }
    MetricRow(
      stringResource(R.string.environment_light),
      if (environment.lightAvailable) environment.lux.formatOr(na, 0, "lx") else stringResource(R.string.sensor_missing),
    )
  }
}

@Composable
internal fun PowerSection(power: PowerSnapshot) {
  val na = stringResource(R.string.not_available)
  val drainLevel = thresholdLevel(power.drainPercentPerHour, 10.0, 20.0)
  val (statusText, statusLevel) = when (power.batteryStatus) {
    BatteryManager.BATTERY_STATUS_CHARGING -> stringResource(R.string.power_status_charging) to StatusLevel.NEUTRAL
    BatteryManager.BATTERY_STATUS_FULL -> stringResource(R.string.power_status_full) to StatusLevel.NEUTRAL
    BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING ->
      stringResource(R.string.power_status_discharging) to (drainLevel ?: StatusLevel.NEUTRAL)
    else -> stringResource(R.string.power_status_unknown) to StatusLevel.NEUTRAL
  }
  SectionCard(
    title = stringResource(R.string.section_power),
    badge = { StatusBadge(statusText, statusLevel) },
  ) {
    MetricRow(stringResource(R.string.power_level), power.levelPercent?.let { "$it %" } ?: na)
    MetricRow(stringResource(R.string.power_current), power.currentMilliAmps.formatSignedOr(na, 0, "mA"))
    MetricRow(stringResource(R.string.power_average_drain), power.averageDrainMilliAmps.formatOr(na, 0, "mA"))
    MetricRow(stringResource(R.string.power_power), power.powerMilliWatts.formatOr(na, 0, "mW"))
    MetricRow(
      stringResource(R.string.power_drain_rate),
      power.drainPercentPerHour.formatOr(na, 1, "%/h"),
      valueColor = levelColor(drainLevel),
    )
    MetricRow(stringResource(R.string.power_capacity), power.capacityMah.formatOr(na, 0, "mAh"))
    MetricRow(stringResource(R.string.power_voltage), power.voltageV.formatOr(na, 2, "V"))
    MetricRow(
      stringResource(R.string.power_temperature),
      power.temperatureC.formatOr(na, 1, "°C"),
      valueColor = levelColor(thresholdLevel(power.temperatureC, 38.0, 43.0)),
    )
    MetricRow(
      stringResource(R.string.power_thermal),
      stringResource(thermalLabel(power.thermalStatus)),
      valueColor = levelColor(thermalLevel(power.thermalStatus)),
    )
    if (power.isCharging) SectionNote(stringResource(R.string.power_charging_hint))
  }
}

@Composable
internal fun SensorsSection(sensors: List<SensorInfo>, measuredRatesHz: Map<Int, Double>) {
  val missing = stringResource(R.string.sensor_missing)
  val available = stringResource(R.string.sensor_available)
  SectionCard(title = stringResource(R.string.section_sensors)) {
    sensors.forEach { info ->
      val measured = measuredRatesHz[info.type]?.takeIf { it > 0.0 }
      val max = info.maxRateHz
      val value = when {
        !info.available -> missing
        measured != null && max != null ->
          stringResource(R.string.sensor_rate_measured, formatNumber(measured, 0), formatNumber(max, 0))
        measured != null -> "${formatNumber(measured, 0)} Hz"
        max != null -> stringResource(R.string.sensor_rate_max, formatNumber(max, 0))
        else -> available
      }
      MetricRow(
        label = stringResource(sensorLabel(info.type)),
        value = value,
        supporting = info.name?.let { name -> listOfNotNull(name, info.vendor).joinToString(" · ") },
        valueColor = if (info.available) Color.Unspecified else StatusLevel.WARNING.color,
      )
    }
  }
}

private fun courseText(gnss: GnssSnapshot, na: String): String {
  val course = gnss.courseDeg ?: return na
  val accuracy = gnss.courseAccuracyDeg?.let { " ${it.formatAccuracyOr(na, 0, "°")}" }.orEmpty()
  return "${course.formatDegreesOr(na)}$accuracy"
}

private fun constellationName(type: Int): String = when (type) {
  GnssStatus.CONSTELLATION_GPS -> "GPS"
  GnssStatus.CONSTELLATION_SBAS -> "SBAS"
  GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
  GnssStatus.CONSTELLATION_QZSS -> "QZSS"
  GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
  GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
  GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
  else -> "?"
}

@StringRes
private fun compassAccuracyLabel(accuracy: Int): Int = when (accuracy) {
  SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> R.string.accuracy_high
  SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> R.string.accuracy_medium
  SensorManager.SENSOR_STATUS_ACCURACY_LOW -> R.string.accuracy_low
  SensorManager.SENSOR_STATUS_UNRELIABLE -> R.string.accuracy_unreliable
  else -> R.string.accuracy_unknown
}

private fun compassAccuracyLevel(accuracy: Int): StatusLevel? = when (accuracy) {
  SensorManager.SENSOR_STATUS_ACCURACY_HIGH, SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> StatusLevel.GOOD
  SensorManager.SENSOR_STATUS_ACCURACY_LOW -> StatusLevel.WARNING
  SensorManager.SENSOR_STATUS_UNRELIABLE -> StatusLevel.CRITICAL
  else -> null
}

@StringRes
private fun thermalLabel(status: Int): Int = when (status) {
  PowerManager.THERMAL_STATUS_NONE -> R.string.thermal_none
  PowerManager.THERMAL_STATUS_LIGHT -> R.string.thermal_light
  PowerManager.THERMAL_STATUS_MODERATE -> R.string.thermal_moderate
  PowerManager.THERMAL_STATUS_SEVERE -> R.string.thermal_severe
  PowerManager.THERMAL_STATUS_CRITICAL -> R.string.thermal_critical
  PowerManager.THERMAL_STATUS_EMERGENCY -> R.string.thermal_emergency
  PowerManager.THERMAL_STATUS_SHUTDOWN -> R.string.thermal_shutdown
  else -> R.string.not_available
}

private fun thermalLevel(status: Int): StatusLevel? = when (status) {
  PowerManager.THERMAL_STATUS_NONE -> StatusLevel.GOOD
  PowerManager.THERMAL_STATUS_LIGHT, PowerManager.THERMAL_STATUS_MODERATE -> StatusLevel.WARNING
  PowerManager.THERMAL_STATUS_SEVERE,
  PowerManager.THERMAL_STATUS_CRITICAL,
  PowerManager.THERMAL_STATUS_EMERGENCY,
  PowerManager.THERMAL_STATUS_SHUTDOWN,
  -> StatusLevel.CRITICAL
  else -> null
}

@StringRes
private fun sensorLabel(type: Int): Int = when (type) {
  Sensor.TYPE_ACCELEROMETER -> R.string.sensor_accelerometer
  Sensor.TYPE_GYROSCOPE -> R.string.sensor_gyroscope
  Sensor.TYPE_MAGNETIC_FIELD -> R.string.sensor_magnetometer
  Sensor.TYPE_PRESSURE -> R.string.sensor_barometer
  Sensor.TYPE_ROTATION_VECTOR -> R.string.sensor_rotation_vector
  Sensor.TYPE_GAME_ROTATION_VECTOR -> R.string.sensor_game_rotation_vector
  Sensor.TYPE_LIGHT -> R.string.sensor_light
  Sensor.TYPE_STEP_DETECTOR -> R.string.sensor_step_detector
  else -> R.string.not_available
}
