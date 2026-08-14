package com.example.lanman.domain.model

data class MessageRequest(
    val contactId: String,
    val displayName: String,
    val identityKey: String,
    val timestamp: Long,
    val firstMessageText: String
)
