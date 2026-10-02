package com.example.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * ConversationMemory:
 * Preserves multi-turn conversation history ("back quotation" / context memory)
 * and active session parameters (such as the user's active subject, topic, and location).
 *
 * Supports ALL types of questions (general knowledge, coding, science, history,
 * recipes, math, sports, weather, Marathi queries) and ensures follow-ups
 * ("tell me more", "how old is he?", "what are the benefits?", "why?", "explain in Marathi")
 * seamlessly continue the conversation.
 */
class ConversationMemory(private val context: Context? = null) {

    data class DialogTurn(
        val role: String, // "user" or "model"
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val turns = mutableListOf<DialogTurn>()
    private val maxTurns = 16 // Retain up to 8 back-and-forth exchanges

    // Active conversational session context
    var activeLocation: String? = null
        private set
    var activeTopic: String? = null
        private set
    var activeSubject: String? = null
        private set

    companion object {
        private const val TAG = "ConversationMemory"
        private const val PREFS_NAME = "jarvis_context_memory"
        private const val KEY_LAST_LOCATION = "last_known_location"
        private const val KEY_LAST_TOPIC = "last_known_topic"
        private const val KEY_LAST_SUBJECT = "last_known_subject"
        private const val KEY_CONVERSATION_TURNS = "conversation_turns_json"

        // Common known locations in Maharashtra and India, plus global cities
        private val KNOWN_CITIES = listOf(
            "akola", "amravati", "nagpur", "pune", "mumbai", "nashik", "aurangabad",
            "chhatrapati sambhajinagar", "jalgaon", "nanded", "kolhapur", "solapur",
            "thane", "navi mumbai", "satara", "sangli", "wardha", "yavatmal", "buldhana",
            "washim", "chandrapur", "gondia", "bhandara", "gadchiroli", "dhule",
            "nandurbar", "delhi", "new delhi", "bengaluru", "bangalore", "hyderabad",
            "chennai", "kolkata", "ahmedabad", "surat", "jaipur", "lucknow", "bhopal",
            "indore", "patna", "chandigarh", "goa", "london", "new york", "tokyo", "paris"
        )
    }

    init {
        // Restore last known context & dialogue turns from SharedPreferences if available
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                activeLocation = prefs.getString(KEY_LAST_LOCATION, null)
                activeTopic = prefs.getString(KEY_LAST_TOPIC, null)
                activeSubject = prefs.getString(KEY_LAST_SUBJECT, null)
                loadTurnsFromDisk(prefs)
            } catch (e: Exception) {
                Log.w(TAG, "Error restoring context prefs", e)
            }
        }
    }

    private fun loadTurnsFromDisk(prefs: SharedPreferences) {
        val jsonStr = prefs.getString(KEY_CONVERSATION_TURNS, null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            turns.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val role = obj.optString("role", "")
                val text = obj.optString("text", "")
                val ts = obj.optLong("timestamp", System.currentTimeMillis())
                if (role.isNotEmpty() && text.isNotEmpty()) {
                    turns.add(DialogTurn(role, text, ts))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing persisted turns", e)
        }
    }

    private fun saveTurnsToDisk() {
        if (context == null) return
        try {
            val jsonArray = JSONArray()
            for (turn in turns) {
                val obj = JSONObject()
                obj.put("role", turn.role)
                obj.put("text", turn.text)
                obj.put("timestamp", turn.timestamp)
                jsonArray.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CONVERSATION_TURNS, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error saving turns to disk", e)
        }
    }

    @Synchronized
    fun setActiveLocation(location: String) {
        val cleanLoc = location.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        activeLocation = cleanLoc
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_LAST_LOCATION, cleanLoc)?.apply()
    }

    @Synchronized
    fun setActiveTopic(topic: String) {
        activeTopic = topic.trim()
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_LAST_TOPIC, activeTopic)?.apply()
    }

    @Synchronized
    fun setActiveSubject(subject: String) {
        val clean = subject.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        activeSubject = clean
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_LAST_SUBJECT, clean)?.apply()
    }

    /**
     * Returns a concise summary badge string for the HUD (e.g. "LOC: AKOLA", "TOPIC: EINSTEIN", "MEMORY: 3 TURNS").
     */
    @Synchronized
    fun getActiveContextSummary(): String? {
        if (!activeLocation.isNullOrBlank()) {
            return "LOC: ${activeLocation?.uppercase(Locale.ROOT)}"
        }
        if (!activeSubject.isNullOrBlank()) {
            val truncated = activeSubject!!.take(16).uppercase(Locale.ROOT)
            return "CTX: $truncated"
        }
        if (!activeTopic.isNullOrBlank()) {
            return "TOPIC: ${activeTopic?.uppercase(Locale.ROOT)}"
        }
        if (turns.isNotEmpty()) {
            val pairs = turns.size / 2
            return if (pairs > 0) "MEMORY: $pairs TURNS" else "MEMORY: 1 TURN"
        }
        return null
    }

    /**
     * Normalizes common speech recognition transcription errors (e.g. homophones like "whether" -> "weather")
     * and enriches follow-up queries that depend on stored context memory across ANY topic.
     */
    fun normalizeAndEnrichQuery(rawQuery: String): String {
        var query = rawQuery.trim()
        val lower = query.lowercase(Locale.ROOT)

        // 1. Homophone normalization: only replace "whether" with "weather" in actual weather context or active weather topic
        if (lower.contains("whether")) {
            val isWeatherContext = lower.contains("forecast") || lower.contains("rain") ||
                    lower.contains("temperature") || lower.contains("climate") ||
                    lower.contains("whether in") || lower.contains("whether report") ||
                    lower.contains("whether today") || lower.contains("whether tomorrow") ||
                    lower.contains("full whether") || lower == "whether" || (activeTopic == "Weather")

            if (isWeatherContext) {
                query = query.replace("(?i)\\bwhether\\b".toRegex(), "weather")
            }
        }

        // 2. Extract geographic location if present
        extractLocationFromQuery(query)?.let { loc ->
            setActiveLocation(loc)
            setActiveTopic("Weather")
        }

        // 3. Extract focal subject from general queries (science, history, tech, people, cooking, etc.)
        val detectedSubject = extractSubjectFromQuery(query)
        if (detectedSubject != null) {
            setActiveSubject(detectedSubject)
            // Infer topic category
            val inferredTopic = inferTopicFromQuery(query, detectedSubject)
            setActiveTopic(inferredTopic)
        }

        // 4. Follow-up enrichment:
        // A. Weather follow-up enrichment
        val currentLoc = activeLocation
        if (!currentLoc.isNullOrBlank()) {
            val lowerUpdated = query.lowercase(Locale.ROOT)
            val lacksLocation = !lowerUpdated.contains(currentLoc.lowercase(Locale.ROOT))

            if (lacksLocation) {
                if (lowerUpdated.matches("(?i)^(full weather|full weather report|full forecast|forecast|tell full weather|what about weather)\\??$".toRegex())) {
                    query = "Full weather forecast for $currentLoc"
                } else if (lowerUpdated.matches("(?i)^(what about tomorrow|tomorrow|how about tomorrow|tomorrow's weather)\\??$".toRegex())) {
                    query = "What is tomorrow's weather in $currentLoc?"
                } else if (lowerUpdated.matches("(?i)^(is it going to rain|will it rain|rain forecast)\\??$".toRegex())) {
                    query = "Is it going to rain in $currentLoc?"
                }
            }
        }

        // B. General pronoun / elliptical follow-up enrichment
        val currentSubj = activeSubject
        if (!currentSubj.isNullOrBlank()) {
            val lowerUpdated = query.lowercase(Locale.ROOT)
            if (lowerUpdated.matches("(?i)^(tell me more|explain more|tell me more about it|explain further|give more details)\\??$".toRegex())) {
                query = "Tell me more about $currentSubj in detail."
            } else if (lowerUpdated.matches("(?i)^(explain in marathi|मराठीत सांगा|मराठी मध्ये सांगा|याचे मराठीत भाषांतर करा)\\??$".toRegex())) {
                query = "$currentSubj बद्दल मराठीमध्ये सविस्तर सांगा."
            }
        }

        return query
    }

    /**
     * Extracts the focal entity/subject from general questions.
     */
    private fun extractSubjectFromQuery(query: String): String? {
        val clean = query.trim().removeSuffix("?").removeSuffix(".").trim()
        val lower = clean.lowercase(Locale.ROOT)

        // Ignore generic continuation phrases and pronouns
        val isGenericContinuation = lower.matches(
            "(?i)^(tell me more|explain more|explain further|give details|why|why is that|how|how so|give examples?|give me 3 examples?|summarize|summarize it|summarize this|in detail|briefly|what else|continue|आणखी सांगा|सविस्तर सांगा|उदाहरणे द्या|स्पष्टीकरण द्या)\\??$".toRegex()
        )
        if (isGenericContinuation) return null

        val isPronounQuestion = lower.matches(
            "(?i)^(how old is (?:he|she)|when was (?:he|she|it) born|where is (?:it|he|she)|who was (?:his|her) wife|what is (?:his|her|its) (?:name|age|height|net worth)|how does (?:it|this) work|what are (?:its|their) (?:benefits|uses|side effects))\\??$".toRegex()
        )
        if (isPronounQuestion) return null

        // English question patterns
        val enPatterns = listOf(
            "(?i)^who (?:is|was|were)\\s+(.+)$".toRegex(),
            "(?i)^what (?:is|was|are)\\s+(?:a|an|the)?\\s*(.+)$".toRegex(),
            "(?i)^tell me about\\s+(.+)$".toRegex(),
            "(?i)^explain\\s+(.+)$".toRegex(),
            "(?i)^recipe for\\s+(.+)$".toRegex(),
            "(?i)^how to make\\s+(.+)$".toRegex(),
            "(?i)^history of\\s+(.+)$".toRegex(),
            "(?i)^how does\\s+(?:a|an|the)?\\s*(.+?)\\s+work$".toRegex(),
            "(?i)^do you know\\s+(.+)$".toRegex()
        )

        for (pat in enPatterns) {
            val match = pat.find(clean)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length in 2..50 && !candidate.lowercase().matches("^(it|he|she|this|that|they|these|those)$".toRegex())) {
                    return candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
            }
        }

        // Marathi question patterns
        val mrPatterns = listOf(
            "(.+?)\\s*(?:कोण आहे|कोण होते|कोण आहेत)$".toRegex(),
            "(.+?)\\s*(?:बद्दल सांगा|बद्दल माहिती द्या|ची माहिती सांगा|ची माहिती द्या)$".toRegex(),
            "(.+?)\\s*(?:म्हणजे काय|काय आहे|काय असते)$".toRegex(),
            "(.+?)\\s*(?:ची रेसिपी सांगा|कशी बनवायची|कसे बनवायचे)$".toRegex(),
            "(.+?)\\s*(?:चा इतिहास सांगा|चा इतिहास काय आहे)$".toRegex()
        )

        for (pat in mrPatterns) {
            val match = pat.find(clean)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length in 2..50 && candidate != "हे" && candidate != "ते" && candidate != "तो" && candidate != "ती") {
                    return candidate
                }
            }
        }

        return null
    }

    /**
     * Infers a high-level category/topic from the query text.
     */
    private fun inferTopicFromQuery(query: String, subject: String): String {
        val lower = query.lowercase(Locale.ROOT)
        return when {
            lower.contains("weather") || lower.contains("rain") || lower.contains("temperature") || lower.contains("हवामान") -> "Weather"
            lower.contains("recipe") || lower.contains("cook") || lower.contains("dish") || lower.contains("रेसिपी") -> "Culinary"
            lower.contains("code") || lower.contains("python") || lower.contains("kotlin") || lower.contains("java") || lower.contains("algorithm") || lower.contains("function") -> "Programming"
            lower.contains("cricket") || lower.contains("match") || lower.contains("score") || lower.contains("football") -> "Sports"
            lower.contains("history") || lower.contains("king") || lower.contains("war") || lower.contains("chhatrapati") || lower.contains("इतिहास") -> "History"
            lower.contains("quantum") || lower.contains("physics") || lower.contains("biology") || lower.contains("space") || lower.contains("science") -> "Science"
            else -> "General Knowledge"
        }
    }

    /**
     * Extract geographic location/city from user voice queries
     */
    private fun extractLocationFromQuery(query: String): String? {
        val lower = query.lowercase(Locale.ROOT)

        // Check against known cities
        for (city in KNOWN_CITIES) {
            if (lower.contains("\\b$city\\b".toRegex())) {
                return city.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }

        // Marathi city references (e.g. अकोल्यात, पुण्यात, मुंबईत, नागपुरात)
        if (query.contains("अकोला") || query.contains("अकोल्यात") || query.contains("अकोल्यामध्ये")) return "Akola"
        if (query.contains("पुणे") || query.contains("पुण्यात")) return "Pune"
        if (query.contains("मुंबई") || query.contains("मुंबईत")) return "Mumbai"
        if (query.contains("नागपूर") || query.contains("नागपुरात")) return "Nagpur"
        if (query.contains("अमरावती") || query.contains("अमरावतीत")) return "Amravati"
        if (query.contains("नाशिक") || query.contains("नाशिकमध्ये")) return "Nashik"

        // Pattern matching: "weather in <City>", "temperature in <City>", etc.
        val weatherPattern = "(?i)\\b(?:weather\\s+in|weather\\s+for|weather\\s+at|temperature\\s+in|temperature\\s+at|forecast\\s+for|forecast\\s+in|climate\\s+in)\\s+([A-Za-z]{3,20})\\b".toRegex()
        val match = weatherPattern.find(query)
        if (match != null) {
            val candidate = match.groupValues[1].trim()
            val candidateLower = candidate.lowercase(Locale.ROOT)
            val nonCities = setOf("the", "today", "tomorrow", "this", "my", "our", "full", "detail", "degrees", "celsius", "python", "kotlin", "java")
            if (!nonCities.contains(candidateLower) && candidate.length >= 3) {
                return candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }

        return null
    }

    @Synchronized
    fun addTurn(role: String, text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        turns.add(DialogTurn(role = role, text = cleanText))

        // Maintain sliding window
        while (turns.size > maxTurns) {
            turns.removeAt(0)
        }

        saveTurnsToDisk()
    }

    @Synchronized
    fun getHistory(): List<DialogTurn> {
        return turns.toList()
    }

    @Synchronized
    fun clearHistory() {
        turns.clear()
        activeLocation = null
        activeTopic = null
        activeSubject = null
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.clear()?.apply()
    }

    /**
     * Builds the JSON "contents" array for the Gemini REST API.
     * Enforces strict alternating sequence: user -> model -> user -> ... -> current user query.
     */
    @Synchronized
    fun buildContentsJsonArray(currentUserQuery: String): JSONArray {
        val contentsArray = JSONArray()

        // Build valid alternating sequence from history
        val validHistory = mutableListOf<DialogTurn>()
        var expectedRole = "user"

        for (turn in turns) {
            if (turn.role == expectedRole) {
                validHistory.add(turn)
                expectedRole = if (expectedRole == "user") "model" else "user"
            }
        }

        // If the history ends with a user turn, remove it because we'll append the currentUserQuery as the final user turn
        if (validHistory.isNotEmpty() && validHistory.last().role == "user") {
            validHistory.removeAt(validHistory.size - 1)
        }

        // Add valid historical turns
        for (turn in validHistory) {
            val contentObj = JSONObject()
            contentObj.put("role", turn.role)
            val partsArray = JSONArray()
            partsArray.put(JSONObject().put("text", turn.text))
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
        }

        // Append the current user query as the final turn
        val currentTurnObj = JSONObject()
        currentTurnObj.put("role", "user")
        val partsArray = JSONArray()
        partsArray.put(JSONObject().put("text", currentUserQuery))
        currentTurnObj.put("parts", partsArray)
        contentsArray.put(currentTurnObj)

        return contentsArray
    }

    /**
     * Generates a context-aware system instruction string that includes active session variables.
     */
    fun buildContextualSystemInstruction(): String {
        return buildString {
            append("You are J.A.R.V.I.S., an advanced AI assistant for Android with complete bilingual fluency in English and Marathi (मराठी).")
            append(" When the user speaks or asks in Marathi (or Devanagari script), respond in fluent, polite, grammatically natural Marathi (e.g., 'होय सर, नक्कीच', 'आपल्या सेवेत हजर आहे').")
            append(" When addressed in English, reply in crisp, composed Jarvis English ('At your service, sir', 'Certainly, sir').")
            append(" Keep all spoken answers concise (under 2-3 sentences) so they can be read cleanly by text-to-speech without formatting markdown symbols or asterisks.")
            append(" When asked for a joke, humor, or comedy (in English, Marathi, Hindi, etc.), generate an original, witty, funny, and entertaining joke tailored to the requested language. Never reuse the same joke repeatedly.")

            append("\n\n[UNIVERSAL CONVERSATION MEMORY & CONTINUOUS DIALOGUE RETENTION]:")
            append(" You possess continuous, multi-turn conversational memory across all user questions and subjects (science, technology, programming, history, sports, calculations, current events, culinary recipes, daily life, weather, etc.).")
            append(" When the user asks follow-up questions using pronouns ('he', 'she', 'it', 'they', 'this', 'that', 'there', 'him', 'her', 'his', 'their') or short elliptical phrases (e.g. 'tell me more', 'why?', 'explain further', 'give 3 examples', 'summarize it', 'translate to Marathi', 'what about tomorrow?'):")
            append(" 1. Seamlessly connect the question to the ongoing conversation history and focal entities.")
            append(" 2. Resolve all pronouns and back-references directly from prior turns.")
            append(" 3. NEVER ask the user to repeat the topic or state what they are referring to.")

            val subj = activeSubject
            val loc = activeLocation
            val topic = activeTopic

            // Real-time synchronization for Indian Standard Time (IST) and device local time
            val istZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
            val istSdf = java.text.SimpleDateFormat("hh:mm:ss a, EEEE, dd MMMM yyyy", java.util.Locale.US).apply {
                timeZone = istZone
            }
            val istTimeStr = istSdf.format(java.util.Date())

            val localSdf = java.text.SimpleDateFormat("hh:mm:ss a, EEEE, dd MMMM yyyy (zzz)", java.util.Locale.getDefault())
            val localTimeStr = localSdf.format(java.util.Date())

            append("\n\n[REAL-TIME CLOCK & ACCURATE TIMEKEEPING]:")
            append("\n- EXACT CURRENT TIME IN INDIA (IST, Asia/Kolkata, UTC+5:30): $istTimeStr")
            append("\n- DEVICE LOCAL TIME & TIMEZONE: $localTimeStr")
            append("\nWhenever the user asks 'what is this time', 'what is time', 'what time is it', 'current time', 'what this time', 'India time', 'what time in India', 'time in India', 'सध्याची वेळ', 'भारतातील वेळ', or asks to schedule tasks, cite this exact time.")
            append("\n\n[NATURAL SPEECH & NUMERIC PRONUNCIATION GUIDELINE]:")
            append("\n- When stating time in English, write it in natural conversational spoken English (e.g. 'The time is twelve thirty PM, sir' or 'It is five forty-five PM' rather than raw '12:30:00 PM').")
            append("\n- In Marathi, state time naturally as 'दुपारी बारा वाजून तीस मिनिटे' or 'सकाळी सात वाजून पंधरा मिनिटे'.")
            append("\n- Never format numbers with raw colons or awkward math formulas that sound robotic when read aloud.")

            if (!subj.isNullOrBlank() || !loc.isNullOrBlank() || !topic.isNullOrBlank()) {
                append("\n\n[ACTIVE SESSION CONTEXT]:")
                if (!subj.isNullOrBlank()) append("\n- Focal Subject / Entity: '$subj'")
                if (!topic.isNullOrBlank()) append("\n- Ongoing Topic: '$topic'")
                if (!loc.isNullOrBlank()) append("\n- Geographic Location: '$loc' (for any weather, local time, or regional queries)")
            }
        }
    }
}
