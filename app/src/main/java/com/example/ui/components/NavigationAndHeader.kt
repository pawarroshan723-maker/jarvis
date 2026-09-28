package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.NeonRed
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.voice.VoiceLanguage

@Composable
fun JarvisTopBar(
    isServiceRunning: Boolean,
    isOnlineEnabled: Boolean,
    isHighThinkingEnabled: Boolean,
    onToggleService: () -> Unit,
    onToggleOnline: (Boolean) -> Unit,
    onToggleThinking: (Boolean) -> Unit,
    selectedLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    onCycleLanguage: () -> Unit = {},
    selectedModelTier: com.example.engine.GeminiModelTier = com.example.engine.GeminiModelTier.AUTO_CASCADE,
    onCycleModelTier: () -> Unit = {},
    isAutoFallbackEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .statusBarsPadding()
            .border(width = 1.dp, color = BorderCyan.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // TOP ROW: Title Brand + Core Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Brand Title with Status Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (isServiceRunning) NeonGreen else NeonAmber)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "J.A.R.V.I.S.",
                        color = ArcCyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = if (isServiceRunning) "CORE: ACTIVE" else "CORE: STANDBY",
                        color = if (isServiceRunning) NeonGreen else TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Background Service Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isServiceRunning) ArcCyanContainer else SurfaceVariantDark)
                        .border(
                            1.dp,
                            if (isServiceRunning) ArcCyan else BorderCyan.copy(alpha = 0.6f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = androidx.compose.material3.ripple(bounded = true, color = ArcCyan),
                            onClick = onToggleService
                        )
                        .padding(horizontal = 7.dp, vertical = 6.dp)
                        .testTag("service_toggle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Toggle Service",
                            tint = if (isServiceRunning) ArcCyan else TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isServiceRunning) "ACTIVE" else "IDLE",
                            color = if (isServiceRunning) ArcCyan else TextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Cloud Toggle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isOnlineEnabled) ArcCyanContainer else SurfaceVariantDark)
                        .border(
                            1.dp,
                            if (isOnlineEnabled) ArcCyan.copy(alpha = 0.8f) else BorderCyan.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = androidx.compose.material3.ripple(bounded = true, color = ArcCyan),
                            onClick = { onToggleOnline(!isOnlineEnabled) }
                        )
                        .testTag("online_intelligence_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isOnlineEnabled) Icons.Default.Cloud else Icons.Default.CloudOff,
                        contentDescription = "Toggle Cloud AI",
                        tint = if (isOnlineEnabled) ArcCyan else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // High Thinking Toggle
                if (isOnlineEnabled) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isHighThinkingEnabled) NeonAmber.copy(alpha = 0.15f) else SurfaceVariantDark)
                            .border(
                                1.dp,
                                if (isHighThinkingEnabled) NeonAmber.copy(alpha = 0.8f) else BorderCyan.copy(alpha = 0.4f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = true, color = NeonAmber),
                                onClick = { onToggleThinking(!isHighThinkingEnabled) }
                            )
                            .testTag("high_thinking_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Toggle High Thinking",
                            tint = if (isHighThinkingEnabled) NeonAmber else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // SECOND ROW: Intelligence Model Pill + Language Pill + DSP Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Model Selector Pill (Interactive)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isOnlineEnabled) ArcCyanContainer else SurfaceVariantDark)
                    .border(
                        1.dp,
                        if (isOnlineEnabled) ArcCyan.copy(alpha = 0.7f) else BorderCyan.copy(alpha = 0.4f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(
                        enabled = isOnlineEnabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = androidx.compose.material3.ripple(bounded = true, color = ArcCyan),
                        onClick = onCycleModelTier
                    )
                    .padding(horizontal = 7.dp, vertical = 5.dp)
                    .testTag("gemini_model_tier_selector"),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Gemini Model",
                        tint = if (isOnlineEnabled) ArcCyan else TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOnlineEnabled) selectedModelTier.shortLabel else "OFFLINE CORE",
                        color = if (isOnlineEnabled) ArcCyan else TextSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.weight(1f)
                    )
                    if (isOnlineEnabled && isAutoFallbackEnabled) {
                        Text(
                            text = "AUTO",
                            color = NeonGreen,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            softWrap = false
                        )
                    }
                }
            }

            // Language Mode Selector Pill (Interactive)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceVariantDark)
                    .border(1.dp, BorderCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = androidx.compose.material3.ripple(bounded = true, color = ArcCyan),
                        onClick = onCycleLanguage
                    )
                    .padding(horizontal = 7.dp, vertical = 5.dp)
                    .testTag("language_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Language Mode",
                        tint = TextPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = selectedLanguage.shortLabel,
                        color = TextPrimary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }

            // DSP Noise Filter Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianDark)
                    .border(1.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "DSP: ON",
                    color = NeonGreen,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun JarvisBottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = SurfaceDark,
        contentColor = ArcCyan,
        tonalElevation = 8.dp,
        modifier = modifier.border(width = 1.dp, color = BorderCyan.copy(alpha = 0.5f))
    ) {
        val items = listOf(
            Triple(0, "Core", Icons.Default.Mic),
            Triple(1, "Comms", Icons.Default.Phone),
            Triple(2, "Sensors", Icons.Default.Sensors),
            Triple(3, "Rules", Icons.Default.DeveloperBoard),
            Triple(4, "Console", Icons.AutoMirrored.Filled.ListAlt)
        )

        items.forEach { (index, label, icon) ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ObsidianDark,
                    selectedTextColor = ArcCyan,
                    indicatorColor = ArcCyan,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                ),
                modifier = Modifier.testTag("nav_tab_$index")
            )
        }
    }
}
