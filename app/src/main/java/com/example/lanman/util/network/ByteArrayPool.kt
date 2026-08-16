package com.example.lanman.util.network

import kotlin.collections.ArrayDeque

/**
 * High-performance object pool for byte arrays to eliminate GC pressure during
 * high-throughput file transfers and streaming encryption over the local network.
 */
object ByteArrayPool {
    const val CHUNK_BUFFER_SIZE = 512 * 1024 // 512 KB
    const val FRAME_BUFFER_SIZE = 64 * 1024  // 64 KB

    private const val MAX_CHUNK_POOL_SIZE = 8
    private const val MAX_FRAME_POOL_SIZE = 16

    private val chunkPool = ArrayDeque<ByteArray>()
    private val framePool = ArrayDeque<ByteArray>()

    @Synchronized
    fun acquireChunkBuffer(): ByteArray {
        return chunkPool.removeFirstOrNull() ?: ByteArray(CHUNK_BUFFER_SIZE)
    }

    @Synchronized
    fun releaseChunkBuffer(buffer: ByteArray) {
        if (buffer.size == CHUNK_BUFFER_SIZE && chunkPool.size < MAX_CHUNK_POOL_SIZE) {
            chunkPool.addLast(buffer)
        }
    }

    @Synchronized
    fun acquireFrameBuffer(): ByteArray {
        return framePool.removeFirstOrNull() ?: ByteArray(FRAME_BUFFER_SIZE)
    }

    @Synchronized
    fun releaseFrameBuffer(buffer: ByteArray) {
        if (buffer.size == FRAME_BUFFER_SIZE && framePool.size < MAX_FRAME_POOL_SIZE) {
            framePool.addLast(buffer)
        }
    }

    @Synchronized
    fun clear() {
        chunkPool.clear()
        framePool.clear()
    }
}
