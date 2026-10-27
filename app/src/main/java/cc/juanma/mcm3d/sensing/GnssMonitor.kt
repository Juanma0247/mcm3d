package cc.juanma.mcm3d.sensing

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.location.altitude.AltitudeConverter
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import cc.juanma.mcm3d.core.gnss.GnssBand
import java.io.IOException

data class GnssSnapshot(
  val providerEnabled: Boolean,
  val latitude: Double?,
  val longitude: Double?,
  val horizontalAccuracyM: Double?,
  val mslAltitudeM: Double?,
  val ellipsoidAltitudeM: Double?,
  val verticalAccuracyM: Double?,
  val speedMps: Double?,
  val courseDeg: Double?,
  val courseAccuracyDeg: Double?,
  val fixAgeSeconds: Double?,
  val satellitesVisible: Int,
  val satellitesUsed: Int,
  val secondFrequencySignalsUsed: Int,
  val topCn0MeanDbHz: Double?,
  val usedByConstellation: List<Pair<Int, Int>>,
) {
  val hasRecentFix: Boolean get() = fixAgeSeconds != null && fixAgeSeconds <= STALE_FIX_SECONDS

  companion object {
    const val STALE_FIX_SECONDS = 5.0

    val EMPTY = GnssSnapshot(
      providerEnabled = false,
      latitude = null,
      longitude = null,
      horizontalAccuracyM = null,
      mslAltitudeM = null,
      ellipsoidAltitudeM = null,
      verticalAccuracyM = null,
      speedMps = null,
      courseDeg = null,
      courseAccuracyDeg = null,
      fixAgeSeconds = null,
      satellitesVisible = 0,
      satellitesUsed = 0,
      secondFrequencySignalsUsed = 0,
      topCn0MeanDbHz = null,
      usedByConstellation = emptyList(),
    )
  }
}

class GnssMonitor(private val context: Context) {
  private val locationManager = context.getSystemService(LocationManager::class.java)
  private val lock = Any()
  private var running = false

  private var location: Location? = null
  private var satellitesVisible = 0
  private var satellitesUsed = 0
  private var secondFrequencyUsed = 0
  private var topCn0Mean = Double.NaN
  private val usedByConstellation = IntArray(CONSTELLATION_SLOTS)

  private val scratchConstellation = IntArray(CONSTELLATION_SLOTS)
  private val scratchTopCn0 = DoubleArray(TOP_CN0_COUNT)
  private var scratchVisibleIds = IntArray(INITIAL_ID_CAPACITY)
  private var scratchUsedIds = IntArray(INITIAL_ID_CAPACITY)
  private var altitudeConverter: Any? = null

  private val locationListener = LocationListenerCompat { onLocation(it) }

  private val statusCallback = object : GnssStatus.Callback() {
    override fun onSatelliteStatusChanged(status: GnssStatus) = onStatus(status)
  }

  fun hasPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED

  @SuppressLint("MissingPermission")
  fun start() {
    if (running || !hasPermission()) return
    val request = LocationRequestCompat.Builder(UPDATE_INTERVAL_MS)
      .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
      .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS)
      .build()
    LocationManagerCompat.requestLocationUpdates(
      locationManager,
      LocationManager.GPS_PROVIDER,
      request,
      SensingThread.executor,
      locationListener,
    )
    locationManager.registerGnssStatusCallback(statusCallback, SensingThread.handler)
    running = true
  }

  @SuppressLint("MissingPermission")
  fun stop() {
    if (!running) return
    LocationManagerCompat.removeUpdates(locationManager, locationListener)
    locationManager.unregisterGnssStatusCallback(statusCallback)
    running = false
  }

  fun snapshot(nowElapsedNanos: Long): GnssSnapshot {
    val current: Location?
    val visible: Int
    val used: Int
    val secondFrequency: Int
    val cn0: Double
    val constellations: List<Pair<Int, Int>>
    synchronized(lock) {
      current = location
      visible = satellitesVisible
      used = satellitesUsed
      secondFrequency = secondFrequencyUsed
      cn0 = topCn0Mean
      constellations = usedByConstellation.withIndex()
        .filter { it.value > 0 }
        .map { it.index to it.value }
        .sortedByDescending { it.second }
    }
    return GnssSnapshot(
      providerEnabled = LocationManagerCompat.isLocationEnabled(locationManager),
      latitude = current?.latitude,
      longitude = current?.longitude,
      horizontalAccuracyM = current?.takeIf { it.hasAccuracy() }?.accuracy?.toDouble(),
      mslAltitudeM = current?.let(::mslAltitude),
      ellipsoidAltitudeM = current?.takeIf { it.hasAltitude() }?.altitude,
      verticalAccuracyM = current?.takeIf { it.hasVerticalAccuracy() }?.verticalAccuracyMeters?.toDouble(),
      speedMps = current?.takeIf { it.hasSpeed() }?.speed?.toDouble(),
      courseDeg = current?.takeIf { it.hasBearing() }?.bearing?.toDouble(),
      courseAccuracyDeg = current?.takeIf { it.hasBearingAccuracy() }?.bearingAccuracyDegrees?.toDouble(),
      fixAgeSeconds = current?.let { (nowElapsedNanos - it.elapsedRealtimeNanos) / 1e9 },
      satellitesVisible = visible,
      satellitesUsed = used,
      secondFrequencySignalsUsed = secondFrequency,
      topCn0MeanDbHz = cn0.takeUnless { it.isNaN() },
      usedByConstellation = constellations,
    )
  }

  private fun onLocation(newLocation: Location) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) addMslAltitude(newLocation)
    synchronized(lock) { location = newLocation }
  }

  @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
  private fun addMslAltitude(target: Location) {
    if (!target.hasAltitude() || target.hasMslAltitude()) return
    val converter = altitudeConverter as? AltitudeConverter ?: AltitudeConverter().also { altitudeConverter = it }
    // The geoid model is optional; without it the ellipsoidal altitude is still shown.
    try {
      converter.addMslAltitudeToLocation(context, target)
    } catch (error: IOException) {
    } catch (error: IllegalArgumentException) {
    }
  }

  private fun mslAltitude(source: Location): Double? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && source.hasMslAltitude()) {
      source.mslAltitudeMeters
    } else {
      null
    }

  private fun onStatus(status: GnssStatus) {
    val count = status.satelliteCount
    if (scratchVisibleIds.size < count) {
      scratchVisibleIds = IntArray(count)
      scratchUsedIds = IntArray(count)
    }
    var visible = 0
    var used = 0
    var secondFrequency = 0
    scratchConstellation.fill(0)
    scratchTopCn0.fill(Double.NaN)

    for (i in 0 until count) {
      val constellation = status.getConstellationType(i)
      val id = constellation * SVID_STRIDE + status.getSvid(i)
      if (!scratchVisibleIds.containsWithin(visible, id)) scratchVisibleIds[visible++] = id
      if (!status.usedInFix(i)) continue
      if (!scratchUsedIds.containsWithin(used, id)) {
        scratchUsedIds[used++] = id
        if (constellation in 0 until CONSTELLATION_SLOTS) scratchConstellation[constellation]++
      }
      if (status.hasCarrierFrequencyHz(i) &&
        GnssBand.fromCarrierHz(status.getCarrierFrequencyHz(i).toDouble()).isSecondFrequency
      ) {
        secondFrequency++
      }
      insertTopCn0(status.getCn0DbHz(i).toDouble())
    }

    val cn0Mean = meanOfTopCn0()
    synchronized(lock) {
      satellitesVisible = visible
      satellitesUsed = used
      secondFrequencyUsed = secondFrequency
      topCn0Mean = cn0Mean
      scratchConstellation.copyInto(usedByConstellation)
    }
  }

  private fun insertTopCn0(value: Double) {
    for (k in scratchTopCn0.indices) {
      val current = scratchTopCn0[k]
      if (current.isNaN() || value > current) {
        for (j in scratchTopCn0.lastIndex downTo k + 1) scratchTopCn0[j] = scratchTopCn0[j - 1]
        scratchTopCn0[k] = value
        return
      }
    }
  }

  private fun meanOfTopCn0(): Double {
    var sum = 0.0
    var n = 0
    for (value in scratchTopCn0) {
      if (value.isNaN()) continue
      sum += value
      n++
    }
    return if (n == 0) Double.NaN else sum / n
  }

  private fun IntArray.containsWithin(size: Int, value: Int): Boolean {
    for (i in 0 until size) if (this[i] == value) return true
    return false
  }

  private companion object {
    const val UPDATE_INTERVAL_MS = 1_000L
    const val CONSTELLATION_SLOTS = 8
    const val TOP_CN0_COUNT = 4
    const val INITIAL_ID_CAPACITY = 128
    const val SVID_STRIDE = 1_000
  }
}
