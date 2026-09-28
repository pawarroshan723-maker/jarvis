package com.example.voice

enum class VoiceLanguage(
    val code: String,
    val label: String,
    val shortLabel: String,
    val speechLocale: String,
    val isDual: Boolean = false
) {
    AUTO("auto", "Dual (मराठी + English)", "MR/EN", "mr-IN", true),
    MARATHI("mr", "मराठी (Marathi)", "मराठी", "mr-IN", false),
    ENGLISH("en", "English (EN)", "EN", "en-IN", false);

    companion object {
        fun fromCode(code: String?): VoiceLanguage {
            return values().firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AUTO
        }
    }
}
