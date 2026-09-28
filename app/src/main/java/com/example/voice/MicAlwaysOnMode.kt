package com.example.voice

enum class MicAlwaysOnMode(
    val code: String,
    val title: String,
    val shortLabel: String,
    val description: String
) {
    ALWAYS_ON_SCREEN_OFF_AND_ON(
        code = "ALWAYS_ON",
        title = "Always-On (Screen Off & On)",
        shortLabel = "ALWAYS MIC ON",
        description = "Continuous listening active even when phone screen is turned off or locked"
    ),
    SCREEN_OFF_ONLY(
        code = "SCREEN_OFF_ONLY",
        title = "Screen-Lock Only",
        shortLabel = "LOCK SCREEN ONLY",
        description = "Listens only when phone screen is locked or turned off. Saves battery when screen is awake"
    ),
    SCREEN_ON_ONLY(
        code = "SCREEN_ON",
        title = "Screen-On Only",
        shortLabel = "SCREEN ON",
        description = "Continuous listening only while screen is awake. Pauses when screen turns off"
    ),
    MANUAL(
        code = "MANUAL",
        title = "Push to Talk",
        shortLabel = "PUSH TO TALK",
        description = "Microphone activates only when tapped on HUD or Arc Reactor"
    );

    companion object {
        fun fromCode(code: String?): MicAlwaysOnMode {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: ALWAYS_ON_SCREEN_OFF_AND_ON
        }
    }
}
