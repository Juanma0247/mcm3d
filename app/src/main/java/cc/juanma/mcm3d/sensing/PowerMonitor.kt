package cc.juanma.mcm3d.sensing

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import cc.juanma.mcm3d.core.power.PowerMath
import cc.juanma.mcm3d.core.stats.WindowedMean
import kotlin.math.abs

data class PowerSnapshot(
  val batteryStatus: Int,
  val levelPercent: Int?,
  val currentMilliAmps: Double?,
  val averageDrainMilliAmps: Double?,
  val powerMilliWatts: Double?,
  val drainPercentPerHour: Double?,
  val capacityMah: Double?,
  val voltageV: Double?,
  val temperatureC: Double?,
  val thermalStatus: Int,
) {
  val isCharging: Boolean
    get() = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
      batteryStatus == BatteryManager.BATTERY_STATUS_FULL

  companion object {
    const val THERMAL_UNKNOWN = -1

    val EMPTY = PowerSnapshot(
      batteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN,
      levelPercent = null,
      currentMilliAmps = null,
      averageDrainMilliAmps = null,
      powerMilliWatts = null,
      drainPercentPerHour = null,
      capacityMah = null,
      voltageV = null,
      temperatureC = null,
      thermalStatus = THERMAL_UNKNOWN,
    )
  }
}

class PowerMonitor(context: Context) {
  private val appContext = context.applicationContext
  private val batteryManager = context.getSystemService(BatteryManager::class.java)
  private val powerManager = context.getSystemService(PowerManager::class.java)
  private val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
  private val drainWindow = WindowedMean(DRAIN_WINDOW_SAMPLES)

  @Volatile
  private var latest = PowerSnapshot.EMPTY

  fun sample() {
    val battery = appContext.registerReceiver(null, batteryFilter)
    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
      ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    val voltage = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }?.div(1000.0)
    val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
      ?.takeIf { it != Int.MIN_VALUE }?.div(10.0)
    val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    val rawCurrent = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    val current = rawCurrent.takeIf { it != Long.MIN_VALUE && it != 0L }?.let(PowerMath::currentToMilliAmps)
    val capacity = level?.let {
      PowerMath.estimateCapacityMah(
        batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
        it,
      )
    }

    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    if (charging) drainWindow.clear() else current?.let { drainWindow.add(abs(it)) }
    val averageDrain = if (charging) null else drainWindow.mean

    latest = PowerSnapshot(
      batteryStatus = status,
      levelPercent = level,
      currentMilliAmps = current,
      averageDrainMilliAmps = averageDrain,
      powerMilliWatts = if (current != null && voltage != null) abs(current) * voltage else null,
      drainPercentPerHour = if (averageDrain != null && capacity != null) {
        PowerMath.drainPercentPerHour(averageDrain, capacity)
      } else {
        null
      },
      capacityMah = capacity,
      voltageV = voltage,
      temperatureC = temperature,
      thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        powerManager.currentThermalStatus
      } else {
        PowerSnapshot.THERMAL_UNKNOWN
      },
    )
  }

  fun snapshot(): PowerSnapshot = latest

  private companion object {
    const val DRAIN_WINDOW_SAMPLES = 30
  }
}
