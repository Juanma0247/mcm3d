package cc.juanma.mcm3d.sensing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import cc.juanma.mcm3d.core.baro.pressureAltitudeMeters
import cc.juanma.mcm3d.core.baro.relativeAltitudeMeters
import cc.juanma.mcm3d.core.stats.ExponentialSmoother
import cc.juanma.mcm3d.core.stats.RateMeter

data class EnvironmentSnapshot(
  val barometerAvailable: Boolean,
  val pressureHpa: Double?,
  val relativeAltitudeM: Double?,
  val pressureAltitudeM: Double?,
  val lightAvailable: Boolean,
  val lux: Double?,
  val pressureRateHz: Double,
) {
  companion object {
    val EMPTY = EnvironmentSnapshot(
      barometerAvailable = false,
      pressureHpa = null,
      relativeAltitudeM = null,
      pressureAltitudeM = null,
      lightAvailable = false,
      lux = null,
      pressureRateHz = 0.0,
    )
  }
}

class EnvironmentMonitor(context: Context) {
  private val sensorManager = context.getSystemService(SensorManager::class.java)
  private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
  private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

  private val lock = Any()
  private var running = false
  private val pressure = ExponentialSmoother(PRESSURE_SMOOTHING)
  private var referenceHpa = Double.NaN
  private var lux = Double.NaN
  private val pressureRate = RateMeter()

  private val listener = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) {
      when (event.sensor.type) {
        Sensor.TYPE_PRESSURE -> synchronized(lock) {
          val smoothed = pressure.add(event.values[0].toDouble())
          if (referenceHpa.isNaN()) referenceHpa = smoothed
          pressureRate.onEvent(event.timestamp)
        }
        Sensor.TYPE_LIGHT -> synchronized(lock) { lux = event.values[0].toDouble() }
      }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
  }

  fun start() {
    if (running) return
    pressureSensor?.let { sensorManager.registerListener(listener, it, PRESSURE_PERIOD_US, SensingThread.handler) }
    lightSensor?.let {
      sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL, SensingThread.handler)
    }
    running = true
  }

  fun stop() {
    if (!running) return
    sensorManager.unregisterListener(listener)
    synchronized(lock) { pressureRate.reset() }
    running = false
  }

  fun resetReference() {
    synchronized(lock) { referenceHpa = if (pressure.isInitialized) pressure.value else Double.NaN }
  }

  fun snapshot(): EnvironmentSnapshot {
    val currentHpa: Double
    val reference: Double
    val currentLux: Double
    val rate: Double
    synchronized(lock) {
      currentHpa = if (pressure.isInitialized) pressure.value else Double.NaN
      reference = referenceHpa
      currentLux = lux
      rate = pressureRate.rateHz
    }
    val hasPressure = !currentHpa.isNaN()
    return EnvironmentSnapshot(
      barometerAvailable = pressureSensor != null,
      pressureHpa = currentHpa.takeIf { hasPressure },
      relativeAltitudeM = if (hasPressure && !reference.isNaN()) relativeAltitudeMeters(currentHpa, reference) else null,
      pressureAltitudeM = if (hasPressure) pressureAltitudeMeters(currentHpa) else null,
      lightAvailable = lightSensor != null,
      lux = currentLux.takeUnless { it.isNaN() },
      pressureRateHz = rate,
    )
  }

  private companion object {
    const val PRESSURE_SMOOTHING = 0.2
    const val PRESSURE_PERIOD_US = 200_000
  }
}
