package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object TriggerTypes {
    const val SHAKE = "SHAKE"
    const val HAND_WAVE = "HAND_WAVE"
    const val PROXIMITY_NEAR = "PROXIMITY_NEAR"
    const val PROXIMITY_FAR = "PROXIMITY_FAR"
    const val LIGHT_BELOW = "LIGHT_BELOW"
    const val LIGHT_ABOVE = "LIGHT_ABOVE"
    const val FLIP_FACE_DOWN = "FLIP_FACE_DOWN"
    const val FLIP_FACE_UP = "FLIP_FACE_UP"
    const val ORIENTATION_UPRIGHT = "ORIENTATION_UPRIGHT"
    const val BATTERY_LOW = "BATTERY_LOW"
    const val BATTERY_FULL = "BATTERY_FULL"
    const val CHARGER_CONNECTED = "CHARGER_CONNECTED"
    const val CHARGER_DISCONNECTED = "CHARGER_DISCONNECTED"

    val ALL = listOf(
        HAND_WAVE to "Hand Wave Gesture (Over Sensor/Screen)",
        SHAKE to "Phone Shake Gesture (Pocket-Protected)",
        PROXIMITY_NEAR to "Proximity Covered / In Pocket",
        PROXIMITY_FAR to "Proximity Uncovered / Picked Up",
        LIGHT_BELOW to "Ambient Light Drops Below",
        LIGHT_ABOVE to "Ambient Light Rises Above",
        FLIP_FACE_DOWN to "Flipped Face Down",
        FLIP_FACE_UP to "Flipped Face Up",
        ORIENTATION_UPRIGHT to "Phone Held Upright / Vertical",
        BATTERY_LOW to "Battery Below Threshold (%)",
        BATTERY_FULL to "Battery Fully Charged (100%)",
        CHARGER_CONNECTED to "Power Charger Connected",
        CHARGER_DISCONNECTED to "Power Charger Disconnected"
    )
}

object ActionTypes {
    const val TOGGLE_FLASHLIGHT = "TOGGLE_FLASHLIGHT"
    const val FLASHLIGHT_ON = "FLASHLIGHT_ON"
    const val FLASHLIGHT_OFF = "FLASHLIGHT_OFF"
    const val FLASHLIGHT_SOS = "FLASHLIGHT_SOS"
    const val SET_VOLUME = "SET_VOLUME"
    const val MUTE_ALL = "MUTE_ALL"
    const val MAX_VOLUME = "MAX_VOLUME"
    const val SPEAK_TTS = "SPEAK_TTS"
    const val ANNOUNCE_BATTERY = "ANNOUNCE_BATTERY"
    const val ANNOUNCE_TIME = "ANNOUNCE_TIME"
    const val LAUNCH_APP = "LAUNCH_APP"
    const val LAUNCH_CAMERA = "LAUNCH_CAMERA"
    const val PLAY_MUSIC = "PLAY_MUSIC"
    const val MEDIA_PLAY_PAUSE = "MEDIA_PLAY_PAUSE"
    const val MEDIA_NEXT = "MEDIA_NEXT"
    const val START_LISTENING = "START_LISTENING"
    const val VIBRATE = "VIBRATE"

    val ALL = listOf(
        START_LISTENING to "Activate Voice Mic / Take Command",
        MEDIA_PLAY_PAUSE to "Toggle Media Play / Pause",
        MEDIA_NEXT to "Skip to Next Song",
        TOGGLE_FLASHLIGHT to "Toggle Flashlight / Torch",
        FLASHLIGHT_ON to "Turn Flashlight ON",
        FLASHLIGHT_OFF to "Turn Flashlight OFF",
        FLASHLIGHT_SOS to "Flashlight SOS / Strobe Pulse",
        MUTE_ALL to "Mute All Media & Ringtone",
        MAX_VOLUME to "Set Max Volume (100%)",
        SET_VOLUME to "Set Volume (%)",
        SPEAK_TTS to "Spoken Voice Announcement",
        ANNOUNCE_BATTERY to "Voice Speak Battery Percentage",
        ANNOUNCE_TIME to "Voice Speak Current Time",
        LAUNCH_APP to "Launch Installed App",
        LAUNCH_CAMERA to "Open Camera App",
        PLAY_MUSIC to "Play Song / Launch Music",
        VIBRATE to "Haptic Pulse / Vibration"
    )
}

@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val triggerType: String,
    val triggerThreshold: Float = 0f,
    val actionType: String,
    val actionParam: String = "",
    val isEnabled: Boolean = true,
    val lastTriggeredTime: Long = 0L,
    val triggerCount: Int = 0,
    val cooldownSeconds: Int = 4
)
