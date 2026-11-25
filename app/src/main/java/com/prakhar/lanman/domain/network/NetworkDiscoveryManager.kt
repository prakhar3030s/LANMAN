package com.prakhar.lanman.domain.network

import com.prakhar.lanman.domain.model.network.DiscoveredPeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class LanNetworkState(
    val isWifiConnected: Boolean = false,
    val localAddresses: List<String> = emptyList(),
    val isDiscoveryActive: Boolean = false,
    val lastError: String? = null
)

interface NetworkDiscoveryManager {
    val discoveredPeers: Flow<DiscoveredPeer>
    val networkState: StateFlow<LanNetworkState>
    val networkTransitions: Flow<Unit>

    fun startDiscovery()
    fun stopDiscovery()
    fun triggerRefreshBurst()
}
