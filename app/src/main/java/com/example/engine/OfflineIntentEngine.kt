package com.example.engine

import com.example.data.ActionTypes
import com.example.data.AutomationRule
import com.example.data.JarvisRepository
import com.example.data.TriggerTypes
import com.example.hardware.HardwareController
import com.example.sensor.SensorTelemetry
import com.example.voice.MarathiTtsManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class IntentResult(
    val success: Boolean,
    val spokenResponse: String,
    val intentAction: String,
    val canFallbackToGemini: Boolean = false
)

class OfflineIntentEngine(
    private val hardware: HardwareController,
    private val repository: JarvisRepository,
    private val context: android.content.Context? = null,
    private val telephonyManager: com.example.system.TelephonyAlarmManager? = null,
    private val speechManager: com.example.voice.JarvisSpeechManager? = null,
    private val autoCallSmsManager: com.example.system.AutoCallSmsManager? = null,
    private val scheduledTaskManager: com.example.system.ScheduledTaskManager? = null
) {

    suspend fun processCommand(command: String, currentTelemetry: SensorTelemetry): IntentResult {
        // Guarantee CPU stays awake while resolving and executing intent on locked device
        hardware.acquireCpuWakeLock(10000, "Jarvis:OfflineIntentProcessing")

        val clean = command.trim().lowercase(Locale.ROOT)
        val isMarathi = clean.any { it in '\u0900'..'\u097F' }

        // PLAYER COMMANDS OVERVIEW / PLAYER HELP (PLAYER CMD)
        if (clean == "player cmd" || clean == "player command" || clean == "player commands" ||
            clean == "player help" || clean == "player status" || clean == "media commands" ||
            clean.contains("प्लेअर कमांड") || clean.contains("म्युझिक कमांड") || clean.contains("प्लेअर हेल्प")
        ) {
            val isLocked = hardware.isDeviceLocked()
            val lockStatusStr = if (isLocked) "Device is locked (WakeLock active)" else "Device is awake"
            val msg = if (isMarathi) {
                "प्लेअर कोर आणि वेक लॉक सिस्टीम सज्ज आहे, सर. $lockStatusStr. तुम्ही 'गाणे लावा <नाव>', 'यूट्यूबवर <नाव> प्ले करा', 'VLC मध्ये प्ले करा', 'स्पॉटिफायवर प्ले करा', 'स्थानिक गाणे लावा', 'गाणे थांबवा / सुरू करा', 'पुढचे / मागचे गाणे', 'आवाज वाढवा / कमी करा', 'वेक लॉक चालू / बंद' बोलू शकता."
            } else {
                "Player Engine & Lockscreen WakeLock Core ready, sir. Status: $lockStatusStr. Available Player Commands:\n• Play <song title> (Auto YouTube / Local)\n• Play <song> on YouTube / VLC / Spotify\n• Play local song <name>\n• Pause / Resume / Stop playback\n• Next / Previous track\n• Volume Up / Down / Max\n• Wake Lock ON / OFF (Keeps player & background core active on locked screen)\n• Wake screen"
            }
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "प्लेअर कोर आणि वेक लॉक सज्ज आहे, सर. उपलब्ध कमांड्स स्क्रीनवर दाखवल्या आहेत."
                else "Player Engine and WakeLock Core ready, sir. All player and media controls are active even on locked device.",
                intentAction = "PLAYER_CMD"
            )
        }

        // WAKE LOCK & LOCKSCREEN HARDWARE COMMANDS
        if (clean.contains("wake lock on") || clean.contains("enable wake lock") ||
            clean.contains("keep awake") || clean.contains("keep screen awake") ||
            clean.contains("stay awake") || clean.contains("वेक लॉक चालू") ||
            clean.contains("स्क्रीन चालू ठेवा")
        ) {
            hardware.setPersistentWakeLock(true)
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "वेक लॉक सक्रिय केला आहे, सर. स्क्रीन बंद किंवा लॉक असली तरी प्लेअर आणि ऑटोमेशन चालू राहील."
                else "Persistent WakeLock engaged, sir. CPU will remain active to process background playback and automations when device is locked.",
                intentAction = "WAKELOCK_ON"
            )
        }

        if (clean.contains("wake lock off") || clean.contains("disable wake lock") ||
            clean.contains("turn off wake lock") || clean.contains("वेक लॉक बंद") ||
            clean.contains("वेक लॉक ऑफ")
        ) {
            hardware.setPersistentWakeLock(false)
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "वेक लॉक बंद केला आहे. सामान्य बॅटरी सेव्हिंग मोड सुरू आहे."
                else "Persistent WakeLock released, sir. Device returned to standard power-saving mode.",
                intentAction = "WAKELOCK_OFF"
            )
        }

        if (clean.contains("wake screen") || clean.contains("wake up screen") ||
            clean.contains("turn on screen") || clean.contains("wake up phone") ||
            clean.contains("स्क्रीन उघडा") || clean.contains("स्क्रीन ऑन करा") ||
            clean.contains("स्क्रीन चालू करा")
        ) {
            hardware.wakeUpScreen(8000)
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "स्क्रीन उघडली आहे, सर." else "Illuminating display screen, sir.",
                intentAction = "WAKE_SCREEN"
            )
        }

        if (clean.contains("wake lock status") || clean.contains("wakelock status") ||
            clean.contains("lock status") || clean.contains("is screen locked") ||
            clean.contains("डिव्हाइस लॉक आहे का") || clean.contains("स्क्रीन स्टेटस")
        ) {
            val isLocked = hardware.isDeviceLocked()
            val isWakeActive = hardware.isWakeLockActive()
            val isScreenOn = hardware.isScreenOn()
            val msg = if (isMarathi) {
                "डिव्हाइस ${if (isLocked) "लॉक आहे" else "अनलॉक आहे"}, स्क्रीन ${if (isScreenOn) "चालू आहे" else "बंद आहे"}, आणि वेक लॉक ${if (isWakeActive) "सक्रिय आहे" else "बंद आहे"}, सर."
            } else {
                "Device is ${if (isLocked) "locked" else "unlocked"}, display is ${if (isScreenOn) "ON" else "OFF"}, and background WakeLock is ${if (isWakeActive) "active" else "idle"}, sir."
            }
            return IntentResult(true, msg, "LOCK_STATUS")
        }

        // WAKE-WORD GATEKEEPER LOCK COMMANDS (ALEXA HANDS-FREE RESTRICTION)
        if (clean.contains("wake word lock on") || clean.contains("enable wake word lock") ||
            clean.contains("lock wake word") || clean.contains("strict wake word") ||
            clean.contains("only jarvis") || clean.contains("alexa mode") ||
            clean.contains("जार्व्हिस लॉक चालू") || clean.contains("वेक वर्ड लॉक")
        ) {
            speechManager?.setWakeWordLock(true)
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "वेक वर्ड लॉक चालू केला आहे, सर. जार्व्हिस बोलल्याशिवाय कोणताही आवाज स्वीकारला जाणार नाही."
                else "Wake-Word Gatekeeper Lock engaged, sir. Voice commands will strictly require 'Jarvis' wake-word to execute.",
                intentAction = "WAKE_WORD_LOCK_ON"
            )
        }

        if (clean.contains("wake word lock off") || clean.contains("disable wake word lock") ||
            clean.contains("unlock wake word") || clean.contains("open mic mode") ||
            clean.contains("वेक वर्ड लॉक बंद")
        ) {
            speechManager?.setWakeWordLock(false)
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "वेक वर्ड लॉक बंद केला आहे. ओपन माईक सुरू आहे."
                else "Wake-Word Gatekeeper Lock disabled, sir. Continuous open microphone active.",
                intentAction = "WAKE_WORD_LOCK_OFF"
            )
        }

        // ALWAYS-ON MIC COMMANDS (SCREEN OFF / SCREEN ON)
        if (clean.contains("always mic on") || clean.contains("set always mic on") ||
            clean.contains("mic always on") || clean.contains("always on mic") ||
            clean.contains("screen off mic") || clean.contains("mic on when screen off") ||
            clean.contains("keep mic on") || clean.contains("नेहमी माईक चालू") ||
            clean.contains("स्क्रीन बंद असताना माईक")
        ) {
            speechManager?.setMicAlwaysOnMode(com.example.voice.MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON)
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "नेहमी माईक चालू मोड सक्रिय केला आहे, सर. स्क्रीन बंद असली तरी जार्व्हिस सतत ऐकत राहील."
                else "Always-on microphone active, sir. Jarvis will now listen continuously even when the screen is powered off or locked.",
                intentAction = "ALWAYS_MIC_ON"
            )
        }

        if (clean.contains("turn off always mic") || clean.contains("disable always mic") ||
            clean.contains("push to talk") || clean.contains("manual mic") || clean == "mic off" ||
            clean.contains("मॅन्युअल माईक")
        ) {
            speechManager?.setMicAlwaysOnMode(com.example.voice.MicAlwaysOnMode.MANUAL)
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "पुश टू टॉक मोड निवडला आहे. आवश्यकतेनुसार टॅप करा."
                else "Always-on microphone deactivated, sir. Reverted to push-to-talk manual mode.",
                intentAction = "MANUAL_MIC"
            )
        }

        if (clean.contains("screen on mic only") || clean.contains("mic screen on") ||
            clean.contains("mic only when screen on")
        ) {
            speechManager?.setMicAlwaysOnMode(com.example.voice.MicAlwaysOnMode.SCREEN_ON_ONLY)
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "स्क्रीन चालू असतानाच माईक चालू राहील."
                else "Microphone set to screen-on continuous listening mode, sir.",
                intentAction = "SCREEN_ON_MIC"
            )
        }

        // AUTO CALL & SMS SYSTEM COMMANDS
        if (clean.contains("turn on auto sms") || clean.contains("auto sms on") ||
            clean.contains("enable auto sms") || clean.contains("automatic sms on") ||
            clean.contains("ऑटो एसएमएस चालू")
        ) {
            autoCallSmsManager?.toggleAutoSms(true)
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "ऑटोमॅटिक एसएमएस सिस्टीम सक्रिय केली आहे."
                else "Automatic SMS response system is now online, sir.",
                intentAction = "AUTO_SMS_ON"
            )
        }

        if (clean.contains("turn off auto sms") || clean.contains("auto sms off") ||
            clean.contains("disable auto sms") || clean.contains("automatic sms off") ||
            clean.contains("ऑटो एसएमएस बंद")
        ) {
            autoCallSmsManager?.toggleAutoSms(false)
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "ऑटोमॅटिक एसएमएस सिस्टीम बंद केली आहे."
                else "Automatic SMS response system has been deactivated.",
                intentAction = "AUTO_SMS_OFF"
            )
        }

        if (clean.contains("driving mode") || clean.contains("activate driving mode") ||
            clean.contains("ड्रायव्हिंग मोड")
        ) {
            autoCallSmsManager?.setPreset("DRIVING")
            autoCallSmsManager?.toggleAutoSms(true)
            autoCallSmsManager?.toggleAutoReplyMissedCalls(true)
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "ड्रायव्हिंग मोड सक्रिय. मिस्ड कॉल्स आणि एसएमएसला सुरक्षित वाहनचालक उत्तर दिले जाईल."
                else "Driving mode engaged. Automatic caller response enabled for safe travel, sir.",
                intentAction = "PRESET_DRIVING"
            )
        }

        if (clean.contains("meeting mode") || clean.contains("activate meeting mode") ||
            clean.contains("मीटिंग मोड")
        ) {
            autoCallSmsManager?.setPreset("MEETING")
            autoCallSmsManager?.toggleAutoSms(true)
            autoCallSmsManager?.toggleAutoReplyMissedCalls(true)
            hardware.muteAllAudio()
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "मीटिंग मोड सक्रिय. फोन म्यूट केला असून ऑटोमॅटिक उत्तर पाठवले जाईल."
                else "Meeting mode active, sir. Audio silenced and automated meeting replies primed.",
                intentAction = "PRESET_MEETING"
            )
        }

        if (clean.contains("normal mode") || clean.contains("standard mode") ||
            clean.contains("सर्वसाधारण मोड")
        ) {
            autoCallSmsManager?.setPreset("NORMAL")
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "सर्वसाधारण मोड पुन्हा सुरू केला आहे."
                else "Restored standard operational preset, sir.",
                intentAction = "PRESET_NORMAL"
            )
        }

        if (clean.contains("emergency sos") || clean == "sos" || clean.contains("send sos") ||
            clean.contains("आपत्कालीन संदेश")
        ) {
            autoCallSmsManager?.triggerEmergencySos()
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "आपत्कालीन एसओएस अलर्ट प्रसारित केला जात आहे!"
                else "Emergency SOS alert transmitting to designated emergency contacts!",
                intentAction = "EMERGENCY_SOS"
            )
        }

        // 1. GREETINGS & STATUS REPORT (English & Marathi)
        if (clean == "jarvis" || clean == "hello jarvis" || clean == "hi jarvis" || clean == "are you there" ||
            clean == "hello" || clean == "hi" || clean == "hey jarvis" || clean == "hey" || clean == "wake up" ||
            clean == "नमस्कार" || clean == "हॅलो" || clean == "हॅलो जार्व्हिस" || clean == "नमस्ते" || clean == "हाय जार्व्हिस"
        ) {
            val mrGreetings = listOf(
                "नमस्कार सर! जार्व्हिस आपल्या सेवेत सज्ज आहे. सांगा मी कशी मदत करू?",
                "होय सर, मी ऐकत आहे. काय आज्ञा आहे?",
                "नमस्कार! सर्व यंत्रणा कार्यरत आहेत. मी आपल्या मदतीसाठी तयार आहे."
            )
            val enGreetings = listOf(
                "Online and operational, sir. How may I assist you?",
                "At your service, sir. Standing by for your instructions.",
                "Yes sir, all systems nominal. What is your command?",
                "Jarvis online, sir. How can I help you today?"
            )
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) mrGreetings.random() else enGreetings.random(),
                intentAction = "GREETING"
            )
        }

        if (clean.contains("status report") || clean.contains("system status") || clean.contains("diagnostics") ||
            clean == "status" || clean == "report" || clean.contains("how are you") ||
            clean.contains("कसा आहेस") || clean.contains("स्टेटस रिपोर्ट") || clean.contains("सिस्टम स्थिती") || clean.contains("कशी आहे सिस्टीम")
        ) {
            val batt = currentTelemetry.batteryLevel
            val charging = if (currentTelemetry.isCharging) (if (isMarathi) "चार्ज होत आहे" else "charging") else (if (isMarathi) "बॅटरीवर आहे" else "discharging")
            val vol = hardware.getMediaVolumePercent()
            val ringer = hardware.getRingerModeString()
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "सिस्टीम स्थिती: बॅटरी $batt टक्के ($charging). आवाज $vol टक्के ($ringer मोड). सर्व मुख्य सेवा उत्तम कार्यरत आहेत, सर."
                else "System status: Battery is at $batt percent, $charging. Media volume is $vol percent in $ringer mode. All primary services are operational, sir.",
                intentAction = "STATUS_REPORT"
            )
        }

        if (clean.contains("what can you do") || clean.contains("help") || clean.contains("commands") ||
            clean.contains("तू काय करू शकतोस") || clean.contains("मदत") || clean.contains("कमांड")
        ) {
            return IntentResult(
                success = true,
                spokenResponse = if (isMarathi) "मी फोन कॉल्स, एसएमएस, टॉर्च, आवाज आणि ऑटोमेशन नियंत्रित करू शकतो. गणित सोडवू शकतो आणि ऑनलाइन जेमिनीद्वारे ताजे जोक व प्रश्नांची सविस्तर उत्तरे देऊ शकतो, सर."
                else "I can handle direct phone calls, compose SMS, control your flashlight, adjust audio volumes, solve calculations, and generate dynamic jokes and deep knowledge via online Gemini intelligence, sir.",
                intentAction = "HELP"
            )
        }

        // TIME & DATE (Indian Standard Time + Device Local Time)
        val asksIndiaTime = clean.contains("india") || clean.contains("indian") || clean.contains("ist") || clean.contains("भारत") || clean.contains("इंडिया")

        val isTimeQuery = clean.contains("what is time") || clean.contains("what is the time") || clean.contains("what's the time") ||
                clean.contains("what time") || clean == "time" || clean.contains("current time") ||
                clean.contains("what this time") || clean.contains("what is this time") || clean == "this time" ||
                clean.contains("tell time") || clean.contains("tell me time") || clean.contains("tell the time") ||
                clean.contains("time please") || clean.contains("time now") || clean.contains("time right now") ||
                clean.contains("india time") || clean.contains("indian time") || clean.contains("time in india") ||
                clean.contains("ist time") || clean.contains("time kya hai") || clean.contains("samay kya hai") ||
                clean.contains("वेळ काय झाली") || clean.contains("किती वाजले") || clean.contains("वेळ सांगा") || clean == "वेळ" ||
                clean.contains("वेळ किती") || clean.contains("सध्याची वेळ") ||
                clean.contains("भारतातील वेळ") || clean.contains("इंडिया वेळ") || clean.contains("आता काय वेळ झाली")

        if (isTimeQuery) {
            val now = Calendar.getInstance()
            val hour24 = now.get(Calendar.HOUR_OF_DAY)
            val rawHour = now.get(Calendar.HOUR)
            val hour12 = if (rawHour == 0) 12 else rawHour
            val minute = now.get(Calendar.MINUTE)
            val isPm = hour24 >= 12
            val amPm = if (isPm) "PM" else "AM"

            val hourWordEn = MarathiTtsManager.numberToEnglishWords(hour12)
            val minuteWordEn = when {
                minute == 0 -> if (amPm.isNotEmpty()) "" else "o'clock"
                minute in 1..9 -> "oh " + MarathiTtsManager.numberToEnglishWords(minute)
                else -> MarathiTtsManager.numberToEnglishWords(minute)
            }
            val enTimeSpoken = if (minuteWordEn.isEmpty()) "$hourWordEn $amPm" else "$hourWordEn $minuteWordEn $amPm"

            val hourWordMr = MarathiTtsManager.numberToMarathiWords(hour12)
            val periodMr = when {
                hour24 in 0..3 -> "मध्यरात्री"
                hour24 in 4..11 -> "सकाळी"
                hour24 in 12..15 -> "दुपारी"
                hour24 in 16..19 -> "संध्याकाळी"
                else -> "रात्री"
            }
            val mrTimeSpoken = if (minute == 0) {
                "$periodMr $hourWordMr वाजता"
            } else {
                "$periodMr $hourWordMr वाजून ${MarathiTtsManager.numberToMarathiWords(minute)} मिनिटे"
            }

            val reply = if (asksIndiaTime) {
                if (isMarathi) "भारतात सध्याची वेळ $mrTimeSpoken झाली आहे, सर."
                else "The current time in India is $enTimeSpoken, sir."
            } else {
                if (isMarathi) "सध्याची वेळ $mrTimeSpoken झाली आहे, सर."
                else "The time is $enTimeSpoken, sir."
            }

            return IntentResult(
                true,
                reply,
                "QUERY_TIME"
            )
        }

        if (clean.contains("what date") || clean.contains("what day") || clean == "date" || clean.contains("today's date") ||
            clean.contains("आजची तारीख") || clean.contains("आज कोणता वार") || clean.contains("तारीख काय") || clean == "तारीख"
        ) {
            val now = Calendar.getInstance()
            val dayOfWeekEn = SimpleDateFormat("EEEE", Locale.US).format(now.time)
            val dateEn = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(now.time)

            val daysMr = mapOf(
                "Monday" to "सोमवार", "Tuesday" to "मंगळवार", "Wednesday" to "बुधवार",
                "Thursday" to "गुरुवार", "Friday" to "शुक्रवार", "Saturday" to "शनिवार", "Sunday" to "रविवार"
            )
            val monthsMr = mapOf(
                "January" to "जानेवारी", "February" to "फेब्रुवारी", "March" to "मार्च",
                "April" to "एप्रिल", "May" to "मे", "June" to "जून",
                "July" to "जुलै", "August" to "ऑगस्ट", "September" to "सप्टेंबर",
                "October" to "ऑक्टोबर", "November" to "नोव्हेंबर", "December" to "डिसेंबर"
            )
            val monthEn = SimpleDateFormat("MMMM", Locale.US).format(now.time)
            val day = now.get(Calendar.DAY_OF_MONTH)
            val year = now.get(Calendar.YEAR)

            val dayMr = daysMr[dayOfWeekEn] ?: dayOfWeekEn
            val monthMr = monthsMr[monthEn] ?: monthEn
            val dayWordMr = MarathiTtsManager.numberToMarathiWords(day)

            return IntentResult(
                true,
                if (isMarathi) "आज $dayMr, $dayWordMr $monthMr $year आहे, सर."
                else "Today is $dayOfWeekEn, $dateEn, sir.",
                "QUERY_DATE"
            )
        }

        // 2. FLASHLIGHT / TORCH (English & Marathi)
        if (clean.contains("turn on flashlight") || clean.contains("flashlight on") ||
            clean.contains("torch on") || clean.contains("turn on torch") || clean == "lumos" ||
            clean == "flashlight" || clean == "torch" || clean.contains("turn on the light") || clean.contains("turn on light") ||
            clean.contains("टॉर्च चालू") || clean.contains("फ्लॅश चालू") || clean.contains("लाईट चालू") ||
            clean.contains("टॉर्च लावा") || clean.contains("लाईट लावा") || clean.contains("टॉर्च ऑन") || clean.contains("फ्लॅश ऑन")
        ) {
            val success = hardware.setFlashlight(true)
            val msg = if (isMarathi) {
                if (success) "टॉर्च चालू केली आहे, सर." else "कॅमेरा फ्लॅश चालू करता आला नाही."
            } else {
                if (success) "Flashlight illuminated, sir." else "Unable to activate camera flash."
            }
            return IntentResult(success, msg, "TORCH_ON")
        }

        if (clean.contains("turn off flashlight") || clean.contains("flashlight off") ||
            clean.contains("torch off") || clean.contains("turn off torch") || clean == "nox" ||
            clean.contains("turn off the light") || clean.contains("turn off light") ||
            clean.contains("टॉर्च बंद") || clean.contains("फ्लॅश बंद") || clean.contains("लाईट बंद") ||
            clean.contains("दिवा बंद") || clean.contains("टॉर्च ऑफ") || clean.contains("फ्लॅश ऑफ")
        ) {
            val success = hardware.setFlashlight(false)
            val msg = if (isMarathi) {
                if (success) "टॉर्च बंद केली आहे." else "टॉर्च बंद करता आली नाही."
            } else {
                if (success) "Flashlight deactivated." else "Unable to deactivate flashlight."
            }
            return IntentResult(success, msg, "TORCH_OFF")
        }

        if (clean.contains("toggle flashlight") || clean.contains("toggle torch") || clean.contains("टॉर्च टॉगल") || clean.contains("फ्लॅश टॉगल")) {
            val success = hardware.toggleFlashlight()
            val state = if (hardware.isFlashlightActive()) "activated" else "deactivated"
            val msg = if (isMarathi) {
                if (hardware.isFlashlightActive()) "टॉर्च चालू केली आहे." else "टॉर्च बंद केली आहे."
            } else {
                "Flashlight $state, sir."
            }
            return IntentResult(success, msg, "TORCH_TOGGLE")
        }

        // 3. AUDIO & VOLUME CONTROL (English & Marathi)
        if (clean.contains("mute phone") || clean.contains("mute all") || clean == "silence" || clean.contains("silent mode") || clean == "mute" ||
            clean.contains("म्यूट करा") || clean.contains("शांत करा") || clean.contains("सायलेंट करा") || clean.contains("आवाज बंद करा")
        ) {
            val success = hardware.muteAllAudio()
            val msg = if (isMarathi) "सर्व आवाज म्यूट केला आहे आणि फोन सायलेंट मोडवर ठेवला आहे." else "All audio streams muted and set to silent mode."
            return IntentResult(success, msg, "MUTE_AUDIO")
        }

        if (clean.contains("volume up") || clean.contains("increase volume") || clean.contains("turn up volume") ||
            clean.contains("आवाज वाढवा") || clean.contains("आवाज मोठा करा") || clean.contains("व्हॉल्युम वाढवा")
        ) {
            val current = hardware.getMediaVolumePercent()
            val newVol = (current + 25).coerceAtMost(100)
            hardware.setMediaVolumePercent(newVol)
            val msg = if (isMarathi) "आवाज वाढवून $newVol टक्के केला आहे." else "Volume increased to $newVol percent."
            return IntentResult(true, msg, "VOLUME_UP")
        }

        if (clean.contains("volume down") || clean.contains("decrease volume") || clean.contains("turn down volume") ||
            clean.contains("आवाज कमी करा") || clean.contains("आवाज बारीक करा") || clean.contains("व्हॉल्युम कमी करा")
        ) {
            val current = hardware.getMediaVolumePercent()
            val newVol = (current - 25).coerceAtLeast(0)
            hardware.setMediaVolumePercent(newVol)
            val msg = if (isMarathi) "आवाज कमी करून $newVol टक्के केला आहे." else "Volume reduced to $newVol percent."
            return IntentResult(true, msg, "VOLUME_DOWN")
        }

        if (clean.contains("max volume") || clean.contains("maximum volume") || clean.contains("full volume") ||
            clean.contains("फुल आवाज") || clean.contains("आवाज पूर्ण करा") || clean.contains("जास्तीत जास्त आवाज")
        ) {
            val success = hardware.setMaxVolume()
            val msg = if (isMarathi) "आवाज १०० टक्के पूर्ण वाढवला आहे." else "Volume raised to maximum output."
            return IntentResult(success, msg, "MAX_VOLUME")
        }

        if (clean.contains("vibrate mode") || clean.contains("set to vibrate") || clean.contains("व्हायब्रेशन मोड") || clean.contains("व्हायब्रेट मोड")) {
            val success = hardware.setRingerMode("VIBRATE")
            val msg = if (isMarathi) "फोन व्हायब्रेशन मोडवर सेट केला आहे." else "Ringer set to vibration mode."
            return IntentResult(success, msg, "RINGER_VIBRATE")
        }

        if (clean.contains("normal ringer") || clean.contains("unmute") || clean.contains("आवाज चालू करा") || clean.contains("अनम्यूट करा")) {
            hardware.setRingerMode("NORMAL")
            hardware.setMediaVolumePercent(60)
            val msg = if (isMarathi) "आवाज पूर्ववत सुरू केला आहे." else "Audio and ringer restored to standard level."
            return IntentResult(true, msg, "UNMUTE")
        }

        val volumeRegex = Regex("""(?:set|change)?\s*volume\s*(?:to)?\s*(\d{1,3})%?""")
        val volMatch = volumeRegex.find(clean)
        if (volMatch != null) {
            val targetPercent = volMatch.groupValues[1].toIntOrNull()
            if (targetPercent != null) {
                val clamped = targetPercent.coerceIn(0, 100)
                val success = hardware.setMediaVolumePercent(clamped)
                return IntentResult(success, "Media volume adjusted to $clamped percent.", "SET_VOLUME")
            }
        }

        // 4. SENSOR QUERIES (English & Marathi)
        if ((clean.contains("battery") && (clean.contains("what") || clean.contains("level") || clean.contains("how much"))) ||
            clean.contains("बॅटरी किती") || clean.contains("चार्जिंग किती") || clean.contains("बॅटरी लेव्हल") || clean.contains("बॅटरी सांगा")
        ) {
            val batt = currentTelemetry.batteryLevel
            val state = if (currentTelemetry.isCharging) (if (isMarathi) "चार्जिंग चालू आहे" else "currently charging") else (if (isMarathi) "बॅटरीवर चालू आहे" else "on battery power")
            val msg = if (isMarathi) "सध्या फोनची बॅटरी $batt टक्के आहे ($state)." else "Current battery level is $batt percent, $state."
            return IntentResult(true, msg, "QUERY_BATTERY")
        }

        if ((clean.contains("light") && (clean.contains("how much") || clean.contains("level") || clean.contains("lux") || clean.contains("ambient"))) ||
            clean.contains("प्रकाश किती") || clean.contains("लाईट किती") || clean.contains("उजेड किती")
        ) {
            val lux = currentTelemetry.lightLux.toInt()
            val desc = when {
                lux < 10 -> if (isMarathi) "पूर्ण अंधार" else "pitch darkness"
                lux < 100 -> if (isMarathi) "मंद प्रकाश" else "dim indoor lighting"
                lux < 1000 -> if (isMarathi) "चांगला प्रकाश" else "well-lit workspace"
                else -> if (isMarathi) "प्रखर सूर्यप्रकाश" else "bright outdoor sunshine"
            }
            val msg = if (isMarathi) "सध्याचा प्रकाश $lux लक्स आहे ($desc)." else "Ambient illuminance reads $lux lux, corresponding to $desc."
            return IntentResult(true, msg, "QUERY_LIGHT")
        }

        if (clean.contains("orientation") || clean.contains("is phone face down") || clean.contains("फोन कसा आहे") || clean.contains("फोनची दिशा")) {
            val orient = currentTelemetry.orientation.name.replace("_", " ").lowercase()
            val msg = if (isMarathi) "फोन सध्या $orient स्थितीत आहे." else "Device attitude is calibrated to $orient."
            return IntentResult(true, msg, "QUERY_ORIENTATION")
        }

        // 4a. MEDIA CONTROLS (PAUSE, RESUME, STOP, NEXT, PREVIOUS)
        if (clean == "pause" || clean == "pause music" || clean == "pause song" ||
            clean == "pause video" || clean == "pause playback" || clean == "pause youtube" ||
            clean.contains("गाणे थांबवा") || clean.contains("गाणं थांबवा") || clean.contains("पॉज करा") ||
            clean == "थांबवा" || clean == "पॉज"
        ) {
            val (success, _) = hardware.controlMedia(com.example.hardware.MediaControlAction.PAUSE)
            val msg = if (isMarathi) "गाणे थांबवले आहे, सर." else "Playback paused, sir."
            return IntentResult(success, msg, "MEDIA_PAUSE")
        }

        if (clean == "resume" || clean == "resume music" || clean == "resume song" ||
            clean == "resume playback" || clean == "continue music" || clean == "unpause" ||
            clean.contains("गाणे पुन्हा सुरू करा") || clean.contains("गाणे चालू करा") || clean == "सुरू करा"
        ) {
            val (success, _) = hardware.controlMedia(com.example.hardware.MediaControlAction.PLAY)
            val msg = if (isMarathi) "गाणे पुन्हा सुरू केले आहे, सर." else "Playback resumed, sir."
            return IntentResult(success, msg, "MEDIA_RESUME")
        }

        if (clean == "next" || clean == "next song" || clean == "next track" ||
            clean == "next video" || clean == "skip" || clean == "skip song" || clean == "skip track" ||
            clean.contains("पुढचे गाणे") || clean.contains("पुढील गाणे") || clean.contains("नेक्स्ट गाणे") ||
            clean == "पुढचे" || clean == "नेक्स्ट"
        ) {
            val (success, _) = hardware.controlMedia(com.example.hardware.MediaControlAction.NEXT)
            val msg = if (isMarathi) "पुढचे गाणे लावले आहे, सर." else "Skipped to next track, sir."
            return IntentResult(success, msg, "MEDIA_NEXT")
        }

        if (clean == "previous" || clean == "previous song" || clean == "previous track" ||
            clean == "previous video" || clean == "back song" || clean == "play previous" ||
            clean.contains("मागचे गाणे") || clean.contains("मागील गाणे") || clean == "मागचे"
        ) {
            val (success, _) = hardware.controlMedia(com.example.hardware.MediaControlAction.PREVIOUS)
            val msg = if (isMarathi) "मागचे गाणे लावले आहे, सर." else "Playing previous track, sir."
            return IntentResult(success, msg, "MEDIA_PREVIOUS")
        }

        if (clean == "stop" || clean == "stop music" || clean == "stop song" ||
            clean == "stop playback" || clean == "stop video" || clean == "stop youtube" ||
            clean.contains("गाणे बंद करा") || clean.contains("म्युझिक बंद करा") || clean.contains("संगीत बंद करा")
        ) {
            val (success, _) = hardware.controlMedia(com.example.hardware.MediaControlAction.STOP)
            val msg = if (isMarathi) "गाणे बंद केले आहे, सर." else "Playback stopped, sir."
            return IntentResult(success, msg, "MEDIA_STOP")
        }

        // 4b. CLOSE APP / CLOSE YOUTUBE / RETURN HOME
        val isCloseCommand = clean.startsWith("close ") || clean.startsWith("exit ") ||
            clean.startsWith("kill ") || clean.endsWith(" बंद करा") || clean.endsWith(" बंद कर") ||
            clean == "go home" || clean == "home screen" || clean == "return home" ||
            clean == "back to home" || clean == "minimize" || clean.contains("होम स्क्रीन") ||
            clean.contains("होम वर जा")

        if (isCloseCommand) {
            val targetApp = clean
                .replace("close", "")
                .replace("exit", "")
                .replace("kill", "")
                .replace("go home", "")
                .replace("home screen", "")
                .replace("return home", "")
                .replace("back to home", "")
                .replace("minimize", "")
                .replace("होम स्क्रीन", "")
                .replace("होम वर जा", "")
                .replace("बंद करा", "")
                .replace("बंद कर", "")
                .replace("app", "")
                .trim()

            val (success, _) = hardware.closeAppOrGoHome(targetApp.ifBlank { null })
            val appLabel = if (targetApp.isNotBlank()) targetApp else "app"
            val msg = if (isMarathi) {
                if (targetApp.isNotBlank()) "$appLabel बंद करून मुख्य स्क्रीनवर परत आलो आहे, सर."
                else "मुख्य स्क्रीनवर परत आलो आहे, सर."
            } else {
                if (targetApp.isNotBlank()) "Closed $appLabel and returned to Home, sir."
                else "Returned to Home screen, sir."
            }
            return IntentResult(success, msg, "CLOSE_APP")
        }

        // 4c. SONG & MUSIC PLAYBACK (English & Marathi) with YouTube, VLC, Spotify & Local Audio Routing
        val isMusicCommand = clean.startsWith("play song") || clean.startsWith("play music") || clean.startsWith("play songs") ||
            clean.startsWith("play track") || clean.startsWith("play local") || clean.startsWith("play offline") ||
            clean.contains("गाणे लावा") || clean.contains("गाणी वाजवा") || clean.contains("गाणी लावा") ||
            clean.contains("संगीत चालू करा") || clean.contains("सॉन्ग लावा") || clean.contains("सॉन्ग वाजवा") ||
            clean.contains("यूट्यूब वर") || clean.contains("यूट्यूबवर") || clean.contains("vlc वर") || clean.contains("vlc मध्ये") ||
            clean.contains("स्पॉटिफायवर") || clean.contains("स्थानिक गाणे") ||
            (clean.contains("youtube") && (clean.contains("play") || clean.contains("गाणे") || clean.contains("सॉन्ग") || clean.contains("लाव"))) ||
            (clean.contains("vlc") && (clean.contains("play") || clean.contains("गाणे") || clean.contains("सॉन्ग") || clean.contains("वाजवा"))) ||
            (clean.contains("spotify") && (clean.contains("play") || clean.contains("गाणे") || clean.contains("सॉन्ग") || clean.contains("लाव"))) ||
            (clean.startsWith("open ") && (clean.contains(" and play ") || clean.contains(" play "))) ||
            (clean.startsWith("play ") && !clean.contains("game") && !clean.contains("football") && !clean.contains("cricket"))

        if (isMusicCommand) {
            val detectedPlayer = when {
                clean.contains("vlc") || clean.contains("व्हीएलसी") -> "vlc"
                clean.contains("youtube") || clean.contains("यूट्यूब") -> "youtube"
                clean.contains("spotify") || clean.contains("स्पॉटिफाय") -> "spotify"
                clean.contains("local") || clean.contains("स्थानिक") || clean.contains("offline") -> "local"
                else -> null
            }

            val songQuery = clean
                .replace("open youtube and play", "")
                .replace("open youtube play", "")
                .replace("open vlc and play", "")
                .replace("open vlc play", "")
                .replace("play song on youtube", "")
                .replace("play song in youtube", "")
                .replace("play song on vlc", "")
                .replace("play song in vlc", "")
                .replace("play song on spotify", "")
                .replace("play song in spotify", "")
                .replace("play local song", "")
                .replace("play local track", "")
                .replace("play local", "")
                .replace("play song", "")
                .replace("play songs", "")
                .replace("play music", "")
                .replace("play track", "")
                .replace("on youtube", "")
                .replace("in youtube", "")
                .replace("on vlc", "")
                .replace("in vlc", "")
                .replace("on spotify", "")
                .replace("in spotify", "")
                .replace("and play", "")
                .replace("play", "")
                .replace("open", "")
                .replace("youtube", "")
                .replace("vlc", "")
                .replace("spotify", "")
                .replace("यूट्यूब वर", "")
                .replace("यूट्यूबवर", "")
                .replace("यूट्यूब उघडून", "")
                .replace("यूट्यूब", "")
                .replace("vlc वर", "")
                .replace("vlc मध्ये", "")
                .replace("स्पॉटिफायवर", "")
                .replace("स्पॉटिफाय", "")
                .replace("स्थानिक गाणे लावा", "")
                .replace("स्थानिक गाणे", "")
                .replace("गाणे लावा", "")
                .replace("गाणी वाजवा", "")
                .replace("गाणी लावा", "")
                .replace("संगीत चालू करा", "")
                .replace("सॉन्ग लावा", "")
                .replace("सॉन्ग वाजवा", "")
                .replace("प्ले करा", "")
                .replace("सुरू करा", "")
                .replace("गाणे", "")
                .replace("सॉन्ग", "")
                .replace("लाव", "")
                .replace("वाजवा", "")
                .trim()

            val (success, desc) = hardware.playSongOrMusic(songQuery, detectedPlayer)
            val msg = if (isMarathi) {
                if (success) {
                    when (detectedPlayer) {
                        "youtube" -> if (songQuery.isNotBlank()) "यूट्यूबवर \"$songQuery\" शोधून प्ले करत आहे, सर." else "यूट्यूब म्युझिक सुरू करत आहे, सर."
                        "vlc" -> if (songQuery.isNotBlank()) "VLC मध्ये \"$songQuery\" गाणे सुरू करत आहे, सर." else "VLC प्लेअर सुरू करत आहे, सर."
                        "spotify" -> if (songQuery.isNotBlank()) "स्पॉटिफायवर \"$songQuery\" सुरू करत आहे, सर." else "स्पॉटिफाय सुरू करत आहे, सर."
                        "local" -> if (songQuery.isNotBlank()) "डिव्हाइसवरील \"$songQuery\" गाणे सुरू करत आहे, सर." else "स्थानिक प्लेअर सुरू करत आहे, सर."
                        else -> if (songQuery.isNotBlank()) "\"$songQuery\" गाणे सुरू करत आहे, सर." else "संगीत सुरू करत आहे, सर."
                    }
                } else "कोणतेही म्युझिक किंवा मीडिया ॲप सापडले नाही."
            } else {
                if (success) {
                    when (detectedPlayer) {
                        "youtube" -> if (songQuery.isNotBlank()) "Searching and playing \"$songQuery\" on YouTube app (WakeLock active), sir." else "Opening YouTube app, sir."
                        "vlc" -> if (songQuery.isNotBlank()) "Playing \"$songQuery\" in VLC player, sir." else "Opening VLC player, sir."
                        "spotify" -> if (songQuery.isNotBlank()) "Launching \"$songQuery\" in Spotify, sir." else "Opening Spotify, sir."
                        "local" -> if (songQuery.isNotBlank()) "Playing local track \"$songQuery\" (WakeLock active), sir." else "Playing local audio storage, sir."
                        else -> if (songQuery.isNotBlank()) "Auto-playing \"$songQuery\" for you, sir." else "$desc, sir."
                    }
                } else "Could not launch music player."
            }
            return IntentResult(success, msg, "PLAY_MUSIC")
        }

        // 5. APP LAUNCHING (English & Marathi)
        val openAppRegex = Regex("""(?:open|launch|start|उघडा|चालू करा)\s+(.+)""")
        val trailingOpenRegex = Regex("""(.+)\s+(?:open|launch|start|उघडा|चालू करा)""")
        val appMatch = openAppRegex.find(clean)
        val trailingMatch = trailingOpenRegex.find(clean)

        val targetApp = when {
            appMatch != null -> appMatch.groupValues[1].trim()
            trailingMatch != null -> trailingMatch.groupValues[1].trim()
            clean.contains("कॅमेरा") -> "camera"
            clean.contains("यूट्यूब") -> "youtube"
            clean.contains("व्हाट्सअ‍ॅप") || clean.contains("व्हॉट्सॲप") -> "whatsapp"
            clean.contains("स्पॉटिफाय") -> "spotify"
            clean.contains("इन्स्टाग्राम") -> "instagram"
            clean.contains("टेलिग्राम") -> "telegram"
            clean.contains("कॅल्क्युलेटर") -> "calculator"
            clean.contains("क्रोम") -> "chrome"
            clean.contains("गॅलरी") -> "gallery"
            else -> null
        }

        if (targetApp != null && targetApp.length > 1) {
            val (launched, actualName) = hardware.launchAppByName(targetApp)
            if (launched) {
                val msg = if (isMarathi) "$actualName ॲप उघडत आहे, सर." else "Opening $actualName now, sir."
                return IntentResult(true, msg, "LAUNCH_APP")
            } else if (appMatch != null || trailingMatch != null) {
                val msg = if (isMarathi) "'$targetApp' नावाचे ॲप डिव्हाइसवर सापडले नाही, सर." else "Could not find an installed application matching '$targetApp'."
                return IntentResult(false, msg, "LAUNCH_APP_FAILED")
            }
        }

        // 6. PHONE CALLS & CONTACT DIALING (English & Marathi)
        if (clean.startsWith("call") || clean.startsWith("dial") || clean.startsWith("make a call to") ||
            clean.contains("फोन लावा") || clean.contains("कॉल करा") || clean.contains("कॉल लावा") || clean.contains("फोन करा")
        ) {
            // Check if number or name
            val dialDigits = clean.filter { it.isDigit() || it == '+' }
            if (dialDigits.length >= 3) {
                if (telephonyManager != null) {
                    val (success, msg) = telephonyManager.makeCall(dialDigits)
                    val reply = if (isMarathi) "$dialDigits वर कॉल लावला आहे." else msg
                    return IntentResult(success, reply, "MAKE_CALL")
                } else {
                    val success = hardware.launchDialer(dialDigits)
                    val msg = if (isMarathi) "$dialDigits वर कॉल करण्यासाठी डायलर उघडत आहे." else "Opening dialer for $dialDigits."
                    return IntentResult(success, msg, "DIAL_PHONE")
                }
            } else {
                // Extract contact name
                val nameQuery = clean
                    .replace("make a call to", "")
                    .replace("call to", "")
                    .replace("call", "")
                    .replace("dial", "")
                    .replace("फोन लावा", "")
                    .replace("कॉल करा", "")
                    .replace("कॉल लावा", "")
                    .replace("फोन करा", "")
                    .replace("ला", "")
                    .trim()

                if (nameQuery.isNotBlank()) {
                    if (telephonyManager != null) {
                        val (success, msg) = telephonyManager.callContactByName(nameQuery)
                        val reply = if (isMarathi) {
                            if (success) "$nameQuery ला कॉल लावत आहे." else "$nameQuery चा नंबर संपर्क सूचीमध्ये सापडला नाही."
                        } else msg
                        return IntentResult(success, reply, "CALL_CONTACT")
                    } else {
                        val success = hardware.launchDialer()
                        return IntentResult(success, "Opening dialer for $nameQuery.", "DIAL_PHONE")
                    }
                }
            }
        }

        // 7. SMS / TEXT MESSAGING (English & Marathi)
        if (clean.startsWith("send sms") || clean.startsWith("send message") || clean.startsWith("text") ||
            clean.contains("मेसेज पाठवा") || clean.contains("एसएमएस पाठवा") || clean.contains("sms करा")
        ) {
            // Parse target and message body
            // Examples: "send sms to 9876543210 saying I am on my way", "text Mom I will be home soon", "राहुल ला मेसेज पाठवा मी पोहोचलो"
            val bodySeparators = listOf("saying", "that", "message", "संदेश", "म्हणून")
            var target = ""
            var messageBody = "Hello from Jarvis"

            if (isMarathi) {
                val mrRegex = Regex("""(?:(.+?)\s+ला\s+(?:मेसेज|एसएमएस|sms)\s+(?:पाठवा|करा)\s*(.*))""")
                val mrMatch = mrRegex.find(clean)
                if (mrMatch != null) {
                    target = mrMatch.groupValues[1].trim()
                    val body = mrMatch.groupValues[2].trim()
                    if (body.isNotBlank()) messageBody = body
                }
            } else {
                val enRegex = Regex("""(?:send\s+(?:sms|message)\s+to|text)\s+([a-zA-Z0-9\+\s]+?)(?:\s+(?:saying|that|with text)\s+(.*))?$""")
                val enMatch = enRegex.find(clean)
                if (enMatch != null) {
                    target = enMatch.groupValues[1].trim()
                    val body = enMatch.groupValues[2]?.trim()
                    if (!body.isNullOrBlank()) messageBody = body
                }
            }

            if (target.isNotBlank()) {
                val targetDigits = target.filter { it.isDigit() || it == '+' }
                if (targetDigits.length >= 3 && telephonyManager != null) {
                    val (success, msg) = telephonyManager.sendSms(targetDigits, messageBody)
                    val reply = if (isMarathi) "$targetDigits ला एसएमएस पाठवला: \"$messageBody\"" else msg
                    return IntentResult(success, reply, "SEND_SMS")
                } else if (telephonyManager != null) {
                    val (success, msg) = telephonyManager.sendSmsToContact(target, messageBody)
                    val reply = if (isMarathi) {
                        if (success) "$target ला एसएमएस पाठवला: \"$messageBody\"" else "$target चा नंबर सापडला नाही."
                    } else msg
                    return IntentResult(success, reply, "SEND_SMS_CONTACT")
                }
            } else if (telephonyManager != null) {
                telephonyManager.openSmsComposer()
                val reply = if (isMarathi) "एसएमएस ॲप उघडत आहे." else "Opening SMS application."
                return IntentResult(true, reply, "OPEN_SMS")
            }
        }

        // 7.5 SCHEDULED AUTO TEXT / TASK CREATION (Offline & Online Voice Commands)
        // 7. SCHEDULED TASKS & AUTO TEXT / GEMINI SCHEDULING (English & Marathi)
        if (clean.contains("schedule text") || clean.contains("schedule sms") ||
            clean.contains("schedule message") || clean.contains("schedule task") ||
            clean.contains("schedule gemini") || clean.contains("schedule prompt") ||
            clean.contains("gemini hourly") || clean.contains("gemini schedule") ||
            clean.contains("auto text") || clean.contains("auto send text") ||
            clean.contains("मेसेज शेड्युल") || clean.contains("टेक्स्ट शेड्युल") || clean.contains("जेमिनी शेड्युल") ||
            (clean.contains("मेसेज पाठवा") && (clean.contains("वाजता") || clean.contains("सकाळी") || clean.contains("संध्याकाळी") || clean.contains("दर")))
        ) {
            val taskMgr = scheduledTaskManager ?: (context?.applicationContext as? com.example.JarvisApplication)?.scheduledTaskManager
            if (taskMgr != null) {
                val isGeminiTask = clean.contains("gemini") || clean.contains("जेमिनी") || clean.contains("prompt") || clean.contains("ai")
                val isHourly = clean.contains("hourly") || clean.contains("every hour") || clean.contains("दर तासाला")
                val isMinutely = clean.contains("every") && (clean.contains("minute") || clean.contains("min") || clean.contains("मिनिट"))
                val isDaily = clean.contains("daily") || clean.contains("every day") || clean.contains("दररोज")
                val useIndiaTime = clean.contains("india") || clean.contains("ist") || clean.contains("भारत")

                var intervalMins = 0
                val minRegex = Regex("""(?:every|दर)\s*(\d{1,2})\s*(?:minute|minutes|min|मिनिटे|मिनिटांनी)""")
                val minMatch = minRegex.find(clean)
                if (minMatch != null) {
                    intervalMins = minMatch.groupValues[1].toIntOrNull() ?: 15
                }

                val repeatFreq = when {
                    isHourly -> "HOURLY"
                    isMinutely || intervalMins > 0 -> "EVERY_X_MINUTES"
                    isDaily -> "DAILY"
                    else -> "ONCE"
                }

                var hour = -1
                var minute = 0
                val isPm = clean.contains("pm") || clean.contains("संध्याकाळी") || clean.contains("रात्री") || clean.contains("दुपारी")
                val isAm = clean.contains("am") || clean.contains("सकाळी") || clean.contains("पहाटे")

                val timeColonRegex = Regex("""(\d{1,2}):(\d{2})""")
                val colonMatch = timeColonRegex.find(clean)
                if (colonMatch != null) {
                    hour = colonMatch.groupValues[1].toIntOrNull() ?: -1
                    minute = colonMatch.groupValues[2].toIntOrNull() ?: 0
                } else {
                    val hourRegex = Regex("""(\d{1,2})\s*(?:o'?clock|am|pm|वाजता|वाजताचे)""")
                    val hourMatch = hourRegex.find(clean)
                    if (hourMatch != null) {
                        hour = hourMatch.groupValues[1].toIntOrNull() ?: -1
                    }
                }

                if (hour != -1) {
                    if (isPm && hour in 1..11) hour += 12
                    else if (isAm && hour == 12) hour = 0
                } else {
                    // Default to next interval or current hour
                    val cal = java.util.Calendar.getInstance()
                    hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                    minute = (cal.get(java.util.Calendar.MINUTE) + 5) % 60
                }

                val numRegex = Regex("""(?:to|number|नंबर)\s*(\+?\d{8,15})""")
                val numMatch = numRegex.find(clean) ?: Regex("""(\+?\d{10,12})""").find(clean)
                val phoneNumber = numMatch?.groupValues?.get(1) ?: if (isGeminiTask) "" else "9876543210"

                val msgRegex = Regex("""(?:saying|text|message|body|prompt|मजकूर)\s*['"]?([^'"]+)['"]?""")
                val msgMatch = msgRegex.find(clean)
                val taskText = msgMatch?.groupValues?.get(1) ?: if (isGeminiTask) "Give me a motivational quote and tech briefing" else "Jarvis Scheduled Auto Text: Greetings!"

                val taskType = if (isGeminiTask) {
                    if (phoneNumber.isNotBlank() && (clean.contains("send to") || clean.contains("sms"))) "GEMINI_TO_SMS" else "GEMINI_QUERY"
                } else {
                    "AUTO_SMS"
                }

                val (success, replyMsg) = taskMgr.createAndScheduleTask(
                    title = if (isGeminiTask) "Scheduled Gemini Prompt" else "Scheduled Auto Text",
                    taskType = taskType,
                    targetPhoneNumber = phoneNumber,
                    messageText = taskText,
                    hour = hour,
                    minute = minute,
                    intervalMinutes = intervalMins,
                    timezoneId = if (useIndiaTime) "Asia/Kolkata" else "LOCAL",
                    repeatFrequency = repeatFreq
                )

                val spoken = if (isMarathi) {
                    "शेड्युल टास्क सक्रिय केला आहे: $repeatFreq ($replyMsg)."
                } else {
                    "Scheduled task successfully configured: $replyMsg"
                }

                return IntentResult(success, spoken, "SCHEDULE_AUTO_TASK")
            }
        }

        // 8. ALARM MANAGEMENT (English & Marathi)
        if (clean.contains("alarm") || clean.contains("अलार्म") || clean.contains("wake me up")) {
            if (clean.contains("show") || clean.contains("list") || clean.contains("दाखवा") || clean.contains("पहा")) {
                if (telephonyManager != null) {
                    val success = telephonyManager.showAlarms()
                    val reply = if (isMarathi) "अलार्म सूची उघडत आहे." else "Displaying configured alarms."
                    return IntentResult(success, reply, "SHOW_ALARMS")
                }
            }

            // Extract time: e.g. "set alarm for 7:30 am", "set alarm for 6 am", "अलार्म लावा 7 वाजता"
            var hour = -1
            var minute = 0
            val isPm = clean.contains("pm") || clean.contains("संध्याकाळी") || clean.contains("रात्री") || clean.contains("दुपारी")
            val isAm = clean.contains("am") || clean.contains("सकाळी") || clean.contains("पहाटे")

            val timeColonRegex = Regex("""(\d{1,2}):(\d{2})""")
            val colonMatch = timeColonRegex.find(clean)
            if (colonMatch != null) {
                hour = colonMatch.groupValues[1].toIntOrNull() ?: -1
                minute = colonMatch.groupValues[2].toIntOrNull() ?: 0
            } else {
                val hourOnlyRegex = Regex("""(?:for|at|वाजता|ला)?\s*(\d{1,2})\s*(?:o'?clock|am|pm|वाजता)?""")
                val hourMatch = hourOnlyRegex.find(clean)
                if (hourMatch != null) {
                    hour = hourMatch.groupValues[1].toIntOrNull() ?: -1
                }
            }

            if (hour != -1) {
                if (isPm && hour in 1..11) hour += 12
                else if (isAm && hour == 12) hour = 0

                if (telephonyManager != null) {
                    val (success, msg) = telephonyManager.setAlarm(hour, minute, "Jarvis Alarm", skipUi = true)
                    val timeFmt = String.format("%02d:%02d", hour, minute)
                    val reply = if (isMarathi) "सकाळी/वेळेसाठी $timeFmt चा अलार्म सेट केला आहे." else msg
                    return IntentResult(success, reply, "SET_ALARM")
                }
            }
        }

        // 9. TIMER MANAGEMENT (English & Marathi)
        if (clean.contains("timer") || clean.contains("टायमर")) {
            if (clean.contains("show") || clean.contains("दाखवा")) {
                if (telephonyManager != null) {
                    telephonyManager.showTimers()
                    val reply = if (isMarathi) "टायमर उघडत आहे." else "Displaying timers."
                    return IntentResult(true, reply, "SHOW_TIMERS")
                }
            }

            // Extract duration: "set timer for 5 minutes", "20 seconds timer", "5 मिनिटे टायमर"
            val minMatch = Regex("""(\d+)\s*(?:min|minute|minutes|मिनिटे|मिनिटांचा)""").find(clean)
            val secMatch = Regex("""(\d+)\s*(?:sec|second|seconds|सेकंद)""").find(clean)

            val minutes = minMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val seconds = secMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val totalSeconds = (minutes * 60) + seconds

            if (totalSeconds > 0 && telephonyManager != null) {
                val (success, msg) = telephonyManager.setTimer(totalSeconds, "Jarvis Timer", skipUi = true)
                val reply = if (isMarathi) "$minutes मिनिटे $seconds सेकंदांचा टायमर लावला आहे." else msg
                return IntentResult(success, reply, "SET_TIMER")
            }
        }

        // 10. CONTACTS SEARCH (English & Marathi)
        if (clean.startsWith("find contact") || clean.startsWith("search contact") || clean.contains("कॉन्टॅक्ट शोधा") || clean.contains("नंबर शोधा")) {
            val q = clean
                .replace("find contact", "")
                .replace("search contact", "")
                .replace("कॉन्टॅक्ट शोधा", "")
                .replace("नंबर शोधा", "")
                .replace("चा", "")
                .trim()
            if (q.isNotBlank() && telephonyManager != null) {
                val results = telephonyManager.searchContacts(q)
                if (results.isNotEmpty()) {
                    val first = results.first()
                    val reply = if (isMarathi) "${first.name} चा नंबर आहे: ${first.number} (${first.type})"
                    else "Found ${first.name}: ${first.number} (${first.type})."
                    return IntentResult(true, reply, "SEARCH_CONTACT")
                } else {
                    val reply = if (isMarathi) "'$q' नावाचा कोणताही संपर्क सापडला नाही." else "No contacts found matching '$q'."
                    return IntentResult(false, reply, "SEARCH_CONTACT_EMPTY")
                }
            }
        }

        // 11. CALENDAR & SCHEDULE
        if (clean.contains("calendar") || clean.contains("कॅलेंडर") || clean.startsWith("add event") || clean.startsWith("इव्हेंट जोडा")) {
            if (clean.contains("open") || clean.contains("show") || clean.contains("उघडा")) {
                if (telephonyManager != null) {
                    telephonyManager.openCalendar()
                    val reply = if (isMarathi) "कॅलेंडर उघडत आहे." else "Opening calendar."
                    return IntentResult(true, reply, "OPEN_CALENDAR")
                }
            } else if (telephonyManager != null) {
                val title = clean
                    .replace("add event", "")
                    .replace("create event", "")
                    .replace("इव्हेंट जोडा", "")
                    .trim()
                    .ifBlank { "Jarvis Schedule" }
                val (success, msg) = telephonyManager.addCalendarEvent(title)
                return IntentResult(success, msg, "ADD_CALENDAR_EVENT")
            }
        }

        // 7. OFFLINE AUTOMATION RULE QUERIES & BUILDER VIA VOICE
        if (clean == "list rules" || clean == "show rules" || clean == "rules status" || clean == "how many rules" ||
            clean == "automation status" || clean == "माझे नियम" || clean == "नियम दाखव" || clean == "नियम किती आहेत"
        ) {
            val rules = repository.getActiveRulesSync()
            val total = repository.allRules
            val msg = if (isMarathi) "स्थानिक कोरमध्ये ${rules.size} ऑटोमेशन नियम सक्रिय आहेत, सर."
            else "You currently have ${rules.size} active automation rules armed in your local Jarvis core, sir."
            return IntentResult(
                success = true,
                spokenResponse = msg,
                intentAction = "LIST_RULES"
            )
        }

        if (clean.contains("load preset rules") || clean.contains("load default rules") ||
            clean.contains("add rules pack") || clean.contains("लोड प्रिसेट नियम") || clean.contains("सर्व नियम लोड करा")
        ) {
            val app = context as? com.example.JarvisApplication
            if (app != null) {
                val presets = listOf(
                    AutomationRule(name = "Pocket-Guard Shake Flashlight", triggerType = TriggerTypes.SHAKE, triggerThreshold = 26.0f, actionType = ActionTypes.TOGGLE_FLASHLIGHT, isEnabled = true, cooldownSeconds = 3),
                    AutomationRule(name = "Flip Face-Down Meeting Silence", triggerType = TriggerTypes.FLIP_FACE_DOWN, triggerThreshold = 0f, actionType = ActionTypes.MUTE_ALL, isEnabled = true, cooldownSeconds = 5),
                    AutomationRule(name = "Low Battery Voice Alert", triggerType = TriggerTypes.BATTERY_LOW, triggerThreshold = 20.0f, actionType = ActionTypes.SPEAK_TTS, actionParam = "Battery low. Please connect charger.", isEnabled = true, cooldownSeconds = 60),
                    AutomationRule(name = "Full Battery 100% Notice", triggerType = TriggerTypes.BATTERY_FULL, triggerThreshold = 100.0f, actionType = ActionTypes.SPEAK_TTS, actionParam = "Battery fully charged.", isEnabled = true, cooldownSeconds = 120),
                    AutomationRule(name = "Power Connected Confirmation", triggerType = TriggerTypes.CHARGER_CONNECTED, triggerThreshold = 0f, actionType = ActionTypes.SPEAK_TTS, actionParam = "Power source connected.", isEnabled = true, cooldownSeconds = 10),
                    AutomationRule(name = "Power Disconnected Alert", triggerType = TriggerTypes.CHARGER_DISCONNECTED, triggerThreshold = 0f, actionType = ActionTypes.SPEAK_TTS, actionParam = "Power disconnected.", isEnabled = true, cooldownSeconds = 10),
                    AutomationRule(name = "Desk Face-Up Ready Volume", triggerType = TriggerTypes.FLIP_FACE_UP, triggerThreshold = 0f, actionType = ActionTypes.SET_VOLUME, actionParam = "75", isEnabled = true, cooldownSeconds = 10),
                    AutomationRule(name = "Outdoor Sunlight Boost", triggerType = TriggerTypes.LIGHT_ABOVE, triggerThreshold = 1000.0f, actionType = ActionTypes.MAX_VOLUME, isEnabled = false, cooldownSeconds = 30),
                    AutomationRule(name = "Night Bedside Mode", triggerType = TriggerTypes.LIGHT_BELOW, triggerThreshold = 5.0f, actionType = ActionTypes.SET_VOLUME, actionParam = "20", isEnabled = false, cooldownSeconds = 30)
                )
                for (r in presets) {
                    repository.insertRule(r)
                }
                val msg = if (isMarathi) "सर्व प्रिसेट ऑटोमेशन नियम यशस्वीरित्या जोडले आहेत, सर."
                else "Pre-configured automation rules pack has been loaded into your local core, sir."
                return IntentResult(true, msg, "LOAD_PRESET_RULES")
            }
        }

        if (clean.startsWith("create rule") || clean.startsWith("add rule") || clean.startsWith("new rule") ||
            clean.startsWith("नियम बनवा") || clean.startsWith("ऑटोमेशन बनवा")
        ) {
            val parsedRule = parseRuleFromVoice(clean)
            if (parsedRule != null) {
                repository.insertRule(parsedRule)
                val msg = if (isMarathi) "नवीन ऑटोमेशन नियम तयार केला आहे: ${parsedRule.name}." else "Automation workflow created: ${parsedRule.name}. Registered and active in local database."
                return IntentResult(
                    success = true,
                    spokenResponse = msg,
                    intentAction = "CREATE_RULE"
                )
            } else {
                val msg = if (isMarathi) "नियम समजला नाही. 'create rule when shake toggle flashlight' किंवा 'create rule when face down mute' असे सांगा."
                else "I understood your intent to create an automation, but couldn't deduce the trigger and action. Try saying 'Create rule when shake toggle flashlight' or 'Create rule when face down mute phone'."
                return IntentResult(
                    success = false,
                    spokenResponse = msg,
                    intentAction = "CREATE_RULE_FAILED"
                )
            }
        }

        // 8. HAPTIC TEST
        if (clean.contains("vibrate") || clean.contains("haptic test") || clean.contains("व्हायब्रेट करा") || clean.contains("व्हायब्रेशन टेस्ट")) {
            hardware.vibrate(350)
            val msg = if (isMarathi) "व्हायब्रेशन पल्स पाठवले आहे." else "Haptic pulse dispatched."
            return IntentResult(true, msg, "VIBRATE")
        }

        // 8b. CLEAR CONVERSATION / RESET LOCATION MEMORY
        if (clean == "clear conversation" || clean == "reset context" || clean == "clear chat" ||
            clean == "new session" || clean == "reset memory" || clean == "clear memory" ||
            clean == "चॅट क्लिअर करा" || clean == "मेमरी क्लिअर करा"
        ) {
            val msg = if (isMarathi) "संभाषण इतिहास आणि स्थान संदर्भ रीसेट केले आहे, सर." else "Conversation context and location memory reset, sir."
            return IntentResult(true, msg, "CLEAR_CONVERSATION")
        }

        // 8c. GEMINI MODEL & FALLBACK CONTROL VIA VOICE (English & Marathi)
        if (clean.contains("gemini") || clean.contains("जेमिनी") || clean.contains("model") || clean.contains("मॉडेल") || clean.contains("fallback") || clean.contains("फॉलबॅक")) {
            val app = context as? com.example.JarvisApplication
            if (app != null) {
                // Check for Auto Fallback toggle
                if (clean.contains("enable auto fallback") || clean.contains("turn on auto fallback") ||
                    clean.contains("auto cascade") || clean.contains("ऑटो फॉलबॅक चालू करा") || clean == "enable fallback"
                ) {
                    app.geminiAssistantEngine.setAutoFallbackEnabled(true)
                    val msg = if (isMarathi) "ऑटो फॉलबॅक कॅस्केड सक्रिय केले आहे. एरर आल्यास इतर मॉडेल्स आपोआप वापरले जातील."
                    else "Auto Fallback Cascade enabled, sir. The engine will automatically transition through tiers upon encountering errors."
                    return IntentResult(true, msg, "SET_AUTO_FALLBACK_ON")
                }

                if (clean.contains("disable auto fallback") || clean.contains("turn off auto fallback") || clean.contains("ऑटो फॉलबॅक बंद करा")) {
                    app.geminiAssistantEngine.setAutoFallbackEnabled(false)
                    val msg = if (isMarathi) "ऑटो फॉलबॅक बंद केले आहे. फक्त निवडलेले मॉडेल वापरले जाईल."
                    else "Auto Fallback disabled, sir. Strictly using designated model tier."
                    return IntentResult(true, msg, "SET_AUTO_FALLBACK_OFF")
                }

                // Check for explicit cascade selection
                if (clean.contains("cascade lite") || clean.contains("lite cascade") || clean.contains("कॅस्केड लाईट")) {
                    app.geminiAssistantEngine.setSelectedModelTier(GeminiModelTier.CASCADE_LITE)
                    val msg = if (isMarathi) "कॅस्केड लाईट सक्रिय केले आहे. कमाल वेग आणि झिरो 503 त्रुटी."
                    else "Cascade Lite mode engaged, sir. Traversing lightweight models for instantaneous voice answers and zero load errors."
                    return IntentResult(true, msg, "SWITCH_CASCADE_LITE")
                }
                if (clean.contains("cascade flash") || clean.contains("flash cascade") || clean.contains("कॅस्केड फ्लॅश")) {
                    app.geminiAssistantEngine.setSelectedModelTier(GeminiModelTier.CASCADE_FLASH)
                    val msg = if (isMarathi) "कॅस्केड फ्लॅश सक्रिय केले आहे. फ्लॅगशिप रिझनिंग कोर कार्यरत आहे."
                    else "Cascade Flash mode engaged, sir. Traversing flagship Gemini 3.8 & 3.7 Flash models with automated quota fallback."
                    return IntentResult(true, msg, "SWITCH_CASCADE_FLASH")
                }

                // Check for explicit model selection
                if (clean.contains("3.8") || clean.contains("३.८")) {
                    app.geminiAssistantEngine.setSelectedModelTier(GeminiModelTier.GEMINI_3_8_FLASH)
                    val msg = if (isMarathi) "जेमिनी ३.८ फ्लॅश मॉडेल सक्रिय केले आहे." else "Gemini 3.8 Flash model engaged, sir."
                    return IntentResult(true, msg, "SWITCH_MODEL_3_8")
                }
                if (clean.contains("3.7") || clean.contains("३.७")) {
                    app.geminiAssistantEngine.setSelectedModelTier(GeminiModelTier.GEMINI_3_7_FLASH)
                    val msg = if (isMarathi) "जेमिनी ३.७ फ्लॅश मॉडेल सक्रिय केले आहे." else "Gemini 3.7 Flash model engaged, sir."
                    return IntentResult(true, msg, "SWITCH_MODEL_3_7")
                }
                if (clean.contains("3.6") || clean.contains("३.६")) {
                    app.geminiAssistantEngine.setSelectedModelTier(GeminiModelTier.GEMINI_3_6_FLASH)
                    val msg = if (isMarathi) "जेमिनी ३.६ फ्लॅश मॉडेल सक्रिय केले आहे." else "Gemini 3.6 Flash model engaged, sir."
                    return IntentResult(true, msg, "SWITCH_MODEL_3_6")
                }
                if (clean.contains("3.5 lite") || clean.contains("3.5 flash lite") || clean.contains("३.५ लाईट")) {
                    app.geminiAssistantEngine.setSelectedModelTier(GeminiModelTier.GEMINI_3_5_FLASH_LITE)
                    val msg = if (isMarathi) "जेमिनी ३.५ फ्लॅश लाईट मॉडेल सक्रिय केले आहे." else "Gemini 3.5 Flash Lite engaged, sir."
                    return IntentResult(true, msg, "SWITCH_MODEL_3_5_LITE")
                }
                if (clean.contains("3.1") || clean.contains("३.१")) {
                    app.geminiAssistantEngine.setSelectedModelTier(GeminiModelTier.GEMINI_3_1_FLASH_LITE)
                    val msg = if (isMarathi) "जेमिनी ३.१ फ्लॅश लाईट मॉडेल सक्रिय केले आहे." else "Gemini 3.1 Flash Lite engaged, sir. High quota limits."
                    return IntentResult(true, msg, "SWITCH_MODEL_3_1_LITE")
                }
                if (clean.contains("lite latest") || clean.contains("flash lite latest")) {
                    app.geminiAssistantEngine.setSelectedModelTier(GeminiModelTier.GEMINI_FLASH_LITE_LATEST)
                    val msg = if (isMarathi) "जेमिनी फ्लॅश-लाईट लेटेस्ट मॉडेल सक्रिय केले आहे." else "Gemini Flash-Lite Latest model engaged, sir."
                    return IntentResult(true, msg, "SWITCH_MODEL_FLASH_LITE_LATEST")
                }
                if (clean.contains("latest") || clean.contains("flash latest") || clean.contains("लेटेस्ट")) {
                    app.geminiAssistantEngine.setSelectedModelTier(GeminiModelTier.GEMINI_FLASH_LATEST)
                    val msg = if (isMarathi) "जेमिनी फ्लॅश लेटेस्ट मॉडेल सक्रिय केले आहे." else "Gemini Flash Latest model engaged, sir."
                    return IntentResult(true, msg, "SWITCH_MODEL_FLASH_LATEST")
                }
                if (clean.contains("3.5") || clean.contains("3 5") || clean.contains("३.५") || clean.contains("flash") || clean.contains("फ्लॅश")) {
                    app.geminiAssistantEngine.setSelectedModelTier(GeminiModelTier.GEMINI_3_5_FLASH)
                    val msg = if (isMarathi) "जेमिनी ३.५ फ्लॅश मॉडेल सक्रिय केले आहे." else "Gemini 3.5 Flash model engaged, sir. Balanced knowledge core active."
                    return IntentResult(true, msg, "SWITCH_MODEL_3_5")
                }
                if (clean.contains("auto") || clean.contains("cascade") || clean.contains("कॅस्केड")) {
                    app.geminiAssistantEngine.setSelectedModelTier(GeminiModelTier.CASCADE_LITE)
                    val msg = if (isMarathi) "कॅस्केड मोड सक्रिय केले आहे. रेट लिमिट्स आपोआप हाताळले जातील."
                    else "Cascade mode engaged, sir. Traversing high-rate models dynamically without rate limit failures."
                    return IntentResult(true, msg, "SWITCH_MODEL_AUTO")
                }
            }
        }

        // 9. OFFLINE KNOWLEDGE, CALCULATIONS, UNIT CONVERSIONS & GENERAL FACTS (English & Marathi)
        val offlineKnowledge = OfflineKnowledgeEngine.answerQuery(command, context)
        if (offlineKnowledge.handled) {
            return IntentResult(
                success = true,
                spokenResponse = offlineKnowledge.answer,
                intentAction = "OFFLINE_KNOWLEDGE",
                canFallbackToGemini = false
            )
        }

        // Fallback for general questions or complex reasoning to Gemini if configured
        return IntentResult(
            success = false,
            spokenResponse = "",
            intentAction = "UNKNOWN",
            canFallbackToGemini = true
        )
    }

    private fun parseRuleFromVoice(text: String): AutomationRule? {
        val triggerType: String
        val actionType: String
        val name: String
        val threshold: Float
        var param = ""

        when {
            text.contains("shake") -> {
                triggerType = TriggerTypes.SHAKE
                name = "Shake Trigger Rule"
                threshold = 26.0f
            }
            text.contains("face down") || text.contains("flip down") || text.contains("पालथा") -> {
                triggerType = TriggerTypes.FLIP_FACE_DOWN
                name = "Face Down Privacy"
                threshold = 0f
            }
            text.contains("face up") || text.contains("flip up") || text.contains("उताणा") -> {
                triggerType = TriggerTypes.FLIP_FACE_UP
                name = "Face Up Desk Ready"
                threshold = 0f
            }
            text.contains("upright") || text.contains("picked up") || text.contains("उभा") -> {
                triggerType = TriggerTypes.ORIENTATION_UPRIGHT
                name = "Phone Picked Up Rule"
                threshold = 0f
            }
            text.contains("proximity") || text.contains("pocket") || text.contains("खिशात") -> {
                triggerType = TriggerTypes.PROXIMITY_FAR
                name = "Proximity Uncovered Rule"
                threshold = 0f
            }
            text.contains("dark") || text.contains("low light") || text.contains("अंधार") -> {
                triggerType = TriggerTypes.LIGHT_BELOW
                name = "Low Light Trigger"
                threshold = 5f
            }
            text.contains("bright") || text.contains("sunlight") || text.contains("उजेड") -> {
                triggerType = TriggerTypes.LIGHT_ABOVE
                name = "High Light Trigger"
                threshold = 1000f
            }
            text.contains("charger connected") || text.contains("charger plugged") || text.contains("चार्जिंग चालू") || text.contains("plugged") -> {
                triggerType = TriggerTypes.CHARGER_CONNECTED
                name = "Charger Plugged Rule"
                threshold = 0f
            }
            text.contains("charger disconnected") || text.contains("charger removed") || text.contains("unplugged") -> {
                triggerType = TriggerTypes.CHARGER_DISCONNECTED
                name = "Charger Unplugged Rule"
                threshold = 0f
            }
            text.contains("battery full") || text.contains("100%") || text.contains("शंभर टक्के") -> {
                triggerType = TriggerTypes.BATTERY_FULL
                name = "Battery Full Notice"
                threshold = 100f
            }
            text.contains("battery low") || text.contains("low battery") || text.contains("बॅटरी कमी") -> {
                triggerType = TriggerTypes.BATTERY_LOW
                name = "Battery Low Safeguard"
                threshold = 20f
            }
            else -> return null
        }

        when {
            text.contains("toggle flash") || text.contains("toggle torch") -> {
                actionType = ActionTypes.TOGGLE_FLASHLIGHT
            }
            text.contains("flash on") || text.contains("torch on") || text.contains("turn on flash") || text.contains("turn on torch") -> {
                actionType = ActionTypes.FLASHLIGHT_ON
            }
            text.contains("flash off") || text.contains("torch off") || text.contains("turn off flash") || text.contains("turn off torch") -> {
                actionType = ActionTypes.FLASHLIGHT_OFF
            }
            text.contains("strobe") || text.contains("sos") -> {
                actionType = ActionTypes.FLASHLIGHT_SOS
            }
            text.contains("mute") || text.contains("silent") || text.contains("सायलेंट") -> {
                actionType = ActionTypes.MUTE_ALL
            }
            text.contains("vibrate") || text.contains("व्हायब्रेट") -> {
                actionType = ActionTypes.VIBRATE
            }
            text.contains("max volume") || text.contains("फुल आवाज") -> {
                actionType = ActionTypes.MAX_VOLUME
            }
            text.contains("camera") || text.contains("कॅमेरा") -> {
                actionType = ActionTypes.LAUNCH_CAMERA
            }
            text.contains("time") || text.contains("वेळ") -> {
                actionType = ActionTypes.ANNOUNCE_TIME
            }
            text.contains("battery percent") || text.contains("बॅटरी सांगा") -> {
                actionType = ActionTypes.ANNOUNCE_BATTERY
            }
            text.contains("speak") || text.contains("say") || text.contains("सांगा") -> {
                actionType = ActionTypes.SPEAK_TTS
                val textAfter = text.substringAfter("speak", "").substringAfter("say", "").substringAfter("सांगा", "").trim()
                param = textAfter.ifBlank { "Automation trigger activated, sir." }
            }
            else -> {
                actionType = ActionTypes.TOGGLE_FLASHLIGHT
            }
        }

        return AutomationRule(
            name = name,
            triggerType = triggerType,
            actionType = actionType,
            triggerThreshold = threshold,
            actionParam = param,
            isEnabled = true
        )
    }
}
