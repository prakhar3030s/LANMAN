package com.example.lanman.ui.chat.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.lanman.ui.theme.ElectricCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun VoiceRecordButton(
    onRecordComplete: (File, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationMs by remember { mutableLongStateOf(0L) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var hasPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) 
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
        }
    )

    var mediaRecorder: MediaRecorder? by remember { mutableStateOf(null) }
    var outputFile: File? by remember { mutableStateOf(null) }

    fun startRecording() {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        outputFile = File(context.cacheDir, "voice_recording_${System.currentTimeMillis()}.m4a")
        
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile!!.absolutePath)
            
            try {
                prepare()
                start()
                isRecording = true
                recordingDurationMs = 0L
                dragOffset = 0f
            } catch (e: Exception) {
                Log.e("VoiceRecordButton", "Failed to start recording", e)
            }
        }
    }

    fun stopRecording(cancel: Boolean) {
        if (!isRecording) return
        
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("VoiceRecordButton", "Failed to stop recording cleanly", e)
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            
            if (cancel) {
                outputFile?.delete()
            } else if (outputFile != null && recordingDurationMs >= 1000) {
                // Only send if >= 1 second
                onRecordComplete(outputFile!!, recordingDurationMs)
            } else {
                // Delete if too short
                outputFile?.delete()
            }
            outputFile = null
            dragOffset = 0f
        }
    }

    // Timer coroutine
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                delay(100)
                recordingDurationMs += 100
            }
        }
    }

    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Slide to cancel UI
        AnimatedVisibility(visible = isRecording) {
            Row(
                modifier = Modifier.padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val seconds = recordingDurationMs / 1000
                Text(
                    text = String.format("%02d:%02d", seconds / 60, seconds % 60),
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "< Slide to cancel",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .scale(scale)
                .clip(CircleShape)
                .background(if (isRecording) Color.Red.copy(alpha = 0.2f) else ElectricCyan.copy(alpha = 0.2f))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (dragOffset < -150f) {
                                stopRecording(cancel = true)
                            } else {
                                stopRecording(cancel = false)
                            }
                        },
                        onDragCancel = { stopRecording(cancel = true) }
                    ) { change, dragAmount ->
                        if (isRecording) {
                            change.consume()
                            dragOffset = (dragOffset + dragAmount.x).coerceAtMost(0f)
                            if (dragOffset < -250f) {
                                stopRecording(cancel = true)
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            startRecording()
                            tryAwaitRelease()
                            // If we weren't cancelled via drag
                            if (isRecording) {
                                stopRecording(cancel = false)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎙",
                color = if (isRecording) Color.Red else ElectricCyan,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
