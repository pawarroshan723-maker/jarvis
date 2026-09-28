package com.example.engine

/**
 * Represents selectable Gemini Model Tiers in Jarvis.
 * Features two distinct Cascades (Cascade Lite & Cascade Flash) plus separate individual models:
 * - Cascade Lite (3.5 Flash Lite -> Flash-Lite Latest -> 3.1 Flash Lite)
 * - Cascade Flash (3.8 Flash -> 3.7 Flash -> 3.6 Flash -> 3.5 Flash -> Flash Latest)
 * - Separate individual models from AI Studio
 */
enum class GeminiModelTier(
    val id: String,
    val shortLabel: String,
    val modelId: String,
    val description: String,
    val isCascade: Boolean = false
) {
    CASCADE_LITE(
        id = "cascade_lite",
        shortLabel = "⚡ Cascade Lite (Zero 503 & High Quota)",
        modelId = "cascade_lite",
        description = "Intelligently cascades strictly across lightweight Flash-Lite models (3.5 Flash Lite ➔ Flash-Lite Latest ➔ 3.1 Flash Lite) for lightning-fast voice responses and zero 503 load errors.",
        isCascade = true
    ),
    CASCADE_FLASH(
        id = "cascade_flash",
        shortLabel = "🔥 Cascade Flash (Flagship Reasoning)",
        modelId = "cascade_flash",
        description = "Cascades across flagship Flash models (3.8 Flash ➔ 3.7 Flash ➔ 3.6 Flash ➔ 3.5 Flash ➔ Flash Latest) for maximum multimodal intelligence with automatic fallback on quota limits.",
        isCascade = true
    ),
    AUTO_CASCADE(
        id = "auto",
        shortLabel = "⚡ Auto Cascade (Balanced)",
        modelId = "auto_cascade",
        description = "Balanced intelligent routing across Flash & Flash-Lite models based on query complexity.",
        isCascade = true
    ),
    GEMINI_3_8_FLASH(
        id = "3_8_flash",
        shortLabel = "Gemini 3.8 Flash",
        modelId = "gemini-3.8-flash",
        description = "Latest flagship Gemini 3.8 Flash model in AI Studio"
    ),
    GEMINI_3_7_FLASH(
        id = "3_7_flash",
        shortLabel = "Gemini 3.7 Flash",
        modelId = "gemini-3.7-flash",
        description = "Highly capable multimodal model for fast and complex reasoning"
    ),
    GEMINI_3_6_FLASH(
        id = "3_6_flash",
        shortLabel = "Gemini 3.6 Flash",
        modelId = "gemini-3.6-flash",
        description = "Balanced speed and quality model"
    ),
    GEMINI_3_5_FLASH(
        id = "3_5_flash",
        shortLabel = "Gemini 3.5 Flash",
        modelId = "gemini-3.5-flash",
        description = "Fast, versatile multimodal model for everyday tasks"
    ),
    GEMINI_3_5_FLASH_LITE(
        id = "3_5_flash_lite",
        shortLabel = "Gemini 3.5 Flash Lite",
        modelId = "gemini-3.5-flash-lite",
        description = "Lightweight, ultra-fast responses with high quota allowance"
    ),
    GEMINI_3_1_FLASH_LITE(
        id = "3_1_flash_lite",
        shortLabel = "Gemini 3.1 Flash Lite",
        modelId = "gemini-3.1-flash-lite-preview",
        description = "High throughput and low latency model with generous rate limits"
    ),
    GEMINI_FLASH_LATEST(
        id = "flash_latest",
        shortLabel = "Gemini Flash Latest",
        modelId = "gemini-flash-latest",
        description = "Always points to the newest stable Flash model in Google AI Studio"
    ),
    GEMINI_FLASH_LITE_LATEST(
        id = "flash_lite_latest",
        shortLabel = "Gemini Flash-Lite Latest",
        modelId = "gemini-flash-lite-latest",
        description = "Always points to the newest lightweight Flash-Lite endpoint"
    );

    companion object {
        val CASCADES = listOf(
            CASCADE_LITE,
            CASCADE_FLASH,
            AUTO_CASCADE
        )

        val INDIVIDUAL_MODELS = listOf(
            GEMINI_3_8_FLASH,
            GEMINI_3_7_FLASH,
            GEMINI_3_6_FLASH,
            GEMINI_3_5_FLASH,
            GEMINI_3_5_FLASH_LITE,
            GEMINI_3_1_FLASH_LITE,
            GEMINI_FLASH_LATEST,
            GEMINI_FLASH_LITE_LATEST
        )

        val ALL_AVAILABLE_MODELS = CASCADES + INDIVIDUAL_MODELS
    }
}

data class ModelAttemptInfo(
    val modelId: String,
    val displayName: String,
    val isSuccess: Boolean,
    val latencyMs: Long,
    val errorMessage: String? = null
)

data class QueryExecutionSummary(
    val requestedModel: String,
    val resolvedModel: String,
    val fallbackOccurred: Boolean,
    val totalLatencyMs: Long,
    val attempts: List<ModelAttemptInfo> = emptyList()
)
