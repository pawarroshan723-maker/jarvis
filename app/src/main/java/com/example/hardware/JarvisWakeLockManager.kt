package com.example.hardware

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisWakeLockManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    private var persistentWakeLock: PowerManager.WakeLock? = null
    private var temporaryWakeLock: PowerManager.WakeLock? = null

    private val _isWakeLockActive = MutableStateFlow(false)
    val isWakeLockActive: StateFlow<Boolean> = _isWakeLockActive.asStateFlow()

    fun acquireCpuWakeLock(timeoutMs: Long = 10000, tag: String = "Jarvis:OfflineCoreExecution") {
        try {
            if (temporaryWakeLock == null) {
                temporaryWakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)?.apply {
                    setReferenceCounted(false)
                }
            }
            temporaryWakeLock?.acquire(timeoutMs)
            _isWakeLockActive.value = true
            Log.d("JarvisWakeLock", "CPU WakeLock acquired for ${timeoutMs}ms ($tag)")
        } catch (e: Exception) {
            Log.w("JarvisWakeLock", "Failed to acquire CPU wake lock: ${e.message}")
        }
    }

    fun releaseCpuWakeLock() {
        try {
            if (temporaryWakeLock?.isHeld == true) {
                temporaryWakeLock?.release()
            }
            if (persistentWakeLock?.isHeld != true) {
                _isWakeLockActive.value = false
            }
        } catch (e: Exception) {
            Log.w("JarvisWakeLock", "Failed to release CPU wake lock: ${e.message}")
        }
    }

    fun setPersistentWakeLock(enabled: Boolean): Boolean {
        return try {
            if (enabled) {
                if (persistentWakeLock == null) {
                    persistentWakeLock = powerManager?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "Jarvis:PersistentAutomationWakeLock"
                    )?.apply {
                        setReferenceCounted(false)
                    }
                }
                persistentWakeLock?.acquire()
                _isWakeLockActive.value = true
                Log.i("JarvisWakeLock", "Persistent automation WakeLock enabled")
                true
            } else {
                if (persistentWakeLock?.isHeld == true) {
                    persistentWakeLock?.release()
                }
                persistentWakeLock = null
                _isWakeLockActive.value = temporaryWakeLock?.isHeld == true
                Log.i("JarvisWakeLock", "Persistent automation WakeLock disabled")
                true
            }
        } catch (e: Exception) {
            Log.e("JarvisWakeLock", "Error toggling persistent wake lock", e)
            false
        }
    }

    fun wakeUpScreen(durationMs: Long = 5000) {
        try {
            @Suppress("DEPRECATION")
            val screenLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "Jarvis:ScreenWakeUpLock"
            )
            screenLock?.acquire(durationMs)
            Log.d("JarvisWakeLock", "Screen bright wake lock acquired for ${durationMs}ms")
        } catch (e: Exception) {
            Log.w("JarvisWakeLock", "Screen wake failed: ${e.message}")
        }
    }

    fun isScreenOn(): Boolean {
        return powerManager?.isInteractive ?: true
    }

    fun isDeviceLocked(): Boolean {
        return keyguardManager?.isKeyguardLocked ?: false
    }

    fun releaseAll() {
        try {
            if (persistentWakeLock?.isHeld == true) {
                persistentWakeLock?.release()
            }
            if (temporaryWakeLock?.isHeld == true) {
                temporaryWakeLock?.release()
            }
            persistentWakeLock = null
            temporaryWakeLock = null
            _isWakeLockActive.value = false
        } catch (e: Exception) {
            Log.w("JarvisWakeLock", "Error releasing all wakelocks: ${e.message}")
        }
    }
}
