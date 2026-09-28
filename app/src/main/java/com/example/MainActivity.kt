package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.JarvisViewModel
import com.example.ui.components.JarvisBottomNav
import com.example.ui.components.JarvisTopBar
import com.example.ui.dialogs.VoiceDiagnosticsDialog
import com.example.ui.screens.AutomationsScreen
import com.example.ui.screens.ConsoleLogsScreen
import com.example.ui.screens.CoreHudScreen
import com.example.ui.screens.SensorsScreen
import com.example.ui.screens.SystemCommsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianDark

class MainActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels {
        JarvisViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enable lockscreen display and turn screen on for voice/sensor commands
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onAppDestroyed()
    }
}

@Composable
fun MainAppContent(viewModel: JarvisViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsState()

    // Handle back button: return to HUD tab first, or close app completely and release mic
    val activityContext = androidx.compose.ui.platform.LocalContext.current
    BackHandler(enabled = true) {
        if (selectedTab != 0) {
            viewModel.selectTab(0)
        } else {
            viewModel.closeAppAndReleaseMic()
            (activityContext as? android.app.Activity)?.finishAndRemoveTask()
        }
    }
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isOnlineEnabled by viewModel.isOnlineIntelligenceEnabled.collectAsState()
    val isHighThinkingEnabled by viewModel.isHighThinkingEnabled.collectAsState()

    val telemetry by viewModel.telemetry.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val executionLogs by viewModel.recentLogs.collectAsState()
    val commandHistory by viewModel.commandHistory.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    val speechState by viewModel.speechState.collectAsState()
    val audioRms by viewModel.audioRms.collectAsState()
    val partialRecognizedText by viewModel.partialRecognizedText.collectAsState()
    val lastSpokenReply by viewModel.lastSpokenText.collectAsState()
    val lastCommand by viewModel.lastRecognizedCommand.collectAsState()
    val recognitionError by viewModel.recognitionError.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val isFlashOn by viewModel.flashState.collectAsState()
    val currentVolume by viewModel.currentVolume.collectAsState()
    val showAddDialog by viewModel.showAddRuleDialog.collectAsState()
    val preferSystemDialog by viewModel.preferSystemSpeechDialog.collectAsState()
    val showDiagnosticsDialog by viewModel.showDiagnosticsDialog.collectAsState()
    val diagnosticReport by viewModel.diagnosticReport.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val permissionsStatus by viewModel.permissionsStatus.collectAsState()
    val contactsList by viewModel.contactsList.collectAsState()
    val systemStatusMessage by viewModel.systemStatusMessage.collectAsState()
    val micAlwaysOnMode by viewModel.micAlwaysOnMode.collectAsState()
    val isVoiceActive by viewModel.isVoiceActive.collectAsState()
    val humanVoiceConfidence by viewModel.humanVoiceConfidence.collectAsState()
    val backgroundNoiseLevel by viewModel.backgroundNoiseLevel.collectAsState()
    val isNoiseSuppressorActive by viewModel.isNoiseSuppressorActive.collectAsState()
    val noiseCancellationStatus by viewModel.noiseCancellationStatus.collectAsState()
    val autoCallSmsConfig by viewModel.autoCallSmsConfig.collectAsState()
    val callSmsLogs by viewModel.callSmsLogs.collectAsState()
    val autoSmsRules by viewModel.autoSmsRules.collectAsState()
    val scheduledTasks by viewModel.scheduledTasks.collectAsState()
    val activeLocationContext by viewModel.activeLocationContext.collectAsState()
    val activeContextSummary by viewModel.activeContextSummary.collectAsState()
    val selectedModelTier by viewModel.selectedModelTier.collectAsState()
    val isAutoFallbackEnabled by viewModel.isAutoFallbackEnabled.collectAsState()
    val lastExecutionSummary by viewModel.lastExecutionSummary.collectAsState()
    val modelTestResult by viewModel.modelTestResult.collectAsState()
    val isTestingModel by viewModel.isTestingModel.collectAsState()
    val sensorConflictWarnings by viewModel.sensorConflictWarnings.collectAsState()
    val isWakeLockActive by viewModel.isWakeLockActive.collectAsState()
    val isWakeWordLockActive by viewModel.isWakeWordLockActive.collectAsState()
    val currentPlayingTrack by viewModel.currentPlayingTrack.collectAsState()
    val isLocalPlayerActive by viewModel.isLocalPlayerActive.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    // Launcher for requesting all permissions at once
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshPermissions()
        viewModel.loadContacts()
    }

    // Launcher for requesting a single permission
    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshPermissions()
        viewModel.loadContacts()
    }

    // Direct audio permission launcher
    lateinit var triggerVoiceAction: () -> Unit

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.clearRecognitionError()
            android.widget.Toast.makeText(context, "Microphone permission granted!", android.widget.Toast.LENGTH_SHORT).show()
            triggerVoiceAction()
        } else {
            viewModel.setRecognitionError("Microphone permission denied. Grant permission in App Settings to enable voice commands.")
        }
    }

    // System voice recognition dialog launcher as fallback/alternative
    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val recognizedText = matches?.firstOrNull()?.trim()
            if (!recognizedText.isNullOrBlank()) {
                viewModel.clearRecognitionError()
                viewModel.submitVoiceCommand(recognizedText)
            } else {
                viewModel.setRecognitionError("No spoken words recognized. Please speak closer to the microphone and try again.")
            }
        } else if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
            val isListening = speechState == com.example.voice.SpeechState.LISTENING
            if (!isListening) {
                android.util.Log.d("MainActivity", "System voice recognition canceled by user")
            }
        }
    }

    val handleLaunchSystemDialog: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            android.widget.Toast.makeText(context, "Requesting microphone permission...", android.widget.Toast.LENGTH_SHORT).show()
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            try {
                systemSpeechLauncher.launch(viewModel.createSystemSpeechIntent())
            } catch (e: android.content.ActivityNotFoundException) {
                android.util.Log.e("MainActivity", "Google Voice activity not found", e)
                viewModel.setRecognitionError("Google Speech Services not found or disabled on device. Switched to In-App Mic.")
                viewModel.startListening()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to launch system speech dialog", e)
                viewModel.setRecognitionError("Failed to launch voice dialog: ${e.localizedMessage ?: "Unknown error"}")
                viewModel.startListening()
            }
        }
    }

    val handleVoiceTrigger: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // Immediate tactile haptic feedback on tapping to engage
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (ignored: Exception) {}

        if (!hasPermission) {
            android.widget.Toast.makeText(context, "Microphone permission required", android.widget.Toast.LENGTH_SHORT).show()
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.clearRecognitionError()
            if (speechState == com.example.voice.SpeechState.SPEAKING) {
                // Stop speech and listen for next command
                viewModel.stopSpeaking()
                viewModel.startListening(promptForSpeech = true)
                android.widget.Toast.makeText(context, "Listening... Speak your command", android.widget.Toast.LENGTH_SHORT).show()
            } else if (preferSystemDialog || !viewModel.canUseInAppRecognition()) {
                handleLaunchSystemDialog()
            } else {
                // Start or refresh speech listening session with buffer reset & temporary wake-word unlock
                val started = viewModel.startListening(promptForSpeech = true)
                if (started) {
                    android.widget.Toast.makeText(context, "Listening... Speak your command", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    handleLaunchSystemDialog()
                }
            }
        }
    }

    triggerVoiceAction = handleVoiceTrigger

    // Initial permissions launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    LaunchedEffect(Unit) {
        viewModel.fallbackToSystemSpeech.collect {
            if (viewModel.preferSystemSpeechDialog.value) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    try {
                        systemSpeechLauncher.launch(viewModel.createSystemSpeechIntent())
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Failed to launch system speech dialog", e)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianDark),
        topBar = {
            JarvisTopBar(
                isServiceRunning = isServiceRunning,
                isOnlineEnabled = isOnlineEnabled,
                isHighThinkingEnabled = isHighThinkingEnabled,
                onToggleService = { viewModel.toggleService() },
                onToggleOnline = { viewModel.toggleOnlineIntelligence(it) },
                onToggleThinking = { viewModel.toggleHighThinking(it) },
                selectedLanguage = selectedLanguage,
                onCycleLanguage = { viewModel.cycleVoiceLanguage() },
                selectedModelTier = selectedModelTier,
                onCycleModelTier = { viewModel.cycleSelectedModelTier() },
                isAutoFallbackEnabled = isAutoFallbackEnabled
            )
        },
        bottomBar = {
            JarvisBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ObsidianDark)
        ) {
            when (selectedTab) {
                0 -> CoreHudScreen(
                    speechState = speechState,
                    audioRms = audioRms,
                    lastCommand = lastCommand,
                    lastSpokenReply = lastSpokenReply,
                    partialRecognizedText = partialRecognizedText,
                    isProcessing = isProcessing,
                    isFlashlightOn = isFlashOn,
                    currentVolume = currentVolume,
                    recognitionError = recognitionError,
                    onToggleListen = handleVoiceTrigger,
                    onSubmitCommand = { viewModel.submitVoiceCommand(it) },
                    onToggleFlashlight = { viewModel.toggleFlashlight() },
                    onSetVolume = { viewModel.setVolume(it) },
                    onMuteAudio = { viewModel.muteAudio() },
                    onClearRecognitionError = { viewModel.clearRecognitionError() },
                    onLaunchSystemSpeechDialog = handleLaunchSystemDialog,
                    preferSystemDialog = preferSystemDialog,
                    onToggleVoiceMode = { viewModel.toggleVoiceEngineMode() },
                    onOpenDiagnostics = { viewModel.setDiagnosticsDialogVisible(true) },
                    onRequestMicPermission = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    selectedLanguage = selectedLanguage,
                    onCycleLanguage = { viewModel.cycleVoiceLanguage() },
                    micAlwaysOnMode = micAlwaysOnMode,
                    onSetMicAlwaysOnMode = { viewModel.setMicAlwaysOnMode(it) },
                    isWakeWordLockActive = isWakeWordLockActive,
                    onToggleWakeWordLock = { viewModel.toggleWakeWordLock() },
                    isVoiceActive = isVoiceActive,
                    humanVoiceConfidence = humanVoiceConfidence,
                    backgroundNoiseLevel = backgroundNoiseLevel,
                    isNoiseSuppressorActive = isNoiseSuppressorActive,
                    noiseCancellationStatus = noiseCancellationStatus,
                    activeLocationContext = activeLocationContext,
                    activeContextSummary = activeContextSummary,
                    onClearConversationMemory = { viewModel.clearConversationMemory() },
                    selectedModelTier = selectedModelTier,
                    onSelectModelTier = { viewModel.setSelectedModelTier(it) },
                    isAutoFallbackEnabled = isAutoFallbackEnabled,
                    onToggleAutoFallback = { viewModel.setAutoFallbackEnabled(it) },
                    lastExecutionSummary = lastExecutionSummary
                )

                1 -> SystemCommsScreen(
                    permissions = permissionsStatus,
                    onRequestAllPermissions = {
                        multiplePermissionsLauncher.launch(com.example.system.TelephonyAlarmManager.ALL_PERMISSIONS)
                    },
                    onRequestSinglePermission = { perm ->
                        singlePermissionLauncher.launch(perm)
                    },
                    onRefreshPermissions = { viewModel.refreshPermissions() },
                    onMakeCall = { num -> viewModel.makeCall(num) },
                    onSendSms = { num, msg -> viewModel.sendSms(num, msg) },
                    onSetAlarm = { h, m, label -> viewModel.setAlarm(h, m, label) },
                    onSetTimer = { sec, label -> viewModel.setTimer(sec, label) },
                    onShowAlarms = { viewModel.showAlarms() },
                    onShowTimers = { viewModel.showTimers() },
                    onOpenCalendar = { viewModel.openCalendar() },
                    contacts = contactsList,
                    onSearchContacts = { q -> viewModel.loadContacts(q) },
                    systemStatusMessage = systemStatusMessage,
                    onDismissStatusMessage = { viewModel.dismissSystemStatus() },
                    autoCallSmsConfig = autoCallSmsConfig,
                    callSmsLogs = callSmsLogs,
                    autoSmsRules = autoSmsRules,
                    onUpdateAutoCallSmsConfig = { viewModel.updateAutoCallSmsConfig(it) },
                    onSetAutoCallPreset = { viewModel.setAutoCallPreset(it) },
                    onToggleAutoSms = { viewModel.toggleAutoSms(it) },
                    onToggleAutoReadSms = { viewModel.toggleAutoReadSms(it) },
                    onToggleAutoAnnounceCalls = { viewModel.toggleAutoAnnounceCalls(it) },
                    onToggleAutoReplyMissedCalls = { viewModel.toggleAutoReplyMissedCalls(it) },
                    onSetCustomAutoReplyText = { viewModel.setCustomAutoReplyText(it) },
                    onSetEmergencyContact = { viewModel.setEmergencyContact(it) },
                    onTriggerEmergencySos = { viewModel.triggerEmergencySos() },
                    onInsertAutoSmsRule = { viewModel.insertAutoSmsRule(it) },
                    onDeleteAutoSmsRule = { viewModel.deleteAutoSmsRule(it) },
                    onToggleAutoSmsRule = { id, en -> viewModel.toggleAutoSmsRule(id, en) },
                    onSimulateIncomingCall = { num, name, isMissed -> viewModel.simulateIncomingCall(num, name, isMissed) },
                    onSimulateIncomingSms = { num, body -> viewModel.simulateIncomingSms(num, body) },
                    onClearCallSmsLogs = { viewModel.clearCallSmsLogs() },
                    onEnrichContactNames = { viewModel.enrichCallSmsContactNames() },
                    onLoadPreconfiguredSmsRules = { viewModel.loadPreconfiguredSmsRulesPack() },
                    scheduledTasks = scheduledTasks,
                    onCreateScheduledTask = { title, taskType, phone, recipientName, messageText, hour, minute, intervalMinutes, timezoneId, repeatFreq ->
                        viewModel.createScheduledTask(
                            title = title,
                            taskType = taskType,
                            targetPhoneNumber = phone,
                            recipientName = recipientName,
                            messageText = messageText,
                            hour = hour,
                            minute = minute,
                            intervalMinutes = intervalMinutes,
                            timezoneId = timezoneId,
                            repeatFrequency = repeatFreq
                        )
                    },
                    onToggleScheduledTask = { id, enabled -> viewModel.toggleScheduledTask(id, enabled) },
                    onExecuteScheduledTaskNow = { id -> viewModel.executeScheduledTaskNow(id) },
                    onDeleteScheduledTask = { id -> viewModel.deleteScheduledTask(id) },
                    onDeleteAllScheduledTasks = { viewModel.deleteAllScheduledTasks() },
                    onDeleteCompletedScheduledTasks = { viewModel.deleteCompletedScheduledTasks() },
                    onUpdateScheduledTask = { task -> viewModel.updateScheduledTask(task) },
                    onSpeakTaskResult = { text -> viewModel.speakTaskResult(text) }
                )

                2 -> SensorsScreen(
                    telemetry = telemetry,
                    conflictWarnings = sensorConflictWarnings
                )

                3 -> AutomationsScreen(
                    rules = rules,
                    installedApps = installedApps,
                    showAddDialog = showAddDialog,
                    onShowAddDialog = { viewModel.setAddRuleDialogVisible(it) },
                    onToggleRule = { viewModel.toggleRule(it) },
                    onDeleteRule = { viewModel.deleteRule(it) },
                    onTestRule = { viewModel.testTriggerRule(it) },
                    onAddRule = { name, trigger, threshold, action, param ->
                        viewModel.addRule(name, trigger, threshold, action, param)
                    },
                    onPlayMusic = { query, player -> viewModel.playSongOrMusic(query, player) },
                    onLaunchApp = { viewModel.launchApp(it) },
                    onLoadPreconfiguredRules = { viewModel.loadPreconfiguredRulesPack() },
                    onControlMedia = { viewModel.controlMedia(it) },
                    isWakeLockActive = isWakeLockActive,
                    onToggleWakeLock = { viewModel.toggleWakeLock() },
                    onIncreaseVolume = { viewModel.increaseVolume() },
                    onDecreaseVolume = { viewModel.decreaseVolume() },
                    onWakeScreen = { viewModel.wakeScreen() },
                    currentTrackTitle = currentPlayingTrack,
                    isLocalPlaying = isLocalPlayerActive,
                    conflictWarnings = sensorConflictWarnings
                )

                4 -> ConsoleLogsScreen(
                    executionLogs = executionLogs,
                    commandHistory = commandHistory,
                    onClearLogs = { viewModel.clearLogs() },
                    onClearHistory = { viewModel.clearHistory() }
                )
            }

            // Diagnostics and System Health Dialog
            if (showDiagnosticsDialog) {
                VoiceDiagnosticsDialog(
                    report = diagnosticReport,
                    onDismiss = { viewModel.setDiagnosticsDialogVisible(false) },
                    onRerunDiagnostics = { viewModel.runDiagnostics() },
                    onTestTts = {
                        viewModel.speak("Jarvis voice synthesis online. All audio output systems functioning within normal parameters.")
                    },
                    onTestMarathiTts = {
                        viewModel.speak("नमस्कार सर! जार्व्हिस मराठी व्हॉईस सिस्टीम उत्तम रीतीने कार्यरत आहे.")
                    },
                    onRequestMicPermission = {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    currentApiKey = viewModel.getEffectiveApiKey(),
                    onSaveApiKey = { newKey ->
                        viewModel.setCustomApiKey(newKey)
                    },
                    selectedLanguage = selectedLanguage,
                    onSelectLanguage = { viewModel.setVoiceLanguage(it) },
                    micAlwaysOnMode = micAlwaysOnMode,
                    onSelectMicMode = { viewModel.setMicAlwaysOnMode(it) },
                    selectedModelTier = selectedModelTier,
                    onSelectModelTier = { viewModel.setSelectedModelTier(it) },
                    isAutoFallbackEnabled = isAutoFallbackEnabled,
                    onToggleAutoFallback = { viewModel.setAutoFallbackEnabled(it) },
                    lastExecutionSummary = lastExecutionSummary,
                    modelTestResult = modelTestResult,
                    isTestingModel = isTestingModel,
                    onTestModel = { viewModel.testModel(it) }
                )
            }
        }
    }
}
