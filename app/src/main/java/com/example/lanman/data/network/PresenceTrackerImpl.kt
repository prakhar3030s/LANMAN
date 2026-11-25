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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class PresenceTrackerImpl(
    private val networkDiscoveryManager: NetworkDiscoveryManager,
    private val contactRepository: ContactRepository
) : PresenceTracker {

    private var scope: CoroutineScope? = null
    private val TAG = "PresenceTracker"

    private data class LivePeerSnapshot(val ip: String, val lastSeenAt: Long)

    // In-memory presence cache avoids writing to Room on every 3-second UDP heartbeat.
    private val livePeers = ConcurrentHashMap<String, LivePeerSnapshot>()
    private val lastDbPersist = ConcurrentHashMap<String, Pair<String, Long>>()
    private val presenceVersion = MutableStateFlow(0L)

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    private val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sharedContactPresence = combine(
        contactRepository.getAllContacts(),
        presenceVersion,
        tickerFlow
    ) { contacts, _, now ->
        contacts.map { contact ->
            val live = livePeers[contact.id]
            val effectiveLastSeen = maxOf(contact.lastSeenAt ?: 0L, live?.lastSeenAt ?: 0L)
                .takeIf { it > 0L }
            val effectiveIp = live?.ip ?: contact.lastKnownIp
            val isOnline = effectiveLastSeen != null &&
                (now - effectiveLastSeen) < NetworkConstants.PRESENCE_TIMEOUT_MS

            ContactPresence(
                contact = contact.copy(lastKnownIp = effectiveIp, lastSeenAt = effectiveLastSeen),
                isOnline = isOnline,
                lastKnownIp = effectiveIp
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
            networkDiscoveryManager.networkTransitions.collect {
                // Network topology changed; drop stale in-memory online timestamps until new heartbeats arrive
                if (!networkDiscoveryManager.networkState.value.isWifiConnected) {
                    livePeers.clear()
                    presenceVersion.value++
                }
            }
        }

        newScope.launch {
            networkDiscoveryManager.discoveredPeers.collect { peer ->
                val existingContact = contactRepository.getContactById(peer.id).firstOrNull()
                    ?: return@collect

                livePeers[peer.id] = LivePeerSnapshot(peer.ipAddress, peer.timestamp)
                presenceVersion.value++

                val lastPersisted = lastDbPersist[peer.id]
                val ipChanged = lastPersisted?.first != peer.ipAddress &&
                    existingContact.lastKnownIp != peer.ipAddress
                val staleDb = lastPersisted == null ||
                    (peer.timestamp - lastPersisted.second) >= DB_PERSIST_INTERVAL_MS

                if (ipChanged || staleDb) {
                    lastDbPersist[peer.id] = peer.ipAddress to peer.timestamp
                    contactRepository.updateLastKnownIp(peer.id, peer.ipAddress, peer.timestamp)
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
        livePeers.clear()
        presenceVersion.value++
    }

    private companion object {
        const val DB_PERSIST_INTERVAL_MS = 30_000L
    }
}
