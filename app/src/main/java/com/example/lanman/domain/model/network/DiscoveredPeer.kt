package com.example.lanman.domain.model.network

data class DiscoveredPeer(
    val id: String,
    val ipAddress: String,
    val tcpPort: Int,
    val timestamp: Long
)
