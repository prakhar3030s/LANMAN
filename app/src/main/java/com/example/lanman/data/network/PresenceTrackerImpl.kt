package com.example.lanman.data.network

import android.util.Log
import com.example.lanman.domain.model.ContactPresence
import com.example.lanman.domain.network.NetworkDiscoveryManager
import com.example.lanman.domain.network.PresenceTracker
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.util.network.NetworkConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PresenceTrackerImpl(
    private val networkDiscoveryManager: NetworkDiscoveryManager,
    private val contactRepository: ContactRepository
) : PresenceTracker {

    private var scope: CoroutineScope? = null
    
    private val TAG = "PresenceTracker"

    // A ticker that emits the current time every second, used to reactively compute online status
    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }
    

    private val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sharedContactPresence = combine(
        contactRepository.getAllContacts(),
        tickerFlow
    ) { contacts, now ->
        contacts.map { contact ->
            val isOnline = contact.lastSeenAt != null && 
                (now - contact.lastSeenAt) < NetworkConstants.PRESENCE_TIMEOUT_MS
                
            ContactPresence(
                contact = contact,
                isOnline = isOnline,
                lastKnownIp = contact.lastKnownIp
            )
        }.sortedByDescending { it.isOnline }
    }.stateIn(
        scope = sharedScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    override val contactPresence: Flow<List<ContactPresence>> = _sharedContactPresence

    @Synchronized
    override fun start() {
        if (scope != null) return
        
        Log.d(TAG, "Starting PresenceTracker")
        
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope

        networkDiscoveryManager.startDiscovery()

        newScope.launch {
            networkDiscoveryManager.discoveredPeers.collect { peer ->
                // Cross-reference with our known contacts
                val existingContact = contactRepository.getContactById(peer.id).firstOrNull()
                
                if (existingContact != null) {
                    Log.d(TAG, "Updating presence for known contact: ${existingContact.displayName}")
                    contactRepository.updateLastKnownIp(peer.id, peer.ipAddress, peer.timestamp)
                } else {
                    // Ignore unknown devices entirely
                }
            }
        }
    }

    @Synchronized
    override fun stop() {
        Log.d(TAG, "Stopping PresenceTracker")
        networkDiscoveryManager.stopDiscovery()
        scope?.cancel()
        scope = null
    }
}
