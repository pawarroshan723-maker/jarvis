package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.ArcCyanContainer
import com.example.ui.theme.BorderCyan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.voice.DspHardwareAudit
import java.util.Locale

@Composable
fun MicSensitivityCard(
    micSensitivity: Float,
    onSetMicSensitivity: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .border(1.dp, ArcCyan.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("mic_sensitivity_card")
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = ArcCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MIC SENSITIVITY CONTROL",
                        color = ArcCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ArcCyanContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = String.format(Locale.ROOT, "%.1fx GAIN", micSensitivity),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArcCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Adjust microphone pick-up sensitivity. Lower sensitivity suppresses background chatter and room noise. Higher sensitivity captures soft voices and distant speech.",
                color = TextSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = micSensitivity,
                onValueChange = { onSetMicSensitivity(it) },
                valueRange = 0.5f..2.0f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = ArcCyan,
                    activeTrackColor = ArcCyan,
                    inactiveTrackColor = BorderCyan.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("mic_sensitivity_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Pair(0.6f, "0.6x QUIET"),
                    Pair(1.0f, "1.0x NORMAL"),
                    Pair(1.5f, "1.5x HIGH"),
                    Pair(2.0f, "2.0x ULTRA")
                ).forEach { (factor, label) ->
                    val isSelected = kotlin.math.abs(micSensitivity - factor) < 0.08f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) ArcCyanContainer else SurfaceVariantDark)
                            .border(
                                1.dp,
                                if (isSelected) ArcCyan else BorderCyan.copy(alpha = 0.4f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onSetMicSensitivity(factor) }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) ArcCyan else TextSecondary,
                            fontSize = 8.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoiseDbSensitivityCard(
    noiseGateThresholdDb: Float,
    onSetNoiseGateThresholdDb: (Float, Boolean) -> Unit,
    isManualNoiseGate: Boolean,
    onSetNoiseGateMode: (Boolean) -> Unit,
    liveSnrDb: Float,
    effectiveGateThresholdDb: Float,
    isNoiseGateOpen: Boolean,
    backgroundNoiseLevel: Float,
    currentRmsDb: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .border(1.dp, ArcCyan.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("noise_db_sensitivity_card")
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = ArcCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NOISE dB SENSITIVITY & GATE",
                        color = ArcCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isManualNoiseGate) NeonAmber.copy(alpha = 0.2f) else ArcCyanContainer)
                            .border(
                                1.dp,
                                if (isManualNoiseGate) NeonAmber else ArcCyan,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onSetNoiseGateMode(!isManualNoiseGate) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("noise_gate_mode_toggle")
                    ) {
                        Text(
                            text = if (isManualNoiseGate) "MANUAL dB" else "AUTO DYNAMIC",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isManualNoiseGate) NeonAmber else ArcCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isNoiseGateOpen) NeonGreen.copy(alpha = 0.2f) else ObsidianDark)
                            .border(
                                1.dp,
                                if (isNoiseGateOpen) NeonGreen else BorderCyan.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isNoiseGateOpen) "GATE OPEN" else "GATE CLOSED",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isNoiseGateOpen) NeonGreen else TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isManualNoiseGate)
                    "Manual Mode: Signals below ${String.format(Locale.ROOT, "%.1f", noiseGateThresholdDb)}dB above noise floor are rejected. Speech exceeding this threshold triggers recognition."
                else
                    "Auto Mode: Dynamically tracks background noise floor and calculates adaptive noise gate (~${String.format(Locale.ROOT, "%.1f", effectiveGateThresholdDb)}dB).",
                color = TextSecondary,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 11.5.sp
            )

            // Telemetry Strip
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(ObsidianDark)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "RMS: ${currentRmsDb.toInt()}dB",
                    fontSize = 7.5.sp,
                    color = ArcCyan,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "NOISE: ${backgroundNoiseLevel.toInt()}dB",
                    fontSize = 7.5.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "SNR: ${liveSnrDb.toInt()}dB",
                    fontSize = 7.5.sp,
                    color = if (liveSnrDb >= effectiveGateThresholdDb) NeonGreen else TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "GATE: ${String.format(Locale.ROOT, "%.1f", effectiveGateThresholdDb)}dB",
                    fontSize = 7.5.sp,
                    color = if (isManualNoiseGate) NeonAmber else ArcCyan,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GATE SENSITIVITY THRESHOLD",
                    color = TextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${String.format(Locale.ROOT, "%.1f", noiseGateThresholdDb)} dB",
                    color = if (isManualNoiseGate) NeonAmber else ArcCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Slider(
                value = noiseGateThresholdDb,
                onValueChange = { onSetNoiseGateThresholdDb(it, true) },
                valueRange = 1.0f..15.0f,
                steps = 13,
                colors = SliderDefaults.colors(
                    thumbColor = if (isManualNoiseGate) NeonAmber else ArcCyan,
                    activeTrackColor = if (isManualNoiseGate) NeonAmber else ArcCyan,
                    inactiveTrackColor = BorderCyan.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .testTag("noise_db_gate_slider")
            )

            // Quick Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Pair(1.5f, "1.5dB QUIET"),
                    Pair(3.0f, "3.0dB NORMAL"),
                    Pair(6.0f, "6.0dB FAN/AC"),
                    Pair(10.0f, "10.0dB LOUD")
                ).forEach { (threshold, label) ->
                    val isSelected = isManualNoiseGate && kotlin.math.abs(noiseGateThresholdDb - threshold) < 0.2f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) NeonAmber.copy(alpha = 0.25f) else SurfaceVariantDark)
                            .border(
                                1.dp,
                                if (isSelected) NeonAmber else BorderCyan.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onSetNoiseGateThresholdDb(threshold, true) }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) NeonAmber else TextSecondary,
                            fontSize = 7.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DspHardwareAuditCard(
    dspHardwareAudit: DspHardwareAudit?,
    onRefreshDspAudit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .border(
                1.dp,
                when {
                    dspHardwareAudit?.isActualHardware == true -> NeonGreen.copy(alpha = 0.8f)
                    dspHardwareAudit?.isSimulator == true -> NeonAmber.copy(alpha = 0.8f)
                    else -> ArcCyan.copy(alpha = 0.6f)
                },
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp)
            .testTag("dsp_hardware_audit_card")
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = when {
                            dspHardwareAudit?.isActualHardware == true -> NeonGreen
                            dspHardwareAudit?.isSimulator == true -> NeonAmber
                            else -> ArcCyan
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DSP: ACTUAL VS SIMULATOR",
                        color = ArcCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                dspHardwareAudit?.isActualHardware == true -> NeonGreen.copy(alpha = 0.2f)
                                dspHardwareAudit?.isSimulator == true -> NeonAmber.copy(alpha = 0.2f)
                                else -> ArcCyanContainer
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                dspHardwareAudit?.isActualHardware == true -> NeonGreen
                                dspHardwareAudit?.isSimulator == true -> NeonAmber
                                else -> ArcCyan
                            },
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = dspHardwareAudit?.badgeLabel ?: "DSP: AUDITING",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            dspHardwareAudit?.isActualHardware == true -> NeonGreen
                            dspHardwareAudit?.isSimulator == true -> NeonAmber
                            else -> ArcCyan
                        },
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Technical Verdict Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(ObsidianDark)
                    .padding(6.dp)
            ) {
                Column {
                    Text(
                        text = "TECHNICAL VERDICT:",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = dspHardwareAudit?.technicalVerdict ?: "Auditing Audio HAL...",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            dspHardwareAudit?.isActualHardware == true -> NeonGreen
                            dspHardwareAudit?.isSimulator == true -> NeonAmber
                            else -> ArcCyan
                        },
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dspHardwareAudit?.detailedExplanation ?: "",
                        fontSize = 7.5.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 10.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Technical Breakdown Grid
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ENVIRONMENT:", fontSize = 7.5.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Text(
                        text = if (dspHardwareAudit?.isSimulator == true) "EMULATOR / SIMULATOR" else "PHYSICAL HARDWARE",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dspHardwareAudit?.isSimulator == true) NeonAmber else NeonGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("CHIPSET / SOC:", fontSize = 7.5.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Text(
                        text = dspHardwareAudit?.chipsetSoc ?: "Unknown",
                        fontSize = 7.sp,
                        color = ArcCyan,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("HARDWARE VENDOR:", fontSize = 7.5.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Text(
                        text = dspHardwareAudit?.hardwareVendor ?: "Generic",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dspHardwareAudit?.isActualHardware == true) NeonGreen else TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("NOISE SUPPRESSOR (NS):", fontSize = 7.5.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Text(
                        text = if (dspHardwareAudit?.isNoiseSuppressorAvailable == true) "AVAILABLE (ON-CHIP)" else "UNAVAILABLE",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dspHardwareAudit?.isNoiseSuppressorAvailable == true) NeonGreen else TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ECHO CANCELER (AEC):", fontSize = 7.5.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Text(
                        text = if (dspHardwareAudit?.isEchoCancelerAvailable == true) "AVAILABLE (ON-CHIP)" else "UNAVAILABLE",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dspHardwareAudit?.isEchoCancelerAvailable == true) NeonGreen else TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(ArcCyanContainer)
                    .border(1.dp, ArcCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable { onRefreshDspAudit() }
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = ArcCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "RE-VERIFY DSP HARDWARE SILICON",
                        color = ArcCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceProfileControllerCard(
    speechRate: Float,
    onSetSpeechRate: (Float) -> Unit,
    speechPitch: Float,
    onSetSpeechPitch: (Float) -> Unit,
    onTestTts: () -> Unit,
    onTestMarathiTts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .border(1.dp, NeonGreen.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("voice_synthesis_controller_card")
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "JARVIS VOICE PROFILE (WOMAN VOICE)",
                        color = NeonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NeonGreen.copy(alpha = 0.15f))
                        .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "FEMALE VOICE ONLY",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Jarvis voice synthesis is strictly locked to female/woman voice profiles across native Marathi (mr-IN) and Indian English (en-IN). Russian accent distortion is permanently eliminated.",
                color = TextSecondary,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 11.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Speech Rate Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SPEECH RATE (SPEED)",
                    color = ArcCyan,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format(Locale.ROOT, "%.2fx", speechRate),
                    color = ArcCyan,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Slider(
                value = speechRate,
                onValueChange = { onSetSpeechRate(it) },
                valueRange = 0.5f..2.0f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = ArcCyan,
                    activeTrackColor = ArcCyan,
                    inactiveTrackColor = BorderCyan.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth().height(26.dp).testTag("speech_rate_slider")
            )

            // Speech Pitch Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SPEECH PITCH (TONE)",
                    color = NeonGreen,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format(Locale.ROOT, "%.2fx", speechPitch),
                    color = NeonGreen,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Slider(
                value = speechPitch,
                onValueChange = { onSetSpeechPitch(it) },
                valueRange = 0.5f..2.0f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = NeonGreen,
                    activeTrackColor = NeonGreen,
                    inactiveTrackColor = NeonGreen.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth().height(26.dp).testTag("speech_pitch_slider")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Test Voice Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ArcCyanContainer)
                        .border(1.dp, ArcCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable { onTestTts() }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "TEST ENGLISH VOICE",
                        color = ArcCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonGreen.copy(alpha = 0.15f))
                        .border(1.dp, NeonGreen.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable { onTestMarathiTts() }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "मराठी व्हॉईस टेस्ट करा",
                        color = NeonGreen,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
