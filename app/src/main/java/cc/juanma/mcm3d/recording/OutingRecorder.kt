package cc.juanma.mcm3d.recording

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.location.Location
import android.os.Build
import android.os.SystemClock
import cc.juanma.mcm3d.core.baro.AltitudeFusion
import cc.juanma.mcm3d.core.geo.distanceMeters
import cc.juanma.mcm3d.core.recording.FixFilter
import cc.juanma.mcm3d.core.recording.StillnessDetector
import cc.juanma.mcm3d.core.route.GainAccumulator
import cc.juanma.mcm3d.core.route.Instruction
import cc.juanma.mcm3d.core.route.RouteProgress
import cc.juanma.mcm3d.core.route.RouteGuide
import cc.juanma.mcm3d.core.route.TrackPoint
import cc.juanma.mcm3d.core.route.Waypoint
import cc.juanma.mcm3d.data.TrackAppender
import cc.juanma.mcm3d.sensing.GnssMonitor
import cc.juanma.mcm3d.sensing.SensingThread

class FollowRoute(val routeId: Long, val name: String, val guide: RouteGuide)

/**
 * Records an outing as a compact track: GNSS positions that add information, with altitude from
 * the barometer fused with GNSS. Only GNSS, the barometer and the step counter run, so the phone
 * can stay in the pocket for hours. Every callback runs on [SensingThread]; there are no timers.
 */
class OutingRecorder(
  context: Context,
  val routeId: Long,
  private val appender: TrackAppender,
  private val follow: FollowRoute?,
  private val onMark: (number: Int, waypoint: Waypoint) -> Unit,
  private val onChanged: (important: Boolean) -> Unit,
) {
  private val sensorManager = context.getSystemService(SensorManager::class.java)
  private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
  private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
  private val motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

  private val gnss = GnssMonitor(context, ::onFix)
  private val fixFilter = FixFilter()
  private val stillness = StillnessDetector()
  private val fusion = AltitudeFusion()
  private val gain = GainAccumulator()

  private var running = false
  private var startNanos = 0L
  private var mode = RecordingMode.MOVING
  private var stillSinceNanos = 0L
  private var stillEntrySteps = 0L
  private var stepBase = -1L
  private var steps = 0L
  private var useMslAltitude: Boolean? = null

  private var lastFix: Location? = null
  private var hasLastPoint = false
  private var lastPointLat = 0.0
  private var lastPointLon = 0.0
  private var distance = 0.0
  private var points = 0
  private var marks = 0
  private var pendingMarks = 0
  private var progress: RouteProgress? = null
  private var offRoute = false
  private var instruction: Instruction? = null
  private var positionLat = Double.NaN
  private var positionLon = Double.NaN

  private var lastFlushNanos = 0L
  private var lastStillCheckNanos = 0L

  private var traceLats = DoubleArray(INITIAL_TRACE_CAPACITY)
  private var traceLons = DoubleArray(INITIAL_TRACE_CAPACITY)
  private var traceSize = 0

  private val pressureListener = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) {
      if (!running || mode != RecordingMode.MOVING) return
      fusion.onPressure(event.values[0].toDouble())
      housekeeping(SystemClock.elapsedRealtimeNanos())
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
  }

  private val stepListener = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) {
      val total = event.values[0].toLong()
      if (stepBase < 0) stepBase = total
      steps = total - stepBase
      if (!running) return
      if (mode == RecordingMode.STILL && steps - stillEntrySteps >= RESUME_STEPS) {
        resumeMoving(probation = false)
      } else {
        housekeeping(SystemClock.elapsedRealtimeNanos())
      }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
  }

  private val motionTrigger = object : TriggerEventListener() {
    override fun onTrigger(event: TriggerEvent) {
      // No steps yet: it may only be the phone being handled, so the stillness check runs in probation.
      SensingThread.handler.post { if (running) resumeMoving(probation = true) }
    }
  }

  fun start() {
    running = true
    startNanos = SystemClock.elapsedRealtimeNanos()
    lastFlushNanos = startNanos
    lastStillCheckNanos = startNanos
    stepSensor?.let {
      sensorManager.registerListener(stepListener, it, STEP_PERIOD_US, STEP_LATENCY_US, SensingThread.handler)
    }
    stillness.restart(0L, steps)
    enterMoving()
    publish()
  }

  fun stop() {
    if (!running) return
    if (pendingMarks > 0) {
      val fix = lastFix
      when {
        fix != null -> emitPendingMarks(fix.latitude, fix.longitude)
        hasLastPoint -> emitPendingMarks(lastPointLat, lastPointLon)
      }
    }
    running = false
    pauseSensing()
    sensorManager.unregisterListener(stepListener)
    motionSensor?.let { sensorManager.cancelTriggerSensor(motionTrigger, it) }
    appender.close()
  }

  fun mark() {
    if (!running) return
    marks++
    pendingMarks++
    if (mode == RecordingMode.STILL) {
      resumeMoving(probation = true)
    } else {
      lastFix?.let { if (isFresh(it) && isGoodForMark(it)) emitPendingMarks(it) }
    }
    publish()
    onChanged(true)
  }

  private fun enterMoving() {
    mode = RecordingMode.MOVING
    pressureSensor?.let {
      sensorManager.registerListener(pressureListener, it, PRESSURE_PERIOD_US, PRESSURE_LATENCY_US, SensingThread.handler)
    }
    gnss.start()
  }

  private fun resumeMoving(probation: Boolean) {
    if (mode != RecordingMode.STILL) return
    val now = SystemClock.elapsedRealtimeNanos()
    motionSensor?.let { sensorManager.cancelTriggerSensor(motionTrigger, it) }
    stillness.restart(elapsedMs(now), steps, probation)
    lastStillCheckNanos = now
    fixFilter.reset()
    fusion.relax((now - stillSinceNanos) / 1_000_000L)
    lastFix = null
    enterMoving()
    publish()
    onChanged(true)
  }

  private fun enterStill(now: Long) {
    mode = RecordingMode.STILL
    stillSinceNanos = now
    stillEntrySteps = steps
    pauseSensing()
    motionSensor?.let { sensorManager.requestTriggerSensor(motionTrigger, it) }
    appender.flush()
    lastFlushNanos = now
    publish()
    onChanged(true)
  }

  private fun pauseSensing() {
    gnss.stop()
    sensorManager.unregisterListener(pressureListener)
  }

  private fun onFix(location: Location) {
    if (!running || mode != RecordingMode.MOVING) return
    val t = elapsedMs(location.elapsedRealtimeNanos)
    val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else Double.NaN
    stillness.onFix(t, location.latitude, location.longitude, accuracy, steps)
    feedAltitude(t, location)
    lastFix = location

    var important = false
    if (accuracy.isNaN() || accuracy <= MAX_ACCURACY_M) {
      positionLat = location.latitude
      positionLon = location.longitude
      follow?.let {
        val guidance = it.guide.update(location.latitude, location.longitude, accuracy)
        progress = guidance.progress
        if (guidance.offRoute != offRoute) important = true
        offRoute = guidance.offRoute
        if (instruction?.javaClass != guidance.instruction.javaClass) important = true
        instruction = guidance.instruction
      }
      if (pendingMarks > 0 && isGoodForMark(location)) {
        emitPendingMarks(location)
        important = true
      }
      val bearing = if (location.hasBearing() && location.hasSpeed() && location.speed >= MIN_BEARING_SPEED_MPS) {
        location.bearing.toDouble()
      } else {
        Double.NaN
      }
      if (fixFilter.accept(t, location.latitude, location.longitude, accuracy, bearing)) addPoint(location)
    }
    publish()
    onChanged(important)
    housekeeping(SystemClock.elapsedRealtimeNanos())
  }

  private fun feedAltitude(t: Long, location: Location) {
    val hasMsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude()
    if (!hasMsl && !location.hasAltitude()) return
    // Mixing geoid and ellipsoid heights would make the altitude jump by the geoid undulation.
    val useMsl = useMslAltitude ?: hasMsl.also { useMslAltitude = it }
    if (useMsl && !hasMsl) return
    val altitude: Double
    val verticalAccuracy: Double
    if (useMsl && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      altitude = location.mslAltitudeMeters
      verticalAccuracy = if (location.hasMslAltitudeAccuracy()) location.mslAltitudeAccuracyMeters.toDouble() else Double.NaN
    } else {
      altitude = location.altitude
      verticalAccuracy = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters.toDouble() else Double.NaN
    }
    fusion.onGnss(t, altitude, verticalAccuracy)
  }

  private fun addPoint(location: Location) {
    val lat = location.latitude
    val lon = location.longitude
    val altitude = fusion.altitude()
    appender.add(TrackPoint(lat, lon, altitude, location.time))
    if (hasLastPoint) distance += distanceMeters(lastPointLat, lastPointLon, lat, lon)
    hasLastPoint = true
    lastPointLat = lat
    lastPointLon = lon
    gain.add(altitude)
    points++
    appendTrace(lat, lon)
  }

  private fun appendTrace(lat: Double, lon: Double) {
    if (traceSize == traceLats.size) {
      traceLats = traceLats.copyOf(traceSize * 2)
      traceLons = traceLons.copyOf(traceSize * 2)
    }
    traceLats[traceSize] = lat
    traceLons[traceSize] = lon
    traceSize++
    OutingStatus.updateTrace(Trace(traceLats, traceLons, traceSize))
  }

  private fun emitPendingMarks(location: Location) = emitPendingMarks(location.latitude, location.longitude)

  private fun emitPendingMarks(lat: Double, lon: Double) {
    val altitude = fusion.altitude()
    val first = marks - pendingMarks + 1
    for (number in first..marks) onMark(number, Waypoint("", lat, lon, altitude))
    pendingMarks = 0
  }

  private fun isFresh(location: Location): Boolean =
    SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos <= FRESH_FIX_NANOS

  private fun isGoodForMark(location: Location): Boolean =
    !location.hasAccuracy() || location.accuracy <= MARK_ACCURACY_M

  private fun housekeeping(now: Long) {
    if (now - lastFlushNanos >= FLUSH_PERIOD_NANOS) {
      lastFlushNanos = now
      appender.flush()
    }
    if (mode == RecordingMode.MOVING && now - lastStillCheckNanos >= STILL_CHECK_PERIOD_NANOS) {
      lastStillCheckNanos = now
      if (stillness.isStill(elapsedMs(now), steps)) enterStill(now)
    }
  }

  private fun publish() {
    val fix = lastFix
    val fixFresh = fix != null && mode == RecordingMode.MOVING && isFresh(fix)
    OutingStatus.update(
      OutingState(
        active = true,
        routeId = routeId,
        followRouteId = follow?.routeId,
        followRouteName = follow?.name,
        startElapsedMs = startNanos / 1_000_000L,
        mode = mode,
        hasFix = fixFresh,
        accuracyM = fix?.takeIf { fixFresh && it.hasAccuracy() }?.accuracy?.toDouble(),
        satellitesUsed = if (fixFresh) gnss.snapshot(SystemClock.elapsedRealtimeNanos()).satellitesUsed else 0,
        altitudeM = fusion.altitude().takeUnless { it.isNaN() },
        distanceM = distance,
        ascentM = gain.ascentM,
        descentM = gain.descentM,
        points = points,
        marks = marks,
        progress = progress,
        offRoute = follow != null && offRoute,
        instruction = instruction,
        positionLat = positionLat.takeUnless { it.isNaN() },
        positionLon = positionLon.takeUnless { it.isNaN() },
      ),
    )
  }

  private fun elapsedMs(nanos: Long): Long = (nanos - startNanos) / 1_000_000L

  private companion object {
    const val PRESSURE_PERIOD_US = 1_000_000
    const val PRESSURE_LATENCY_US = 2_000_000
    const val STEP_PERIOD_US = 1_000_000
    const val STEP_LATENCY_US = 60_000_000
    const val RESUME_STEPS = 15L
    const val MAX_ACCURACY_M = 30.0
    const val MARK_ACCURACY_M = 20f
    const val MIN_BEARING_SPEED_MPS = 0.8f
    const val FRESH_FIX_NANOS = 5_000_000_000L
    const val FLUSH_PERIOD_NANOS = 30_000_000_000L
    const val STILL_CHECK_PERIOD_NANOS = 5_000_000_000L
    const val INITIAL_TRACE_CAPACITY = 1_024
  }
}
