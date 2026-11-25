package com.prakhar.lanman.ui.chat.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.prakhar.lanman.ui.common.AppIcons
import com.prakhar.lanman.ui.theme.ElectricCyan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun VoiceRecordButton(
    onRecordComplete: (File, Long) -> Unit,
    onRecordingHint: (String) -> Unit = {},
    onPermissionDenied: (permanentlyDenied: Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val cancelDragThresholdPx = remember(density) { with(density) { 88.dp.toPx() } }

    var isRecording by remember { mutableStateOf(false) }
    var recordingStartMs by remember { mutableLongStateOf(0L) }
    var recordingDurationMs by remember { mutableLongStateOf(0L) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
            if (!granted) {
                val activity = context.findActivity()
                val permanentlyDenied = activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
                onPermissionDenied(permanentlyDenied)
            } else {
                onRecordingHint("Hold the mic button to record a voice message")
            }
        }
    )

    var mediaRecorder: MediaRecorder? by remember { mutableStateOf(null) }
    var outputFile: File? by remember { mutableStateOf(null) }
    var focusRequest: AudioFocusRequest? by remember { mutableStateOf(null) }
    val recordingsDir = remember { File(context.cacheDir, "voice_recordings").apply { mkdirs() } }

    fun abandonFocus() {
        val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        focusRequest?.let { am?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    fun requestRecordingFocus() {
        val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        focusRequest = req
        am.requestAudioFocus(req)
    }

    fun stopRecording(cancel: Boolean) {
        if (!isRecording && mediaRecorder == null) return
        val elapsedMs = if (recordingStartMs > 0L) {
            (SystemClock.elapsedRealtime() - recordingStartMs).coerceAtLeast(0L)
        } else {
            recordingDurationMs
        }
        val recorder = mediaRecorder
        val file = outputFile

        mediaRecorder = null
        outputFile = null
        isRecording = false
        recordingStartMs = 0L
        dragOffset = 0f

        var stopSucceeded = false
        if (recorder != null) {
            try {
                recorder.stop()
                stopSucceeded = true
            } catch (e: RuntimeException) {
                // MediaRecorder.stop() throws RuntimeException on ultra-short (< ~300ms) taps.
                Log.w("VoiceRecordButton", "Short/interrupted recording stopped: ${e.message}")
            } catch (e: Exception) {
                Log.e("VoiceRecordButton", "Failed to stop recording cleanly", e)
            } finally {
                runCatching { recorder.release() }
            }
        }
        abandonFocus()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

        if (!cancel && stopSucceeded && file != null && file.exists() && file.length() > 0L && elapsedMs >= MIN_VOICE_DURATION_MS) {
            onRecordComplete(file, elapsedMs)
        } else {
            runCatching { file?.delete() }
            if (!cancel && elapsedMs < MIN_VOICE_DURATION_MS) {
                onRecordingHint("Hold to record a voice message")
            }
        }
        recordingDurationMs = 0L
    }

    fun startRecording(): Boolean {
        // Pause any voice playback before opening the microphone.
        VoicePlaybackCoordinator.stopCurrent()

        val targetFile = File(recordingsDir, "voice_recording_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            requestRecordingFocus()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(44_100)
            recorder.setAudioEncodingBitRate(96_000)
            recorder.setMaxDuration(MAX_VOICE_DURATION_MS)
            recorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopRecording(cancel = false)
                }
            }
            recorder.setOutputFile(targetFile.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            outputFile = targetFile
            recordingStartMs = SystemClock.elapsedRealtime()
            recordingDurationMs = 0L
            dragOffset = 0f
            isRecording = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            true
        } catch (e: Exception) {
            Log.e("VoiceRecordButton", "Failed to start recording", e)
            runCatching { recorder.release() }
            runCatching { targetFile.delete() }
            abandonFocus()
            mediaRecorder = null
            outputFile = null
            isRecording = false
            onRecordingHint("Could not start audio recording")
            false
        }
    }

    // Re-check permission after returning from Settings, and safely stop if app backgrounds mid-recording.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    if (isRecording) {
                        stopRecording(cancel = true)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (isRecording || mediaRecorder != null) {
                stopRecording(cancel = true)
            }
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isActive && isRecording) {
                recordingDurationMs = (SystemClock.elapsedRealtime() - recordingStartMs).coerceAtLeast(0L)
                delay(100L)
            }
        }
    }

    // Only animate pulse while actively recording so idle ConversationScreen burns zero frames.
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            pulseScale.animateTo(
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            pulseScale.snapTo(1f)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = isRecording) {
            Row(
                modifier = Modifier.padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val seconds = (recordingDurationMs / 1000).toInt()
                Text(
                    text = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "\u2190 Slide to cancel",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .scale(pulseScale.value)
                .clip(CircleShape)
                .background(
                    if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                    else ElectricCyan.copy(alpha = 0.2f)
                )
                .semantics {
                    contentDescription = if (isRecording) {
                        "Recording, release to send, drag left to cancel"
                    } else {
                        "Hold to record a voice message"
                    }
                }
                .pointerInput(hasPermission, cancelDragThresholdPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@awaitEachGesture
                        }

                        if (!startRecording()) return@awaitEachGesture

                        var cancelled = false
                        var previousX = down.position.x
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) {
                                    break
                                }
                                val deltaX = change.position.x - previousX
                                previousX = change.position.x
                                dragOffset = (dragOffset + deltaX).coerceIn(-cancelDragThresholdPx * 1.4f, 0f)
                                change.consume()
                                if (dragOffset <= -cancelDragThresholdPx) {
                                    cancelled = true
                                    break
                                }
                            }
                        } catch (_: CancellationException) {
                            cancelled = true
                        } finally {
                            stopRecording(cancel = cancelled)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Mic,
                contentDescription = null,
                tint = if (isRecording) MaterialTheme.colorScheme.error else ElectricCyan,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private const val MIN_VOICE_DURATION_MS = 800L
private const val MAX_VOICE_DURATION_MS = 5 * 60 * 1000
