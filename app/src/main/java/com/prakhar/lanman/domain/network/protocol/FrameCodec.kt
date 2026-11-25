package com.prakhar.lanman.domain.network.protocol

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object FrameCodec {
    const val MAX_FRAME_SIZE = 10 * 1024 * 1024 // 10 MB absolute cap
    private const val MAX_CONTROL_PAYLOAD_SIZE = 256 * 1024 // 256 KB cap for non-chunk control frames
    private const val MAX_UBYTE = 255
    private const val MAX_USHORT = 65_535
    private const val MAX_AUTH_FIELD_SIZE = 512

    /**
     * Streams a frame directly into [outputStream] with zero intermediate buffer copies
     * for large file chunks.
     */
    fun writeFrame(outputStream: OutputStream, frame: Frame) {
        if (frame is Frame.FileChunkFrame) {
            val transferIdBytes = utf8Within(frame.fileTransferId, MAX_UBYTE, "fileTransferId")
            val payloadCapacity = 1 + transferIdBytes.size + 4 + 4 + frame.data.size
            val frameLength = 1 + payloadCapacity
            require(frameLength <= MAX_FRAME_SIZE) { "FileChunkFrame exceeds max frame size" }

            val headerBuffer = ByteBuffer.allocate(4 + 1 + 1 + transferIdBytes.size + 4 + 4)
            headerBuffer.putInt(frameLength)
            headerBuffer.put(frame.type.typeByte)
            headerBuffer.put(transferIdBytes.size.toByte())
            headerBuffer.put(transferIdBytes)
            headerBuffer.putInt(frame.chunkIndex)
            headerBuffer.putInt(frame.data.size)

            outputStream.write(headerBuffer.array())
            outputStream.write(frame.data)
            return
        }

        val encoded = encode(frame)
        outputStream.write(encoded)
    }

    fun encode(frame: Frame): ByteArray {
        if (frame is Frame.FileChunkFrame) {
            val transferIdBytes = utf8Within(frame.fileTransferId, MAX_UBYTE, "fileTransferId")
            val payloadCapacity = 1 + transferIdBytes.size + 4 + 4 + frame.data.size
            val frameLength = 1 + payloadCapacity
            require(frameLength <= MAX_FRAME_SIZE) { "FileChunkFrame exceeds max frame size" }

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
                val contactIdBytes = utf8Within(frame.contactId, MAX_UBYTE, "contactId")
                val displayNameBytes = utf8Within(frame.displayName, MAX_UBYTE, "displayName")
                val identityKeyBytes = utf8Within(frame.identityKey, MAX_USHORT, "identityKey")
                require(frame.clientChallenge.size <= MAX_UBYTE) { "clientChallenge exceeds 255 bytes" }

                val includeV2Auth = frame.protocolVersion >= Frame.HelloFrame.CURRENT_PROTOCOL_VERSION ||
                    frame.clientChallenge.isNotEmpty()
                val extraAuthSize = if (includeV2Auth) 1 + 1 + frame.clientChallenge.size else 0

                val capacity = 1 + contactIdBytes.size + 1 + displayNameBytes.size + 2 +
                    identityKeyBytes.size + extraAuthSize
                val buffer = ByteBuffer.allocate(capacity)
                buffer.put(contactIdBytes.size.toByte())
                buffer.put(contactIdBytes)
                buffer.put(displayNameBytes.size.toByte())
                buffer.put(displayNameBytes)
                buffer.putShort(identityKeyBytes.size.toShort())
                buffer.put(identityKeyBytes)
                if (includeV2Auth) {
                    buffer.put(frame.protocolVersion)
                    buffer.put(frame.clientChallenge.size.toByte())
                    buffer.put(frame.clientChallenge)
                }
                buffer.array()
            }
            is Frame.AuthChallengeFrame -> {
                require(frame.serverChallenge.size <= MAX_UBYTE) { "serverChallenge too large" }
                require(frame.serverSignature.size <= MAX_USHORT) { "serverSignature too large" }
                val capacity = 1 + frame.serverChallenge.size + 2 + frame.serverSignature.size
                ByteBuffer.allocate(capacity)
                    .put(frame.serverChallenge.size.toByte())
                    .put(frame.serverChallenge)
                    .putShort(frame.serverSignature.size.toShort())
                    .put(frame.serverSignature)
                    .array()
            }
            is Frame.AuthResponseFrame -> {
                require(frame.clientSignature.size <= MAX_USHORT) { "clientSignature too large" }
                ByteBuffer.allocate(2 + frame.clientSignature.size)
                    .putShort(frame.clientSignature.size.toShort())
                    .put(frame.clientSignature)
                    .array()
            }
            is Frame.AuthResultFrame -> {
                val reasonBytes = utf8Within(frame.reason, MAX_UBYTE, "reason")
                ByteBuffer.allocate(1 + 1 + reasonBytes.size)
                    .put(if (frame.accepted) 1.toByte() else 0.toByte())
                    .put(reasonBytes.size.toByte())
                    .put(reasonBytes)
                    .array()
            }
            Frame.PingFrame -> ByteArray(0)
            Frame.PongFrame -> ByteArray(0)
            is Frame.MessageFrame -> {
                val msgIdBytes = utf8Within(frame.messageId, MAX_UBYTE, "messageId")
                val senderIdBytes = utf8Within(frame.senderId, MAX_UBYTE, "senderId")

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
            is Frame.AckFrame -> utf8Within(frame.messageId, MAX_UBYTE, "messageId")
            is Frame.ReadFrame -> utf8Within(frame.messageId, MAX_UBYTE, "messageId")
            is Frame.FileOfferFrame -> {
                val msgIdBytes = utf8Within(frame.messageId, MAX_UBYTE, "messageId")
                val transferIdBytes = utf8Within(frame.fileTransferId, MAX_UBYTE, "fileTransferId")
                val fileNameBytes = utf8Within(frame.fileName, MAX_USHORT, "fileName")
                val mimeTypeBytes = utf8Within(frame.mimeType, MAX_USHORT, "mimeType")

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
            is Frame.FileCompleteFrame -> utf8Within(frame.fileTransferId, MAX_UBYTE, "fileTransferId")
            is Frame.FileHashFrame -> {
                val transferIdBytes = utf8Within(frame.fileTransferId, MAX_UBYTE, "fileTransferId")
                require(frame.sha256.size == 32) { "sha256 must be 32 bytes" }
                ByteBuffer.allocate(1 + transferIdBytes.size + 32)
                    .put(transferIdBytes.size.toByte())
                    .put(transferIdBytes)
                    .put(frame.sha256)
                    .array()
            }
            is Frame.KeyBundleRequestFrame -> utf8Within(frame.contactId, MAX_UBYTE, "contactId")
            is Frame.KeyBundleResponseFrame -> frame.bundle
            is Frame.UnknownFrame -> ByteArray(0)
        }

        val frameLength = 1 + payload.size
        require(frameLength <= MAX_FRAME_SIZE) { "Frame exceeds max frame size" }

        val buffer = ByteBuffer.allocate(4 + frameLength)
        buffer.putInt(frameLength)
        buffer.put(frame.type.typeByte)
        buffer.put(payload)

        return buffer.array()
    }

    fun decode(inputStream: InputStream): Frame? {
        return try {
            val dataIn = if (inputStream is DataInputStream) inputStream else DataInputStream(inputStream)

            val length = dataIn.readInt()
            if (length < 1 || length > MAX_FRAME_SIZE) {
                return null
            }

            val typeByte = dataIn.readByte()
            val frameType = FrameType.fromByte(typeByte)
            val payloadLength = length - 1

            // Restrict non-chunk control payloads before allocation to prevent DoS via 10MB HELLO/PING frames.
            val maxForType = when (frameType) {
                FrameType.FILE_CHUNK, FrameType.UNKNOWN -> MAX_FRAME_SIZE - 1
                else -> MAX_CONTROL_PAYLOAD_SIZE
            }
            if (payloadLength > maxForType) {
                return null
            }

            val payload = ByteArray(payloadLength)
            if (payloadLength > 0) {
                dataIn.readFully(payload)
            }

            val buffer = ByteBuffer.wrap(payload)
            when (frameType) {
                FrameType.HELLO -> {
                    val contactId = readString(buffer, buffer.get().toInt() and 0xFF) ?: return null
                    val displayName = readString(buffer, buffer.get().toInt() and 0xFF) ?: return null
                    val identityKey = readString(buffer, buffer.short.toInt() and 0xFFFF) ?: return null

                    var protocolVersion = Frame.HelloFrame.LEGACY_PROTOCOL_VERSION
                    var clientChallenge = ByteArray(0)
                    if (buffer.remaining() >= 2) {
                        protocolVersion = buffer.get()
                        val challengeLen = buffer.get().toInt() and 0xFF
                        clientChallenge = readBytes(buffer, challengeLen, MAX_AUTH_FIELD_SIZE) ?: return null
                    }
                    Frame.HelloFrame(contactId, displayName, identityKey, protocolVersion, clientChallenge)
                }
                FrameType.AUTH_CHALLENGE -> {
                    val challengeLen = buffer.get().toInt() and 0xFF
                    val serverChallenge = readBytes(buffer, challengeLen, MAX_AUTH_FIELD_SIZE) ?: return null
                    val sigLen = buffer.short.toInt() and 0xFFFF
                    val serverSig = readBytes(buffer, sigLen, MAX_AUTH_FIELD_SIZE) ?: return null
                    Frame.AuthChallengeFrame(serverChallenge, serverSig)
                }
                FrameType.AUTH_RESPONSE -> {
                    val sigLen = buffer.short.toInt() and 0xFFFF
                    val clientSig = readBytes(buffer, sigLen, MAX_AUTH_FIELD_SIZE) ?: return null
                    Frame.AuthResponseFrame(clientSig)
                }
                FrameType.AUTH_RESULT -> {
                    val accepted = buffer.get() != 0.toByte()
                    val reasonLen = buffer.get().toInt() and 0xFF
                    val reason = readString(buffer, reasonLen) ?: return null
                    Frame.AuthResultFrame(accepted, reason)
                }
                FrameType.PING -> Frame.PingFrame
                FrameType.PONG -> Frame.PongFrame
                FrameType.MESSAGE -> {
                    val msgId = readString(buffer, buffer.get().toInt() and 0xFF) ?: return null
                    val senderId = readString(buffer, buffer.get().toInt() and 0xFF) ?: return null
                    if (buffer.remaining() < 1 + 8 + 4) return null
                    val msgType = buffer.get()
                    val timestamp = buffer.long
                    val contentLen = buffer.int
                    val content = readBytes(buffer, contentLen, MAX_CONTROL_PAYLOAD_SIZE) ?: return null
                    Frame.MessageFrame(msgId, senderId, msgType, timestamp, content)
                }
                FrameType.ACK -> Frame.AckFrame(String(payload, StandardCharsets.UTF_8))
                FrameType.READ -> Frame.ReadFrame(String(payload, StandardCharsets.UTF_8))
                FrameType.FILE_OFFER -> {
                    val msgId = readString(buffer, buffer.get().toInt() and 0xFF) ?: return null
                    val transferId = readString(buffer, buffer.get().toInt() and 0xFF) ?: return null
                    val fileName = readString(buffer, buffer.short.toInt() and 0xFFFF) ?: return null
                    if (buffer.remaining() < 8 + 2) return null
                    val fileSizeBytes = buffer.long
                    if (fileSizeBytes < 0L) return null
                    val mimeType = readString(buffer, buffer.short.toInt() and 0xFFFF) ?: return null
                    if (buffer.remaining() < 4 + 4 + 4) return null
                    val chunkSize = buffer.int
                    val totalChunks = buffer.int
                    val envelopeLen = buffer.int
                    val envelope = readBytes(buffer, envelopeLen, MAX_CONTROL_PAYLOAD_SIZE) ?: return null
                    if (buffer.remaining() < 8) return null
                    val senderTimestamp = buffer.long
                    Frame.FileOfferFrame(
                        msgId, transferId, fileName, fileSizeBytes, mimeType,
                        chunkSize, totalChunks, envelope, senderTimestamp
                    )
                }
                FrameType.FILE_CHUNK -> {
                    val transferId = readString(buffer, buffer.get().toInt() and 0xFF) ?: return null
                    if (buffer.remaining() < 8) return null
                    val chunkIndex = buffer.int
                    val dataLen = buffer.int
                    val data = readBytes(buffer, dataLen, MAX_FRAME_SIZE) ?: return null
                    Frame.FileChunkFrame(transferId, chunkIndex, data)
                }
                FrameType.FILE_COMPLETE -> Frame.FileCompleteFrame(String(payload, StandardCharsets.UTF_8))
                FrameType.FILE_HASH -> {
                    val transferId = readString(buffer, buffer.get().toInt() and 0xFF) ?: return null
                    val sha256 = readBytes(buffer, 32, 32) ?: return null
                    Frame.FileHashFrame(transferId, sha256)
                }
                FrameType.KEY_BUNDLE_REQUEST -> Frame.KeyBundleRequestFrame(String(payload, StandardCharsets.UTF_8))
                FrameType.KEY_BUNDLE_RESPONSE -> Frame.KeyBundleResponseFrame(payload)
                FrameType.UNKNOWN -> Frame.UnknownFrame(typeByte)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readBytes(buffer: ByteBuffer, len: Int, maxLen: Int): ByteArray? {
        if (len < 0 || len > maxLen || len > buffer.remaining()) return null
        val bytes = ByteArray(len)
        buffer.get(bytes)
        return bytes
    }

    private fun readString(buffer: ByteBuffer, len: Int): String? {
        val bytes = readBytes(buffer, len, MAX_USHORT) ?: return null
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun utf8Within(value: String, maxBytes: Int, fieldName: String): ByteArray {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= maxBytes) { "$fieldName exceeds $maxBytes UTF-8 bytes" }
        return bytes
    }
}
