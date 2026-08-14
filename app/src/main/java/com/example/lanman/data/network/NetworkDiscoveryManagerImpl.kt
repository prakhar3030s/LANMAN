package com.example.lanman.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.example.lanman.domain.model.network.DiscoveredPeer
import com.example.lanman.domain.network.HeartbeatCodec
import com.example.lanman.domain.network.NetworkDiscoveryManager
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.util.network.NetworkConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException

class NetworkDiscoveryManagerImpl(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val wifiManager: WifiManager,
    private val connectivityManager: ConnectivityManager
) : NetworkDiscoveryManager {

    private var scope: CoroutineScope? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    
    private var broadcasterSocket: DatagramSocket? = null
    private var listenerSocket: DatagramSocket? = null

    private val _discoveredPeers = MutableSharedFlow<DiscoveredPeer>(extraBufferCapacity = 64)
    override val discoveredPeers: Flow<DiscoveredPeer> = _discoveredPeers.asSharedFlow()

    private val TAG = "NetworkDiscoveryManager"

    override fun startDiscovery() {
        if (scope != null) return // Already running
        
        Log.d(TAG, "Starting discovery")
        
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope

        acquireMulticastLock()
        
        newScope.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
            releaseMulticastLock()
        }

        newScope.launch {
            val myId = profileRepository.getProfile().firstOrNull()?.id ?: return@launch
            startBroadcaster(myId)
        }

        newScope.launch {
            val myId = profileRepository.getProfile().firstOrNull()?.id ?: return@launch
            startListener(myId)
        }
    }

    override fun stopDiscovery() {
        Log.d(TAG, "Stopping discovery")
        scope?.cancel()
        scope = null
        
        broadcasterSocket?.close()
        broadcasterSocket = null
        
        listenerSocket?.close()
        listenerSocket = null

        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("LocalLinkDiscoveryLock").apply {
                setReferenceCounted(true)
                acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        multicastLock = null
    }

    private suspend fun startBroadcaster(myId: String) {
        val payloadBytes = HeartbeatCodec.encode(myId)
        
        while (scope?.isActive == true) {
            try {
                if (broadcasterSocket == null || broadcasterSocket?.isClosed == true) {
                    broadcasterSocket = DatagramSocket().apply {
                        broadcast = true
                    }
                }

                val broadcastAddresses = getBroadcastAddresses()
                for (broadcastAddress in broadcastAddresses) {
                    try {
                        val packet = DatagramPacket(
                            payloadBytes, 
                            payloadBytes.size, 
                            broadcastAddress, 
                            NetworkConstants.UDP_DISCOVERY_PORT
                        )
                        broadcasterSocket?.send(packet)
                        Log.d(TAG, "Sent heartbeat packet to $broadcastAddress")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send heartbeat to $broadcastAddress", e)
                    }
                }
            } catch (e: SocketException) {
                // Socket was closed, likely from stopDiscovery. Ignore.
            } catch (e: Exception) {
                Log.e(TAG, "Error in UDP broadcaster", e)
                broadcasterSocket?.close()
                broadcasterSocket = null
            }
            
            delay(NetworkConstants.HEARTBEAT_INTERVAL_MS)
        }
    }

    private fun startListener(myId: String) {
        try {
            listenerSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(NetworkConstants.UDP_DISCOVERY_PORT))
            }

            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)

            Log.d(TAG, "Listener started on port ${NetworkConstants.UDP_DISCOVERY_PORT}")

            while (scope?.isActive == true) {
                try {
                    listenerSocket?.receive(packet)
                    val senderIp = packet.address.hostAddress
                    if (senderIp == null) continue

                    val decoded = HeartbeatCodec.decode(packet.data, packet.length)
                    if (decoded != null && decoded.id != myId) {
                        val peer = DiscoveredPeer(
                            id = decoded.id,
                            ipAddress = senderIp,
                            tcpPort = decoded.tcpPort,
                            timestamp = System.currentTimeMillis()
                        )
                        _discoveredPeers.tryEmit(peer)
                    }
                } catch (e: SocketException) {
                    // Socket closed, exit loop
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error receiving packet", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listener socket", e)
        }
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) {
                        addresses.add(broadcast)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network interfaces", e)
        }
        
        // Fallback to limited broadcast if none found
        if (addresses.isEmpty()) {
            try {
                addresses.add(InetAddress.getByName("255.255.255.255"))
            } catch (e: Exception) {
                // Ignore
            }
        }
        return addresses
    }
}
