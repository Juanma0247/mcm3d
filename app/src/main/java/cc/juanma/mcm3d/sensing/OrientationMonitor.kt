package cc.juanma.mcm3d.sensing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import cc.juanma.mcm3d.core.orientation.Attitude
import cc.juanma.mcm3d.core.stats.RateMeter
import kotlin.math.sqrt

data class OrientationSnapshot(
  val rotationAvailable: Boolean,
  val gameRotationAvailable: Boolean,
  val magnetometerAvailable: Boolean,
  val attitude: Attitude?,
  val gyroAttitude: Attitude?,
  val headingAccuracyDeg: Double?,
  val magneticFieldMicroTesla: Double?,
  val magneticAccuracy: Int,
  val rotationRateHz: Double,
  val gameRotationRateHz: Double,
  val magneticRateHz: Double,
) {
  companion object {
    const val ACCURACY_UNKNOWN = -1

    val EMPTY = OrientationSnapshot(
      rotationAvailable = false,
      gameRotationAvailable = false,
      magnetometerAvailable = false,
      attitude = null,
      gyroAttitude = null,
      headingAccuracyDeg = null,
      magneticFieldMicroTesla = null,
      magneticAccuracy = ACCURACY_UNKNOWN,
      rotationRateHz = 0.0,
      gameRotationRateHz = 0.0,
      magneticRateHz = 0.0,
    )
  }
}

class OrientationMonitor(context: Context) {
  private val sensorManager = context.getSystemService(SensorManager::class.java)
  private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
  private val gameRotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
  private val magneticSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

  private val lock = Any()
  private var running = false
  private val scratch = FloatArray(9)
  private val rotationMatrix = FloatArray(9)
  private val gameMatrix = FloatArray(9)
  private var hasRotation = false
  private var hasGameRotation = false
  private var headingAccuracyRad = Float.NaN
  private var fieldMagnitude = Double.NaN
  private var magneticAccuracy = OrientationSnapshot.ACCURACY_UNKNOWN
  private val rotationRate = RateMeter()
  private val gameRotationRate = RateMeter()
  private val magneticRate = RateMeter()

  private val listener = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) {
      when (event.sensor.type) {
        Sensor.TYPE_ROTATION_VECTOR -> {
          SensorManager.getRotationMatrixFromVector(scratch, event.values)
          synchronized(lock) {
            scratch.copyInto(rotationMatrix)
            hasRotation = true
            headingAccuracyRad = if (event.values.size > 4) event.values[4] else Float.NaN
            rotationRate.onEvent(event.timestamp)
          }
        }
        Sensor.TYPE_GAME_ROTATION_VECTOR -> {
          SensorManager.getRotationMatrixFromVector(scratch, event.values)
          synchronized(lock) {
            scratch.copyInto(gameMatrix)
            hasGameRotation = true
            gameRotationRate.onEvent(event.timestamp)
          }
        }
        Sensor.TYPE_MAGNETIC_FIELD -> {
          val v = event.values
          val magnitude = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
          synchronized(lock) {
            fieldMagnitude = magnitude
            magneticAccuracy = event.accuracy
            magneticRate.onEvent(event.timestamp)
          }
        }
      }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
      if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) synchronized(lock) { magneticAccuracy = accuracy }
    }
  }

  fun start() {
    if (running) return
    listOfNotNull(rotationSensor, gameRotationSensor, magneticSensor).forEach {
      sensorManager.registerListener(listener, it, SAMPLING_PERIOD_US, SensingThread.handler)
    }
    running = true
  }

  fun stop() {
    if (!running) return
    sensorManager.unregisterListener(listener)
    synchronized(lock) {
      rotationRate.reset()
      gameRotationRate.reset()
      magneticRate.reset()
    }
    running = false
  }

  fun snapshot(): OrientationSnapshot {
    val rotation = FloatArray(9)
    val game = FloatArray(9)
    val rotationReady: Boolean
    val gameReady: Boolean
    val accuracyRad: Float
    val field: Double
    val fieldAccuracy: Int
    val rates: DoubleArray
    synchronized(lock) {
      rotationMatrix.copyInto(rotation)
      gameMatrix.copyInto(game)
      rotationReady = hasRotation
      gameReady = hasGameRotation
      accuracyRad = headingAccuracyRad
      field = fieldMagnitude
      fieldAccuracy = magneticAccuracy
      rates = doubleArrayOf(rotationRate.rateHz, gameRotationRate.rateHz, magneticRate.rateHz)
    }
    return OrientationSnapshot(
      rotationAvailable = rotationSensor != null,
      gameRotationAvailable = gameRotationSensor != null,
      magnetometerAvailable = magneticSensor != null,
      attitude = if (rotationReady) Attitude.fromRotationMatrix(rotation) else null,
      gyroAttitude = if (gameReady) Attitude.fromRotationMatrix(game) else null,
      headingAccuracyDeg = accuracyRad.takeIf { !it.isNaN() && it >= 0f }?.let { Math.toDegrees(it.toDouble()) },
      magneticFieldMicroTesla = field.takeUnless { it.isNaN() },
      magneticAccuracy = fieldAccuracy,
      rotationRateHz = rates[0],
      gameRotationRateHz = rates[1],
      magneticRateHz = rates[2],
    )
  }

  private companion object {
    const val SAMPLING_PERIOD_US = 20_000
  }
}
