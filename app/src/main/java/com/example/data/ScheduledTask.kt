package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "Scheduled Task",
    val taskType: String = "AUTO_SMS", // AUTO_SMS, GEMINI_QUERY, GEMINI_TO_SMS
    val targetPhoneNumber: String = "",
    val recipientName: String = "",
    val messageText: String = "", // Prompt for Gemini or SMS body
    val hour: Int = 0, // 0 to 23
    val minute: Int = 0, // 0 to 59
    val intervalMinutes: Int = 0, // 0 for exact time, or 5, 10, 15, 30, 60 for interval repeating
    val timezoneId: String = "Asia/Kolkata", // "Asia/Kolkata" (India Standard Time) or "LOCAL"
    val scheduledTimeMillis: Long = 0L,
    val repeatFrequency: String = "ONCE", // ONCE, EVERY_X_MINUTES, HOURLY, DAILY
    val isEnabled: Boolean = true,
    val status: String = "PENDING", // PENDING, EXECUTED, CANCELLED, FAILED
    val lastResultText: String = "", // Output from Gemini AI or delivery status
    val lastExecutedTimeMillis: Long = 0L,
    val executionCount: Int = 0,
    val createdTimeMillis: Long = System.currentTimeMillis()
)

