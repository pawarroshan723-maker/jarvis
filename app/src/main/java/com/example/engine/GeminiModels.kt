package com.example.engine

/**
 * Represents selectable Gemini Model Tiers in Jarvis.
 * Features Cascades plus exact Gemini 3.x / 2.x models and dynamic aliases from Google AI Studio.
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
        description = "Intelligently cascades strictly across lightweight Flash-Lite models (Gemini 3.5 Flash-Lite ➔ Gemini 3.1 Flash-Lite ➔ Gemini Flash-Lite Latest ➔ Gemini 3.5 Flash) for lightning-fast voice responses.",
        isCascade = true
    ),
    CASCADE_FLASH(
        id = "cascade_flash",
        shortLabel = "🔥 Cascade Flash (Flagship Reasoning)",
        modelId = "cascade_flash",
        description = "Cascades across flagship Flash models (Gemini Flash Latest ➔ Gemini 3.8 Flash ➔ Gemini 3.7 Flash ➔ Gemini 3.5 Flash ➔ Gemini 2.5 Flash) for maximum intelligence.",
        isCascade = true
    ),
    CASCADE_PRO(
        id = "cascade_pro",
        shortLabel = "💎 Cascade Pro (Ultimate Intelligence & STEM)",
        modelId = "cascade_pro",
        description = "Cascades from Gemini 3.1 Pro Preview for complex STEM/reasoning/coding down through Gemini Flash Latest & 3.5 Flash.",
        isCascade = true
    ),
    AUTO_CASCADE(
        id = "auto",
        shortLabel = "⚡ Auto Cascade (Balanced)",
        modelId = "auto_cascade",
        description = "Balanced intelligent routing across Flash, Pro & Flash-Lite models based on query complexity.",
        isCascade = true
    ),
    GEMINI_3_1_PRO(
        id = "3_1_pro",
        shortLabel = "Gemini 3.1 Pro",
        modelId = "gemini-3.1-pro-preview",
        description = "Flagship advanced reasoning, coding, math and complex knowledge model"
    ),
    GEMINI_FLASH_LATEST(
        id = "flash_latest",
        shortLabel = "Gemini Flash Latest",
        modelId = "gemini-flash-latest",
        description = "Dynamically points to the latest stable version of the flagship Flash tier (e.g., gemini-3.8-flash)"
    ),
    GEMINI_FLASH_LITE_LATEST(
        id = "flash_lite_latest",
        shortLabel = "Gemini Flash-Lite Latest",
        modelId = "gemini-flash-lite-latest",
        description = "Dynamically points to the latest stable version of the Flash-Lite tier (e.g., gemini-3.5-flash-lite)"
    ),
    GEMINI_3_8_FLASH(
        id = "3_8_flash",
        shortLabel = "Gemini 3.8 Flash",
        modelId = "gemini-3.8-flash",
        description = "Stable (Latest) flagship Gemini Flash model in AI Studio"
    ),
    GEMINI_3_7_FLASH(
        id = "3_7_flash",
        shortLabel = "Gemini 3.7 Flash",
        modelId = "gemini-3.7-flash",
        description = "Stable multimodal model for fast reasoning and coding"
    ),
    GEMINI_3_6_FLASH(
        id = "3_6_flash",
        shortLabel = "Gemini 3.6 Flash",
        modelId = "gemini-3.6-flash",
        description = "Stable speed and quality model"
    ),
    GEMINI_3_5_FLASH(
        id = "3_5_flash",
        shortLabel = "Gemini 3.5 Flash",
        modelId = "gemini-3.5-flash",
        description = "Stable versatile multimodal model for everyday tasks"
    ),
    GEMINI_3_FLASH_PREVIEW(
        id = "3_flash_preview",
        shortLabel = "Gemini 3 Flash Preview",
        modelId = "gemini-3-flash-preview",
        description = "Preview edition of Gemini 3 Flash"
    ),
    GEMINI_3_5_FLASH_LITE(
        id = "3_5_flash_lite",
        shortLabel = "Gemini 3.5 Flash-Lite",
        modelId = "gemini-3.5-flash-lite",
        description = "Active (Latest) lightweight model for text, image, audio, video, PDF"
    ),
    GEMINI_3_1_FLASH_LITE(
        id = "3_1_flash_lite",
        shortLabel = "Gemini 3.1 Flash-Lite",
        modelId = "gemini-3.1-flash-lite",
        description = "Active high-throughput lightweight model with generous rate limits"
    ),
    GEMINI_2_5_FLASH(
        id = "2_5_flash",
        shortLabel = "Gemini 2.5 Flash",
        modelId = "gemini-2.5-flash",
        description = "Stable Gemini 2.x generation workhorse model"
    );

    companion object {
        val CASCADES = listOf(
            CASCADE_LITE,
            CASCADE_FLASH,
            CASCADE_PRO,
            AUTO_CASCADE
        )

        val PRO_MODELS = listOf(
            GEMINI_3_1_PRO
        )

        val DYNAMIC_ALIASES = listOf(
            GEMINI_FLASH_LATEST,
            GEMINI_FLASH_LITE_LATEST
        )

        val GEMINI_3_FLASH = listOf(
            GEMINI_3_8_FLASH,
            GEMINI_3_7_FLASH,
            GEMINI_3_6_FLASH,
            GEMINI_3_5_FLASH,
            GEMINI_3_FLASH_PREVIEW
        )

        val GEMINI_FLASH_LITE = listOf(
            GEMINI_3_5_FLASH_LITE,
            GEMINI_3_1_FLASH_LITE
        )

        val GEMINI_2_SERIES = listOf(
            GEMINI_2_5_FLASH
        )

        val INDIVIDUAL_MODELS = PRO_MODELS + DYNAMIC_ALIASES + GEMINI_3_FLASH + GEMINI_FLASH_LITE + GEMINI_2_SERIES

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
