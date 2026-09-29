package com.example.system

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.JarvisApplication
import com.example.MainActivity
import com.example.data.CallSmsLog
import com.example.data.ExecutionLog
import com.example.data.JarvisRepository
import com.example.data.ScheduledTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ScheduledTaskManager(
    private val context: Context,
    private val repository: JarvisRepository,
    private val telephonyManager: TelephonyAlarmManager,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "ScheduledTaskManager"
        const val ACTION_EXECUTE_TASK = "com.example.jarvis.ACTION_EXECUTE_SCHEDULED_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"
        private const val NOTIFICATION_CHANNEL_ID = "jarvis_scheduled_task_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Jarvis Scheduled Tasks"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for automated scheduled text messages and Gemini AI intelligence tasks"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun calculateNextTriggerMillis(
        hour: Int,
        minute: Int,
        repeatFrequency: String = "ONCE",
        intervalMinutes: Int = 0,
        timezoneId: String = "Asia/Kolkata"
    ): Long {
        val now = System.currentTimeMillis()
        return when (repeatFrequency) {
            "EVERY_X_MINUTES", "MINUTELY" -> {
                val mins = if (intervalMinutes > 0) intervalMinutes else 15
                now + (mins * 60 * 1000L)
            }
            "HOURLY" -> {
                val hours = if (intervalMinutes > 0) (intervalMinutes / 60).coerceAtLeast(1) else 1
                now + (hours * 3600 * 1000L)
            }
            "DAILY", "ONCE" -> {
                val tz = if (timezoneId == "Asia/Kolkata" || timezoneId == "IST") {
                    TimeZone.getTimeZone("Asia/Kolkata")
                } else {
                    TimeZone.getDefault()
                }
                val calendar = Calendar.getInstance(tz).apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (calendar.timeInMillis <= now) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                calendar.timeInMillis
            }
            else -> {
                now + (60 * 60 * 1000L)
            }
        }
    }

    suspend fun createAndScheduleTask(
        title: String = "",
        taskType: String = "AUTO_SMS", // "AUTO_SMS", "GEMINI_QUERY", "GEMINI_TO_SMS"
        targetPhoneNumber: String = "",
        recipientName: String = "",
        messageText: String,
        hour: Int = 0,
        minute: Int = 0,
        intervalMinutes: Int = 0,
        timezoneId: String = "Asia/Kolkata",
        repeatFrequency: String = "ONCE"
    ): Pair<Boolean, String> {
        if (taskType != "GEMINI_QUERY" && targetPhoneNumber.isBlank()) {
            return Pair(false, "Phone number or recipient is required for SMS tasks.")
        }
        if (messageText.isBlank()) {
            return Pair(false, "Message or prompt text cannot be empty.")
        }

        val clampedHour = hour.coerceIn(0, 23)
        val clampedMinute = minute.coerceIn(0, 59)
        val triggerMillis = calculateNextTriggerMillis(
            hour = clampedHour,
            minute = clampedMinute,
            repeatFrequency = repeatFrequency,
            intervalMinutes = intervalMinutes,
            timezoneId = timezoneId
        )

        val (cleanTargetPhone, resolvedName) = if (taskType == "GEMINI_TO_SMS" || taskType == "AUTO_SMS") {
            val res = resolveTargetPhoneNumber(targetPhoneNumber)
            val finalName = if (recipientName.isNotBlank()) recipientName else res.second.ifBlank { telephonyManager.lookupContactNameByNumber(res.first) }
            Pair(res.first, finalName)
        } else {
            Pair("", "")
        }

        val taskTitle = if (title.isNotBlank()) title else when (taskType) {
            "GEMINI_QUERY" -> "Gemini Prompt (${if (repeatFrequency == "HOURLY") "Hourly" else if (repeatFrequency == "EVERY_X_MINUTES") "Every ${if (intervalMinutes > 0) intervalMinutes else 15}m" else "Scheduled"})"
            "GEMINI_TO_SMS" -> if (resolvedName.isNotBlank()) "Gemini SMS to $resolvedName" else "Gemini SMS to $cleanTargetPhone"
            else -> if (resolvedName.isNotBlank()) "Auto SMS to $resolvedName" else "Auto SMS to $cleanTargetPhone"
        }

        val task = ScheduledTask(
            title = taskTitle,
            taskType = taskType,
            targetPhoneNumber = cleanTargetPhone,
            recipientName = resolvedName,
            messageText = messageText,
            hour = clampedHour,
            minute = clampedMinute,
            intervalMinutes = intervalMinutes,
            timezoneId = timezoneId,
            scheduledTimeMillis = triggerMillis,
            repeatFrequency = repeatFrequency,
            isEnabled = true,
            status = "PENDING"
        )

        val id = repository.insertScheduledTask(task)
        val scheduledTask = task.copy(id = id)

        val scheduled = scheduleAlarmManagerExact(scheduledTask)

        val tzLabel = if (timezoneId == "Asia/Kolkata") "India IST" else "Local"
        val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).apply {
            if (timezoneId == "Asia/Kolkata") timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(Date(triggerMillis))

        val recipientLabel = if (resolvedName.isNotBlank()) "$resolvedName ($cleanTargetPhone)" else cleanTargetPhone
        val confirmation = when (taskType) {
            "GEMINI_QUERY" -> "Scheduled Gemini query ($repeatFrequency) at $dateStr ($tzLabel): \"$messageText\""
            "GEMINI_TO_SMS" -> "Scheduled Gemini AI text to $recipientLabel at $dateStr ($tzLabel)"
            else -> "Scheduled auto SMS to $recipientLabel at $dateStr ($tzLabel)"
        }

        Log.i(TAG, "Created task #$id ($taskType, $repeatFrequency) scheduled for $dateStr")

        return Pair(scheduled, confirmation)
    }

    suspend fun updateAndRescheduleTask(updatedTask: ScheduledTask): Pair<Boolean, String> {
        cancelAlarm(updatedTask.id)

        val triggerMillis = if (updatedTask.scheduledTimeMillis <= System.currentTimeMillis() || updatedTask.status != "PENDING") {
            calculateNextTriggerMillis(
                hour = updatedTask.hour,
                minute = updatedTask.minute,
                repeatFrequency = updatedTask.repeatFrequency,
                intervalMinutes = updatedTask.intervalMinutes,
                timezoneId = updatedTask.timezoneId
            )
        } else {
            updatedTask.scheduledTimeMillis
        }

        val taskToSave = updatedTask.copy(
            scheduledTimeMillis = triggerMillis,
            status = "PENDING"
        )

        repository.updateScheduledTask(taskToSave)
        val scheduled = if (taskToSave.isEnabled) {
            scheduleAlarmManagerExact(taskToSave)
        } else {
            true
        }

        Log.i(TAG, "Updated task #${taskToSave.id} scheduled for $triggerMillis")
        return Pair(scheduled, "Updated scheduled task \"${taskToSave.title}\" successfully.")
    }

    fun scheduleAlarmManagerExact(task: ScheduledTask): Boolean {
        return try {
            val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
                action = ACTION_EXECUTE_TASK
                putExtra(EXTRA_TASK_ID, task.id)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                task.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        task.scheduledTimeMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        task.scheduledTimeMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    task.scheduledTimeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    task.scheduledTimeMillis,
                    pendingIntent
                )
            }
            Log.i(TAG, "Exact alarm set for task #${task.id} at ${task.scheduledTimeMillis}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm for task #${task.id}", e)
            false
        }
    }

    fun cancelAlarm(taskId: Long) {
        try {
            val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
                action = ACTION_EXECUTE_TASK
                putExtra(EXTRA_TASK_ID, taskId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.i(TAG, "Cancelled alarm for task #$taskId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling alarm #$taskId", e)
        }
    }

    suspend fun executeTask(taskId: Long): Boolean {
        val task = repository.getScheduledTaskById(taskId) ?: run {
            Log.w(TAG, "Task #$taskId not found in database for execution")
            return false
        }

        if (!task.isEnabled || task.status == "CANCELLED") {
            Log.i(TAG, "Task #$taskId is disabled or cancelled. Skipping execution.")
            return false
        }

        val now = System.currentTimeMillis()
        var sendSuccess = true
        var resultDetail = ""

        when (task.taskType) {
            "GEMINI_QUERY" -> {
                Log.i(TAG, "Executing scheduled Gemini query #${task.id}: \"${task.messageText}\"")
                val app = context.applicationContext as? JarvisApplication
                val geminiEngine = app?.geminiAssistantEngine ?: com.example.engine.GeminiAssistantEngine(context)

                val aiResponse = try {
                    geminiEngine.queryAssistant(
                        userQuery = task.messageText,
                        enableHighThinking = false,
                        useSearch = true
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling Gemini for scheduled task", e)
                    "Gemini scheduled query executed, but cloud network was unreachable."
                }

                resultDetail = aiResponse
                sendSuccess = aiResponse.isNotBlank()

                repository.insertLog(
                    ExecutionLog(
                        ruleName = "Scheduled Gemini AI Query",
                        triggerType = "TIME_SCHEDULE (${task.repeatFrequency})",
                        details = "Prompt: \"${task.messageText}\"\nAI Response: $aiResponse",
                        isSuccess = sendSuccess,
                        timestamp = now
                    )
                )

                postGeminiExecutionNotification(task, aiResponse)
            }

            "GEMINI_TO_SMS" -> {
                Log.i(TAG, "Executing scheduled Gemini-to-SMS #${task.id} to ${task.targetPhoneNumber}")
                val app = context.applicationContext as? JarvisApplication
                val geminiEngine = app?.geminiAssistantEngine ?: com.example.engine.GeminiAssistantEngine(context)

                val prompt = "Draft a concise, polite text message (under 140 characters, no markdown asterisks, no quotes) for: ${task.messageText}"
                val aiMessage = try {
                    val res = geminiEngine.queryAssistant(prompt)
                    if (res.contains("strict offline mode", ignoreCase = true) ||
                        res.contains("API key", ignoreCase = true) ||
                        res.isBlank()
                    ) {
                        task.messageText
                    } else {
                        res
                    }
                } catch (e: Exception) {
                    task.messageText
                }

                val cleanMsg = aiMessage.replace("*", "").replace("\"", "").trim()
                val (smsOk, smsDesc) = sendSmsDirect(task.targetPhoneNumber, cleanMsg)
                sendSuccess = smsOk
                resultDetail = if (smsOk) "Sent: $cleanMsg" else "Failed ($smsDesc): $cleanMsg"

                val finalContactName = task.recipientName.ifBlank {
                    telephonyManager.lookupContactNameByNumber(task.targetPhoneNumber).ifBlank { "" }
                }

                repository.insertCallSmsLog(
                    CallSmsLog(
                        type = "OUTGOING_SMS",
                        phoneNumber = task.targetPhoneNumber,
                        contactName = finalContactName,
                        messageBody = "[Scheduled Gemini SMS] $cleanMsg",
                        timestamp = now,
                        status = if (smsOk) "DELIVERED" else "FAILED"
                    )
                )

                repository.insertLog(
                    ExecutionLog(
                        ruleName = "Scheduled Gemini to SMS",
                        triggerType = "TIME_SCHEDULE (${task.repeatFrequency})",
                        details = "$smsDesc\nRecipient: ${if (finalContactName.isNotBlank()) "$finalContactName (${task.targetPhoneNumber})" else task.targetPhoneNumber}\nMessage: \"$cleanMsg\"",
                        isSuccess = smsOk,
                        timestamp = now
                    )
                )

                postExecutionNotification(task.copy(messageText = cleanMsg, recipientName = finalContactName), smsOk, smsDesc)
            }

            else -> { // "AUTO_SMS"
                Log.i(TAG, "Executing scheduled task #${task.id}: Auto SMS to ${task.targetPhoneNumber}")
                val (smsOk, smsDesc) = sendSmsDirect(task.targetPhoneNumber, task.messageText)
                sendSuccess = smsOk
                resultDetail = if (smsOk) "Sent: ${task.messageText}" else "Failed ($smsDesc): ${task.messageText}"

                val finalContactName = task.recipientName.ifBlank {
                    telephonyManager.lookupContactNameByNumber(task.targetPhoneNumber).ifBlank { "" }
                }

                repository.insertCallSmsLog(
                    CallSmsLog(
                        type = "OUTGOING_SMS",
                        phoneNumber = task.targetPhoneNumber,
                        contactName = finalContactName,
                        messageBody = "[Scheduled Auto Text] ${task.messageText}",
                        timestamp = now,
                        status = if (smsOk) "DELIVERED" else "FAILED"
                    )
                )

                repository.insertLog(
                    ExecutionLog(
                        ruleName = "Scheduled Auto Text",
                        triggerType = "TIME_SCHEDULE (${task.repeatFrequency})",
                        details = "$smsDesc\nMessage: \"${task.messageText}\"",
                        isSuccess = smsOk,
                        timestamp = now
                    )
                )

                postExecutionNotification(task, smsOk, smsDesc)
            }
        }

        // Calculate next repeat time or finalize task
        val isRecurring = task.repeatFrequency == "HOURLY" ||
                task.repeatFrequency == "EVERY_X_MINUTES" ||
                task.repeatFrequency == "MINUTELY" ||
                task.repeatFrequency == "DAILY"

        if (isRecurring) {
            val nextTrigger = when (task.repeatFrequency) {
                "EVERY_X_MINUTES", "MINUTELY" -> {
                    val mins = if (task.intervalMinutes > 0) task.intervalMinutes else 15
                    now + (mins * 60 * 1000L)
                }
                "HOURLY" -> {
                    val hours = if (task.intervalMinutes > 0) (task.intervalMinutes / 60).coerceAtLeast(1) else 1
                    now + (hours * 3600 * 1000L)
                }
                "DAILY" -> {
                    calculateNextTriggerMillis(task.hour, task.minute, "DAILY", 0, task.timezoneId)
                }
                else -> 0L
            }

            if (nextTrigger > 0L) {
                val updatedTask = task.copy(
                    scheduledTimeMillis = nextTrigger,
                    lastExecutedTimeMillis = now,
                    executionCount = task.executionCount + 1,
                    lastResultText = resultDetail,
                    status = if (sendSuccess) "PENDING" else "FAILED"
                )
                repository.updateScheduledTask(updatedTask)
                scheduleAlarmManagerExact(updatedTask)
                Log.i(TAG, "Task #${task.id} (${task.repeatFrequency}) rescheduled for $nextTrigger")
            }
        } else {
            val updatedTask = task.copy(
                lastExecutedTimeMillis = now,
                executionCount = task.executionCount + 1,
                lastResultText = resultDetail,
                status = if (sendSuccess) "EXECUTED" else "FAILED"
            )
            repository.updateScheduledTask(updatedTask)
        }

        return sendSuccess
    }

    private fun resolveTargetPhoneNumber(input: String): Pair<String, String> {
        val trimmed = input.trim()
        if (trimmed.any { it.isLetter() }) {
            try {
                val matches = telephonyManager.searchContacts(trimmed)
                if (matches.isNotEmpty()) {
                    val contact = matches.first()
                    val clean = contact.number.replace(Regex("[^0-9+]"), "")
                    return Pair(clean, contact.name)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Contact lookup failed for $trimmed: ${e.message}")
            }
        }
        val clean = trimmed.replace(Regex("[^0-9+]"), "")
        return Pair(clean, "")
    }

    private fun recordSentSmsInSystemProvider(phoneNumber: String, message: String) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, phoneNumber)
                put(Telephony.Sms.BODY, message)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
            }
            context.contentResolver.insert(Uri.parse("content://sms/sent"), values)
            Log.i(TAG, "Recorded sent SMS in content://sms/sent")
        } catch (e: Exception) {
            Log.w(TAG, "Could not record to content://sms/sent (normal if non-default SMS app): ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    fun sendSmsDirect(phoneNumber: String, message: String): Pair<Boolean, String> {
        val (cleanNumber, contactName) = resolveTargetPhoneNumber(phoneNumber)
        if (cleanNumber.isBlank()) {
            return Pair(false, "Invalid recipient: '$phoneNumber' could not be resolved.")
        }

        // Check SEND_SMS runtime permission
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SEND_SMS permission not granted. Falling back to SMS composer.")
            val opened = telephonyManager.openSmsComposer(cleanNumber, message)
            return if (opened) {
                Pair(true, "SEND_SMS permission needed; pre-filled in SMS app for $cleanNumber.")
            } else {
                Pair(false, "SEND_SMS permission is not granted. Please allow SMS permission in Permissions tab.")
            }
        }

        return try {
            val smsManager: SmsManager = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
                    if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                    } else {
                        context.getSystemService(SmsManager::class.java)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
                    if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        SmsManager.getSmsManagerForSubscriptionId(subId)
                    } else {
                        SmsManager.getDefault()
                    }
                }
            } catch (e: Exception) {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanNumber, null, message, null, null)
            }

            recordSentSmsInSystemProvider(cleanNumber, message)
            Log.i(TAG, "SMS successfully dispatched directly via SmsManager to $cleanNumber")
            Pair(true, "SMS sent directly to $cleanNumber${if (contactName.isNotBlank()) " ($contactName)" else ""}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS directly via SmsManager to $cleanNumber, falling back to composer", e)
            val opened = telephonyManager.openSmsComposer(cleanNumber, message)
            if (opened) {
                Pair(true, "Cellular error; opened SMS draft composer for $cleanNumber.")
            } else {
                Pair(false, "Failed to send SMS to $cleanNumber: ${e.message}")
            }
        }
    }

    private fun postExecutionNotification(task: ScheduledTask, isSuccess: Boolean, statusDetail: String = "") {
        try {
            val title = when {
                !isSuccess -> "Jarvis Auto Text Failed ✗"
                statusDetail.contains("opened SMS", ignoreCase = true) -> "Jarvis SMS App Ready ↗"
                else -> "Jarvis Auto Text Sent ✓"
            }
            val body = if (statusDetail.isNotBlank()) statusDetail else "To ${task.recipientName.ifBlank { task.targetPhoneNumber }}: \"${task.messageText}\""

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\nDraft: \"${task.messageText}\""))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify((10000 + task.id).toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification for task #${task.id}", e)
        }
    }

    private fun postGeminiExecutionNotification(task: ScheduledTask, responseText: String) {
        try {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpen = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Jarvis Gemini Scheduled Update: ${task.title}")
                .setContentText(responseText.take(120))
                .setStyle(NotificationCompat.BigTextStyle().bigText("Prompt: ${task.messageText}\n\n$responseText"))
                .setContentIntent(pendingOpen)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify((20000 + task.id).toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification for Gemini task #${task.id}", e)
        }
    }

    fun rescheduleAllPendingTasks() {
        scope.launch(Dispatchers.IO) {
            try {
                val pending = repository.getPendingScheduledTasksSync()
                Log.i(TAG, "Rescheduling ${pending.size} active tasks after boot/start...")
                for (task in pending) {
                    val trigger = if (task.scheduledTimeMillis <= System.currentTimeMillis()) {
                        calculateNextTriggerMillis(
                            hour = task.hour,
                            minute = task.minute,
                            repeatFrequency = task.repeatFrequency,
                            intervalMinutes = task.intervalMinutes,
                            timezoneId = task.timezoneId
                        )
                    } else {
                        task.scheduledTimeMillis
                    }
                    val updated = task.copy(scheduledTimeMillis = trigger)
                    repository.updateScheduledTask(updated)
                    scheduleAlarmManagerExact(updated)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling pending tasks", e)
            }
        }
    }
}
