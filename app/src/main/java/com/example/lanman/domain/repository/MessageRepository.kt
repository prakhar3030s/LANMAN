package com.example.lanman.domain.repository

import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun getMessageById(id: String): Message?
    fun getMessagesForContact(contactId: String): Flow<List<Message>>
    suspend fun getPendingMessagesForContact(contactId: String, status: MessageStatus = MessageStatus.PENDING): List<Message>
    suspend fun getUnacknowledgedMessagesForContact(contactId: String): List<Message>
    fun getContactsWithUnacknowledgedMessagesFlow(): Flow<List<String>>
    suspend fun insertMessage(message: Message)
    suspend fun insertIfNotExists(message: Message): Boolean
    suspend fun updateMessageStatus(id: String, status: MessageStatus)
    suspend fun updateMessageStatusIfCurrent(id: String, currentStatus: MessageStatus, status: MessageStatus)
    fun getLastMessageForContact(contactId: String): Flow<Message?>
    suspend fun markConversationRead(contactId: String)
    suspend fun deleteConversation(contactId: String)
    fun getMessageCount(contactId: String): Flow<Int>
    fun getAttachmentCount(contactId: String): Flow<Int>
}
