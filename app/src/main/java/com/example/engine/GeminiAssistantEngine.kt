package com.example.engine

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiAssistantEngine(private val context: Context? = null) {

    companion object {
        private const val PREFS_NAME = "jarvis_gemini_prefs"
        private const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
        private const val KEY_SELECTED_TIER = "selected_gemini_tier"
        private const val KEY_AUTO_FALLBACK = "auto_fallback_enabled"
    }

    val conversationMemory = ConversationMemory(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _selectedModelTier = MutableStateFlow<GeminiModelTier>(loadSelectedTier())
    val selectedModelTier: StateFlow<GeminiModelTier> = _selectedModelTier.asStateFlow()

    private val _isAutoFallbackEnabled = MutableStateFlow<Boolean>(loadAutoFallback())
    val isAutoFallbackEnabled: StateFlow<Boolean> = _isAutoFallbackEnabled.asStateFlow()

    private val _lastExecutionSummary = MutableStateFlow<QueryExecutionSummary?>(null)
    val lastExecutionSummary: StateFlow<QueryExecutionSummary?> = _lastExecutionSummary.asStateFlow()

    private fun loadSelectedTier(): GeminiModelTier {
        if (context == null) return GeminiModelTier.AUTO_CASCADE
        val savedId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_TIER, GeminiModelTier.AUTO_CASCADE.id)
        return GeminiModelTier.ALL_AVAILABLE_MODELS.firstOrNull { it.id == savedId }
            ?: GeminiModelTier.AUTO_CASCADE
    }

    private fun loadAutoFallback(): Boolean {
        if (context == null) return true
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_FALLBACK, true)
    }

    fun setSelectedModelTier(tier: GeminiModelTier) {
        _selectedModelTier.value = tier
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_SELECTED_TIER, tier.id)?.apply()
    }

    fun setAutoFallbackEnabled(enabled: Boolean) {
        _isAutoFallbackEnabled.value = enabled
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_AUTO_FALLBACK, enabled)?.apply()
    }

    fun getEffectiveApiKey(): String {
        if (context != null) {
            val custom = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CUSTOM_API_KEY, null)?.trim()
            if (!custom.isNullOrBlank()) {
                return custom
            }
        }
        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun setCustomApiKey(key: String?) {
        if (context == null) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (key.isNullOrBlank()) {
            prefs.edit().remove(KEY_CUSTOM_API_KEY).apply()
        } else {
            prefs.edit().putString(KEY_CUSTOM_API_KEY, key.trim()).apply()
        }
    }

    fun isCustomApiKeySet(): Boolean {
        if (context == null) return false
        val custom = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_API_KEY, null)?.trim()
        return !custom.isNullOrBlank()
    }

    fun clearConversationHistory() {
        conversationMemory.clearHistory()
    }

    fun getActiveLocation(): String? {
        return conversationMemory.activeLocation
    }

    /**
     * Determines whether a query is complex / hard (requiring deep reasoning, math, code, or step-by-step breakdown)
     */
    fun isComplexQuery(query: String): Boolean {
        val q = query.lowercase().trim()
        val complexKeywords = listOf(
            "explain", "how to", "why does", "step by step", "step-by-step",
            "calculate", "solve", "derive", "compare", "difference between",
            "write code", "program", "function", "algorithm", "debug", "error in",
            "proof", "physics", "chemistry", "mathematics", "formula",
            "deep dive", "analyze", "pros and cons", "architecture", "diagram",
            "coroutine", "compose", "kotlin", "python", "java", "sql",
            "स्पष्टीकरण", "कारण काय", "कसे करावे", "फरक", "सोडवा", "गणित", "सविस्तर"
        )
        return complexKeywords.any { q.contains(it) } || q.split("\\s+".toRegex()).size > 20
    }

    private data class ModelConfig(
        val model: String,
        val displayName: String,
        val highThinking: Boolean,
        val allowTools: Boolean
    )

    /**
     * Executes intelligent multi-tier query routing with automatic fallback across independent model quota buckets.
     */
    suspend fun queryAssistant(
        userQuery: String,
        enableHighThinking: Boolean = false,
        useSearch: Boolean = false,
        useMaps: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = getEffectiveApiKey()

        // 1. Normalize speech transcription homophones ("whether" -> "weather") & resolve follow-up context
        val normalizedQuery = conversationMemory.normalizeAndEnrichQuery(userQuery)

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val offlineKnowledge = OfflineKnowledgeEngine.answerQuery(normalizedQuery, context)
            if (offlineKnowledge.handled) {
                conversationMemory.addTurn("user", normalizedQuery)
                conversationMemory.addTurn("model", offlineKnowledge.answer)
                val latency = System.currentTimeMillis() - startTime
                _lastExecutionSummary.value = QueryExecutionSummary(
                    requestedModel = "Offline Engine",
                    resolvedModel = "Offline Engine",
                    fallbackOccurred = false,
                    totalLatencyMs = latency,
                    attempts = listOf(
                        ModelAttemptInfo("offline", "Offline Knowledge Core", true, latency)
                    )
                )
                return@withContext offlineKnowledge.answer
            }
            return@withContext "I am operating in strict offline mode, sir. Local hardware commands, environmental sensor automations, and system controls remain fully functional without cloud access."
        }

        val isHardQuestion = enableHighThinking || isComplexQuery(normalizedQuery)
        val selectedTier = _selectedModelTier.value
        val autoFallback = _isAutoFallbackEnabled.value

        // Determine cascade based on selected tier and complexity
        val modelCascade = when {
            selectedTier == GeminiModelTier.CASCADE_LITE -> listOf(
                ModelConfig("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", highThinking = false, allowTools = true),
                ModelConfig("gemini-flash-lite-latest", "Gemini Flash-Lite Latest", highThinking = false, allowTools = true),
                ModelConfig("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite", highThinking = false, allowTools = false)
            )
            selectedTier == GeminiModelTier.CASCADE_FLASH -> listOf(
                ModelConfig("gemini-3.8-flash", "Gemini 3.8 Flash", highThinking = isHardQuestion, allowTools = true),
                ModelConfig("gemini-3.7-flash", "Gemini 3.7 Flash", highThinking = isHardQuestion, allowTools = true),
                ModelConfig("gemini-3.6-flash", "Gemini 3.6 Flash", highThinking = false, allowTools = true),
                ModelConfig("gemini-3.5-flash", "Gemini 3.5 Flash", highThinking = false, allowTools = true),
                ModelConfig("gemini-flash-latest", "Gemini Flash Latest", highThinking = false, allowTools = true),
                // Safety fallback to lite if all flagship flash models are 503 overloaded
                ModelConfig("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite (Safety Fallback)", highThinking = false, allowTools = false)
            )
            selectedTier == GeminiModelTier.AUTO_CASCADE && isHardQuestion -> listOf(
                ModelConfig("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", highThinking = false, allowTools = false),
                ModelConfig("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite", highThinking = false, allowTools = false),
                ModelConfig("gemini-flash-lite-latest", "Gemini Flash-Lite Latest", highThinking = false, allowTools = false),
                ModelConfig("gemini-3.7-flash", "Gemini 3.7 Flash", highThinking = true, allowTools = false),
                ModelConfig("gemini-3.8-flash", "Gemini 3.8 Flash", highThinking = true, allowTools = false)
            )
            selectedTier == GeminiModelTier.AUTO_CASCADE -> listOf(
                ModelConfig("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", highThinking = false, allowTools = true),
                ModelConfig("gemini-flash-lite-latest", "Gemini Flash-Lite Latest", highThinking = false, allowTools = true),
                ModelConfig("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite", highThinking = false, allowTools = false),
                ModelConfig("gemini-3.7-flash", "Gemini 3.7 Flash", highThinking = false, allowTools = true),
                ModelConfig("gemini-3.8-flash", "Gemini 3.8 Flash", highThinking = false, allowTools = true)
            )
            !autoFallback -> listOf(
                ModelConfig(selectedTier.modelId, selectedTier.shortLabel, highThinking = enableHighThinking, allowTools = true)
            )
            else -> {
                val primary = ModelConfig(selectedTier.modelId, selectedTier.shortLabel, highThinking = enableHighThinking, allowTools = true)
                val fallbacks = listOf(
                    ModelConfig("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", highThinking = false, allowTools = true),
                    ModelConfig("gemini-flash-lite-latest", "Gemini Flash-Lite Latest", highThinking = false, allowTools = true),
                    ModelConfig("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite", highThinking = false, allowTools = false),
                    ModelConfig("gemini-3.7-flash", "Gemini 3.7 Flash", highThinking = false, allowTools = false),
                    ModelConfig("gemini-3.8-flash", "Gemini 3.8 Flash", highThinking = false, allowTools = false)
                ).filter { it.model != selectedTier.modelId }
                listOf(primary) + fallbacks
            }
        }

        val attempts = mutableListOf<ModelAttemptInfo>()
        val requestedModelName = modelCascade.first().displayName

        // Try cascading tiers sequentially on 429 (Rate Limited) or API failures
        for (i in modelCascade.indices) {
            val config = modelCascade[i]
            val attemptStart = System.currentTimeMillis()
            Log.d("GeminiEngine", "Attempting query with model tier [${i + 1}/${modelCascade.size}]: ${config.model} (Hard: $isHardQuestion)")

            val result = executeGeminiRequest(
                model = config.model,
                apiKey = apiKey,
                userQuery = normalizedQuery,
                enableHighThinking = config.highThinking,
                useSearch = if (config.allowTools) useSearch else false,
                useMaps = if (config.allowTools) useMaps else false
            )
            val attemptLatency = System.currentTimeMillis() - attemptStart

            when (result) {
                is ApiResult.Success -> {
                    attempts.add(ModelAttemptInfo(config.model, config.displayName, true, attemptLatency))
                    conversationMemory.addTurn("user", normalizedQuery)
                    conversationMemory.addTurn("model", result.text)
                    val totalLatency = System.currentTimeMillis() - startTime
                    _lastExecutionSummary.value = QueryExecutionSummary(
                        requestedModel = requestedModelName,
                        resolvedModel = config.displayName,
                        fallbackOccurred = i > 0,
                        totalLatencyMs = totalLatency,
                        attempts = attempts
                    )
                    return@withContext result.text
                }
                is ApiResult.RateLimited -> {
                    val rateLimitMsg = "429 Rate Limit (Free Tier RPM quota exceeded)"
                    attempts.add(ModelAttemptInfo(config.model, config.displayName, false, attemptLatency, rateLimitMsg))
                    Log.w("GeminiEngine", "Model ${config.model} returned 429 Rate Limited. Trying next tier...")
                    
                    // Check if local offline engine can answer immediately without waiting
                    val offlineKnowledge = OfflineKnowledgeEngine.answerQuery(normalizedQuery, context)
                    if (offlineKnowledge.handled) {
                        conversationMemory.addTurn("user", normalizedQuery)
                        conversationMemory.addTurn("model", offlineKnowledge.answer)
                        val totalLatency = System.currentTimeMillis() - startTime
                        _lastExecutionSummary.value = QueryExecutionSummary(
                            requestedModel = requestedModelName,
                            resolvedModel = "Offline Engine (Fallback)",
                            fallbackOccurred = true,
                            totalLatencyMs = totalLatency,
                            attempts = attempts
                        )
                        return@withContext offlineKnowledge.answer
                    }
                    if (i < modelCascade.size - 1) {
                        delay(250) // Brief backoff before next model tier
                    }
                }
                is ApiResult.Error -> {
                    attempts.add(ModelAttemptInfo(config.model, config.displayName, false, attemptLatency, "HTTP ${result.code}"))
                    Log.w("GeminiEngine", "Model ${config.model} returned HTTP ${result.code}: ${result.message}")
                    // If tools caused a 400 error, retry this tier without tools first
                    if (config.allowTools && (useSearch || useMaps)) {
                        val retryStart = System.currentTimeMillis()
                        val retryNoTools = executeGeminiRequest(
                            model = config.model,
                            apiKey = apiKey,
                            userQuery = normalizedQuery,
                            enableHighThinking = config.highThinking,
                            useSearch = false,
                            useMaps = false
                        )
                        val retryLatency = System.currentTimeMillis() - retryStart
                        if (retryNoTools is ApiResult.Success) {
                            attempts.add(ModelAttemptInfo(config.model, "${config.displayName} (No Tools)", true, retryLatency))
                            conversationMemory.addTurn("user", normalizedQuery)
                            conversationMemory.addTurn("model", retryNoTools.text)
                            val totalLatency = System.currentTimeMillis() - startTime
                            _lastExecutionSummary.value = QueryExecutionSummary(
                                requestedModel = requestedModelName,
                                resolvedModel = config.displayName,
                                fallbackOccurred = i > 0,
                                totalLatencyMs = totalLatency,
                                attempts = attempts
                            )
                            return@withContext retryNoTools.text
                        }
                    }
                }
                is ApiResult.ExceptionError -> {
                    attempts.add(ModelAttemptInfo(config.model, config.displayName, false, attemptLatency, result.exception.message ?: "Exception"))
                    Log.w("GeminiEngine", "Exception on ${config.model}: ${result.exception.message}")
                }
            }
        }

        // Final Fallback: Offline Knowledge Engine
        val finalOffline = OfflineKnowledgeEngine.answerQuery(normalizedQuery, context)
        if (finalOffline.handled) {
            conversationMemory.addTurn("user", normalizedQuery)
            conversationMemory.addTurn("model", finalOffline.answer)
            val totalLatency = System.currentTimeMillis() - startTime
            _lastExecutionSummary.value = QueryExecutionSummary(
                requestedModel = requestedModelName,
                resolvedModel = "Offline Engine (Final Fallback)",
                fallbackOccurred = true,
                totalLatencyMs = totalLatency,
                attempts = attempts
            )
            return@withContext finalOffline.answer
        }

        val totalLatency = System.currentTimeMillis() - startTime
        _lastExecutionSummary.value = QueryExecutionSummary(
            requestedModel = requestedModelName,
            resolvedModel = "Exhausted (Offline Switch)",
            fallbackOccurred = true,
            totalLatencyMs = totalLatency,
            attempts = attempts
        )

        val isMarathiQuery = com.example.voice.MarathiTtsManager.isDevanagari(normalizedQuery) ||
                com.example.voice.MarathiTtsManager.isMarathiPhrase(normalizedQuery)

        return@withContext if (isMarathiQuery) {
            "क्लाउड कोटा तात्पुरता संपला आहे. मी स्थानिक ऑफलाईन मोडवर कार्यरत आहे, सर. तुम्ही डायग्नोस्टिक्समध्ये तुमची स्वतःची Gemini API Key जोडू शकता."
        } else {
            "Cloud intelligence rate limit reached across all tiers. Switched to offline protocol, sir. You can configure your own Gemini API key in Diagnostics for dedicated quota."
        }
    }

    private fun executeGeminiRequest(
        model: String,
        apiKey: String,
        userQuery: String,
        enableHighThinking: Boolean,
        useSearch: Boolean,
        useMaps: Boolean
    ): ApiResult {
        return try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val requestJson = JSONObject().apply {
                // Contents: Multi-turn history preserved with active user query appended
                val contentsArray = conversationMemory.buildContentsJsonArray(userQuery)
                put("contents", contentsArray)

                // System Instruction: Contextualized with active user location and conversation guidelines
                val sysInstruction = JSONObject()
                val sysParts = JSONArray()
                sysParts.put(
                    JSONObject().put(
                        "text",
                        conversationMemory.buildContextualSystemInstruction()
                    )
                )
                sysInstruction.put("parts", sysParts)
                put("systemInstruction", sysInstruction)

                // Generation Config
                val genConfig = JSONObject()
                if (enableHighThinking) {
                    val thinkingObj = JSONObject().apply {
                        put("thinkingLevel", "HIGH")
                    }
                    genConfig.put("thinkingConfig", thinkingObj)
                }
                genConfig.put("temperature", 0.7)
                put("generationConfig", genConfig)

                // Grounding tools (only when explicitly requested and not high thinking)
                if (!enableHighThinking) {
                    val toolsArray = JSONArray()
                    if (useMaps) {
                        toolsArray.put(JSONObject().put("googleMaps", JSONObject()))
                    } else if (useSearch) {
                        toolsArray.put(JSONObject().put("googleSearch", JSONObject()))
                    }
                    if (toolsArray.length() > 0) {
                        put("tools", toolsArray)
                    }
                }
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val code = response.code
            val responseBody = response.body?.string() ?: ""

            if (code == 429) {
                return ApiResult.RateLimited(responseBody)
            }

            if (!response.isSuccessful) {
                return ApiResult.Error(code, responseBody)
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val textBuilder = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("text")) {
                            textBuilder.append(part.getString("text"))
                        }
                    }
                    val text = textBuilder.toString().trim()
                    if (text.isNotEmpty()) {
                        return ApiResult.Success(text)
                    }
                }
            }

            ApiResult.Success("I received no textual response from the intelligence core, sir.")
        } catch (e: Exception) {
            Log.e("GeminiEngine", "Exception during Gemini request ($model)", e)
            ApiResult.ExceptionError(e)
        }
    }

    /**
     * Pings a specific model tier with a lightweight 1-token query to verify responsiveness without consuming heavy RPM quota.
     */
    suspend fun testModelHealth(tier: GeminiModelTier): ModelAttemptInfo = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext ModelAttemptInfo(
                modelId = tier.modelId,
                displayName = tier.shortLabel,
                isSuccess = false,
                latencyMs = 0,
                errorMessage = "API Key not configured in Secrets"
            )
        }

        val targetModel = when (tier) {
            GeminiModelTier.CASCADE_LITE -> "gemini-3.5-flash-lite"
            GeminiModelTier.CASCADE_FLASH -> "gemini-3.7-flash"
            GeminiModelTier.AUTO_CASCADE -> "gemini-3.5-flash-lite"
            else -> tier.modelId
        }
        val start = System.currentTimeMillis()
        var result = executePingRequest(targetModel, apiKey)
        var latency = System.currentTimeMillis() - start

        // If 503 (High demand) or 429 occurs on single test, retry once with a quick 350ms backoff
        if (result is ApiResult.RateLimited || (result is ApiResult.Error && result.code == 503)) {
            kotlinx.coroutines.delay(350)
            val retryStart = System.currentTimeMillis()
            val retryResult = executePingRequest(targetModel, apiKey)
            if (retryResult is ApiResult.Success) {
                result = retryResult
                latency = System.currentTimeMillis() - retryStart
            }
        }

        when (result) {
            is ApiResult.Success -> ModelAttemptInfo(targetModel, tier.shortLabel, true, latency)
            is ApiResult.RateLimited -> ModelAttemptInfo(
                targetModel,
                tier.shortLabel,
                false,
                latency,
                "429 Quota Exceeded (Free key RPM limit on this model. Auto-Cascade will route to high-quota models)"
            )
            is ApiResult.Error -> {
                val msg = when (result.code) {
                    503 -> "503 High Demand (Google server overloaded on this model. Auto-Cascade routes to Flash-Lite)"
                    404 -> "404 Not Found (Model ID not active on this key)"
                    403 -> "403 Forbidden (Check AI Studio key permissions)"
                    else -> "HTTP ${result.code}: ${result.message.take(70)}"
                }
                ModelAttemptInfo(targetModel, tier.shortLabel, false, latency, msg)
            }
            is ApiResult.ExceptionError -> ModelAttemptInfo(
                targetModel,
                tier.shortLabel,
                false,
                latency,
                result.exception.message?.take(80) ?: "Network Error"
            )
        }
    }

    private fun executePingRequest(
        model: String,
        apiKey: String
    ): ApiResult {
        return try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "ping"))
                        })
                    })
                }
                put("contents", contents)
                val genConfig = JSONObject().apply {
                    put("maxOutputTokens", 5)
                    put("temperature", 0.0)
                }
                put("generationConfig", genConfig)
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val code = response.code
            val responseBody = response.body?.string() ?: ""

            if (code == 429) {
                return ApiResult.RateLimited(responseBody)
            }
            if (!response.isSuccessful) {
                return ApiResult.Error(code, responseBody)
            }
            ApiResult.Success("OK")
        } catch (e: Exception) {
            ApiResult.ExceptionError(e)
        }
    }

    private sealed class ApiResult {
        data class Success(val text: String) : ApiResult()
        data class RateLimited(val details: String) : ApiResult()
        data class Error(val code: Int, val message: String) : ApiResult()
        data class ExceptionError(val exception: Exception) : ApiResult()
    }
}
