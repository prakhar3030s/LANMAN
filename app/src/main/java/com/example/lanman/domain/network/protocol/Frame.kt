package com.example.lanman.domain.network.protocol

enum class FrameType(val typeByte: Byte) {
    HELLO(0),
    PING(1),
    PONG(2),
    MESSAGE(3),
    ACK(4),
    READ(5),
    FILE_OFFER(6),
    FILE_CHUNK(7),
    FILE_COMPLETE(8),
    KEY_BUNDLE_REQUEST(9),
    KEY_BUNDLE_RESPONSE(10),
    UNKNOWN(-1);

    companion object {
        // Return null ONLY for truly invalid situations (e.g., EOF issues), otherwise use UNKNOWN for forward compatibility
        fun fromByte(byte: Byte): FrameType = entries.find { it.typeByte == byte } ?: UNKNOWN
    }
}

sealed class Frame {
    abstract val type: FrameType

    data class HelloFrame(
        val contactId: String,
        val displayName: String,
        val identityKey: String
    ) : Frame() {
        override val type = FrameType.HELLO
    }

    object PingFrame : Frame() {
        override val type = FrameType.PING
    }

    object PongFrame : Frame() {
        override val type = FrameType.PONG
    }

    data class MessageFrame(
        val messageId: String,
        val senderId: String,
        val messageType: Byte,
        val timestamp: Long,
        val content: ByteArray
    ) : Frame() {
        override val type = FrameType.MESSAGE
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MessageFrame

            if (messageId != other.messageId) return false
            if (senderId != other.senderId) return false
            if (messageType != other.messageType) return false
            if (timestamp != other.timestamp) return false
            if (!content.contentEquals(other.content)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = messageId.hashCode()
            result = 31 * result + senderId.hashCode()
            result = 31 * result + messageType
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + content.contentHashCode()
            return result
        }
    }

    data class AckFrame(val messageId: String) : Frame() {
        override val type = FrameType.ACK
    }

    data class ReadFrame(val messageId: String) : Frame() {
        override val type = FrameType.READ
    }

    data class FileOfferFrame(
        val messageId: String,
        val fileTransferId: String,
        val fileName: String,
        val fileSizeBytes: Long,
        val mimeType: String,
        val chunkSize: Int,
        val totalChunks: Int,
        val encryptedKeyEnvelope: ByteArray,
        val senderTimestamp: Long
    ) : Frame() {
        override val type = FrameType.FILE_OFFER
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FileOfferFrame
            if (messageId != other.messageId) return false
            if (fileTransferId != other.fileTransferId) return false
            if (fileName != other.fileName) return false
            if (fileSizeBytes != other.fileSizeBytes) return false
            if (mimeType != other.mimeType) return false
            if (chunkSize != other.chunkSize) return false
            if (totalChunks != other.totalChunks) return false
            if (!encryptedKeyEnvelope.contentEquals(other.encryptedKeyEnvelope)) return false
            if (senderTimestamp != other.senderTimestamp) return false
            return true
        }

        override fun hashCode(): Int {
            var result = messageId.hashCode()
            result = 31 * result + fileTransferId.hashCode()
            result = 31 * result + fileName.hashCode()
            result = 31 * result + fileSizeBytes.hashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + chunkSize
            result = 31 * result + totalChunks
            result = 31 * result + encryptedKeyEnvelope.contentHashCode()
            result = 31 * result + senderTimestamp.hashCode()
            return result
        }
    }

    data class FileChunkFrame(
        val fileTransferId: String,
        val chunkIndex: Int,
        val data: ByteArray
    ) : Frame() {
        override val type = FrameType.FILE_CHUNK
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FileChunkFrame
            if (fileTransferId != other.fileTransferId) return false
            if (chunkIndex != other.chunkIndex) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = fileTransferId.hashCode()
            result = 31 * result + chunkIndex
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    data class FileCompleteFrame(
        val fileTransferId: String
    ) : Frame() {
        override val type = FrameType.FILE_COMPLETE
    }

    data class KeyBundleRequestFrame(
        val contactId: String
    ) : Frame() {
        override val type = FrameType.KEY_BUNDLE_REQUEST
    }

    data class KeyBundleResponseFrame(
        val bundle: ByteArray
    ) : Frame() {
        override val type = FrameType.KEY_BUNDLE_RESPONSE
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as KeyBundleResponseFrame
            if (!bundle.contentEquals(other.bundle)) return false
            return true
        }

        override fun hashCode(): Int {
            return bundle.contentHashCode()
        }
    }

    data class UnknownFrame(
        val unknownTypeByte: Byte
    ) : Frame() {
        override val type = FrameType.UNKNOWN
    }
}
