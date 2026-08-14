package com.example.lanman.domain.network

import com.example.lanman.domain.model.ContactPresence
import kotlinx.coroutines.flow.Flow

interface PresenceTracker {
    val contactPresence: Flow<List<ContactPresence>>
    fun start()
    fun stop()
}
