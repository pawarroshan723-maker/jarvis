package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JarvisDao {
    // Automation Rules
    @Query("SELECT * FROM automation_rules ORDER BY id ASC")
    fun getAllRules(): Flow<List<AutomationRule>>

    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    suspend fun getActiveRulesSync(): List<AutomationRule>

    @Query("SELECT * FROM automation_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): AutomationRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutomationRule): Long

    @Update
    suspend fun updateRule(rule: AutomationRule)

    @Query("UPDATE automation_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setRuleEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE automation_rules SET lastTriggeredTime = :timestamp, triggerCount = triggerCount + 1 WHERE id = :id")
    suspend fun recordRuleTriggered(id: Long, timestamp: Long)

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    // Execution Logs
    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentLogs(): Flow<List<ExecutionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ExecutionLog): Long

    @Query("DELETE FROM execution_logs")
    suspend fun clearLogs()

    // Command History
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT 50")
    fun getCommandHistory(): Flow<List<CommandHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(item: CommandHistoryItem): Long

    @Query("DELETE FROM command_history")
    suspend fun clearHistory()

    // Call & SMS Logs
    @Query("SELECT * FROM call_sms_logs ORDER BY timestamp DESC LIMIT 60")
    fun getRecentCallSmsLogs(): Flow<List<CallSmsLog>>

    @Query("SELECT * FROM call_sms_logs ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentCallSmsLogsSync(): List<CallSmsLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallSmsLog(log: CallSmsLog): Long

    @Update
    suspend fun updateCallSmsLog(log: CallSmsLog)

    @Query("DELETE FROM call_sms_logs")
    suspend fun clearCallSmsLogs()

    // Auto SMS Rules
    @Query("SELECT * FROM auto_sms_rules ORDER BY id ASC")
    fun getAllAutoSmsRules(): Flow<List<AutoSmsRule>>

    @Query("SELECT * FROM auto_sms_rules WHERE isEnabled = 1")
    suspend fun getActiveAutoSmsRulesSync(): List<AutoSmsRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutoSmsRule(rule: AutoSmsRule): Long

    @Update
    suspend fun updateAutoSmsRule(rule: AutoSmsRule)

    @Query("UPDATE auto_sms_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setAutoSmsRuleEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM auto_sms_rules WHERE id = :id")
    suspend fun deleteAutoSmsRule(id: Long)

    // Scheduled Tasks
    @Query("SELECT * FROM scheduled_tasks ORDER BY scheduledTimeMillis ASC")
    fun getAllScheduledTasks(): Flow<List<ScheduledTask>>

    @Query("SELECT * FROM scheduled_tasks WHERE isEnabled = 1 AND status = 'PENDING'")
    suspend fun getPendingScheduledTasksSync(): List<ScheduledTask>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun getScheduledTaskById(id: Long): ScheduledTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledTask(task: ScheduledTask): Long

    @Update
    suspend fun updateScheduledTask(task: ScheduledTask)

    @Query("UPDATE scheduled_tasks SET isEnabled = :enabled WHERE id = :id")
    suspend fun setScheduledTaskEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE scheduled_tasks SET status = :status, lastExecutedTimeMillis = :timestamp, executionCount = executionCount + 1 WHERE id = :id")
    suspend fun recordScheduledTaskExecuted(id: Long, status: String, timestamp: Long)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteScheduledTask(id: Long)

    @Query("DELETE FROM scheduled_tasks")
    suspend fun deleteAllScheduledTasks()

    @Query("DELETE FROM scheduled_tasks WHERE status = 'EXECUTED'")
    suspend fun deleteCompletedScheduledTasks()
}
