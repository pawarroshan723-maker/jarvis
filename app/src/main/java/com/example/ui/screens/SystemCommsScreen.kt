package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.system.ContactEntry
import com.example.system.PermissionStatus
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemCommsScreen(
    permissions: List<PermissionStatus>,
    onRequestAllPermissions: () -> Unit,
    onRequestSinglePermission: (String) -> Unit,
    onRefreshPermissions: () -> Unit,
    onMakeCall: (String) -> Unit,
    onSendSms: (String, String) -> Unit,
    onSetAlarm: (Int, Int, String) -> Unit,
    onSetTimer: (Int, String) -> Unit,
    onShowAlarms: () -> Unit,
    onShowTimers: () -> Unit,
    onOpenCalendar: () -> Unit,
    contacts: List<ContactEntry>,
    onSearchContacts: (String) -> Unit,
    systemStatusMessage: String?,
    onDismissStatusMessage: () -> Unit,
    autoCallSmsConfig: com.example.system.AutoCallSmsConfig = com.example.system.AutoCallSmsConfig(),
    callSmsLogs: List<com.example.data.CallSmsLog> = emptyList(),
    autoSmsRules: List<com.example.data.AutoSmsRule> = emptyList(),
    onUpdateAutoCallSmsConfig: (com.example.system.AutoCallSmsConfig) -> Unit = {},
    onSetAutoCallPreset: (String) -> Unit = {},
    onToggleAutoSms: (Boolean) -> Unit = {},
    onToggleAutoReadSms: (Boolean) -> Unit = {},
    onToggleAutoAnnounceCalls: (Boolean) -> Unit = {},
    onToggleAutoReplyMissedCalls: (Boolean) -> Unit = {},
    onSetCustomAutoReplyText: (String) -> Unit = {},
    onSetEmergencyContact: (String) -> Unit = {},
    onTriggerEmergencySos: () -> Unit = {},
    onInsertAutoSmsRule: (com.example.data.AutoSmsRule) -> Unit = {},
    onDeleteAutoSmsRule: (Long) -> Unit = {},
    onToggleAutoSmsRule: (Long, Boolean) -> Unit = { _, _ -> },
    onSimulateIncomingCall: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onSimulateIncomingSms: (String, String) -> Unit = { _, _ -> },
    onClearCallSmsLogs: () -> Unit = {},
    onEnrichContactNames: () -> Unit = {},
    onLoadPreconfiguredSmsRules: () -> Unit = {},
    scheduledTasks: List<com.example.data.ScheduledTask> = emptyList(),
    onCreateScheduledTask: (
        title: String,
        taskType: String,
        phone: String,
        recipientName: String,
        messageText: String,
        hour: Int,
        minute: Int,
        intervalMinutes: Int,
        timezoneId: String,
        repeatFreq: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    onToggleScheduledTask: (Long, Boolean) -> Unit = { _, _ -> },
    onExecuteScheduledTaskNow: (Long) -> Unit = {},
    onDeleteScheduledTask: (Long) -> Unit = {},
    onDeleteAllScheduledTasks: () -> Unit = {},
    onDeleteCompletedScheduledTasks: () -> Unit = {},
    onUpdateScheduledTask: (com.example.data.ScheduledTask) -> Unit = {},
    onSpeakTaskResult: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("Auto Call & SMS", "Schedule & Auto Text", "Permissions", "Call & SMS", "Alarms & Timers", "Contacts")

    LaunchedEffect(Unit) {
        onRefreshPermissions()
        onSearchContacts("")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
    ) {
        // Status Banner if any
        AnimatedVisibility(visible = systemStatusMessage != null) {
            systemStatusMessage?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ArcCyanContainer.copy(alpha = 0.9f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = msg,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "OK",
                        color = ArcCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDismissStatusMessage() }
                            .padding(8.dp)
                    )
                }
            }
        }

        // Sub Navigation Header
        ScrollableTabRow(
            selectedTabIndex = subTab,
            containerColor = SurfaceDark,
            contentColor = ArcCyan,
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = subTab == index,
                    onClick = { subTab = index },
                    text = {
                        Text(
                            text = title.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = if (subTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            color = if (subTab == index) ArcCyan else TextMuted
                        )
                    },
                    modifier = Modifier.testTag("sub_tab_$index")
                )
            }
        }

        when (subTab) {
            0 -> AutoCallSmsView(
                config = autoCallSmsConfig,
                logs = callSmsLogs,
                rules = autoSmsRules,
                contacts = contacts,
                onUpdateConfig = onUpdateAutoCallSmsConfig,
                onSetPreset = onSetAutoCallPreset,
                onToggleAutoSms = onToggleAutoSms,
                onToggleAutoReadSms = onToggleAutoReadSms,
                onToggleAutoAnnounceCalls = onToggleAutoAnnounceCalls,
                onToggleAutoReplyMissedCalls = onToggleAutoReplyMissedCalls,
                onSetCustomReplyText = onSetCustomAutoReplyText,
                onSetEmergencyContact = onSetEmergencyContact,
                onTriggerEmergencySos = onTriggerEmergencySos,
                onInsertRule = onInsertAutoSmsRule,
                onDeleteRule = onDeleteAutoSmsRule,
                onToggleRule = onToggleAutoSmsRule,
                onSimulateCall = onSimulateIncomingCall,
                onSimulateSms = onSimulateIncomingSms,
                onClearLogs = onClearCallSmsLogs,
                onEnrichContactNames = onEnrichContactNames,
                onLoadPreconfiguredSmsRules = onLoadPreconfiguredSmsRules
            )
            1 -> ScheduledTasksView(
                tasks = scheduledTasks,
                contacts = contacts,
                onSearchContacts = onSearchContacts,
                onCreateTask = onCreateScheduledTask,
                onUpdateTask = onUpdateScheduledTask,
                onToggleTask = onToggleScheduledTask,
                onExecuteNow = onExecuteScheduledTaskNow,
                onDeleteTask = onDeleteScheduledTask,
                onDeleteAllTasks = onDeleteAllScheduledTasks,
                onDeleteCompletedTasks = onDeleteCompletedScheduledTasks,
                onSpeakResult = onSpeakTaskResult
            )
            2 -> PermissionsView(
                permissions = permissions,
                onRequestAll = onRequestAllPermissions,
                onRequestSingle = onRequestSinglePermission,
                onRefresh = onRefreshPermissions
            )
            3 -> CallAndSmsView(
                contacts = contacts,
                onSearchContacts = onSearchContacts,
                onMakeCall = onMakeCall,
                onSendSms = onSendSms
            )
            4 -> AlarmsAndTimersView(
                onSetAlarm = onSetAlarm,
                onSetTimer = onSetTimer,
                onShowAlarms = onShowAlarms,
                onShowTimers = onShowTimers,
                onOpenCalendar = onOpenCalendar
            )
            5 -> ContactsManagerView(
                contacts = contacts,
                onSearch = onSearchContacts,
                onCall = onMakeCall,
                onSms = { num -> onSendSms(num, "Hello from Jarvis") }
            )
        }
    }
}

// ----------------------------------------------------
// 1. PERMISSIONS MANAGER VIEW
// ----------------------------------------------------
@Composable
fun PermissionsView(
    permissions: List<PermissionStatus>,
    onRequestAll: () -> Unit,
    onRequestSingle: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val grantedCount = permissions.count { it.isGranted }
    val totalCount = permissions.size
    val allGranted = grantedCount == totalCount && totalCount > 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Master Permission Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (allGranted) NeonGreen.copy(alpha = 0.5f) else NeonAmber.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (allGranted) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = "Security",
                                tint = if (allGranted) NeonGreen else NeonAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SYSTEM PERMISSIONS MATRIX",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "$grantedCount of $totalCount Permissions Granted",
                                    fontSize = 11.sp,
                                    color = if (allGranted) NeonGreen else NeonAmber,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = ArcCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRequestAll,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArcCyan,
                                contentColor = ObsidianDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("grant_all_permissions_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GRANT ALL",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ArcCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("open_app_settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "APP SETTINGS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Individual Permission Cards
        items(permissions) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (item.isGranted) NeonGreen.copy(alpha = 0.3f) else BorderCyan.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (item.isGranted) NeonGreen else NeonRed)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = item.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = item.description,
                                fontSize = 10.sp,
                                color = TextMuted,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (item.isGranted) {
                        Text(
                            text = "ACTIVE",
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .background(NeonGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else {
                        Button(
                            onClick = { onRequestSingle(item.permission) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArcCyanContainer,
                                contentColor = ArcCyan
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "ALLOW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 2. CALL & SMS VIEW
// ----------------------------------------------------
@Composable
fun CallAndSmsView(
    contacts: List<ContactEntry> = emptyList(),
    onSearchContacts: (String) -> Unit = {},
    onMakeCall: (String) -> Unit,
    onSendSms: (String, String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var smsMessage by remember { mutableStateOf("") }

    val quickSmsTemplates = listOf(
        "I'm on my way!",
        "In a meeting, will call back soon.",
        "Jarvis automated check-in.",
        "Please call me when free."
    )

    val matchingCallContacts = remember(phoneNumber, contacts) {
        if (phoneNumber.isBlank()) emptyList()
        else {
            val q = phoneNumber.trim().lowercase()
            val qDigits = q.filter { it.isDigit() }
            contacts.filter { c ->
                c.name.lowercase().contains(q) ||
                c.number.lowercase().contains(q) ||
                (qDigits.isNotBlank() && c.number.filter { it.isDigit() }.contains(qDigits))
            }.take(5)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Direct Phone Call Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Phone",
                            tint = ArcCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DIRECT VOICE CALL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = {
                            phoneNumber = it
                            onSearchContacts(it)
                        },
                        label = { Text("Phone Number or Contact Name", fontSize = 11.sp) },
                        placeholder = { Text("Search name (e.g. Tony) or type number...", fontSize = 11.sp, color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null, tint = ArcCyan) },
                        trailingIcon = {
                            if (phoneNumber.isNotBlank()) {
                                IconButton(onClick = {
                                    phoneNumber = ""
                                    onSearchContacts("")
                                }) {
                                    Icon(Icons.Default.Close, "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = BorderCyan,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("call_number_input")
                    )

                    if (matchingCallContacts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(matchingCallContacts) { contact ->
                                Surface(
                                    color = ArcCyan.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable {
                                        phoneNumber = contact.number
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Person, null, tint = ArcCyan, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("${contact.name} (${contact.number})", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (phoneNumber.isNotBlank()) {
                                    onMakeCall(phoneNumber)
                                }
                            },
                            enabled = phoneNumber.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = ObsidianDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("direct_call_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CALL NOW",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                phoneNumber = ""
                                onSearchContacts("")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("CLEAR", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // SMS Dispatcher Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = "SMS",
                            tint = NeonAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SMS / TEXT MESSAGING",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = smsMessage,
                        onValueChange = { smsMessage = it },
                        label = { Text("Message Body", fontSize = 11.sp) },
                        placeholder = { Text("Type your message here...", fontSize = 11.sp, color = TextMuted) },
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonAmber,
                            unfocusedBorderColor = BorderCyan,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sms_body_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Quick Templates:",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickSmsTemplates.take(2).forEach { tmpl ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(SurfaceVariantDark, RoundedCornerShape(6.dp))
                                    .border(1.dp, BorderCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .clickable { smsMessage = tmpl }
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = tmpl,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (phoneNumber.isNotBlank() && smsMessage.isNotBlank()) {
                                onSendSms(phoneNumber, smsMessage)
                            }
                        },
                        enabled = phoneNumber.isNotBlank() && smsMessage.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonAmber,
                            contentColor = ObsidianDark
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("send_sms_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DISPATCH SMS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 3. ALARMS & TIMERS VIEW
// ----------------------------------------------------
@Composable
fun AlarmsAndTimersView(
    onSetAlarm: (Int, Int, String) -> Unit,
    onSetTimer: (Int, String) -> Unit,
    onShowAlarms: () -> Unit,
    onShowTimers: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    var alarmHour by remember { mutableStateOf("07") }
    var alarmMinute by remember { mutableStateOf("00") }
    var alarmLabel by remember { mutableStateOf("Jarvis Alarm") }

    val quickAlarms = listOf(
        Pair(6, 0),
        Pair(6, 30),
        Pair(7, 0),
        Pair(7, 30),
        Pair(8, 0),
        Pair(22, 0)
    )

    val quickTimers = listOf(
        Pair("1 Min", 60),
        Pair("5 Min", 300),
        Pair("10 Min", 600),
        Pair("15 Min", 900),
        Pair("30 Min", 1800),
        Pair("45 Min", 2700)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Alarm Studio Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, ArcCyan.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessAlarm,
                                contentDescription = "Alarm",
                                tint = ArcCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SYSTEM ALARM STUDIO",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }

                        OutlinedButton(
                            onClick = onShowAlarms,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ArcCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("VIEW ALL", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = alarmHour,
                            onValueChange = { if (it.length <= 2) alarmHour = it },
                            label = { Text("Hour (0-23)", fontSize = 10.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArcCyan,
                                unfocusedBorderColor = BorderCyan,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = alarmMinute,
                            onValueChange = { if (it.length <= 2) alarmMinute = it },
                            label = { Text("Minute (0-59)", fontSize = 10.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArcCyan,
                                unfocusedBorderColor = BorderCyan,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = alarmLabel,
                        onValueChange = { alarmLabel = it },
                        label = { Text("Alarm Label", fontSize = 10.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = BorderCyan,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Quick Presets:",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickAlarms.take(4).forEach { (h, m) ->
                            val fmt = String.format("%02d:%02d", h, m)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(SurfaceVariantDark, RoundedCornerShape(6.dp))
                                    .border(1.dp, BorderCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .clickable {
                                        alarmHour = String.format("%02d", h)
                                        alarmMinute = String.format("%02d", m)
                                        onSetAlarm(h, m, "Jarvis Morning Alarm")
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = fmt,
                                    fontSize = 11.sp,
                                    color = ArcCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val h = alarmHour.toIntOrNull() ?: 7
                            val m = alarmMinute.toIntOrNull() ?: 0
                            onSetAlarm(h, m, alarmLabel.ifBlank { "Jarvis Alarm" })
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArcCyan,
                            contentColor = ObsidianDark
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("set_alarm_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AlarmOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ACTIVATE ALARM",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Timer Studio Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmber.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HourglassBottom,
                                contentDescription = "Timer",
                                tint = NeonAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "COUNTDOWN TIMERS",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }

                        OutlinedButton(
                            onClick = onShowTimers,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmber.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("ACTIVE TIMERS", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "1-Tap Quick Countdown:",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickTimers.take(3).forEach { (label, secs) ->
                                Button(
                                    onClick = { onSetTimer(secs, "$label Timer") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SurfaceVariantDark,
                                        contentColor = NeonAmber
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickTimers.drop(3).forEach { (label, secs) ->
                                Button(
                                    onClick = { onSetTimer(secs, "$label Timer") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SurfaceVariantDark,
                                        contentColor = NeonAmber
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Calendar Shortcut Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Calendar",
                            tint = ArcCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CALENDAR & SCHEDULES",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Launch system calendar & sync events",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Button(
                        onClick = onOpenCalendar,
                        colors = ButtonDefaults.buttonColors(containerColor = ArcCyanContainer, contentColor = ArcCyan),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("OPEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 4. CONTACTS MANAGER VIEW
// ----------------------------------------------------
@Composable
fun ContactsManagerView(
    contacts: List<ContactEntry>,
    onSearch: (String) -> Unit,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(searchQuery, contacts) {
        if (searchQuery.isBlank()) contacts
        else {
            val q = searchQuery.trim().lowercase()
            val qDigits = q.filter { it.isDigit() }
            contacts.filter { c ->
                c.name.lowercase().contains(q) ||
                c.number.lowercase().contains(q) ||
                (qDigits.isNotBlank() && c.number.filter { it.isDigit() }.contains(qDigits))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearch(it)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = ArcCyan
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        onSearch("")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            placeholder = { Text("Search by name (e.g. Tony) or number...", fontSize = 12.sp, color = TextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ArcCyan,
                unfocusedBorderColor = BorderCyan,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_contacts_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredContacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Contacts,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No contacts found matching '$searchQuery'"
                        else "No contacts loaded. Grant 'Read Contacts' permission above.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredContacts) { contact ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(ArcCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.name.take(1).uppercase(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArcCyan
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = contact.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${contact.number} • ${contact.type}",
                                        fontSize = 11.sp,
                                        color = ArcCyan,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(
                                    onClick = { onCall(contact.number) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(NeonGreen.copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call",
                                        tint = NeonGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onSms(contact.number) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(NeonAmber.copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Message,
                                        contentDescription = "SMS",
                                        tint = NeonAmber,
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
}
