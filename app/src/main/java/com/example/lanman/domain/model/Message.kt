package com.example.lanman.domain.model

data class Message(
    val id: String,
    val contactId: String,
    val direction: MessageDirection,
    val content: ByteArray,
    val type: MessageType,
    val status: MessageStatus,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message

        if (id != other.id) return false
        if (contactId != other.contactId) return false
        if (direction != other.direction) return false
        if (!content.contentEquals(other.content)) return false
        if (type != other.type) return false
        if (status != other.status) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + contactId.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
