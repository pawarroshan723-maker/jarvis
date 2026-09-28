package com.example.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.JarvisApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledTaskReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduledTaskReceiver"
        private const val WAKELOCK_TAG = "Jarvis:ScheduledTaskWakeLock"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Received Broadcast: $action")

        val app = context.applicationContext as? JarvisApplication ?: return
        val taskManager = app.scheduledTaskManager

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.MY_PACKAGE_REPLACED" ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i(TAG, "Device booted or app updated. Rescheduling all active scheduled tasks...")
            taskManager.rescheduleAllPendingTasks()
            return
        }

        if (action == ScheduledTaskManager.ACTION_EXECUTE_TASK) {
            val taskId = intent.getLongExtra(ScheduledTaskManager.EXTRA_TASK_ID, -1L)
            if (taskId <= 0L) {
                Log.w(TAG, "Received execute broadcast with invalid task ID: $taskId")
                return
            }

            Log.i(TAG, "Executing scheduled task #$taskId from alarm broadcast...")

            val pendingResult = goAsync()
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)?.apply {
                acquire(15000) // Acquire WakeLock for up to 15s to guarantee completion
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    taskManager.executeTask(taskId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing scheduled task #$taskId", e)
                } finally {
                    try {
                        if (wakeLock != null && wakeLock.isHeld) {
                            wakeLock.release()
                        }
                    } catch (ignored: Exception) {}
                    try {
                        pendingResult.finish()
                    } catch (ignored: Exception) {}
                }
            }
        }
    }
}
