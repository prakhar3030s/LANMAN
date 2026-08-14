package com.example.lanman.domain.network.protocol

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object FrameCodec {
    fun encode(frame: Frame): ByteArray {
        if (frame is Frame.FileChunkFrame) {
            val transferIdBytes = frame.fileTransferId.toByteArray(StandardCharsets.UTF_8)
            val payloadCapacity = 1 + transferIdBytes.size + 4 + 4 + frame.data.size
            val frameLength = 1 + payloadCapacity
            val buffer = ByteBuffer.allocate(4 + frameLength)
            buffer.putInt(frameLength)
            buffer.put(frame.type.typeByte)
            buffer.put(transferIdBytes.size.toByte())
            buffer.put(transferIdBytes)
            buffer.putInt(frame.chunkIndex)
            buffer.putInt(frame.data.size)
            buffer.put(frame.data)
            return buffer.array()
        }

        val payload = when (frame) {
            is Frame.HelloFrame -> {
                val contactIdBytes = frame.contactId.toByteArray(StandardCharsets.UTF_8)
                val displayNameBytes = frame.displayName.toByteArray(StandardCharsets.UTF_8)
                val identityKeyBytes = frame.identityKey.toByteArray(StandardCharsets.UTF_8)
                val capacity = 1 + contactIdBytes.size + 1 + displayNameBytes.size + 2 + identityKeyBytes.size
                val buffer = ByteBuffer.allocate(capacity)
                buffer.put(contactIdBytes.size.toByte())
                buffer.put(contactIdBytes)
                buffer.put(displayNameBytes.size.toByte())
                buffer.put(displayNameBytes)
                buffer.putShort(identityKeyBytes.size.toShort())
                buffer.put(identityKeyBytes)
                buffer.array()
            }
            Frame.PingFrame -> ByteArray(0)
            Frame.PongFrame -> ByteArray(0)
            is Frame.MessageFrame -> {
                val msgIdBytes = frame.messageId.toByteArray(StandardCharsets.UTF_8)
                val senderIdBytes = frame.senderId.toByteArray(StandardCharsets.UTF_8)
                
                // Format: [msgIdLength(1)][msgId][senderIdLength(1)][senderId][messageType(1)][timestamp(8)][contentLength(4)][content]
                val capacity = 1 + msgIdBytes.size + 1 + senderIdBytes.size + 1 + 8 + 4 + frame.content.size
                val buffer = ByteBuffer.allocate(capacity)
                buffer.put(msgIdBytes.size.toByte())
                buffer.put(msgIdBytes)
                buffer.put(senderIdBytes.size.toByte())
                buffer.put(senderIdBytes)
                buffer.put(frame.messageType)
                buffer.putLong(frame.timestamp)
                buffer.putInt(frame.content.size)
                buffer.put(frame.content)
                buffer.array()
            }
            is Frame.AckFrame -> frame.messageId.toByteArray(StandardCharsets.UTF_8)
            is Frame.ReadFrame -> frame.messageId.toByteArray(StandardCharsets.UTF_8)
            is Frame.FileOfferFrame -> {
                val msgIdBytes = frame.messageId.toByteArray(StandardCharsets.UTF_8)
                val transferIdBytes = frame.fileTransferId.toByteArray(StandardCharsets.UTF_8)
                val fileNameBytes = frame.fileName.toByteArray(StandardCharsets.UTF_8)
                val mimeTypeBytes = frame.mimeType.toByteArray(StandardCharsets.UTF_8)
                
                val capacity = 1 + msgIdBytes.size + 1 + transferIdBytes.size + 2 + fileNameBytes.size + 
                               8 + 2 + mimeTypeBytes.size + 4 + 4 + 4 + frame.encryptedKeyEnvelope.size + 8
                val buffer = ByteBuffer.allocate(capacity)
                buffer.put(msgIdBytes.size.toByte())
                buffer.put(msgIdBytes)
                buffer.put(transferIdBytes.size.toByte())
                buffer.put(transferIdBytes)
                buffer.putShort(fileNameBytes.size.toShort())
                buffer.put(fileNameBytes)
                buffer.putLong(frame.fileSizeBytes)
                buffer.putShort(mimeTypeBytes.size.toShort())
                buffer.put(mimeTypeBytes)
                buffer.putInt(frame.chunkSize)
                buffer.putInt(frame.totalChunks)
                buffer.putInt(frame.encryptedKeyEnvelope.size)
                buffer.put(frame.encryptedKeyEnvelope)
                buffer.putLong(frame.senderTimestamp)
                buffer.array()
            }
            is Frame.FileChunkFrame -> throw IllegalStateException("FileChunkFrame is handled early")
            is Frame.FileCompleteFrame -> frame.fileTransferId.toByteArray(StandardCharsets.UTF_8)
            is Frame.KeyBundleRequestFrame -> frame.contactId.toByteArray(StandardCharsets.UTF_8)
            is Frame.KeyBundleResponseFrame -> frame.bundle
            is Frame.UnknownFrame -> ByteArray(0) // We never send this
        }
        
        // Format: [4-byte length][1-byte type][payload bytes]
        // The length field includes the type byte and the payload.
        val frameLength = 1 + payload.size
        
        val buffer = ByteBuffer.allocate(4 + frameLength)
        buffer.putInt(frameLength)
        buffer.put(frame.type.typeByte)
        buffer.put(payload)
        
        return buffer.array()
    }

    fun decode(inputStream: InputStream): Frame? {
        try {
            val dataIn = DataInputStream(inputStream)
            
            // Read 4-byte length
            val length = dataIn.readInt()
            if (length < 1) return null // Must at least contain a type byte
            
            // Enforce max frame size (10MB) to prevent memory exhaustion DoS
            if (length > 10 * 1024 * 1024) {
                return null
            }
            
            // Read 1-byte type
            val typeByte = dataIn.readByte()
            val frameType = FrameType.fromByte(typeByte)
            
            // Read payload
            val payloadLength = length - 1
            val payload = ByteArray(payloadLength)
            if (payloadLength > 0) {
                dataIn.readFully(payload)
            }
            
            return when (frameType) {
                FrameType.HELLO -> {
                    val buffer = ByteBuffer.wrap(payload)
                    val contactIdLen = buffer.get().toInt() and 0xFF
                    val contactIdBytes = ByteArray(contactIdLen)
                    buffer.get(contactIdBytes)
                    val contactId = String(contactIdBytes, StandardCharsets.UTF_8)

                    val displayNameLen = buffer.get().toInt() and 0xFF
                    val displayNameBytes = ByteArray(displayNameLen)
                    buffer.get(displayNameBytes)
                    val displayName = String(displayNameBytes, StandardCharsets.UTF_8)

                    val identityKeyLen = buffer.short.toInt() and 0xFFFF
                    val identityKeyBytes = ByteArray(identityKeyLen)
                    buffer.get(identityKeyBytes)
                    val identityKey = String(identityKeyBytes, StandardCharsets.UTF_8)

                    Frame.HelloFrame(contactId, displayName, identityKey)
                }
                FrameType.PING -> Frame.PingFrame
                FrameType.PONG -> Frame.PongFrame
                FrameType.MESSAGE -> {
                    val buffer = ByteBuffer.wrap(payload)
                    val msgIdLen = buffer.get().toInt() and 0xFF
                    val msgIdBytes = ByteArray(msgIdLen)
                    buffer.get(msgIdBytes)
                    val msgId = String(msgIdBytes, StandardCharsets.UTF_8)
                    
                    val senderIdLen = buffer.get().toInt() and 0xFF
                    val senderIdBytes = ByteArray(senderIdLen)
                    buffer.get(senderIdBytes)
                    val senderId = String(senderIdBytes, StandardCharsets.UTF_8)
                    
                    val msgType = buffer.get()
                    val timestamp = buffer.long
                    
                    val contentLen = buffer.int
                    val content = ByteArray(contentLen)
                    buffer.get(content)
                    
                    Frame.MessageFrame(msgId, senderId, msgType, timestamp, content)
                }
                FrameType.ACK -> {
                    val msgId = String(payload, StandardCharsets.UTF_8)
                    Frame.AckFrame(msgId)
                }
                FrameType.READ -> {
                    val msgId = String(payload, StandardCharsets.UTF_8)
                    Frame.ReadFrame(msgId)
                }
                FrameType.FILE_OFFER -> {
                    val buffer = ByteBuffer.wrap(payload)
                    
                    val msgIdLen = buffer.get().toInt() and 0xFF
                    val msgIdBytes = ByteArray(msgIdLen)
                    buffer.get(msgIdBytes)
                    val msgId = String(msgIdBytes, StandardCharsets.UTF_8)
                    
                    val transferIdLen = buffer.get().toInt() and 0xFF
                    val transferIdBytes = ByteArray(transferIdLen)
                    buffer.get(transferIdBytes)
                    val transferId = String(transferIdBytes, StandardCharsets.UTF_8)
                    
                    val fileNameLen = buffer.short.toInt() and 0xFFFF
                    val fileNameBytes = ByteArray(fileNameLen)
                    buffer.get(fileNameBytes)
                    val fileName = String(fileNameBytes, StandardCharsets.UTF_8)
                    
                    val fileSizeBytes = buffer.long
                    
                    val mimeTypeLen = buffer.short.toInt() and 0xFFFF
                    val mimeTypeBytes = ByteArray(mimeTypeLen)
                    buffer.get(mimeTypeBytes)
                    val mimeType = String(mimeTypeBytes, StandardCharsets.UTF_8)
                    
                    val chunkSize = buffer.int
                    val totalChunks = buffer.int
                    
                    val envelopeLen = buffer.int
                    val envelope = ByteArray(envelopeLen)
                    buffer.get(envelope)
                    
                    val senderTimestamp = buffer.long
                    
                    Frame.FileOfferFrame(msgId, transferId, fileName, fileSizeBytes, mimeType, chunkSize, totalChunks, envelope, senderTimestamp)
                }
                FrameType.FILE_CHUNK -> {
                    val buffer = ByteBuffer.wrap(payload)
                    
                    val transferIdLen = buffer.get().toInt() and 0xFF
                    val transferIdBytes = ByteArray(transferIdLen)
                    buffer.get(transferIdBytes)
                    val transferId = String(transferIdBytes, StandardCharsets.UTF_8)
                    
                    val chunkIndex = buffer.int
                    val dataLen = buffer.int
                    val data = ByteArray(dataLen)
                    buffer.get(data)
                    
                    Frame.FileChunkFrame(transferId, chunkIndex, data)
                }
                FrameType.FILE_COMPLETE -> {
                    val transferId = String(payload, StandardCharsets.UTF_8)
                    Frame.FileCompleteFrame(transferId)
                }
                FrameType.KEY_BUNDLE_REQUEST -> {
                    val contactId = String(payload, StandardCharsets.UTF_8)
                    Frame.KeyBundleRequestFrame(contactId)
                }
                FrameType.KEY_BUNDLE_RESPONSE -> {
                    Frame.KeyBundleResponseFrame(payload)
                }
                FrameType.UNKNOWN -> {
                    Frame.UnknownFrame(typeByte)
                }
            }
        } catch (e: Exception) {
            return null
        }
    }
}
