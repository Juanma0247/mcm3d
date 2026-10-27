package cc.juanma.mcm3d.sensing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

data class SensorInfo(
  val type: Int,
  val name: String?,
  val vendor: String?,
  val maxRateHz: Double?,
) {
  val available: Boolean get() = name != null
}

object SensorInventory {
  private val TRACKED_TYPES = intArrayOf(
    Sensor.TYPE_ACCELEROMETER,
    Sensor.TYPE_GYROSCOPE,
    Sensor.TYPE_MAGNETIC_FIELD,
    Sensor.TYPE_PRESSURE,
    Sensor.TYPE_ROTATION_VECTOR,
    Sensor.TYPE_GAME_ROTATION_VECTOR,
    Sensor.TYPE_LIGHT,
    Sensor.TYPE_STEP_DETECTOR,
  )

  fun read(context: Context): List<SensorInfo> {
    val sensorManager = context.getSystemService(SensorManager::class.java)
    return TRACKED_TYPES.map { type ->
      val sensor = sensorManager.getDefaultSensor(type)
      SensorInfo(
        type = type,
        name = sensor?.name,
        vendor = sensor?.vendor,
        maxRateHz = sensor?.minDelay?.takeIf { it > 0 }?.let { 1_000_000.0 / it },
      )
    }
  }
}
