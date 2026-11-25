package com.example.lanman.ui.chat.components

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.lanman.ui.common.AppIcons
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.util.Locale

@Composable
fun VoicePlayer(
    audioFile: File,
    durationMs: Long,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember(audioFile.absolutePath) { mutableStateOf(false) }
    var currentPosition by remember(audioFile.absolutePath) { mutableIntStateOf(0) }
    var totalDurationMs by remember(audioFile.absolutePath, durationMs) {
        mutableIntStateOf(durationMs.toInt().coerceAtLeast(0))
    }
    var mediaPlayer by remember(audioFile.absolutePath) { mutableStateOf<MediaPlayer?>(null) }
    var errorText by remember(audioFile.absolutePath) { mutableStateOf<String?>(null) }

    val fileAvailable = remember(audioFile.absolutePath, audioFile.length()) {
        audioFile.exists() && audioFile.length() > 0L
    }

    DisposableEffect(audioFile.absolutePath) {
        onDispose {
            VoicePlaybackCoordinator.releaseIfOwner(audioFile.absolutePath)
            mediaPlayer = null
        }
    }

    DisposableEffect(lifecycleOwner, audioFile.absolutePath) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && isPlaying) {
                VoicePlaybackCoordinator.pause(audioFile.absolutePath)
                isPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isPlaying, mediaPlayer) {
        if (isPlaying) {
            while (isActive && isPlaying) {
                val pos = runCatching { mediaPlayer?.currentPosition }.getOrNull()
                if (pos != null && pos >= 0) {
                    currentPosition = pos
                }
                delay(80L)
            }
        }
    }

    val effectiveDuration = totalDurationMs.coerceAtLeast(1)
    val progress = (currentPosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)

    if (!fileAvailable || errorText != null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Audio unavailable",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = errorText ?: "Audio file removed",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    VoicePlaybackCoordinator.pause(audioFile.absolutePath)
                    isPlaying = false
                } else {
                    errorText = null
                    isPlaying = true
                    mediaPlayer = VoicePlaybackCoordinator.play(
                        context = context,
                        audioFile = audioFile,
                        seekToMs = currentPosition,
                        onPreparedDuration = { dur ->
                            if (dur > 0) totalDurationMs = dur
                        },
                        onCompletion = {
                            isPlaying = false
                            currentPosition = 0
                        },
                        onPauseCallback = {
                            isPlaying = false
                        },
                        onError = { msg ->
                            isPlaying = false
                            mediaPlayer = null
                            errorText = msg
                        }
                    )
                    if (mediaPlayer == null) {
                        isPlaying = false
                    }
                }
            },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ElectricCyan.copy(alpha = 0.2f))
        ) {
            Icon(
                imageVector = if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause voice message" else "Play voice message",
                tint = ElectricCyan,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = progress,
                onValueChange = { fraction ->
                    val targetMs = (fraction * effectiveDuration).toInt()
                    currentPosition = targetMs
                    VoicePlaybackCoordinator.seekTo(audioFile.absolutePath, targetMs)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = textColor.copy(alpha = 0.2f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val shownSeconds = if (isPlaying || currentPosition > 0) {
                    currentPosition / 1000
                } else {
                    totalDurationMs / 1000
                }
                Text(
                    text = formatAudioTime(shownSeconds),
                    color = textColor.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall
                )
                if (isPlaying || currentPosition > 0) {
                    Text(
                        text = formatAudioTime(totalDurationMs / 1000),
                        color = textColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private fun formatAudioTime(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
}
