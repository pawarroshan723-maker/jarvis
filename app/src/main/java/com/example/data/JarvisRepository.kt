package com.example.data

import kotlinx.coroutines.flow.Flow

class JarvisRepository(private val dao: JarvisDao) {

    val allRules: Flow<List<AutomationRule>> = dao.getAllRules()
    val recentLogs: Flow<List<ExecutionLog>> = dao.getRecentLogs()
    val commandHistory: Flow<List<CommandHistoryItem>> = dao.getCommandHistory()
    val recentCallSmsLogs: Flow<List<CallSmsLog>> = dao.getRecentCallSmsLogs()
    val allAutoSmsRules: Flow<List<AutoSmsRule>> = dao.getAllAutoSmsRules()
    val allScheduledTasks: Flow<List<ScheduledTask>> = dao.getAllScheduledTasks()

    suspend fun getActiveRulesSync(): List<AutomationRule> = dao.getActiveRulesSync()
    suspend fun getActiveAutoSmsRulesSync(): List<AutoSmsRule> = dao.getActiveAutoSmsRulesSync()
    suspend fun getPendingScheduledTasksSync(): List<ScheduledTask> = dao.getPendingScheduledTasksSync()
    suspend fun getScheduledTaskById(id: Long): ScheduledTask? = dao.getScheduledTaskById(id)

    suspend fun insertRule(rule: AutomationRule): Long = dao.insertRule(rule)

    suspend fun updateRule(rule: AutomationRule) = dao.updateRule(rule)

    suspend fun setRuleEnabled(id: Long, enabled: Boolean) = dao.setRuleEnabled(id, enabled)

    suspend fun recordRuleTriggered(id: Long, timestamp: Long) = dao.recordRuleTriggered(id, timestamp)

    suspend fun deleteRule(id: Long) = dao.deleteRule(id)

    suspend fun insertLog(log: ExecutionLog): Long = dao.insertLog(log)

    suspend fun clearLogs() = dao.clearLogs()

    suspend fun insertCommand(item: CommandHistoryItem): Long = dao.insertCommand(item)

    suspend fun clearHistory() = dao.clearHistory()

    // Call and SMS methods
    suspend fun insertCallSmsLog(log: CallSmsLog): Long = dao.insertCallSmsLog(log)

    suspend fun updateCallSmsLog(log: CallSmsLog) = dao.updateCallSmsLog(log)

    suspend fun getRecentCallSmsLogsSync(): List<CallSmsLog> = dao.getRecentCallSmsLogsSync()

    suspend fun clearCallSmsLogs() = dao.clearCallSmsLogs()

    suspend fun insertAutoSmsRule(rule: AutoSmsRule): Long = dao.insertAutoSmsRule(rule)

    suspend fun updateAutoSmsRule(rule: AutoSmsRule) = dao.updateAutoSmsRule(rule)

    suspend fun setAutoSmsRuleEnabled(id: Long, enabled: Boolean) = dao.setAutoSmsRuleEnabled(id, enabled)

    suspend fun deleteAutoSmsRule(id: Long) = dao.deleteAutoSmsRule(id)

    // Scheduled Tasks methods
    suspend fun insertScheduledTask(task: ScheduledTask): Long = dao.insertScheduledTask(task)

    suspend fun updateScheduledTask(task: ScheduledTask) = dao.updateScheduledTask(task)

    suspend fun setScheduledTaskEnabled(id: Long, enabled: Boolean) = dao.setScheduledTaskEnabled(id, enabled)

    suspend fun recordScheduledTaskExecuted(id: Long, status: String, timestamp: Long) =
        dao.recordScheduledTaskExecuted(id, status, timestamp)

    suspend fun deleteScheduledTask(id: Long) = dao.deleteScheduledTask(id)

    suspend fun deleteAllScheduledTasks() = dao.deleteAllScheduledTasks()

    suspend fun deleteCompletedScheduledTasks() = dao.deleteCompletedScheduledTasks()

    suspend fun enforceSafeShakeThreshold() {
        try {
            val rules = dao.getActiveRulesSync()
            for (r in rules) {
                if (r.triggerType == TriggerTypes.SHAKE && r.triggerThreshold < 24.0f) {
                    dao.updateRule(
                        r.copy(
                            triggerThreshold = 26.0f,
                            name = if (r.name.startsWith("Shake to Toggle Flashlight")) "Shake to Toggle Flashlight (Pocket Protected)" else r.name
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }
}
