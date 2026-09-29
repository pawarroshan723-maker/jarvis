package com.example.hardware

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisAudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val _currentTrack = MutableStateFlow<String?>(null)
    val currentTrack: StateFlow<String?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun playUri(uri: Uri, title: String): Boolean {
        return try {
            stop()
            mediaPlayer = MediaPlayer().apply {
                // Ensure audio continues playing in background even if screen locks
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, uri)
                prepare()
                start()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentTrack.value = null
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("JarvisAudioPlayer", "MediaPlayer error: what=$what, extra=$extra")
                    _isPlaying.value = false
                    _currentTrack.value = null
                    true
                }
            }
            _currentTrack.value = title
            _isPlaying.value = true
            Log.i("JarvisAudioPlayer", "Started local playback of: $title")
            true
        } catch (e: Exception) {
            Log.e("JarvisAudioPlayer", "Failed to play URI $uri", e)
            _isPlaying.value = false
            _currentTrack.value = null
            false
        }
    }

    fun resume(): Boolean {
        return try {
            if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
                mediaPlayer?.start()
                _isPlaying.value = true
                true
            } else false
        } catch (e: Exception) {
            Log.e("JarvisAudioPlayer", "Error resuming playback", e)
            false
        }
    }

    fun pause(): Boolean {
        return try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _isPlaying.value = false
                true
            } else false
        } catch (e: Exception) {
            Log.e("JarvisAudioPlayer", "Error pausing playback", e)
            false
        }
    }

    fun stop(): Boolean {
        return try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            _isPlaying.value = false
            _currentTrack.value = null
            true
        } catch (e: Exception) {
            Log.e("JarvisAudioPlayer", "Error stopping playback", e)
            mediaPlayer = null
            _isPlaying.value = false
            _currentTrack.value = null
            false
        }
    }

    private var isDucked = false

    fun duckVolume(duck: Boolean) {
        try {
            if (isDucked == duck) return
            isDucked = duck
            val vol = if (duck) 0.15f else 1.0f
            mediaPlayer?.setVolume(vol, vol)
            Log.d("JarvisAudioPlayer", "AudioPlayer duck volume set to: $vol (ducked=$duck)")
        } catch (e: Exception) {
            Log.w("JarvisAudioPlayer", "Error adjusting duck volume: ${e.message}")
        }
    }

    fun release() {
        stop()
    }
}
