package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_logs")
data class ExecutionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ruleName: String,
    val triggerType: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true
)

@Entity(tableName = "command_history")
data class CommandHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val queryText: String,
    val replyText: String,
    val source: String = "OFFLINE_LOCAL", // OFFLINE_LOCAL or GEMINI_ONLINE
    val timestamp: Long = System.currentTimeMillis()
)
