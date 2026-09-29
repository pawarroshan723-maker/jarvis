package com.example.ui.dialogs

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.GeminiModelTier
import com.example.engine.QueryExecutionSummary
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.ArcCyanContainer
import com.example.ui.theme.BorderCyan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.voice.DiagnosticItem
import com.example.voice.VoiceDiagnosticReport
import com.example.voice.VoiceLanguage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceDiagnosticsDialog(
    report: VoiceDiagnosticReport?,
    onDismiss: () -> Unit,
    onRerunDiagnostics: () -> Unit,
    onTestTts: () -> Unit,
    onTestMarathiTts: () -> Unit = {},
    onRequestMicPermission: () -> Unit,
    currentApiKey: String = "",
    onSaveApiKey: (String?) -> Unit = {},
    selectedLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    onSelectLanguage: (VoiceLanguage) -> Unit = {},
    micAlwaysOnMode: com.example.voice.MicAlwaysOnMode = com.example.voice.MicAlwaysOnMode.MANUAL,
    onSelectMicMode: (com.example.voice.MicAlwaysOnMode) -> Unit = {},
    selectedModelTier: GeminiModelTier = GeminiModelTier.AUTO_CASCADE,
    onSelectModelTier: (GeminiModelTier) -> Unit = {},
    isAutoFallbackEnabled: Boolean = true,
    onToggleAutoFallback: (Boolean) -> Unit = {},
    lastExecutionSummary: QueryExecutionSummary? = null,
    modelTestResult: com.example.engine.ModelAttemptInfo? = null,
    isTestingModel: Boolean = false,
    onTestModel: (GeminiModelTier) -> Unit = {},
    exhaustedModels: List<String> = emptyList(),
    onResetCooldowns: () -> Unit = {}
) {
    val context = LocalContext.current
    var showApiKeyEditor by remember { mutableStateOf(false) }
    var inputKey by remember { mutableStateOf(currentApiKey) }
    var keySavedNotification by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .background(ObsidianDark)
                .border(1.dp, ArcCyan, RoundedCornerShape(16.dp))
                .padding(14.dp)
                .testTag("voice_diagnostics_dialog")
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Fixed Header with Close Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ArcCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "VOICE & SYSTEM DIAGNOSTICS",
                                color = ArcCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "GEMINI 3.5 FLASH & FLASH-LITE CORE",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceVariantDark)
                            .clickable(onClick = onDismiss)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Content Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Overall Status Badge
                    val allPassed = report?.allPassed ?: false
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (allPassed) NeonGreen.copy(alpha = 0.12f)
                                else NeonAmber.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (allPassed) NeonGreen.copy(alpha = 0.4f) else NeonAmber.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (allPassed) NeonGreen else NeonAmber)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = report?.overallStatus ?: "INITIALIZING DIAGNOSTICS...",
                                    color = if (allPassed) NeonGreen else NeonAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = "${report?.items?.count { it.isPassed } ?: 0}/${report?.items?.size ?: 0} READY",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // GEMINI MULTI-TIER ENGINE & AUTO FALLBACK CARD
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .border(1.dp, ArcCyan.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .testTag("gemini_engine_model_card")
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = ArcCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "GEMINI MULTI-TIER AI ENGINE",
                                        color = ArcCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isAutoFallbackEnabled) NeonGreen.copy(alpha = 0.15f) else SurfaceVariantDark)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isAutoFallbackEnabled) "AUTO-CASCADE ON" else "STRICT SINGLE",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAutoFallbackEnabled) NeonGreen else TextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Select preferred model. On free tier keys, Flash-Lite offers the highest RPM and zero 429 limits. Auto-Cascade automatically handles rate limits.",
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 12.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Section 1: Cascades
                            Text(
                                text = "CASCADE ROUTING ENGINES (AUTO FAILOVER)",
                                color = ArcCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GeminiModelTier.CASCADES.forEach { tier ->
                                    val isSelected = selectedModelTier == tier
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) ArcCyanContainer else SurfaceVariantDark)
                                            .border(
                                                1.dp,
                                                if (isSelected) ArcCyan else BorderCyan.copy(alpha = 0.5f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onSelectModelTier(tier) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                            .testTag("model_chip_${tier.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tier.shortLabel,
                                            color = if (isSelected) ArcCyan else TextPrimary,
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Section 2: Dynamic Aliases & Pinned Models
                            Text(
                                text = "DYNAMIC ALIASES (-LATEST)",
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GeminiModelTier.DYNAMIC_ALIASES.forEach { tier ->
                                    val isSelected = selectedModelTier == tier
                                    val isExhausted = exhaustedModels.contains(tier.modelId)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when {
                                                    isSelected -> ArcCyanContainer
                                                    isExhausted -> NeonAmber.copy(alpha = 0.1f)
                                                    else -> SurfaceVariantDark
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                when {
                                                    isSelected -> ArcCyan
                                                    isExhausted -> NeonAmber.copy(alpha = 0.5f)
                                                    else -> BorderCyan.copy(alpha = 0.3f)
                                                },
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onSelectModelTier(tier) }
                                            .padding(horizontal = 7.dp, vertical = 5.dp)
                                            .testTag("model_chip_${tier.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isExhausted) "${tier.shortLabel} ⚠" else tier.shortLabel,
                                            color = when {
                                                isSelected -> ArcCyan
                                                isExhausted -> NeonAmber
                                                else -> TextSecondary
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected || isExhausted) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "GEMINI 3.X FLASH (PINNED)",
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GeminiModelTier.GEMINI_3_FLASH.forEach { tier ->
                                    val isSelected = selectedModelTier == tier
                                    val isExhausted = exhaustedModels.contains(tier.modelId)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when {
                                                    isSelected -> ArcCyanContainer
                                                    isExhausted -> NeonAmber.copy(alpha = 0.1f)
                                                    else -> SurfaceVariantDark
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                when {
                                                    isSelected -> ArcCyan
                                                    isExhausted -> NeonAmber.copy(alpha = 0.5f)
                                                    else -> BorderCyan.copy(alpha = 0.3f)
                                                },
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onSelectModelTier(tier) }
                                            .padding(horizontal = 7.dp, vertical = 5.dp)
                                            .testTag("model_chip_${tier.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isExhausted) "${tier.shortLabel} ⚠" else tier.shortLabel,
                                            color = when {
                                                isSelected -> ArcCyan
                                                isExhausted -> NeonAmber
                                                else -> TextSecondary
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected || isExhausted) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "GEMINI FLASH-LITE & 2.X SERIES",
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (GeminiModelTier.GEMINI_FLASH_LITE + GeminiModelTier.GEMINI_2_SERIES).forEach { tier ->
                                    val isSelected = selectedModelTier == tier
                                    val isExhausted = exhaustedModels.contains(tier.modelId)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when {
                                                    isSelected -> ArcCyanContainer
                                                    isExhausted -> NeonAmber.copy(alpha = 0.1f)
                                                    else -> SurfaceVariantDark
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                when {
                                                    isSelected -> ArcCyan
                                                    isExhausted -> NeonAmber.copy(alpha = 0.5f)
                                                    else -> BorderCyan.copy(alpha = 0.3f)
                                                },
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onSelectModelTier(tier) }
                                            .padding(horizontal = 7.dp, vertical = 5.dp)
                                            .testTag("model_chip_${tier.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isExhausted) "${tier.shortLabel} ⚠" else tier.shortLabel,
                                            color = when {
                                                isSelected -> ArcCyan
                                                isExhausted -> NeonAmber
                                                else -> TextSecondary
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected || isExhausted) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "ℹ ${selectedModelTier.description}",
                                color = TextMuted,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Test Model Health Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ObsidianDark)
                                    .border(1.dp, BorderCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "PING TEST: ${selectedModelTier.shortLabel}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArcCyan,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (modelTestResult != null) {
                                        Text(
                                            text = if (modelTestResult.isSuccess) "✓ Response OK (${modelTestResult.latencyMs}ms)" else "✗ ${modelTestResult.errorMessage ?: "Failed"} (${modelTestResult.latencyMs}ms)",
                                            fontSize = 8.sp,
                                            color = if (modelTestResult.isSuccess) NeonGreen else NeonAmber,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    } else {
                                        Text(
                                            text = "Verify API key quota and latency for this tier",
                                            fontSize = 8.sp,
                                            color = TextMuted,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isTestingModel) SurfaceVariantDark else ArcCyanContainer)
                                        .border(1.dp, ArcCyan, RoundedCornerShape(4.dp))
                                        .clickable(enabled = !isTestingModel) { onTestModel(selectedModelTier) }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                        .testTag("test_model_button")
                                ) {
                                    Text(
                                        text = if (isTestingModel) "TESTING..." else "TEST PING",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArcCyan,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Quota Circuit Breaker Banner
                            if (exhaustedModels.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NeonAmber.copy(alpha = 0.12f))
                                        .border(1.dp, NeonAmber.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "⚡ QUOTA CIRCUIT BREAKER ACTIVE",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeonAmber,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Token limit reached on: ${exhaustedModels.joinToString(", ")}. Cascades automatically bypass these and route to Gemini 3.5 Flash & Flash-Lite.",
                                                fontSize = 8.sp,
                                                color = TextSecondary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(NeonAmber.copy(alpha = 0.25f))
                                                .border(1.dp, NeonAmber, RoundedCornerShape(4.dp))
                                                .clickable { onResetCooldowns() }
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = "RESET",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeonAmber,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Auto-Fallback Toggle Switch Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ObsidianDark)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = if (isAutoFallbackEnabled) NeonGreen else TextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "Auto-Fallback Error Mitigation",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAutoFallbackEnabled) NeonGreen else TextPrimary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "Cascades to Flash-Lite on HTTP 429 rate limit or outage",
                                            fontSize = 8.sp,
                                            color = TextMuted,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Switch(
                                    checked = isAutoFallbackEnabled,
                                    onCheckedChange = { onToggleAutoFallback(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianDark,
                                        checkedTrackColor = NeonGreen,
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = SurfaceVariantDark
                                    ),
                                    modifier = Modifier.testTag("auto_fallback_toggle_switch")
                                )
                            }

                            // Last Query Execution Trace
                            if (lastExecutionSummary != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ObsidianDark)
                                        .border(1.dp, BorderCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "LAST EXECUTION TRACE",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ArcCyan,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "${lastExecutionSummary.totalLatencyMs}ms TOTAL",
                                                fontSize = 8.sp,
                                                color = TextMuted,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        Text(
                                            text = "• Requested: ${lastExecutionSummary.requestedModel} → Resolved: ${lastExecutionSummary.resolvedModel}",
                                            fontSize = 8.5.sp,
                                            color = if (lastExecutionSummary.fallbackOccurred) NeonAmber else NeonGreen,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        if (lastExecutionSummary.attempts.isNotEmpty()) {
                                            lastExecutionSummary.attempts.forEach { att ->
                                                Text(
                                                    text = "  ${if (att.isSuccess) "✓" else "✗"} ${att.displayName}: ${if (att.isSuccess) "Success (${att.latencyMs}ms)" else "${att.errorMessage ?: "Failed"} (${att.latencyMs}ms)"}",
                                                    fontSize = 7.5.sp,
                                                    color = if (att.isSuccess) NeonGreen.copy(alpha = 0.9f) else NeonAmber.copy(alpha = 0.9f),
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Voice Language Selector Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .border(1.dp, BorderCyan.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = ArcCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "VOICE LANGUAGE / भाषा मोड",
                                    color = ArcCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                VoiceLanguage.values().forEach { lang ->
                                    val isSelected = selectedLanguage == lang
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) ArcCyanContainer else SurfaceVariantDark)
                                            .border(
                                                1.dp,
                                                if (isSelected) ArcCyan else BorderCyan.copy(alpha = 0.5f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onSelectLanguage(lang) }
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lang.shortLabel,
                                            color = if (isSelected) ArcCyan else TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Always-On Microphone Settings Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .border(
                                1.dp,
                                if (micAlwaysOnMode == com.example.voice.MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON) NeonGreen.copy(alpha = 0.8f) else BorderCyan.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "ALWAYS-ON MIC",
                                    color = if (micAlwaysOnMode == com.example.voice.MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON) NeonGreen else ArcCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (micAlwaysOnMode == com.example.voice.MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON || micAlwaysOnMode == com.example.voice.MicAlwaysOnMode.SCREEN_OFF_ONLY) NeonGreen.copy(alpha = 0.2f) else SurfaceVariantDark)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = when (micAlwaysOnMode) {
                                            com.example.voice.MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON -> "ALWAYS ON"
                                            com.example.voice.MicAlwaysOnMode.SCREEN_OFF_ONLY -> "LOCK SCREEN"
                                            com.example.voice.MicAlwaysOnMode.SCREEN_ON_ONLY -> "SCREEN ON"
                                            com.example.voice.MicAlwaysOnMode.MANUAL -> "MANUAL"
                                        },
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (micAlwaysOnMode == com.example.voice.MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON || micAlwaysOnMode == com.example.voice.MicAlwaysOnMode.SCREEN_OFF_ONLY) NeonGreen else TextMuted,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val modes = listOf(
                                    Pair(com.example.voice.MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON, "Always On"),
                                    Pair(com.example.voice.MicAlwaysOnMode.SCREEN_OFF_ONLY, "Lock Screen"),
                                    Pair(com.example.voice.MicAlwaysOnMode.SCREEN_ON_ONLY, "Screen On"),
                                    Pair(com.example.voice.MicAlwaysOnMode.MANUAL, "Push Talk")
                                )

                                modes.forEach { (mode, label) ->
                                    val isSelected = micAlwaysOnMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) ArcCyanContainer else SurfaceVariantDark)
                                            .border(
                                                1.dp,
                                                if (isSelected) (if (mode == com.example.voice.MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON || mode == com.example.voice.MicAlwaysOnMode.SCREEN_OFF_ONLY) NeonGreen else ArcCyan)
                                                else BorderCyan.copy(alpha = 0.4f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onSelectMicMode(mode) }
                                            .padding(vertical = 7.dp, horizontal = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) (if (mode == com.example.voice.MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON || mode == com.example.voice.MicAlwaysOnMode.SCREEN_OFF_ONLY) NeonGreen else ArcCyan) else TextSecondary,
                                            fontSize = 8.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (micAlwaysOnMode != com.example.voice.MicAlwaysOnMode.MANUAL) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "● Auto-Restart Loop: Mic stays open. Listens continuously, waits 2s after speech, and executes.",
                                    fontSize = 8.sp,
                                    color = NeonGreen.copy(alpha = 0.85f),
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ObsidianDark)
                                    .padding(6.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "HUMAN VOICE DETECTION & NOISE SUPPRESSION",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArcCyan,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "• Hardware DSP NoiseSuppressor & AcousticEchoCanceler\n• Vocal Formant Bandpass (300Hz - 3400Hz)\n• Dynamic Noise Floor Adaptation & ZCR Filtering",
                                        fontSize = 7.5.sp,
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Diagnostics Checklist Items
                    report?.items?.forEach { item ->
                        DiagnosticCardItem(
                            item = item,
                            onActionClick = {
                                when (item.actionType) {
                                    "CONFIGURE_API_KEY" -> {
                                        showApiKeyEditor = !showApiKeyEditor
                                    }
                                    "OPEN_SETTINGS" -> {
                                        try {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            onRequestMicPermission()
                                        }
                                    }
                                    "OPEN_PLAY_STORE" -> {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.googlequicksearchbox")).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox")).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(webIntent)
                                        }
                                    }
                                    "REINIT_TTS" -> onTestTts()
                                    "OPEN_TTS_SETTINGS" -> {
                                        com.example.voice.MarathiTtsManager.openTtsSettings(context)
                                    }
                                }
                            }
                        )
                    }

                    // Custom API Key Editor Card (collapsible)
                    if (showApiKeyEditor) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .border(1.dp, ArcCyan.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = ArcCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "CUSTOM GEMINI API KEY",
                                        color = ArcCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "If standard project quota hits Rate Limit (HTTP 429), paste your personal Gemini API key here.",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 13.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = inputKey,
                                    onValueChange = { 
                                        inputKey = it
                                        keySavedNotification = false
                                    },
                                    placeholder = { Text("Paste AI Studio / Gemini API key", fontSize = 11.sp, color = TextMuted) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ArcCyan,
                                        unfocusedBorderColor = BorderCyan,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        cursorColor = ArcCyan
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ArcCyanContainer)
                                            .border(1.dp, ArcCyan, RoundedCornerShape(6.dp))
                                            .clickable {
                                                onSaveApiKey(inputKey.trim())
                                                keySavedNotification = true
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (keySavedNotification) "SAVED ✓" else "SAVE KEY",
                                            color = ArcCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SurfaceVariantDark)
                                            .border(1.dp, BorderCyan, RoundedCornerShape(6.dp))
                                            .clickable {
                                                inputKey = ""
                                                onSaveApiKey(null)
                                                keySavedNotification = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "RESET DEFAULT",
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fixed Bottom Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Re-test Diagnostics
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .border(1.dp, BorderCyan, RoundedCornerShape(8.dp))
                                .clickable(onClick = onRerunDiagnostics)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = ArcCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "RE-TEST",
                                    color = ArcCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Test English Voice
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, BorderCyan, RoundedCornerShape(8.dp))
                                .clickable(onClick = onTestTts)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "TEST EN",
                                    color = TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Test Marathi Voice
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ArcCyanContainer)
                                .border(1.dp, ArcCyan, RoundedCornerShape(8.dp))
                                .clickable(onClick = onTestMarathiTts)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = ArcCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "मराठी आवाज",
                                    color = ArcCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }

                        // App Settings Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .border(1.dp, BorderCyan, RoundedCornerShape(8.dp))
                                .clickable {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (ignored: Exception) {}
                                }
                                .padding(vertical = 10.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "App Settings",
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Prominent Close Button at Bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceVariantDark)
                            .border(1.dp, BorderCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CLOSE / बंद करा",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCardItem(
    item: DiagnosticItem,
    onActionClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .border(
                1.dp,
                if (item.isPassed) BorderCyan.copy(alpha = 0.5f) else NeonAmber.copy(alpha = 0.8f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (item.isPassed) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (item.isPassed) NeonGreen else NeonAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = if (item.isPassed) "PASS" else "FAIL / CHECK",
                    color = if (item.isPassed) NeonGreen else NeonAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.details,
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )

            if (!item.actionLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ArcCyanContainer)
                        .border(1.dp, ArcCyan, RoundedCornerShape(6.dp))
                        .clickable(onClick = onActionClick)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.actionLabel,
                        color = ArcCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
