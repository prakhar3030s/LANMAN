package com.example.lanman.domain.network

import com.example.lanman.domain.model.network.DiscoveredPeer
import kotlinx.coroutines.flow.Flow

interface NetworkDiscoveryManager {
    val discoveredPeers: Flow<DiscoveredPeer>
    fun startDiscovery()
    fun stopDiscovery()
}
