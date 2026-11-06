package cc.juanma.mcm3d.sensing

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import cc.juanma.mcm3d.core.geo.normalizeDegrees
import cc.juanma.mcm3d.core.geo.signedAngleDifferenceDegrees
import cc.juanma.mcm3d.core.orientation.Attitude
import kotlin.math.abs

fun hasFineLocation(context: Context): Boolean =
  ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * True heading of the phone while the screen is visible: the top edge when the phone lies flat,
 * the camera axis when it is held up. Smoothed, and updated only when it moves at least 1 degree.
 */
@Composable
fun rememberHeading(declinationAt: Location?): State<Float?> {
  val context = LocalContext.current
  val state = remember { mutableStateOf<Float?>(null) }
  val declination = declinationAt?.let {
    val field = GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), System.currentTimeMillis())
    Math.round(field.declination * 10) / 10f
  } ?: 0f
  LifecycleStartEffect(declination) {
    val manager = context.getSystemService(SensorManager::class.java)
    val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    val matrix = FloatArray(9)
    val quaternion = FloatArray(4)
    var smoothed = Double.NaN
    val listener = object : SensorEventListener {
      override fun onSensorChanged(event: SensorEvent) {
        event.values.copyInto(quaternion, endIndex = minOf(4, event.values.size))
        SensorManager.getRotationMatrixFromVector(matrix, quaternion)
        val heading = normalizeDegrees(Attitude.fromRotationMatrix(matrix).referenceHeadingDeg + declination)
        smoothed = if (smoothed.isNaN()) heading else normalizeDegrees(smoothed + signedAngleDifferenceDegrees(heading, smoothed) * SMOOTHING)
        val current = state.value
        if (current == null || abs(signedAngleDifferenceDegrees(smoothed, current.toDouble())) >= MIN_CHANGE_DEG) {
          state.value = smoothed.toFloat()
        }
      }

      override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }
    sensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
    onStopOrDispose { manager.unregisterListener(listener) }
  }
  return state
}

/**
 * Position for maps outside an outing: GPS plus the network provider for a quick first guess.
 * Runs only while the screen is visible and the permission is granted.
 */
@SuppressLint("MissingPermission")
@Composable
fun rememberUserLocation(permissionGranted: Boolean): State<Location?> {
  val context = LocalContext.current
  val state = remember { mutableStateOf<Location?>(null) }
  LifecycleStartEffect(permissionGranted) {
    val manager = context.getSystemService(LocationManager::class.java)
    if (!permissionGranted || !hasFineLocation(context)) return@LifecycleStartEffect onStopOrDispose { }
    fun offer(location: Location) {
      val current = state.value
      val fresherGps = current != null && current.provider == LocationManager.GPS_PROVIDER &&
        location.provider != LocationManager.GPS_PROVIDER &&
        location.elapsedRealtimeNanos - current.elapsedRealtimeNanos < GPS_PREFERENCE_NANOS
      if (!fresherGps) state.value = location
    }
    listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
      runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.let { if (state.value == null) offer(it) }
    }
    val listener = LocationListenerCompat { offer(it) }
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
      runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
    }
    for (provider in providers) {
      val request = LocationRequestCompat.Builder(if (provider == LocationManager.GPS_PROVIDER) 1_000L else 5_000L)
        .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
        .build()
      LocationManagerCompat.requestLocationUpdates(manager, provider, request, listener, Looper.getMainLooper())
    }
    onStopOrDispose { LocationManagerCompat.removeUpdates(manager, listener) }
  }
  return state
}

private const val SMOOTHING = 0.3
private const val MIN_CHANGE_DEG = 1.0
private const val GPS_PREFERENCE_NANOS = 10_000_000_000L
