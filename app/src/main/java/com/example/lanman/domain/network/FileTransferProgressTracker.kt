package com.example.lanman.domain.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class FileTransferProgressTracker @Inject constructor() {
    private val _progressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progressMap: StateFlow<Map<String, Float>> = _progressMap.asStateFlow()

    private val lastUpdateTimes = ConcurrentHashMap<String, Long>()
    private val lastPercentages = ConcurrentHashMap<String, Float>()

    fun updateProgress(fileTransferId: String, bytesTransferred: Long, totalBytes: Long) {
        val percentage = if (totalBytes > 0) {
            bytesTransferred.toFloat() / totalBytes.toFloat()
        } else 0f

        val now = System.currentTimeMillis()
        val lastTime = lastUpdateTimes[fileTransferId] ?: 0L
        val lastPct = lastPercentages[fileTransferId] ?: -1f

        // Throttle emissions to max once every 50ms or on >= 1% progress delta or 100% completion
        if (percentage >= 1.0f || abs(percentage - lastPct) >= 0.01f || now - lastTime >= 50) {
            lastUpdateTimes[fileTransferId] = now
            lastPercentages[fileTransferId] = percentage
            _progressMap.update { it + (fileTransferId to percentage) }
        }
    }

    fun clearProgress(fileTransferId: String) {
        lastUpdateTimes.remove(fileTransferId)
        lastPercentages.remove(fileTransferId)
        _progressMap.update { it - fileTransferId }
    }
}
