package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.JarvisApplication
import com.example.data.ActionTypes
import com.example.data.AutoSmsRule
import com.example.data.AutomationRule
import com.example.data.CallSmsLog
import com.example.data.CommandHistoryItem
import com.example.data.ExecutionLog
import com.example.data.TriggerTypes
import com.example.hardware.HardwareController
import com.example.hardware.InstalledAppInfo
import com.example.sensor.SensorHub
import com.example.sensor.SensorTelemetry
import com.example.service.JarvisAutomationService
import com.example.system.AutoCallSmsConfig
import com.example.voice.JarvisSpeechManager
import com.example.voice.MicAlwaysOnMode
import com.example.voice.SpeechState
import com.example.voice.VoiceDiagnostics
import com.example.voice.VoiceDiagnosticReport
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as JarvisApplication
    private val repository = app.repository
    val hardware: HardwareController = app.hardwareController
    val telephonyManager: com.example.system.TelephonyAlarmManager = app.telephonyAlarmManager
    val sensorHub: SensorHub = app.sensorHub
    val autoCallSmsManager = app.autoCallSmsManager

    // Shared singleton speech manager
    val speechManager: JarvisSpeechManager = app.speechManager

    val micAlwaysOnMode: StateFlow<MicAlwaysOnMode> = speechManager.micAlwaysOnMode
    val isScreenOffArmed: StateFlow<Boolean> = speechManager.isScreenOffArmed

    val autoCallSmsConfig: StateFlow<AutoCallSmsConfig> = autoCallSmsManager.config
    val callSmsLogs: StateFlow<List<CallSmsLog>> = repository.recentCallSmsLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val autoSmsRules: StateFlow<List<AutoSmsRule>> = repository.allAutoSmsRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val scheduledTasks: StateFlow<List<com.example.data.ScheduledTask>> = repository.allScheduledTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _fallbackToSystemSpeech = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val fallbackToSystemSpeech: SharedFlow<Unit> = _fallbackToSystemSpeech.asSharedFlow()

    val rules: StateFlow<List<AutomationRule>> = repository.allRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sensorConflictWarnings: StateFlow<List<com.example.sensor.SensorConflictWarning>> = repository.allRules
        .map { app.sensorConflictEngine.analyzeRuleConflicts(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<ExecutionLog>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val commandHistory: StateFlow<List<CommandHistoryItem>> = repository.commandHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val telemetry: StateFlow<SensorTelemetry> = sensorHub.telemetry
    val isServiceRunning: StateFlow<Boolean> = JarvisAutomationService.isRunning

    val speechState: StateFlow<SpeechState> = speechManager.speechState
    val audioRms: StateFlow<Float> = speechManager.audioRms
    val partialRecognizedText: StateFlow<String> = speechManager.partialText
    val lastSpokenText: StateFlow<String> = speechManager.lastSpokenText
    val recognitionError: StateFlow<String?> = speechManager.recognitionError
    val selectedLanguage: StateFlow<com.example.voice.VoiceLanguage> = speechManager.selectedLanguage

    // Human Voice Activity Detection & Noise Cancellation states
    val isVoiceActive: StateFlow<Boolean> = speechManager.isVoiceActive
    val humanVoiceConfidence: StateFlow<Float> = speechManager.humanVoiceConfidence
    val backgroundNoiseLevel: StateFlow<Float> = speechManager.backgroundNoiseLevel
    val isNoiseSuppressorActive: StateFlow<Boolean> = speechManager.isNoiseSuppressorActive
    val noiseCancellationStatus: StateFlow<String> = speechManager.noiseCancellationStatus

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _permissionsStatus = MutableStateFlow<List<com.example.system.PermissionStatus>>(emptyList())
    val permissionsStatus: StateFlow<List<com.example.system.PermissionStatus>> = _permissionsStatus.asStateFlow()

    private val _contactsList = MutableStateFlow<List<com.example.system.ContactEntry>>(emptyList())
    val contactsList: StateFlow<List<com.example.system.ContactEntry>> = _contactsList.asStateFlow()

    private val _systemStatusMessage = MutableStateFlow<String?>(null)
    val systemStatusMessage: StateFlow<String?> = _systemStatusMessage.asStateFlow()

    private val _isOnlineIntelligenceEnabled = MutableStateFlow(
        app.geminiAssistantEngine.getEffectiveApiKey().isNotBlank() &&
        app.geminiAssistantEngine.getEffectiveApiKey() != "MY_GEMINI_API_KEY"
    )
    val isOnlineIntelligenceEnabled: StateFlow<Boolean> = _isOnlineIntelligenceEnabled.asStateFlow()

    val selectedModelTier: StateFlow<com.example.engine.GeminiModelTier> = app.geminiAssistantEngine.selectedModelTier
    val isAutoFallbackEnabled: StateFlow<Boolean> = app.geminiAssistantEngine.isAutoFallbackEnabled
    val lastExecutionSummary: StateFlow<com.example.engine.QueryExecutionSummary?> = app.geminiAssistantEngine.lastExecutionSummary
    val exhaustedModelList: StateFlow<List<String>> = app.geminiAssistantEngine.exhaustedModelList

    fun clearModelQuotaCooldowns() {
        app.geminiAssistantEngine.clearAllCooldowns()
        _modelTestResult.value = null
    }

    private val _modelTestResult = MutableStateFlow<com.example.engine.ModelAttemptInfo?>(null)
    val modelTestResult: StateFlow<com.example.engine.ModelAttemptInfo?> = _modelTestResult.asStateFlow()

    private val _isTestingModel = MutableStateFlow(false)
    val isTestingModel: StateFlow<Boolean> = _isTestingModel.asStateFlow()

    fun testModel(tier: com.example.engine.GeminiModelTier) {
        viewModelScope.launch {
            _isTestingModel.value = true
            _modelTestResult.value = null
            try {
                val result = app.geminiAssistantEngine.testModelHealth(tier)
                _modelTestResult.value = result
            } finally {
                _isTestingModel.value = false
            }
        }
    }

    private val _activeLocationContext = MutableStateFlow<String?>(app.geminiAssistantEngine.getActiveLocation())
    val activeLocationContext: StateFlow<String?> = _activeLocationContext.asStateFlow()

    private val _activeContextSummary = MutableStateFlow<String?>(app.geminiAssistantEngine.conversationMemory.getActiveContextSummary())
    val activeContextSummary: StateFlow<String?> = _activeContextSummary.asStateFlow()

    val isWakeLockActive: StateFlow<Boolean> = hardware.wakeLockManager.isWakeLockActive
    val isWakeWordLockActive: StateFlow<Boolean> = speechManager.isWakeWordLockActive
    val isTemporarilyUnlocked: StateFlow<Boolean> = speechManager.isTemporarilyUnlocked
    val currentPlayingTrack: StateFlow<String?> = hardware.audioPlayer.currentTrack
    val isLocalPlayerActive: StateFlow<Boolean> = hardware.audioPlayer.isPlaying

    fun clearConversationMemory() {
        app.geminiAssistantEngine.clearConversationHistory()
        _activeLocationContext.value = null
        _activeContextSummary.value = null
    }

    private val _isHighThinkingEnabled = MutableStateFlow(false)
    val isHighThinkingEnabled: StateFlow<Boolean> = _isHighThinkingEnabled.asStateFlow()

    // Default to in-app continuous speech recognizer for seamless 2s silence & auto-restart loop
    private val _preferSystemSpeechDialog = MutableStateFlow(false)
    val preferSystemSpeechDialog: StateFlow<Boolean> = _preferSystemSpeechDialog.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _lastRecognizedCommand = MutableStateFlow("")
    val lastRecognizedCommand: StateFlow<String> = _lastRecognizedCommand.asStateFlow()

    private val _flashState = MutableStateFlow(false)
    val flashState: StateFlow<Boolean> = _flashState.asStateFlow()

    private val _currentVolume = MutableStateFlow(hardware.getMediaVolumePercent())
    val currentVolume: StateFlow<Int> = _currentVolume.asStateFlow()

    private val _showAddRuleDialog = MutableStateFlow(false)
    val showAddRuleDialog: StateFlow<Boolean> = _showAddRuleDialog.asStateFlow()

    private val systemPrefs = app.getSharedPreferences("jarvis_system_prefs", android.content.Context.MODE_PRIVATE)
    private val _isShowAboveLockscreen = MutableStateFlow(systemPrefs.getBoolean("key_show_above_lockscreen", true))
    val isShowAboveLockscreen: StateFlow<Boolean> = _isShowAboveLockscreen.asStateFlow()

    fun setShowAboveLockscreen(enabled: Boolean) {
        _isShowAboveLockscreen.value = enabled
        systemPrefs.edit().putBoolean("key_show_above_lockscreen", enabled).apply()
    }

    private val _showDiagnosticsDialog = MutableStateFlow(false)
    val showDiagnosticsDialog: StateFlow<Boolean> = _showDiagnosticsDialog.asStateFlow()

    private val _diagnosticReport = MutableStateFlow<VoiceDiagnosticReport?>(null)
    val diagnosticReport: StateFlow<VoiceDiagnosticReport?> = _diagnosticReport.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    init {
        sensorHub.startListening()
        loadInstalledApps()
        refreshPermissions()
        loadContacts("")
        enrichCallSmsContactNames()

        speechManager.onFallbackToSystemSpeech = {
            _fallbackToSystemSpeech.tryEmit(Unit)
        }
        app.activeCommandDispatcher = { cmd ->
            submitVoiceCommand(cmd)
        }
    }

    fun refreshPermissions() {
        _permissionsStatus.value = telephonyManager.getAllPermissionsStatus()
        loadContacts("")
    }

    fun loadContacts(query: String = "") {
        viewModelScope.launch {
            _contactsList.value = telephonyManager.searchContacts(query)
        }
    }

    fun makeCall(number: String) {
        val (success, msg) = telephonyManager.makeCall(number)
        _systemStatusMessage.value = msg
        speechManager.speak(msg)
        hardware.vibrate(100)
    }

    fun sendSms(number: String, message: String) {
        val (success, msg) = telephonyManager.sendSms(number, message)
        _systemStatusMessage.value = msg
        speechManager.speak(msg)
        hardware.vibrate(100)
    }

    fun setAlarm(hour: Int, minute: Int, label: String = "Jarvis Alarm") {
        val (success, msg) = telephonyManager.setAlarm(hour, minute, label, skipUi = false)
        _systemStatusMessage.value = msg
        speechManager.speak(msg)
        hardware.vibrate(120)
    }

    fun setTimer(seconds: Int, label: String = "Jarvis Timer") {
        val (success, msg) = telephonyManager.setTimer(seconds, label, skipUi = false)
        _systemStatusMessage.value = msg
        speechManager.speak(msg)
        hardware.vibrate(120)
    }

    fun showAlarms() {
        telephonyManager.showAlarms()
    }

    fun showTimers() {
        telephonyManager.showTimers()
    }

    fun openCalendar() {
        telephonyManager.openCalendar()
    }

    fun addCalendarEvent(title: String, description: String = "") {
        val (success, msg) = telephonyManager.addCalendarEvent(title, description)
        _systemStatusMessage.value = msg
        speechManager.speak(msg)
    }

    fun dismissSystemStatus() {
        _systemStatusMessage.value = null
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = hardware.getInstalledApps()
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    // Scheduled Tasks methods
    fun createScheduledTask(
        title: String,
        taskType: String = "AUTO_SMS",
        targetPhoneNumber: String = "",
        recipientName: String = "",
        messageText: String,
        hour: Int = 0,
        minute: Int = 0,
        intervalMinutes: Int = 0,
        timezoneId: String = "Asia/Kolkata",
        repeatFrequency: String = "ONCE"
    ) {
        viewModelScope.launch {
            val (success, msg) = app.scheduledTaskManager.createAndScheduleTask(
                title = title,
                taskType = taskType,
                targetPhoneNumber = targetPhoneNumber,
                recipientName = recipientName,
                messageText = messageText,
                hour = hour,
                minute = minute,
                intervalMinutes = intervalMinutes,
                timezoneId = timezoneId,
                repeatFrequency = repeatFrequency
            )
            _systemStatusMessage.value = msg
        }
    }

    fun updateScheduledTask(task: com.example.data.ScheduledTask) {
        viewModelScope.launch {
            val (success, msg) = app.scheduledTaskManager.updateAndRescheduleTask(task)
            _systemStatusMessage.value = msg
        }
    }

    fun speakTaskResult(text: String) {
        if (text.isNotBlank()) {
            speechManager.speak(text)
        }
    }

    fun toggleScheduledTask(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setScheduledTaskEnabled(id, enabled)
            if (!enabled) {
                app.scheduledTaskManager.cancelAlarm(id)
            } else {
                val task = repository.getScheduledTaskById(id)
                if (task != null) {
                    val nextTrigger = app.scheduledTaskManager.calculateNextTriggerMillis(
                        hour = task.hour,
                        minute = task.minute,
                        repeatFrequency = task.repeatFrequency,
                        intervalMinutes = task.intervalMinutes,
                        timezoneId = task.timezoneId
                    )
                    val updated = task.copy(scheduledTimeMillis = nextTrigger, status = "PENDING", isEnabled = true)
                    repository.updateScheduledTask(updated)
                    app.scheduledTaskManager.scheduleAlarmManagerExact(updated)
                }
            }
        }
    }

    fun executeScheduledTaskNow(id: Long) {
        viewModelScope.launch {
            val success = app.scheduledTaskManager.executeTask(id)
            _systemStatusMessage.value = if (success) "Executed task #$id immediately." else "Failed to execute task #$id."
        }
    }

    fun deleteScheduledTask(id: Long) {
        viewModelScope.launch {
            app.scheduledTaskManager.cancelAlarm(id)
            repository.deleteScheduledTask(id)
            _systemStatusMessage.value = "Scheduled task deleted."
        }
    }

    fun deleteAllScheduledTasks() {
        viewModelScope.launch {
            val tasks = repository.getPendingScheduledTasksSync()
            tasks.forEach { app.scheduledTaskManager.cancelAlarm(it.id) }
            repository.deleteAllScheduledTasks()
            _systemStatusMessage.value = "All scheduled tasks removed."
        }
    }

    fun deleteCompletedScheduledTasks() {
        viewModelScope.launch {
            repository.deleteCompletedScheduledTasks()
            _systemStatusMessage.value = "Completed task logs cleaned up."
        }
    }

    fun toggleService() {
        val current = isServiceRunning.value
        if (current) {
            JarvisAutomationService.stop(app)
        } else {
            JarvisAutomationService.start(app)
        }
    }

    fun toggleOnlineIntelligence(enabled: Boolean) {
        _isOnlineIntelligenceEnabled.value = enabled
    }

    fun setSelectedModelTier(tier: com.example.engine.GeminiModelTier) {
        app.geminiAssistantEngine.setSelectedModelTier(tier)
    }

    fun setAutoFallbackEnabled(enabled: Boolean) {
        app.geminiAssistantEngine.setAutoFallbackEnabled(enabled)
    }

    fun cycleSelectedModelTier(): com.example.engine.GeminiModelTier {
        val all = com.example.engine.GeminiModelTier.ALL_AVAILABLE_MODELS
        val current = selectedModelTier.value
        val currentIndex = all.indexOf(current)
        val next = all[(currentIndex + 1) % all.size]
        setSelectedModelTier(next)
        return next
    }

    fun toggleHighThinking(enabled: Boolean) {
        _isHighThinkingEnabled.value = enabled
    }

    fun toggleVoiceEngineMode() {
        _preferSystemSpeechDialog.value = !_preferSystemSpeechDialog.value
    }

    fun setVoiceLanguage(language: com.example.voice.VoiceLanguage) {
        speechManager.setLanguage(language)
    }

    fun cycleVoiceLanguage(): com.example.voice.VoiceLanguage {
        return speechManager.cycleLanguage()
    }

    fun setVoiceEngineMode(useSystemDialog: Boolean) {
        _preferSystemSpeechDialog.value = useSystemDialog
    }

    fun setAddRuleDialogVisible(visible: Boolean) {
        _showAddRuleDialog.value = visible
    }

    fun setDiagnosticsDialogVisible(visible: Boolean) {
        _showDiagnosticsDialog.value = visible
        if (visible) {
            runDiagnostics()
        }
    }

    fun runDiagnostics() {
        _diagnosticReport.value = VoiceDiagnostics.runFullDiagnostics(
            context = app,
            isTtsReady = speechManager.isTtsEngineReady(),
            ttsDiagnostics = speechManager.getTtsDiagnostics()
        )
    }

    fun setRecognitionError(error: String?) {
        speechManager.setRecognitionError(error)
    }

    fun clearRecognitionError() {
        speechManager.clearRecognitionError()
    }

    fun speak(text: String) {
        speechManager.speak(text)
    }

    fun stopSpeaking() {
        speechManager.stopSpeaking()
    }

    fun startListening(promptForSpeech: Boolean = true): Boolean {
        return speechManager.startListening(promptForSpeech = promptForSpeech)
    }

    fun stopListening() {
        speechManager.stopListening()
    }

    fun toggleListening(): Boolean {
        return if (speechState.value == SpeechState.SPEAKING) {
            speechManager.stopSpeaking()
            speechManager.startListening(promptForSpeech = true)
            true
        } else if (speechManager.isContinuousModeActive()) {
            speechManager.startListening(promptForSpeech = true)
            true
        } else {
            if (speechState.value == SpeechState.LISTENING) {
                speechManager.stopListening()
                false
            } else {
                speechManager.startListening(promptForSpeech = true)
                true
            }
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean {
        return speechManager.isRecognitionServiceAvailable()
    }

    fun getEffectiveApiKey(): String {
        return app.geminiAssistantEngine.getEffectiveApiKey()
    }

    fun setCustomApiKey(key: String?) {
        app.geminiAssistantEngine.setCustomApiKey(key)
        runDiagnostics()
    }

    fun isCustomApiKeySet(): Boolean {
        return app.geminiAssistantEngine.isCustomApiKeySet()
    }

    fun canUseInAppRecognition(): Boolean {
        return speechManager.canUseInAppRecognition()
    }

    fun createSystemSpeechIntent(): android.content.Intent {
        return speechManager.createSystemSpeechIntent()
    }

    fun submitVoiceCommand(command: String) {
        if (command.isBlank()) return
        if (speechManager.isAcousticSelfEcho(command)) {
            android.util.Log.i("JarvisViewModel", "[EchoGuard] Discarded command matching recent spoken reply: '$command'")
            _isProcessing.value = false
            return
        }
        _lastRecognizedCommand.value = command
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val currentTelem = telemetry.value
                val offlineResult = app.offlineIntentEngine.processCommand(command, currentTelem)

                if (offlineResult.success) {
                    if (offlineResult.intentAction == "CLEAR_CONVERSATION") {
                        clearConversationMemory()
                    }
                    speechManager.speak(offlineResult.spokenResponse)
                    repository.insertCommand(
                        CommandHistoryItem(
                            queryText = command,
                            replyText = offlineResult.spokenResponse,
                            source = "OFFLINE_CORE"
                        )
                    )
                    _flashState.value = hardware.isFlashlightActive()
                    _currentVolume.value = hardware.getMediaVolumePercent()
                } else {
                    // Check local offline knowledge engine first (general questions, Marathi queries)
                    val offlineKnowledge = com.example.engine.OfflineKnowledgeEngine.answerQuery(command, app)
                    if (offlineKnowledge.handled) {
                        // Persist offline knowledge interaction into universal conversation memory
                        app.geminiAssistantEngine.conversationMemory.addTurn("user", command)
                        app.geminiAssistantEngine.conversationMemory.addTurn("model", offlineKnowledge.answer)
                        _activeLocationContext.value = app.geminiAssistantEngine.getActiveLocation()
                        _activeContextSummary.value = app.geminiAssistantEngine.conversationMemory.getActiveContextSummary()

                        speechManager.speak(offlineKnowledge.answer)
                        repository.insertCommand(
                            CommandHistoryItem(
                                queryText = command,
                                replyText = offlineKnowledge.answer,
                                source = "OFFLINE_KNOWLEDGE"
                            )
                        )
                    } else if (offlineResult.canFallbackToGemini && _isOnlineIntelligenceEnabled.value) {
                        val cmdLower = command.lowercase()
                        val isWeatherQuery = cmdLower.contains("weather") || cmdLower.contains("whether") ||
                                cmdLower.contains("forecast") || cmdLower.contains("temperature") ||
                                cmdLower.contains("rain") || cmdLower.contains("climate") ||
                                cmdLower.contains("हवामान") || cmdLower.contains("पाऊस") ||
                                (app.geminiAssistantEngine.conversationMemory.activeTopic == "Weather")

                        val isRealTimeQuery = cmdLower.contains("today") || cmdLower.contains("yesterday") ||
                                cmdLower.contains("score") || cmdLower.contains("match") ||
                                cmdLower.contains("price") || cmdLower.contains("rate") ||
                                cmdLower.contains("current") || cmdLower.contains("who won") ||
                                cmdLower.contains("winner") || cmdLower.contains("live") ||
                                cmdLower.contains("latest") || cmdLower.contains("recent") ||
                                cmdLower.contains("news") || cmdLower.contains("election") ||
                                cmdLower.contains("stock") || isWeatherQuery ||
                                cmdLower.contains("आजचा") || cmdLower.contains("ताज्या बातम्या")

                        val useSearch = cmdLower.contains("search") || isRealTimeQuery
                        val useMaps = cmdLower.contains("where") || cmdLower.contains("map") || cmdLower.contains("near")

                        val answer = app.geminiAssistantEngine.queryAssistant(
                            userQuery = command,
                            enableHighThinking = _isHighThinkingEnabled.value,
                            useSearch = useSearch,
                            useMaps = useMaps
                        )
                        _activeLocationContext.value = app.geminiAssistantEngine.getActiveLocation()
                        _activeContextSummary.value = app.geminiAssistantEngine.conversationMemory.getActiveContextSummary()
                        speechManager.speak(answer)
                        repository.insertCommand(
                            CommandHistoryItem(
                                queryText = command,
                                replyText = answer,
                                source = "GEMINI_ONLINE"
                            )
                        )
                    } else {
                        val isMarathiCmd = command.any { it in '\u0900'..'\u097F' } || selectedLanguage.value == com.example.voice.VoiceLanguage.MARATHI
                        val fallbackMsg = if (offlineResult.spokenResponse.isNotEmpty()) {
                            offlineResult.spokenResponse
                        } else if (isMarathiCmd) {
                            "हा आदेश ऑफलाइन कोअरमध्ये उपलब्ध नाही. अधिक माहितीसाठी वरच्या उजव्या बाजूला क्लाउड बुद्धिमत्ता (Cloud AI) चालू करा."
                        } else {
                            "Command not recognized in offline core. Enable cloud intelligence in top-right for extended reasoning and web search."
                        }
                        speechManager.speak(fallbackMsg)
                        repository.insertCommand(
                            CommandHistoryItem(
                                queryText = command,
                                replyText = fallbackMsg,
                                source = "OFFLINE_CORE"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                speechManager.speak("Encountered an internal error processing that request.")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun toggleRule(rule: AutomationRule) {
        viewModelScope.launch {
            repository.setRuleEnabled(rule.id, !rule.isEnabled)
        }
    }

    fun deleteRule(rule: AutomationRule) {
        viewModelScope.launch {
            repository.deleteRule(rule.id)
        }
    }

    fun addRule(
        name: String,
        triggerType: String,
        triggerThreshold: Float,
        actionType: String,
        actionParam: String
    ) {
        viewModelScope.launch {
            val newRule = AutomationRule(
                name = name.ifBlank { "Custom $triggerType Rule" },
                triggerType = triggerType,
                triggerThreshold = triggerThreshold,
                actionType = actionType,
                actionParam = actionParam,
                isEnabled = true,
                cooldownSeconds = 4
            )
            repository.insertRule(newRule)
            speechManager.speak("Workflow rule ${newRule.name} saved and active.")
            _showAddRuleDialog.value = false
        }
    }

    fun testTriggerRule(rule: AutomationRule) {
        viewModelScope.launch {
            when (rule.actionType) {
                ActionTypes.TOGGLE_FLASHLIGHT -> {
                    hardware.toggleFlashlight()
                    _flashState.value = hardware.isFlashlightActive()
                }
                ActionTypes.FLASHLIGHT_ON -> {
                    hardware.setFlashlight(true)
                    _flashState.value = true
                }
                ActionTypes.FLASHLIGHT_OFF -> {
                    hardware.setFlashlight(false)
                    _flashState.value = false
                }
                ActionTypes.FLASHLIGHT_SOS -> {
                    hardware.setFlashlight(true)
                    _flashState.value = true
                    hardware.vibrate(200)
                    speechManager.speak("Flashlight alert active.")
                }
                ActionTypes.MUTE_ALL -> {
                    hardware.muteAllAudio()
                    _currentVolume.value = 0
                    speechManager.speak("Muted audio streams.")
                }
                ActionTypes.MAX_VOLUME -> {
                    hardware.setMaxVolume()
                    _currentVolume.value = 100
                }
                ActionTypes.SET_VOLUME -> {
                    val pct = rule.actionParam.toIntOrNull() ?: 50
                    hardware.setMediaVolumePercent(pct)
                    _currentVolume.value = pct
                }
                ActionTypes.SPEAK_TTS -> {
                    speechManager.speak(rule.actionParam.ifBlank { "Testing automation rule ${rule.name}" })
                }
                ActionTypes.ANNOUNCE_BATTERY -> {
                    val level = telemetry.value.batteryLevel
                    val isCharging = telemetry.value.isCharging
                    speechManager.speak("Current battery is $level percent${if (isCharging) ", charging" else ""}.")
                }
                ActionTypes.ANNOUNCE_TIME -> {
                    val timeStr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                    speechManager.speak("Current time is $timeStr.")
                }
                ActionTypes.LAUNCH_APP -> {
                    hardware.launchAppByName(rule.actionParam)
                }
                ActionTypes.LAUNCH_CAMERA -> {
                    hardware.launchCamera()
                }
                ActionTypes.START_LISTENING -> {
                    hardware.acquireCpuWakeLock(10000, "Jarvis:TestVoiceTrigger")
                    hardware.wakeUpScreen(5000)
                    speechManager.startListening(promptForSpeech = true)
                }
                ActionTypes.MEDIA_PLAY_PAUSE -> {
                    hardware.controlMedia(com.example.hardware.MediaControlAction.TOGGLE)
                }
                ActionTypes.MEDIA_NEXT -> {
                    hardware.controlMedia(com.example.hardware.MediaControlAction.NEXT)
                }
                ActionTypes.PLAY_MUSIC -> {
                    hardware.playSongOrMusic(rule.actionParam)
                }
                ActionTypes.VIBRATE -> {
                    hardware.vibrate(250)
                }
            }

            repository.recordRuleTriggered(rule.id, System.currentTimeMillis())
            repository.insertLog(
                ExecutionLog(
                    ruleName = rule.name,
                    triggerType = "MANUAL_TEST",
                    details = "Manually triggered from dashboard",
                    isSuccess = true
                )
            )
        }
    }

    // Media & Apps Controls
    fun playSongOrMusic(query: String = "", preferredPlayer: String? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val (success, desc) = hardware.playSongOrMusic(query, preferredPlayer)
            _systemStatusMessage.value = desc
            if (success) {
                val voiceMsg = when (preferredPlayer?.lowercase()) {
                    "vlc" -> if (query.isNotBlank()) "Playing \"$query\" in VLC player, sir." else "VLC player opened, sir."
                    "youtube" -> if (query.isNotBlank()) "Playing \"$query\" on YouTube, sir." else "YouTube opened, sir."
                    else -> if (query.isNotBlank()) "Auto-playing \"$query\", sir." else "$desc, sir."
                }
                speechManager.speak(voiceMsg)
            } else {
                speechManager.speak("Unable to launch music player.")
            }
        }
    }

    fun launchApp(appName: String) {
        viewModelScope.launch {
            val (success, name) = hardware.launchAppByName(appName)
            if (success) {
                speechManager.speak("Opening $name now, sir.")
            } else {
                speechManager.speak("Application $appName not found on device.")
            }
        }
    }

    fun controlMedia(action: com.example.hardware.MediaControlAction) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val (success, desc) = hardware.controlMedia(action)
            _systemStatusMessage.value = desc
            speechManager.speak("$desc, sir.")
        }
    }

    fun toggleWakeLock() {
        viewModelScope.launch {
            val enabled = hardware.togglePersistentWakeLock()
            val msg = if (hardware.isWakeLockActive()) "Persistent WakeLock engaged. Playback & automations will run on locked device, sir."
            else "Persistent WakeLock released. Reverted to standard battery saving mode, sir."
            _systemStatusMessage.value = msg
            speechManager.speak(msg)
        }
    }

    fun increaseVolume() {
        viewModelScope.launch {
            val newVol = hardware.increaseVolume(15)
            _currentVolume.value = newVol
            _systemStatusMessage.value = "Volume set to $newVol%"
            speechManager.speak("Volume $newVol percent, sir.")
        }
    }

    fun decreaseVolume() {
        viewModelScope.launch {
            val newVol = hardware.decreaseVolume(15)
            _currentVolume.value = newVol
            _systemStatusMessage.value = "Volume set to $newVol%"
            speechManager.speak("Volume $newVol percent, sir.")
        }
    }

    fun wakeScreen() {
        viewModelScope.launch {
            hardware.wakeUpScreen(8000)
            _systemStatusMessage.value = "Illuminating screen display"
            speechManager.speak("Screen display illuminated, sir.")
        }
    }

    fun closeAppOrGoHome(appName: String? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val (success, desc) = hardware.closeAppOrGoHome(appName)
            _systemStatusMessage.value = desc
            speechManager.speak("$desc, sir.")
        }
    }

    fun enrichCallSmsContactNames() {
        autoCallSmsManager.enrichExistingLogsWithContactNames()
    }

    fun loadPreconfiguredRulesPack() {
        viewModelScope.launch {
            val presets = listOf(
                AutomationRule(
                    name = "Wave Hand Voice Command Trigger",
                    triggerType = TriggerTypes.HAND_WAVE,
                    triggerThreshold = 0f,
                    actionType = ActionTypes.START_LISTENING,
                    actionParam = "Wave Hand Voice Trigger (Lockscreen Ready)",
                    isEnabled = true,
                    cooldownSeconds = 3
                ),
                AutomationRule(
                    name = "Wave Hand Media Play/Pause",
                    triggerType = TriggerTypes.HAND_WAVE,
                    triggerThreshold = 0f,
                    actionType = ActionTypes.MEDIA_PLAY_PAUSE,
                    actionParam = "Kinetic Wave Media Toggle",
                    isEnabled = false,
                    cooldownSeconds = 3
                ),
                AutomationRule(
                    name = "Pocket-Guard Shake Flashlight",
                    triggerType = TriggerTypes.SHAKE,
                    triggerThreshold = 26.0f,
                    actionType = ActionTypes.TOGGLE_FLASHLIGHT,
                    actionParam = "Kinetic Torch Toggle (Pocket Safe)",
                    isEnabled = true,
                    cooldownSeconds = 3
                ),
                AutomationRule(
                    name = "Flip Face-Down Meeting Silence",
                    triggerType = TriggerTypes.FLIP_FACE_DOWN,
                    triggerThreshold = 0f,
                    actionType = ActionTypes.MUTE_ALL,
                    actionParam = "Auto Muted for Privacy",
                    isEnabled = true,
                    cooldownSeconds = 5
                ),
                AutomationRule(
                    name = "Low Battery Voice Alert",
                    triggerType = TriggerTypes.BATTERY_LOW,
                    triggerThreshold = 20.0f,
                    actionType = ActionTypes.SPEAK_TTS,
                    actionParam = "Battery power depleted below twenty percent. Please connect charger.",
                    isEnabled = true,
                    cooldownSeconds = 60
                ),
                AutomationRule(
                    name = "Full Battery 100% Notice",
                    triggerType = TriggerTypes.BATTERY_FULL,
                    triggerThreshold = 100.0f,
                    actionType = ActionTypes.SPEAK_TTS,
                    actionParam = "Battery is fully charged at one hundred percent, sir. You may unplug.",
                    isEnabled = true,
                    cooldownSeconds = 120
                ),
                AutomationRule(
                    name = "Power Connected Confirmation",
                    triggerType = TriggerTypes.CHARGER_CONNECTED,
                    triggerThreshold = 0f,
                    actionType = ActionTypes.SPEAK_TTS,
                    actionParam = "Power source connected. Jarvis core recharging.",
                    isEnabled = true,
                    cooldownSeconds = 10
                ),
                AutomationRule(
                    name = "Power Disconnected Alert",
                    triggerType = TriggerTypes.CHARGER_DISCONNECTED,
                    triggerThreshold = 0f,
                    actionType = ActionTypes.SPEAK_TTS,
                    actionParam = "Power source disconnected. Operating on battery power.",
                    isEnabled = true,
                    cooldownSeconds = 10
                ),
                AutomationRule(
                    name = "Dark Light Night Mode",
                    triggerType = TriggerTypes.LIGHT_BELOW,
                    triggerThreshold = 5.0f,
                    actionType = ActionTypes.SET_VOLUME,
                    actionParam = "20",
                    isEnabled = false,
                    cooldownSeconds = 30
                ),
                AutomationRule(
                    name = "Outdoor Sunlight Sound Boost",
                    triggerType = TriggerTypes.LIGHT_ABOVE,
                    triggerThreshold = 1000.0f,
                    actionType = ActionTypes.MAX_VOLUME,
                    actionParam = "Max volume for outdoor environment",
                    isEnabled = false,
                    cooldownSeconds = 30
                ),
                AutomationRule(
                    name = "Desk Face-Up Ready Volume",
                    triggerType = TriggerTypes.FLIP_FACE_UP,
                    triggerThreshold = 0f,
                    actionType = ActionTypes.SET_VOLUME,
                    actionParam = "75",
                    isEnabled = true,
                    cooldownSeconds = 10
                ),
                AutomationRule(
                    name = "Phone Picked Up Time Announcement",
                    triggerType = TriggerTypes.ORIENTATION_UPRIGHT,
                    triggerThreshold = 0f,
                    actionType = ActionTypes.ANNOUNCE_TIME,
                    actionParam = "Spoken Time Alert",
                    isEnabled = false,
                    cooldownSeconds = 60
                ),
                AutomationRule(
                    name = "Proximity Uncovered Welcome & Battery",
                    triggerType = TriggerTypes.PROXIMITY_FAR,
                    triggerThreshold = 0f,
                    actionType = ActionTypes.ANNOUNCE_BATTERY,
                    actionParam = "Proximity Sensor Trigger",
                    isEnabled = false,
                    cooldownSeconds = 60
                )
            )

            for (rule in presets) {
                repository.insertRule(rule)
            }
            speechManager.speak("Full suite of pre-configured automation rules added to local core.")
        }
    }

    fun loadPreconfiguredSmsRulesPack() {
        viewModelScope.launch {
            val presets = listOf(
                AutoSmsRule(
                    keyword = "urgent",
                    replyTemplate = "Jarvis Emergency Alert: Urgent priority acknowledged. Notifying user immediately. Status: {charging}, Battery: {battery}%.",
                    isEnabled = true,
                    matchType = "CONTAINS"
                ),
                AutoSmsRule(
                    keyword = "where are you",
                    replyTemplate = "Jarvis Status: The user is currently mobile. Battery: {battery}%, Time: {time}.",
                    isEnabled = true,
                    matchType = "CONTAINS"
                ),
                AutoSmsRule(
                    keyword = "call me",
                    replyTemplate = "Jarvis Auto: Message received. User informed to call you shortly as soon as available.",
                    isEnabled = true,
                    matchType = "CONTAINS"
                ),
                AutoSmsRule(
                    keyword = "meeting",
                    replyTemplate = "Jarvis Auto: Currently in a meeting. Please text if critical.",
                    isEnabled = true,
                    matchType = "CONTAINS"
                ),
                AutoSmsRule(
                    keyword = "status",
                    replyTemplate = "Jarvis Diagnostics: Battery at {battery}%, {charging}. Systems nominal.",
                    isEnabled = true,
                    matchType = "CONTAINS"
                ),
                AutoSmsRule(
                    keyword = "कुठे आहेस",
                    replyTemplate = "जार्व्हिस ऑटो: युझर सध्या व्यस्त आहे. बॅटरी: {battery}%. लवकरच संपर्क करतील.",
                    isEnabled = true,
                    matchType = "CONTAINS"
                ),
                AutoSmsRule(
                    keyword = "कॉल कर",
                    replyTemplate = "जार्व्हिस ऑटो: संदेश मिळाला आहे. युझर थोड्या वेळात कॉल करतील.",
                    isEnabled = true,
                    matchType = "CONTAINS"
                ),
                AutoSmsRule(
                    keyword = "help",
                    replyTemplate = "Jarvis Emergency: Priority assistance requested. Relaying to user now.",
                    isEnabled = true,
                    matchType = "CONTAINS"
                ),
                AutoSmsRule(
                    keyword = "driving",
                    replyTemplate = "Jarvis Auto: User is currently driving. Voice systems active.",
                    isEnabled = true,
                    matchType = "CONTAINS"
                ),
                AutoSmsRule(
                    keyword = "good morning",
                    replyTemplate = "Jarvis Auto: Good morning! User has received your message.",
                    isEnabled = true,
                    matchType = "CONTAINS"
                )
            )
            for (rule in presets) {
                repository.insertAutoSmsRule(rule)
            }
            speechManager.speak("Pre-configured auto-SMS rules deployed.")
        }
    }

    // Hardware quick toggles
    fun toggleFlashlight() {
        val success = hardware.toggleFlashlight()
        if (success) {
            _flashState.value = hardware.isFlashlightActive()
            hardware.vibrate(80)
        }
    }

    fun setVolume(percent: Int) {
        hardware.setMediaVolumePercent(percent)
        _currentVolume.value = percent
    }

    fun muteAudio() {
        hardware.muteAllAudio()
        _currentVolume.value = 0
        hardware.vibrate(100)
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Always-On Mic Control
    fun setMicAlwaysOnMode(mode: MicAlwaysOnMode) {
        speechManager.setMicAlwaysOnMode(mode)
    }

    fun cycleMicAlwaysOnMode(): MicAlwaysOnMode {
        return speechManager.cycleMicAlwaysOnMode()
    }

    // Wake-Word Gatekeeper Lock Control (Alexa-Style Strict "Jarvis" requirement)
    fun setWakeWordLock(enabled: Boolean) {
        speechManager.setWakeWordLock(enabled)
        val msg = if (enabled) "Wake-Word Gatekeeper Lock engaged. Mic will only accept commands starting with 'Jarvis'."
        else "Wake-Word Gatekeeper Lock disabled. Mic will accept any continuous speech."
        _systemStatusMessage.value = msg
        speechManager.speak(msg)
    }

    fun toggleWakeWordLock(): Boolean {
        val next = speechManager.toggleWakeWordLock()
        val msg = if (next) "Wake-Word Lock ON: Speak 'Jarvis' to command."
        else "Wake-Word Lock OFF: Continuous open mic active."
        _systemStatusMessage.value = msg
        speechManager.speak(msg)
        return next
    }

    // Auto Call & SMS System Control
    fun updateAutoCallSmsConfig(config: AutoCallSmsConfig) {
        autoCallSmsManager.updateConfig(config)
    }

    fun setAutoCallPreset(preset: String) {
        autoCallSmsManager.setPreset(preset)
    }

    fun toggleAutoSms(enabled: Boolean) {
        autoCallSmsManager.toggleAutoSms(enabled)
    }

    fun toggleAutoReadSms(enabled: Boolean) {
        autoCallSmsManager.toggleAutoReadSms(enabled)
    }

    fun toggleAutoAnnounceCalls(enabled: Boolean) {
        autoCallSmsManager.toggleAutoAnnounceCalls(enabled)
    }

    fun toggleAutoReplyMissedCalls(enabled: Boolean) {
        autoCallSmsManager.toggleAutoReplyMissedCalls(enabled)
    }

    fun setCustomAutoReplyText(text: String) {
        autoCallSmsManager.setCustomReplyText(text)
    }

    fun setEmergencyContact(contact: String) {
        autoCallSmsManager.setEmergencyContact(contact)
    }

    fun triggerEmergencySos() {
        autoCallSmsManager.triggerEmergencySos()
    }

    fun insertAutoSmsRule(rule: AutoSmsRule) {
        viewModelScope.launch {
            repository.insertAutoSmsRule(rule)
        }
    }

    fun updateAutoSmsRule(rule: AutoSmsRule) {
        viewModelScope.launch {
            repository.updateAutoSmsRule(rule)
        }
    }

    fun deleteAutoSmsRule(id: Long) {
        viewModelScope.launch {
            repository.deleteAutoSmsRule(id)
        }
    }

    fun toggleAutoSmsRule(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoSmsRuleEnabled(id, enabled)
        }
    }

    fun simulateIncomingCall(number: String, name: String, missed: Boolean) {
        autoCallSmsManager.simulateIncomingCall(number, name, missed)
    }

    fun simulateIncomingSms(number: String, message: String) {
        autoCallSmsManager.simulateIncomingSms(number, message)
    }

    fun clearCallSmsLogs() {
        viewModelScope.launch {
            repository.clearCallSmsLogs()
        }
    }

    fun closeAppAndReleaseMic() {
        speechManager.stopListening()
        if (!JarvisAutomationService.isRunning.value) {
            speechManager.destroy()
        }
    }

    fun onAppDestroyed() {
        // If foreground automation service is not running, cleanly release all microphone resources
        if (!JarvisAutomationService.isRunning.value) {
            speechManager.destroy()
        } else {
            // Keep background service alive but stop any UI listening loop
            speechManager.stopListening()
        }
    }

    override fun onCleared() {
        super.onCleared()
        app.activeCommandDispatcher = null
        closeAppAndReleaseMic()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return JarvisViewModel(application) as T
        }
    }
}
