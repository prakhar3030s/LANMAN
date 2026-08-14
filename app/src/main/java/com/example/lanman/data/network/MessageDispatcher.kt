package com.example.lanman.data.network

import android.util.Log
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.FileTransferProgressTracker
import com.example.lanman.domain.network.PresenceTracker
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.repository.FileTransferRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.domain.security.EncryptionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

import com.example.lanman.domain.network.NetworkDiscoveryManager

@Singleton
class MessageDispatcher @Inject constructor(
    private val networkDiscoveryManager: NetworkDiscoveryManager,
    private val connectionManager: ConnectionManager,
    private val messageRepository: MessageRepository,
    private val profileRepository: ProfileRepository,
    private val fileTransferRepository: FileTransferRepository,
    private val progressTracker: FileTransferProgressTracker,
    private val encryptionEngine: EncryptionEngine
) {
    private val TAG = "MessageDispatcher"
    private var job: Job? = null
    
    // Keeps track of currently flushing contacts so we don't start multiple flush loops for the same person
    private val flushingContacts = ConcurrentHashMap<String, Boolean>()

    // Maps messageId to the last timestamp (System.currentTimeMillis) we attempted to send it
    private val lastSentAttempt = ConcurrentHashMap<String, Long>()
    private val sendFailures = ConcurrentHashMap<String, Int>()

    fun start(scope: CoroutineScope) {
        if (job != null) return
        
        job = scope.launch(Dispatchers.IO) {
            val myId = profileRepository.getProfile().firstOrNull()?.id ?: return@launch
            
            // Trigger 1: DB changes (new unacknowledged messages)
            launch {
                messageRepository.getContactsWithUnacknowledgedMessagesFlow().collect { contactIds ->
                    for (contactId in contactIds) {
                        if (flushingContacts.putIfAbsent(contactId, true) == null) {
                            launch {
                                try {
                                    flushUnacknowledgedMessages(myId, contactId)
                                } finally {
                                    flushingContacts.remove(contactId)
                                }
                            }
                        }
                    }
                }
            }
            
            // Trigger 2: Peer heartbeat received (someone came online)
            launch {
                networkDiscoveryManager.discoveredPeers.collect { peer ->
                    val contactId = peer.id
                    if (flushingContacts.putIfAbsent(contactId, true) == null) {
                        launch {
                            try {
                                flushUnacknowledgedMessages(myId, contactId)
                            } finally {
                                flushingContacts.remove(contactId)
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        flushingContacts.clear()
        lastSentAttempt.clear()
    }

    private suspend fun flushUnacknowledgedMessages(myId: String, contactId: String) {
        val messages = messageRepository.getUnacknowledgedMessagesForContact(contactId)
        if (messages.isEmpty()) return
        
        val now = System.currentTimeMillis()
        val messagesToSend = messages.filter { message ->
            val failures = sendFailures[message.id] ?: 0
            val lastAttempt = lastSentAttempt[message.id] ?: 0L
            val backoffMs = if (failures == 0) {
                if (message.status == MessageStatus.PENDING) 0L else 10_000L
            } else {
                (10_000L * Math.pow(2.0, (failures - 1).coerceAtMost(6).toDouble())).toLong()
            }
            (now - lastAttempt) > backoffMs
        }

        if (messagesToSend.isEmpty()) return
        
        Log.d(TAG, "Flushing ${messagesToSend.size} unacknowledged messages for $contactId")
        
        for (message in messagesToSend) {
            val isSuccess = if (message.type == com.example.lanman.domain.model.MessageType.FILE) {
                sendFileMessage(myId, contactId, message)
            } else {
                sendTextMessage(myId, contactId, message)
            }
            
            lastSentAttempt[message.id] = System.currentTimeMillis()
            
            if (isSuccess) {
                sendFailures.remove(message.id)
                if (message.status != MessageStatus.SENT) {
                    messageRepository.updateMessageStatus(message.id, MessageStatus.SENT)
                }
                Log.d(TAG, "Successfully sent message ${message.id} to $contactId")
            } else {
                val newFailures = (sendFailures[message.id] ?: 0) + 1
                sendFailures[message.id] = newFailures
                
                if (newFailures >= 6) {
                    messageRepository.updateMessageStatus(message.id, MessageStatus.FAILED)
                    Log.e(TAG, "Message ${message.id} to $contactId failed $newFailures times. Marked as FAILED.")
                } else {
                    Log.w(TAG, "Failed to send message ${message.id} to $contactId (failure $newFailures). Aborting flush queue.")
                }
                break // Stop sending this queue, wait for next presence heartbeat to retry
            }
        }
    }

    private suspend fun sendTextMessage(myId: String, contactId: String, message: com.example.lanman.domain.model.Message): Boolean {
        val ciphertext = try {
            encryptionEngine.encrypt(contactId, message.content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt message ${message.id} for $contactId: ${e.message}", e)
            return false 
        }

        val frame = Frame.MessageFrame(
            messageId = message.id,
            senderId = myId,
            messageType = 0, // TEXT
            timestamp = message.timestamp,
            content = ciphertext
        )
        
        return connectionManager.sendFrame(contactId, frame).isSuccess
    }

    private suspend fun sendFileMessage(myId: String, contactId: String, message: com.example.lanman.domain.model.Message): Boolean {
        val fileTransfer = fileTransferRepository.getByMessageId(message.id).firstOrNull() ?: run {
            Log.e(TAG, "FileTransfer not found for message ${message.id}")
            return false
        }
        
        val encryptedFile = File(fileTransfer.localUri)
        if (!encryptedFile.exists()) {
            Log.e(TAG, "Encrypted local file not found at ${fileTransfer.localUri}")
            return false
        }
        
        return try {
            FileInputStream(encryptedFile).use { inputStream ->
                // Read 44 bytes (32 for AES key, 12 for base nonce)
                val keyAndNonce = ByteArray(44)
                if (inputStream.read(keyAndNonce) != 44) {
                    throw IllegalStateException("Encrypted file too short for key material")
                }
                
                val envelope = encryptionEngine.encrypt(contactId, keyAndNonce)
                
                // Calculate total chunks from remaining file size. 
                // Wait, the file size in FileTransfer is the original plaintext size.
                // We should calculate totalChunks by just counting how many chunks are in the file.
                // Each chunk in the file has a 4-byte length header + ciphertext.
                // To avoid reading the whole file to count chunks, we can estimate it, but it's better to just pass 0 if unknown,
                // or we can calculate based on original file size.
                // For a file of size N, chunks = ceil(N / CHUNK_SIZE).
                val CHUNK_SIZE = 64 * 1024
                val totalChunks = ((fileTransfer.fileSizeBytes + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
                
                // 1. Send FileOffer
                val offerFrame = Frame.FileOfferFrame(
                    messageId = message.id,
                    fileTransferId = fileTransfer.id,
                    fileName = fileTransfer.fileName,
                    fileSizeBytes = fileTransfer.fileSizeBytes,
                    mimeType = fileTransfer.mimeType,
                    chunkSize = CHUNK_SIZE,
                    totalChunks = totalChunks,
                    encryptedKeyEnvelope = envelope,
                    senderTimestamp = message.timestamp
                )
                
                if (connectionManager.sendFrame(contactId, offerFrame).isFailure) return false
                
                var chunkIndex = 0
                val lengthBufferBytes = ByteArray(4)
                var bytesSent = 0L
                
                // 2. Stream FileChunks
                while (true) {
                    val readLenBytes = inputStream.read(lengthBufferBytes)
                    if (readLenBytes == -1) break // EOF
                    if (readLenBytes != 4) throw IllegalStateException("Corrupt chunk length header")
                    
                    val chunkLength = ByteBuffer.wrap(lengthBufferBytes).int
                    val chunkData = ByteArray(chunkLength)
                    var readSoFar = 0
                    while (readSoFar < chunkLength) {
                        val readChunk = inputStream.read(chunkData, readSoFar, chunkLength - readSoFar)
                        if (readChunk == -1) throw IllegalStateException("Unexpected EOF in chunk data")
                        readSoFar += readChunk
                    }
                    
                    val chunkFrame = Frame.FileChunkFrame(
                        fileTransferId = fileTransfer.id,
                        chunkIndex = chunkIndex,
                        data = chunkData
                    )
                    
                    if (connectionManager.sendFrame(contactId, chunkFrame, flush = false).isFailure) return false
                    
                    // Plaintext size for progress update (approx)
                    bytesSent += CHUNK_SIZE.coerceAtMost((fileTransfer.fileSizeBytes - bytesSent).toInt())
                    progressTracker.updateProgress(fileTransfer.id, bytesSent, fileTransfer.fileSizeBytes)
                    
                    chunkIndex++
                }
                
                // 3. Send FileComplete
                val completeFrame = Frame.FileCompleteFrame(fileTransfer.id)
                connectionManager.sendFrame(contactId, completeFrame).isSuccess
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream file ${fileTransfer.fileName} to $contactId", e)
            false
        }
    }
}
