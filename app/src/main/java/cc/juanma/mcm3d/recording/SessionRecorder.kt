package cc.juanma.mcm3d.recording

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.location.GnssStatus
import android.location.Location
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import cc.juanma.mcm3d.BuildConfig
import cc.juanma.mcm3d.core.geo.signedAngleDifferenceDegrees
import cc.juanma.mcm3d.core.orientation.Attitude
import cc.juanma.mcm3d.core.recording.ChangeFilter
import cc.juanma.mcm3d.core.recording.FixFilter
import cc.juanma.mcm3d.core.recording.StillnessDetector
import cc.juanma.mcm3d.sensing.GnssMonitor
import cc.juanma.mcm3d.sensing.PowerMonitor
import cc.juanma.mcm3d.sensing.SensingThread
import java.io.BufferedWriter
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Writes one CSV file per session. Every callback runs on [SensingThread]. There are no timers:
 * periodic rows (power, satellites, flushes) piggyback on the events that wake the CPU anyway, and
 * while the user stays still GNSS and motion sensors are off until significant motion or steps.
 */
class SessionRecorder(private val context: Context, private val onModeChanged: (RecordingMode) -> Unit) {
  private val sensorManager = context.getSystemService(SensorManager::class.java)
  private val powerManager = context.getSystemService(PowerManager::class.java)
  private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
  private val gameRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
  private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
  private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
  private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
  private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
  private val motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

  private val gnss = GnssMonitor(context, ::onFix, ::onSatellites)
  private val power = PowerMonitor(context)
  private val fixFilter = FixFilter()
  private val pressureFilter = ChangeFilter(minChange = PRESSURE_MIN_CHANGE_HPA, heartbeatMs = HEARTBEAT_MS)
  private val stillness = StillnessDetector()

  private var writer: BufferedWriter? = null
  private var file: File? = null
  private val line = StringBuilder(256)
  private var startNanos = 0L
  private var mode = RecordingMode.MOVING
  private var stillSinceNanos = 0L
  private var stillTotalNanos = 0L
  private var stillEntrySteps = 0L

  private var stepBase = -1L
  private var steps = 0L
  private val rotation = FloatArray(5)
  private val rotationQuaternion = FloatArray(4)
  private val magnetic = FloatArray(3)
  private var hasMagnetic = false

  // Recent Game Rotation Vector samples: batched delivery can hand them over before or after the
  // Rotation Vector of the same instant, so each ORI row waits for the closest one.
  private val gameTimes = LongArray(GAME_HISTORY)
  private val gameValues = FloatArray(GAME_HISTORY * 4)
  private val gameQuaternion = FloatArray(4)
  private var gameCount = 0
  private var pendingOrientationNanos = Long.MIN_VALUE
  private val pendingRotation = FloatArray(5)
  private var magneticAccuracy = -1
  private var lux = Float.NaN
  private val matrix = FloatArray(9)

  private var lastOrientationNanos = Long.MIN_VALUE
  private var lastHeading = Double.NaN
  private var lastElevation = Double.NaN
  private var lastSatellitesNanos = Long.MIN_VALUE
  private var lastPowerNanos = 0L
  private var lastFlushNanos = 0L
  private var lastStillCheckNanos = 0L

  private var fixes = 0
  private var lines = 0
  private var bytes = 0L
  private var marks = 0

  private val listener = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) = onSensor(event)
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
      if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) magneticAccuracy = accuracy
    }
  }

  private val stepListener = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) = onSteps(event)
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
  }

  private val motionTrigger = object : TriggerEventListener() {
    override fun onTrigger(event: TriggerEvent) {
      SensingThread.handler.post { if (writer != null) resumeMoving("significant_motion", probation = true) }
    }
  }

  val outputFile: File? get() = file

  fun start() {
    val dir = File(context.getExternalFilesDir(null), SESSIONS_DIR).apply { mkdirs() }
    val now = OffsetDateTime.now()
    val target = File(dir, "session_${now.format(FILE_STAMP)}.csv")
    file = target
    writer = target.bufferedWriter(Charsets.UTF_8, BUFFER_SIZE)
    startNanos = SystemClock.elapsedRealtimeNanos()
    lastFlushNanos = startNanos
    lastStillCheckNanos = startNanos
    writeHeader(now)
    stepSensor?.let {
      sensorManager.registerListener(stepListener, it, STEP_PERIOD_US, STEP_LATENCY_US, SensingThread.handler)
    }
    stillness.restart(0L, steps)
    enterMoving("start")
    samplePower(startNanos)
    flush(startNanos)
    publish()
  }

  fun stop() {
    val out = writer ?: return
    val now = SystemClock.elapsedRealtimeNanos()
    if (mode == RecordingMode.STILL) stillTotalNanos += now - stillSinceNanos
    pauseMotionSensing()
    sensorManager.unregisterListener(stepListener)
    motionSensor?.let { sensorManager.cancelTriggerSensor(motionTrigger, it) }
    samplePower(now)
    begin("END", now).append(fixes).append(',').append(marks)
    end()
    out.flush()
    out.close()
    writer = null
    RecordingStatus.update(RecordingState.IDLE)
  }

  fun mark() {
    if (writer == null) return
    marks++
    val now = SystemClock.elapsedRealtimeNanos()
    begin("MARK", now).append(marks).append(',').append(steps)
    end()
    flush(now)
    // A mark needs a fresh position, so it wakes GNSS if the recording was paused.
    if (mode == RecordingMode.STILL) resumeMoving("mark", probation = true)
    publish()
  }

  private fun enterMoving(reason: String) {
    mode = RecordingMode.MOVING
    listOfNotNull(rotationSensor, gameRotationSensor, magneticSensor).forEach {
      sensorManager.registerListener(listener, it, MOTION_PERIOD_US, MOTION_LATENCY_US, SensingThread.handler)
    }
    listOfNotNull(pressureSensor, lightSensor).forEach {
      sensorManager.registerListener(listener, it, SLOW_PERIOD_US, SLOW_LATENCY_US, SensingThread.handler)
    }
    gnss.start()
    writeMode(reason)
  }

  private fun resumeMoving(reason: String, probation: Boolean) {
    if (mode != RecordingMode.STILL) return
    val now = SystemClock.elapsedRealtimeNanos()
    stillTotalNanos += now - stillSinceNanos
    motionSensor?.let { sensorManager.cancelTriggerSensor(motionTrigger, it) }
    stillness.restart(elapsedMs(now), steps, probation)
    lastStillCheckNanos = now
    fixFilter.reset()
    enterMoving(reason)
    samplePower(now)
    onModeChanged(mode)
    publish()
  }

  private fun enterStill(now: Long) {
    mode = RecordingMode.STILL
    stillSinceNanos = now
    stillEntrySteps = steps
    pauseMotionSensing()
    motionSensor?.let { sensorManager.requestTriggerSensor(motionTrigger, it) }
    writeMode("no_displacement")
    if (now - lastPowerNanos >= MIN_POWER_GAP_NANOS) samplePower(now)
    flush(now)
    onModeChanged(mode)
    publish()
  }

  private fun pauseMotionSensing() {
    gnss.stop()
    sensorManager.unregisterListener(listener)
    gameCount = 0
    pendingOrientationNanos = Long.MIN_VALUE
    hasMagnetic = false
    lastHeading = Double.NaN
  }

  private fun onFix(location: Location) {
    if (writer == null || mode != RecordingMode.MOVING) return
    val now = location.elapsedRealtimeNanos
    val t = elapsedMs(now)
    val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else Double.NaN
    stillness.onFix(t, location.latitude, location.longitude, accuracy, steps)
    val bearing = if (location.hasBearing() && location.hasSpeed() && location.speed >= MIN_BEARING_SPEED_MPS) {
      location.bearing.toDouble()
    } else {
      Double.NaN
    }
    if (fixFilter.accept(t, location.latitude, location.longitude, accuracy, bearing)) {
      val summary = gnss.snapshot(SystemClock.elapsedRealtimeNanos())
      begin("FIX", now)
      num(location.latitude, 7)
      num(location.longitude, 7)
      num(accuracy, 1)
      num(if (location.hasAltitude()) location.altitude else Double.NaN, 1)
      num(summary.mslAltitudeM ?: Double.NaN, 1)
      num(if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters.toDouble() else Double.NaN, 1)
      num(if (location.hasSpeed()) location.speed.toDouble() else Double.NaN, 2)
      num(if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond.toDouble() else Double.NaN, 2)
      num(if (location.hasBearing()) location.bearing.toDouble() else Double.NaN, 1)
      num(if (location.hasBearingAccuracy()) location.bearingAccuracyDegrees.toDouble() else Double.NaN, 1)
      line.append(summary.satellitesUsed).append(',').append(summary.satellitesVisible).append(',')
      line.append(summary.secondFrequencySignalsUsed).append(',')
      num(summary.topCn0MeanDbHz ?: Double.NaN, 1)
      line.append(steps)
      end()
      fixes++
      publish()
    }
    housekeeping(SystemClock.elapsedRealtimeNanos())
  }

  private fun onSatellites(status: GnssStatus) {
    if (writer == null || mode != RecordingMode.MOVING) return
    val now = SystemClock.elapsedRealtimeNanos()
    if (lastSatellitesNanos == Long.MIN_VALUE || now - lastSatellitesNanos >= SATELLITES_PERIOD_NANOS) {
      lastSatellitesNanos = now
      for (i in 0 until status.satelliteCount) {
        begin("SAT", now)
        line.append(status.getConstellationType(i)).append(',').append(status.getSvid(i)).append(',')
        num(status.getCn0DbHz(i).toDouble(), 1)
        num(status.getElevationDegrees(i).toDouble(), 0)
        num(status.getAzimuthDegrees(i).toDouble(), 0)
        line.append(if (status.usedInFix(i)) 1 else 0).append(',')
        val carrierMhz = if (status.hasCarrierFrequencyHz(i)) status.getCarrierFrequencyHz(i) / 1e6 else Double.NaN
        num(carrierMhz, 3, last = true)
        end()
      }
    }
    housekeeping(now)
  }

  private fun onSensor(event: SensorEvent) {
    if (writer == null || mode != RecordingMode.MOVING) return
    when (event.sensor.type) {
      Sensor.TYPE_ROTATION_VECTOR -> {
        rotation.fill(Float.NaN)
        event.values.copyInto(rotation, endIndex = minOf(event.values.size, rotation.size))
        maybeQueueOrientation(event.timestamp)
        maybeWriteOrientation(event.timestamp)
      }
      Sensor.TYPE_GAME_ROTATION_VECTOR -> {
        val slot = gameCount % GAME_HISTORY
        gameTimes[slot] = event.timestamp
        event.values.copyInto(gameValues, destinationOffset = slot * 4, endIndex = minOf(event.values.size, 4))
        gameCount++
        maybeWriteOrientation(event.timestamp)
      }
      Sensor.TYPE_MAGNETIC_FIELD -> {
        event.values.copyInto(magnetic, endIndex = 3)
        magneticAccuracy = event.accuracy
        hasMagnetic = true
      }
      Sensor.TYPE_PRESSURE -> {
        val hpa = event.values[0].toDouble()
        if (pressureFilter.accept(elapsedMs(event.timestamp), hpa)) {
          begin("BARO", event.timestamp)
          num(hpa, 3, last = true)
          end()
        }
      }
      Sensor.TYPE_LIGHT -> lux = event.values[0]
    }
    housekeeping(SystemClock.elapsedRealtimeNanos())
  }

  private fun onSteps(event: SensorEvent) {
    val total = event.values[0].toLong()
    if (stepBase < 0) stepBase = total
    steps = total - stepBase
    if (writer != null && mode == RecordingMode.STILL && steps - stillEntrySteps >= RESUME_STEPS) {
      resumeMoving("steps", probation = false)
    }
  }

  private fun maybeQueueOrientation(now: Long) {
    if (pendingOrientationNanos != Long.MIN_VALUE) return
    if (lastOrientationNanos != Long.MIN_VALUE && now - lastOrientationNanos < ORIENTATION_MIN_PERIOD_NANOS) return
    rotation.copyInto(rotationQuaternion, endIndex = 4)
    SensorManager.getRotationMatrixFromVector(matrix, rotationQuaternion)
    val attitude = Attitude.fromRotationMatrix(matrix)
    val heading = attitude.referenceHeadingDeg
    val changed = lastHeading.isNaN() ||
      abs(signedAngleDifferenceDegrees(heading, lastHeading)) >= ORIENTATION_MIN_CHANGE_DEG ||
      abs(attitude.cameraElevationDeg - lastElevation) >= ORIENTATION_MIN_CHANGE_DEG ||
      now - lastOrientationNanos >= ORIENTATION_HEARTBEAT_NANOS
    if (!changed) return
    lastOrientationNanos = now
    lastHeading = heading
    lastElevation = attitude.cameraElevationDeg
    pendingOrientationNanos = now
    rotation.copyInto(pendingRotation)
  }

  private fun maybeWriteOrientation(now: Long) {
    val target = pendingOrientationNanos
    if (target == Long.MIN_VALUE) return
    val latestGame = if (gameCount > 0) gameTimes[(gameCount - 1) % GAME_HISTORY] else Long.MIN_VALUE
    val waiting = gameRotationSensor != null && latestGame < target && now - target < GAME_WAIT_NANOS
    if (waiting) return
    pendingOrientationNanos = Long.MIN_VALUE

    pendingRotation.copyInto(rotationQuaternion, endIndex = 4)
    SensorManager.getRotationMatrixFromVector(matrix, rotationQuaternion)
    val attitude = Attitude.fromRotationMatrix(matrix)
    begin("ORI", target)
    num(attitude.cameraHeadingDeg, 1)
    num(attitude.cameraElevationDeg, 1)
    num(attitude.rollDeg, 1)
    num(attitude.topHeadingDeg, 1)
    for (v in pendingRotation) num(v.toDouble(), 5)

    var nearest = -1
    var nearestGap = Long.MAX_VALUE
    for (i in 0 until minOf(gameCount, GAME_HISTORY)) {
      val gap = abs(gameTimes[i] - target)
      if (gap < nearestGap) {
        nearestGap = gap
        nearest = i
      }
    }
    val aligned = nearest >= 0 && nearestGap <= GAME_MAX_GAP_NANOS
    if (aligned) {
      gameValues.copyInto(gameQuaternion, startIndex = nearest * 4, endIndex = nearest * 4 + 4)
      SensorManager.getRotationMatrixFromVector(matrix, gameQuaternion)
      num(Attitude.fromRotationMatrix(matrix).referenceHeadingDeg, 1)
      for (v in gameQuaternion) num(v.toDouble(), 5)
    } else {
      repeat(5) { line.append(',') }
    }
    if (hasMagnetic) {
      for (v in magnetic) num(v.toDouble(), 2)
    } else {
      repeat(3) { line.append(',') }
    }
    line.append(magneticAccuracy).append(',')
    if (aligned) line.append((gameTimes[nearest] - target) / 1_000_000L)
    end()
  }

  private fun housekeeping(now: Long) {
    if (now - lastPowerNanos >= POWER_PERIOD_NANOS) samplePower(now)
    if (now - lastFlushNanos >= FLUSH_PERIOD_NANOS) flush(now)
    if (mode == RecordingMode.MOVING && now - lastStillCheckNanos >= STILL_CHECK_PERIOD_NANOS) {
      lastStillCheckNanos = now
      if (stillness.isStill(elapsedMs(now), steps)) enterStill(now)
    }
  }

  private fun samplePower(now: Long) {
    lastPowerNanos = now
    power.sample()
    val p = power.snapshot()
    begin("PWR", now)
    line.append(p.levelPercent ?: "").append(',')
    num(p.currentMilliAmps ?: Double.NaN, 0)
    num(p.voltageV ?: Double.NaN, 3)
    num(p.temperatureC ?: Double.NaN, 1)
    line.append(p.thermalStatus).append(',')
    line.append(if (powerManager.isInteractive) 1 else 0).append(',')
    num(lux.toDouble(), 0, last = true)
    end()
  }

  private fun writeMode(reason: String) {
    val now = SystemClock.elapsedRealtimeNanos()
    begin("MODE", now)
    line.append(mode.name.lowercase(Locale.ROOT)).append(',').append(reason).append(',').append(steps)
    end()
  }

  private fun writeHeader(now: OffsetDateTime) {
    val out = writer ?: return
    val header = buildString {
      appendLine("# MCM3D session format 2")
      appendLine("# start=${now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)} start_elapsed_ns=$startNanos")
      appendLine("# device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} app=${BuildConfig.VERSION_NAME}")
      appendLine("# t_ms: milliseconds since start (elapsedRealtime). Empty field: not available.")
      appendLine(
        "# FIX,t_ms,lat,lon,acc_m,alt_ellipsoid_m,alt_msl_m,vacc_m,speed_mps,speed_acc_mps," +
          "bearing_deg,bearing_acc_deg,sats_used,sats_visible,second_freq_used,cn0_top4_dbhz,steps",
      )
      appendLine("# SAT,t_ms,constellation,svid,cn0_dbhz,elevation_deg,azimuth_deg,used,carrier_mhz")
      appendLine(
        "# ORI,t_ms,cam_heading_mag_deg,cam_elevation_deg,roll_deg,top_heading_mag_deg," +
          "rv_x,rv_y,rv_z,rv_w,rv_heading_acc_rad,grv_heading_deg,grv_x,grv_y,grv_z,grv_w," +
          "mag_x_ut,mag_y_ut,mag_z_ut,mag_accuracy,grv_dt_ms",
      )
      appendLine("# BARO,t_ms,pressure_hpa")
      appendLine("# PWR,t_ms,level_pct,current_ma,voltage_v,battery_temp_c,thermal_status,screen_on,lux")
      appendLine("# MODE,t_ms,moving|still,reason,steps")
      appendLine("# MARK,t_ms,number,steps")
      appendLine("# END,t_ms,fixes,marks")
    }
    out.write(header)
    bytes += header.length
  }

  private fun begin(type: String, nanos: Long): StringBuilder {
    line.setLength(0)
    line.append(type).append(',').append(elapsedMs(nanos)).append(',')
    return line
  }

  private fun num(value: Double, decimals: Int, last: Boolean = false) {
    if (value.isFinite()) line.append(String.format(Locale.ROOT, "%.${decimals}f", value))
    if (!last) line.append(',')
  }

  private fun end() {
    val out = writer ?: return
    line.append('\n')
    out.append(line)
    lines++
    bytes += line.length
  }

  private fun flush(now: Long) {
    lastFlushNanos = now
    writer?.flush()
  }

  private fun publish() {
    val now = SystemClock.elapsedRealtimeNanos()
    val still = stillTotalNanos + if (mode == RecordingMode.STILL) now - stillSinceNanos else 0L
    RecordingStatus.update(
      RecordingState(
        active = writer != null,
        fileName = file?.name,
        startElapsedMs = startNanos / 1_000_000L,
        mode = mode,
        fixesWritten = fixes,
        linesWritten = lines,
        bytesWritten = bytes,
        marks = marks,
        stillMinutes = still / 60e9,
      ),
    )
  }

  private fun elapsedMs(nanos: Long): Long = (nanos - startNanos) / 1_000_000L

  private companion object {
    const val SESSIONS_DIR = "sessions"
    const val BUFFER_SIZE = 64 * 1024
    val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    const val MOTION_PERIOD_US = 100_000
    const val MOTION_LATENCY_US = 1_000_000
    const val SLOW_PERIOD_US = 1_000_000
    const val SLOW_LATENCY_US = 10_000_000
    const val STEP_PERIOD_US = 1_000_000
    const val STEP_LATENCY_US = 60_000_000

    const val PRESSURE_MIN_CHANGE_HPA = 0.05
    const val HEARTBEAT_MS = 60_000L
    const val MIN_BEARING_SPEED_MPS = 0.8f
    const val RESUME_STEPS = 15L

    const val ORIENTATION_MIN_PERIOD_NANOS = 1_000_000_000L
    const val ORIENTATION_HEARTBEAT_NANOS = 15_000_000_000L
    const val ORIENTATION_MIN_CHANGE_DEG = 3.0
    const val GAME_HISTORY = 32
    const val GAME_WAIT_NANOS = 2_500_000_000L
    const val GAME_MAX_GAP_NANOS = 60_000_000L
    const val SATELLITES_PERIOD_NANOS = 30_000_000_000L
    const val POWER_PERIOD_NANOS = 60_000_000_000L
    const val MIN_POWER_GAP_NANOS = 5_000_000_000L
    const val FLUSH_PERIOD_NANOS = 30_000_000_000L
    const val STILL_CHECK_PERIOD_NANOS = 5_000_000_000L
  }
}
