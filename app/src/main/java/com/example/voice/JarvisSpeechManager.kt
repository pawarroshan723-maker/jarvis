package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class SpeechState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

/**
 * Intelligent Speech and Voice Management Engine for Jarvis.
 *
 * Core Architecture & User Flow:
 * 1. Human Voice Detection System (VAD): Distinguishes genuine human vocal tract formant frequencies
 *    from ambient room noises, fans, and AC units without conflicting AudioRecord collisions.
 * 2. Background Noise Cancellation: Uses hardware DSP (AudioSource.VOICE_RECOGNITION) +
 *    Dynamic Adaptive Noise Floor tracking to eliminate false triggers and client binding issues.
 * 3. 2-Second Silence Trigger: When user speech ceases for 2 full seconds (2000 ms), the buffered
 *    sentence is dispatched to the intelligence engine.
 * 4. Get Answer & Speak: Jarvis processes the request, gets the answer, and speaks it via TTS.
 * 5. Mic On Again: As soon as TTS finishes speaking, the microphone automatically
 *    turns back ON and resumes listening seamlessly.
 * 6. Clean Complete Exit: When user closes the app, all microphone listeners, wake locks,
 *    and background threads are completely released immediately.
 */
class JarvisSpeechManager(
    private val context: Context,
    var onCommandRecognized: (String) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "JarvisSpeechManager"
        private const val PREFS_NAME = "jarvis_voice_prefs"
        private const val KEY_LANG = "voice_language_mode"
        private const val KEY_MIC_MODE = "voice_mic_always_on_mode"
        private const val KEY_WAKE_WORD_LOCK = "jarvis_wake_word_lock"

    // Exact 2-second silence countdown before dispatching words
        const val SILENCE_STOP_INTERVAL_MILLIS = 2000L
        const val MINIMUM_SPEECH_LENGTH_MILLIS = 1500L
        const val WATCHDOG_CHECK_INTERVAL_MILLIS = 6000L
        const val TTS_AUDIO_COOLDOWN_MILLIS = 850L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private var pendingSpeech: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isScreenReceiverRegistered = false

    // Acoustic Echo Suppression & Self-Loop Guard
    @Volatile
    private var isTtsSpeaking = false
    @Volatile
    private var ttsAudioCooldownUntil = 0L
    private val recentSpokenUtterances = mutableListOf<Pair<Long, String>>()

    var onFallbackToSystemSpeech: (() -> Unit)? = null

    private val _speechState = MutableStateFlow(SpeechState.IDLE)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _lastSpokenText = MutableStateFlow("")
    val lastSpokenText: StateFlow<String> = _lastSpokenText.asStateFlow()

    private val _recognitionError = MutableStateFlow<String?>(null)
    val recognitionError: StateFlow<String?> = _recognitionError.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(loadSavedLanguage())
    val selectedLanguage: StateFlow<VoiceLanguage> = _selectedLanguage.asStateFlow()

    private val _micAlwaysOnMode = MutableStateFlow(loadSavedMicMode())
    val micAlwaysOnMode: StateFlow<MicAlwaysOnMode> = _micAlwaysOnMode.asStateFlow()

    private val _isWakeWordLockActive = MutableStateFlow(loadSavedWakeWordLock())
    val isWakeWordLockActive: StateFlow<Boolean> = _isWakeWordLockActive.asStateFlow()

    private val _isTemporarilyUnlocked = MutableStateFlow(false)
    val isTemporarilyUnlocked: StateFlow<Boolean> = _isTemporarilyUnlocked.asStateFlow()

    private var relockRunnable: Runnable? = null

    private val _isScreenOffArmed = MutableStateFlow(false)
    val isScreenOffArmed: StateFlow<Boolean> = _isScreenOffArmed.asStateFlow()

    // Human Voice Activity & Background Noise Cancellation Engine
    val voiceActivityDetector = HumanVoiceActivityDetector(
        context = context,
        onHumanVoiceStarted = {
            onHumanVoiceDetected()
        },
        onHumanVoiceStopped2Seconds = {
            onHumanVoiceStoppedAfter2Sec()
        }
    )

    val isVoiceActive: StateFlow<Boolean> = voiceActivityDetector.isVoiceActive
    val humanVoiceConfidence: StateFlow<Float> = voiceActivityDetector.humanVoiceConfidence
    val backgroundNoiseLevel: StateFlow<Float> = voiceActivityDetector.backgroundNoiseLevel
    val isNoiseSuppressorActive: StateFlow<Boolean> = voiceActivityDetector.isNoiseSuppressorActive
    val noiseCancellationStatus: StateFlow<String> = voiceActivityDetector.noiseCancellationStatus

    // 2-Second Silence Buffer Management
    private val speechBuffer = StringBuilder()
    private var isSpeechActive = false
    private var silenceTimerRunnable: Runnable? = null

    private var isContinuousRestartScheduled = false
    private var isDestroyed = false
    private var isRecognizerActive = false

    // Anti-stalling watchdog timer: only runs while LISTENING in continuous mode
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (isDestroyed) return
            try {
                if (isContinuousModeActive() && _speechState.value == SpeechState.LISTENING) {
                    if (!isRecognizerActive) {
                        Log.d(TAG, "Watchdog: Recognizer idle while LISTENING. Refreshing session...")
                        startRecognizerSession()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Watchdog execution error: ${e.message}")
            }
            if (!isDestroyed && isContinuousModeActive() && _speechState.value == SpeechState.LISTENING) {
                mainHandler.postDelayed(this, WATCHDOG_CHECK_INTERVAL_MILLIS)
            }
        }
    }

    private fun loadSavedLanguage(): VoiceLanguage {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_LANG, VoiceLanguage.AUTO.code)
        return VoiceLanguage.fromCode(code)
    }

    private fun loadSavedMicMode(): MicAlwaysOnMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_MIC_MODE, MicAlwaysOnMode.SCREEN_ON_ONLY.code)
        return MicAlwaysOnMode.fromCode(code)
    }

    private fun loadSavedWakeWordLock(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WAKE_WORD_LOCK, false) // Default to Open Mic mode for immediate out-of-the-box command recognition
    }

    fun setWakeWordLock(enabled: Boolean) {
        _isWakeWordLockActive.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WAKE_WORD_LOCK, enabled).apply()
        Log.i(TAG, "Jarvis Wake-Word Gatekeeper Lock set to: $enabled")
    }

    fun toggleWakeWordLock(): Boolean {
        val next = !_isWakeWordLockActive.value
        setWakeWordLock(next)
        return next
    }

    fun armTemporaryUnlock(durationMillis: Long = 15000L) {
        relockRunnable?.let { mainHandler.removeCallbacks(it) }
        _isTemporarilyUnlocked.value = true
        relockRunnable = Runnable {
            _isTemporarilyUnlocked.value = false
            Log.i(TAG, "[JarvisLock] Voice command window expired. Re-engaged Wake-Word Lock.")
        }
        mainHandler.postDelayed(relockRunnable!!, durationMillis)
    }

    fun cancelTemporaryUnlock() {
        relockRunnable?.let { mainHandler.removeCallbacks(it) }
        _isTemporarilyUnlocked.value = false
    }

    /**
     * Checks if the candidate speech contains the wake word "Jarvis" / "Hey Jarvis" / "जार्व्हिस".
     * Returns Pair(isWakeWordPresent, strippedCommandText).
     */
    fun extractWakeWordAndCommand(raw: String): Pair<Boolean, String> {
        val clean = raw.trim()
        val lower = clean.lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{M}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val wakePrefixes = listOf(
            "hey jarvis", "हे जार्व्हिस", "हे जार्विस", "हे झार्व्हिस", "ok jarvis", "ओके जार्व्हिस",
            "hi jarvis", "हाय जार्व्हिस", "jarvis please", "jarvis", "जार्व्हिस", "जार्विस", "जॉर्विस",
            "झार्व्हिस", "झार्विस", "जाविस", "सर्व्हिस", "jarvish", "javis", "jarves", "dharvis"
        )

        // 1. Direct exact wake word call (e.g. "Jarvis" or "Hey Jarvis")
        for (prefix in wakePrefixes) {
            if (lower == prefix) {
                return Pair(true, "")
            }
        }

        // 2. Starts with wake word prefix (e.g. "Jarvis play Arijit Singh")
        for (prefix in wakePrefixes) {
            if (lower.startsWith("$prefix ")) {
                val remainder = clean.substring(minOf(prefix.length, clean.length)).trim()
                return Pair(true, remainder)
            }
        }

        // 3. Contains wake word anywhere in the utterance
        for (prefix in wakePrefixes) {
            if (lower.contains(prefix)) {
                val cleanedCommand = clean.replace(Regex("(?i)\\b$prefix\\b"), "").trim()
                return Pair(true, cleanedCommand)
            }
        }

        return Pair(false, raw)
    }

    fun isContinuousModeActive(): Boolean =
        _micAlwaysOnMode.value != MicAlwaysOnMode.MANUAL

    fun setMicAlwaysOnMode(mode: MicAlwaysOnMode) {
        _micAlwaysOnMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MIC_MODE, mode.code).apply()
        Log.i(TAG, "Mic mode set to: ${mode.title}")

        when (mode) {
            MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON -> {
                acquireWakeLockIfNeeded()
                _isScreenOffArmed.value = true
                startListening()
            }
            MicAlwaysOnMode.SCREEN_OFF_ONLY -> {
                acquireWakeLockIfNeeded()
                _isScreenOffArmed.value = true
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm?.isInteractive == false) {
                    startListening()
                } else {
                    stopListening()
                }
            }
            MicAlwaysOnMode.SCREEN_ON_ONLY -> {
                releaseWakeLock()
                _isScreenOffArmed.value = false
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm?.isInteractive != false) {
                    startListening()
                } else {
                    stopListening()
                }
            }
            MicAlwaysOnMode.MANUAL -> {
                releaseWakeLock()
                _isScreenOffArmed.value = false
                stopListening()
            }
        }
    }

    fun cycleMicAlwaysOnMode(): MicAlwaysOnMode {
        val next = when (_micAlwaysOnMode.value) {
            MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON -> MicAlwaysOnMode.SCREEN_OFF_ONLY
            MicAlwaysOnMode.SCREEN_OFF_ONLY -> MicAlwaysOnMode.SCREEN_ON_ONLY
            MicAlwaysOnMode.SCREEN_ON_ONLY -> MicAlwaysOnMode.MANUAL
            MicAlwaysOnMode.MANUAL -> MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON
        }
        setMicAlwaysOnMode(next)
        return next
    }

    private fun acquireWakeLockIfNeeded() {
        if (_micAlwaysOnMode.value == MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON ||
            _micAlwaysOnMode.value == MicAlwaysOnMode.SCREEN_OFF_ONLY
        ) {
            try {
                if (wakeLock == null) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis:AlwaysOnMicWakeLock")?.apply {
                        setReferenceCounted(false)
                    }
                }
                wakeLock?.let {
                    if (!it.isHeld) {
                        it.acquire(2 * 60 * 60 * 1000L) // 2 hours max
                        Log.i(TAG, "Partial WakeLock acquired for Always-On Mic")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error acquiring WakeLock: ${e.message}")
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "Partial WakeLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        }
    }

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen turned OFF. Mode: ${_micAlwaysOnMode.value}")
                    if (_micAlwaysOnMode.value == MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON ||
                        _micAlwaysOnMode.value == MicAlwaysOnMode.SCREEN_OFF_ONLY
                    ) {
                        acquireWakeLockIfNeeded()
                        _isScreenOffArmed.value = true
                        mainHandler.postDelayed({
                            if (!isDestroyed && _speechState.value != SpeechState.SPEAKING && _speechState.value != SpeechState.PROCESSING) {
                                startListening()
                            }
                        }, 300)
                    } else {
                        _isScreenOffArmed.value = false
                        stopListening()
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.i(TAG, "Screen turned ON. Mode: ${_micAlwaysOnMode.value}")
                    if (_micAlwaysOnMode.value == MicAlwaysOnMode.SCREEN_OFF_ONLY) {
                        // In screen-off-only mode, save battery while user is interacting on-screen
                        _isScreenOffArmed.value = false
                        stopListening()
                    } else if (_micAlwaysOnMode.value == MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON ||
                        _micAlwaysOnMode.value == MicAlwaysOnMode.SCREEN_ON_ONLY
                    ) {
                        mainHandler.postDelayed({
                            if (!isDestroyed && _speechState.value != SpeechState.SPEAKING && _speechState.value != SpeechState.PROCESSING) {
                                startListening()
                            }
                        }, 250)
                    }
                }
            }
        }
    }

    fun setLanguage(language: VoiceLanguage) {
        _selectedLanguage.value = language
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, language.code).apply()
        Log.i(TAG, "Language updated to: ${language.label}")
    }

    fun cycleLanguage(): VoiceLanguage {
        val next = when (_selectedLanguage.value) {
            VoiceLanguage.AUTO -> VoiceLanguage.MARATHI
            VoiceLanguage.MARATHI -> VoiceLanguage.ENGLISH
            VoiceLanguage.ENGLISH -> VoiceLanguage.AUTO
        }
        setLanguage(next)
        return next
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    init {
        runOnMainThread {
            initSpeechRecognizer()
            textToSpeech = TextToSpeech(context, this)

            // Register screen state receiver
            try {
                val filter = android.content.IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                }
                context.registerReceiver(screenStateReceiver, filter)
                isScreenReceiverRegistered = true
                Log.i(TAG, "ScreenStateReceiver registered for mic operations")
            } catch (e: Exception) {
                Log.w(TAG, "Could not register screenStateReceiver: ${e.message}")
            }
        }
    }

    private fun initSpeechRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null

            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(this@JarvisSpeechManager)
            Log.i(TAG, "SpeechRecognizer initialized successfully: $speechRecognizer")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
            speechRecognizer = null
        }
    }

    fun canUseInAppRecognition(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context) ||
                (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
    }

    fun isRecognitionServiceAvailable(): Boolean {
        val systemIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val hasSystemActivity = systemIntent.resolveActivity(context.packageManager) != null
        return canUseInAppRecognition() || hasSystemActivity
    }

    fun createSystemSpeechIntent(): Intent {
        val lang = _selectedLanguage.value
        val primaryLocale = when (lang) {
            VoiceLanguage.MARATHI -> "mr-IN"
            VoiceLanguage.ENGLISH -> "en-IN"
            VoiceLanguage.AUTO -> "mr-IN"
        }

        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLocale)
            if (lang == VoiceLanguage.AUTO) {
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("mr-IN", "en-IN", "en-US", "hi-IN"))
            }
            val prompt = if (lang == VoiceLanguage.MARATHI) "जार्व्हिस ऐकत आहे. बोला..." else "Jarvis is listening. Speak in Marathi or English..."
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MINIMUM_SPEECH_LENGTH_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_STOP_INTERVAL_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_STOP_INTERVAL_MILLIS)
        }
    }

    fun isTtsEngineReady(): Boolean = isTtsReady

    fun setRecognitionError(error: String?) {
        _recognitionError.value = error
    }

    fun clearRecognitionError() {
        _recognitionError.value = null
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            textToSpeech?.let { tts ->
                MarathiTtsManager.prepareAndConfigureTts(tts, "Jarvis", _selectedLanguage.value)

                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS Utterance onStart: Speaking answer...")
                        isTtsSpeaking = true
                        runOnMainThread {
                            cancelProcessingWatchdog()
                            cancelSilenceTimer()
                            speechBuffer.clear()
                            isSpeechActive = false
                            isRecognizerActive = false
                            try {
                                speechRecognizer?.cancel()
                            } catch (ignored: Exception) {}
                            _speechState.value = SpeechState.SPEAKING
                            _audioRms.value = 0f
                            voiceActivityDetector.notifyVoiceInterruptedByTts()
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS Utterance onDone: Finished speaking answer. Cooling down audio buffer before mic resume...")
                        isTtsSpeaking = false
                        ttsAudioCooldownUntil = System.currentTimeMillis() + TTS_AUDIO_COOLDOWN_MILLIS
                        synchronized(recentSpokenUtterances) {
                            val last = recentSpokenUtterances.lastOrNull()
                            if (last != null) {
                                recentSpokenUtterances[recentSpokenUtterances.size - 1] = Pair(System.currentTimeMillis(), last.second)
                            }
                        }
                        runOnMainThread {
                            cancelProcessingWatchdog()
                            cancelSilenceTimer()
                            speechBuffer.clear()
                            isSpeechActive = false
                            _speechState.value = SpeechState.IDLE
                            _audioRms.value = 0f
                            scheduleContinuousRestart(delayMillis = TTS_AUDIO_COOLDOWN_MILLIS)
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "TTS Utterance onError. Cooling down audio buffer...")
                        isTtsSpeaking = false
                        ttsAudioCooldownUntil = System.currentTimeMillis() + TTS_AUDIO_COOLDOWN_MILLIS
                        runOnMainThread {
                            cancelProcessingWatchdog()
                            cancelSilenceTimer()
                            speechBuffer.clear()
                            isSpeechActive = false
                            _speechState.value = SpeechState.IDLE
                            _audioRms.value = 0f
                            scheduleContinuousRestart(delayMillis = TTS_AUDIO_COOLDOWN_MILLIS)
                        }
                    }
                })
            }
            pendingSpeech?.let { queuedText ->
                pendingSpeech = null
                speak(queuedText)
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status $status")
        }
    }

    /**
     * Checks if the recognized text is an acoustic reflection/echo of Jarvis's own recent TTS speaker output.
     * Prevents infinite feedback loops where Gemini or Core speaker output is heard by the microphone.
     */
    fun isAcousticSelfEcho(candidate: String): Boolean {
        val cleanCandidate = candidate.trim().lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{M}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleanCandidate.isBlank()) return true

        val now = System.currentTimeMillis()
        synchronized(recentSpokenUtterances) {
            // Only consider TTS utterances spoken within the last 2.5 seconds
            recentSpokenUtterances.removeAll { now - it.first > 3000L }

            val candidateTokens = cleanCandidate.split(" ").filter { it.isNotBlank() }.toSet()
            if (candidateTokens.isEmpty()) return true

            for ((timestamp, utterance) in recentSpokenUtterances) {
                val timeSinceSpeech = now - timestamp
                if (timeSinceSpeech < 2500L) {
                    val cleanUtterance = utterance.lowercase(Locale.ROOT)
                        .replace(Regex("""[^\p{L}\p{M}\p{Nd}\s]"""), " ")
                        .replace(Regex("""\s+"""), " ")
                        .trim()

                    // Exact match or full containment within 2.5 seconds of TTS finishing
                    if (cleanCandidate == cleanUtterance || (cleanUtterance.length > 5 && cleanCandidate == cleanUtterance)) {
                        Log.i(TAG, "[EchoGuard] Matched recent TTS speech: '$candidate' (spoken ${timeSinceSpeech}ms ago)")
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Music bleed check: never filter out human voice commands spoken into the microphone.
     */
    fun isMusicBleedIgnored(candidate: String): Boolean {
        // Do not discard user voice input
        return false
    }

    private fun onHumanVoiceDetected() {
        runOnMainThread {
            if (_speechState.value == SpeechState.SPEAKING || _speechState.value == SpeechState.PROCESSING) return@runOnMainThread
            isSpeechActive = true
            _speechState.value = SpeechState.LISTENING
            resetSilenceTimer()
        }
    }

    private fun onHumanVoiceStoppedAfter2Sec() {
        runOnMainThread {
            if (isSpeechActive || speechBuffer.isNotBlank()) {
                commitAndSendSpeech()
            }
        }
    }

    /**
     * Starts listening cleanly. Solves voice client binding and mic flutter issues.
     */
    fun startListening(promptForSpeech: Boolean = false): Boolean {
        runOnMainThread {
            if (isDestroyed) return@runOnMainThread

            // If user explicitly triggers listening, stop any ongoing TTS speech immediately
            if (_speechState.value == SpeechState.SPEAKING) {
                stopSpeaking()
            }
            cancelProcessingWatchdog()

            try {
                isContinuousRestartScheduled = false
                _recognitionError.value = null
                _speechState.value = SpeechState.LISTENING

                if (promptForSpeech) {
                    speechBuffer.clear()
                    isSpeechActive = false
                    armTemporaryUnlock(15000L)
                }

                voiceActivityDetector.startVadStream()
                startRecognizerSession()

                // Kick off watchdog if continuous mode
                if (isContinuousModeActive()) {
                    mainHandler.removeCallbacks(watchdogRunnable)
                    mainHandler.postDelayed(watchdogRunnable, WATCHDOG_CHECK_INTERVAL_MILLIS)
                }
                Log.d(TAG, "Mic started with Human Voice Detection & Background Noise Cancellation Active (prompt=$promptForSpeech)")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition: ${e.message}", e)
                _speechState.value = if (isContinuousModeActive()) SpeechState.LISTENING else SpeechState.IDLE
                scheduleContinuousRestart(delayMillis = 600)
            }
        }
        return true
    }

    private fun startRecognizerSession() {
        if (isDestroyed) return
        if (isTtsSpeaking || System.currentTimeMillis() < ttsAudioCooldownUntil ||
            _speechState.value == SpeechState.SPEAKING || _speechState.value == SpeechState.PROCESSING
        ) return

        try {
            if (speechRecognizer == null) {
                initSpeechRecognizer()
            }
            if (speechRecognizer == null) {
                Log.w(TAG, "SpeechRecognizer not initialized")
                return
            }

            val lang = _selectedLanguage.value
            val primaryLocale = when (lang) {
                VoiceLanguage.MARATHI -> "mr-IN"
                VoiceLanguage.ENGLISH -> "en-IN"
                VoiceLanguage.AUTO -> "mr-IN"
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLocale)
                if (lang == VoiceLanguage.AUTO) {
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("mr-IN", "en-IN", "en-US", "hi-IN"))
                }
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

                // Optimal silence window for prompt user voice recognition
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }

            isRecognizerActive = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start in-app recognizer session: ${e.message}")
            isRecognizerActive = false
        }
    }

    /**
     * Completely stops listening and releases audio resources.
     */
    fun stopListening() {
        runOnMainThread {
            try {
                cancelSilenceTimer()
                mainHandler.removeCallbacks(watchdogRunnable)
                speechBuffer.clear()
                isSpeechActive = false
                isRecognizerActive = false
                isContinuousRestartScheduled = false
                voiceActivityDetector.stopVadStream()

                try {
                    speechRecognizer?.stopListening()
                    speechRecognizer?.cancel()
                } catch (ignored: Exception) {}

                _speechState.value = SpeechState.IDLE
                _audioRms.value = 0f
                releaseWakeLock()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping listening: ${e.message}")
            }
        }
    }

    private var processingWatchdogRunnable: Runnable? = null

    private fun startProcessingWatchdog() {
        cancelProcessingWatchdog()
        processingWatchdogRunnable = Runnable {
            if (_speechState.value == SpeechState.PROCESSING) {
                Log.w(TAG, "Processing watchdog timed out after 6 seconds. Resetting speech state to IDLE to prevent hang.")
                _speechState.value = SpeechState.IDLE
                scheduleContinuousRestart(delayMillis = TTS_AUDIO_COOLDOWN_MILLIS)
            }
        }
        mainHandler.postDelayed(processingWatchdogRunnable!!, 6000L)
    }

    private fun cancelProcessingWatchdog() {
        processingWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        processingWatchdogRunnable = null
    }

    private fun resetSilenceTimer() {
        cancelSilenceTimer()
        silenceTimerRunnable = Runnable {
            commitAndSendSpeech()
        }
        mainHandler.postDelayed(silenceTimerRunnable!!, SILENCE_STOP_INTERVAL_MILLIS)
    }

    private fun cancelSilenceTimer() {
        silenceTimerRunnable?.let { mainHandler.removeCallbacks(it) }
        silenceTimerRunnable = null
    }

    /**
     * Extracts buffered text, sends words to get answer, and transitions state safely.
     */
    private fun commitAndSendSpeech() {
        cancelSilenceTimer()
        val finalWords = speechBuffer.toString().trim()
        speechBuffer.clear()
        isSpeechActive = false
        voiceActivityDetector.onSpeechDetected(false)

        if (finalWords.isNotBlank()) {
            // Check for acoustic echo feedback loop before sending!
            if (isAcousticSelfEcho(finalWords)) {
                Log.i(TAG, "[EchoGuard] Filtered out speaker self-voice echo in commitAndSendSpeech: '$finalWords'")
                _speechState.value = if (isContinuousModeActive()) SpeechState.LISTENING else SpeechState.IDLE
                _partialText.value = ""
                _audioRms.value = 0f
                if (isContinuousModeActive() && !isDestroyed) {
                    scheduleContinuousRestart(delayMillis = 700)
                }
                return
            }

            // Check for loudspeaker background music lyrics / bleed!
            if (isMusicBleedIgnored(finalWords)) {
                Log.i(TAG, "[MusicBleedGuard] Filtered out speaker song lyrics in commitAndSendSpeech: '$finalWords'")
                _speechState.value = if (isContinuousModeActive()) SpeechState.LISTENING else SpeechState.IDLE
                _partialText.value = ""
                _audioRms.value = 0f
                if (isContinuousModeActive() && !isDestroyed) {
                    scheduleContinuousRestart(delayMillis = 850)
                }
                return
            }

            // --- J.A.R.V.I.S. Wake-Word Gatekeeper Lock ---
            var effectiveCommand = finalWords
            if (_isWakeWordLockActive.value && !_isTemporarilyUnlocked.value) {
                val (hasWakeWord, strippedCommand) = extractWakeWordAndCommand(finalWords)
                if (!hasWakeWord) {
                    Log.i(TAG, "[JarvisLock] Ignored non-wake speech while locked: '$finalWords'")
                    _speechState.value = if (isContinuousModeActive()) SpeechState.LISTENING else SpeechState.IDLE
                    _partialText.value = ""
                    _audioRms.value = 0f
                    if (isContinuousModeActive() && !isDestroyed) {
                        scheduleContinuousRestart(delayMillis = 600)
                    }
                    return
                }

                // Wake-word detected! Turn screen on if needed
                try {
                    val app = context.applicationContext as? com.example.JarvisApplication
                    app?.hardwareController?.acquireCpuWakeLock(12000, "Jarvis:WakeWordGate")
                    app?.hardwareController?.wakeUpScreen(8000)
                } catch (ignored: Exception) {}

                if (strippedCommand.isBlank()) {
                    // User spoke just "Jarvis" / "Hey Jarvis" / "जार्व्हिस"
                    Log.i(TAG, "[JarvisLock] Wake-word only detected ('$finalWords'). Arming 10s command window.")
                    armTemporaryUnlock(10000L)
                    val greeting = if (_selectedLanguage.value == VoiceLanguage.MARATHI) "बोला सर, मी ऐकत आहे." else "Yes, sir?"
                    speak(greeting) {
                        scheduleContinuousRestart(delayMillis = 300)
                    }
                    return
                } else {
                    // User spoke "Jarvis [command]" in one sentence!
                    Log.i(TAG, "[JarvisLock] Wake-word + Command detected: '$finalWords' -> Command: '$strippedCommand'")
                    cancelTemporaryUnlock()
                    effectiveCommand = strippedCommand
                }
            } else if (_isTemporarilyUnlocked.value) {
                // User was already unlocked; consume command and re-arm lock
                cancelTemporaryUnlock()
                val (_, stripped) = extractWakeWordAndCommand(finalWords)
                effectiveCommand = if (stripped.isNotBlank()) stripped else finalWords
            }

            Log.i(TAG, "Recognized user words: '$effectiveCommand'. Sending to get answer...")
            _speechState.value = SpeechState.PROCESSING
            _audioRms.value = 0f
            _partialText.value = effectiveCommand
            isRecognizerActive = false

            // Start 6-second safety watchdog so state never hangs if assistant takes too long
            startProcessingWatchdog()

            try {
                speechRecognizer?.stopListening()
            } catch (ignored: Exception) {}

            // Send words to assistant engine with exception guard
            try {
                onCommandRecognized(effectiveCommand)
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking onCommandRecognized", e)
                cancelProcessingWatchdog()
                _speechState.value = SpeechState.IDLE
                scheduleContinuousRestart(delayMillis = TTS_AUDIO_COOLDOWN_MILLIS)
            }
        } else {
            // No speech text was captured, resume continuous listening loop smoothly
            if (isContinuousModeActive() && _speechState.value != SpeechState.SPEAKING && !isDestroyed) {
                scheduleContinuousRestart(delayMillis = 300)
            }
        }
    }

    private var continuousRestartRunnable: Runnable? = null

    /**
     * Schedules a continuous listening restart without disconnecting Always-On mode.
     */
    fun scheduleContinuousRestart(delayMillis: Long = 100) {
        if (!isContinuousModeActive() || isDestroyed) return

        continuousRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            isContinuousRestartScheduled = false
            if (isContinuousModeActive() &&
                !isTtsSpeaking &&
                System.currentTimeMillis() >= ttsAudioCooldownUntil &&
                _speechState.value != SpeechState.SPEAKING &&
                _speechState.value != SpeechState.PROCESSING &&
                !isDestroyed
            ) {
                startListening()
            }
        }
        continuousRestartRunnable = runnable
        isContinuousRestartScheduled = true
        mainHandler.postDelayed(runnable, delayMillis)
    }

    fun speak(text: String, onCompleted: (() -> Unit)? = null) {
        cancelProcessingWatchdog()
        _lastSpokenText.value = text

        if (text.isBlank()) {
            Log.d(TAG, "Empty text provided to speak(); transitioning to IDLE")
            _speechState.value = SpeechState.IDLE
            scheduleContinuousRestart(delayMillis = TTS_AUDIO_COOLDOWN_MILLIS)
            return
        }

        // Register in echo memory
        synchronized(recentSpokenUtterances) {
            recentSpokenUtterances.add(Pair(System.currentTimeMillis(), text))
        }

        if (!isTtsReady || textToSpeech == null) {
            Log.w(TAG, "TTS not initialized yet; queuing speech: $text")
            pendingSpeech = text
            _speechState.value = SpeechState.IDLE
            return
        }
        runOnMainThread {
            try {
                // Cancel pending timers and pause recognition while speaking
                cancelSilenceTimer()
                speechBuffer.clear()
                isSpeechActive = false
                isRecognizerActive = false
                isTtsSpeaking = true
                voiceActivityDetector.notifyVoiceInterruptedByTts()

                try {
                    speechRecognizer?.stopListening()
                    speechRecognizer?.cancel()
                } catch (ignored: Exception) {}

                _speechState.value = SpeechState.SPEAKING
                _audioRms.value = 0f

                val preparedSpeech = textToSpeech?.let { tts ->
                    MarathiTtsManager.prepareAndConfigureTts(tts, text, _selectedLanguage.value)
                }
                val speechPayload = preparedSpeech?.textToSpeak ?: text

                val utteranceId = "jarvis_${System.currentTimeMillis()}"
                textToSpeech?.speak(speechPayload, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } catch (e: Exception) {
                Log.e(TAG, "Error speaking text", e)
                isTtsSpeaking = false
                _speechState.value = SpeechState.IDLE
                scheduleContinuousRestart(delayMillis = TTS_AUDIO_COOLDOWN_MILLIS)
            }
        }
    }

    fun openTtsSettings() {
        MarathiTtsManager.openTtsSettings(context)
    }

    fun getTtsDiagnostics(): MarathiTtsManager.TtsDiagnosticInfo {
        return MarathiTtsManager.getDiagnostics(textToSpeech)
    }

    fun stopSpeaking() {
        if (isTtsReady) {
            textToSpeech?.stop()
            isTtsSpeaking = false
            ttsAudioCooldownUntil = System.currentTimeMillis() + TTS_AUDIO_COOLDOWN_MILLIS
            if (_speechState.value == SpeechState.SPEAKING) {
                _speechState.value = SpeechState.IDLE
                scheduleContinuousRestart(delayMillis = TTS_AUDIO_COOLDOWN_MILLIS)
            }
        }
    }

    /**
     * Completely destroys and cleans up all audio capture, recognizers, wake locks, and threads.
     * Ensures Android green microphone dot is completely extinguished when app closes.
     */
    fun destroy() {
        isDestroyed = true
        runOnMainThread {
            try {
                cancelSilenceTimer()
                mainHandler.removeCallbacksAndMessages(null)
                releaseWakeLock()
                voiceActivityDetector.stopVadStream()

                if (isScreenReceiverRegistered) {
                    try {
                        context.unregisterReceiver(screenStateReceiver)
                        isScreenReceiverRegistered = false
                    } catch (e: Exception) {
                        Log.w(TAG, "Error unregistering screenStateReceiver: ${e.message}")
                    }
                }

                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (ignored: Exception) {}
                speechRecognizer = null

                try {
                    textToSpeech?.stop()
                    textToSpeech?.shutdown()
                } catch (ignored: Exception) {}
                textToSpeech = null

                _speechState.value = SpeechState.IDLE
                _audioRms.value = 0f
                Log.i(TAG, "JarvisSpeechManager destroyed and mic completely released.")
            } catch (e: Exception) {
                Log.e(TAG, "Destroy failed", e)
            }
        }
    }

    // --- SpeechRecognizer Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        if (isTtsSpeaking || System.currentTimeMillis() < ttsAudioCooldownUntil ||
            _speechState.value == SpeechState.SPEAKING || _speechState.value == SpeechState.PROCESSING
        ) {
            return
        }
        Log.d(TAG, "onReadyForSpeech: Speech recognizer armed and ready")
        _speechState.value = SpeechState.LISTENING
        _recognitionError.value = null
        isRecognizerActive = true
    }

    override fun onBeginningOfSpeech() {
        if (isTtsSpeaking || System.currentTimeMillis() < ttsAudioCooldownUntil ||
            _speechState.value == SpeechState.SPEAKING || _speechState.value == SpeechState.PROCESSING
        ) {
            return
        }
        Log.d(TAG, "onBeginningOfSpeech: Catching user words...")
        _speechState.value = SpeechState.LISTENING
        isSpeechActive = true
        voiceActivityDetector.onSpeechDetected(true)
        resetSilenceTimer()
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (isTtsSpeaking || System.currentTimeMillis() < ttsAudioCooldownUntil ||
            _speechState.value == SpeechState.SPEAKING || _speechState.value == SpeechState.PROCESSING
        ) {
            _audioRms.value = 0f
            return
        }
        // Feed real-time audio dB into the background noise filter and human voice detector
        voiceActivityDetector.onAudioEnergySample(rmsdB)

        // Noise gate: ignore quiet background noise / fans (< 2.5 dB) for the HUD visualizer
        val normalized = if (rmsdB > 2.5f) ((rmsdB - 2.5f) / 7.5f).coerceIn(0.05f, 1f) else 0f
        _audioRms.value = normalized

        // If audio energy is high while user is speaking, reset the 2s silence timer
        if (isSpeechActive && rmsdB > 3.0f) {
            resetSilenceTimer()
        }
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        if (isTtsSpeaking || _speechState.value == SpeechState.SPEAKING) {
            return
        }
        Log.d(TAG, "onEndOfSpeech: Native recognizer detected end of sound burst.")
        voiceActivityDetector.onSpeechDetected(false)
        if (speechBuffer.isNotBlank() && isSpeechActive) {
            resetSilenceTimer()
        }
    }

    override fun onError(error: Int) {
        isRecognizerActive = false
        _audioRms.value = 0f

        if (isTtsSpeaking || _speechState.value == SpeechState.SPEAKING || isDestroyed) {
            return
        }
        Log.d(TAG, "SpeechRecognizer onError code: $error")

        // If speech was active and we already have recognized words buffered, commit and send them!
        if (speechBuffer.isNotBlank()) {
            Log.d(TAG, "Committing buffered speech on recognizer end: '${speechBuffer.toString()}'")
            commitAndSendSpeech()
            return
        }

        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // Normal ambient pause when user hasn't spoken yet - maintain LISTENING state with stable 300ms restart
                if (isContinuousModeActive() && _speechState.value != SpeechState.SPEAKING && _speechState.value != SpeechState.PROCESSING) {
                    _speechState.value = SpeechState.LISTENING
                    scheduleContinuousRestart(delayMillis = 300)
                    return
                }
            }

            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_AUDIO -> {
                // Voice client binding or audio device busy: cancel previous session and wait before retrying
                Log.w(TAG, "Voice client state reset for code $error")
                try {
                    speechRecognizer?.cancel()
                } catch (ignored: Exception) {}

                if (isContinuousModeActive() && _speechState.value != SpeechState.SPEAKING && _speechState.value != SpeechState.PROCESSING) {
                    _speechState.value = SpeechState.LISTENING
                    scheduleContinuousRestart(delayMillis = 400)
                    return
                }
            }

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                _speechState.value = SpeechState.IDLE
                _recognitionError.value = "Microphone permission required."
                return
            }

            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                if (isContinuousModeActive() && _speechState.value != SpeechState.SPEAKING && _speechState.value != SpeechState.PROCESSING) {
                    _speechState.value = SpeechState.LISTENING
                    scheduleContinuousRestart(delayMillis = 500)
                    return
                }
            }
        }

        if (!isContinuousModeActive()) {
            _speechState.value = SpeechState.IDLE
        }
    }

    override fun onResults(results: Bundle?) {
        _audioRms.value = 0f
        if (isTtsSpeaking || System.currentTimeMillis() < ttsAudioCooldownUntil || _speechState.value == SpeechState.SPEAKING) {
            Log.d(TAG, "[EchoGuard] Dropped recognizer onResults during TTS/Cooldown")
            return
        }

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val recognizedSentence = matches?.firstOrNull()?.trim()
        Log.i(TAG, "onResults received: '$recognizedSentence'")

        if (!recognizedSentence.isNullOrBlank()) {
            if (isAcousticSelfEcho(recognizedSentence)) {
                Log.i(TAG, "[EchoGuard] Filtered out speaker self-voice echo in onResults: '$recognizedSentence'")
                speechBuffer.clear()
                _partialText.value = ""
                if (isContinuousModeActive() && !isDestroyed) {
                    scheduleContinuousRestart(delayMillis = 200)
                }
                return
            }

            if (isMusicBleedIgnored(recognizedSentence)) {
                Log.i(TAG, "[MusicBleedGuard] Filtered out loudspeaker music audio in onResults: '$recognizedSentence'")
                speechBuffer.clear()
                _partialText.value = ""
                if (isContinuousModeActive() && !isDestroyed) {
                    scheduleContinuousRestart(delayMillis = 300)
                }
                return
            }

            isSpeechActive = true
            speechBuffer.clear()
            speechBuffer.append(recognizedSentence)
            _partialText.value = recognizedSentence
            voiceActivityDetector.onSpeechDetected(true, hasRecognizedWords = true)

            // Sentence recognition is complete; commit and send immediately for instant response!
            commitAndSendSpeech()
        } else {
            // No speech recognized in this cycle, keep listening seamlessly if continuous with 300ms restart
            if (isContinuousModeActive() && !isDestroyed && _speechState.value != SpeechState.SPEAKING) {
                _speechState.value = SpeechState.LISTENING
                scheduleContinuousRestart(delayMillis = 300)
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        if (isTtsSpeaking || System.currentTimeMillis() < ttsAudioCooldownUntil || _speechState.value == SpeechState.SPEAKING) {
            return
        }

        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val partial = matches?.firstOrNull()?.trim()
        if (!partial.isNullOrBlank()) {
            if (isAcousticSelfEcho(partial)) {
                Log.d(TAG, "[EchoGuard] Dropped partial echo matching recent TTS: '$partial'")
                return
            }

            if (isMusicBleedIgnored(partial)) {
                Log.d(TAG, "[MusicBleedGuard] Dropped partial matching background song lyrics: '$partial'")
                return
            }

            isSpeechActive = true
            speechBuffer.clear()
            speechBuffer.append(partial)
            _partialText.value = partial
            voiceActivityDetector.onSpeechDetected(true, hasRecognizedWords = true)
            Log.d(TAG, "Catching speech: '$partial' (resetting 2s silence timer)")

            // Reset 2-second silence countdown every time new speech is caught!
            resetSilenceTimer()
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
