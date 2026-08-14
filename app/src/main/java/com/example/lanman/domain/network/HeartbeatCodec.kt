package com.example.lanman.domain.network

import com.example.lanman.util.network.NetworkConstants
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class HeartbeatPacket(
    val id: String,
    val tcpPort: Int
)

object HeartbeatCodec {
    fun encode(ownId: String): ByteArray {
        val idBytes = ownId.toByteArray(StandardCharsets.UTF_8)
        val capacity = NetworkConstants.MAGIC_HEADER.size + 1 + 1 + idBytes.size + 2 // Magic(4) + Version(1) + IdLength(1) + IdBytes + Port(2)
        
        val buffer = ByteBuffer.allocate(capacity)
        buffer.put(NetworkConstants.MAGIC_HEADER)
        buffer.put(NetworkConstants.PROTOCOL_VERSION)
        buffer.put(idBytes.size.toByte())
        buffer.put(idBytes)
        buffer.putShort(NetworkConstants.TCP_MESSAGE_PORT.toShort())
        
        return buffer.array()
    }

    fun decode(bytes: ByteArray, length: Int): HeartbeatPacket? {
        try {
            if (length < NetworkConstants.MAGIC_HEADER.size + 1 + 1 + 2) return null
            
            val buffer = ByteBuffer.wrap(bytes, 0, length)
            
            val magic = ByteArray(NetworkConstants.MAGIC_HEADER.size)
            buffer.get(magic)
            if (!magic.contentEquals(NetworkConstants.MAGIC_HEADER)) return null
            
            val version = buffer.get()
            if (version != NetworkConstants.PROTOCOL_VERSION) return null
            
            val idLength = buffer.get().toInt() and 0xFF
            if (buffer.remaining() < idLength + 2) return null // Need at least idBytes and 2 bytes for port
            
            val idBytes = ByteArray(idLength)
            buffer.get(idBytes)
            val id = String(idBytes, StandardCharsets.UTF_8)
            
            val tcpPort = buffer.short.toInt() and 0xFFFF
            
            return HeartbeatPacket(id, tcpPort)
        } catch (e: Exception) {
            return null
        }
    }
}
