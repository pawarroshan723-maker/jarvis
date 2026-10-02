package com.example.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.Log
import com.example.JarvisApplication

class JarvisSmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "JarvisSmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
            intent.action != "android.provider.Telephony.SMS_RECEIVED"
        ) {
            return
        }

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val app = context.applicationContext as? JarvisApplication ?: return
            val autoManager = app.autoCallSmsManager

            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: "Unknown"
                val body = sms.displayMessageBody ?: sms.messageBody ?: ""
                Log.i(TAG, "SMS received from: $sender: $body")
                autoManager.onSmsReceived(sender, body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming SMS broadcast", e)
        }
    }
}

class JarvisCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "JarvisCallReceiver"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var savedNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        try {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            val app = context.applicationContext as? JarvisApplication ?: return
            val autoManager = app.autoCallSmsManager

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    lastState = TelephonyManager.CALL_STATE_RINGING
                    savedNumber = incomingNumber
                    val numberToReport = incomingNumber ?: "Private Number"
                    Log.i(TAG, "Incoming call ringing: $numberToReport")
                    autoManager.onCallRinging(numberToReport)
                    app.speechManager.onCallStateChanged(TelephonyManager.CALL_STATE_RINGING)
                }

                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        Log.i(TAG, "Call answered (off-hook)")
                        autoManager.onCallAnswered()
                    }
                    lastState = TelephonyManager.CALL_STATE_OFFHOOK
                    app.speechManager.onCallStateChanged(TelephonyManager.CALL_STATE_OFFHOOK)
                }

                TelephonyManager.EXTRA_STATE_IDLE -> {
                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        Log.i(TAG, "Call missed/rejected")
                        autoManager.onCallEnded()
                    } else if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                        Log.i(TAG, "Call completed")
                        autoManager.onCallEnded()
                    }
                    lastState = TelephonyManager.CALL_STATE_IDLE
                    savedNumber = null
                    app.speechManager.onCallStateChanged(TelephonyManager.CALL_STATE_IDLE)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling phone state broadcast", e)
        }
    }
}
