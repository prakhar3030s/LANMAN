package com.prakhar.lanman.domain.network

import com.prakhar.lanman.domain.model.ContactPresence
import kotlinx.coroutines.flow.Flow

interface PresenceTracker {
    val contactPresence: Flow<List<ContactPresence>>
    fun start()
    fun stop()
}
