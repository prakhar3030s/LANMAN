package com.example.lanman.util.network

object NetworkConstants {
    const val UDP_DISCOVERY_PORT = 47474
    const val TCP_MESSAGE_PORT = 47475 // Control Channel: Instant messaging, ACKs, handshakes
    const val TCP_DATA_PORT = 47476    // Data Channel: High-throughput file and media streaming

    const val HEARTBEAT_INTERVAL_MS = 3000L
    const val PRESENCE_TIMEOUT_MS = 9000L
    const val FAST_DISCOVERY_BURST_COUNT = 3
    const val FAST_DISCOVERY_BURST_DELAY_MS = 200L

    const val CONTROL_SOCKET_BUFFER_SIZE = 64 * 1024       // 64 KB for low latency control
    const val DATA_SOCKET_BUFFER_SIZE = 1024 * 1024        // 1 MB for gigabit file transfers
    const val FILE_STREAM_BUFFER_SIZE = 256 * 1024         // 256 KB for disk I/O buffers

    val MAGIC_HEADER = byteArrayOf(0x4C, 0x4C, 0x4E, 0x4B) // "LLNK"
    const val PROTOCOL_VERSION: Byte = 1
}

