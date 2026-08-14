package com.example.lanman.domain.repository

import com.example.lanman.domain.model.Contact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun getAllContacts(): Flow<List<Contact>>
    fun getContactById(id: String): Flow<Contact?>
    suspend fun upsertContact(contact: Contact)
    suspend fun deleteContact(id: String)
    suspend fun updateLastKnownIp(id: String, ip: String?, timestamp: Long?)
    fun getChatListContacts(): Flow<List<com.example.lanman.domain.model.ChatListContact>>
}
