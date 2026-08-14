package com.example.lanman.data.repository

import com.example.lanman.data.local.dao.ContactDao
import com.example.lanman.domain.model.Contact
import com.example.lanman.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao
) : ContactRepository {

    override fun getAllContacts(): Flow<List<Contact>> {
        return contactDao.getAllContacts().map { list -> list.map { it.toDomain() } }
    }

    override fun getContactById(id: String): Flow<Contact?> {
        return contactDao.getContactById(id).map { it?.toDomain() }
    }

    override suspend fun upsertContact(contact: Contact) {
        contactDao.upsertContact(contact.toEntity())
    }

    override suspend fun deleteContact(id: String) {
        contactDao.deleteContact(id)
    }

    override suspend fun updateLastKnownIp(id: String, ip: String?, timestamp: Long?) {
        contactDao.updateLastKnownIp(id, ip, timestamp)
    }

    override fun getChatListContacts(): Flow<List<com.example.lanman.domain.model.ChatListContact>> {
        return contactDao.getChatListContacts().map { entities ->
            entities.map { entity ->
                com.example.lanman.domain.model.ChatListContact(
                    contact = entity.contact.toDomain(),
                    lastMessageContent = entity.lastMessageContent,
                    lastMessageTimestamp = entity.lastMessageTimestamp,
                    unreadCount = entity.unreadCount,
                    isOnline = false // Will be updated by PresenceTracker at UI layer or we can inject PresenceTracker here.
                )
            }
        }
    }
}
