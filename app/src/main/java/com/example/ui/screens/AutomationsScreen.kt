package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActionTypes
import com.example.data.AutomationRule
import com.example.data.TriggerTypes
import com.example.hardware.InstalledAppInfo
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsScreen(
    rules: List<AutomationRule>,
    installedApps: List<InstalledAppInfo>,
    showAddDialog: Boolean,
    onShowAddDialog: (Boolean) -> Unit,
    onToggleRule: (AutomationRule) -> Unit,
    onDeleteRule: (AutomationRule) -> Unit,
    onTestRule: (AutomationRule) -> Unit,
    onAddRule: (name: String, triggerType: String, threshold: Float, actionType: String, actionParam: String) -> Unit,
    onPlayMusic: (String, String?) -> Unit = { _, _ -> },
    onLaunchApp: (String) -> Unit = {},
    onLoadPreconfiguredRules: () -> Unit = {},
    onControlMedia: (com.example.hardware.MediaControlAction) -> Unit = {},
    isWakeLockActive: Boolean = false,
    onToggleWakeLock: () -> Unit = {},
    onIncreaseVolume: () -> Unit = {},
    onDecreaseVolume: () -> Unit = {},
    onWakeScreen: () -> Unit = {},
    currentTrackTitle: String? = null,
    isLocalPlaying: Boolean = false,
    conflictWarnings: List<com.example.sensor.SensorConflictWarning> = emptyList(),
    modifier: Modifier = Modifier
) {
    var songSearchQuery by remember { mutableStateOf("") }
    var selectedPlayer by remember { mutableStateOf("auto") }
    var selectedAppToLaunch by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var appDropdownExpanded by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var showConflictDetails by remember { mutableStateOf(false) }

    val filteredRules = remember(rules, selectedCategoryFilter) {
        when (selectedCategoryFilter) {
            "GESTURES" -> rules.filter { it.triggerType == TriggerTypes.SHAKE || it.triggerType.contains("PROXIMITY") }
            "POWER" -> rules.filter { it.triggerType.contains("BATTERY") || it.triggerType.contains("CHARGER") }
            "LIGHT" -> rules.filter { it.triggerType.contains("LIGHT") }
            "ORIENTATION" -> rules.filter { it.triggerType.contains("FLIP") || it.triggerType.contains("ORIENTATION") }
            else -> rules
        }
    }

    Box(modifier = modifier.fillMaxSize().background(ObsidianDark)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ACTIVE AUTOMATION WORKFLOWS",
                            color = ArcCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${rules.count { it.isEnabled }} OF ${rules.size} RULES ARMED IN LOCAL CORE",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ArcCyanContainer)
                            .border(1.dp, ArcCyan, RoundedCornerShape(6.dp))
                            .clickable { onShowAddDialog(true) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("add_rule_top_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Rule",
                                tint = ArcCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "NEW RULE",
                                color = ArcCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Sensor Conflict & Loop Guard Status Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreen.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "SENSOR CONFLICT & LOOP GUARD",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonGreen,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = if (conflictWarnings.isEmpty()) "All sensors optimized • Redundant triggers blocked" else "${conflictWarnings.size} rules auto-stabilized with edge guards",
                                        fontSize = 9.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            if (conflictWarnings.isNotEmpty()) {
                                TextButton(
                                    onClick = { showConflictDetails = !showConflictDetails },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text(
                                        text = if (showConflictDetails) "HIDE" else "DETAILS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArcCyan,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Feature badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ObsidianDark)
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text("✓ 100% Vol Redundancy Guard", fontSize = 8.sp, color = ArcCyan, fontFamily = FontFamily.Monospace)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ObsidianDark)
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text("✓ Edge-Triggering Active", fontSize = 8.sp, color = NeonAmber, fontFamily = FontFamily.Monospace)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ObsidianDark)
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text("✓ Pocket Safe", fontSize = 8.sp, color = NeonGreen, fontFamily = FontFamily.Monospace)
                            }
                        }

                        AnimatedVisibility(visible = showConflictDetails && conflictWarnings.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                conflictWarnings.forEach { warning ->
                                    Text(
                                        text = "• ${warning.rule1Name} ↔ ${warning.rule2Name}: ${warning.conflictReason}",
                                        fontSize = 9.sp,
                                        color = when (warning.severity) {
                                            com.example.sensor.ConflictSeverity.CRITICAL -> NeonRed
                                            com.example.sensor.ConflictSeverity.WARNING -> NeonAmber
                                            else -> TextSecondary
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Category Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "ALL" to "All (${rules.size})",
                        "POWER" to "⚡ Power & Battery",
                        "GESTURES" to "🛡️ Gestures",
                        "ORIENTATION" to "🤫 Flip & Orientation",
                        "LIGHT" to "☀️ Light"
                    ).forEach { (catKey, label) ->
                        val isSel = selectedCategoryFilter == catKey
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) ArcCyan else SurfaceDark)
                                .border(1.dp, if (isSel) ArcCyan else BorderCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .clickable { selectedCategoryFilter = catKey }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) ObsidianDark else TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // 1. PRE-CONFIGURED HIGH AUTOMATION PRESETS BANNER
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = NeonAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PRE-CONFIGURED AUTOMATION RULES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonAmber,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Button(
                                onClick = { onLoadPreconfiguredRules() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonAmber),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⚡ LOAD 11+ PRESETS",
                                    fontSize = 10.sp,
                                    color = ObsidianDark,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1-Tap deploy comprehensive autonomous rules:\n• 🛡️ Pocket-Guard Shake Torch (Proximity Safe)\n• 🤫 Flip Face-Down Silence (Meeting Privacy)\n• 🔊 Desk Face-Up Restore Volume (75%)\n• 🔋 Low Battery (<20%) & 💯 100% Full Charge Voice Alerts\n• ⚡ Charger Plugged & Unplugged Spoken Alerts\n• ☀️ Sunlight Outdoor Max Volume & 🌙 Night Bedside Silence\n• 🕒 Phone Picked Up Time Announcement",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 2. PLAY SONGS & OPEN APPS CONTROL CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ArcCyan.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = ArcCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PLAYER CORE & LOCKSCREEN CONTROLS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ArcCyan,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isWakeLockActive) ArcCyan.copy(alpha = 0.2f) else SurfaceVariantDark)
                                    .border(1.dp, if (isWakeLockActive) ArcCyan else BorderCyan.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .clickable { onToggleWakeLock() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isWakeLockActive) "⚡ WAKELOCK: ON" else "💤 WAKELOCK: OFF",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWakeLockActive) ArcCyan else TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // WakeLock Info Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(ArcCyan.copy(alpha = 0.08f))
                                .border(1.dp, ArcCyan.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🔒 Lockscreen Ready: WakeLock keeps CPU active so commands, YouTube autoplay, local media, and background automations continue when device is locked/sleeping.",
                                fontSize = 8.5.sp,
                                color = ArcCyan.copy(alpha = 0.9f),
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Player Preference Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "auto" to "⚡ Auto",
                                "youtube" to "▶ YouTube",
                                "spotify" to "🟩 Spotify",
                                "vlc" to "🟧 VLC",
                                "local" to "📁 Local File"
                            ).forEach { (mode, label) ->
                                val isSelected = selectedPlayer == mode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) ArcCyan else SurfaceVariantDark)
                                        .border(1.dp, if (isSelected) ArcCyan else BorderCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .clickable { selectedPlayer = mode }
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 8.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) ObsidianDark else TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Song Player Input
                        OutlinedTextField(
                            value = songSearchQuery,
                            onValueChange = { songSearchQuery = it },
                            placeholder = { Text("Song title or artist (e.g. Believer, Arijit)", color = TextMuted, fontSize = 11.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                Button(
                                    onClick = { onPlayMusic(songSearchQuery, selectedPlayer) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArcCyan),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text("▶ PLAY", color = ObsidianDark, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArcCyan,
                                unfocusedBorderColor = BorderCyan,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextSecondary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Media Playback Controls Row (Prev, Play/Pause, Stop, Next, Vol -, Vol +, Wake)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "⏮" to { onControlMedia(com.example.hardware.MediaControlAction.PREVIOUS) },
                                "⏯" to { onControlMedia(com.example.hardware.MediaControlAction.TOGGLE) },
                                "⏹" to { onControlMedia(com.example.hardware.MediaControlAction.STOP) },
                                "⏭" to { onControlMedia(com.example.hardware.MediaControlAction.NEXT) },
                                "🔉 -" to { onDecreaseVolume() },
                                "🔊 +" to { onIncreaseVolume() },
                                "☀️ Wake" to { onWakeScreen() }
                            ).forEach { (label, action) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceVariantDark)
                                        .border(1.dp, BorderCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .clickable { action() }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        if (currentTrackTitle != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "NOW PLAYING: $currentTrackTitle",
                                fontSize = 8.5.sp,
                                color = ArcCyan,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Music Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "" to "▶ Music Player",
                                "Believer" to "🎵 Believer",
                                "Arijit Singh" to "🎵 Arijit Singh",
                                "Lofi Chill" to "🎵 Lofi"
                            ).forEach { (query, label) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceVariantDark)
                                        .border(1.dp, BorderCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .clickable { onPlayMusic(query, selectedPlayer) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 9.sp,
                                        color = TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Fast Device App Shortcuts
                        Text(
                            text = "INSTANT DEVICE APPS:",
                            fontSize = 9.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "whatsapp" to "WhatsApp",
                                "youtube" to "YouTube",
                                "vlc" to "VLC",
                                "camera" to "Camera",
                                "chrome" to "Chrome",
                                "settings" to "Settings"
                            ).forEach { (appKey, label) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ArcCyanContainer)
                                        .border(1.dp, ArcCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .clickable { onLaunchApp(appKey) }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 9.sp,
                                        color = ArcCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Dropdown for ANY installed app on the device
                        ExposedDropdownMenuBox(
                            expanded = appDropdownExpanded,
                            onExpandedChange = { appDropdownExpanded = !appDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedAppToLaunch?.appName ?: "Select any installed app to open (${installedApps.size} apps)",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appDropdownExpanded) },
                                leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(16.dp)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ArcCyan,
                                    unfocusedBorderColor = BorderCyan,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextSecondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = appDropdownExpanded,
                                onDismissRequest = { appDropdownExpanded = false },
                                modifier = Modifier.background(SurfaceDark)
                            ) {
                                installedApps.take(30).forEach { app ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = app.appName,
                                                fontSize = 11.sp,
                                                color = TextPrimary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        },
                                        onClick = {
                                            selectedAppToLaunch = app
                                            appDropdownExpanded = false
                                            onLaunchApp(app.packageName)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (filteredRules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (rules.isEmpty()) "NO AUTOMATION RULES CONFIGURED\nTAP 'NEW RULE' OR 'LOAD 11+ PRESETS'"
                            else "NO RULES MATCHING CURRENT FILTER",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(filteredRules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { onToggleRule(rule) },
                        onDelete = { onDeleteRule(rule) },
                        onTest = { onTestRule(rule) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { onShowAddDialog(true) },
            containerColor = ArcCyan,
            contentColor = ObsidianDark,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_add_rule")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Workflow")
        }
    }

    if (showAddDialog) {
        AddRuleDialog(
            installedApps = installedApps,
            onDismiss = { onShowAddDialog(false) },
            onConfirm = { name, trigger, threshold, action, param ->
                onAddRule(name, trigger, threshold, action, param)
            }
        )
    }
}

@Composable
fun RuleCard(
    rule: AutomationRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .border(
                1.dp,
                if (rule.isEnabled) ArcCyan.copy(alpha = 0.5f) else BorderCyan.copy(alpha = 0.3f),
                RoundedCornerShape(10.dp)
            )
            .padding(14.dp)
            .testTag("rule_card_${rule.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rule.name,
                color = if (rule.isEnabled) TextPrimary else TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )

            Switch(
                checked = rule.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ArcCyan,
                    checkedTrackColor = ArcCyanContainer,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceVariantDark
                ),
                modifier = Modifier.testTag("rule_switch_${rule.id}")
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Trigger & Action Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceVariantDark)
                    .border(1.dp, NeonAmber.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "TRIGGER: ${rule.triggerType}",
                    color = NeonAmber,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text("->", color = TextMuted, fontSize = 10.sp)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceVariantDark)
                    .border(1.dp, ArcCyan.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "ACTION: ${rule.actionType}",
                    color = ArcCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (rule.actionParam.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Payload: \"${rule.actionParam}\"",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Footer with stats & actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val lastTime = if (rule.lastTriggeredTime > 0) {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(rule.lastTriggeredTime))
            } else "NEVER"

            Text(
                text = "TRIGGERS: ${rule.triggerCount} // LAST: $lastTime",
                color = TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonGreen.copy(alpha = 0.15f))
                        .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable { onTest() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("test_rule_${rule.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Test Trigger",
                            tint = NeonGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "TEST",
                            color = NeonGreen,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_rule_${rule.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Rule",
                        tint = NeonRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    installedApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, triggerType: String, threshold: Float, actionType: String, actionParam: String) -> Unit
) {
    var ruleName by remember { mutableStateOf("") }
    var selectedTrigger by remember { mutableStateOf(TriggerTypes.SHAKE) }
    var thresholdText by remember { mutableStateOf("26.0") }
    var selectedAction by remember { mutableStateOf(ActionTypes.TOGGLE_FLASHLIGHT) }
    var actionParam by remember { mutableStateOf("") }

    var triggerExpanded by remember { mutableStateOf(false) }
    var actionExpanded by remember { mutableStateOf(false) }

    val quickTemplates = listOf(
        "⚡ Shake Flashlight" to Triple(TriggerTypes.SHAKE, "26.0", ActionTypes.TOGGLE_FLASHLIGHT),
        "🤫 Face-Down Silence" to Triple(TriggerTypes.FLIP_FACE_DOWN, "0", ActionTypes.MUTE_ALL),
        "🔋 Battery <20% Alert" to Triple(TriggerTypes.BATTERY_LOW, "20.0", ActionTypes.SPEAK_TTS),
        "💯 Battery 100% Notice" to Triple(TriggerTypes.BATTERY_FULL, "100.0", ActionTypes.SPEAK_TTS),
        "🔌 Charger Plugged Alert" to Triple(TriggerTypes.CHARGER_CONNECTED, "0", ActionTypes.SPEAK_TTS),
        "☀️ Outdoor Max Volume" to Triple(TriggerTypes.LIGHT_ABOVE, "1000.0", ActionTypes.MAX_VOLUME),
        "🌙 Night Bedside Silence" to Triple(TriggerTypes.LIGHT_BELOW, "5.0", ActionTypes.SET_VOLUME),
        "🕒 Phone Picked Up Time" to Triple(TriggerTypes.ORIENTATION_UPRIGHT, "0", ActionTypes.ANNOUNCE_TIME),
        "📸 Quick Camera on Shake" to Triple(TriggerTypes.SHAKE, "30.0", ActionTypes.LAUNCH_CAMERA)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text(
                text = "CREATE AUTOMATION RULE",
                color = ArcCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Quick Templates Carousel
                Text(
                    text = "1-TAP QUICK TEMPLATES:",
                    fontSize = 9.sp,
                    color = NeonAmber,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickTemplates) { (tmplName, triple) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, ArcCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .clickable {
                                    ruleName = tmplName.substringAfter(" ")
                                    selectedTrigger = triple.first
                                    thresholdText = triple.second
                                    selectedAction = triple.third
                                    if (triple.third == ActionTypes.SPEAK_TTS) {
                                        actionParam = when (triple.first) {
                                            TriggerTypes.BATTERY_LOW -> "Battery power depleted below twenty percent. Please connect charger."
                                            TriggerTypes.BATTERY_FULL -> "Battery fully charged at one hundred percent, sir."
                                            TriggerTypes.CHARGER_CONNECTED -> "Power source connected. Systems recharging."
                                            else -> "Automation rule triggered, sir."
                                        }
                                    } else if (triple.third == ActionTypes.SET_VOLUME) {
                                        actionParam = "20"
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tmplName,
                                fontSize = 9.sp,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Name
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Rule Name", color = TextMuted) },
                    placeholder = { Text("e.g. Night Torch Trigger", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ArcCyan,
                        unfocusedBorderColor = BorderCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Trigger Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = triggerExpanded,
                    onExpandedChange = { triggerExpanded = !triggerExpanded }
                ) {
                    OutlinedTextField(
                        value = TriggerTypes.ALL.firstOrNull { it.first == selectedTrigger }?.second ?: selectedTrigger,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Environmental Trigger", color = TextMuted) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = triggerExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = BorderCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = triggerExpanded,
                        onDismissRequest = { triggerExpanded = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        TriggerTypes.ALL.forEach { (type, desc) ->
                            DropdownMenuItem(
                                text = { Text(desc, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                                onClick = {
                                    selectedTrigger = type
                                    triggerExpanded = false
                                    // Set default threshold
                                    if (type == TriggerTypes.LIGHT_BELOW) thresholdText = "5.0"
                                    if (type == TriggerTypes.LIGHT_ABOVE) thresholdText = "800.0"
                                    if (type == TriggerTypes.BATTERY_LOW) thresholdText = "20.0"
                                    if (type == TriggerTypes.SHAKE) thresholdText = "26.0"
                                }
                            )
                        }
                    }
                }

                // Threshold if relevant
                if (selectedTrigger == TriggerTypes.LIGHT_BELOW || selectedTrigger == TriggerTypes.LIGHT_ABOVE ||
                    selectedTrigger == TriggerTypes.BATTERY_LOW || selectedTrigger == TriggerTypes.SHAKE
                ) {
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it },
                        label = { Text("Threshold Value (Lux / % / Force)", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = BorderCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Action Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = actionExpanded,
                    onExpandedChange = { actionExpanded = !actionExpanded }
                ) {
                    OutlinedTextField(
                        value = ActionTypes.ALL.firstOrNull { it.first == selectedAction }?.second ?: selectedAction,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Execution Action", color = TextMuted) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = BorderCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = actionExpanded,
                        onDismissRequest = { actionExpanded = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        ActionTypes.ALL.forEach { (type, desc) ->
                            DropdownMenuItem(
                                text = { Text(desc, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                                onClick = {
                                    selectedAction = type
                                    actionExpanded = false
                                }
                            )
                        }
                    }
                }

                // Parameter
                if (selectedAction == ActionTypes.SPEAK_TTS) {
                    OutlinedTextField(
                        value = actionParam,
                        onValueChange = { actionParam = it },
                        label = { Text("Spoken Voice Text", color = TextMuted) },
                        placeholder = { Text("Announcement text...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = BorderCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (selectedAction == ActionTypes.LAUNCH_APP) {
                    OutlinedTextField(
                        value = actionParam,
                        onValueChange = { actionParam = it },
                        label = { Text("App Name or Package", color = TextMuted) },
                        placeholder = { Text("e.g. camera, calculator, chrome", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = BorderCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (selectedAction == ActionTypes.SET_VOLUME) {
                    OutlinedTextField(
                        value = actionParam,
                        onValueChange = { actionParam = it },
                        label = { Text("Target Volume % (0-100)", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = BorderCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (selectedAction == ActionTypes.PLAY_MUSIC) {
                    OutlinedTextField(
                        value = actionParam,
                        onValueChange = { actionParam = it },
                        label = { Text("Song Title / Artist (Optional)", color = TextMuted) },
                        placeholder = { Text("e.g. Believer, or leave empty for default player", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = BorderCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val threshold = thresholdText.toFloatOrNull() ?: 0f
                    val name = if (ruleName.isBlank()) "Custom Rule ${System.currentTimeMillis() % 1000}" else ruleName
                    onConfirm(name, selectedTrigger, threshold, selectedAction, actionParam)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArcCyan,
                    contentColor = ObsidianDark
                ),
                modifier = Modifier.testTag("save_rule_button")
            ) {
                Text("SAVE RULE", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextMuted, fontFamily = FontFamily.Monospace)
            }
        }
    )
}
