package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ScheduleSend
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScheduledTask
import com.example.system.ContactEntry
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class AutomationTemplate(
    val title: String,
    val icon: String,
    val taskMode: Int, // 0 = Gemini, 1 = SMS, 2 = Gemini to SMS
    val frequency: String,
    val intervalMinutes: Int,
    val hour: Int,
    val minute: Int,
    val defaultText: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTasksView(
    tasks: List<ScheduledTask>,
    contacts: List<ContactEntry> = emptyList(),
    onSearchContacts: (String) -> Unit = {},
    onCreateTask: (
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
    ) -> Unit,
    onUpdateTask: (ScheduledTask) -> Unit = {},
    onToggleTask: (id: Long, enabled: Boolean) -> Unit,
    onExecuteNow: (id: Long) -> Unit,
    onDeleteTask: (id: Long) -> Unit,
    onDeleteAllTasks: () -> Unit = {},
    onDeleteCompletedTasks: () -> Unit = {},
    onSpeakResult: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 0 = GEMINI_QUERY, 1 = AUTO_SMS, 2 = GEMINI_TO_SMS
    var selectedTaskMode by remember { mutableIntStateOf(0) }

    var taskTitle by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    var contactSearchQuery by remember { mutableStateOf("") }
    var taskSearchQuery by remember { mutableStateOf("") }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<ScheduledTask?>(null) }
    var messageText by remember { mutableStateOf("Summarize current AI and technology news updates") }

    // India Standard Time default
    var selectedTimezone by remember { mutableStateOf("Asia/Kolkata") }

    val calInit = Calendar.getInstance(TimeZone.getTimeZone(selectedTimezone))
    var selectedHour by remember { mutableIntStateOf(calInit.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf((calInit.get(Calendar.MINUTE) + 10) % 60) }
    var repeatFrequency by remember { mutableStateOf("HOURLY") } // HOURLY, EVERY_X_MINUTES, DAILY, ONCE
    var customIntervalMinutes by remember { mutableIntStateOf(60) }

    val templates = remember {
        listOf(
            AutomationTemplate(
                title = "Morning AI Briefing",
                icon = "🌅",
                taskMode = 0,
                frequency = "DAILY",
                intervalMinutes = 0,
                hour = 9,
                minute = 0,
                defaultText = "Give me a quick 3-bullet morning briefing with weather, top world tech news, and an empowering productivity tip for today.",
                description = "Daily 9:00 AM summary"
            ),
            AutomationTemplate(
                title = "Hourly Health & Posture",
                icon = "💧",
                taskMode = 0,
                frequency = "HOURLY",
                intervalMinutes = 60,
                hour = 10,
                minute = 0,
                defaultText = "Give me a 1-sentence posture correction, deep breath, and water hydration reminder.",
                description = "Every 1 Hour"
            ),
            AutomationTemplate(
                title = "Hourly Tech Breakthroughs",
                icon = "🚀",
                taskMode = 0,
                frequency = "HOURLY",
                intervalMinutes = 60,
                hour = 11,
                minute = 0,
                defaultText = "Summarize top latest AI, tech, and space exploration breakthroughs in 2 punchy bullet points.",
                description = "Every 1 Hour"
            ),
            AutomationTemplate(
                title = "Evening Check-In SMS",
                icon = "💬",
                taskMode = 1,
                frequency = "DAILY",
                intervalMinutes = 0,
                hour = 20,
                minute = 0,
                defaultText = "Jarvis Auto: Good evening! Checking in to see how your day went. Have a peaceful night.",
                description = "Daily 8:00 PM SMS"
            ),
            AutomationTemplate(
                title = "AI Smart Draft SMS",
                icon = "✨",
                taskMode = 2,
                frequency = "DAILY",
                intervalMinutes = 0,
                hour = 18,
                minute = 30,
                defaultText = "Generate a polite end-of-day wrap-up and greeting message for my team.",
                description = "AI drafted & sent"
            ),
            AutomationTemplate(
                title = "15-Min Quick Alert",
                icon = "⚡",
                taskMode = 0,
                frequency = "EVERY_X_MINUTES",
                intervalMinutes = 15,
                hour = 12,
                minute = 0,
                defaultText = "Brief 1-sentence focus and high-energy productivity check.",
                description = "Every 15 mins"
            )
        )
    }

    if (showClearAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = NeonRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Clear All Scheduled Tasks?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "This will cancel all pending exact alarms and remove all ${tasks.size} scheduled tasks permanently.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllConfirmDialog = false
                        onDeleteAllTasks()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CLEAR ALL", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmDialog = false }) {
                    Text("CANCEL", color = TextSecondary, fontSize = 12.sp)
                }
            },
            containerColor = SurfaceDark,
            tonalElevation = 8.dp
        )
    }

    // Edit Task Dialog
    taskToEdit?.let { task ->
        EditScheduledTaskDialog(
            task = task,
            contacts = contacts,
            onDismiss = { taskToEdit = null },
            onSave = { updated ->
                onUpdateTask(updated)
                taskToEdit = null
            }
        )
    }

    val geminiPresets = listOf(
        "Hourly AI & tech breakthrough highlights",
        "Motivational mindset quote and mental clarity exercise",
        "Hourly summary of key programming tips",
        "Daily morning news and weather briefing",
        "Hourly health, hydration & posture reminder"
    )

    val smsPresets = listOf(
        "Jarvis Auto: Greetings, hope you have a great day!",
        "Jarvis Status: Operational and on schedule.",
        "Reminder: Meeting starts in 10 minutes.",
        "All systems checked and running smoothly."
    )

    val filteredTasks = remember(tasks, taskSearchQuery, contacts) {
        if (taskSearchQuery.isBlank()) tasks
        else {
            val q = taskSearchQuery.trim().lowercase()
            tasks.filter { task ->
                val resolvedContact = task.recipientName.ifBlank {
                    contacts.find { c ->
                        c.number == task.targetPhoneNumber ||
                        (task.targetPhoneNumber.isNotBlank() && c.number.filter { it.isDigit() }.endsWith(task.targetPhoneNumber.filter { it.isDigit() }))
                    }?.name ?: ""
                }
                task.title.lowercase().contains(q) ||
                resolvedContact.lowercase().contains(q) ||
                task.targetPhoneNumber.lowercase().contains(q) ||
                task.messageText.lowercase().contains(q) ||
                task.taskType.lowercase().contains(q)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick 1-Tap Automation Templates Hub
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianDark),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ArcCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, null, tint = NeonAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "1-TAP AUTOMATION PRESETS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text("Tap to Auto-Fill", fontSize = 10.sp, color = TextSecondary)
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(templates) { t ->
                            Surface(
                                modifier = Modifier.clickable {
                                    selectedTaskMode = t.taskMode
                                    taskTitle = t.title
                                    messageText = t.defaultText
                                    repeatFrequency = t.frequency
                                    customIntervalMinutes = t.intervalMinutes
                                    selectedHour = t.hour
                                    selectedMinute = t.minute
                                    Toast.makeText(context, "Loaded preset: ${t.title}", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceDark,
                                border = BorderStroke(1.dp, if (taskTitle == t.title) NeonAmber else Color.White.copy(alpha = 0.12f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(t.icon, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = t.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (taskTitle == t.title) NeonAmber else Color.White
                                        )
                                        Text(
                                            text = t.description,
                                            fontSize = 9.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Creation Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NEW SCHEDULED AUTOMATION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArcCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Surface(
                            color = NeonGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "EXACT ALARM ACTIVE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Task Type Selector (Gemini AI vs Auto SMS vs Gemini to SMS)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            Triple(0, "🤖 Gemini AI", "Query Gemini automatically"),
                            Triple(1, "💬 Auto SMS", "Send offline SMS"),
                            Triple(2, "✨ AI to SMS", "AI drafts & texts")
                        )

                        modes.forEach { (index, label, _) ->
                            val isSelected = selectedTaskMode == index
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedTaskMode = index
                                        if (index == 0 && messageText.startsWith("Jarvis")) {
                                            messageText = geminiPresets.first()
                                        } else if (index == 1 && !messageText.startsWith("Jarvis")) {
                                            messageText = smsPresets.first()
                                        }
                                    },
                                color = if (isSelected) ArcCyan.copy(alpha = 0.2f) else ObsidianDark,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) ArcCyan else Color.White.copy(alpha = 0.1f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) ArcCyan else Color.LightGray,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // Custom Title field (optional)
                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        label = { Text("Task Title (Optional)", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Daily Morning Tech News Briefing", fontSize = 11.sp, color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = ObsidianDark,
                            unfocusedContainerColor = ObsidianDark
                        )
                    )

                    // Timezone selection chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Target Timezone:", fontSize = 11.sp, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = selectedTimezone == "Asia/Kolkata",
                                onClick = { selectedTimezone = "Asia/Kolkata" },
                                label = { Text("🇮🇳 India IST", fontSize = 10.sp) },
                                leadingIcon = {
                                    if (selectedTimezone == "Asia/Kolkata") {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp))
                                    }
                                }
                            )
                            FilterChip(
                                selected = selectedTimezone == "LOCAL",
                                onClick = { selectedTimezone = "LOCAL" },
                                label = { Text("🌐 Local Device", fontSize = 10.sp) },
                                leadingIcon = {
                                    if (selectedTimezone == "LOCAL") {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp))
                                    }
                                }
                            )
                        }
                    }

                    // Repeat Frequency Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Schedule Interval / Frequency:",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val freqOptions = listOf(
                                Triple("HOURLY", "Every 1 Hour", 60),
                                Triple("EVERY_X_MINUTES_5", "Every 5 Min", 5),
                                Triple("EVERY_X_MINUTES_15", "Every 15 Min", 15),
                                Triple("EVERY_X_MINUTES_30", "Every 30 Min", 30),
                                Triple("HOURLY_2", "Every 2 Hours", 120),
                                Triple("DAILY", "Daily Exact Time", 0),
                                Triple("ONCE", "One-Time Exact", 0)
                            )

                            items(freqOptions) { (key, label, minutes) ->
                                val isSelected = when (key) {
                                    "HOURLY" -> repeatFrequency == "HOURLY" && customIntervalMinutes == 60
                                    "HOURLY_2" -> repeatFrequency == "HOURLY" && customIntervalMinutes == 120
                                    "EVERY_X_MINUTES_5" -> repeatFrequency == "EVERY_X_MINUTES" && customIntervalMinutes == 5
                                    "EVERY_X_MINUTES_15" -> repeatFrequency == "EVERY_X_MINUTES" && customIntervalMinutes == 15
                                    "EVERY_X_MINUTES_30" -> repeatFrequency == "EVERY_X_MINUTES" && customIntervalMinutes == 30
                                    "DAILY" -> repeatFrequency == "DAILY"
                                    "ONCE" -> repeatFrequency == "ONCE"
                                    else -> false
                                }

                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        when (key) {
                                            "HOURLY" -> {
                                                repeatFrequency = "HOURLY"
                                                customIntervalMinutes = 60
                                            }
                                            "HOURLY_2" -> {
                                                repeatFrequency = "HOURLY"
                                                customIntervalMinutes = 120
                                            }
                                            "EVERY_X_MINUTES_5" -> {
                                                repeatFrequency = "EVERY_X_MINUTES"
                                                customIntervalMinutes = 5
                                            }
                                            "EVERY_X_MINUTES_15" -> {
                                                repeatFrequency = "EVERY_X_MINUTES"
                                                customIntervalMinutes = 15
                                            }
                                            "EVERY_X_MINUTES_30" -> {
                                                repeatFrequency = "EVERY_X_MINUTES"
                                                customIntervalMinutes = 30
                                            }
                                            "DAILY" -> {
                                                repeatFrequency = "DAILY"
                                                customIntervalMinutes = 0
                                            }
                                            "ONCE" -> {
                                                repeatFrequency = "ONCE"
                                                customIntervalMinutes = 0
                                            }
                                        }
                                    },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }
                    }

                    // Custom Hour & Minute Selector (for Daily or Once or start anchor)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Time",
                                        tint = NeonAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (repeatFrequency == "DAILY" || repeatFrequency == "ONCE") "Start / Target Time:" else "Anchor Time (IST/Local):",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                Text(
                                    text = String.format(Locale.US, "%02d:%02d %s",
                                        if (selectedHour % 12 == 0) 12 else selectedHour % 12,
                                        selectedMinute,
                                        if (selectedHour >= 12) "PM" else "AM"
                                    ),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonAmber,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Hour Control
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Hour: ", fontSize = 11.sp, color = Color.Gray)
                                    IconButton(
                                        onClick = { selectedHour = (selectedHour - 1 + 24) % 24 },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, "Dec Hour", tint = Color.White)
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%02d", selectedHour),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    IconButton(
                                        onClick = { selectedHour = (selectedHour + 1) % 24 },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, "Inc Hour", tint = Color.White)
                                    }
                                }

                                // Minute Control
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Min: ", fontSize = 11.sp, color = Color.Gray)
                                    IconButton(
                                        onClick = { selectedMinute = (selectedMinute - 5 + 60) % 60 },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, "Dec Min", tint = Color.White)
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%02d", selectedMinute),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    IconButton(
                                        onClick = { selectedMinute = (selectedMinute + 5) % 60 },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, "Inc Min", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // Recipient Number (Only if SMS or Gemini-to-SMS)
                    AnimatedVisibility(visible = selectedTaskMode == 1 || selectedTaskMode == 2) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Contact Search Input
                            OutlinedTextField(
                                value = contactSearchQuery,
                                onValueChange = { query ->
                                    contactSearchQuery = query
                                    onSearchContacts(query)
                                    if (query.isBlank()) {
                                        phoneNumber = ""
                                        recipientName = ""
                                    } else {
                                        val digitsOnly = query.filter { it.isDigit() || it == '+' }
                                        val matched = contacts.find {
                                            it.name.equals(query, ignoreCase = true) ||
                                            it.number == query ||
                                            (digitsOnly.length >= 6 && it.number.filter { c -> c.isDigit() }.endsWith(digitsOnly.filter { c -> c.isDigit() }))
                                        }
                                        if (matched != null) {
                                            phoneNumber = matched.number
                                            recipientName = matched.name
                                        } else if (query.any { it.isLetter() }) {
                                            recipientName = query
                                        } else {
                                            phoneNumber = query
                                        }
                                    }
                                },
                                label = { Text("Search Contact by Name or Number", fontSize = 11.sp) },
                                placeholder = { Text("Type name (e.g. Tony, Alex) or phone number...", fontSize = 11.sp, color = TextMuted) },
                                leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null, tint = ArcCyan) },
                                trailingIcon = {
                                    if (contactSearchQuery.isNotBlank() || phoneNumber.isNotBlank() || recipientName.isNotBlank()) {
                                        IconButton(onClick = {
                                            contactSearchQuery = ""
                                            phoneNumber = ""
                                            recipientName = ""
                                            onSearchContacts("")
                                        }) {
                                            Icon(Icons.Default.Close, "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ArcCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedContainerColor = ObsidianDark,
                                    unfocusedContainerColor = ObsidianDark
                                )
                            )

                            // Dynamic Matching Contacts List
                            val matchingContacts = remember(contactSearchQuery, contacts) {
                                if (contactSearchQuery.isBlank()) {
                                    contacts.take(5)
                                } else {
                                    val q = contactSearchQuery.lowercase()
                                    contacts.filter { it.name.lowercase().contains(q) || it.number.contains(q) }.take(6)
                                }
                            }

                            if (matchingContacts.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Pick Contact:", fontSize = 10.sp, color = TextSecondary)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(matchingContacts) { c ->
                                            val isSelected = phoneNumber == c.number || recipientName == c.name
                                            Surface(
                                                modifier = Modifier.clickable {
                                                    phoneNumber = c.number
                                                    recipientName = c.name
                                                    contactSearchQuery = c.name
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) ArcCyan.copy(alpha = 0.25f) else ObsidianDark,
                                                border = BorderStroke(1.dp, if (isSelected) ArcCyan else Color.White.copy(alpha = 0.1f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = if (isSelected) ArcCyan else Color.LightGray,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "${c.name} (${c.number.takeLast(10)})",
                                                        fontSize = 10.sp,
                                                        color = if (isSelected) ArcCyan else Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Prompt or Message Body
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = {
                            Text(
                                text = if (selectedTaskMode == 0) "Gemini AI Prompt / Query" else "SMS Message Body / AI Instructions",
                                fontSize = 11.sp
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (selectedTaskMode == 0) NeonAmber else ArcCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = ObsidianDark,
                            unfocusedContainerColor = ObsidianDark
                        )
                    )

                    // Suggested presets row
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (selectedTaskMode == 0) "Suggested Gemini Prompts:" else "Suggested SMS Messages:",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val activePresets = if (selectedTaskMode == 0) geminiPresets else smsPresets
                            items(activePresets) { preset ->
                                FilterChip(
                                    selected = messageText == preset,
                                    onClick = { messageText = preset },
                                    label = { Text(preset.take(28) + "...", fontSize = 9.sp) }
                                )
                            }
                        }
                    }

                    // Submit Schedule Button
                    Button(
                        onClick = {
                            val type = when (selectedTaskMode) {
                                0 -> "GEMINI_QUERY"
                                2 -> "GEMINI_TO_SMS"
                                else -> "AUTO_SMS"
                            }
                            val autoTitle = if (taskTitle.isNotBlank()) taskTitle else when (type) {
                                "GEMINI_QUERY" -> "Gemini AI: ${messageText.take(24)}"
                                "GEMINI_TO_SMS" -> if (recipientName.isNotBlank()) "Gemini SMS to $recipientName" else "Gemini SMS to $phoneNumber"
                                else -> if (recipientName.isNotBlank()) "Auto SMS to $recipientName" else "Auto SMS to $phoneNumber"
                            }

                            onCreateTask(
                                autoTitle,
                                type,
                                phoneNumber,
                                recipientName,
                                messageText,
                                selectedHour,
                                selectedMinute,
                                customIntervalMinutes,
                                selectedTimezone,
                                repeatFrequency
                            )
                        },
                        enabled = messageText.isNotBlank() && (selectedTaskMode == 0 || phoneNumber.isNotBlank() || recipientName.isNotBlank()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTaskMode == 0) NeonAmber else ArcCyan
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedTaskMode == 0) Icons.Default.AutoAwesome else Icons.Default.ScheduleSend,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (selectedTaskMode) {
                                0 -> "ACTIVATE GEMINI AI SCHEDULE"
                                2 -> "SCHEDULE GEMINI AI TO SMS"
                                else -> "SCHEDULE AUTO SMS"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Active Tasks Header & Search
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE SCHEDULES (${tasks.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )

                    if (tasks.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CLEANUP",
                                fontSize = 10.sp,
                                color = ArcCyan,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clickable { onDeleteCompletedTasks() }
                                    .padding(4.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CLEAR ALL",
                                fontSize = 10.sp,
                                color = NeonRed,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clickable { showClearAllConfirmDialog = true }
                                    .padding(4.dp)
                            )
                        }
                    }
                }

                if (tasks.isNotEmpty()) {
                    OutlinedTextField(
                        value = taskSearchQuery,
                        onValueChange = { taskSearchQuery = it },
                        label = { Text("Filter schedules by title, contact name or phone", fontSize = 10.sp) },
                        placeholder = { Text("Search scheduled items...", fontSize = 10.sp, color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = ArcCyan, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (taskSearchQuery.isNotBlank()) {
                                IconButton(onClick = { taskSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
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

        // List of tasks
        if (filteredTasks.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (tasks.isEmpty()) "No scheduled tasks yet. Pick a 1-tap template above or create one." else "No matching scheduled tasks found.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        } else {
            items(filteredTasks, key = { it.id }) { task ->
                ScheduledTaskCard(
                    task = task,
                    contacts = contacts,
                    onToggle = { enabled -> onToggleTask(task.id, enabled) },
                    onExecuteNow = { onExecuteNow(task.id) },
                    onEdit = { taskToEdit = task },
                    onDelete = { onDeleteTask(task.id) },
                    onSpeak = { text -> onSpeakResult(text) }
                )
            }
        }
    }
}

@Composable
fun ScheduledTaskCard(
    task: ScheduledTask,
    contacts: List<ContactEntry> = emptyList(),
    onToggle: (Boolean) -> Unit,
    onExecuteNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSpeak: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = NeonRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Remove Schedule?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${task.title}\"? This automated background task will be stopped and removed completely.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("DELETE PERMANENTLY", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("CANCEL", color = TextSecondary, fontSize = 12.sp)
                }
            },
            containerColor = SurfaceDark,
            tonalElevation = 8.dp
        )
    }

    val isGemini = task.taskType == "GEMINI_QUERY"
    val isGeminiSms = task.taskType == "GEMINI_TO_SMS"

    val tz = if (task.timezoneId == "Asia/Kolkata") TimeZone.getTimeZone("Asia/Kolkata") else TimeZone.getDefault()
    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).apply { timeZone = tz }.format(Date(task.scheduledTimeMillis))
    val tzTag = if (task.timezoneId == "Asia/Kolkata") "IST" else "Local"

    val resolvedContactName = remember(task.recipientName, task.targetPhoneNumber, contacts) {
        if (task.recipientName.isNotBlank() && task.recipientName != task.targetPhoneNumber) {
            task.recipientName
        } else {
            contacts.find { c ->
                c.number == task.targetPhoneNumber ||
                (task.targetPhoneNumber.isNotBlank() && c.number.filter { it.isDigit() }.takeLast(8) == task.targetPhoneNumber.filter { it.isDigit() }.takeLast(8))
            }?.name ?: ""
        }
    }

    val hasContactName = resolvedContactName.isNotBlank()
    val recipientSubtitle = when {
        isGemini -> "🤖 Gemini AI Prompt • ${task.repeatFrequency}"
        isGeminiSms -> if (hasContactName) "✨ Gemini to SMS: $resolvedContactName (${task.targetPhoneNumber})" else "✨ Gemini to SMS: ${task.targetPhoneNumber}"
        else -> if (hasContactName) "💬 Auto SMS to: $resolvedContactName (${task.targetPhoneNumber})" else "💬 Auto SMS to: ${task.targetPhoneNumber}"
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (task.isEnabled) SurfaceDark else SurfaceDark.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isGemini) NeonAmber.copy(alpha = 0.35f) else if (task.isEnabled) ArcCyan.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isGemini) NeonAmber.copy(0.2f)
                                else if (isGeminiSms) NeonGreen.copy(0.2f)
                                else ArcCyan.copy(0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isGemini -> Icons.Default.Psychology
                                isGeminiSms -> Icons.Default.AutoAwesome
                                else -> Icons.Default.Sms
                            },
                            contentDescription = null,
                            tint = when {
                                isGemini -> NeonAmber
                                isGeminiSms -> NeonGreen
                                else -> ArcCyan
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = task.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = recipientSubtitle,
                            fontSize = 11.sp,
                            color = if (isGemini) NeonAmber else TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (task.status) {
                            "EXECUTED" -> NeonGreen.copy(0.2f)
                            "PENDING" -> NeonAmber.copy(0.2f)
                            else -> NeonRed.copy(0.2f)
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = task.status,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (task.status) {
                                "EXECUTED" -> NeonGreen
                                "PENDING" -> NeonAmber
                                else -> NeonRed
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Switch(
                        checked = task.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = if (isGemini) NeonAmber else ArcCyan
                        )
                    )
                }
            }

            // Message or Prompt box
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianDark),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isGemini) "PROMPT: \"${task.messageText}\"" else "SMS: \"${task.messageText}\"",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )

                    // Last execution result preview (if any)
                    if (task.lastResultText.isNotBlank()) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                        val isFailed = task.lastResultText.startsWith("Failed", ignoreCase = true)
                        val isOpenedApp = task.lastResultText.contains("opened SMS", ignoreCase = true) || task.lastResultText.contains("permission needed", ignoreCase = true)
                        val resultColor = when {
                            isFailed -> NeonRed
                            isOpenedApp -> ArcCyan
                            isGemini -> NeonAmber
                            else -> NeonGreen
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LATEST RESULT (${task.executionCount} runs):",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = resultColor,
                                fontFamily = FontFamily.Monospace
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Speak button
                                if (isGemini && task.lastResultText.isNotBlank()) {
                                    IconButton(
                                        onClick = { onSpeak(task.lastResultText) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.VolumeUp, "Speak Result", tint = NeonAmber, modifier = Modifier.size(13.dp))
                                    }
                                }

                                // Copy button
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Jarvis Result", task.lastResultText))
                                        Toast.makeText(context, "Copied result to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Copy", tint = ArcCyan, modifier = Modifier.size(13.dp))
                                }
                            }
                        }

                        Text(
                            text = task.lastResultText.take(280) + if (task.lastResultText.length > 280) "..." else "",
                            fontSize = 11.sp,
                            color = if (isFailed) NeonRed.copy(0.9f) else Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Timing details & Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Next: $dateStr ($tzTag)",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // EDIT BUTTON
                    TextButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(13.dp), tint = ArcCyan)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("EDIT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ArcCyan)
                    }

                    // TEST NOW
                    TextButton(
                        onClick = onExecuteNow,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(13.dp), tint = NeonGreen)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("TEST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                    }

                    // REMOVE BUTTON
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, "Remove Schedule", tint = NeonRed, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EditScheduledTaskDialog(
    task: ScheduledTask,
    contacts: List<ContactEntry> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (ScheduledTask) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var messageText by remember { mutableStateOf(task.messageText) }
    var hour by remember { mutableIntStateOf(task.hour) }
    var minute by remember { mutableIntStateOf(task.minute) }
    var repeatFreq by remember { mutableStateOf(task.repeatFrequency) }
    var intervalMinutes by remember { mutableIntStateOf(task.intervalMinutes) }
    var timezoneId by remember { mutableStateOf(task.timezoneId) }
    var phone by remember { mutableStateOf(task.targetPhoneNumber) }
    var recipientName by remember { mutableStateOf(task.recipientName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditCalendar, null, tint = ArcCyan, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Scheduled Task", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text(if (task.taskType == "GEMINI_QUERY") "AI Prompt" else "SMS Message", fontSize = 11.sp) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                // Hour & Minute Picker
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Time:", fontSize = 11.sp, color = TextSecondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { hour = (hour - 1 + 24) % 24 }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Remove, null, tint = Color.White)
                            }
                            Text(String.format(Locale.US, "%02d", hour), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                            IconButton(onClick = { hour = (hour + 1) % 24 }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Add, null, tint = Color.White)
                            }
                            Text(":", color = Color.White, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { minute = (minute - 5 + 60) % 60 }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Remove, null, tint = Color.White)
                            }
                            Text(String.format(Locale.US, "%02d", minute), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                            IconButton(onClick = { minute = (minute + 5) % 60 }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Add, null, tint = Color.White)
                            }
                        }
                    }
                }

                // Frequency options
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val opts = listOf(
                        "HOURLY" to "Hourly",
                        "EVERY_X_MINUTES" to "Every 15m",
                        "DAILY" to "Daily",
                        "ONCE" to "Once"
                    )
                    items(opts) { (key, label) ->
                        FilterChip(
                            selected = repeatFreq == key,
                            onClick = {
                                repeatFreq = key
                                if (key == "EVERY_X_MINUTES" && intervalMinutes == 0) intervalMinutes = 15
                                if (key == "HOURLY") intervalMinutes = 60
                            },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = task.copy(
                        title = title.ifBlank { task.title },
                        messageText = messageText.ifBlank { task.messageText },
                        hour = hour,
                        minute = minute,
                        repeatFrequency = repeatFreq,
                        intervalMinutes = intervalMinutes,
                        timezoneId = timezoneId,
                        targetPhoneNumber = phone,
                        recipientName = recipientName
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ArcCyan)
            ) {
                Text("SAVE CHANGES", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondary, fontSize = 11.sp)
            }
        },
        containerColor = SurfaceDark
    )
}
