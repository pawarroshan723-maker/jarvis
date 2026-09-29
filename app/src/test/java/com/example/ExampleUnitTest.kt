package com.example

import com.example.engine.OfflineKnowledgeEngine
import com.example.voice.VoiceLanguage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Local unit tests verifying Offline Knowledge Engine, conversation memory, and Marathi language support.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun verifyModernGeminiModelTiersAndAbsenceOfDeprecatedModels() {
        val tiers = com.example.engine.GeminiModelTier.ALL_AVAILABLE_MODELS
        assertTrue(tiers.any { it.modelId == "cascade_lite" })
        assertTrue(tiers.any { it.modelId == "cascade_flash" })
        assertTrue(tiers.any { it.modelId == "auto_cascade" })

        // Dynamic Aliases
        assertTrue(tiers.any { it.modelId == "gemini-flash-latest" })
        assertTrue(tiers.any { it.modelId == "gemini-flash-lite-latest" })

        // Gemini 3.x Flash
        assertTrue(tiers.any { it.modelId == "gemini-3.8-flash" })
        assertTrue(tiers.any { it.modelId == "gemini-3.7-flash" })
        assertTrue(tiers.any { it.modelId == "gemini-3.6-flash" })
        assertTrue(tiers.any { it.modelId == "gemini-3.5-flash" })
        assertTrue(tiers.any { it.modelId == "gemini-3-flash-preview" })

        // Gemini Flash-Lite
        assertTrue(tiers.any { it.modelId == "gemini-3.5-flash-lite" })
        assertTrue(tiers.any { it.modelId == "gemini-3.1-flash-lite" })

        // Gemini 2.x Series
        assertTrue(tiers.any { it.modelId == "gemini-2.5-flash" })

        assertEquals(3, com.example.engine.GeminiModelTier.CASCADES.size)
        assertEquals(10, com.example.engine.GeminiModelTier.INDIVIDUAL_MODELS.size)

        // Strictly verify NO deprecated 1.5 or 2.0 models exist
        assertFalse(tiers.any { it.modelId.contains("2.0") })
        assertFalse(tiers.any { it.modelId.contains("1.5") })
    }

    @Test
    fun verifyExtendedTriggerAndActionTypes() {
        assertTrue(com.example.data.TriggerTypes.ALL.any { it.first == com.example.data.TriggerTypes.BATTERY_FULL })
        assertTrue(com.example.data.TriggerTypes.ALL.any { it.first == com.example.data.TriggerTypes.ORIENTATION_UPRIGHT })
        assertTrue(com.example.data.TriggerTypes.ALL.any { it.first == com.example.data.TriggerTypes.CHARGER_DISCONNECTED })

        assertTrue(com.example.data.ActionTypes.ALL.any { it.first == com.example.data.ActionTypes.ANNOUNCE_BATTERY })
        assertTrue(com.example.data.ActionTypes.ALL.any { it.first == com.example.data.ActionTypes.ANNOUNCE_TIME })
        assertTrue(com.example.data.ActionTypes.ALL.any { it.first == com.example.data.ActionTypes.LAUNCH_CAMERA })
        assertTrue(com.example.data.ActionTypes.ALL.any { it.first == com.example.data.ActionTypes.FLASHLIGHT_ON })
        assertTrue(com.example.data.ActionTypes.ALL.any { it.first == com.example.data.ActionTypes.FLASHLIGHT_OFF })
        assertTrue(com.example.data.ActionTypes.ALL.any { it.first == com.example.data.ActionTypes.FLASHLIGHT_SOS })
    }

    @Test
    fun verifyTriggerConstants() {
        assertTrue(com.example.data.TriggerTypes.ALL.isNotEmpty())
        assertTrue(com.example.data.ActionTypes.ALL.isNotEmpty())
    }

    @Test
    fun verifyDiagnosticDataStructures() {
        val item = com.example.voice.DiagnosticItem(
            title = "Microphone Permission",
            isPassed = true,
            details = "Granted",
            actionLabel = null,
            actionType = null
        )
        assertTrue(item.isPassed)
        val report = com.example.voice.VoiceDiagnosticReport(
            items = listOf(item),
            allPassed = true,
            overallStatus = "ALL SYSTEMS OPERATIONAL"
        )
        assertTrue(report.allPassed)
    }

    @Test
    fun verifyOfflineKnowledgeEngineMathAndConversions() {
        val math = OfflineKnowledgeEngine.answerQuery("what is 25 times 4")
        assertTrue(math.handled)
        assertTrue(math.answer.contains("100"))

        val conversion = OfflineKnowledgeEngine.answerQuery("convert 100 celsius to fahrenheit")
        assertTrue(conversion.handled)
        assertTrue(conversion.answer.contains("212"))
    }

    @Test
    fun verifyMarathiSupportInKnowledgeEngine() {
        val mrMath = OfflineKnowledgeEngine.answerQuery("200 चे 10 टक्के")
        assertTrue(mrMath.handled)
        assertTrue(mrMath.answer.contains("20"))

        val mrFact = OfflineKnowledgeEngine.answerQuery("तू कोण आहेस")
        assertTrue(mrFact.handled)
        assertTrue(mrFact.answer.contains("जार्व्हिस"))

        val mrVoiceLang = VoiceLanguage.fromCode("mr")
        assertEquals(VoiceLanguage.MARATHI, mrVoiceLang)
    }

    @Test
    fun verifyMicAlwaysOnModeEnum() {
        val modes = com.example.voice.MicAlwaysOnMode.values()
        assertEquals(4, modes.size)
        assertTrue(modes.contains(com.example.voice.MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON))
        assertTrue(modes.contains(com.example.voice.MicAlwaysOnMode.SCREEN_OFF_ONLY))
        assertTrue(modes.contains(com.example.voice.MicAlwaysOnMode.SCREEN_ON_ONLY))
        assertTrue(modes.contains(com.example.voice.MicAlwaysOnMode.MANUAL))
    }

    @Test
    fun verifyAutoCallSmsConfigAndPresets() {
        val config = com.example.system.AutoCallSmsConfig()
        assertFalse(config.isAutoSmsEnabled) // Secure opt-in default
        assertTrue(config.isAutoReadSmsEnabled)
        assertTrue(config.isAutoAnnounceCallsEnabled)
        assertFalse(config.isAutoReplyMissedCallsEnabled) // Secure opt-in default
        assertEquals("NORMAL", config.activePreset)
        assertEquals(3, config.antiSpamCooldownMinutes)

        val drivingPreset = com.example.system.AutoCallSmsManager.PRESET_MESSAGES["DRIVING"]
        assertNotNull(drivingPreset)
        assertTrue(drivingPreset!!.contains("driving"))

        val meetingPreset = com.example.system.AutoCallSmsManager.PRESET_MESSAGES["MEETING"]
        assertNotNull(meetingPreset)
        assertTrue(meetingPreset!!.contains("meeting"))
    }

    @Test
    fun verifySpeechSilenceIntervalIsTwoSeconds() {
        assertEquals(2000L, com.example.voice.JarvisSpeechManager.SILENCE_STOP_INTERVAL_MILLIS)
        assertEquals(1500L, com.example.voice.JarvisSpeechManager.MINIMUM_SPEECH_LENGTH_MILLIS)
    }

    @Test
    fun verifyConversationMemoryMultiTurnAndLocationContext() {
        val memory = com.example.engine.ConversationMemory()

        // Turn 1: User asks "todays whether in akola"
        val query1 = memory.normalizeAndEnrichQuery("todays whether in akola")
        assertEquals("todays weather in akola", query1)
        assertEquals("Akola", memory.activeLocation)
        assertEquals("Weather", memory.activeTopic)

        // Gemini replies with Akola weather
        memory.addTurn("user", query1)
        memory.addTurn("model", "In Akola today, it is sunny with a temperature of 34°C.")

        // Verify history stored
        val history = memory.getHistory()
        assertEquals(2, history.size)
        assertEquals("user", history[0].role)
        assertEquals("model", history[1].role)

        // Turn 2: User asks follow up "full whether"
        // Should normalize "whether" -> "weather" and enrich with the stored "Akola" location!
        val query2 = memory.normalizeAndEnrichQuery("full whether")
        assertEquals("Full weather forecast for Akola", query2)

        // Verify payload JSON contents structure
        val contentsJson = memory.buildContentsJsonArray(query2)
        assertEquals(3, contentsJson.length())
        assertEquals("user", contentsJson.getJSONObject(0).getString("role"))
        assertEquals("model", contentsJson.getJSONObject(1).getString("role"))
        assertEquals("user", contentsJson.getJSONObject(2).getString("role"))
        assertEquals(
            "Full weather forecast for Akola",
            contentsJson.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text")
        )

        // Verify contextual system instruction contains Akola directive
        val sysInstruction = memory.buildContextualSystemInstruction()
        assertTrue(sysInstruction.contains("Akola"))

        // Reset memory
        memory.clearHistory()
        assertNull(memory.activeLocation)
        assertTrue(memory.getHistory().isEmpty())
    }

    @Test
    fun verifyUniversalConversationMemoryForAllQuestionTypes() {
        val memory = com.example.engine.ConversationMemory()

        // 1. General Science / Biography: "Who is Nikola Tesla?"
        val q1 = memory.normalizeAndEnrichQuery("Who is Nikola Tesla?")
        assertEquals("Nikola Tesla", memory.activeSubject)
        assertEquals("CTX: NIKOLA TESLA", memory.getActiveContextSummary())

        memory.addTurn("user", q1)
        memory.addTurn("model", "Nikola Tesla was a renowned inventor, electrical engineer, and futurist.")

        // 2. Pronoun follow up: "When was he born?"
        // Subject MUST NOT be overwritten by pronoun question
        val q2 = memory.normalizeAndEnrichQuery("When was he born?")
        assertEquals("Nikola Tesla", memory.activeSubject)

        // Verify multi-turn alternating structure in API payload
        val jsonPayload = memory.buildContentsJsonArray(q2)
        assertEquals(3, jsonPayload.length())
        assertEquals("user", jsonPayload.getJSONObject(0).getString("role"))
        assertEquals("model", jsonPayload.getJSONObject(1).getString("role"))
        assertEquals("user", jsonPayload.getJSONObject(2).getString("role"))
        assertEquals("When was he born?", jsonPayload.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text"))

        memory.addTurn("user", q2)
        memory.addTurn("model", "He was born on July 10, 1856 in Smiljan, Austrian Empire.")

        // 3. Elliptical follow-up: "tell me more"
        val q3 = memory.normalizeAndEnrichQuery("tell me more")
        assertEquals("Tell me more about Nikola Tesla in detail.", q3)
        assertEquals("Nikola Tesla", memory.activeSubject)

        // 4. Language switch follow-up: "मराठीत सांगा"
        val q4 = memory.normalizeAndEnrichQuery("मराठीत सांगा")
        assertTrue(q4.contains("Nikola Tesla"))
        assertTrue(q4.contains("मराठीमध्ये"))

        // 5. System instruction validation
        val prompt = memory.buildContextualSystemInstruction()
        assertTrue(prompt.contains("UNIVERSAL CONVERSATION MEMORY"))
        assertTrue(prompt.contains("Nikola Tesla"))

        // 6. Reset
        memory.clearHistory()
        assertNull(memory.activeSubject)
        assertNull(memory.getActiveContextSummary())
    }

    @Test
    fun verifyJokesShiftedToOnlineIntelligence() {
        // Jokes are removed from offline database so they are generated dynamically online via Gemini
        val joke1 = OfflineKnowledgeEngine.answerQuery("tell me a joke")
        assertFalse(joke1.handled)

        val mrJoke = OfflineKnowledgeEngine.answerQuery("जोक सांग")
        assertFalse(mrJoke.handled)

        val hiJoke = OfflineKnowledgeEngine.answerQuery("हिंदी चुटकुला सुनाओ")
        assertFalse(hiJoke.handled)
    }

    @Test
    fun verifyExtendedOfflineMathAndConversions() {
        // Average
        val avg = OfflineKnowledgeEngine.answerQuery("average of 10, 20, 30, 40")
        assertTrue(avg.handled)
        assertTrue(avg.answer.contains("25"))

        // Factorial
        val fact = OfflineKnowledgeEngine.answerQuery("5 factorial")
        assertTrue(fact.handled)
        assertTrue(fact.answer.contains("120"))

        // Trigonometry
        val trig = OfflineKnowledgeEngine.answerQuery("sin 30")
        assertTrue(trig.handled)
        assertTrue(trig.answer.contains("0.5"))

        // Logarithm
        val log = OfflineKnowledgeEngine.answerQuery("log 100")
        assertTrue(log.handled)
        assertTrue(log.answer.contains("2"))

        // Modulo
        val mod = OfflineKnowledgeEngine.answerQuery("17 mod 5")
        assertTrue(mod.handled)
        assertTrue(mod.answer.contains("2"))

        // Storage Conversion
        val storage = OfflineKnowledgeEngine.answerQuery("16 gb to mb")
        assertTrue(storage.handled)
        assertTrue(storage.answer.contains("16384"))

        // Distance Conversion
        val dist = OfflineKnowledgeEngine.answerQuery("5 meters to feet")
        assertTrue(dist.handled)
        assertTrue(dist.answer.contains("16.4"))

        // Capital Fact
        val capital = OfflineKnowledgeEngine.answerQuery("capital of japan")
        assertTrue(capital.handled)
        assertTrue(capital.answer.contains("Tokyo") || capital.answer.contains("टोकियो"))

        // Multi-Step Infix Math Evaluation
        val infixMath = OfflineKnowledgeEngine.answerQuery("10 + 20 * 5")
        assertTrue(infixMath.handled)
        assertTrue(infixMath.answer.contains("110"))

        val parenMath = OfflineKnowledgeEngine.answerQuery("(50 + 20) / 2")
        assertTrue(parenMath.handled)
        assertTrue(parenMath.answer.contains("35"))

        // Emergency & First Aid
        val snakeBite = OfflineKnowledgeEngine.answerQuery("snake bite first aid")
        assertTrue(snakeBite.handled)
        assertTrue(snakeBite.answer.contains("रुग्णालयात") || snakeBite.answer.contains("प्रथमोपचार"))

        // Historical & National symbols
        val sambhaji = OfflineKnowledgeEngine.answerQuery("sambhaji maharaj")
        assertTrue(sambhaji.handled)
        assertTrue(sambhaji.answer.contains("संभाजी महाराज") || sambhaji.answer.contains("छत्रपती"))
    }

    @Test
    fun verifySpeechNormalizationForTimeAndNumbers() {
        // English time: 12:30 PM should normalize to "twelve thirty PM"
        val enTime = com.example.voice.MarathiTtsManager.normalizeTextForSpeech("The time is 12:30 PM, sir.", isMarathi = false)
        assertTrue(enTime.contains("twelve thirty PM"))
        assertFalse(enTime.contains("12:30"))

        // English time: 1:05 AM should normalize to "one oh five AM"
        val enTime2 = com.example.voice.MarathiTtsManager.normalizeTextForSpeech("It is 1:05 AM.", isMarathi = false)
        assertTrue(enTime2.contains("one oh five AM"))

        // Marathi time: 12:30 PM should convert colons to natural Marathi spoken words
        val mrTime = com.example.voice.MarathiTtsManager.normalizeTextForSpeech("सध्याची वेळ 12:30 PM झाली आहे.", isMarathi = true)
        assertTrue(mrTime.contains("बारा वाजून तीस मिनिटे"))
        assertFalse(mrTime.contains("12:30"))

        // Percentages: 80% should normalize to "80 percent" in English and "80 टक्के" in Marathi
        val enPercent = com.example.voice.MarathiTtsManager.normalizeTextForSpeech("Battery is 80%", isMarathi = false)
        assertTrue(enPercent.contains("80 percent"))

        val mrPercent = com.example.voice.MarathiTtsManager.normalizeTextForSpeech("बॅटरी 80%", isMarathi = true)
        assertTrue(mrPercent.contains("80 टक्के"))

        // Colons in status messages should be converted to commas
        val statusText = com.example.voice.MarathiTtsManager.normalizeTextForSpeech("System status: Battery is OK", isMarathi = false)
        assertTrue(statusText.contains("System status, Battery is OK"))
        assertFalse(statusText.contains("System status:"))
    }

    @Test
    fun verifyQuotationDiversityAndCommandExecution() {
        // Quotation queries in English
        val q1 = OfflineKnowledgeEngine.answerQuery("give me a quote")
        assertTrue(q1.handled)
        assertTrue(q1.answer.isNotBlank())

        val q2 = OfflineKnowledgeEngine.answerQuery("inspire me")
        assertTrue(q2.handled)
        assertTrue(q2.answer.isNotBlank())

        // Marathi Suvichar
        val mrQuote1 = OfflineKnowledgeEngine.answerQuery("आजचा सुविचार सांगा")
        assertTrue(mrQuote1.handled)
        assertTrue(mrQuote1.answer.isNotBlank())

        val mrQuote2 = OfflineKnowledgeEngine.answerQuery("प्रेरणादायी विचार")
        assertTrue(mrQuote2.handled)
        assertTrue(mrQuote2.answer.isNotBlank())
    }

    @Test
    fun verifyWakeWordExtractionPhonetics() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val speechManager = com.example.voice.JarvisSpeechManager(context) {}

        // English wake words
        val (wake1, cmd1) = speechManager.extractWakeWordAndCommand("Jarvis turn on flashlight")
        assertTrue(wake1)
        assertEquals("turn on flashlight", cmd1)

        val (wake2, cmd2) = speechManager.extractWakeWordAndCommand("hey jarvis what time is it")
        assertTrue(wake2)
        assertEquals("what time is it", cmd2)

        val (wake3, cmd3) = speechManager.extractWakeWordAndCommand("ok jarvis")
        assertTrue(wake3)
        assertEquals("", cmd3)

        // Marathi phonetic wake words
        val (wake4, cmd4) = speechManager.extractWakeWordAndCommand("जार्व्हिस टॉर्च चालू करा")
        assertTrue(wake4)
        assertEquals("टॉर्च चालू करा", cmd4)

        val (wake5, cmd5) = speechManager.extractWakeWordAndCommand("हे जार्व्हिस वेळ सांगा")
        assertTrue(wake5)
        assertEquals("वेळ सांगा", cmd5)

        val (wake6, cmd6) = speechManager.extractWakeWordAndCommand("जार्विस गाणे लावा")
        assertTrue(wake6)
        assertEquals("गाणे लावा", cmd6)

        // Without wake word
        val (wake7, cmd7) = speechManager.extractWakeWordAndCommand("turn on flashlight")
        assertFalse(wake7)
        assertEquals("turn on flashlight", cmd7)
    }

    @Test
    fun verifyGeminiQuotaCircuitBreakerAndCooldowns() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = com.example.engine.GeminiAssistantEngine(context)

        // Initial state: no models in cooldown
        assertFalse(engine.isModelInCooldown("gemini-3.8-flash"))
        assertTrue(engine.exhaustedModelList.value.isEmpty())

        // Trigger circuit breaker for 3.8 flash
        engine.recordModelQuotaExhausted("gemini-3.8-flash", 10)
        assertTrue(engine.isModelInCooldown("gemini-3.8-flash"))
        // Dynamic alias should also be linked
        assertTrue(engine.isModelInCooldown("gemini-flash-latest"))
        // Other model buckets should remain free
        assertFalse(engine.isModelInCooldown("gemini-3.5-flash"))
        assertFalse(engine.isModelInCooldown("gemini-3.5-flash-lite"))

        assertTrue(engine.exhaustedModelList.value.contains("gemini-3.8-flash"))
        assertTrue(engine.exhaustedModelList.value.contains("gemini-flash-latest"))

        // Reset cooldowns
        engine.clearAllCooldowns()
        assertFalse(engine.isModelInCooldown("gemini-3.8-flash"))
        assertFalse(engine.isModelInCooldown("gemini-flash-latest"))
        assertTrue(engine.exhaustedModelList.value.isEmpty())
    }
}
