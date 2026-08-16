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

import android.content.Context
import android.net.Uri
import com.example.lanman.domain.security.StreamingEncryption
import com.example.lanman.util.network.ByteArrayPool
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.security.SecureRandom

@Singleton
class MessageDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
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
            val isSuccess = if (message.type == com.example.lanman.domain.model.MessageType.FILE || message.type == com.example.lanman.domain.model.MessageType.VOICE) {
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
        
        // 1. Resolve Key Material (32 bytes AES key + 12 bytes base nonce)
        val keyFile = File(File(context.filesDir, "transfer_keys"), fileTransfer.id)
        val keyAndNonce = ByteArray(44)
        if (keyFile.exists() && keyFile.length() >= 44) {
            FileInputStream(keyFile).use { it.read(keyAndNonce) }
        } else {
            // Generate on the fly if key file missing
            val secureRandom = SecureRandom()
            secureRandom.nextBytes(keyAndNonce)
        }
        
        val aesKey = keyAndNonce.copyOfRange(0, 32)
        val baseNonce = keyAndNonce.copyOfRange(32, 44)

        // 2. Open input stream from content URI or absolute file path
        val inputStream: InputStream = try {
            if (fileTransfer.localUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(fileTransfer.localUri))
                    ?: throw IllegalArgumentException("Could not open content URI: ${fileTransfer.localUri}")
            } else {
                val f = File(fileTransfer.localUri)
                if (!f.exists()) throw IllegalArgumentException("Local file does not exist: ${fileTransfer.localUri}")
                FileInputStream(f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open source file for transfer ${fileTransfer.id}", e)
            return false
        }

        return try {
            inputStream.use { sourceStream ->
                // Encrypt envelope with Signal session
                val envelope = encryptionEngine.encrypt(contactId, keyAndNonce)
                
                val chunkSize = StreamingEncryption.CHUNK_SIZE
                val totalChunks = if (fileTransfer.fileSizeBytes > 0) {
                    ((fileTransfer.fileSizeBytes + chunkSize - 1) / chunkSize).toInt()
                } else 0
                
                // 3. Send FileOffer over Control Socket
                val offerFrame = Frame.FileOfferFrame(
                    messageId = message.id,
                    fileTransferId = fileTransfer.id,
                    fileName = fileTransfer.fileName,
                    fileSizeBytes = fileTransfer.fileSizeBytes,
                    mimeType = fileTransfer.mimeType,
                    chunkSize = chunkSize,
                    totalChunks = totalChunks,
                    encryptedKeyEnvelope = envelope,
                    senderTimestamp = message.timestamp
                )
                
                if (connectionManager.sendFrame(contactId, offerFrame).isFailure) return false
                
                // 4. Stream Chunks over High-Speed Data Channel using ByteArrayPool
                val readBuffer = ByteArrayPool.acquireChunkBuffer()
                var chunkIndex = 0
                var bytesSent = 0L

                try {
                    while (true) {
                        var totalRead = 0
                        while (totalRead < chunkSize) {
                            val bytesRead = sourceStream.read(readBuffer, totalRead, chunkSize - totalRead)
                            if (bytesRead == -1) break
                            totalRead += bytesRead
                        }
                        
                        if (totalRead == 0) break // EOF

                        val chunkCiphertext = StreamingEncryption.encryptChunk(
                            plaintext = readBuffer,
                            offset = 0,
                            length = totalRead,
                            chunkIndex = chunkIndex,
                            aesKey = aesKey,
                            baseNonce = baseNonce
                        )

                        val chunkFrame = Frame.FileChunkFrame(
                            fileTransferId = fileTransfer.id,
                            chunkIndex = chunkIndex,
                            data = chunkCiphertext
                        )

                        if (connectionManager.sendDataFrame(contactId, chunkFrame, flush = false).isFailure) {
                            return false
                        }

                        bytesSent += totalRead
                        progressTracker.updateProgress(fileTransfer.id, bytesSent.coerceAtMost(fileTransfer.fileSizeBytes), fileTransfer.fileSizeBytes)

                        chunkIndex++
                    }
                } finally {
                    ByteArrayPool.releaseChunkBuffer(readBuffer)
                }

                // 5. Send FileComplete over Data Channel
                val completeFrame = Frame.FileCompleteFrame(fileTransfer.id)
                val success = connectionManager.sendDataFrame(contactId, completeFrame, flush = true).isSuccess
                if (success) {
                    progressTracker.updateProgress(fileTransfer.id, fileTransfer.fileSizeBytes, fileTransfer.fileSizeBytes)
                }
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream file ${fileTransfer.fileName} to $contactId on-the-fly", e)
            false
        }
    }
}
