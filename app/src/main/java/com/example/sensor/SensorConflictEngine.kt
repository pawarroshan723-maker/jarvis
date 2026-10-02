package com.example.sensor

import android.util.Log
import com.example.data.ActionTypes
import com.example.data.AutomationRule
import com.example.data.TriggerTypes
import com.example.hardware.HardwareController
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class SensorConflictWarning(
    val rule1Name: String,
    val rule2Name: String,
    val conflictReason: String,
    val severity: ConflictSeverity = ConflictSeverity.WARNING
)

enum class ConflictSeverity {
    INFO,
    WARNING,
    CRITICAL
}

class SensorConflictEngine(
    private val sensorHub: SensorHub,
    private val hardwareController: HardwareController
) {
    companion object {
        private const val TAG = "SensorConflictEngine"
        private const val VOLUME_HYSTERESIS_TOLERANCE = 3 // % volume tolerance
        private const val LIGHT_HYSTERESIS_LUX = 25f // lux deadband
    }

    // Tracks condition state for edge-triggering (true = in triggered state, false = reset)
    private val ruleConditionActiveMap = ConcurrentHashMap<Long, Boolean>()
    // Tracks timestamp of last volume adjustment to prevent rapid oscillation between opposing rules
    @Volatile
    private var lastVolumeAdjustmentTimestamp = 0L

    /**
     * Determines whether a rule should execute given the incoming sensor event,
     * applying edge-triggering (state transition only), redundant state suppression,
     * and cross-sensor conflict resolution.
     */
    fun shouldExecuteRule(rule: AutomationRule, event: SensorTriggerEvent, now: Long): Pair<Boolean, String?> {
        val telemetry = sensorHub.telemetry.value

        // 1. Instantaneous Pulse Events (Shake / HandWave)
        when (event) {
            is SensorTriggerEvent.Shake -> {
                val minThreshold = if (rule.triggerThreshold >= 20.0f) rule.triggerThreshold else 26.0f
                if (rule.triggerType == TriggerTypes.SHAKE && event.force >= minThreshold) {
                    val cooldownMs = rule.cooldownSeconds * 1000L
                    if (now - rule.lastTriggeredTime < cooldownMs) {
                        return Pair(false, "Cooldown active (${(cooldownMs - (now - rule.lastTriggeredTime)) / 1000}s remaining)")
                    }
                    if (sensorHub.isDeviceInPocket()) {
                        return Pair(false, "Suppressed: Device in pocket or proximity covered")
                    }
                    return Pair(true, null)
                }
                return Pair(false, "Condition not met")
            }
            is SensorTriggerEvent.HandWave -> {
                if (rule.triggerType == TriggerTypes.HAND_WAVE) {
                    val cooldownMs = rule.cooldownSeconds * 1000L
                    if (now - rule.lastTriggeredTime < cooldownMs) {
                        return Pair(false, "Cooldown active")
                    }
                    Log.i(TAG, "[HandWave] Rule '${rule.name}' triggered by hand wave gesture (${event.type})")
                    return Pair(true, null)
                }
                return Pair(false, "Condition not met")
            }
            else -> {}
        }

        // 2. Continuous State Condition Evaluation
        val conditionState = when (event) {
            is SensorTriggerEvent.LightChanged -> {
                when (rule.triggerType) {
                    TriggerTypes.LIGHT_BELOW -> event.lux <= rule.triggerThreshold
                    TriggerTypes.LIGHT_ABOVE -> event.lux >= rule.triggerThreshold
                    else -> false
                }
            }
            is SensorTriggerEvent.ProximityChanged -> {
                when (rule.triggerType) {
                    TriggerTypes.PROXIMITY_NEAR -> event.isNear
                    TriggerTypes.PROXIMITY_FAR -> !event.isNear
                    else -> false
                }
            }
            is SensorTriggerEvent.OrientationChanged -> {
                when (rule.triggerType) {
                    TriggerTypes.FLIP_FACE_DOWN -> event.newOrientation == DeviceOrientation.FLAT_FACE_DOWN
                    TriggerTypes.FLIP_FACE_UP -> event.newOrientation == DeviceOrientation.FLAT_FACE_UP
                    TriggerTypes.ORIENTATION_UPRIGHT -> event.newOrientation == DeviceOrientation.UPRIGHT
                    else -> false
                }
            }
            is SensorTriggerEvent.BatteryChanged -> {
                when (rule.triggerType) {
                    TriggerTypes.BATTERY_LOW -> event.level <= (rule.triggerThreshold.toInt().takeIf { it > 0 } ?: 20) && !telemetry.isCharging
                    TriggerTypes.BATTERY_FULL -> event.level >= 99 && telemetry.isCharging
                    else -> false
                }
            }
            is SensorTriggerEvent.ChargerStatusChanged -> {
                if (rule.triggerType == TriggerTypes.CHARGER_CONNECTED && event.isCharging) true
                else rule.triggerType == TriggerTypes.CHARGER_DISCONNECTED && !event.isCharging
            }
            else -> false
        }

        val wasActive = ruleConditionActiveMap[rule.id] ?: false

        if (!conditionState) {
            // Condition fell below threshold (state reset)
            ruleConditionActiveMap[rule.id] = false
            return Pair(false, "Condition not met")
        }

        if (wasActive) {
            // Continuous condition holding: do not re-fire on same state
            return Pair(false, "Condition already active (level hold)")
        }

        // New rising edge detected! Register state transition immediately
        ruleConditionActiveMap[rule.id] = true

        // 3. Evaluate Cooldown
        val cooldownMs = rule.cooldownSeconds * 1000L
        if (now - rule.lastTriggeredTime < cooldownMs) {
            return Pair(false, "Cooldown active (${(cooldownMs - (now - rule.lastTriggeredTime)) / 1000}s remaining)")
        }

        // 4. Contextual Pocket Guard
        val inPocket = sensorHub.isDeviceInPocket()
        if (inPocket) {
            when (rule.actionType) {
                ActionTypes.TOGGLE_FLASHLIGHT,
                ActionTypes.FLASHLIGHT_ON,
                ActionTypes.FLASHLIGHT_SOS -> {
                    return Pair(false, "Suppressed: Device in pocket or proximity covered")
                }
                ActionTypes.MAX_VOLUME -> {
                    return Pair(false, "Suppressed: Device in pocket (preventing blast)")
                }
                ActionTypes.LAUNCH_APP,
                ActionTypes.LAUNCH_CAMERA -> {
                    return Pair(false, "Suppressed: Device in pocket (preventing accidental launch)")
                }
            }
        }

        // 5. Face-Down Privacy Guard
        if (telemetry.orientation == DeviceOrientation.FLAT_FACE_DOWN) {
            if (rule.triggerType == TriggerTypes.LIGHT_ABOVE && (rule.actionType == ActionTypes.MAX_VOLUME || rule.actionType == ActionTypes.SET_VOLUME)) {
                return Pair(false, "Suppressed: Device is face-down on table (meeting mode active)")
            }
            if (rule.actionType == ActionTypes.SPEAK_TTS && rule.triggerType != TriggerTypes.FLIP_FACE_DOWN) {
                return Pair(false, "Suppressed spoken audio: Phone is placed face-down")
            }
        }

        // 6. Redundant Hardware State Guard (No-Op Prevention)
        when (rule.actionType) {
            ActionTypes.MAX_VOLUME -> {
                val currentVol = hardwareController.getMediaVolumePercent()
                if (currentVol >= 98) {
                    Log.i(TAG, "[NoOpGuard] Rule '${rule.name}' skipped: Media volume already at 100%")
                    return Pair(false, "Skipped: Volume is already at 100%")
                }
            }
            ActionTypes.SET_VOLUME -> {
                val targetVol = rule.actionParam.toIntOrNull() ?: 60
                val currentVol = hardwareController.getMediaVolumePercent()
                if (abs(currentVol - targetVol) <= VOLUME_HYSTERESIS_TOLERANCE) {
                    Log.i(TAG, "[NoOpGuard] Rule '${rule.name}' skipped: Volume already at target $currentVol%")
                    return Pair(false, "Skipped: Volume already at target ($currentVol%)")
                }
            }
            ActionTypes.MUTE_ALL -> {
                val currentVol = hardwareController.getMediaVolumePercent()
                if (currentVol == 0) {
                    return Pair(false, "Skipped: Audio already muted")
                }
            }
            ActionTypes.FLASHLIGHT_ON -> {
                if (hardwareController.isFlashlightActive()) {
                    return Pair(false, "Skipped: Flashlight is already ON")
                }
            }
            ActionTypes.FLASHLIGHT_OFF -> {
                if (!hardwareController.isFlashlightActive()) {
                    return Pair(false, "Skipped: Flashlight is already OFF")
                }
            }
        }

        // 7. Opposing Volume Rapid Oscillation Guard
        if (rule.actionType == ActionTypes.MAX_VOLUME || rule.actionType == ActionTypes.SET_VOLUME || rule.actionType == ActionTypes.MUTE_ALL) {
            if (now - lastVolumeAdjustmentTimestamp < 2500L) {
                return Pair(false, "Suppressed: Opposing volume adjustment stabilizer active")
            }
        }

        Log.i(TAG, "[EdgeTrigger] Rule '${rule.name}' triggered on rising edge")
        return Pair(true, null)
    }

    /**
     * Records successful execution of a rule to update volume timestamps and stabilizers.
     */
    fun onRuleExecuted(rule: AutomationRule, now: Long) {
        if (rule.actionType == ActionTypes.MAX_VOLUME || rule.actionType == ActionTypes.SET_VOLUME || rule.actionType == ActionTypes.MUTE_ALL) {
            lastVolumeAdjustmentTimestamp = now
        }
    }

    /**
     * Analyzes all currently active rules for configuration conflicts (e.g. opposing light rules, conflicting orientations).
     */
    fun analyzeRuleConflicts(rules: List<AutomationRule>): List<SensorConflictWarning> {
        val warnings = mutableListOf<SensorConflictWarning>()
        val activeRules = rules.filter { it.isEnabled }

        for (i in activeRules.indices) {
            for (j in i + 1 until activeRules.size) {
                val r1 = activeRules[i]
                val r2 = activeRules[j]

                // Conflict 1: Opposing Light Rules on Audio
                if ((r1.triggerType == TriggerTypes.LIGHT_ABOVE && r2.triggerType == TriggerTypes.LIGHT_BELOW) ||
                    (r1.triggerType == TriggerTypes.LIGHT_BELOW && r2.triggerType == TriggerTypes.LIGHT_ABOVE)
                ) {
                    val isVolAction1 = r1.actionType == ActionTypes.MAX_VOLUME || r1.actionType == ActionTypes.SET_VOLUME || r1.actionType == ActionTypes.MUTE_ALL
                    val isVolAction2 = r2.actionType == ActionTypes.MAX_VOLUME || r2.actionType == ActionTypes.SET_VOLUME || r2.actionType == ActionTypes.MUTE_ALL
                    if (isVolAction1 && isVolAction2) {
                        warnings.add(
                            SensorConflictWarning(
                                rule1Name = r1.name,
                                rule2Name = r2.name,
                                conflictReason = "Opposing ambient light triggers control volume (Auto-stabilized by Edge-Triggering & Hysteresis)",
                                severity = ConflictSeverity.INFO
                            )
                        )
                    }
                }

                // Conflict 2: Face-Down Mute vs Proximity Volume Up
                if ((r1.triggerType == TriggerTypes.FLIP_FACE_DOWN && r1.actionType == ActionTypes.MUTE_ALL) &&
                    (r2.triggerType == TriggerTypes.PROXIMITY_NEAR && (r2.actionType == ActionTypes.MAX_VOLUME || r2.actionType == ActionTypes.SET_VOLUME))
                ) {
                    warnings.add(
                        SensorConflictWarning(
                            rule1Name = r1.name,
                            rule2Name = r2.name,
                            conflictReason = "Face-Down mute conflicts with Proximity volume increase (Auto-prioritized to Mute)",
                            severity = ConflictSeverity.WARNING
                        )
                    )
                }

                // Conflict 3: Duplicate Triggers with Opposing Flashlight Actions
                if (r1.triggerType == r2.triggerType && r1.triggerType != TriggerTypes.SHAKE) {
                    if ((r1.actionType == ActionTypes.FLASHLIGHT_ON && r2.actionType == ActionTypes.FLASHLIGHT_OFF) ||
                        (r1.actionType == ActionTypes.FLASHLIGHT_OFF && r2.actionType == ActionTypes.FLASHLIGHT_ON)
                    ) {
                        warnings.add(
                            SensorConflictWarning(
                                rule1Name = r1.name,
                                rule2Name = r2.name,
                                conflictReason = "Identical trigger '${r1.triggerType}' commands both Flashlight ON and OFF simultaneously",
                                severity = ConflictSeverity.CRITICAL
                            )
                        )
                    }
                }
            }
        }
        return warnings
    }
}
