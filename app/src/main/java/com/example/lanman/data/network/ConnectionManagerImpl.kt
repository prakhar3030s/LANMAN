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
    private var serverSocket: ServerSocket? = null
    
    private val activeConnections = ConcurrentHashMap<String, ActiveConnection>()
    private val connectMutex = kotlinx.coroutines.sync.Mutex()
    
    private val _incomingFrames = MutableSharedFlow<IncomingFrameEvent>(extraBufferCapacity = 512)
    override val incomingFrames: Flow<IncomingFrameEvent> = _incomingFrames.asSharedFlow()

    override fun start() {
        if (scope != null) return
        Log.d(TAG, "Starting ConnectionManager")
        
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        
        newScope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(NetworkConstants.TCP_MESSAGE_PORT))
                }
                Log.d(TAG, "TCP Server listening on port ${NetworkConstants.TCP_MESSAGE_PORT}")
                
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    socket.sendBufferSize = 512 * 1024
                    socket.receiveBufferSize = 512 * 1024
                    socket.tcpNoDelay = true
                    Log.d(TAG, "Accepted incoming TCP connection from ${socket.inetAddress.hostAddress}")
                    launch { handleIncomingConnection(socket) }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Server socket error", e)
                }
            }
        }
    }

    override fun stop() {
        Log.d(TAG, "Stopping ConnectionManager")
        scope?.cancel()
        scope = null
        
        serverSocket?.close()
        serverSocket = null
        
        activeConnections.values.forEach { 
            try { it.socket.close() } catch (e: Exception) {} 
        }
        activeConnections.clear()
    }

    private suspend fun handleIncomingConnection(socket: Socket) {
        var contactId: String? = null
        try {
            val inputStream = socket.getInputStream()
            
            // Require HELLO as the first frame with a 5s timeout
            val helloFrame = withTimeoutOrNull(5000L) {
                FrameCodec.decode(inputStream)
            }
            
            if (helloFrame !is Frame.HelloFrame) {
                Log.w(TAG, "First frame was not HELLO. Closing socket.")
                socket.close()
                return
            }
            
            contactId = helloFrame.contactId
            
            // Phase 23: Extra defensive check for malformed contactId before hitting the DB
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
                        sendFrameInternal(contactId, connection, Frame.KeyBundleRequestFrame(contactId))
                    }
                } catch (e: Exception) {}
            }
            
            readLoop(contactId, connection, inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming connection", e)
        } finally {
            try { socket.close() } catch (e: Exception) {}
            if (contactId != null) {
                activeConnections.remove(contactId)
                Log.d(TAG, "Cleaned up connection map for $contactId")
            }
        }
    }

    private suspend fun readLoop(contactId: String, connection: ActiveConnection, inputStream: InputStream) {
        try {
            while (scope?.isActive == true && !connection.socket.isClosed) {
                val frame = FrameCodec.decode(inputStream)
                if (frame == null) {
                    Log.d(TAG, "EOF or decode error from $contactId")
                    break // EOF or error
                }
                
                when (frame) {
                    is Frame.PingFrame -> {
                        Log.d(TAG, "Received PING from $contactId, sending PONG")
                        sendFrameInternal(contactId, connection, Frame.PongFrame)
                    }
                    is Frame.UnknownFrame -> {
                        Log.d(TAG, "Ignoring unknown frame type (forward compatibility) from $contactId")
                    }
                    else -> {
                        val event = IncomingFrameEvent(
                            contactId = contactId,
                            frame = frame,
                            isUnknown = connection.isUnknown,
                            unknownDisplayName = connection.unknownDisplayName,
                            unknownIdentityKey = connection.unknownIdentityKey
                        )
                        _incomingFrames.emit(event)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in read loop for $contactId", e)
        }
    }

    override suspend fun connectTo(contactId: String): Result<Unit> {
        connectMutex.withLock {
            return try {
                if (activeConnections.containsKey(contactId)) {
                    Log.d(TAG, "reusing existing connection to $contactId")
                    return Result.success(Unit)
                }
                
                Log.d(TAG, "opening new connection to $contactId")
                
                val contact = contactRepository.getContactById(contactId).firstOrNull() 
                    ?: return Result.failure(Exception("Contact not found"))
                    
                val ip = contact.lastKnownIp 
                    ?: return Result.failure(Exception("Contact has no known IP"))
                    
                val myProfile = profileRepository.getProfile().firstOrNull()
                    ?: return Result.failure(Exception("Profile not found"))

                val socket = Socket(ip, NetworkConstants.TCP_MESSAGE_PORT)
                socket.sendBufferSize = 512 * 1024
                socket.receiveBufferSize = 512 * 1024
                socket.tcpNoDelay = true
                
                val connection = ActiveConnection(socket)
                
                // Immediately send HELLO
                var myIdentityKey = ""
                myProfile.publicKeyBundle?.let { bytes ->
                    try {
                        val json = org.json.JSONObject(String(bytes, Charsets.UTF_8))
                        myIdentityKey = json.optString("identityKey", "")
                    } catch (e: Exception) {}
                }
                sendFrameInternal(contactId, connection, Frame.HelloFrame(myProfile.id, myProfile.displayName, myIdentityKey))
                
                activeConnections[contactId] = connection
                
                if (contact.publicKeyBundle != null) {
                    try {
                        val json = org.json.JSONObject(String(contact.publicKeyBundle, Charsets.UTF_8))
                        if (!json.has("kyberPreKey")) {
                            Log.d(TAG, "Missing kyberPreKey for outbound $contactId, requesting key bundle")
                            sendFrameInternal(contactId, connection, Frame.KeyBundleRequestFrame(contactId))
                        }
                    } catch (e: Exception) {}
                }
                
                scope?.launch {
                    try {
                        readLoop(contactId, connection, socket.getInputStream())
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

    override suspend fun sendFrame(contactId: String, frame: Frame, flush: Boolean): Result<Unit> {
        var connection = activeConnections[contactId]
        
        if (connection == null || connection.socket.isClosed) {
            val connectResult = connectTo(contactId)
            if (connectResult.isFailure) return connectResult
            
            connection = activeConnections[contactId] 
                ?: return Result.failure(Exception("Failed to acquire connection after connectTo"))
        }
        
        return try {
            sendFrameInternal(contactId, connection, frame, flush)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send frame to $contactId", e)
            try { connection.socket.close() } catch (ex: Exception) {}
            activeConnections.remove(contactId)
            Result.failure(e)
        }
    }
    
    private suspend fun sendFrameInternal(contactId: String, connection: ActiveConnection, frame: Frame, flush: Boolean = true) {
        val bytes = FrameCodec.encode(frame)
        connection.writeMutex.withLock {
            connection.outputStream.write(bytes)
            if (flush) {
                connection.outputStream.flush()
            }
        }
    }

    override fun disconnectFrom(contactId: String) {
        val connection = activeConnections.remove(contactId)
        if (connection != null) {
            try {
                connection.socket.close()
                Log.d(TAG, "Explicitly disconnected from $contactId")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing connection to $contactId", e)
            }
        }
    }
}
