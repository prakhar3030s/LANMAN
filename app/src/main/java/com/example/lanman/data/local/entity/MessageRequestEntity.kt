package com.example.lanman.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_requests")
data class MessageRequestEntity(
    @PrimaryKey val contactId: String,
    val displayName: String,
    val identityKey: String,
    val timestamp: Long,
    val firstMessageText: String
)
