package com.prakhar.lanman.data.repository

import com.prakhar.lanman.data.local.dao.MessageRequestDao
import com.prakhar.lanman.data.local.entity.MessageRequestEntity
import com.prakhar.lanman.domain.model.MessageRequest
import com.prakhar.lanman.domain.repository.MessageRequestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRequestRepositoryImpl @Inject constructor(
    private val dao: MessageRequestDao
) : MessageRequestRepository {

    override fun getAllMessageRequests(): Flow<List<MessageRequest>> {
        return dao.getAllMessageRequests().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addMessageRequest(request: MessageRequest) {
        dao.insertMessageRequest(request.toEntity())
    }

    override suspend fun deleteMessageRequest(contactId: String) {
        dao.deleteMessageRequest(contactId)
    }

    private fun MessageRequestEntity.toDomain() = MessageRequest(
        contactId = contactId,
        displayName = displayName,
        identityKey = identityKey,
        timestamp = timestamp,
        firstMessageText = firstMessageText
    )

    private fun MessageRequest.toEntity() = MessageRequestEntity(
        contactId = contactId,
        displayName = displayName,
        identityKey = identityKey,
        timestamp = timestamp,
        firstMessageText = firstMessageText
    )
}
