package com.example.lanman.data.repository

import com.example.lanman.data.local.dao.MessageDao
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : MessageRepository {

    override suspend fun getMessageById(id: String): Message? {
        return messageDao.getMessageById(id)?.toDomain()
    }

    override fun getMessagesForContact(contactId: String): Flow<List<Message>> {
        return messageDao.getMessagesForContact(contactId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getPendingMessagesForContact(contactId: String, status: MessageStatus): List<Message> {
        return messageDao.getPendingMessagesForContact(contactId, status).map { it.toDomain() }
    }

    override suspend fun getUnacknowledgedMessagesForContact(contactId: String): List<Message> {
        return messageDao.getUnacknowledgedMessagesForContact(
            contactId,
            listOf(MessageStatus.PENDING, MessageStatus.SENT)
        ).map { it.toDomain() }
    }

    override fun getContactsWithUnacknowledgedMessagesFlow(): Flow<List<String>> {
        return messageDao.getContactsWithUnacknowledgedMessages(
            listOf(MessageStatus.PENDING, MessageStatus.SENT)
        )
    }

    override suspend fun insertMessage(message: Message) {
        messageDao.insertMessage(message.toEntity())
    }

    override suspend fun insertIfNotExists(message: Message): Boolean {
        return messageDao.insertIfNotExists(message.toEntity())
    }

    override suspend fun updateMessageStatus(id: String, status: MessageStatus) {
        messageDao.updateMessageStatus(id, status)
    }

    override fun getLastMessageForContact(contactId: String): Flow<Message?> {
        return messageDao.getLastMessageForContact(contactId).map { it?.toDomain() }
    }
}
