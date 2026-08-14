package com.example.lanman.domain.repository

import com.example.lanman.domain.model.MessageRequest
import kotlinx.coroutines.flow.Flow

interface MessageRequestRepository {
    fun getAllMessageRequests(): Flow<List<MessageRequest>>
    suspend fun addMessageRequest(request: MessageRequest)
    suspend fun deleteMessageRequest(contactId: String)
}
