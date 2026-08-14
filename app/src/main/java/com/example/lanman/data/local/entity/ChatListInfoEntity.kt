package com.example.lanman.data.local.entity

import androidx.room.Embedded

data class ChatListInfoEntity(
    @Embedded val contact: ContactEntity,
    val lastMessageContent: ByteArray?,
    val lastMessageTimestamp: Long?,
    val unreadCount: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChatListInfoEntity

        if (contact != other.contact) return false
        if (lastMessageContent != null) {
            if (other.lastMessageContent == null) return false
            if (!lastMessageContent.contentEquals(other.lastMessageContent)) return false
        } else if (other.lastMessageContent != null) return false
        if (lastMessageTimestamp != other.lastMessageTimestamp) return false
        if (unreadCount != other.unreadCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contact.hashCode()
        result = 31 * result + (lastMessageContent?.contentHashCode() ?: 0)
        result = 31 * result + (lastMessageTimestamp?.hashCode() ?: 0)
        result = 31 * result + unreadCount
        return result
    }
}
