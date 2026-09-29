package com.example.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.JarvisApplication

/**
 * Handles device boot and app update broadcasts to restore scheduled alarms.
 * Guarded strictly by the RECEIVE_BOOT_COMPLETED permission.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Received system boot/update broadcast: $action")

        val app = context.applicationContext as? JarvisApplication ?: return
        val taskManager = app.scheduledTaskManager

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.MY_PACKAGE_REPLACED" ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i(TAG, "Device booted or package replaced. Rescheduling all pending tasks...")
            taskManager.rescheduleAllPendingTasks()
        }
    }
}
