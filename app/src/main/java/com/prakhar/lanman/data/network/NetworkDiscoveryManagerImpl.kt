package com.prakhar.lanman.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.prakhar.lanman.domain.model.network.DiscoveredPeer
import com.prakhar.lanman.domain.network.HeartbeatCodec
import com.prakhar.lanman.domain.network.LanNetworkState
import com.prakhar.lanman.domain.network.NetworkDiscoveryManager
import com.prakhar.lanman.domain.repository.ProfileRepository
import com.prakhar.lanman.util.network.NetworkConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException

class NetworkDiscoveryManagerImpl(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val wifiManager: WifiManager,
    private val connectivityManager: ConnectivityManager
) : NetworkDiscoveryManager {

    private var scope: CoroutineScope? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var broadcasterSocket: DatagramSocket? = null
    private var listenerSocket: DatagramSocket? = null

    private val nsdManager: NsdManager? by lazy {
        runCatching { context.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull()
    }
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredPeers = MutableSharedFlow<DiscoveredPeer>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val discoveredPeers: Flow<DiscoveredPeer> = _discoveredPeers.asSharedFlow()

    private val _networkState = MutableStateFlow(LanNetworkState())
    override val networkState: StateFlow<LanNetworkState> = _networkState.asStateFlow()

    private val _networkTransitions = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val networkTransitions: Flow<Unit> = _networkTransitions.asSharedFlow()

    private val burstSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @Volatile
    private var cachedBroadcastAddresses: List<InetAddress>? = null

    @Volatile
    private var lastBroadcastLookupTime: Long = 0L

    private val TAG = "NetworkDiscoveryManager"

    @Synchronized
    override fun startDiscovery() {
        if (scope != null) return

        Log.d(TAG, "Starting discovery")
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope

        acquireMulticastLock()
        registerNetworkCallback()
        refreshNetworkState()

        _networkState.value = _networkState.value.copy(isDiscoveryActive = true, lastError = null)

        newScope.launch {
            val myId = profileRepository.getProfile().firstOrNull { it != null }?.id ?: return@launch
            launch { startBroadcaster(myId) }
            launch { startListener(myId) }
            launch { startMdns(myId) }
        }
    }

    @Synchronized
    override fun stopDiscovery() {
        Log.d(TAG, "Stopping discovery")
        unregisterNetworkCallback()
        stopMdns()

        scope?.cancel()
        scope = null

        runCatching { broadcasterSocket?.close() }
        broadcasterSocket = null

        runCatching { listenerSocket?.close() }
        listenerSocket = null

        releaseMulticastLock()
        cachedBroadcastAddresses = null
        _networkState.value = _networkState.value.copy(isDiscoveryActive = false)
    }

    override fun triggerRefreshBurst() {
        cachedBroadcastAddresses = null
        refreshNetworkState()
        burstSignal.tryEmit(Unit)
    }

    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            runCatching {
                multicastLock = wifiManager.createMulticastLock("LANMANDiscoveryLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.onFailure { Log.w(TAG, "Failed to acquire MulticastLock", it) }
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        }
        multicastLock = null
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onNetworkTopologyChanged(shouldNotifyReconnect = true)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                onNetworkTopologyChanged(shouldNotifyReconnect = true)
            }

            override fun onLost(network: Network) {
                onNetworkTopologyChanged(shouldNotifyReconnect = true)
            }
        }
        networkCallback = callback
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        runCatching { connectivityManager.registerNetworkCallback(request, callback) }
            .onFailure { Log.w(TAG, "Failed to register NetworkCallback", it) }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        runCatching { connectivityManager.unregisterNetworkCallback(cb) }
    }

    private fun onNetworkTopologyChanged(shouldNotifyReconnect: Boolean) {
        cachedBroadcastAddresses = null
        lastBroadcastLookupTime = 0L
        refreshNetworkState()
        if (shouldNotifyReconnect) {
            _networkTransitions.tryEmit(Unit)
        }
        if (_networkState.value.isWifiConnected) {
            burstSignal.tryEmit(Unit)
        }
    }

    private fun refreshNetworkState() {
        val addresses = findLocalIpv4Addresses()
        val hasWifiTransport = runCatching {
            @Suppress("DEPRECATION")
            connectivityManager.allNetworks.any { network ->
                val caps = connectivityManager.getNetworkCapabilities(network) ?: return@any false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            }
        }.getOrDefault(false)

        val wifiOrLanUp = hasWifiTransport || addresses.isNotEmpty()
        _networkState.value = _networkState.value.copy(
            isWifiConnected = wifiOrLanUp,
            localAddresses = addresses
        )
    }

    private fun findLocalIpv4Addresses(): List<String> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { nic -> nic.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
                .filterNot { it.startsWith("169.254.") }
        }.getOrDefault(emptyList())
    }

    private suspend fun startBroadcaster(myId: String) {
        val payloadBytes = HeartbeatCodec.encode(myId)
        var burstRemaining = NetworkConstants.FAST_DISCOVERY_BURST_COUNT

        val currentScope = scope ?: return
        currentScope.launch {
            burstSignal.collect {
                burstRemaining = NetworkConstants.FAST_DISCOVERY_BURST_COUNT
            }
        }

        while (scope?.isActive == true) {
            val state = _networkState.value
            if (!state.isWifiConnected) {
                delay(1_500L)
                refreshNetworkState()
                continue
            }

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
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to send heartbeat to $broadcastAddress: ${e.message}")
                    }
                }
            } catch (_: SocketException) {
                // Socket closed during stopDiscovery
            } catch (e: Exception) {
                Log.e(TAG, "Error in UDP broadcaster", e)
                runCatching { broadcasterSocket?.close() }
                broadcasterSocket = null
            }

            if (burstRemaining > 0) {
                burstRemaining--
                delay(NetworkConstants.FAST_DISCOVERY_BURST_DELAY_MS)
            } else {
                delay(NetworkConstants.HEARTBEAT_INTERVAL_MS)
            }
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
                    val senderIp = packet.address?.hostAddress ?: continue
                    val decoded = HeartbeatCodec.decode(packet.data, packet.length)
                    if (decoded != null && decoded.id != myId) {
                        _discoveredPeers.tryEmit(
                            DiscoveredPeer(
                                id = decoded.id,
                                ipAddress = senderIp,
                                tcpPort = decoded.tcpPort,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (_: SocketException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error receiving UDP packet", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start UDP listener socket", e)
            _networkState.value = _networkState.value.copy(
                lastError = "UDP port ${NetworkConstants.UDP_DISCOVERY_PORT} unavailable: ${e.message}"
            )
        }
    }

    private fun startMdns(myId: String) {
        val manager = nsdManager ?: return
        runCatching {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "lanman-${myId.take(8)}"
                serviceType = MDNS_SERVICE_TYPE
                port = NetworkConstants.TCP_MESSAGE_PORT
                setAttribute("id", myId)
            }

            val regListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    Log.d(TAG, "mDNS registered: ${info.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS registration failed: $errorCode")
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            nsdRegistrationListener = regListener
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, regListener)

            val discListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (!service.serviceType.contains("lanman")) return
                    if (service.serviceName == "lanman-${myId.take(8)}") return
                    resolveMdnsService(manager, service, myId)
                }

                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "mDNS discovery start failed: $errorCode")
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            nsdDiscoveryListener = discListener
            manager.discoverServices(MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discListener)
        }.onFailure { Log.w(TAG, "mDNS unavailable on this device", it) }
    }

    @Suppress("DEPRECATION")
    private fun resolveMdnsService(manager: NsdManager, service: NsdServiceInfo, myId: String) {
        runCatching {
            manager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val peerIdBytes = serviceInfo.attributes["id"] ?: return
                    val peerId = String(peerIdBytes, Charsets.UTF_8)
                    if (peerId.isBlank() || peerId == myId) return
                    val ip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        serviceInfo.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
                            ?: serviceInfo.hostAddresses.firstOrNull()?.hostAddress
                    } else {
                        serviceInfo.host?.hostAddress
                    } ?: return
                    _discoveredPeers.tryEmit(
                        DiscoveredPeer(
                            id = peerId,
                            ipAddress = ip,
                            tcpPort = serviceInfo.port,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            })
        }
    }

    private fun stopMdns() {
        val manager = nsdManager ?: return
        nsdRegistrationListener?.let { listener ->
            runCatching { manager.unregisterService(listener) }
        }
        nsdRegistrationListener = null

        nsdDiscoveryListener?.let { listener ->
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        nsdDiscoveryListener = null
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val now = System.currentTimeMillis()
        val cached = cachedBroadcastAddresses
        if (cached != null && (now - lastBroadcastLookupTime) < 30_000L) {
            return cached
        }

        val addresses = mutableSetOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
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

        if (addresses.isEmpty()) {
            runCatching { addresses.add(InetAddress.getByName("255.255.255.255")) }
        }

        val result = addresses.toList()
        cachedBroadcastAddresses = result
        lastBroadcastLookupTime = now
        return result
    }

    private companion object {
        const val MDNS_SERVICE_TYPE = "_lanman._tcp."
    }
}
