package com.example.hardware

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent

enum class MediaControlAction {
    PLAY,
    PAUSE,
    TOGGLE,
    STOP,
    NEXT,
    PREVIOUS
}

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

class HardwareController(private val context: Context) {

    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val vibrator: Vibrator? =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val batteryManager: BatteryManager? =
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private var isTorchOn: Boolean = false
    private var torchCameraId: String? = null

    val wakeLockManager: JarvisWakeLockManager = JarvisWakeLockManager(context)
    val audioPlayer: JarvisAudioPlayer = JarvisAudioPlayer(context)

    init {
        findCameraWithFlash()
    }

    private fun findCameraWithFlash() {
        try {
            cameraManager?.cameraIdList?.forEach { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    torchCameraId = id
                    return
                } else if (hasFlash && torchCameraId == null) {
                    torchCameraId = id
                }
            }
        } catch (e: Exception) {
            Log.e("HardwareController", "Error finding flash camera", e)
        }
    }

    // --- FLASHLIGHT ---
    fun toggleFlashlight(): Boolean {
        return setFlashlight(!isTorchOn)
    }

    fun setFlashlight(enabled: Boolean): Boolean {
        val camId = torchCameraId
        if (cameraManager == null || camId == null) {
            Log.w("HardwareController", "CameraManager or torchId unavailable")
            return false
        }
        return try {
            cameraManager.setTorchMode(camId, enabled)
            isTorchOn = enabled
            true
        } catch (e: CameraAccessException) {
            Log.e("HardwareController", "CameraAccessException toggling torch", e)
            false
        } catch (e: Exception) {
            Log.e("HardwareController", "Error setting torch mode", e)
            false
        }
    }

    fun isFlashlightActive(): Boolean = isTorchOn

    // --- AUDIO & VOLUME ---
    fun setMediaVolumePercent(percent: Int): Boolean {
        if (audioManager == null) return false
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = ((percent.coerceIn(0, 100) / 100f) * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
        return true
    }

    fun increaseVolume(stepPercent: Int = 10): Int {
        val current = getMediaVolumePercent()
        val newVol = (current + stepPercent).coerceAtMost(100)
        setMediaVolumePercent(newVol)
        return newVol
    }

    fun decreaseVolume(stepPercent: Int = 10): Int {
        val current = getMediaVolumePercent()
        val newVol = (current - stepPercent).coerceAtLeast(0)
        setMediaVolumePercent(newVol)
        return newVol
    }

    fun getMediaVolumePercent(): Int {
        if (audioManager == null) return 0
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (max == 0) return 0
        return ((current.toFloat() / max.toFloat()) * 100).toInt()
    }

    fun muteAllAudio(): Boolean {
        if (audioManager == null) return false
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            return true
        } catch (e: Exception) {
            Log.e("HardwareController", "Error muting audio", e)
            return false
        }
    }

    fun setMaxVolume(): Boolean {
        if (audioManager == null) return false
        try {
            val maxMedia = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMedia, AudioManager.FLAG_SHOW_UI)
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            return true
        } catch (e: Exception) {
            Log.e("HardwareController", "Error setting max volume", e)
            return false
        }
    }

    fun setRingerMode(mode: String): Boolean {
        if (audioManager == null) return false
        return try {
            when (mode.uppercase()) {
                "SILENT" -> audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                "VIBRATE" -> audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                else -> audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            true
        } catch (e: Exception) {
            Log.e("HardwareController", "Error setting ringer mode", e)
            false
        }
    }

    fun getRingerModeString(): String {
        return when (audioManager?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Normal"
        }
    }

    // --- HAPTIC / VIBRATION ---
    fun vibrate(durationMs: Long = 200) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.e("HardwareController", "Vibration failed", e)
        }
    }

    // --- APP LAUNCHING & SYSTEM INTENTS ---
    fun launchAppByPackage(packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("HardwareController", "Launch app failed for $packageName", e)
            false
        }
    }

    fun launchCamera(): Boolean {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("HardwareController", "Launch camera failed", e)
            false
        }
    }

    fun launchDialer(number: String? = null): Boolean {
        return try {
            val intent = if (number.isNullOrBlank()) {
                Intent(Intent.ACTION_DIAL)
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("HardwareController", "Launch dialer failed", e)
            false
        }
    }

    fun launchBrowser(url: String = "https://www.google.com"): Boolean {
        return try {
            val targetUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("HardwareController", "Launch browser failed", e)
            false
        }
    }

    fun launchSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("HardwareController", "Launch settings failed", e)
            false
        }
    }

    fun launchBluetoothSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun launchWifiSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun launchAppByName(nameQuery: String): Pair<Boolean, String> {
        val query = nameQuery.trim().lowercase()
        // Special keywords & popular app mappings (English & Marathi)
        when {
            query.contains("camera") || query.contains("कॅमेरा") -> return Pair(launchCamera(), "Camera")
            query.contains("dial") || query.contains("phone") || query.contains("फोन") || query.contains("डायलर") -> return Pair(launchDialer(), "Phone Dialer")
            query.contains("setting") || query.contains("सेटिंग") -> return Pair(launchSettings(), "System Settings")
            query.contains("wifi") || query.contains("wi-fi") || query.contains("वायफाय") -> return Pair(launchWifiSettings(), "Wi-Fi Settings")
            query.contains("bluetooth") || query.contains("ब्लूटूथ") -> return Pair(launchBluetoothSettings(), "Bluetooth Settings")
            query.contains("browser") || query.contains("chrome") || query.contains("क्रोम") || query.contains("ब्राउझर") -> return Pair(launchBrowser(), "Web Browser")
            query.contains("youtube") || query.contains("यूट्यूब") -> {
                val launched = launchAppByPackage("com.google.android.youtube")
                if (launched) return Pair(true, "YouTube")
            }
            query.contains("whatsapp") || query.contains("व्हाट्सअ‍ॅप") || query.contains("व्हॉट्सॲप") -> {
                val launched = launchAppByPackage("com.whatsapp")
                if (launched) return Pair(true, "WhatsApp")
            }
            query.contains("spotify") || query.contains("स्पॉटिफाय") -> {
                val launched = launchAppByPackage("com.spotify.music")
                if (launched) return Pair(true, "Spotify")
            }
            query.contains("instagram") || query.contains("इन्स्टाग्राम") -> {
                val launched = launchAppByPackage("com.instagram.android")
                if (launched) return Pair(true, "Instagram")
            }
            query.contains("telegram") || query.contains("टेलिग्राम") -> {
                val launched = launchAppByPackage("org.telegram.messenger")
                if (launched) return Pair(true, "Telegram")
            }
            query.contains("map") || query.contains("मॅप") || query.contains("नकाशा") -> {
                val launched = launchAppByPackage("com.google.android.apps.maps")
                if (launched) return Pair(true, "Google Maps")
            }
            query.contains("calculator") || query.contains("कॅल्क्युलेटर") -> {
                val launched = launchAppByPackage("com.google.android.calculator") || launchAppByPackage("com.android.calculator2")
                if (launched) return Pair(true, "Calculator")
            }
            query.contains("clock") || query.contains("घड्याळ") || query.contains("अलार्म") -> {
                val launched = launchAppByPackage("com.google.android.deskclock") || launchAppByPackage("com.android.deskclock")
                if (launched) return Pair(true, "Clock")
            }
            query.contains("gallery") || query.contains("photo") || query.contains("गॅलरी") || query.contains("फोटो") -> {
                val launched = launchAppByPackage("com.google.android.apps.photos") || launchAppByPackage("com.android.gallery3d")
                if (launched) return Pair(true, "Photos")
            }
            query.contains("calendar") || query.contains("कॅलेंडर") -> {
                val launched = launchAppByPackage("com.google.android.calendar")
                if (launched) return Pair(true, "Calendar")
            }
            query.contains("gmail") || query.contains("email") || query.contains("ईमेल") || query.contains("जीमेल") -> {
                val launched = launchAppByPackage("com.google.android.gm")
                if (launched) return Pair(true, "Gmail")
            }
            query.contains("vlc") || query.contains("व्हीएलसी") -> {
                val launched = launchAppByPackage("org.videolan.vlc")
                if (launched) return Pair(true, "VLC")
            }
            query.contains("play store") || query.contains("प्ले स्टोअर") || query.contains("store") -> {
                val launched = launchAppByPackage("com.android.vending")
                if (launched) return Pair(true, "Play Store")
            }
        }

        val cleanTarget = query
            .replace("open", "")
            .replace("launch", "")
            .replace("app", "")
            .replace("ॲप", "")
            .replace("उघडा", "")
            .replace("चालू करा", "")
            .trim()

        val installed = getInstalledApps()
        val match = installed.firstOrNull { it.appName.lowercase() == cleanTarget }
            ?: installed.firstOrNull { it.appName.lowercase().contains(cleanTarget) && cleanTarget.length >= 3 }
            ?: installed.firstOrNull { it.packageName.lowercase().contains(cleanTarget) && cleanTarget.length >= 3 }
            ?: installed.firstOrNull { it.appName.lowercase().contains(query) }
            ?: installed.firstOrNull { it.packageName.lowercase().contains(query) }

        return if (match != null) {
            val success = launchAppByPackage(match.packageName)
            Pair(success, match.appName)
        } else {
            Pair(false, nameQuery)
        }
    }

    fun findLocalAudioUri(query: String): Pair<Uri, String>? {
        if (query.isBlank()) return null
        return try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST
            )
            val selection = "${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?"
            val selectionArgs = arrayOf("%$query%", "%$query%")
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: query
                    Pair(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), title)
                } else null
            }
        } catch (e: Exception) {
            Log.w("HardwareController", "Local audio lookup failed: ${e.message}")
            null
        }
    }

    fun wakeUpScreen(durationMs: Long = 5000) {
        wakeLockManager.wakeUpScreen(durationMs)
    }

    fun acquireCpuWakeLock(timeoutMs: Long = 10000, tag: String = "Jarvis:HardwareAction") {
        wakeLockManager.acquireCpuWakeLock(timeoutMs, tag)
    }

    fun releaseCpuWakeLock() {
        wakeLockManager.releaseCpuWakeLock()
    }

    fun togglePersistentWakeLock(): Boolean {
        val currentlyActive = wakeLockManager.isWakeLockActive.value
        return wakeLockManager.setPersistentWakeLock(!currentlyActive)
    }

    fun setPersistentWakeLock(enabled: Boolean): Boolean {
        return wakeLockManager.setPersistentWakeLock(enabled)
    }

    fun isWakeLockActive(): Boolean = wakeLockManager.isWakeLockActive.value

    fun isDeviceLocked(): Boolean = wakeLockManager.isDeviceLocked()

    fun isScreenOn(): Boolean = wakeLockManager.isScreenOn()

    fun resolveYouTubeVideoId(query: String): String? {
        if (query.isBlank()) return null
        return try {
            val task = java.util.concurrent.FutureTask {
                fetchFirstVideoId(query)
            }
            Thread(task).start()
            task.get(2200, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Log.w("HardwareController", "resolveYouTubeVideoId error: ${e.message}")
            null
        }
    }

    private fun fetchFirstVideoId(query: String): String? {
        var conn: java.net.HttpURLConnection? = null
        return try {
            val encodedQuery = Uri.encode(query)
            val url = java.net.URL("https://www.youtube.com/results?search_query=$encodedQuery")
            conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
            }
            val stream = conn.inputStream
            val reader = java.io.BufferedReader(java.io.InputStreamReader(stream, Charsets.UTF_8))
            val pattern = java.util.regex.Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
            val sb = StringBuilder()
            val buf = CharArray(8192)
            var n: Int
            var foundId: String? = null
            var total = 0
            while (reader.read(buf).also { n = it } != -1 && total < 1_500_000) {
                total += n
                sb.append(buf, 0, n)
                val m = pattern.matcher(sb)
                if (m.find()) {
                    foundId = m.group(1)
                    break
                }
                if (sb.length > 200) {
                    sb.delete(0, sb.length - 100)
                }
            }
            reader.close()
            foundId
        } catch (e: Exception) {
            Log.w("HardwareController", "fetchFirstVideoId failed: ${e.message}")
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    fun playSongOrMusic(songQuery: String = "", preferredPlayer: String? = null): Pair<Boolean, String> {
        // Acquire CPU WakeLock and wake screen so player runs smoothly when device is locked/asleep
        acquireCpuWakeLock(15000, "Jarvis:PlayerExecution")
        wakeUpScreen(8000)

        val rawQuery = songQuery.trim()
        val pm = context.packageManager

        // Identify target player preference
        val targetPlayer = preferredPlayer?.lowercase() ?: when {
            rawQuery.contains("vlc") || rawQuery.contains("व्हीएलसी") -> "vlc"
            rawQuery.contains("youtube") || rawQuery.contains("यूट्यूब") -> "youtube"
            rawQuery.contains("spotify") || rawQuery.contains("स्पॉटिफाय") -> "spotify"
            rawQuery.contains("local") || rawQuery.contains("स्थानिक") || rawQuery.contains("offline") -> "local"
            else -> "auto"
        }

        val clean = rawQuery
            .replace("on youtube", "", ignoreCase = true)
            .replace("in youtube", "", ignoreCase = true)
            .replace("on vlc", "", ignoreCase = true)
            .replace("in vlc", "", ignoreCase = true)
            .replace("on spotify", "", ignoreCase = true)
            .replace("in spotify", "", ignoreCase = true)
            .replace("youtube", "", ignoreCase = true)
            .replace("यूट्यूब", "", ignoreCase = true)
            .replace("vlc", "", ignoreCase = true)
            .replace("व्हीएलसी", "", ignoreCase = true)
            .replace("spotify", "", ignoreCase = true)
            .replace("and play", "", ignoreCase = true)
            .replace("play", "", ignoreCase = true)
            .replace("song", "", ignoreCase = true)
            .replace("गाणे", "", ignoreCase = true)
            .replace("सॉन्ग", "", ignoreCase = true)
            .replace("लावा", "", ignoreCase = true)
            .replace("वाजवा", "", ignoreCase = true)
            .replace("वर", "", ignoreCase = true)
            .replace("मध्ये", "", ignoreCase = true)
            .replace("local", "", ignoreCase = true)
            .replace("स्थानिक", "", ignoreCase = true)
            .trim()

        // 0. If local player or local file playback requested
        if (targetPlayer == "local" || (targetPlayer == "auto" && clean.isNotBlank())) {
            val localAudio = findLocalAudioUri(clean)
            if (localAudio != null) {
                val (uri, trackTitle) = localAudio
                val played = audioPlayer.playUri(uri, trackTitle)
                if (played) {
                    return Pair(true, "Playing local track \"$trackTitle\" (WakeLock active)")
                }
            }
        }

        // 1. If specific player requested: VLC
        if (targetPlayer == "vlc") {
            // First check if a local song file exists on device storage
            val localAudio = if (clean.isNotBlank()) findLocalAudioUri(clean) else null
            if (localAudio != null) {
                val (uri, trackTitle) = localAudio
                try {
                    val vlcIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "audio/*")
                        setPackage("org.videolan.vlc")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    if (vlcIntent.resolveActivity(pm) != null) {
                        context.startActivity(vlcIntent)
                        return Pair(true, "Auto-playing \"$trackTitle\" in VLC")
                    }
                } catch (e: Exception) {
                    Log.w("HardwareController", "VLC direct file play failed: ${e.message}")
                }
            }

            // Try VLC Media play search
            if (clean.isNotBlank()) {
                try {
                    val vlcSearch = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        setPackage("org.videolan.vlc")
                        putExtra(android.app.SearchManager.QUERY, clean)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (vlcSearch.resolveActivity(pm) != null) {
                        context.startActivity(vlcSearch)
                        return Pair(true, "Playing \"$clean\" in VLC")
                    }
                } catch (e: Exception) {
                    Log.w("HardwareController", "VLC search failed: ${e.message}")
                }
            }

            // Fallback: Launch VLC app directly
            val launched = launchAppByPackage("org.videolan.vlc")
            if (launched) {
                return Pair(true, if (clean.isNotBlank()) "VLC opened for \"$clean\"" else "VLC Player launched")
            }
        }

        // 2. If specific player requested: YouTube / ReVanced (or auto with query)
        if (targetPlayer == "youtube" || (targetPlayer == "auto" && clean.isNotBlank())) {
            val encodedQuery = Uri.encode(clean)
            val ytPackages = listOf(
                "app.revanced.android.youtube",
                "com.vanced.android.youtube",
                "app.revanced.android.apps.youtube.music",
                "com.google.android.youtube",
                "com.google.android.apps.youtube.music",
                "org.schabi.newpipe"
            )

            // Priority 1: DIRECT VIDEO AUTOPLAY (Fetches top matching video ID and launches player directly)
            val directVideoId = resolveYouTubeVideoId(clean)
            if (!directVideoId.isNullOrBlank()) {
                val watchUri = Uri.parse("https://www.youtube.com/watch?v=$directVideoId")
                for (pkg in ytPackages) {
                    try {
                        val ytDirectIntent = Intent(Intent.ACTION_VIEW, watchUri).apply {
                            setPackage(pkg)
                            putExtra("force_fullscreen", false)
                            putExtra("finish_on_ended", false)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        if (ytDirectIntent.resolveActivity(pm) != null) {
                            context.startActivity(ytDirectIntent)
                            val appLabel = if (pkg.contains("revanced")) "YouTube ReVanced" else "YouTube"
                            return Pair(true, "Auto-playing \"$clean\" on $appLabel")
                        }
                    } catch (e: Exception) {
                        Log.w("HardwareController", "Direct video play for $pkg failed: ${e.message}")
                    }
                }

                // If specific package not matched, try generic system handler for direct video
                try {
                    val genericDirectIntent = Intent(Intent.ACTION_VIEW, watchUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (genericDirectIntent.resolveActivity(pm) != null) {
                        context.startActivity(genericDirectIntent)
                        return Pair(true, "Auto-playing \"$clean\"")
                    }
                } catch (e: Exception) {
                    Log.w("HardwareController", "Generic direct video launch failed: ${e.message}")
                }
            }

            // Priority 2: Media Play From Search (Official Voice Assistant Intent)
            for (pkg in ytPackages) {
                try {
                    val ytPlayIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        setPackage(pkg)
                        putExtra(android.app.SearchManager.QUERY, clean)
                        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
                        putExtra(MediaStore.EXTRA_MEDIA_TITLE, clean)
                        putExtra("autostart", true)
                        putExtra("autoplay", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (ytPlayIntent.resolveActivity(pm) != null) {
                        context.startActivity(ytPlayIntent)
                        val appLabel = if (pkg.contains("revanced")) "YouTube ReVanced" else "YouTube"
                        return Pair(true, "Auto-playing \"$clean\" on $appLabel")
                    }
                } catch (e: Exception) {
                    Log.w("HardwareController", "YouTube MEDIA_PLAY_FROM_SEARCH for $pkg failed: ${e.message}")
                }
            }

            // Priority 3: Direct Deep Link Search URI (Fallback if direct video ID resolution failed)
            for (pkg in ytPackages) {
                try {
                    val searchUri = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
                    val ytAppUri = Intent(Intent.ACTION_VIEW, searchUri).apply {
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (ytAppUri.resolveActivity(pm) != null) {
                        context.startActivity(ytAppUri)
                        val appLabel = if (pkg.contains("revanced")) "YouTube ReVanced" else "YouTube"
                        return Pair(true, "Searching & opening \"$clean\" in $appLabel")
                    }
                } catch (e: Exception) {
                    Log.w("HardwareController", "YouTube URI intent for $pkg failed: ${e.message}")
                }
            }

            // Priority 4: Custom vnd.youtube Scheme
            for (pkg in ytPackages) {
                try {
                    val vndUri = Uri.parse("vnd.youtube://search?q=$encodedQuery")
                    val ytVndIntent = Intent(Intent.ACTION_VIEW, vndUri).apply {
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (ytVndIntent.resolveActivity(pm) != null) {
                        context.startActivity(ytVndIntent)
                        val appLabel = if (pkg.contains("revanced")) "YouTube ReVanced" else "YouTube"
                        return Pair(true, "Playing \"$clean\" in $appLabel")
                    }
                } catch (e: Exception) {
                    Log.w("HardwareController", "vnd.youtube intent for $pkg failed: ${e.message}")
                }
            }

            // Priority 4: System-wide Search Intent without setPackage
            try {
                val genericUri = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
                val genericIntent = Intent(Intent.ACTION_VIEW, genericUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (genericIntent.resolveActivity(pm) != null) {
                    context.startActivity(genericIntent)
                    return Pair(true, "Searching \"$clean\" on YouTube")
                }
            } catch (e: Exception) {
                Log.w("HardwareController", "Generic YouTube URI failed: ${e.message}")
            }

            // Priority 4: Local Audio match if YouTube app isn't installed
            val localMatch = findLocalAudioUri(clean)
            if (localMatch != null) {
                val (uri, trackTitle) = localMatch
                try {
                    val localIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "audio/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    if (localIntent.resolveActivity(pm) != null) {
                        context.startActivity(localIntent)
                        return Pair(true, "Auto-playing local song \"$trackTitle\"")
                    }
                } catch (e: Exception) {
                    Log.w("HardwareController", "Local audio auto-play failed: ${e.message}")
                }
            }

            // Priority 5: YouTube Music search intent
            try {
                val ytMusicIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=${Uri.encode(clean)}")).apply {
                    setPackage("com.google.android.apps.youtube.music")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (ytMusicIntent.resolveActivity(pm) != null) {
                    context.startActivity(ytMusicIntent)
                    return Pair(true, "Searching \"$clean\" on YouTube Music")
                }
            } catch (e: Exception) {
                // Ignore
            }

            // Priority 6: General Browser / System search intent
            try {
                val webSearchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(clean)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webSearchIntent)
                return Pair(true, "Playing \"$clean\" via YouTube")
            } catch (e: Exception) {
                Log.e("HardwareController", "Web search failed", e)
            }
        }

        // 3. General "play music" or "play songs" requested without query
        try {
            val musicIntent = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (musicIntent.resolveActivity(pm) != null) {
                context.startActivity(musicIntent)
                return Pair(true, "Music player opened")
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Try installed popular music apps
        val musicApps = listOf(
            "org.videolan.vlc" to "VLC",
            "com.spotify.music" to "Spotify",
            "com.google.android.apps.youtube.music" to "YouTube Music",
            "com.google.android.youtube" to "YouTube",
            "com.amazon.mp3" to "Amazon Music",
            "com.jio.media.jiobeats" to "JioSaavn"
        )
        for ((pkg, name) in musicApps) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return Pair(true, "$name opened")
            }
        }

        // Fallback: Send standard Play media key event to system audio
        try {
            val event = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager?.dispatchMediaKeyEvent(event)
            val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager?.dispatchMediaKeyEvent(upEvent)
            return Pair(true, "Media playback toggled")
        } catch (e: Exception) {
            Log.e("HardwareController", "Failed dispatching media key", e)
        }

        return Pair(false, "No active music player found")
    }

    fun getInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val list = mutableListOf<InstalledAppInfo>()
        try {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                // Filter launchable user/system apps
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    val label = pm.getApplicationLabel(app).toString()
                    list.add(InstalledAppInfo(appName = label, packageName = app.packageName))
                }
            }
        } catch (e: Exception) {
            Log.e("HardwareController", "Error querying installed apps", e)
        }
        return list.sortedBy { it.appName.lowercase() }
    }

    // --- BATTERY INFO ---
    fun getBatteryLevel(): Int {
        return try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
        } catch (e: Exception) {
            85
        }
    }

    fun isCharging(): Boolean {
        return try {
            val status = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    // --- MEDIA CONTROLS (PAUSE, STOP, NEXT, PREVIOUS, RESUME) ---
    fun controlMedia(action: MediaControlAction): Pair<Boolean, String> {
        acquireCpuWakeLock(5000, "Jarvis:MediaControl")
        val keycode = when (action) {
            MediaControlAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaControlAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaControlAction.TOGGLE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaControlAction.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
            MediaControlAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaControlAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }

        // Check if built-in audioPlayer is active
        var internalHandled = false
        when (action) {
            MediaControlAction.PAUSE -> {
                if (audioPlayer.isPlaying.value) {
                    audioPlayer.pause()
                    internalHandled = true
                }
            }
            MediaControlAction.PLAY -> {
                if (audioPlayer.currentTrack.value != null) {
                    audioPlayer.resume()
                    internalHandled = true
                }
            }
            MediaControlAction.STOP -> {
                if (audioPlayer.isPlaying.value || audioPlayer.currentTrack.value != null) {
                    audioPlayer.stop()
                    internalHandled = true
                }
            }
            else -> {}
        }

        return try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keycode)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keycode)
            audioManager?.dispatchMediaKeyEvent(upEvent)

            // For STOP: request transient audio focus to forcibly cut off background playback
            if (action == MediaControlAction.STOP) {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            val desc = when (action) {
                MediaControlAction.PLAY -> "Playback resumed"
                MediaControlAction.PAUSE -> "Playback paused"
                MediaControlAction.TOGGLE -> "Playback toggled"
                MediaControlAction.STOP -> "Playback stopped"
                MediaControlAction.NEXT -> "Skipped to next track"
                MediaControlAction.PREVIOUS -> "Playing previous track"
            }
            Pair(true, desc)
        } catch (e: Exception) {
            Log.e("HardwareController", "Failed to dispatch media key $keycode", e)
            if (internalHandled) Pair(true, "Internal media updated")
            else Pair(false, "Media control failed: ${e.message}")
        }
    }

    // --- CLOSE APP / CLOSE YOUTUBE / GO HOME ---
    fun closeAppOrGoHome(appNameOrPackage: String? = null): Pair<Boolean, String> {
        val target = appNameOrPackage?.trim()?.lowercase()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        // If target app requested (e.g., YouTube, ReVanced, Spotify, etc.)
        if (!target.isNullOrBlank()) {
            val matchedPkgs = when {
                target.contains("youtube") || target.contains("यूट्यूब") || target.contains("revanced") -> listOf(
                    "app.revanced.android.youtube",
                    "com.google.android.youtube",
                    "com.vanced.android.youtube",
                    "app.revanced.android.apps.youtube.music",
                    "com.google.android.apps.youtube.music",
                    "org.schabi.newpipe"
                )
                target.contains("spotify") || target.contains("स्पॉटिफाय") -> listOf("com.spotify.music")
                target.contains("vlc") || target.contains("व्हीएलसी") -> listOf("org.videolan.vlc")
                else -> {
                    val installed = getInstalledApps()
                    installed.filter {
                        it.appName.lowercase().contains(target) || it.packageName.lowercase().contains(target)
                    }.map { it.packageName }
                }
            }

            for (pkg in matchedPkgs) {
                try {
                    activityManager?.killBackgroundProcesses(pkg)
                } catch (e: Exception) {
                    Log.w("HardwareController", "Failed to kill background process $pkg: ${e.message}")
                }
            }
        }

        // Return to Android Home screen (closes/minimizes foreground app)
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            val desc = if (!target.isNullOrBlank()) "Closed $target and returned home" else "Returned to Home screen"
            Pair(true, desc)
        } catch (e: Exception) {
            Log.e("HardwareController", "Failed to go home", e)
            Pair(false, "Failed to navigate home: ${e.message}")
        }
    }
}
