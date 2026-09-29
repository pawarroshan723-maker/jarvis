package com.example

import android.app.Application
import com.example.data.CommandHistoryItem
import com.example.data.JarvisDatabase
import com.example.data.JarvisRepository
import com.example.engine.GeminiAssistantEngine
import com.example.engine.OfflineIntentEngine
import com.example.hardware.HardwareController
import com.example.sensor.SensorHub
import com.example.voice.JarvisSpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JarvisApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { JarvisDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { JarvisRepository(database.jarvisDao()) }
    val hardwareController by lazy { HardwareController(this) }
    val telephonyAlarmManager by lazy { com.example.system.TelephonyAlarmManager(this) }
    val sensorHub by lazy { SensorHub(this, applicationScope) }
    val sensorConflictEngine by lazy { com.example.sensor.SensorConflictEngine(sensorHub, hardwareController) }

    val scheduledTaskManager by lazy {
        com.example.system.ScheduledTaskManager(
            context = this,
            repository = repository,
            telephonyManager = telephonyAlarmManager,
            scope = applicationScope
        )
    }

    val autoCallSmsManager: com.example.system.AutoCallSmsManager by lazy {
        com.example.system.AutoCallSmsManager(
            context = this,
            repository = repository,
            telephonyManager = telephonyAlarmManager,
            hardwareController = hardwareController,
            speechManager = speechManager,
            sensorHub = sensorHub,
            scope = applicationScope
        )
    }

    val offlineIntentEngine by lazy {
        OfflineIntentEngine(
            hardware = hardwareController,
            repository = repository,
            context = this,
            telephonyManager = telephonyAlarmManager,
            speechManager = speechManager,
            autoCallSmsManager = autoCallSmsManager,
            scheduledTaskManager = scheduledTaskManager
        )
    }

    val geminiAssistantEngine by lazy { GeminiAssistantEngine(this) }

    val lastCommandFlow = kotlinx.coroutines.flow.MutableStateFlow<String>("")
    val isExecutingCommand = kotlinx.coroutines.flow.MutableStateFlow<Boolean>(false)
    var activeCommandDispatcher: ((String) -> Unit)? = null

    val speechManager: JarvisSpeechManager by lazy {
        JarvisSpeechManager(this) { recognizedWords ->
            val dispatcher = activeCommandDispatcher
            if (dispatcher != null) {
                dispatcher.invoke(recognizedWords)
            } else {
                handleVoiceCommand(recognizedWords)
            }
        }
    }

    /**
     * Executes voice commands:
     * 1. Offline Engine evaluation (system controls, toggles, Marathi, offline knowledge)
     * 2. Gemini AI fallback if offline engine doesn't match
     * 3. Speaks the answer via Text-to-Speech
     * 4. When TTS ends, JarvisSpeechManager automatically turns the mic back ON!
     */
    fun handleVoiceCommand(command: String, onCompleted: ((String) -> Unit)? = null) {
        if (command.isBlank()) return
        if (speechManager.isAcousticSelfEcho(command)) {
            android.util.Log.i("JarvisApp", "[EchoGuard] Discarded command matching recent spoken reply: '$command'")
            return
        }
        lastCommandFlow.value = command
        isExecutingCommand.value = true

        applicationScope.launch {
            try {
                val currentTelem = sensorHub.telemetry.value
                val offlineResult = offlineIntentEngine.processCommand(command, currentTelem)

                if (offlineResult.success) {
                    if (offlineResult.intentAction == "CLEAR_CONVERSATION") {
                        geminiAssistantEngine.clearConversationHistory()
                    }
                    val response = offlineResult.spokenResponse
                    speechManager.speak(response)
                    repository.insertCommand(
                        CommandHistoryItem(
                            queryText = command,
                            replyText = response,
                            source = "OFFLINE_CORE"
                        )
                    )
                    onCompleted?.invoke(response)
                } else {
                    // Check OfflineKnowledgeEngine before external calls
                    val offlineKnowledge = com.example.engine.OfflineKnowledgeEngine.answerQuery(command, this@JarvisApplication)
                    val response = if (offlineKnowledge.handled) {
                        geminiAssistantEngine.conversationMemory.addTurn("user", command)
                        geminiAssistantEngine.conversationMemory.addTurn("model", offlineKnowledge.answer)
                        offlineKnowledge.answer
                    } else {
                        try {
                            val cmdLower = command.lowercase()
                            val isWeatherQuery = cmdLower.contains("weather") || cmdLower.contains("whether") ||
                                    cmdLower.contains("forecast") || cmdLower.contains("temperature") ||
                                    cmdLower.contains("rain") || cmdLower.contains("climate") ||
                                    cmdLower.contains("हवामान") || cmdLower.contains("पाऊस") ||
                                    (geminiAssistantEngine.conversationMemory.activeTopic == "Weather")

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
                            geminiAssistantEngine.queryAssistant(
                                userQuery = command,
                                enableHighThinking = false,
                                useSearch = useSearch,
                                useMaps = useMaps
                            )
                        } catch (e: Exception) {
                            if (offlineResult.spokenResponse.isNotEmpty()) {
                                offlineResult.spokenResponse
                            } else {
                                "Command processed. Ready for next query."
                            }
                        }
                    }

                    speechManager.speak(response)
                    repository.insertCommand(
                        CommandHistoryItem(
                            queryText = command,
                            replyText = response,
                            source = if (offlineKnowledge.handled) "OFFLINE_KNOWLEDGE" else "GEMINI_ONLINE"
                        )
                    )
                    onCompleted?.invoke(response)
                }
            } catch (e: Exception) {
                android.util.Log.e("JarvisApp", "Error executing voice command in background", e)
                val fallback = "I encountered an error processing that request."
                speechManager.speak(fallback)
                onCompleted?.invoke(fallback)
            } finally {
                isExecutingCommand.value = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            repository.enforceSafeShakeThreshold()
            scheduledTaskManager.rescheduleAllPendingTasks()
        }
    }
}
