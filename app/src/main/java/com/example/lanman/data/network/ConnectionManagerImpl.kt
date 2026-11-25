package com.example.lanman.data.network

import android.util.Log
import com.example.lanman.data.network.protocol.ActiveConnection
import com.example.lanman.data.security.BlockedContactsStore
import com.example.lanman.data.security.SecureKeyStore
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.IncomingFrameEvent
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.network.protocol.FrameCodec
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.domain.security.PeerAuth
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ConnectionManagerImpl(
    private val profileRepository: ProfileRepository,
    private val contactRepository: ContactRepository,
    private val secureKeyStore: SecureKeyStore,
    private val blockedContactsStore: BlockedContactsStore
) : ConnectionManager {

    private val TAG = "ConnectionManager"

    private var scope: CoroutineScope? = null
    private var controlServerSocket: ServerSocket? = null
    private var dataServerSocket: ServerSocket? = null

    private val activeConnections = ConcurrentHashMap<String, ActiveConnection>()
    private val connectMutexes = ConcurrentHashMap<String, Mutex>()
    private val dataConnectMutexes = ConcurrentHashMap<String, Mutex>()
    private val secureRandom = SecureRandom()

    private val _incomingFrames = MutableSharedFlow<IncomingFrameEvent>(extraBufferCapacity = 1024)
    override val incomingFrames: Flow<IncomingFrameEvent> = _incomingFrames.asSharedFlow()

    private fun connectLockFor(contactId: String): Mutex =
        connectMutexes.computeIfAbsent(contactId) { Mutex() }

    private fun dataConnectLockFor(contactId: String): Mutex =
        dataConnectMutexes.computeIfAbsent(contactId) { Mutex() }

    override fun hasActiveConnection(contactId: String): Boolean {
        val conn = activeConnections[contactId]
        return conn != null && !conn.socket.isClosed
    }

    override fun start() {
        if (scope != null) return
        Log.d(TAG, "Starting ConnectionManager with Dual-Channel Transport")

        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope

        newScope.launch {
            try {
                controlServerSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(NetworkConstants.TCP_MESSAGE_PORT))
                }
                Log.d(TAG, "Control TCP Server listening on port ${NetworkConstants.TCP_MESSAGE_PORT}")

                while (isActive) {
                    val socket = controlServerSocket?.accept() ?: break
                    configureSocket(socket, NetworkConstants.CONTROL_SOCKET_BUFFER_SIZE)
                    launch { handleIncomingControlConnection(socket) }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Control server socket error", e)
                }
            }
        }

        newScope.launch {
            try {
                dataServerSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(NetworkConstants.TCP_DATA_PORT))
                }
                    Log.d(TAG, "Data TCP Server listening on port ${NetworkConstants.TCP_DATA_PORT}")

                while (isActive) {
                    val socket = dataServerSocket?.accept() ?: break
                    configureSocket(socket, NetworkConstants.DATA_SOCKET_BUFFER_SIZE)
                    launch { handleIncomingDataConnection(socket) }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Data server socket error", e)
                }
            }
        }

        // 3. Periodic TCP Keepalive (PING / dead-socket sweep)
        newScope.launch {
            while (isActive) {
                delay(NetworkConstants.TCP_KEEPALIVE_INTERVAL_MS)
                val now = System.currentTimeMillis()
                activeConnections.entries.forEach { (contactId, conn) ->
                    if (conn.socket.isClosed) {
                        activeConnections.remove(contactId, conn)
                        return@forEach
                    }
                    if (now - conn.lastActivityAt > NetworkConstants.TCP_IDLE_TIMEOUT_MS) {
                        Log.w(TAG, "Closing idle/stale connection for $contactId")
                        activeConnections.remove(contactId, conn)
                        conn.close()
                    } else if (now - conn.lastActivityAt >= NetworkConstants.TCP_KEEPALIVE_INTERVAL_MS) {
                        runCatching { sendFrameInternal(conn, Frame.PingFrame, flush = true) }
                            .onFailure {
                                activeConnections.remove(contactId, conn)
                                conn.close()
                            }
                    }
                }
            }
        }
    }

    override fun stop() {
        Log.d(TAG, "Stopping ConnectionManager")
        scope?.cancel()
        scope = null

        try { controlServerSocket?.close() } catch (_: Exception) {}
        controlServerSocket = null

        try { dataServerSocket?.close() } catch (_: Exception) {}
        dataServerSocket = null

        activeConnections.values.forEach { it.close() }
        activeConnections.clear()
    }

    private fun configureSocket(socket: Socket, bufferSize: Int) {
        socket.sendBufferSize = bufferSize
        socket.receiveBufferSize = bufferSize
        socket.tcpNoDelay = true
        socket.keepAlive = true
    }

    private suspend fun handleIncomingControlConnection(socket: Socket) {
        var contactId: String? = null
        var activeConnection: ActiveConnection? = null
        try {
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val inputStream = socket.getInputStream()
            val out = BufferedOutputStream(socket.getOutputStream(), NetworkConstants.CONTROL_SOCKET_BUFFER_SIZE)

            val helloFrame = FrameCodec.decode(inputStream)
            if (helloFrame !is Frame.HelloFrame) {
                Log.w(TAG, "First frame was not HELLO on control channel. Closing socket.")
                socket.close()
                return
            }

            contactId = helloFrame.contactId
            if (!isValidUuid(contactId)) {
                Log.w(TAG, "Malformed contact ID in HELLO")
                writeHandshakeFrame(out, Frame.AuthResultFrame(false, "Invalid contact ID"))
                socket.close()
                return
            }

            if (blockedContactsStore.isBlocked(contactId)) {
                Log.w(TAG, "Rejected HELLO from blocked contact")
                writeHandshakeFrame(out, Frame.AuthResultFrame(false, "Blocked"))
                socket.close()
                return
            }

            if (helloFrame.protocolVersion < Frame.HelloFrame.CURRENT_PROTOCOL_VERSION ||
                helloFrame.clientChallenge.size < MIN_CHALLENGE_SIZE
            ) {
                Log.w(TAG, "Rejected unauthenticated/legacy HELLO (v${helloFrame.protocolVersion})")
                writeHandshakeFrame(out, Frame.AuthResultFrame(false, "Incompatible protocol version"))
                socket.close()
                return
            }

            val myProfile = profileRepository.getProfile().firstOrNull()
            val myIdentityKeyPair = secureKeyStore.getIdentityKeyPair()
            if (myProfile == null || myIdentityKeyPair == null) {
                socket.close()
                return
            }

            val existingContact = contactRepository.getContactById(contactId).firstOrNull()
            val isUnknown = existingContact == null

            val expectedIdentityKey: String = if (!isUnknown) {
                val pinned = PeerAuth.extractIdentityKeyBase64(existingContact?.publicKeyBundle)
                if (pinned == null) {
                    writeHandshakeFrame(out, Frame.AuthResultFrame(false, "Missing pinned key"))
                    socket.close()
                    return
                }
                if (helloFrame.identityKey.isNotBlank() && helloFrame.identityKey != pinned) {
                    Log.w(TAG, "HELLO identity key mismatch for paired contact")
                    writeHandshakeFrame(out, Frame.AuthResultFrame(false, "Identity key mismatch"))
                    socket.close()
                    return
                }
                pinned
            } else {
                if (PeerAuth.parseIdentityKey(helloFrame.identityKey) == null) {
                    writeHandshakeFrame(out, Frame.AuthResultFrame(false, "Missing identity key"))
                    socket.close()
                    return
                }
                helloFrame.identityKey
            }

            // Mutual challenge-response authentication
            val serverChallenge = ByteArray(CHALLENGE_SIZE).also { secureRandom.nextBytes(it) }
            val serverSig = PeerAuth.signChallenge(
                identityKeyPair = myIdentityKeyPair,
                challenge = helloFrame.clientChallenge,
                signerId = myProfile.id,
                verifierId = contactId,
                channelTag = CHANNEL_CTRL
            )
            writeHandshakeFrame(out, Frame.AuthChallengeFrame(serverChallenge, serverSig))

            val authResponse = FrameCodec.decode(inputStream)
            if (authResponse !is Frame.AuthResponseFrame ||
                !PeerAuth.verifyChallenge(
                    identityKeyBase64 = expectedIdentityKey,
                    challenge = serverChallenge,
                    signature = authResponse.clientSignature,
                    signerId = contactId,
                    verifierId = myProfile.id,
                    channelTag = CHANNEL_CTRL
                )
            ) {
                Log.w(TAG, "Control channel authentication failed for $contactId")
                writeHandshakeFrame(out, Frame.AuthResultFrame(false, "Authentication failed"))
                socket.close()
                return
            }

            writeHandshakeFrame(out, Frame.AuthResultFrame(true))
            socket.soTimeout = 0

            if (!isUnknown) {
                val remoteIp = socket.inetAddress?.hostAddress
                if (!remoteIp.isNullOrBlank()) {
                    contactRepository.updateLastKnownIp(contactId, remoteIp, System.currentTimeMillis())
                }
            }

            val connection = ActiveConnection(
                socket = socket,
                isUnknown = isUnknown,
                unknownDisplayName = if (isUnknown) helloFrame.displayName else null,
                unknownIdentityKey = if (isUnknown) helloFrame.identityKey else null
            )
            activeConnections[contactId] = connection
            activeConnection = connection

            if (!isUnknown && existingContact?.publicKeyBundle != null) {
                try {
                    val json = org.json.JSONObject(String(existingContact.publicKeyBundle, Charsets.UTF_8))
                    if (!json.has("kyberPreKey")) {
                        sendFrameInternal(connection, Frame.KeyBundleRequestFrame(contactId))
                    }
                } catch (_: Exception) {}
            }

            readControlLoop(contactId, connection, inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming control connection", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
            val id = contactId
            val ownConnection = activeConnection
            if (id != null && ownConnection != null) {
                activeConnections.remove(id, ownConnection)
            }
        }
    }

    private suspend fun handleIncomingDataConnection(socket: Socket) {
        try {
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val inputStream = socket.getInputStream()
            val out = BufferedOutputStream(socket.getOutputStream(), NetworkConstants.DATA_SOCKET_BUFFER_SIZE)

            val helloFrame = FrameCodec.decode(inputStream)
            if (helloFrame !is Frame.HelloFrame) {
                socket.close()
                return
            }

            val contactId = helloFrame.contactId
            if (!isValidUuid(contactId) || blockedContactsStore.isBlocked(contactId)) {
                writeHandshakeFrame(out, Frame.AuthResultFrame(false, "Rejected"))
                socket.close()
                return
            }

            if (helloFrame.protocolVersion < Frame.HelloFrame.CURRENT_PROTOCOL_VERSION ||
                helloFrame.clientChallenge.size < MIN_CHALLENGE_SIZE
            ) {
                writeHandshakeFrame(out, Frame.AuthResultFrame(false, "Incompatible protocol version"))
                socket.close()
                return
            }

            val controlConnection = activeConnections[contactId]
            if (controlConnection == null ||
                controlConnection.socket.isClosed ||
                controlConnection.isUnknown ||
                controlConnection.socket.inetAddress != socket.inetAddress
            ) {
                Log.w(TAG, "Rejected data connection without matching authenticated control connection")
                writeHandshakeFrame(out, Frame.AuthResultFrame(false, "Control channel required"))
                socket.close()
                return
            }

            val existingContact = contactRepository.getContactById(contactId).firstOrNull()
            val expectedIdentityKey = PeerAuth.extractIdentityKeyBase64(existingContact?.publicKeyBundle)
            val myProfile = profileRepository.getProfile().firstOrNull()
            val myIdentityKeyPair = secureKeyStore.getIdentityKeyPair()
            if (expectedIdentityKey == null || myProfile == null || myIdentityKeyPair == null) {
                socket.close()
                return
            }

            val serverChallenge = ByteArray(CHALLENGE_SIZE).also { secureRandom.nextBytes(it) }
            val serverSig = PeerAuth.signChallenge(
                identityKeyPair = myIdentityKeyPair,
                challenge = helloFrame.clientChallenge,
                signerId = myProfile.id,
                verifierId = contactId,
                channelTag = CHANNEL_DATA
            )
            writeHandshakeFrame(out, Frame.AuthChallengeFrame(serverChallenge, serverSig))

            val authResponse = FrameCodec.decode(inputStream)
            if (authResponse !is Frame.AuthResponseFrame ||
                !PeerAuth.verifyChallenge(
                    identityKeyBase64 = expectedIdentityKey,
                    challenge = serverChallenge,
                    signature = authResponse.clientSignature,
                    signerId = contactId,
                    verifierId = myProfile.id,
                    channelTag = CHANNEL_DATA
                )
            ) {
                Log.w(TAG, "Data channel authentication failed for $contactId")
                writeHandshakeFrame(out, Frame.AuthResultFrame(false, "Authentication failed"))
                socket.close()
                return
            }

            writeHandshakeFrame(out, Frame.AuthResultFrame(true))
            socket.soTimeout = 0

            controlConnection.setDataConnection(socket)
            Log.d(TAG, "Attached authenticated data connection for $contactId")
            readDataLoop(contactId, controlConnection, inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming data connection", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private suspend fun readControlLoop(contactId: String, connection: ActiveConnection, inputStream: InputStream) {
        try {
            while (scope?.isActive == true && !connection.socket.isClosed) {
                val frame = FrameCodec.decode(inputStream) ?: break
                connection.markActive()

                when (frame) {
                    is Frame.PingFrame -> {
                        sendFrameInternal(connection, Frame.PongFrame)
                    }
                    is Frame.PongFrame -> {
                        // Activity timestamp already updated above.
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
                val frame = FrameCodec.decode(inputStream) ?: break
                connection.markActive()

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

    override suspend fun connectTo(contactId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (blockedContactsStore.isBlocked(contactId)) {
            return@withContext Result.failure(SecurityException("Contact is blocked"))
        }

        connectLockFor(contactId).withLock {
            var socket: Socket? = null
            return@withLock try {
                val existing = activeConnections[contactId]
                if (existing != null && !existing.socket.isClosed) {
                    return@withLock Result.success(Unit)
                }

                val contact = contactRepository.getContactById(contactId).firstOrNull()
                    ?: return@withLock Result.failure(Exception("Contact not found"))
                val ip = contact.lastKnownIp
                    ?: return@withLock Result.failure(Exception("Contact has no known IP"))
                val expectedIdentityKey = PeerAuth.extractIdentityKeyBase64(contact.publicKeyBundle)
                    ?: return@withLock Result.failure(SecurityException("Contact has no pinned identity key"))
                val myProfile = profileRepository.getProfile().firstOrNull()
                    ?: return@withLock Result.failure(Exception("Profile not found"))
                val myIdentityKeyPair = secureKeyStore.getIdentityKeyPair()
                    ?: return@withLock Result.failure(IllegalStateException("Identity keys missing"))
                val myIdentityKey = PeerAuth.extractIdentityKeyBase64(myProfile.publicKeyBundle) ?: ""

                socket = Socket()
                socket.connect(InetSocketAddress(ip, NetworkConstants.TCP_MESSAGE_PORT), CONNECT_TIMEOUT_MS)
                configureSocket(socket, NetworkConstants.CONTROL_SOCKET_BUFFER_SIZE)
                socket.soTimeout = HANDSHAKE_TIMEOUT_MS

                val connection = ActiveConnection(socket)
                performClientHandshake(
                    inputStream = socket.getInputStream(),
                    outputStream = connection.outputStream,
                    myId = myProfile.id,
                    myDisplayName = myProfile.displayName,
                    myIdentityKey = myIdentityKey,
                    myIdentityKeyPair = myIdentityKeyPair,
                    peerId = contactId,
                    peerExpectedIdentityKey = expectedIdentityKey,
                    channelTag = CHANNEL_CTRL
                )
                socket.soTimeout = 0

                activeConnections[contactId] = connection

                if (contact.publicKeyBundle != null) {
                    try {
                        val json = org.json.JSONObject(String(contact.publicKeyBundle, Charsets.UTF_8))
                        if (!json.has("kyberPreKey")) {
                            sendFrameInternal(connection, Frame.KeyBundleRequestFrame(contactId))
                        }
                    } catch (_: Exception) {}
                }

                val connectedSocket = socket
                scope?.launch {
                    try {
                        readControlLoop(contactId, connection, connectedSocket.getInputStream())
                    } finally {
                        try { connectedSocket.close() } catch (_: Exception) {}
                        activeConnections.remove(contactId, connection)
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                try { socket?.close() } catch (_: Exception) {}
                Log.e(TAG, "Failed to connect to $contactId", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun connectDataChannelTo(contactId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (blockedContactsStore.isBlocked(contactId)) {
            return@withContext Result.failure(SecurityException("Contact is blocked"))
        }

        dataConnectLockFor(contactId).withLock {
            var dataSocket: Socket? = null
            return@withLock try {
                val connection = activeConnections[contactId]
                    ?: run {
                        val connectRes = connectTo(contactId)
                        if (connectRes.isFailure) return@withLock connectRes
                        activeConnections[contactId] ?: return@withLock Result.failure(Exception("No connection found"))
                    }

                if (connection.dataSocket != null && connection.dataSocket?.isClosed == false) {
                    return@withLock Result.success(Unit)
                }

                val contact = contactRepository.getContactById(contactId).firstOrNull()
                    ?: return@withLock Result.failure(Exception("Contact not found"))
                val ip = contact.lastKnownIp
                    ?: return@withLock Result.failure(Exception("No known IP"))
                val expectedIdentityKey = PeerAuth.extractIdentityKeyBase64(contact.publicKeyBundle)
                    ?: return@withLock Result.failure(SecurityException("Contact has no pinned identity key"))
                val myProfile = profileRepository.getProfile().firstOrNull()
                    ?: return@withLock Result.failure(Exception("Profile not found"))
                val myIdentityKeyPair = secureKeyStore.getIdentityKeyPair()
                    ?: return@withLock Result.failure(IllegalStateException("Identity keys missing"))
                val myIdentityKey = PeerAuth.extractIdentityKeyBase64(myProfile.publicKeyBundle) ?: ""

                dataSocket = Socket()
                dataSocket.connect(InetSocketAddress(ip, NetworkConstants.TCP_DATA_PORT), CONNECT_TIMEOUT_MS)
                configureSocket(dataSocket, NetworkConstants.DATA_SOCKET_BUFFER_SIZE)
                dataSocket.soTimeout = HANDSHAKE_TIMEOUT_MS

                val out = BufferedOutputStream(dataSocket.getOutputStream(), NetworkConstants.DATA_SOCKET_BUFFER_SIZE)
                performClientHandshake(
                    inputStream = dataSocket.getInputStream(),
                    outputStream = out,
                    myId = myProfile.id,
                    myDisplayName = myProfile.displayName,
                    myIdentityKey = myIdentityKey,
                    myIdentityKeyPair = myIdentityKeyPair,
                    peerId = contactId,
                    peerExpectedIdentityKey = expectedIdentityKey,
                    channelTag = CHANNEL_DATA
                )
                dataSocket.soTimeout = 0

                connection.setDataConnection(dataSocket)

                val connectedDataSocket = dataSocket
                scope?.launch {
                    try {
                        readDataLoop(contactId, connection, connectedDataSocket.getInputStream())
                    } finally {
                        try { connectedDataSocket.close() } catch (_: Exception) {}
                        if (connection.dataSocket === connectedDataSocket) {
                            connection.dataSocket = null
                            connection.dataOutputStream = null
                        }
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                try { dataSocket?.close() } catch (_: Exception) {}
                Log.w(TAG, "Failed to connect data channel to $contactId: ${e.message}")
                Result.failure(e)
            }
        }
    }

    private fun performClientHandshake(
        inputStream: InputStream,
        outputStream: BufferedOutputStream,
        myId: String,
        myDisplayName: String,
        myIdentityKey: String,
        myIdentityKeyPair: org.signal.libsignal.protocol.IdentityKeyPair,
        peerId: String,
        peerExpectedIdentityKey: String,
        channelTag: String
    ) {
        val clientChallenge = ByteArray(CHALLENGE_SIZE).also { secureRandom.nextBytes(it) }
        writeHandshakeFrame(
            outputStream,
            Frame.HelloFrame(
                contactId = myId,
                displayName = myDisplayName,
                identityKey = myIdentityKey,
                protocolVersion = Frame.HelloFrame.CURRENT_PROTOCOL_VERSION,
                clientChallenge = clientChallenge
            )
        )

        val serverReply = FrameCodec.decode(inputStream)
        if (serverReply is Frame.AuthResultFrame && !serverReply.accepted) {
            throw SecurityException("Peer rejected handshake: ${serverReply.reason}")
        }
        val challengeFrame = serverReply as? Frame.AuthChallengeFrame
            ?: throw SecurityException("Peer did not complete authentication challenge")

        if (challengeFrame.serverChallenge.size < MIN_CHALLENGE_SIZE ||
            !PeerAuth.verifyChallenge(
                identityKeyBase64 = peerExpectedIdentityKey,
                challenge = clientChallenge,
                signature = challengeFrame.serverSignature,
                signerId = peerId,
                verifierId = myId,
                channelTag = channelTag
            )
        ) {
            throw SecurityException("Peer identity verification failed")
        }

        val clientSig = PeerAuth.signChallenge(
            identityKeyPair = myIdentityKeyPair,
            challenge = challengeFrame.serverChallenge,
            signerId = myId,
            verifierId = peerId,
            channelTag = channelTag
        )
        writeHandshakeFrame(outputStream, Frame.AuthResponseFrame(clientSig))

        val resultFrame = FrameCodec.decode(inputStream) as? Frame.AuthResultFrame
            ?: throw SecurityException("Missing authentication confirmation")
        if (!resultFrame.accepted) {
            throw SecurityException("Peer rejected authentication: ${resultFrame.reason}")
        }
    }

    private fun writeHandshakeFrame(out: BufferedOutputStream, frame: Frame) {
        FrameCodec.writeFrame(out, frame)
        out.flush()
    }

    private fun isValidUuid(id: String): Boolean {
        return try {
            UUID.fromString(id)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun sendFrame(contactId: String, frame: Frame, flush: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (frame is Frame.FileChunkFrame ||
                frame is Frame.FileHashFrame ||
                frame is Frame.FileCompleteFrame
            ) {
                return@withContext sendDataFrame(contactId, frame, flush)
            }

            var connection = activeConnections[contactId]
            if (connection == null || connection.socket.isClosed) {
                val connectResult = connectTo(contactId)
                if (connectResult.isFailure) return@withContext connectResult

                connection = activeConnections[contactId]
                    ?: return@withContext Result.failure(Exception("Failed to acquire connection after connectTo"))
            }

            val conn = connection
            try {
                sendFrameInternal(conn, frame, flush)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send frame to $contactId", e)
                try { conn.socket.close() } catch (_: Exception) {}
                activeConnections.remove(contactId, conn)
                Result.failure(e)
            }
        }

    override suspend fun sendDataFrame(contactId: String, frame: Frame, flush: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            var connection = activeConnections[contactId]
            if (connection == null || connection.socket.isClosed) {
                val connectResult = connectTo(contactId)
                if (connectResult.isFailure) return@withContext connectResult
                connection = activeConnections[contactId]
                    ?: return@withContext Result.failure(Exception("Failed to acquire connection"))
            }

            if (connection.dataSocket == null || connection.dataSocket?.isClosed == true) {
                connectDataChannelTo(contactId)
            }

            val dataStream = connection.dataOutputStream
            if (dataStream != null && connection.dataSocket?.isClosed == false) {
                return@withContext try {
                    connection.dataWriteMutex.withLock {
                        FrameCodec.writeFrame(dataStream, frame)
                        if (flush) {
                            dataStream.flush()
                        }
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.w(TAG, "Data channel send failed, falling back to control channel: ${e.message}")
                    sendFrameFallback(connection, frame, flush)
                }
            }

            sendFrameFallback(connection, frame, flush)
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

    override fun closeAllConnections() {
        val keys = activeConnections.keys.toList()
        keys.forEach { key ->
            activeConnections.remove(key)?.close()
        }
        Log.d(TAG, "Closed ${keys.size} active peer sockets on network transition")
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 4_000
        const val HANDSHAKE_TIMEOUT_MS = 5_000
        const val CHALLENGE_SIZE = 32
        const val MIN_CHALLENGE_SIZE = 16
        const val CHANNEL_CTRL = "CTRL"
        const val CHANNEL_DATA = "DATA"
    }
}
