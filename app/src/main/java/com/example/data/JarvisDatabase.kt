package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [AutomationRule::class, ExecutionLog::class, CommandHistoryItem::class, CallSmsLog::class, AutoSmsRule::class, ScheduledTask::class],
    version = 4,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun jarvisDao(): JarvisDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_core_db"
                ).addCallback(JarvisDatabaseCallback(scope))
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class JarvisDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialRules(database.jarvisDao())
                    }
                }
            }

            private suspend fun populateInitialRules(dao: JarvisDao) {
                val defaults = listOf(
                    AutomationRule(
                        name = "Shake to Toggle Flashlight (Pocket Protected)",
                        triggerType = TriggerTypes.SHAKE,
                        triggerThreshold = 26.0f,
                        actionType = ActionTypes.TOGGLE_FLASHLIGHT,
                        actionParam = "Torch toggled by deliberate kinetic trigger",
                        isEnabled = true,
                        cooldownSeconds = 3
                    ),
                    AutomationRule(
                        name = "Face-Down Meeting Silence",
                        triggerType = TriggerTypes.FLIP_FACE_DOWN,
                        triggerThreshold = 0f,
                        actionType = ActionTypes.MUTE_ALL,
                        actionParam = "Muted for privacy",
                        isEnabled = true,
                        cooldownSeconds = 5
                    ),
                    AutomationRule(
                        name = "Desk Face-Up Ready Volume",
                        triggerType = TriggerTypes.FLIP_FACE_UP,
                        triggerThreshold = 0f,
                        actionType = ActionTypes.SET_VOLUME,
                        actionParam = "75",
                        isEnabled = true,
                        cooldownSeconds = 10
                    ),
                    AutomationRule(
                        name = "Low Battery Voice Alert",
                        triggerType = TriggerTypes.BATTERY_LOW,
                        triggerThreshold = 20.0f,
                        actionType = ActionTypes.SPEAK_TTS,
                        actionParam = "Battery power depleted below twenty percent. Please connect charger.",
                        isEnabled = true,
                        cooldownSeconds = 60
                    ),
                    AutomationRule(
                        name = "Full Battery Charge Notice",
                        triggerType = TriggerTypes.BATTERY_FULL,
                        triggerThreshold = 100.0f,
                        actionType = ActionTypes.SPEAK_TTS,
                        actionParam = "Battery fully charged at one hundred percent, sir. You may disconnect the charger.",
                        isEnabled = true,
                        cooldownSeconds = 120
                    ),
                    AutomationRule(
                        name = "Power Connected Confirmation",
                        triggerType = TriggerTypes.CHARGER_CONNECTED,
                        triggerThreshold = 0f,
                        actionType = ActionTypes.SPEAK_TTS,
                        actionParam = "Power source connected. Jarvis core recharging.",
                        isEnabled = true,
                        cooldownSeconds = 10
                    ),
                    AutomationRule(
                        name = "Power Disconnected Alert",
                        triggerType = TriggerTypes.CHARGER_DISCONNECTED,
                        triggerThreshold = 0f,
                        actionType = ActionTypes.SPEAK_TTS,
                        actionParam = "Power source disconnected. Running on internal battery.",
                        isEnabled = true,
                        cooldownSeconds = 10
                    ),
                    AutomationRule(
                        name = "Outdoor Sunlight Sound Boost",
                        triggerType = TriggerTypes.LIGHT_ABOVE,
                        triggerThreshold = 1000.0f,
                        actionType = ActionTypes.MAX_VOLUME,
                        actionParam = "Max volume for outdoor environment",
                        isEnabled = false,
                        cooldownSeconds = 30
                    ),
                    AutomationRule(
                        name = "Dark Light Night Mode",
                        triggerType = TriggerTypes.LIGHT_BELOW,
                        triggerThreshold = 5.0f,
                        actionType = ActionTypes.SET_VOLUME,
                        actionParam = "20",
                        isEnabled = false,
                        cooldownSeconds = 30
                    ),
                    AutomationRule(
                        name = "Phone Picked Up Time Notice",
                        triggerType = TriggerTypes.ORIENTATION_UPRIGHT,
                        triggerThreshold = 0f,
                        actionType = ActionTypes.ANNOUNCE_TIME,
                        actionParam = "Spoken time announcement",
                        isEnabled = false,
                        cooldownSeconds = 60
                    )
                )
                for (rule in defaults) {
                    dao.insertRule(rule)
                }

                // Default Auto-SMS intelligent keyword rules (Multilingual & High Automation)
                val defaultSmsRules = listOf(
                    AutoSmsRule(
                        keyword = "where are you",
                        replyTemplate = "Jarvis Auto: The user is currently mobile. Battery: {battery}%, Status: {charging}.",
                        isEnabled = true,
                        matchType = "CONTAINS"
                    ),
                    AutoSmsRule(
                        keyword = "urgent",
                        replyTemplate = "Jarvis Emergency Alert: Urgent priority acknowledged. Alerting user immediately. Status: {charging}, Battery: {battery}%.",
                        isEnabled = true,
                        matchType = "CONTAINS"
                    ),
                    AutoSmsRule(
                        keyword = "status",
                        replyTemplate = "Jarvis Diagnostics: Battery at {battery}%, {charging}. Systems nominal.",
                        isEnabled = true,
                        matchType = "CONTAINS"
                    ),
                    AutoSmsRule(
                        keyword = "call me",
                        replyTemplate = "Jarvis Auto: Message received. User informed to call you shortly.",
                        isEnabled = true,
                        matchType = "CONTAINS"
                    ),
                    AutoSmsRule(
                        keyword = "meeting",
                        replyTemplate = "Jarvis Auto: User is currently in a meeting. Will reply as soon as free.",
                        isEnabled = true,
                        matchType = "CONTAINS"
                    ),
                    AutoSmsRule(
                        keyword = "कुठे आहेस",
                        replyTemplate = "जार्व्हिस ऑटो: युझर सध्या व्यस्त आहे. बॅटरी: {battery}%. लवकरच संपर्क करतील.",
                        isEnabled = true,
                        matchType = "CONTAINS"
                    ),
                    AutoSmsRule(
                        keyword = "कॉल कर",
                        replyTemplate = "जार्व्हिस ऑटो: तुमचा संदेश मिळाला आहे. युझर थोड्या वेळात कॉल करतील.",
                        isEnabled = true,
                        matchType = "CONTAINS"
                    ),
                    AutoSmsRule(
                        keyword = "help",
                        replyTemplate = "Jarvis Emergency: Assistance request detected. Alerting user right away.",
                        isEnabled = true,
                        matchType = "CONTAINS"
                    ),
                    AutoSmsRule(
                        keyword = "driving",
                        replyTemplate = "Jarvis Auto: User is currently driving. Please call in case of emergency.",
                        isEnabled = true,
                        matchType = "CONTAINS"
                    ),
                    AutoSmsRule(
                        keyword = "good morning",
                        replyTemplate = "Jarvis Auto: Good morning! User has received your message.",
                        isEnabled = true,
                        matchType = "CONTAINS"
                    )
                )
                for (smsRule in defaultSmsRules) {
                    dao.insertAutoSmsRule(smsRule)
                }

                dao.insertLog(
                    ExecutionLog(
                        ruleName = "Core Initialization",
                        triggerType = "SYSTEM_BOOT",
                        details = "Jarvis automation core initialized with expanded rules catalog.",
                        isSuccess = true
                    )
                )
            }
        }
    }
}
