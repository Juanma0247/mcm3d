package cc.juanma.mcm3d.diagnostics

import android.app.Application
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.juanma.mcm3d.core.geo.normalizeDegrees
import cc.juanma.mcm3d.core.geo.signedAngleDifferenceDegrees
import cc.juanma.mcm3d.sensing.EnvironmentMonitor
import cc.juanma.mcm3d.sensing.EnvironmentSnapshot
import cc.juanma.mcm3d.sensing.GnssMonitor
import cc.juanma.mcm3d.sensing.GnssSnapshot
import cc.juanma.mcm3d.sensing.OrientationMonitor
import cc.juanma.mcm3d.sensing.OrientationSnapshot
import cc.juanma.mcm3d.sensing.PowerMonitor
import cc.juanma.mcm3d.sensing.PowerSnapshot
import cc.juanma.mcm3d.sensing.SensorInfo
import cc.juanma.mcm3d.sensing.SensorInventory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

data class HeadingReadout(
  val usesCameraAxis: Boolean,
  val magneticHeadingDeg: Double?,
  val trueHeadingDeg: Double?,
  val declinationDeg: Double?,
  val compassMinusGyroDeg: Double?,
  val compassVsCourseDeg: Double?,
  val expectedFieldMicroTesla: Double?,
  val fieldDeviationPercent: Double?,
) {
  companion object {
    val EMPTY = HeadingReadout(
      usesCameraAxis = true,
      magneticHeadingDeg = null,
      trueHeadingDeg = null,
      declinationDeg = null,
      compassMinusGyroDeg = null,
      compassVsCourseDeg = null,
      expectedFieldMicroTesla = null,
      fieldDeviationPercent = null,
    )
  }
}

data class DiagnosticsState(
  val locationPermission: Boolean,
  val gnss: GnssSnapshot,
  val orientation: OrientationSnapshot,
  val heading: HeadingReadout,
  val environment: EnvironmentSnapshot,
  val power: PowerSnapshot,
  val sensors: List<SensorInfo>,
  val measuredRatesHz: Map<Int, Double>,
)

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
  private val gnss = GnssMonitor(application)
  private val orientation = OrientationMonitor(application)
  private val environment = EnvironmentMonitor(application)
  private val power = PowerMonitor(application)
  private val sensors = SensorInventory.read(application)

  private val _state = MutableStateFlow(
    DiagnosticsState(
      locationPermission = gnss.hasPermission(),
      gnss = GnssSnapshot.EMPTY,
      orientation = OrientationSnapshot.EMPTY,
      heading = HeadingReadout.EMPTY,
      environment = EnvironmentSnapshot.EMPTY,
      power = PowerSnapshot.EMPTY,
      sensors = sensors,
      measuredRatesHz = emptyMap(),
    ),
  )
  val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

  private var ticker: Job? = null
  private var geomagneticField: GeomagneticField? = null
  private var geomagneticKey = Long.MIN_VALUE

  fun start() {
    if (ticker != null) return
    orientation.start()
    environment.start()
    gnss.start()
    ticker = viewModelScope.launch(Dispatchers.Default) {
      var nextPowerSampleNanos = 0L
      while (isActive) {
        val now = SystemClock.elapsedRealtimeNanos()
        if (now >= nextPowerSampleNanos) {
          power.sample()
          nextPowerSampleNanos = now + POWER_SAMPLE_PERIOD_NANOS
        }
        _state.value = buildState(now)
        delay(UI_REFRESH_MS)
      }
    }
  }

  fun stop() {
    ticker?.cancel()
    ticker = null
    gnss.stop()
    orientation.stop()
    environment.stop()
  }

  fun onLocationPermissionChanged() {
    if (ticker != null) gnss.start()
  }

  fun resetRelativeAltitude() = environment.resetReference()

  override fun onCleared() = stop()

  private fun buildState(nowNanos: Long): DiagnosticsState {
    val gnssSnapshot = gnss.snapshot(nowNanos)
    val orientationSnapshot = orientation.snapshot()
    val environmentSnapshot = environment.snapshot()
    return DiagnosticsState(
      locationPermission = gnss.hasPermission(),
      gnss = gnssSnapshot,
      orientation = orientationSnapshot,
      heading = headingReadout(gnssSnapshot, orientationSnapshot),
      environment = environmentSnapshot,
      power = power.snapshot(),
      sensors = sensors,
      measuredRatesHz = mapOf(
        Sensor.TYPE_ROTATION_VECTOR to orientationSnapshot.rotationRateHz,
        Sensor.TYPE_GAME_ROTATION_VECTOR to orientationSnapshot.gameRotationRateHz,
        Sensor.TYPE_MAGNETIC_FIELD to orientationSnapshot.magneticRateHz,
        Sensor.TYPE_PRESSURE to environmentSnapshot.pressureRateHz,
      ),
    )
  }

  private fun headingReadout(gnss: GnssSnapshot, orientation: OrientationSnapshot): HeadingReadout {
    val attitude = orientation.attitude
    val field = geomagneticFieldFor(gnss)
    val declination = field?.declination?.toDouble()
    val magneticHeading = attitude?.referenceHeadingDeg
    val trueHeading = if (magneticHeading != null && declination != null) {
      normalizeDegrees(magneticHeading + declination)
    } else {
      null
    }
    val gyroAttitude = orientation.gyroAttitude
    val compassMinusGyro = if (attitude != null && gyroAttitude != null) {
      signedAngleDifferenceDegrees(attitude.referenceHeadingDeg, gyroAttitude.referenceHeadingDeg)
    } else {
      null
    }
    val course = gnss.courseDeg?.takeIf { gnss.hasRecentFix && (gnss.speedMps ?: 0.0) >= MIN_COURSE_SPEED_MPS }
    val compassVsCourse = if (trueHeading != null && course != null && attitude?.usesCameraAxis == true) {
      signedAngleDifferenceDegrees(trueHeading, course)
    } else {
      null
    }
    val expectedField = field?.fieldStrength?.div(1000.0)
    val measuredField = orientation.magneticFieldMicroTesla
    return HeadingReadout(
      usesCameraAxis = attitude?.usesCameraAxis ?: true,
      magneticHeadingDeg = magneticHeading,
      trueHeadingDeg = trueHeading,
      declinationDeg = declination,
      compassMinusGyroDeg = compassMinusGyro,
      compassVsCourseDeg = compassVsCourse,
      expectedFieldMicroTesla = expectedField,
      fieldDeviationPercent = if (expectedField != null && measuredField != null) {
        (measuredField - expectedField) / expectedField * 100.0
      } else {
        null
      },
    )
  }

  private fun geomagneticFieldFor(gnss: GnssSnapshot): GeomagneticField? {
    val latitude = gnss.latitude ?: return null
    val longitude = gnss.longitude ?: return null
    val key = (latitude * 100).roundToLong() * 100_000L + (longitude * 100).roundToLong()
    if (key != geomagneticKey) {
      geomagneticField = GeomagneticField(
        latitude.toFloat(),
        longitude.toFloat(),
        (gnss.mslAltitudeM ?: gnss.ellipsoidAltitudeM ?: 0.0).toFloat(),
        System.currentTimeMillis(),
      )
      geomagneticKey = key
    }
    return geomagneticField
  }

  private companion object {
    const val UI_REFRESH_MS = 250L
    const val POWER_SAMPLE_PERIOD_NANOS = 1_000_000_000L
    const val MIN_COURSE_SPEED_MPS = 0.8
  }
}
