package com.example.system

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat

data class ContactEntry(
    val name: String,
    val number: String,
    val type: String = "Mobile"
)

data class PermissionStatus(
    val permission: String,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isCrucial: Boolean = true
)

class TelephonyAlarmManager(private val context: Context) {

    companion object {
        const val TAG = "TelephonyAlarmManager"

        val ALL_PERMISSIONS = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }.toTypedArray()
    }

    // --- PERMISSION CHECKS ---
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getAllPermissionsStatus(): List<PermissionStatus> {
        val list = mutableListOf<PermissionStatus>()

        list.add(
            PermissionStatus(
                permission = Manifest.permission.RECORD_AUDIO,
                title = "Microphone",
                description = "Required for hands-free Jarvis speech recognition and voice commands.",
                isGranted = hasPermission(Manifest.permission.RECORD_AUDIO)
            )
        )

        list.add(
            PermissionStatus(
                permission = Manifest.permission.CALL_PHONE,
                title = "Phone Calls",
                description = "Allows Jarvis to initiate direct voice calls to contacts and dialed numbers.",
                isGranted = hasPermission(Manifest.permission.CALL_PHONE)
            )
        )

        list.add(
            PermissionStatus(
                permission = Manifest.permission.SEND_SMS,
                title = "Send SMS",
                description = "Allows Jarvis to send text messages to contacts automatically or by command.",
                isGranted = hasPermission(Manifest.permission.SEND_SMS)
            )
        )

        list.add(
            PermissionStatus(
                permission = Manifest.permission.READ_CONTACTS,
                title = "Read Contacts",
                description = "Enables voice search of address book names to dial or message family and friends.",
                isGranted = hasPermission(Manifest.permission.READ_CONTACTS)
            )
        )

        list.add(
            PermissionStatus(
                permission = Manifest.permission.CAMERA,
                title = "Camera & Torch",
                description = "Required for flashlight control and launching camera visual capture.",
                isGranted = hasPermission(Manifest.permission.CAMERA)
            )
        )

        list.add(
            PermissionStatus(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                title = "GPS Location",
                description = "Enables location-aware responses, telemetry and local environment tracking.",
                isGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(
                PermissionStatus(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    title = "Notifications",
                    description = "Displays background service status and sensor alerts in notification bar.",
                    isGranted = hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(
                PermissionStatus(
                    permission = Manifest.permission.BLUETOOTH_CONNECT,
                    title = "Bluetooth Device",
                    description = "Allows checking Bluetooth connectivity status and device integration.",
                    isGranted = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
                )
            )
        }

        return list
    }

    // --- CALL MANAGEMENT ---
    fun makeCall(number: String): Pair<Boolean, String> {
        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank()) {
            return Pair(false, "Invalid phone number provided.")
        }

        return try {
            if (hasPermission(Manifest.permission.CALL_PHONE)) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Pair(true, "Calling $cleanNumber now.")
            } else {
                // Fallback to dialer
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Pair(true, "Dialer opened for $cleanNumber (Direct Call permission recommended).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating call to $cleanNumber", e)
            Pair(false, "Failed to initiate call: ${e.message}")
        }
    }

    fun callContactByName(contactQuery: String): Pair<Boolean, String> {
        val matches = searchContacts(contactQuery)
        if (matches.isEmpty()) {
            return Pair(false, "Could not find a contact matching '$contactQuery'.")
        }

        val target = matches.first()
        val (success, _) = makeCall(target.number)
        return if (success) {
            Pair(true, "Calling ${target.name} (${target.number}).")
        } else {
            Pair(false, "Failed to call ${target.name}.")
        }
    }

    // --- SMS MANAGEMENT ---
    @Suppress("DEPRECATION")
    fun sendSms(number: String, message: String): Pair<Boolean, String> {
        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank()) {
            return Pair(false, "Invalid phone number provided.")
        }

        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            Log.w(TAG, "SEND_SMS permission not granted. Cannot send background SMS directly.")
            return Pair(
                false,
                "SMS permission (SEND_SMS) is not granted. Please allow SMS permission in the Permissions tab to send messages automatically without going to drafts."
            )
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

            recordSentSms(cleanNumber, message)
            Pair(true, "SMS dispatched successfully to $cleanNumber: \"$message\"")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException sending SMS to $cleanNumber", se)
            Pair(
                false,
                "SMS permission denied by Android security policy. Please grant SEND_SMS in Settings / Permissions tab."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error directly sending SMS to $cleanNumber", e)
            Pair(false, "Failed to send SMS directly: ${e.localizedMessage ?: "Cellular radio error"}")
        }
    }

    private fun recordSentSms(address: String, body: String) {
        try {
            val values = ContentValues().apply {
                put("address", address)
                put("body", body)
                put("date", System.currentTimeMillis())
                put("read", 1)
                put("type", 2) // MESSAGE_TYPE_SENT = 2
            }
            context.contentResolver.insert(Uri.parse("content://sms/sent"), values)
        } catch (e: Exception) {
            Log.w(TAG, "Could not insert sent SMS record into system provider: ${e.message}")
        }
    }

    fun sendSmsToContact(contactQuery: String, message: String): Pair<Boolean, String> {
        val matches = searchContacts(contactQuery)
        if (matches.isEmpty()) {
            return Pair(false, "Could not find a contact matching '$contactQuery'.")
        }

        val target = matches.first()
        val (success, _) = sendSms(target.number, message)
        return if (success) {
            Pair(true, "SMS sent to ${target.name} (${target.number}): \"$message\"")
        } else {
            Pair(false, "Failed to send SMS to ${target.name}.")
        }
    }

    fun openSmsComposer(number: String = "", message: String = ""): Boolean {
        return try {
            val uri = if (number.isNotBlank()) Uri.parse("smsto:$number") else Uri.parse("smsto:")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                if (message.isNotBlank()) {
                    putExtra("sms_body", message)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SMS composer", e)
            false
        }
    }

    // --- CONTACTS SEARCH & QUERY ---
    fun lookupContactNameByNumber(phoneNumber: String): String {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.w(TAG, "READ_CONTACTS permission not granted for lookup")
            return ""
        }
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isBlank()) return ""

        try {
            // 1. Primary method: ContactsContract.PhoneLookup
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(cleanNumber)
            )
            val lookupProjection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.NUMBER
            )
            val lookupCursor = context.contentResolver.query(
                lookupUri,
                lookupProjection,
                null,
                null,
                null
            )
            lookupCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) {
                            Log.d(TAG, "PhoneLookup found contact: '$name' for $cleanNumber")
                            return name
                        }
                    }
                }
            }

            // 2. Secondary fallback: Normalized digit suffix matching in CommonDataKinds.Phone (last 10 digits for Indian/international numbers)
            val digitsOnly = cleanNumber.filter { it.isDigit() }
            if (digitsOnly.length >= 7) {
                val suffix = digitsOnly.takeLast(10)
                val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val phoneProjection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val phoneCursor = context.contentResolver.query(
                    phoneUri,
                    phoneProjection,
                    "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                    arrayOf("%$suffix%"),
                    null
                )
                phoneCursor?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (nameIdx != -1) {
                            val name = cursor.getString(nameIdx)
                            if (!name.isNullOrBlank()) {
                                Log.d(TAG, "Digit suffix match found contact: '$name' for $cleanNumber")
                                return name
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name by number: $cleanNumber", e)
        }

        return ""
    }

    fun searchContacts(query: String): List<ContactEntry> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.w(TAG, "READ_CONTACTS permission not granted")
            return emptyList()
        }

        val results = mutableListOf<ContactEntry>()
        val cleanQuery = query.trim()

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            )

            // 1. Primary search: CONTENT_FILTER_URI for fast indexed name and prefix matching
            if (cleanQuery.isNotBlank()) {
                try {
                    val filterUri = Uri.withAppendedPath(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                        Uri.encode(cleanQuery)
                    )
                    context.contentResolver.query(
                        filterUri,
                        projection,
                        null,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                    )?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                        while (cursor.moveToNext() && results.size < 60) {
                            val name = if (nameIndex != -1) cursor.getString(nameIndex) else "Unknown"
                            val number = if (numberIndex != -1) cursor.getString(numberIndex) else ""
                            val typeCode = if (typeIndex != -1) cursor.getInt(typeIndex) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                            val typeStr = when (typeCode) {
                                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                                ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                                ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                                else -> "Other"
                            }

                            if (number.isNotBlank()) {
                                results.add(ContactEntry(name = name ?: "Unknown", number = number, type = typeStr))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "CONTENT_FILTER_URI query exception, falling back to CONTENT_URI", e)
                }
            }

            // 2. Secondary search or initial list if filter produced few/no results
            if (results.isEmpty()) {
                val selection = if (cleanQuery.isNotBlank()) {
                    "(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?)"
                } else null

                val selectionArgs = if (cleanQuery.isNotBlank()) {
                    arrayOf("%$cleanQuery%", "%$cleanQuery%")
                } else null

                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                )?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                    while (cursor.moveToNext() && results.size < 80) {
                        val name = if (nameIndex != -1) cursor.getString(nameIndex) else "Unknown"
                        val number = if (numberIndex != -1) cursor.getString(numberIndex) else ""
                        val typeCode = if (typeIndex != -1) cursor.getInt(typeIndex) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        val typeStr = when (typeCode) {
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                            else -> "Other"
                        }

                        if (number.isNotBlank()) {
                            results.add(ContactEntry(name = name ?: "Unknown", number = number, type = typeStr))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
        }

        return results.distinctBy { it.name.lowercase() + it.number.replace(Regex("[^0-9]"), "") }
    }

    // --- ALARM & TIMER MANAGEMENT ---
    fun setAlarm(
        hour: Int,
        minute: Int,
        message: String = "Jarvis Alarm",
        skipUi: Boolean = true
    ): Pair<Boolean, String> {
        val clampedHour = hour.coerceIn(0, 23)
        val clampedMinute = minute.coerceIn(0, 59)
        val timeFormatted = String.format("%02d:%02d", clampedHour, clampedMinute)

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, clampedHour)
                putExtra(AlarmClock.EXTRA_MINUTES, clampedMinute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Alarm set for $timeFormatted with label \"$message\".")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting alarm", e)
            Pair(false, "Failed to set alarm: ${e.message}")
        }
    }

    fun setTimer(
        seconds: Int,
        message: String = "Jarvis Timer",
        skipUi: Boolean = true
    ): Pair<Boolean, String> {
        val duration = seconds.coerceAtLeast(1)
        val min = duration / 60
        val sec = duration % 60
        val durationStr = if (min > 0) "$min min $sec sec" else "$sec seconds"

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, duration)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Timer set for $durationStr ($message).")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting timer", e)
            Pair(false, "Failed to set timer: ${e.message}")
        }
    }

    fun showAlarms(): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show alarms", e)
            false
        }
    }

    fun showTimers(): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show timers", e)
            false
        }
    }

    // --- CALENDAR MANAGEMENT ---
    fun addCalendarEvent(
        title: String,
        description: String = "",
        startMillis: Long = System.currentTimeMillis() + 3600000L,
        durationMinutes: Int = 60
    ): Pair<Boolean, String> {
        return try {
            val endMillis = startMillis + (durationMinutes * 60 * 1000L)
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, description)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Created calendar event: \"$title\".")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert calendar event", e)
            Pair(false, "Unable to create calendar event.")
        }
    }

    fun openCalendar(): Boolean {
        return try {
            val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open calendar", e)
            false
        }
    }
}
