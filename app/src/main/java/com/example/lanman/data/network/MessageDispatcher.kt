package com.example.lanman.data.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.lanman.data.security.SecureKeyStore
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.FileTransferProgressTracker
import com.example.lanman.domain.network.NetworkDiscoveryManager
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.repository.FileTransferRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.domain.security.EncryptionEngine
import com.example.lanman.domain.security.StreamingEncryption
import com.example.lanman.util.network.ByteArrayPool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkDiscoveryManager: NetworkDiscoveryManager,
    private val connectionManager: ConnectionManager,
    private val messageRepository: MessageRepository,
    private val profileRepository: ProfileRepository,
    private val fileTransferRepository: FileTransferRepository,
    private val progressTracker: FileTransferProgressTracker,
    private val encryptionEngine: EncryptionEngine,
    private val secureKeyStore: SecureKeyStore
) {
    private val TAG = "MessageDispatcher"
    private var job: Job? = null

    private val flushingContacts = ConcurrentHashMap<String, Boolean>()
    private val lastSentAttempt = ConcurrentHashMap<String, Long>()
    private val sendFailures = ConcurrentHashMap<String, Int>()
    private val lastReconnectAttempt = ConcurrentHashMap<String, Long>()
    private val cancelledTransfers = ConcurrentHashMap<String, Boolean>()
    private val activeFileSendContact = ConcurrentHashMap<String, Boolean>()

    private companion object {
        const val RETRY_INTERVAL_MS = 5_000L
        const val CONNECTION_STALE_MS = 15_000L
        const val FILE_CONNECTION_STALE_MS = 45_000L
        const val RECONNECT_INTERVAL_MS = 30_000L
        const val MAX_SEND_FAILURES = 6
    }

    fun start(scope: CoroutineScope) {
        if (job != null) return

        job = scope.launch(Dispatchers.IO) {
            val myId = profileRepository.getProfile().firstOrNull { it != null }?.id ?: return@launch

            launch {
                messageRepository.getContactsWithUnacknowledgedMessagesFlow().collect { contactIds ->
                    contactIds.forEach { contactId -> scheduleFlush(myId, contactId) }
                }
            }

            launch {
                networkDiscoveryManager.discoveredPeers.collect { peer ->
                    scheduleFlush(myId, peer.id)
                }
            }

            launch {
                while (isActive) {
                    delay(RETRY_INTERVAL_MS)
                    retryUnacknowledged(myId)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        flushingContacts.clear()
        lastSentAttempt.clear()
        lastReconnectAttempt.clear()
        sendFailures.clear()
        cancelledTransfers.clear()
        activeFileSendContact.clear()
    }

    /** Immediately flags an outgoing file or voice transfer as cancelled so streaming aborts mid-flight. */
    fun cancelTransfer(messageId: String) {
        cancelledTransfers[messageId] = true
    }

    private suspend fun CoroutineScope.retryUnacknowledged(myId: String) {
        val contactIds = try {
            messageRepository.getContactsWithUnacknowledgedMessagesFlow().first()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query contacts with unacknowledged messages", e)
            return
        }
        if (contactIds.isEmpty()) return

        val now = System.currentTimeMillis()
        for (contactId in contactIds) {
            if (activeFileSendContact.containsKey(contactId)) {
                continue // Never drop a socket while a file transfer is actively streaming chunks
            }

            val messages = try {
                messageRepository.getUnacknowledgedMessagesForContact(contactId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load unacknowledged messages for $contactId", e)
                continue
            }
            if (messages.isEmpty()) continue

            val sentMessages = messages.filter { it.status == MessageStatus.SENT }
            val oldestSilentWrite = sentMessages.mapNotNull { lastSentAttempt[it.id] }.minOrNull()
            val hasFileInFlight = sentMessages.any { it.type != MessageType.TEXT }
            val staleThreshold = if (hasFileInFlight) FILE_CONNECTION_STALE_MS else CONNECTION_STALE_MS

            if (oldestSilentWrite != null &&
                now - oldestSilentWrite > staleThreshold &&
                now - (lastReconnectAttempt[contactId] ?: 0L) > RECONNECT_INTERVAL_MS
            ) {
                Log.w(TAG, "No ACK from $contactId for ${now - oldestSilentWrite} ms; reconnecting")
                lastReconnectAttempt[contactId] = now
                connectionManager.disconnectFrom(contactId)
                messages.forEach { lastSentAttempt.remove(it.id) }
            }

            scheduleFlush(myId, contactId)
        }
    }

    private fun CoroutineScope.scheduleFlush(myId: String, contactId: String) {
        if (flushingContacts.putIfAbsent(contactId, true) != null) return
        launch {
            try {
                flushUnacknowledgedMessages(myId, contactId)
            } finally {
                flushingContacts.remove(contactId)
            }
        }
    }

    private suspend fun flushUnacknowledgedMessages(myId: String, contactId: String) {
        val messages = messageRepository.getUnacknowledgedMessagesForContact(contactId)
        if (messages.isEmpty()) return

        messages.forEach { message ->
            if (message.status == MessageStatus.PENDING) {
                cancelledTransfers.remove(message.id)
                if ((sendFailures[message.id] ?: 0) >= MAX_SEND_FAILURES) {
                    sendFailures.remove(message.id)
                    lastSentAttempt.remove(message.id)
                }
            }
        }

        val now = System.currentTimeMillis()
        // Flush TEXT messages before FILE/VOICE messages so large transfers never head-of-line block chats
        val messagesToSend = messages.filter { message ->
            val failures = sendFailures[message.id] ?: 0
            val lastAttempt = lastSentAttempt[message.id] ?: 0L
            val backoffMs = if (failures == 0) {
                if (message.status == MessageStatus.PENDING) 0L else 10_000L
            } else {
                (10_000L * Math.pow(2.0, (failures - 1).coerceAtMost(6).toDouble())).toLong()
            }
            (now - lastAttempt) > backoffMs
        }.sortedBy { if (it.type == MessageType.TEXT) 0 else 1 }

        if (messagesToSend.isEmpty()) return

        Log.d(TAG, "Flushing ${messagesToSend.size} unacknowledged messages for $contactId")

        for (message in messagesToSend) {
            if (cancelledTransfers.remove(message.id) == true ||
                messageRepository.getMessageById(message.id)?.status == MessageStatus.FAILED
            ) {
                continue
            }

            val isSuccess = if (message.type == MessageType.FILE || message.type == MessageType.VOICE) {
                activeFileSendContact[contactId] = true
                try {
                    sendFileMessage(contactId, message)
                } finally {
                    activeFileSendContact.remove(contactId)
                }
            } else {
                sendTextMessage(myId, contactId, message)
            }

            lastSentAttempt[message.id] = System.currentTimeMillis()

            if (cancelledTransfers.remove(message.id) == true ||
                messageRepository.getMessageById(message.id)?.status == MessageStatus.FAILED
            ) {
                Log.d(TAG, "Transfer ${message.id} was cancelled by user")
                continue
            }

            if (isSuccess) {
                sendFailures.remove(message.id)
                messageRepository.updateMessageStatusIfCurrent(
                    message.id,
                    MessageStatus.PENDING,
                    MessageStatus.SENT
                )
                Log.d(TAG, "Successfully sent message ${message.id} to $contactId")
            } else {
                val newFailures = (sendFailures[message.id] ?: 0) + 1
                sendFailures[message.id] = newFailures

                if (newFailures >= MAX_SEND_FAILURES) {
                    messageRepository.updateMessageStatus(message.id, MessageStatus.FAILED)
                    Log.e(TAG, "Message ${message.id} to $contactId failed $newFailures times. Marked as FAILED.")
                } else {
                    Log.w(TAG, "Failed to send message ${message.id} (failure $newFailures). Pausing queue.")
                }
                break
            }
        }
    }

    private suspend fun sendTextMessage(myId: String, contactId: String, message: Message): Boolean {
        val ciphertext = try {
            encryptionEngine.encrypt(contactId, message.content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt message ${message.id}: ${e.message}", e)
            return false
        }

        val frame = Frame.MessageFrame(
            messageId = message.id,
            senderId = myId,
            messageType = 0,
            timestamp = message.timestamp,
            content = ciphertext
        )

        return connectionManager.sendFrame(contactId, frame).isSuccess
    }

    private suspend fun sendFileMessage(contactId: String, message: Message): Boolean {
        val fileTransfer = fileTransferRepository.getByMessageId(message.id).firstOrNull() ?: run {
            Log.e(TAG, "FileTransfer not found for message ${message.id}")
            return false
        }

        val storedKey = secureKeyStore.getTransferKey(fileTransfer.id)
        val keyFile = File(File(context.filesDir, "transfer_keys"), fileTransfer.id)
        val keyAndNonce = when {
            storedKey != null && storedKey.size == 44 -> storedKey
            keyFile.exists() && keyFile.length() >= 44 -> {
                ByteArray(44).also { buf -> FileInputStream(keyFile).use { it.read(buf) } }
            }
            else -> ByteArray(44).also { SecureRandom().nextBytes(it) }
        }

        val aesKey = keyAndNonce.copyOfRange(0, 32)
        val baseNonce = keyAndNonce.copyOfRange(32, 44)

        val inputStream: InputStream = try {
            if (fileTransfer.localUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(fileTransfer.localUri))
                    ?: throw IllegalArgumentException("Could not open content URI: ${fileTransfer.localUri}")
            } else {
                val f = File(fileTransfer.localUri)
                if (!f.exists()) throw IllegalArgumentException("Local file does not exist")
                FileInputStream(f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open source file for transfer ${fileTransfer.id}", e)
            return false
        }

        return try {
            inputStream.use { sourceStream ->
                val envelope = encryptionEngine.encrypt(contactId, keyAndNonce)
                val chunkSize = StreamingEncryption.CHUNK_SIZE
                val totalChunks = if (fileTransfer.fileSizeBytes > 0) {
                    ((fileTransfer.fileSizeBytes + chunkSize - 1) / chunkSize).toInt()
                } else 0

                // Send FileOfferFrame on the Data Channel so Offer + Chunks + Hash + Complete share
                // one ordered TCP stream (sendDataFrame automatically falls back to Control if needed).
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

                if (connectionManager.sendDataFrame(contactId, offerFrame, flush = true).isFailure) {
                    return false
                }

                val sha256 = MessageDigest.getInstance("SHA-256")
                val readBuffer = ByteArrayPool.acquireChunkBuffer()
                var chunkIndex = 0
                var bytesSent = 0L

                try {
                    while (true) {
                        if (cancelledTransfers.containsKey(message.id) ||
                            messageRepository.getMessageById(message.id)?.status == MessageStatus.FAILED
                        ) {
                            progressTracker.clearProgress(fileTransfer.id)
                            return false
                        }

                        var totalRead = 0
                        while (totalRead < chunkSize) {
                            val bytesRead = sourceStream.read(readBuffer, totalRead, chunkSize - totalRead)
                            if (bytesRead == -1) break
                            totalRead += bytesRead
                        }

                        if (totalRead == 0) break

                        sha256.update(readBuffer, 0, totalRead)

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
                        progressTracker.updateProgress(
                            fileTransfer.id,
                            bytesSent.coerceAtMost(fileTransfer.fileSizeBytes.coerceAtLeast(bytesSent)),
                            fileTransfer.fileSizeBytes.coerceAtLeast(bytesSent)
                        )

                        chunkIndex++
                    }
                } finally {
                    ByteArrayPool.releaseChunkBuffer(readBuffer)
                }

                val hashFrame = Frame.FileHashFrame(fileTransfer.id, sha256.digest())
                if (connectionManager.sendDataFrame(contactId, hashFrame, flush = false).isFailure) {
                    return false
                }

                val completeFrame = Frame.FileCompleteFrame(fileTransfer.id)
                val success = connectionManager.sendDataFrame(contactId, completeFrame, flush = true).isSuccess
                if (success) {
                    progressTracker.updateProgress(
                        fileTransfer.id,
                        fileTransfer.fileSizeBytes.coerceAtLeast(bytesSent),
                        fileTransfer.fileSizeBytes.coerceAtLeast(bytesSent)
                    )
                }
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream file transfer ${fileTransfer.id}", e)
            false
        }
    }
}
