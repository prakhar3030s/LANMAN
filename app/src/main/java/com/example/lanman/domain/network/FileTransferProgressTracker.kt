package com.example.lanman.domain.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class TransferMetrics(
    val progress: Float,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long
)

@Singleton
class FileTransferProgressTracker @Inject constructor() {
    private val _progressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progressMap: StateFlow<Map<String, Float>> = _progressMap.asStateFlow()

    private val _metricsMap = MutableStateFlow<Map<String, TransferMetrics>>(emptyMap())
    val metricsMap: StateFlow<Map<String, TransferMetrics>> = _metricsMap.asStateFlow()

    private val lastUpdateTimes = ConcurrentHashMap<String, Long>()
    private val lastPercentages = ConcurrentHashMap<String, Float>()
    private val startTimes = ConcurrentHashMap<String, Long>()

    fun updateProgress(fileTransferId: String, bytesTransferred: Long, totalBytes: Long) {
        val percentage = if (totalBytes > 0) {
            (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val now = System.currentTimeMillis()
        val startTime = startTimes.putIfAbsent(fileTransferId, now) ?: now
        val lastTime = lastUpdateTimes[fileTransferId] ?: 0L
        val lastPct = lastPercentages[fileTransferId] ?: -1f

        // Throttle emissions to max once every 100ms or on >= 1% progress delta or completion
        if (percentage >= 1.0f || abs(percentage - lastPct) >= 0.01f || now - lastTime >= 100) {
            lastUpdateTimes[fileTransferId] = now
            lastPercentages[fileTransferId] = percentage
            val elapsedMs = (now - startTime).coerceAtLeast(1L)
            val speedBps = if (elapsedMs >= 250L) (bytesTransferred * 1000L) / elapsedMs else 0L
            val metrics = TransferMetrics(
                progress = percentage,
                bytesTransferred = bytesTransferred,
                totalBytes = totalBytes,
                bytesPerSecond = speedBps
            )
            _progressMap.update { it + (fileTransferId to percentage) }
            _metricsMap.update { it + (fileTransferId to metrics) }
        }
    }

    fun clearProgress(fileTransferId: String) {
        lastUpdateTimes.remove(fileTransferId)
        lastPercentages.remove(fileTransferId)
        startTimes.remove(fileTransferId)
        _progressMap.update { it - fileTransferId }
        _metricsMap.update { it - fileTransferId }
    }

    fun clearAll() {
        lastUpdateTimes.clear()
        lastPercentages.clear()
        startTimes.clear()
        _progressMap.value = emptyMap()
        _metricsMap.value = emptyMap()
    }
}
