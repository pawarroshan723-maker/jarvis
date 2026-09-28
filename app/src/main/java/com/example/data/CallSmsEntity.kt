package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_sms_logs")
data class CallSmsLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // INCOMING_CALL, MISSED_CALL, INCOMING_SMS, AUTO_REPLY_SMS, OUTGOING_CALL, OUTGOING_SMS
    val phoneNumber: String,
    val contactName: String = "",
    val messageBody: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SUCCESS" // SUCCESS, REPLIED, ANNOUNCED, DELIVERED, FAILED
)

@Entity(tableName = "auto_sms_rules")
data class AutoSmsRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,
    val replyTemplate: String,
    val isEnabled: Boolean = true,
    val matchType: String = "CONTAINS" // CONTAINS, EXACT, STARTS_WITH
)
