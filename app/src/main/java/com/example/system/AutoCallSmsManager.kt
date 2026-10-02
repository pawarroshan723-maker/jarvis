package com.example.system

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.AutoSmsRule
import com.example.data.CallSmsLog
import com.example.data.JarvisRepository
import com.example.hardware.HardwareController
import com.example.sensor.SensorHub
import com.example.voice.JarvisSpeechManager
import com.example.voice.MarathiTtsManager
import com.example.voice.VoiceLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class AutoCallSmsConfig(
    val isAutoSmsEnabled: Boolean = false, // Secure default: user must explicitly enable auto-SMS
    val isAutoReadSmsEnabled: Boolean = true,
    val isAutoAnnounceCallsEnabled: Boolean = true,
    val isAutoReplyMissedCallsEnabled: Boolean = false, // Secure default: off until user opts-in
    val activePreset: String = "NORMAL", // NORMAL, DRIVING, MEETING, EMERGENCY_SOS, CUSTOM
    val customAutoReplyText: String = "Jarvis Auto: Currently unavailable. I will get back to you shortly.",
    val emergencyContact: String = "",
    val emergencySmsText: String = "EMERGENCY: User requires urgent assistance. Location coordinates transmitting.",
    val antiSpamCooldownMinutes: Int = 3
)

class AutoCallSmsManager(
    private val context: Context,
    private val repository: JarvisRepository,
    private val telephonyManager: TelephonyAlarmManager,
    private val hardwareController: HardwareController,
    private val speechManager: JarvisSpeechManager,
    private val sensorHub: SensorHub,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "AutoCallSmsManager"
        private const val PREFS_NAME = "jarvis_auto_call_sms_prefs"

        private const val KEY_AUTO_SMS = "key_auto_sms_enabled"
        private const val KEY_AUTO_READ_SMS = "key_auto_read_sms_enabled"
        private const val KEY_AUTO_ANNOUNCE_CALLS = "key_auto_announce_calls_enabled"
        private const val KEY_AUTO_REPLY_MISSED = "key_auto_reply_missed_calls"
        private const val KEY_ACTIVE_PRESET = "key_active_preset"
        private const val KEY_CUSTOM_REPLY_TEXT = "key_custom_reply_text"
        private const val KEY_EMERGENCY_CONTACT = "key_emergency_contact"
        private const val KEY_EMERGENCY_TEXT = "key_emergency_text"
        private const val KEY_COOLDOWN_MINUTES = "key_cooldown_minutes"

        val PRESET_MESSAGES = mapOf(
            "NORMAL" to "Jarvis Auto: User is currently occupied and will respond as soon as available.",
            "DRIVING" to "Jarvis Auto: Currently driving safely. I will call or message you once arrived.",
            "MEETING" to "Jarvis Auto: In an important meeting. Please leave a message or text if urgent.",
            "EMERGENCY_SOS" to "Jarvis SOS: Emergency protocol engaged. Please contact emergency services or retry shortly.",
            "CUSTOM" to "Jarvis Auto: Currently unavailable. I will get back to you shortly."
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<AutoCallSmsConfig> = _config.asStateFlow()

    // Anti-spam cooldown per phone number: PhoneNumber -> LastRepliedTimestamp
    private val recentReplies = ConcurrentHashMap<String, Long>()

    // Track active ringing calls: PhoneNumber -> Timestamp
    private var lastRingingNumber: String? = null
    private var lastRingingName: String? = null
    private var callWasAnswered: Boolean = false

    init {
        Log.i(TAG, "AutoCallSmsManager initialized. Master Auto-SMS: ${_config.value.isAutoSmsEnabled}, Preset: ${_config.value.activePreset}")
        scope.launch(Dispatchers.IO) {
            enrichExistingLogsWithContactNames()
        }
    }

    private fun loadConfig(): AutoCallSmsConfig {
        return AutoCallSmsConfig(
            isAutoSmsEnabled = prefs.getBoolean(KEY_AUTO_SMS, false),
            isAutoReadSmsEnabled = prefs.getBoolean(KEY_AUTO_READ_SMS, true),
            isAutoAnnounceCallsEnabled = prefs.getBoolean(KEY_AUTO_ANNOUNCE_CALLS, true),
            isAutoReplyMissedCallsEnabled = prefs.getBoolean(KEY_AUTO_REPLY_MISSED, false),
            activePreset = prefs.getString(KEY_ACTIVE_PRESET, "NORMAL") ?: "NORMAL",
            customAutoReplyText = prefs.getString(KEY_CUSTOM_REPLY_TEXT, PRESET_MESSAGES["CUSTOM"]) ?: PRESET_MESSAGES["CUSTOM"]!!,
            emergencyContact = prefs.getString(KEY_EMERGENCY_CONTACT, "") ?: "",
            emergencySmsText = prefs.getString(KEY_EMERGENCY_TEXT, "EMERGENCY: User requires urgent assistance.") ?: "",
            antiSpamCooldownMinutes = prefs.getInt(KEY_COOLDOWN_MINUTES, 3)
        )
    }

    fun updateConfig(newConfig: AutoCallSmsConfig) {
        _config.value = newConfig
        prefs.edit()
            .putBoolean(KEY_AUTO_SMS, newConfig.isAutoSmsEnabled)
            .putBoolean(KEY_AUTO_READ_SMS, newConfig.isAutoReadSmsEnabled)
            .putBoolean(KEY_AUTO_ANNOUNCE_CALLS, newConfig.isAutoAnnounceCallsEnabled)
            .putBoolean(KEY_AUTO_REPLY_MISSED, newConfig.isAutoReplyMissedCallsEnabled)
            .putString(KEY_ACTIVE_PRESET, newConfig.activePreset)
            .putString(KEY_CUSTOM_REPLY_TEXT, newConfig.customAutoReplyText)
            .putString(KEY_EMERGENCY_CONTACT, newConfig.emergencyContact)
            .putString(KEY_EMERGENCY_TEXT, newConfig.emergencySmsText)
            .putInt(KEY_COOLDOWN_MINUTES, newConfig.antiSpamCooldownMinutes)
            .apply()
        Log.i(TAG, "Config saved: preset=${newConfig.activePreset}, autoSms=${newConfig.isAutoSmsEnabled}")
    }

    fun setPreset(preset: String) {
        val updated = _config.value.copy(activePreset = preset)
        updateConfig(updated)
    }

    fun toggleAutoSms(enabled: Boolean) {
        updateConfig(_config.value.copy(isAutoSmsEnabled = enabled))
    }

    fun toggleAutoReadSms(enabled: Boolean) {
        updateConfig(_config.value.copy(isAutoReadSmsEnabled = enabled))
    }

    fun toggleAutoAnnounceCalls(enabled: Boolean) {
        updateConfig(_config.value.copy(isAutoAnnounceCallsEnabled = enabled))
    }

    fun toggleAutoReplyMissedCalls(enabled: Boolean) {
        updateConfig(_config.value.copy(isAutoReplyMissedCallsEnabled = enabled))
    }

    fun setCustomReplyText(text: String) {
        updateConfig(_config.value.copy(customAutoReplyText = text))
    }

    fun setEmergencyContact(contact: String) {
        updateConfig(_config.value.copy(emergencyContact = contact))
    }

    // ==========================================
    // INCOMING CALL HANDLING
    // ==========================================

    fun onCallRinging(incomingNumber: String, overrideName: String? = null) {
        val number = incomingNumber.trim()
        val contactName = overrideName?.ifBlank { null } ?: resolveContactName(number)
        lastRingingNumber = number
        lastRingingName = contactName
        callWasAnswered = false

        Log.i(TAG, "Incoming call ringing from: $number ($contactName)")

        scope.launch(Dispatchers.IO) {
            repository.insertCallSmsLog(
                CallSmsLog(
                    type = "INCOMING_CALL",
                    phoneNumber = number,
                    contactName = contactName,
                    messageBody = if (contactName.isNotBlank()) "Call from $contactName" else "Incoming call ringing",
                    status = "RINGING"
                )
            )

            if (_config.value.isAutoAnnounceCallsEnabled) {
                val isMr = speechManager.selectedLanguage.value == VoiceLanguage.MARATHI
                val announceText = if (isMr) {
                    if (contactName.isNotBlank()) {
                        "$contactName यांचा फोन येत आहे."
                    } else {
                        "${formatSpokenNumber(number)} कडून फोन येत आहे."
                    }
                } else {
                    if (contactName.isNotBlank()) {
                        "Incoming call from $contactName, sir."
                    } else {
                        "Incoming call from ${formatSpokenNumber(number)}, sir."
                    }
                }
                speechManager.speak(announceText)
            }
        }
    }

    fun onCallAnswered() {
        callWasAnswered = true
        Log.i(TAG, "Call was answered")
    }

    fun onCallEnded() {
        val number = lastRingingNumber
        val name = lastRingingName ?: ""
        val answered = callWasAnswered

        lastRingingNumber = null
        lastRingingName = null
        callWasAnswered = false

        if (number.isNullOrBlank()) return

        scope.launch(Dispatchers.IO) {
            if (!answered) {
                Log.i(TAG, "Call missed or rejected from: $number")
                repository.insertCallSmsLog(
                    CallSmsLog(
                        type = "MISSED_CALL",
                        phoneNumber = number,
                        contactName = name,
                        messageBody = "Call was missed or rejected",
                        status = "MISSED"
                    )
                )

                if (_config.value.isAutoReplyMissedCallsEnabled) {
                    processMissedCallAutoReply(number, name)
                }
            } else {
                repository.insertCallSmsLog(
                    CallSmsLog(
                        type = "INCOMING_CALL",
                        phoneNumber = number,
                        contactName = name,
                        messageBody = "Call completed",
                        status = "COMPLETED"
                    )
                )
            }
        }
    }

    private suspend fun processMissedCallAutoReply(number: String, contactName: String) {
        if (isOnCooldown(number)) {
            Log.i(TAG, "Auto-reply to $number skipped due to anti-spam cooldown")
            return
        }

        val template = getEffectiveReplyTemplate()
        val interpolated = interpolateTemplate(template, contactName)

        val (success, msg) = telephonyManager.sendSms(number, interpolated)
        recentReplies[number] = System.currentTimeMillis()

        repository.insertCallSmsLog(
            CallSmsLog(
                type = "AUTO_REPLY_SMS",
                phoneNumber = number,
                contactName = contactName,
                messageBody = interpolated,
                status = if (success) "DELIVERED" else "FAILED"
            )
        )

        val voiceConfirm = if (contactName.isNotBlank()) {
            "Missed call from $contactName. Automatic SMS dispatched."
        } else {
            "Missed call. Automatic SMS dispatched."
        }
        speechManager.speak(voiceConfirm)
    }

    // ==========================================
    // INCOMING SMS HANDLING
    // ==========================================

    fun onSmsReceived(sender: String, messageBody: String) {
        val number = sender.trim()
        val contactName = resolveContactName(number)

        Log.i(TAG, "Incoming SMS from $number ($contactName): $messageBody")

        scope.launch(Dispatchers.IO) {
            repository.insertCallSmsLog(
                CallSmsLog(
                    type = "INCOMING_SMS",
                    phoneNumber = number,
                    contactName = contactName,
                    messageBody = messageBody,
                    status = "RECEIVED"
                )
            )

            // Auto-Read SMS Aloud via Jarvis Voice
            if (_config.value.isAutoReadSmsEnabled) {
                val isMr = speechManager.selectedLanguage.value == VoiceLanguage.MARATHI
                val isMsgMarathi = MarathiTtsManager.isDevanagari(messageBody) || MarathiTtsManager.isMarathiPhrase(messageBody)

                val textToSpeak = if (isMr && isMsgMarathi) {
                    val displayName = contactName.ifBlank { "अनोळखी नंबर" }
                    "$displayName कडून संदेश आला आहे: $messageBody"
                } else if (isMr && !isMsgMarathi) {
                    // Marathi language selected, but message itself is English!
                    // Speak purely in English so it NEVER sounds like distorted Russian English!
                    val displayName = contactName.ifBlank { "unknown contact" }
                    "Message received from $displayName: $messageBody"
                } else {
                    val displayName = contactName.ifBlank { "an unknown contact" }
                    "Message received from $displayName: $messageBody"
                }
                speechManager.speak(textToSpeak)
            }

            // Automatic SMS Auto-Responder
            if (_config.value.isAutoSmsEnabled) {
                processIncomingSmsAutoReply(number, contactName, messageBody)
            }
        }
    }

    private suspend fun processIncomingSmsAutoReply(senderNumber: String, contactName: String, incomingText: String) {
        if (isOnCooldown(senderNumber)) {
            Log.i(TAG, "Auto-reply to $senderNumber skipped due to anti-spam cooldown")
            return
        }

        val cleanText = incomingText.trim().lowercase(Locale.ROOT)

        // 1. Check against custom keyword Auto-SMS rules in Room DB
        val activeRules = repository.getActiveAutoSmsRulesSync()
        var matchedRule: AutoSmsRule? = null

        for (rule in activeRules) {
            val key = rule.keyword.trim().lowercase(Locale.ROOT)
            val isMatch = when (rule.matchType) {
                "EXACT" -> cleanText == key
                "STARTS_WITH" -> cleanText.startsWith(key)
                else -> cleanText.contains(key)
            }
            if (isMatch) {
                matchedRule = rule
                break
            }
        }

        val replyText = if (matchedRule != null) {
            interpolateTemplate(matchedRule.replyTemplate, contactName)
        } else {
            // Use active preset auto-reply
            val template = getEffectiveReplyTemplate()
            interpolateTemplate(template, contactName)
        }

        Log.i(TAG, "Dispatching Auto-SMS to $senderNumber: \"$replyText\" (Rule: ${matchedRule?.keyword ?: _config.value.activePreset})")

        val (success, _) = telephonyManager.sendSms(senderNumber, replyText)
        recentReplies[senderNumber] = System.currentTimeMillis()

        repository.insertCallSmsLog(
            CallSmsLog(
                type = "AUTO_REPLY_SMS",
                phoneNumber = senderNumber,
                contactName = contactName,
                messageBody = replyText,
                status = if (success) "DELIVERED" else "FAILED"
            )
        )
    }

    // ==========================================
    // TEMPLATE INTERPOLATION & UTILITIES
    // ==========================================

    private fun getEffectiveReplyTemplate(): String {
        val preset = _config.value.activePreset
        return if (preset == "CUSTOM") {
            _config.value.customAutoReplyText.ifBlank { PRESET_MESSAGES["CUSTOM"]!! }
        } else {
            PRESET_MESSAGES[preset] ?: PRESET_MESSAGES["NORMAL"]!!
        }
    }

    private fun interpolateTemplate(template: String, contactName: String): String {
        val telemetry = sensorHub.telemetry.value
        val batteryPct = "${telemetry.batteryLevel}"
        val chargingStatus = if (telemetry.isCharging) "Charging" else "On battery"
        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())

        return template
            .replace("{battery}", batteryPct)
            .replace("{charging}", chargingStatus)
            .replace("{time}", timeStr)
            .replace("{date}", dateStr)
            .replace("{sender}", contactName.ifBlank { "there" })
            .replace("{lux}", "${telemetry.lightLux.toInt()}")
    }

    private fun isOnCooldown(number: String): Boolean {
        val lastTime = recentReplies[number] ?: return false
        val cooldownMs = _config.value.antiSpamCooldownMinutes * 60 * 1000L
        return (System.currentTimeMillis() - lastTime) < cooldownMs
    }

    fun resolveContactName(number: String): String {
        val clean = number.trim()
        if (clean.isBlank()) return ""
        // Check Telephony reverse lookup (ContactsContract.PhoneLookup & normalized digits)
        val lookup = telephonyManager.lookupContactNameByNumber(clean)
        if (lookup.isNotBlank() && lookup != "Unknown") return lookup

        // Secondary fallback: searchContacts with normalized digits
        val digits = clean.filter { it.isDigit() }
        if (digits.length >= 7) {
            val results = telephonyManager.searchContacts(digits.takeLast(10))
            val match = results.firstOrNull()?.name
            if (!match.isNullOrBlank() && match != "Unknown") return match
        }
        return ""
    }

    fun enrichExistingLogsWithContactNames() {
        scope.launch(Dispatchers.IO) {
            try {
                val logs = repository.getRecentCallSmsLogsSync()
                for (log in logs) {
                    if (log.contactName.isBlank() && log.phoneNumber.isNotBlank()) {
                        val resolved = resolveContactName(log.phoneNumber)
                        if (resolved.isNotBlank()) {
                            repository.updateCallSmsLog(log.copy(contactName = resolved))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enriching existing call logs", e)
            }
        }
    }

    private fun formatSpokenNumber(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length >= 4) {
            "number ending in ${digits.takeLast(4)}"
        } else {
            number
        }
    }

    // ==========================================
    // SIMULATION TESTING HOOKS (For Emulator & Instant Verification)
    // ==========================================

    fun simulateIncomingCall(number: String, name: String, simulateMissed: Boolean) {
        scope.launch(Dispatchers.IO) {
            onCallRinging(number, name.ifBlank { null })
            kotlinx.coroutines.delay(2000)
            if (simulateMissed) {
                onCallEnded()
            } else {
                onCallAnswered()
                kotlinx.coroutines.delay(2000)
                onCallEnded()
            }
        }
    }

    fun simulateIncomingSms(number: String, message: String) {
        scope.launch(Dispatchers.IO) {
            onSmsReceived(number, message)
        }
    }

    fun triggerEmergencySos() {
        val contact = _config.value.emergencyContact
        val text = _config.value.emergencySmsText
        if (contact.isNotBlank()) {
            telephonyManager.sendSms(contact, text)
            speechManager.speak("Emergency SOS alert message transmitted to designated contact.")
        } else {
            speechManager.speak("Emergency SOS initiated. Please designate an emergency contact number in settings.")
        }
    }
}
