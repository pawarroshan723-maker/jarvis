package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AutoSmsRule
import com.example.data.CallSmsLog
import com.example.system.AutoCallSmsConfig
import com.example.system.ContactEntry
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoCallSmsView(
    config: AutoCallSmsConfig,
    logs: List<CallSmsLog>,
    rules: List<AutoSmsRule>,
    contacts: List<ContactEntry> = emptyList(),
    onUpdateConfig: (AutoCallSmsConfig) -> Unit,
    onSetPreset: (String) -> Unit,
    onToggleAutoSms: (Boolean) -> Unit,
    onToggleAutoReadSms: (Boolean) -> Unit,
    onToggleAutoAnnounceCalls: (Boolean) -> Unit,
    onToggleAutoReplyMissedCalls: (Boolean) -> Unit,
    onSetCustomReplyText: (String) -> Unit,
    onSetEmergencyContact: (String) -> Unit,
    onTriggerEmergencySos: () -> Unit,
    onInsertRule: (AutoSmsRule) -> Unit,
    onDeleteRule: (Long) -> Unit,
    onToggleRule: (Long, Boolean) -> Unit,
    onSimulateCall: (String, String, Boolean) -> Unit,
    onSimulateSms: (String, String) -> Unit,
    onClearLogs: () -> Unit,
    onEnrichContactNames: () -> Unit = {},
    onLoadPreconfiguredSmsRules: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showRuleCreator by remember { mutableStateOf(false) }
    var ruleKeyword by remember { mutableStateOf("") }
    var ruleResponse by remember { mutableStateOf("") }
    var customTemplateInput by remember(config.customAutoReplyText) { mutableStateOf(config.customAutoReplyText) }
    var emergencyContactInput by remember(config.emergencyContact) { mutableStateOf(config.emergencyContact) }
    var logSearchQuery by remember { mutableStateOf("") }

    val presets = listOf(
        Pair("NORMAL", "Standard Mode"),
        Pair("DRIVING", "Driving Mode"),
        Pair("MEETING", "Meeting Mode"),
        Pair("BUSY", "Busy Mode"),
        Pair("SLEEP", "Sleep Mode")
    )

    val filteredLogs = remember(logs, logSearchQuery, contacts) {
        if (logSearchQuery.isBlank()) logs
        else {
            val q = logSearchQuery.trim().lowercase()
            logs.filter { log ->
                val resolvedName = log.contactName.ifBlank {
                    contacts.find { c ->
                        c.number == log.phoneNumber ||
                        (log.phoneNumber.isNotBlank() && c.number.filter { it.isDigit() }.endsWith(log.phoneNumber.filter { it.isDigit() }))
                    }?.name ?: ""
                }
                resolvedName.lowercase().contains(q) ||
                log.phoneNumber.lowercase().contains(q) ||
                log.messageBody.lowercase().contains(q) ||
                log.type.lowercase().contains(q)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. MASTER CONTROL & STATUS CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (config.isAutoSmsEnabled) ArcCyan else TextMuted.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auto_call_sms_master_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (config.isAutoSmsEnabled) NeonGreen else NeonAmber)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "AUTOMATIC TELEPHONY SYSTEM",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "PRESET: ${config.activePreset} | ANTI-SPAM PROTECTED",
                                    fontSize = 10.sp,
                                    color = if (config.isAutoSmsEnabled) NeonGreen else TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Switch(
                            checked = config.isAutoSmsEnabled,
                            onCheckedChange = { onToggleAutoSms(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianDark,
                                checkedTrackColor = NeonGreen,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceVariantDark
                            ),
                            modifier = Modifier.testTag("auto_sms_master_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preset Mode Selector Chips
                    Text(
                        text = "OPERATIONAL PRESETS:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArcCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presets.forEach { (mode, label) ->
                            val isSelected = config.activePreset.equals(mode, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ArcCyanContainer else SurfaceVariantDark)
                                    .border(
                                        1.dp,
                                        if (isSelected) ArcCyan else BorderCyan.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSetPreset(mode) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = ArcCyan,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = label.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) ArcCyan else TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. FEATURE TOGGLES & AUTOMATION RULES
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "VOICE & RESPONSE BEHAVIORS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArcCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Announce Callers Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Announce Caller Names",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Jarvis speaks caller identity aloud when phone rings",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = config.isAutoAnnounceCallsEnabled,
                            onCheckedChange = { onToggleAutoAnnounceCalls(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianDark,
                                checkedTrackColor = ArcCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Auto Read SMS Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Read Incoming SMS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Jarvis reads SMS sender and body aloud via TTS",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = config.isAutoReadSmsEnabled,
                            onCheckedChange = { onToggleAutoReadSms(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianDark,
                                checkedTrackColor = ArcCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Auto Reply to Missed Calls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Reply Missed Calls",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Dispatches SMS when incoming call is missed or rejected",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = config.isAutoReplyMissedCallsEnabled,
                            onCheckedChange = { onToggleAutoReplyMissedCalls(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianDark,
                                checkedTrackColor = ArcCyan
                            )
                        )
                    }
                }
            }
        }

        // 3. AUTO-REPLY TEMPLATE & EMERGENCY SOS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RESPONSE TEMPLATE & EMERGENCY SOS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArcCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customTemplateInput,
                        onValueChange = { newVal ->
                            customTemplateInput = newVal
                            onSetCustomReplyText(newVal)
                        },
                        label = { Text("Custom Auto-Reply Message", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = BorderCyan,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Emergency Contact & SOS
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = emergencyContactInput,
                            onValueChange = { newVal ->
                                emergencyContactInput = newVal
                                onSetEmergencyContact(newVal)
                            },
                            label = { Text("Emergency Contact (Name or Phone #)", fontSize = 11.sp) },
                            placeholder = { Text("Search contact name or +1234567890", fontSize = 11.sp, color = TextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonRed,
                                unfocusedBorderColor = BorderCyan,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextSecondary
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onTriggerEmergencySos,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("trigger_emergency_sos_btn")
                        ) {
                            Text("SOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Emergency Contact Quick Picker
                    if (contacts.isNotEmpty()) {
                        val matchingEmergency = remember(emergencyContactInput, contacts) {
                            if (emergencyContactInput.isBlank()) contacts.take(5)
                            else contacts.filter {
                                it.name.contains(emergencyContactInput, ignoreCase = true) ||
                                it.number.contains(emergencyContactInput, ignoreCase = true)
                            }.take(6)
                        }
                        if (matchingEmergency.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(matchingEmergency) { c ->
                                    Surface(
                                        color = ObsidianDark,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            emergencyContactInput = c.number
                                            onSetEmergencyContact(c.number)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Person, null, tint = NeonRed, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${c.name} (${c.number})", fontSize = 10.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. CUSTOM SMART SMS RULES
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "KEYWORD & CONTACT RULES (${rules.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArcCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onLoadPreconfiguredSmsRules() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "⚡ PRESETS",
                                    fontSize = 10.sp,
                                    color = NeonAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            OutlinedButton(
                                onClick = { showRuleCreator = !showRuleCreator },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (showRuleCreator) "CANCEL" else "+ NEW RULE",
                                    fontSize = 10.sp,
                                    color = ArcCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Rule Creator Form
                    AnimatedVisibility(visible = showRuleCreator) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "QUICK TEMPLATES:",
                                fontSize = 9.sp,
                                color = NeonAmber,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(listOf(
                                    "urgent" to "Jarvis Emergency Alert: Urgent priority acknowledged. Alerting user immediately. Battery: {battery}%.",
                                    "where are you" to "Jarvis Status: User is currently mobile. Battery: {battery}%, Time: {time}.",
                                    "call me" to "Jarvis Auto: Message received. User informed to call you shortly.",
                                    "meeting" to "Jarvis Auto: User is currently in a meeting. Will reply as soon as free.",
                                    "कुठे आहेस" to "जार्व्हिस ऑटो: युझर सध्या व्यस्त आहे. बॅटरी: {battery}%. लवकरच संपर्क करतील.",
                                    "कॉल कर" to "जार्व्हिस ऑटो: संदेश मिळाला आहे. युझर थोड्या वेळात कॉल करतील.",
                                    "help" to "Jarvis Emergency: Assistance request detected. Alerting user right away.",
                                    "driving" to "Jarvis Auto: User is currently driving. Voice systems active."
                                )) { (kw, tpl) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SurfaceDark)
                                            .border(1.dp, ArcCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                            .clickable {
                                                ruleKeyword = kw
                                                ruleResponse = tpl
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(kw, fontSize = 9.sp, color = ArcCyan, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = ruleKeyword,
                                onValueChange = { ruleKeyword = it },
                                label = { Text("Keyword Trigger (e.g. urgent, invoice, कॉल कर)", fontSize = 10.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ArcCyan,
                                    unfocusedBorderColor = BorderCyan,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextSecondary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = ruleResponse,
                                onValueChange = { ruleResponse = it },
                                label = { Text("Auto-Response Message ({battery}, {time}, {charging})", fontSize = 10.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ArcCyan,
                                    unfocusedBorderColor = BorderCyan,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextSecondary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (ruleKeyword.isNotBlank()) {
                                        onInsertRule(
                                            AutoSmsRule(
                                                keyword = ruleKeyword.trim(),
                                                replyTemplate = if (ruleResponse.isBlank()) "Jarvis auto-response: Received urgent request." else ruleResponse.trim(),
                                                isEnabled = true
                                            )
                                        )
                                        ruleKeyword = ""
                                        ruleResponse = ""
                                        showRuleCreator = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ArcCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("SAVE RULE", color = ObsidianDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (rules.isEmpty()) {
                        Text(
                            text = "No custom keyword rules defined. Default template will be applied.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        rules.forEach { rule ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceVariantDark.copy(alpha = 0.7f))
                                    .padding(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Keyword: \"${rule.keyword}\"",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArcCyan,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Reply: \"${rule.replyTemplate}\"",
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Switch(
                                        checked = rule.isEnabled,
                                        onCheckedChange = { onToggleRule(rule.id, it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = ObsidianDark,
                                            checkedTrackColor = NeonGreen
                                        )
                                    )
                                    IconButton(
                                        onClick = { onDeleteRule(rule.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Rule",
                                            tint = NeonRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. LIVE SIMULATION & TESTING STUDIO
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmber.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = NeonAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIVE SIMULATION & TESTING STUDIO",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonAmber,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "Trigger simulated events to test TTS announcement & auto-reply workflows directly on device",
                        fontSize = 10.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onSimulateCall("+15550199", "Tony Stark", false) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Simulate Call", fontSize = 9.sp, color = ArcCyan)
                        }

                        OutlinedButton(
                            onClick = { onSimulateCall("+15550199", "Tony Stark", true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Missed Call", fontSize = 9.sp, color = NeonAmber)
                        }

                        OutlinedButton(
                            onClick = { onSimulateSms("+15550199", "Jarvis please report urgent status update.") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Simulate SMS", fontSize = 9.sp, color = NeonGreen)
                        }
                    }
                }
            }
        }

        // 6. TELEPHONY ACTIVITY AUDIT LOG
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "TELEPHONY AUDIT LOG (${logs.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArcCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SYNC NAMES",
                            fontSize = 10.sp,
                            color = ArcCyan,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { onEnrichContactNames() }
                                .padding(4.dp)
                        )
                        if (logs.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CLEAR LOGS",
                                fontSize = 10.sp,
                                color = NeonRed,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clickable { onClearLogs() }
                                    .padding(4.dp)
                            )
                        }
                    }
                }

                if (logs.isNotEmpty()) {
                    OutlinedTextField(
                        value = logSearchQuery,
                        onValueChange = { logSearchQuery = it },
                        label = { Text("Search logs by contact name or number", fontSize = 10.sp) },
                        placeholder = { Text("e.g. John, 9876, or message text", fontSize = 10.sp, color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = ArcCyan, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (logSearchQuery.isNotBlank()) {
                                IconButton(onClick = { logSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, "Clear", tint = TextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = BorderCyan.copy(alpha = 0.4f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (filteredLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (logs.isEmpty()) "No telephony events recorded yet. Incoming calls and SMS will be logged here." else "No matching log entries found.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            items(filteredLogs) { log ->
                val timeStr = remember(log.timestamp) {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                }
                val icon = when (log.type) {
                    "MISSED_CALL" -> Icons.AutoMirrored.Filled.CallMissed
                    "INCOMING_CALL" -> Icons.Default.Call
                    "EMERGENCY_SOS" -> Icons.Default.Warning
                    else -> Icons.AutoMirrored.Filled.Message
                }
                val tint = when (log.type) {
                    "MISSED_CALL" -> NeonAmber
                    "EMERGENCY_SOS" -> NeonRed
                    "AUTO_REPLY_SMS" -> NeonGreen
                    else -> ArcCyan
                }

                val matchedContactName = remember(log.contactName, log.phoneNumber, contacts) {
                    if (log.contactName.isNotBlank() && log.contactName != log.phoneNumber) {
                        log.contactName
                    } else {
                        contacts.find { c ->
                            c.number == log.phoneNumber ||
                            (log.phoneNumber.isNotBlank() && c.number.filter { it.isDigit() }.takeLast(8) == log.phoneNumber.filter { it.isDigit() }.takeLast(8))
                        }?.name ?: ""
                    }
                }

                val hasName = matchedContactName.isNotBlank()
                val primaryName = if (hasName) matchedContactName else log.phoneNumber.ifBlank { "Unknown Caller" }
                val secondaryInfo = if (hasName && log.phoneNumber.isNotBlank()) log.phoneNumber else if (log.phoneNumber.isNotBlank()) log.phoneNumber else "Unknown / Not Saved"

                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = primaryName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasName) ArcCyan else TextPrimary
                                )
                                if (hasName) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Saved Contact",
                                        tint = NeonGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Text(
                                text = timeStr,
                                fontSize = 9.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Number & Type pill row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Text(
                                text = secondaryInfo,
                                fontSize = 10.sp,
                                color = if (hasName) TextSecondary else TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = log.type.replace("_", " "),
                                fontSize = 8.sp,
                                color = tint,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(tint.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        if (log.messageBody.isNotBlank()) {
                            Text(
                                text = log.messageBody,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Text(
                            text = "Status: ${log.status}",
                            fontSize = 9.sp,
                            color = if (log.status == "REPLIED" || log.status == "SUCCESS" || log.status == "DELIVERED") NeonGreen else TextMuted,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
