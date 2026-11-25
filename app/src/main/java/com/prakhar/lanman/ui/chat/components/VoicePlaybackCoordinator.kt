package com.prakhar.lanman.ui.chat.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * Coordinates voice-note playback across message bubbles so only one note plays at a time
 * and Android audio focus is properly acquired and released.
 */
object VoicePlaybackCoordinator {
    private const val TAG = "VoicePlayback"

    private var activePath: String? = null
    private var activePlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var onExternalPause: (() -> Unit)? = null

    fun play(
        context: Context,
        audioFile: File,
        seekToMs: Int = 0,
        onPreparedDuration: (Int) -> Unit,
        onCompletion: () -> Unit,
        onPauseCallback: () -> Unit,
        onError: (String) -> Unit
    ): MediaPlayer? {
        if (!audioFile.exists() || audioFile.length() <= 0L) {
            onError("Audio file is unavailable")
            return null
        }

        val path = audioFile.absolutePath
        if (activePath != path) {
            stopCurrent()
        } else {
            val existing = activePlayer
            if (existing != null) {
                requestFocus(context)
                if (seekToMs > 0) {
                    runCatching { existing.seekTo(seekToMs) }
                }
                runCatching { existing.start() }.onFailure {
                    stopCurrent()
                    onError("Unable to resume playback")
                }
                onPauseCallback.let { this.onExternalPause = it }
                return existing
            }
        }

        requestFocus(context)
        val player = MediaPlayer()
        activePath = path
        activePlayer = player
        onExternalPause = onPauseCallback

        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(path)
            player.setOnPreparedListener { mp ->
                if (activePlayer !== mp) {
                    runCatching { mp.release() }
                    return@setOnPreparedListener
                }
                onPreparedDuration(mp.duration.coerceAtLeast(0))
                if (seekToMs in 1 until mp.duration) {
                    runCatching { mp.seekTo(seekToMs) }
                }
                runCatching { mp.start() }.onFailure { e ->
                    Log.e(TAG, "Failed to start MediaPlayer", e)
                    stopCurrent()
                    onError("Unable to play audio")
                }
            }
            player.setOnCompletionListener {
                abandonFocus()
                onCompletion()
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                stopCurrent()
                onError("Audio playback error")
                true
            }
            player.prepareAsync()
            return player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPlayer", e)
            stopCurrent()
            onError("Audio file could not be opened")
            return null
        }
    }

    fun pause(path: String) {
        if (activePath == path) {
            runCatching {
                if (activePlayer?.isPlaying == true) {
                    activePlayer?.pause()
                }
            }
            abandonFocus()
        }
    }

    fun seekTo(path: String, positionMs: Int) {
        if (activePath == path) {
            runCatching { activePlayer?.seekTo(positionMs) }
        }
    }

    fun releaseIfOwner(path: String) {
        if (activePath == path) {
            stopCurrent()
        }
    }

    fun stopCurrent() {
        val prevPause = onExternalPause
        onExternalPause = null
        activePath = null
        activePlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.release() }
        }
        activePlayer = null
        abandonFocus()
        prevPause?.invoke()
    }

    private fun requestFocus(context: Context) {
        val am = audioManager ?: (context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.also { audioManager = it } ?: return
        if (focusRequest == null) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    ) {
                        activePlayer?.let { mp ->
                            runCatching { if (mp.isPlaying) mp.pause() }
                        }
                        onExternalPause?.invoke()
                    }
                }
                .build()
        }
        focusRequest?.let { am.requestAudioFocus(it) }
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
    }
}
