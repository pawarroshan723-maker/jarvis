package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.JarvisApplication
import com.example.MainActivity
import com.example.R
import com.example.data.ActionTypes
import com.example.data.AutomationRule
import com.example.data.ExecutionLog
import com.example.data.TriggerTypes
import com.example.sensor.DeviceOrientation
import com.example.sensor.SensorTriggerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class JarvisAutomationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val CHANNEL_ID = "jarvis_automation_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START_JARVIS"
        const val ACTION_STOP = "ACTION_STOP_JARVIS"
        const val ACTION_VOICE_TRIGGER = "ACTION_VOICE_TRIGGER"
        const val ACTION_MEDIA_TOGGLE = "ACTION_MEDIA_TOGGLE"
        const val ACTION_MEDIA_NEXT = "ACTION_MEDIA_NEXT"
        const val ACTION_WAKE_SCREEN = "ACTION_WAKE_SCREEN"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, JarvisAutomationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisAutomationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? JarvisApplication
        val action = intent?.action

        when (action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                _isRunning.value = false
                return START_NOT_STICKY
            }
            ACTION_VOICE_TRIGGER -> {
                app?.hardwareController?.acquireCpuWakeLock(12000, "Jarvis:LockscreenVoiceAction")
                app?.hardwareController?.wakeUpScreen(8000)
                app?.speechManager?.startListening(promptForSpeech = true)
                updateNotification("🎤 Listening for lockscreen command...")
                // Also bring MainActivity with lockscreen flags to front
                val voiceIntent = Intent(this, MainActivity::class.java).apply {
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(voiceIntent)
                return START_STICKY
            }
            ACTION_MEDIA_TOGGLE -> {
                app?.hardwareController?.controlMedia(com.example.hardware.MediaControlAction.TOGGLE)
                updateNotification("Media play/pause toggled")
                return START_STICKY
            }
            ACTION_MEDIA_NEXT -> {
                app?.hardwareController?.controlMedia(com.example.hardware.MediaControlAction.NEXT)
                updateNotification("Skipped to next track")
                return START_STICKY
            }
            ACTION_WAKE_SCREEN -> {
                app?.hardwareController?.wakeUpScreen(8000)
                updateNotification("Screen display illuminated")
                return START_STICKY
            }
        }

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notification = buildForegroundNotification("Monitoring Environmental Triggers, Telephony & Voice Core")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val fgsTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    (if (hasMicPermission) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
                startForeground(NOTIFICATION_ID, notification, fgsTypes)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (hasMicPermission) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("JarvisAutomationService", "startForeground failed: ${e.message}", e)
        }
        _isRunning.value = true

        startAutomationEngine()
        return START_STICKY
    }

    private fun startAutomationEngine() {
        val app = application as? JarvisApplication ?: return
        val sensorHub = app.sensorHub
        val repository = app.repository
        val hardware = app.hardwareController
        val speech = app.speechManager
        val autoCallSms = app.autoCallSmsManager

        sensorHub.startListening()

        // If Always-On Mic mode is enabled, ensure microphone session is active
        if (speech.isContinuousModeActive()) {
            speech.startListening()
            Log.i("JarvisService", "Started background continuous speech recognition in automation service")
        }

        val sensorConflict = app.sensorConflictEngine

        serviceScope.launch {
            sensorHub.triggerEvents.collect { event ->
                val activeRules = repository.getActiveRulesSync()
                val now = System.currentTimeMillis()

                for (rule in activeRules) {
                    val (shouldTrigger, reason) = sensorConflict.shouldExecuteRule(rule, event, now)
                    if (shouldTrigger) {
                        executeRule(rule, app, now)
                    } else if (reason != null && !reason.startsWith("Condition not met") && !reason.startsWith("Cooldown active") && !reason.startsWith("Condition already active")) {
                        Log.d("JarvisService", "[ConflictEngine] ${rule.name}: $reason")
                    }
                }
            }
        }
    }

    private fun executeRule(rule: AutomationRule, app: JarvisApplication, timestamp: Long) {
        val hardware = app.hardwareController
        val speech = app.speechManager
        val repository = app.repository
        val sensorConflict = app.sensorConflictEngine

        var resultDetail = ""
        var success = true

        try {
            when (rule.actionType) {
                ActionTypes.TOGGLE_FLASHLIGHT -> {
                    val toggled = hardware.toggleFlashlight()
                    resultDetail = "Flashlight toggled (now ${if (hardware.isFlashlightActive()) "ON" else "OFF"})"
                    success = toggled
                }
                ActionTypes.FLASHLIGHT_ON -> {
                    success = hardware.setFlashlight(true)
                    resultDetail = "Flashlight engaged ON"
                }
                ActionTypes.FLASHLIGHT_OFF -> {
                    success = hardware.setFlashlight(false)
                    resultDetail = "Flashlight turned OFF"
                }
                ActionTypes.FLASHLIGHT_SOS -> {
                    hardware.setFlashlight(true)
                    hardware.vibrate(200)
                    resultDetail = "Flashlight SOS alert triggered"
                    speech.speak("Flashlight alert active, sir.")
                }
                ActionTypes.MUTE_ALL -> {
                    hardware.muteAllAudio()
                    hardware.vibrate(150)
                    resultDetail = "Media & ringtone muted to silent"
                    speech.speak("Privacy silence mode active, sir.")
                }
                ActionTypes.MAX_VOLUME -> {
                    hardware.setMaxVolume()
                    resultDetail = "Volume maximized to 100%"
                }
                ActionTypes.SET_VOLUME -> {
                    val pct = rule.actionParam.toIntOrNull() ?: 60
                    hardware.setMediaVolumePercent(pct)
                    resultDetail = "Volume set to $pct%"
                }
                ActionTypes.SPEAK_TTS -> {
                    val text = rule.actionParam.ifBlank { "Trigger ${rule.name} activated, sir." }
                    speech.speak(text)
                    resultDetail = "Spoke: $text"
                }
                ActionTypes.ANNOUNCE_BATTERY -> {
                    val level = app.sensorHub.telemetry.value.batteryLevel
                    val isCharging = app.sensorHub.telemetry.value.isCharging
                    val chargingStr = if (isCharging) "and currently charging" else "on battery power"
                    val msg = "Battery is at $level percent, $chargingStr, sir."
                    speech.speak(msg)
                    resultDetail = "Announced battery: $level%"
                }
                ActionTypes.ANNOUNCE_TIME -> {
                    val timeStr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                    val msg = "The time is $timeStr, sir."
                    speech.speak(msg)
                    resultDetail = "Announced time: $timeStr"
                }
                ActionTypes.LAUNCH_APP -> {
                    val (launched, name) = hardware.launchAppByName(rule.actionParam)
                    resultDetail = "Launched application $name"
                    success = launched
                }
                ActionTypes.LAUNCH_CAMERA -> {
                    success = hardware.launchCamera()
                    resultDetail = "Launched Camera for quick capture"
                }
                ActionTypes.START_LISTENING -> {
                    hardware.acquireCpuWakeLock(12000, "Jarvis:RuleVoiceTrigger")
                    hardware.wakeUpScreen(8000)
                    speech.startListening(promptForSpeech = true)
                    resultDetail = "Activated voice microphone for command"
                    success = true
                }
                ActionTypes.MEDIA_PLAY_PAUSE -> {
                    val (toggled, msg) = hardware.controlMedia(com.example.hardware.MediaControlAction.TOGGLE)
                    resultDetail = "Media Toggle: $msg"
                    success = toggled
                }
                ActionTypes.MEDIA_NEXT -> {
                    val (nextOk, msg) = hardware.controlMedia(com.example.hardware.MediaControlAction.NEXT)
                    resultDetail = "Next Track: $msg"
                    success = nextOk
                }
                ActionTypes.PLAY_MUSIC -> {
                    val (played, msg) = hardware.playSongOrMusic(rule.actionParam)
                    resultDetail = "Music: $msg"
                    success = played
                }
                ActionTypes.VIBRATE -> {
                    hardware.vibrate(300)
                    resultDetail = "Dispatched haptic vibration pulse"
                }
                else -> {
                    resultDetail = "Unknown action ${rule.actionType}"
                }
            }

            sensorConflict.onRuleExecuted(rule, timestamp)

            serviceScope.launch {
                repository.recordRuleTriggered(rule.id, timestamp)
                repository.insertLog(
                    ExecutionLog(
                        ruleName = rule.name,
                        triggerType = rule.triggerType,
                        details = resultDetail,
                        timestamp = timestamp,
                        isSuccess = success
                    )
                )
            }

            updateNotification("Last action: ${rule.name}")
        } catch (e: Exception) {
            Log.e("JarvisService", "Error executing rule ${rule.name}", e)
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Lockscreen Voice Action
        val voiceIntent = Intent(this, JarvisAutomationService::class.java).apply {
            action = ACTION_VOICE_TRIGGER
        }
        val pendingVoice = PendingIntent.getService(
            this, 10, voiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Media Play/Pause Action
        val mediaIntent = Intent(this, JarvisAutomationService::class.java).apply {
            action = ACTION_MEDIA_TOGGLE
        }
        val pendingMedia = PendingIntent.getService(
            this, 11, mediaIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Media Next Action
        val nextIntent = Intent(this, JarvisAutomationService::class.java).apply {
            action = ACTION_MEDIA_NEXT
        }
        val pendingNext = PendingIntent.getService(
            this, 12, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Action
        val stopIntent = Intent(this, JarvisAutomationService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("J.A.R.V.I.S. Lockscreen Core")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingOpen)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .setVibrate(longArrayOf(0L))
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_btn_speak_now, "🎤 Mic", pendingVoice)
            .addAction(android.R.drawable.ic_media_play, "⏯ Play/Pause", pendingMedia)
            .addAction(android.R.drawable.ic_media_next, "⏭ Next", pendingNext)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(newText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildForegroundNotification(newText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Background Automation Core",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors real-time environmental sensors and voice commands on lockscreen"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        _isRunning.value = false
        val app = application as? JarvisApplication
        app?.sensorHub?.stopListening()
        app?.speechManager?.stopListening()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
