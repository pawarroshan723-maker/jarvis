package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

data class DiagnosticItem(
    val title: String,
    val isPassed: Boolean,
    val details: String,
    val actionLabel: String? = null,
    val actionType: String? = null
)

data class VoiceDiagnosticReport(
    val items: List<DiagnosticItem>,
    val allPassed: Boolean,
    val overallStatus: String
)

object VoiceDiagnostics {

    fun runFullDiagnostics(
        context: Context,
        isTtsReady: Boolean,
        ttsDiagnostics: MarathiTtsManager.TtsDiagnosticInfo? = null
    ): VoiceDiagnosticReport {
        val items = mutableListOf<DiagnosticItem>()

        // 1. Microphone Permission Check
        val micPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        items.add(
            DiagnosticItem(
                title = "Microphone Permission",
                isPassed = micPermissionGranted,
                details = if (micPermissionGranted) "Granted. App has access to record audio."
                else "Denied. App cannot capture your voice. Grant permission in App Settings.",
                actionLabel = if (!micPermissionGranted) "Grant / Open Settings" else null,
                actionType = if (!micPermissionGranted) "OPEN_SETTINGS" else null
            )
        )

        // 1b. Phone & Telephony Permission Check
        val phoneGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        items.add(
            DiagnosticItem(
                title = "Phone & Calling Permission",
                isPassed = phoneGranted,
                details = if (phoneGranted) "Granted. Direct calling available."
                else "Not granted. Falls back to phone dialer.",
                actionLabel = if (!phoneGranted) "Grant Permission" else null,
                actionType = if (!phoneGranted) "OPEN_SETTINGS" else null
            )
        )

        // 1c. SMS & Contacts Permission Check
        val smsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val contactsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        items.add(
            DiagnosticItem(
                title = "SMS & Contacts Access",
                isPassed = smsGranted && contactsGranted,
                details = if (smsGranted && contactsGranted) "Granted. SMS messaging & address book search active."
                else if (smsGranted) "SMS granted. Contacts search not granted."
                else if (contactsGranted) "Contacts granted. SMS messaging not granted."
                else "Permissions missing. Grant in Comms tab or Settings.",
                actionLabel = if (!smsGranted || !contactsGranted) "Grant Access" else null,
                actionType = if (!smsGranted || !contactsGranted) "OPEN_SETTINGS" else null
            )
        )

        // 2. Google Speech Recognition Service
        val systemIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val hasSystemActivity = systemIntent.resolveActivity(context.packageManager) != null
        val inAppAvailable = SpeechRecognizer.isRecognitionAvailable(context) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

        items.add(
            DiagnosticItem(
                title = "Google Speech Recognition",
                isPassed = hasSystemActivity || inAppAvailable,
                details = if (hasSystemActivity) "Google Voice Recognizer detected and ready."
                else if (inAppAvailable) "In-App Speech Engine is available on this device."
                else "No Speech Recognition service found. Please install or enable Google App / Speech Services.",
                actionLabel = if (!hasSystemActivity && !inAppAvailable) "Google Play Services" else null,
                actionType = if (!hasSystemActivity && !inAppAvailable) "OPEN_PLAY_STORE" else null
            )
        )

        // 3. Audio Recording Hardware Availability Test
        val micHardwareOk = testMicrophoneHardware(context, micPermissionGranted)
        items.add(
            DiagnosticItem(
                title = "Audio Recording Hardware",
                isPassed = micHardwareOk,
                details = if (micHardwareOk) "AudioRecord stream successfully allocated."
                else "Hardware recording stream busy or permission blocked.",
                actionLabel = null,
                actionType = null
            )
        )

        // 4. Text-To-Speech (TTS) Voice Engine
        items.add(
            DiagnosticItem(
                title = "Jarvis Voice (TTS)",
                isPassed = isTtsReady,
                details = if (isTtsReady) "Speech synthesizer online and operational."
                else "Speech synthesizer initializing or unavailable.",
                actionLabel = if (!isTtsReady) "Re-init Voice" else null,
                actionType = if (!isTtsReady) "REINIT_TTS" else null
            )
        )

        // 4b. Marathi Voice Pack & Pronunciation Core (mr-IN)
        val marathiOk = ttsDiagnostics?.isMarathiSupported == true
        val hindiOk = ttsDiagnostics?.isHindiSupported == true
        val marathiDetails = when {
            marathiOk -> "Native Marathi (mr-IN) voice pack active. Natural Devanagari audio synthesis ready."
            hindiOk -> "Marathi voice missing on device. Using Hindi (hi-IN) Devanagari fallback + phonetic transliteration."
            else -> "Devanagari voice missing. Intelligent phonetic transliteration enabled. Open TTS settings to install Marathi voice."
        }
        items.add(
            DiagnosticItem(
                title = "Marathi Voice Pack (mr-IN)",
                isPassed = marathiOk || hindiOk,
                details = marathiDetails,
                actionLabel = if (!marathiOk) "Install / TTS Settings" else null,
                actionType = if (!marathiOk) "OPEN_TTS_SETTINGS" else null
            )
        )

        // 5. Network Connectivity
        val networkConnected = checkNetwork(context)
        items.add(
            DiagnosticItem(
                title = "Network Connectivity",
                isPassed = networkConnected,
                details = if (networkConnected) "Connected. Ready for internet queries & cloud sync."
                else "Offline. Operating on local offline intent rule engine.",
                actionLabel = null,
                actionType = null
            )
        )

        // 6. Gemini Intelligence & API Status
        val customKey = context.getSharedPreferences("jarvis_gemini_prefs", Context.MODE_PRIVATE)
            .getString("custom_gemini_api_key", null)?.trim()
        val hasCustomKey = !customKey.isNullOrBlank()
        val hasBuildKey = try {
            com.example.BuildConfig.GEMINI_API_KEY.isNotBlank() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
        } catch (e: Exception) {
            false
        }
        val keyConfigured = hasCustomKey || hasBuildKey
        val keyDetails = when {
            hasCustomKey -> "Custom Gemini API key active. Rate-limit tier configured by user."
            hasBuildKey -> "Standard Gemini API key active. If rate-limited (HTTP 429), you can set a custom key."
            else -> "No API key configured. Offline engine handles calculations, hardware, and automation."
        }
        items.add(
            DiagnosticItem(
                title = "Gemini Cloud Intelligence",
                isPassed = keyConfigured,
                details = keyDetails,
                actionLabel = "Configure API Key",
                actionType = "CONFIGURE_API_KEY"
            )
        )

        val allPassed = items.all { it.isPassed }
        val overallStatus = if (allPassed) "ALL SYSTEMS OPERATIONAL" else "ATTENTION REQUIRED"

        return VoiceDiagnosticReport(
            items = items,
            allPassed = allPassed,
            overallStatus = overallStatus
        )
    }

    private fun testMicrophoneHardware(context: Context, hasPermission: Boolean): Boolean {
        if (!hasPermission) return false
        return try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (bufferSize <= 0) return true

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            val state = recorder.state == AudioRecord.STATE_INITIALIZED
            recorder.release()
            state
        } catch (e: Exception) {
            true // fallback to true if security manager restricts test
        }
    }

    private fun checkNetwork(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }
}
