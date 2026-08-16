package com.example.lanman.data.network

import android.util.Log
import com.example.lanman.data.network.protocol.ActiveConnection
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.IncomingFrameEvent
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.network.protocol.FrameCodec
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.util.network.NetworkConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class ConnectionManagerImpl(
    private val profileRepository: ProfileRepository,
    private val contactRepository: ContactRepository
) : ConnectionManager {

    private val TAG = "ConnectionManager"
    
    private var scope: CoroutineScope? = null
    private var controlServerSocket: ServerSocket? = null
    private var dataServerSocket: ServerSocket? = null
    
    private val activeConnections = ConcurrentHashMap<String, ActiveConnection>()
    private val connectMutex = kotlinx.coroutines.sync.Mutex()
    private val dataConnectMutex = kotlinx.coroutines.sync.Mutex()
    
    private val _incomingFrames = MutableSharedFlow<IncomingFrameEvent>(extraBufferCapacity = 1024)
    override val incomingFrames: Flow<IncomingFrameEvent> = _incomingFrames.asSharedFlow()

    override fun hasActiveConnection(contactId: String): Boolean {
        val conn = activeConnections[contactId]
        return conn != null && !conn.socket.isClosed
    }

    override fun start() {
        if (scope != null) return
        Log.d(TAG, "Starting ConnectionManager with Dual-Channel Transport")
        
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        
        // 1. Control Channel Listener (Port 47475)
        newScope.launch {
            try {
                controlServerSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(NetworkConstants.TCP_MESSAGE_PORT))
                }
                Log.d(TAG, "Control TCP Server listening on port ${NetworkConstants.TCP_MESSAGE_PORT}")
                
                while (isActive) {
                    val socket = controlServerSocket?.accept() ?: break
                    socket.sendBufferSize = NetworkConstants.CONTROL_SOCKET_BUFFER_SIZE
                    socket.receiveBufferSize = NetworkConstants.CONTROL_SOCKET_BUFFER_SIZE
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    Log.d(TAG, "Accepted incoming Control TCP connection from ${socket.inetAddress.hostAddress}")
                    launch { handleIncomingControlConnection(socket) }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Control server socket error", e)
                }
            }
        }

        // 2. Data Channel Listener (Port 47476)
        newScope.launch {
            try {
                dataServerSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(NetworkConstants.TCP_DATA_PORT))
                }
                Log.d(TAG, "Data TCP Server listening on port ${NetworkConstants.TCP_DATA_PORT}")
                
                while (isActive) {
                    val socket = dataServerSocket?.accept() ?: break
                    socket.sendBufferSize = NetworkConstants.DATA_SOCKET_BUFFER_SIZE
                    socket.receiveBufferSize = NetworkConstants.DATA_SOCKET_BUFFER_SIZE
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    Log.d(TAG, "Accepted incoming Data TCP connection from ${socket.inetAddress.hostAddress}")
                    launch { handleIncomingDataConnection(socket) }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Data server socket error", e)
                }
            }
        }
    }

    override fun stop() {
        Log.d(TAG, "Stopping ConnectionManager")
        scope?.cancel()
        scope = null
        
        try { controlServerSocket?.close() } catch (e: Exception) {}
        controlServerSocket = null

        try { dataServerSocket?.close() } catch (e: Exception) {}
        dataServerSocket = null
        
        activeConnections.values.forEach { 
            it.close()
        }
        activeConnections.clear()
    }

    private suspend fun handleIncomingControlConnection(socket: Socket) {
        var contactId: String? = null
        try {
            val inputStream = socket.getInputStream()
            
            // Require HELLO as the first frame with a 5s timeout
            val helloFrame = withTimeoutOrNull(5000L) {
                FrameCodec.decode(inputStream)
            }
            
            if (helloFrame !is Frame.HelloFrame) {
                Log.w(TAG, "First frame was not HELLO on control channel. Closing socket.")
                socket.close()
                return
            }
            
            contactId = helloFrame.contactId
            
            try {
                java.util.UUID.fromString(contactId)
            } catch (e: Exception) {
                Log.w(TAG, "Malformed contact ID in HELLO: $contactId")
                socket.close()
                return
            }

            val existingContact = contactRepository.getContactById(contactId).firstOrNull()
            val isUnknown = existingContact == null
            if (isUnknown) {
                Log.d(TAG, "Received HELLO from unknown contact $contactId. Accepting connection conditionally.")
            } else {
                Log.d(TAG, "Verified HELLO from known contact: $contactId")
            }
            
            val connection = ActiveConnection(
                socket = socket,
                isUnknown = isUnknown,
                unknownDisplayName = if (isUnknown) helloFrame.displayName else null,
                unknownIdentityKey = if (isUnknown) helloFrame.identityKey else null
            )
            activeConnections[contactId] = connection
            
            if (!isUnknown && existingContact?.publicKeyBundle != null) {
                try {
                    val json = org.json.JSONObject(String(existingContact.publicKeyBundle, Charsets.UTF_8))
                    if (!json.has("kyberPreKey")) {
                        Log.d(TAG, "Missing kyberPreKey for incoming $contactId, requesting key bundle")
                        sendFrameInternal(connection, Frame.KeyBundleRequestFrame(contactId))
                    }
                } catch (e: Exception) {}
            }
            
            readControlLoop(contactId, connection, inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming control connection", e)
        } finally {
            try { socket.close() } catch (e: Exception) {}
            if (contactId != null) {
                activeConnections.remove(contactId)
                Log.d(TAG, "Cleaned up connection map for $contactId")
            }
        }
    }

    private suspend fun handleIncomingDataConnection(socket: Socket) {
        var contactId: String? = null
        try {
            val inputStream = socket.getInputStream()
            
            val helloFrame = withTimeoutOrNull(5000L) {
                FrameCodec.decode(inputStream)
            }
            
            if (helloFrame !is Frame.HelloFrame) {
                Log.w(TAG, "First frame was not HELLO on data channel. Closing data socket.")
                socket.close()
                return
            }
            
            contactId = helloFrame.contactId
            val connection = activeConnections[contactId]
            if (connection != null) {
                connection.setDataConnection(socket)
                Log.d(TAG, "Attached incoming data connection for $contactId")
                readDataLoop(contactId, connection, inputStream)
            } else {
                Log.w(TAG, "Data connection received from $contactId without active control connection. Reading directly.")
                val standaloneConnection = ActiveConnection(socket)
                standaloneConnection.setDataConnection(socket)
                readDataLoop(contactId, standaloneConnection, inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming data connection", e)
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private suspend fun readControlLoop(contactId: String, connection: ActiveConnection, inputStream: InputStream) {
        try {
            while (scope?.isActive == true && !connection.socket.isClosed) {
                val frame = FrameCodec.decode(inputStream)
                if (frame == null) {
                    Log.d(TAG, "EOF or decode error from $contactId on control channel")
                    break
                }
                
                when (frame) {
                    is Frame.PingFrame -> {
                        Log.d(TAG, "Received PING from $contactId, sending PONG")
                        sendFrameInternal(connection, Frame.PongFrame)
                    }
                    is Frame.UnknownFrame -> {
                        Log.d(TAG, "Ignoring unknown frame type from $contactId")
                    }
                    else -> {
                        val event = IncomingFrameEvent(
                            contactId = contactId,
                            frame = frame,
                            isUnknown = connection.isUnknown,
                            unknownDisplayName = connection.unknownDisplayName,
                            unknownIdentityKey = connection.unknownIdentityKey,
                            isDataChannel = false
                        )
                        _incomingFrames.emit(event)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in control read loop for $contactId", e)
        }
    }

    private suspend fun readDataLoop(contactId: String, connection: ActiveConnection, inputStream: InputStream) {
        try {
            while (scope?.isActive == true && connection.dataSocket?.isClosed == false) {
                val frame = FrameCodec.decode(inputStream)
                if (frame == null) {
                    Log.d(TAG, "EOF or decode error from $contactId on data channel")
                    break
                }
                
                val event = IncomingFrameEvent(
                    contactId = contactId,
                    frame = frame,
                    isUnknown = connection.isUnknown,
                    unknownDisplayName = connection.unknownDisplayName,
                    unknownIdentityKey = connection.unknownIdentityKey,
                    isDataChannel = true
                )
                _incomingFrames.emit(event)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in data read loop for $contactId", e)
        }
    }

    override suspend fun connectTo(contactId: String): Result<Unit> {
        connectMutex.withLock {
            return try {
                val existing = activeConnections[contactId]
                if (existing != null && !existing.socket.isClosed) {
                    return Result.success(Unit)
                }
                
                Log.d(TAG, "Opening new control connection to $contactId")
                
                val contact = contactRepository.getContactById(contactId).firstOrNull() 
                    ?: return Result.failure(Exception("Contact not found"))
                    
                val ip = contact.lastKnownIp 
                    ?: return Result.failure(Exception("Contact has no known IP"))
                    
                val myProfile = profileRepository.getProfile().firstOrNull()
                    ?: return Result.failure(Exception("Profile not found"))

                val socket = Socket(ip, NetworkConstants.TCP_MESSAGE_PORT)
                socket.sendBufferSize = NetworkConstants.CONTROL_SOCKET_BUFFER_SIZE
                socket.receiveBufferSize = NetworkConstants.CONTROL_SOCKET_BUFFER_SIZE
                socket.tcpNoDelay = true
                socket.keepAlive = true
                
                val connection = ActiveConnection(socket)
                
                // Send HELLO
                var myIdentityKey = ""
                myProfile.publicKeyBundle?.let { bytes ->
                    try {
                        val json = org.json.JSONObject(String(bytes, Charsets.UTF_8))
                        myIdentityKey = json.optString("identityKey", "")
                    } catch (e: Exception) {}
                }
                sendFrameInternal(connection, Frame.HelloFrame(myProfile.id, myProfile.displayName, myIdentityKey))
                
                activeConnections[contactId] = connection
                
                if (contact.publicKeyBundle != null) {
                    try {
                        val json = org.json.JSONObject(String(contact.publicKeyBundle, Charsets.UTF_8))
                        if (!json.has("kyberPreKey")) {
                            Log.d(TAG, "Missing kyberPreKey for outbound $contactId, requesting key bundle")
                            sendFrameInternal(connection, Frame.KeyBundleRequestFrame(contactId))
                        }
                    } catch (e: Exception) {}
                }
                
                scope?.launch {
                    try {
                        readControlLoop(contactId, connection, socket.getInputStream())
                    } finally {
                        try { socket.close() } catch (e: Exception) {}
                        activeConnections.remove(contactId)
                        Log.d(TAG, "Cleaned up connection map for $contactId (outbound)")
                    }
                }
                
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to $contactId", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun connectDataChannelTo(contactId: String): Result<Unit> {
        dataConnectMutex.withLock {
            return try {
                val connection = activeConnections[contactId] 
                    ?: run {
                        val connectRes = connectTo(contactId)
                        if (connectRes.isFailure) return connectRes
                        activeConnections[contactId] ?: return Result.failure(Exception("No connection found"))
                    }

                if (connection.dataSocket != null && connection.dataSocket?.isClosed == false) {
                    return Result.success(Unit)
                }

                val contact = contactRepository.getContactById(contactId).firstOrNull()
                    ?: return Result.failure(Exception("Contact not found"))
                val ip = contact.lastKnownIp ?: return Result.failure(Exception("No known IP"))
                val myProfile = profileRepository.getProfile().firstOrNull()
                    ?: return Result.failure(Exception("Profile not found"))

                Log.d(TAG, "Opening high-speed Data Channel socket to $contactId ($ip:${NetworkConstants.TCP_DATA_PORT})")
                val dataSocket = Socket(ip, NetworkConstants.TCP_DATA_PORT)
                dataSocket.sendBufferSize = NetworkConstants.DATA_SOCKET_BUFFER_SIZE
                dataSocket.receiveBufferSize = NetworkConstants.DATA_SOCKET_BUFFER_SIZE
                dataSocket.tcpNoDelay = true
                dataSocket.keepAlive = true

                connection.setDataConnection(dataSocket)

                // Handshake on data socket
                var myIdentityKey = ""
                myProfile.publicKeyBundle?.let { bytes ->
                    try {
                        val json = org.json.JSONObject(String(bytes, Charsets.UTF_8))
                        myIdentityKey = json.optString("identityKey", "")
                    } catch (e: Exception) {}
                }
                
                connection.dataWriteMutex.withLock {
                    connection.dataOutputStream?.let { out ->
                        FrameCodec.writeFrame(out, Frame.HelloFrame(myProfile.id, myProfile.displayName, myIdentityKey))
                        out.flush()
                    }
                }

                scope?.launch {
                    try {
                        readDataLoop(contactId, connection, dataSocket.getInputStream())
                    } finally {
                        try { dataSocket.close() } catch (e: Exception) {}
                        connection.dataSocket = null
                        connection.dataOutputStream = null
                        Log.d(TAG, "Data channel closed for $contactId")
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect data channel to $contactId: ${e.message}")
                Result.failure(e)
            }
        }
    }

    override suspend fun sendFrame(contactId: String, frame: Frame, flush: Boolean): Result<Unit> {
        // Forward high-volume chunk frames to dedicated data channel if applicable
        if (frame is Frame.FileChunkFrame || frame is Frame.FileCompleteFrame) {
            return sendDataFrame(contactId, frame, flush)
        }

        var connection = activeConnections[contactId]
        
        if (connection == null || connection.socket.isClosed) {
            val connectResult = connectTo(contactId)
            if (connectResult.isFailure) return connectResult
            
            connection = activeConnections[contactId] 
                ?: return Result.failure(Exception("Failed to acquire connection after connectTo"))
        }
        
        return try {
            sendFrameInternal(connection, frame, flush)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send frame to $contactId", e)
            try { connection.socket.close() } catch (ex: Exception) {}
            activeConnections.remove(contactId)
            Result.failure(e)
        }
    }

    override suspend fun sendDataFrame(contactId: String, frame: Frame, flush: Boolean): Result<Unit> {
        var connection = activeConnections[contactId]
        if (connection == null || connection.socket.isClosed) {
            val connectResult = connectTo(contactId)
            if (connectResult.isFailure) return connectResult
            connection = activeConnections[contactId]
                ?: return Result.failure(Exception("Failed to acquire connection"))
        }

        // Try connecting Data Channel if not already active
        if (connection.dataSocket == null || connection.dataSocket?.isClosed == true) {
            connectDataChannelTo(contactId)
        }

        val dataStream = connection.dataOutputStream
        if (dataStream != null && connection.dataSocket?.isClosed == false) {
            return try {
                connection.dataWriteMutex.withLock {
                    FrameCodec.writeFrame(dataStream, frame)
                    if (flush) {
                        dataStream.flush()
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "Data channel send failed, falling back to control channel: ${e.message}")
                // Fallback to control channel below
                sendFrameFallback(connection, frame, flush)
            }
        }

        // Fallback to control channel if data channel unavailable
        return sendFrameFallback(connection, frame, flush)
    }

    private suspend fun sendFrameFallback(connection: ActiveConnection, frame: Frame, flush: Boolean): Result<Unit> {
        return try {
            sendFrameInternal(connection, frame, flush)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun sendFrameInternal(connection: ActiveConnection, frame: Frame, flush: Boolean = true) {
        connection.writeMutex.withLock {
            FrameCodec.writeFrame(connection.outputStream, frame)
            if (flush) {
                connection.outputStream.flush()
            }
        }
    }

    override fun disconnectFrom(contactId: String) {
        val connection = activeConnections.remove(contactId)
        if (connection != null) {
            connection.close()
            Log.d(TAG, "Explicitly disconnected from $contactId")
        }
    }
}
