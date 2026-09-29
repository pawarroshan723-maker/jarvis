package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.GeminiModelTier
import com.example.engine.QueryExecutionSummary
import com.example.ui.components.ArcReactorCore
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
import com.example.voice.MicAlwaysOnMode
import com.example.voice.SpeechState
import com.example.voice.VoiceLanguage

/**
 * Optimized CoreHudScreen:
 * Best-Fit High-Quality Cybernetic Layout with Minimum Scrolling.
 * Fits key controls, Arc Reactor, Communication Console, and Hardware shortcuts
 * effortlessly into a single unified mobile viewport.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreHudScreen(
    speechState: SpeechState,
    audioRms: Float,
    lastCommand: String,
    lastSpokenReply: String,
    partialRecognizedText: String = "",
    isProcessing: Boolean,
    isFlashlightOn: Boolean,
    currentVolume: Int,
    onToggleListen: () -> Unit,
    onSubmitCommand: (String) -> Unit,
    onToggleFlashlight: () -> Unit,
    onSetVolume: (Int) -> Unit,
    onMuteAudio: () -> Unit,
    recognitionError: String? = null,
    onClearRecognitionError: () -> Unit = {},
    onLaunchSystemSpeechDialog: () -> Unit = {},
    preferSystemDialog: Boolean = false,
    onToggleVoiceMode: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onRequestMicPermission: () -> Unit = {},
    selectedLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    onCycleLanguage: () -> Unit = {},
    micAlwaysOnMode: MicAlwaysOnMode = MicAlwaysOnMode.MANUAL,
    onSetMicAlwaysOnMode: (MicAlwaysOnMode) -> Unit = {},
    isWakeWordLockActive: Boolean = true,
    onToggleWakeWordLock: () -> Unit = {},
    isVoiceActive: Boolean = false,
    humanVoiceConfidence: Float = 0f,
    backgroundNoiseLevel: Float = 25f,
    isNoiseSuppressorActive: Boolean = true,
    noiseCancellationStatus: String = "DSP Active (Vocal Filter 300Hz-3.4kHz)",
    activeLocationContext: String? = null,
    activeContextSummary: String? = null,
    onClearConversationMemory: () -> Unit = {},
    selectedModelTier: GeminiModelTier = GeminiModelTier.AUTO_CASCADE,
    onSelectModelTier: (GeminiModelTier) -> Unit = {},
    isAutoFallbackEnabled: Boolean = true,
    onToggleAutoFallback: (Boolean) -> Unit = {},
    lastExecutionSummary: QueryExecutionSummary? = null,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    var showAdvancedControls by remember { mutableStateOf(false) }

    val quickCommands = when (selectedLanguage) {
        VoiceLanguage.MARATHI -> listOf(
            "नमस्कार जार्व्हिस",
            "अलार्म लावा",
            "5 मिनिट टायमर",
            "टॉर्च चालू करा",
            "बॅटरी किती आहे?",
            "आवाज वाढवा",
            "फोन म्यूट करा",
            "वेळ काय झाली?",
            "कॅलेंडर उघडा",
            "कॅमेरा उघडा"
        )
        VoiceLanguage.ENGLISH -> listOf(
            "Status Report",
            "Set alarm 7 AM",
            "5m Timer",
            "Torch On",
            "Battery Level?",
            "Volume Up",
            "Mute Audio",
            "Tell me a joke",
            "Open Calendar",
            "Open Camera"
        )
        VoiceLanguage.AUTO -> listOf(
            "Status Report",
            "नमस्कार",
            "Set alarm 7 AM",
            "5m Timer",
            "Torch On",
            "बॅटरी किती आहे?",
            "Mute Phone",
            "Open Camera"
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. HERO UNIT: ARC REACTOR + TELEMETRY & VOICE CONTROLS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("core_hero_hud_card")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top Telemetry Status Line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (speechState) {
                                            SpeechState.LISTENING -> ArcCyan
                                            SpeechState.PROCESSING -> NeonAmber
                                            SpeechState.SPEAKING -> NeonGreen
                                            SpeechState.ERROR -> NeonRed
                                            SpeechState.IDLE -> ArcCyan.copy(alpha = 0.7f)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CORE TELEMETRY",
                                color = ArcCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        // DSP & Noise Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "NOISE: ${backgroundNoiseLevel.toInt()}dB",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ObsidianDark)
                                    .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isVoiceActive) "VOCAL ${(humanVoiceConfidence * 100).toInt()}%" else "DSP ACTIVE",
                                    color = NeonGreen,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Compact Arc Reactor Core (105dp size) + Reactive audio ring
                    ArcReactorCore(
                        speechState = speechState,
                        audioRms = audioRms,
                        onClick = onToggleListen,
                        orbSize = 105.dp,
                        showStatePill = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Compact Action Strip: Mode Toggle + Quick Listen Button + Diagnostics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // In-App Mic vs Google Voice Dialog selector pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (preferSystemDialog) ArcCyanContainer else SurfaceVariantDark)
                                .border(
                                    1.dp,
                                    if (preferSystemDialog) ArcCyan else BorderCyan.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = androidx.compose.material3.ripple(bounded = true, color = ArcCyan),
                                    onClick = onToggleVoiceMode
                                )
                                .padding(horizontal = 8.dp)
                                .testTag("voice_mode_toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (preferSystemDialog) NeonGreen else ArcCyan)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (preferSystemDialog) "GOOGLE VOICE" else "IN-APP MIC",
                                    color = if (preferSystemDialog) ArcCyan else TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Google Voice Direct Launch
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (speechState == SpeechState.LISTENING) NeonAmber.copy(alpha = 0.2f) else SurfaceVariantDark)
                                .border(
                                    1.dp,
                                    if (speechState == SpeechState.LISTENING) NeonAmber else BorderCyan.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = androidx.compose.material3.ripple(bounded = true, color = ArcCyan),
                                    onClick = onLaunchSystemSpeechDialog
                                )
                                .padding(horizontal = 8.dp)
                                .testTag("launch_system_speech_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "SPEECH DIALOG",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Diagnostics Action Button (48dp accessible touch target container)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, BorderCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = androidx.compose.material3.ripple(bounded = true, color = ArcCyan),
                                    onClick = onOpenDiagnostics
                                )
                                .testTag("open_diagnostics_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Diagnostics",
                                tint = ArcCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. RECOGNITION ERROR / NOTICE ALERT (IF ANY)
        if (!recognitionError.isNullOrBlank()) {
            item {
                val errorMsg = recognitionError
                val isPermissionError = errorMsg.contains("permission", ignoreCase = true)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonRed.copy(alpha = 0.15f))
                        .border(1.dp, NeonRed, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = NeonRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "VOICE INPUT NOTICE",
                                color = NeonRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "DISMISS",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { onClearRecognitionError() }
                                .padding(4.dp)
                        )
                    }

                    Text(
                        text = errorMsg,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )

                    if (isPermissionError) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonGreen.copy(alpha = 0.2f))
                                .border(1.dp, NeonGreen, RoundedCornerShape(6.dp))
                                .clickable {
                                    onClearRecognitionError()
                                    onRequestMicPermission()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "GRANT PERMISSION",
                                color = NeonGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 3. LIVE SPEECH CAPTURE & PARTIAL RECOGNITION BANNER
        if (speechState == SpeechState.LISTENING && partialRecognizedText.isNotBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .border(1.dp, ArcCyan, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Caught speech:",
                                color = TextMuted,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "\"$partialRecognizedText\"",
                                color = ArcCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ArcCyanContainer)
                                .border(1.dp, ArcCyan, RoundedCornerShape(6.dp))
                                .clickable { onSubmitCommand(partialRecognizedText) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "SEND",
                                color = ArcCyan,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 4. COMMUNICATION CONSOLE (PROMPT & RESPONSE)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("communication_console_card")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Title Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "COMMUNICATION CONSOLE",
                                color = ArcCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            val contextBadge = activeContextSummary ?: if (!activeLocationContext.isNullOrBlank()) "LOC: ${activeLocationContext.uppercase()}" else null
                            if (!contextBadge.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NeonGreen.copy(alpha = 0.15f))
                                        .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .clickable { onClearConversationMemory() }
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = contextBadge,
                                        color = NeonGreen,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isProcessing || speechState == SpeechState.PROCESSING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    color = NeonAmber,
                                    strokeWidth = 1.5.dp
                                )
                            }

                            val hasMemory = lastCommand.isNotBlank() || !activeLocationContext.isNullOrBlank() || !activeContextSummary.isNullOrBlank()
                            if (hasMemory) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SurfaceVariantDark)
                                        .border(1.dp, BorderCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .clickable { onClearConversationMemory() }
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "CLEAR",
                                        color = TextSecondary,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // User Query Section (if available)
                    if (lastCommand.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, NeonAmber.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "YOU",
                                    color = NeonAmber,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = lastCommand,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    // Jarvis Response Section
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ArcCyanContainer)
                            .border(1.dp, ArcCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "J.A.R.V.I.S.",
                                    color = ArcCyan,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )

                                if (speechState == SpeechState.SPEAKING) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = NeonGreen,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "SPEAKING",
                                            color = NeonGreen,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = lastSpokenReply.ifBlank { "Awaiting command, sir. Ready for vocal instructions or environmental triggers." },
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        // 5. UNIFIED HIGH-DENSITY QUICK CONTROLS MATRIX (SINGLE COMPACT CARD)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_controls_card")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header row: Hardware Controls & Expand Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "SYSTEM CONTROLS & AI MATRIX",
                            color = ArcCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = if (showAdvancedControls) "LESS ▲" else "MORE ▼",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { showAdvancedControls = !showAdvancedControls }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // Row 1: Flashlight Button + Mute Button + Inline Volume Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Torch Button
                        Box(
                            modifier = Modifier
                                .weight(0.9f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isFlashlightOn) NeonAmber.copy(alpha = 0.25f) else SurfaceVariantDark)
                                .border(
                                    1.dp,
                                    if (isFlashlightOn) NeonAmber else BorderCyan.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onToggleFlashlight() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                    contentDescription = "Flashlight",
                                    tint = if (isFlashlightOn) NeonAmber else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isFlashlightOn) "TORCH ON" else "TORCH OFF",
                                    color = if (isFlashlightOn) NeonAmber else TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Mute Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, BorderCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { onMuteAudio() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeMute,
                                contentDescription = "Mute",
                                tint = NeonRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Inline Volume Slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, BorderCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                                contentDescription = "Volume",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Slider(
                                value = currentVolume.toFloat(),
                                onValueChange = { onSetVolume(it.toInt()) },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ArcCyan,
                                    activeTrackColor = ArcCyan,
                                    inactiveTrackColor = BorderCyan.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .testTag("volume_slider")
                            )
                            Text(
                                text = "$currentVolume%",
                                color = ArcCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Row 2: Always-On Listening Selector (4 compact tabs)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val modes = listOf(
                            Pair(MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON, "Always On"),
                            Pair(MicAlwaysOnMode.SCREEN_OFF_ONLY, "Lock Only"),
                            Pair(MicAlwaysOnMode.SCREEN_ON_ONLY, "Screen On"),
                            Pair(MicAlwaysOnMode.MANUAL, "Manual")
                        )

                        modes.forEach { (mode, label) ->
                            val isSelected = micAlwaysOnMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) ArcCyanContainer else SurfaceVariantDark)
                                    .border(
                                        1.dp,
                                        if (isSelected) (if (mode == MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON || mode == MicAlwaysOnMode.SCREEN_OFF_ONLY) NeonGreen else ArcCyan)
                                        else BorderCyan.copy(alpha = 0.25f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onSetMicAlwaysOnMode(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 8.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) (if (mode == MicAlwaysOnMode.ALWAYS_ON_SCREEN_OFF_AND_ON || mode == MicAlwaysOnMode.SCREEN_OFF_ONLY) NeonGreen else ArcCyan) else TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // --- WAKE-WORD GATEKEEPER LOCK (STRICT ALEXA HANDS-FREE MODE) ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isWakeWordLockActive) ArcCyanContainer else SurfaceVariantDark)
                            .border(
                                1.dp,
                                if (isWakeWordLockActive) NeonGreen else BorderCyan.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggleWakeWordLock() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("wake_word_gate_lock_toggle")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isWakeWordLockActive) Icons.Default.Lock else Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = if (isWakeWordLockActive) NeonGreen else TextMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                                Column {
                                    Text(
                                        text = if (isWakeWordLockActive) "JARVIS WAKE-WORD LOCK (STRICT)" else "OPEN MIC MODE (UNLOCKED)",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isWakeWordLockActive) NeonGreen else TextSecondary
                                    )
                                    Text(
                                        text = if (isWakeWordLockActive) "Requires 'Jarvis' wake-word. Background chatter & music ignored."
                                        else "Accepts any spoken words without 'Jarvis' wake-word.",
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isWakeWordLockActive) NeonGreen.copy(alpha = 0.2f) else ObsidianDark)
                                    .border(1.dp, if (isWakeWordLockActive) NeonGreen else BorderCyan.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isWakeWordLockActive) "LOCKED" else "OPEN",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isWakeWordLockActive) NeonGreen else TextMuted
                                )
                            }
                        }
                    }

                    // Collapsible AI Model Selection & Fallback details
                    AnimatedVisibility(visible = showAdvancedControls) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            // Model Chips
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "GEMINI MODEL TIER",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = ArcCyan
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isAutoFallbackEnabled) NeonGreen.copy(alpha = 0.15f) else SurfaceVariantDark)
                                        .border(
                                            1.dp,
                                            if (isAutoFallbackEnabled) NeonGreen.copy(alpha = 0.5f) else BorderCyan.copy(alpha = 0.3f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { onToggleAutoFallback(!isAutoFallbackEnabled) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("hud_auto_fallback_toggle")
                                ) {
                                    Text(
                                        text = if (isAutoFallbackEnabled) "FALLBACK: ON" else "FALLBACK: OFF",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isAutoFallbackEnabled) NeonGreen else TextMuted
                                    )
                                }
                            }

                            // Cascades
                            Text(
                                text = "CASCADES (AUTO FAILOVER)",
                                color = ArcCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                GeminiModelTier.CASCADES.forEach { tier ->
                                    val isSelected = selectedModelTier == tier
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) ArcCyanContainer else SurfaceVariantDark)
                                            .border(
                                                1.dp,
                                                if (isSelected) ArcCyan else BorderCyan.copy(alpha = 0.4f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable { onSelectModelTier(tier) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .testTag("hud_model_chip_${tier.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tier.shortLabel,
                                            color = if (isSelected) ArcCyan else TextSecondary,
                                            fontSize = 8.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "INDIVIDUAL MODELS (DIRECT)",
                                color = TextMuted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                GeminiModelTier.INDIVIDUAL_MODELS.forEach { tier ->
                                    val isSelected = selectedModelTier == tier
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) ArcCyanContainer else SurfaceVariantDark)
                                            .border(
                                                1.dp,
                                                if (isSelected) ArcCyan else BorderCyan.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable { onSelectModelTier(tier) }
                                            .padding(horizontal = 5.dp, vertical = 3.dp)
                                            .testTag("hud_model_chip_${tier.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tier.shortLabel,
                                            color = if (isSelected) ArcCyan else TextMuted,
                                            fontSize = 7.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            if (lastExecutionSummary != null) {
                                Text(
                                    text = "Trace: ${lastExecutionSummary.resolvedModel} (${lastExecutionSummary.totalLatencyMs}ms)",
                                    fontSize = 8.sp,
                                    color = if (lastExecutionSummary.fallbackOccurred) NeonAmber else NeonGreen,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. QUICK COMMAND PROTOCOLS (HORIZONTAL CAROUSEL CHIPS)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "COMMAND PROTOCOLS",
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickCommands.forEach { cmd ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, BorderCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .clickable { onSubmitCommand(cmd) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cmd,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 7. COMPACT COMMAND INPUT (ALWAYS VISIBLE & FULL TOUCH TARGETS)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text("Ask Jarvis or enter instruction...", color = TextMuted, fontSize = 12.sp)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input_field"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (textInput.isNotBlank()) {
                                onSubmitCommand(textInput)
                                textInput = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedBorderColor = ArcCyan,
                        unfocusedBorderColor = BorderCyan.copy(alpha = 0.5f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Microphone quick toggle button (48dp x 48dp accessible)
                IconButton(
                    onClick = onToggleListen,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (speechState == SpeechState.LISTENING) NeonAmber.copy(alpha = 0.25f) else ArcCyanContainer)
                        .border(
                            1.dp,
                            if (speechState == SpeechState.LISTENING) NeonAmber else ArcCyan,
                            RoundedCornerShape(10.dp)
                        )
                        .testTag("voice_mic_button")
                ) {
                    Icon(
                        imageVector = if (speechState == SpeechState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = if (speechState == SpeechState.LISTENING) NeonAmber else ArcCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Send Button (48dp x 48dp accessible)
                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onSubmitCommand(textInput)
                            textInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ArcCyanContainer)
                        .border(1.dp, ArcCyan, RoundedCornerShape(10.dp))
                        .testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = ArcCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
