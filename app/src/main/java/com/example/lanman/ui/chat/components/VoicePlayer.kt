package com.example.lanman.ui.chat.components

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lanman.ui.theme.ElectricCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

@Composable
fun VoicePlayer(
    audioFile: File,
    durationMs: Long,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    
    // Cleanup media player when composable leaves composition
    DisposableEffect(audioFile.absolutePath) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive && mediaPlayer != null) {
                currentPosition = mediaPlayer?.currentPosition ?: 0
                delay(50)
            }
        }
    }
    
    val totalDuration = if (durationMs > 0) durationMs.toInt() else (mediaPlayer?.duration?.takeIf { it > 0 } ?: 1)
    val progress = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f) else 0f
    val seconds = totalDuration / 1000

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    mediaPlayer?.pause()
                    isPlaying = false
                } else {
                    if (mediaPlayer == null) {
                        try {
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(audioFile.absolutePath)
                                prepare()
                                setOnCompletionListener {
                                    isPlaying = false
                                    currentPosition = 0
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("VoicePlayer", "Failed to init MediaPlayer", e)
                            return@IconButton
                        }
                    }
                    mediaPlayer?.start()
                    isPlaying = true
                }
            },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ElectricCyan.copy(alpha = 0.2f))
        ) {
            Text(
                text = if (isPlaying) "⏸" else "▶",
                color = ElectricCyan,
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(CircleShape),
            color = ElectricCyan,
            trackColor = textColor.copy(alpha = 0.2f)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Duration
        Text(
            text = String.format("%02d:%02d", seconds / 60, seconds % 60),
            color = textColor,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
