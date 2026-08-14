package com.example.lanman.util.network

object NetworkConstants {
    const val UDP_DISCOVERY_PORT = 47474
    const val TCP_MESSAGE_PORT = 47475
    const val HEARTBEAT_INTERVAL_MS = 3000L
    const val PRESENCE_TIMEOUT_MS = 9000L
    val MAGIC_HEADER = byteArrayOf(0x4C, 0x4C, 0x4E, 0x4B) // "LLNK"
    const val PROTOCOL_VERSION: Byte = 1
}
