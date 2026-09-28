package com.example.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

enum class DeviceOrientation {
    UNKNOWN,
    FLAT_FACE_UP,
    FLAT_FACE_DOWN,
    UPRIGHT
}

data class SensorTelemetry(
    val lightLux: Float = 0f,
    val proximityDistance: Float = 5f,
    val isProximityNear: Boolean = false,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 9.8f,
    val orientation: DeviceOrientation = DeviceOrientation.FLAT_FACE_UP,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val hasLightSensor: Boolean = true,
    val hasProximitySensor: Boolean = true,
    val hasAccelerometer: Boolean = true
)

sealed class SensorTriggerEvent {
    data class Shake(val force: Float) : SensorTriggerEvent()
    data class HandWave(val type: String = "PROXIMITY_WAVE") : SensorTriggerEvent()
    data class LightChanged(val lux: Float) : SensorTriggerEvent()
    data class ProximityChanged(val isNear: Boolean, val distance: Float) : SensorTriggerEvent()
    data class OrientationChanged(val oldOrientation: DeviceOrientation, val newOrientation: DeviceOrientation) : SensorTriggerEvent()
    data class BatteryChanged(val level: Int, val isCharging: Boolean) : SensorTriggerEvent()
    data class ChargerStatusChanged(val isCharging: Boolean) : SensorTriggerEvent()
}

class SensorHub(
    private val context: Context,
    private val scope: CoroutineScope
) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _telemetry = MutableStateFlow(
        SensorTelemetry(
            hasLightSensor = lightSensor != null,
            hasProximitySensor = proximitySensor != null,
            hasAccelerometer = accelerometer != null
        )
    )
    val telemetry: StateFlow<SensorTelemetry> = _telemetry.asStateFlow()

    private val _triggerEvents = MutableSharedFlow<SensorTriggerEvent>(extraBufferCapacity = 64)
    val triggerEvents: SharedFlow<SensorTriggerEvent> = _triggerEvents.asSharedFlow()

    // Shake calculation state
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 9.8f
    private var lastShakeTimestamp = 0L

    // Hand wave detection state
    private var proximityNearStartTime = 0L
    private var lastWaveTimestamp = 0L
    private var lastLuxDropTime = 0L
    private var preDropLux = 0f

    // Battery receiver
    private var isBatteryReceiverRegistered = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 85
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val prevCharging = _telemetry.value.isCharging
                val prevLevel = _telemetry.value.batteryLevel

                _telemetry.value = _telemetry.value.copy(
                    batteryLevel = batteryPct,
                    isCharging = isCharging
                )

                if (prevCharging != isCharging) {
                    _triggerEvents.tryEmit(SensorTriggerEvent.ChargerStatusChanged(isCharging))
                }
                if (batteryPct != prevLevel) {
                    _triggerEvents.tryEmit(SensorTriggerEvent.BatteryChanged(batteryPct, isCharging))
                }
            }
        }
    }

    fun startListening() {
        try {
            lightSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            proximitySensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }

            if (!isBatteryReceiverRegistered) {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                context.registerReceiver(batteryReceiver, filter)
                isBatteryReceiverRegistered = true
            }
        } catch (e: Exception) {
            Log.e("SensorHub", "Error starting sensor listeners", e)
        }
    }

    fun stopListening() {
        try {
            sensorManager?.unregisterListener(this)
            if (isBatteryReceiverRegistered) {
                context.unregisterReceiver(batteryReceiver)
                isBatteryReceiverRegistered = false
            }
        } catch (e: Exception) {
            Log.e("SensorHub", "Error stopping sensor listeners", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                val prevLux = _telemetry.value.lightLux
                _telemetry.value = _telemetry.value.copy(lightLux = lux)
                val now = System.currentTimeMillis()

                if (abs(lux - prevLux) > 3f) {
                    _triggerEvents.tryEmit(SensorTriggerEvent.LightChanged(lux))
                }

                // Ambient Shadow Hand-Wave detection (hand moving left/right/up/down casting shadow)
                if (prevLux > 30f && lux < prevLux * 0.4f && (now - lastWaveTimestamp > 1200)) {
                    preDropLux = prevLux
                    lastLuxDropTime = now
                } else if (lastLuxDropTime > 0 && (now - lastLuxDropTime in 80..1200) && lux >= preDropLux * 0.7f) {
                    if (now - lastWaveTimestamp > 1500) {
                        lastWaveTimestamp = now
                        lastLuxDropTime = 0
                        Log.i("SensorHub", "Ambient shadow hand wave gesture detected")
                        _triggerEvents.tryEmit(SensorTriggerEvent.HandWave("AMBIENT_LIGHT_WAVE"))
                    }
                }
            }

            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = event.sensor.maximumRange
                val isNear = distance < maxRange && distance < 4f
                val prevNear = _telemetry.value.isProximityNear
                val now = System.currentTimeMillis()

                _telemetry.value = _telemetry.value.copy(
                    proximityDistance = distance,
                    isProximityNear = isNear
                )

                if (prevNear != isNear) {
                    _triggerEvents.tryEmit(SensorTriggerEvent.ProximityChanged(isNear, distance))

                    if (isNear) {
                        proximityNearStartTime = now
                    } else if (proximityNearStartTime > 0) {
                        val duration = now - proximityNearStartTime
                        proximityNearStartTime = 0
                        // A quick wave is between 80ms and 1200ms (not holding in pocket)
                        if (duration in 80..1200 && (now - lastWaveTimestamp > 1200)) {
                            lastWaveTimestamp = now
                            Log.i("SensorHub", "Proximity hand wave gesture detected ($duration ms)")
                            _triggerEvents.tryEmit(SensorTriggerEvent.HandWave("PROXIMITY_WAVE"))
                        }
                    }
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Orientation calculation
                val newOrientation = when {
                    z < -7.0f && abs(x) < 5.0f && abs(y) < 5.0f -> DeviceOrientation.FLAT_FACE_DOWN
                    z > 7.0f && abs(x) < 5.0f && abs(y) < 5.0f -> DeviceOrientation.FLAT_FACE_UP
                    abs(y) > 6.5f -> DeviceOrientation.UPRIGHT
                    else -> DeviceOrientation.UNKNOWN
                }

                val oldOrientation = _telemetry.value.orientation

                _telemetry.value = _telemetry.value.copy(
                    accelX = x,
                    accelY = y,
                    accelZ = z,
                    orientation = newOrientation
                )

                if (oldOrientation != newOrientation && newOrientation != DeviceOrientation.UNKNOWN) {
                    _triggerEvents.tryEmit(SensorTriggerEvent.OrientationChanged(oldOrientation, newOrientation))
                }

                // Shake detection calculation (Decreased sensitivity & Pocket Protection)
                val deltaX = x - lastAccelX
                val deltaY = y - lastAccelY
                val deltaZ = z - lastAccelZ
                lastAccelX = x
                lastAccelY = y
                lastAccelZ = z

                val accelerationDiff = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()
                val now = System.currentTimeMillis()

                // Pocket Safety: Suppress shake detection if proximity sensor is near (in pocket / face down)
                val isInPocket = _telemetry.value.isProximityNear || _telemetry.value.proximityDistance < 3.0f
                if (!isInPocket && accelerationDiff >= 24.0f && (now - lastShakeTimestamp) > 1500) {
                    lastShakeTimestamp = now
                    _triggerEvents.tryEmit(SensorTriggerEvent.Shake(accelerationDiff))
                }
            }
        }
    }

    fun isDeviceInPocket(): Boolean {
        val t = _telemetry.value
        return t.isProximityNear || (t.proximityDistance < 3.0f && t.lightLux < 2.0f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
