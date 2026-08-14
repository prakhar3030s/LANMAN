package com.example.lanman.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["contactId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
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

        other as MessageEntity

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
