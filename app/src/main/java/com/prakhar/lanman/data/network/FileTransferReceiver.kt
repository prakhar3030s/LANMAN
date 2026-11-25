package com.prakhar.lanman.data.network

import android.content.Context
import android.util.Log
import com.prakhar.lanman.data.security.BlockedContactsStore
import com.prakhar.lanman.domain.model.FileTransfer
import com.prakhar.lanman.domain.model.Message
import com.prakhar.lanman.domain.model.MessageDirection
import com.prakhar.lanman.domain.model.MessageStatus
import com.prakhar.lanman.domain.model.MessageType
import com.prakhar.lanman.domain.network.ConnectionManager
import com.prakhar.lanman.domain.network.FileTransferProgressTracker
import com.prakhar.lanman.domain.network.protocol.Frame
import com.prakhar.lanman.domain.repository.ContactRepository
import com.prakhar.lanman.domain.repository.FileTransferRepository
import com.prakhar.lanman.domain.repository.MessageRepository
import com.prakhar.lanman.domain.security.EncryptionEngine
import com.prakhar.lanman.domain.security.StreamingEncryption
import com.prakhar.lanman.util.network.NetworkConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileTransferReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager,
    private val encryptionEngine: EncryptionEngine,
    private val messageRepository: MessageRepository,
    private val fileTransferRepository: FileTransferRepository,
    private val progressTracker: FileTransferProgressTracker,
    private val contactRepository: ContactRepository,
    private val blockedContactsStore: BlockedContactsStore
) {
    private val TAG = "FileTransferReceiver"
    private var job: Job? = null

    data class ActiveTransferState(
        val messageId: String,
        val contactId: String,
        val fileName: String,
        val fileSizeBytes: Long,
        val expectedTotalChunks: Int,
        val mimeType: String,
        val aesKey: ByteArray,
        val baseNonce: ByteArray,
        val outputStream: BufferedOutputStream,
        val partialFile: File,
        val finalFile: File,
        val senderTimestamp: Long,
        val sha256: MessageDigest = MessageDigest.getInstance("SHA-256"),
        var expectedChunkIndex: Int = 0,
        var bytesReceived: Long = 0,
        var expectedSha256: ByteArray? = null,
        var lastActivityAt: Long = System.currentTimeMillis()
    )

    private class EarlyFrameBuffer(
        val contactId: String,
        val createdAt: Long = System.currentTimeMillis(),
        val chunks: MutableList<Frame.FileChunkFrame> = mutableListOf(),
        var hashFrame: Frame.FileHashFrame? = null,
        var completeFrame: Frame.FileCompleteFrame? = null,
        var bufferedBytes: Int = 0
    )

    private val activeTransfers = ConcurrentHashMap<String, ActiveTransferState>()
    private val earlyBuffers = ConcurrentHashMap<String, EarlyFrameBuffer>()

    fun start(scope: CoroutineScope) {
        if (job != null) return

        job = scope.launch(Dispatchers.IO) {
            sweepOrphanedPartials()

            launch { monitorStalledTransfers() }

            connectionManager.incomingFrames.collect { event ->
                try {
                    val contactId = event.contactId
                    val frame = event.frame
                    when (frame) {
                        is Frame.FileOfferFrame -> handleFileOffer(contactId, frame)
                        is Frame.FileChunkFrame -> handleFileChunk(contactId, frame)
                        is Frame.FileHashFrame -> handleFileHash(contactId, frame)
                        is Frame.FileCompleteFrame -> handleFileComplete(contactId, frame)
                        else -> {}
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing incoming file transfer frame", e)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null

        activeTransfers.entries.forEach { (transferId, state) ->
            discardTransfer(transferId, state)
        }
        activeTransfers.clear()
        earlyBuffers.clear()
    }

    private suspend fun handleFileOffer(contactId: String, frame: Frame.FileOfferFrame) {
        if (blockedContactsStore.isBlocked(contactId)) {
            Log.w(TAG, "Ignoring file offer from blocked contact")
            earlyBuffers.remove(frame.fileTransferId)
            return
        }
        if (contactRepository.getContactById(contactId).firstOrNull() == null) {
            Log.w(TAG, "Ignoring file offer from unknown/unpaired contact")
            earlyBuffers.remove(frame.fileTransferId)
            return
        }
        if (frame.fileSizeBytes < 0L || frame.fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            Log.w(TAG, "Rejecting file offer with invalid byte count: ${frame.fileSizeBytes}")
            earlyBuffers.remove(frame.fileTransferId)
            return
        }

        val decryptResult = encryptionEngine.decrypt(contactId, frame.encryptedKeyEnvelope)
        if (decryptResult.isFailure) {
            Log.e(TAG, "Failed to decrypt FileOffer envelope for transfer ${frame.fileTransferId}", decryptResult.exceptionOrNull())
            earlyBuffers.remove(frame.fileTransferId)
            return
        }

        val keyAndNonce = decryptResult.getOrThrow()
        if (keyAndNonce.size != 44) {
            Log.e(TAG, "Decrypted envelope length is not 44 bytes, it is ${keyAndNonce.size}")
            earlyBuffers.remove(frame.fileTransferId)
            return
        }

        val aesKey = keyAndNonce.copyOfRange(0, 32)
        val baseNonce = keyAndNonce.copyOfRange(32, 44)

        val receivedDir = File(context.filesDir, "received_files")
        if (!receivedDir.exists()) receivedDir.mkdirs()

        if (frame.fileSizeBytes > 0 && receivedDir.usableSpace < frame.fileSizeBytes + MIN_FREE_SPACE_BYTES) {
            Log.e(TAG, "Insufficient disk space for transfer ${frame.fileTransferId}")
            earlyBuffers.remove(frame.fileTransferId)
            return
        }

        val displayName = safeDisplayName(frame.fileName)
        val finalFile = uniqueDestination(receivedDir, frame.fileTransferId, frame.fileName)

        // If already delivered and verified on disk, re-acknowledge idempotently.
        val existingMessage = messageRepository.getMessageById(frame.messageId)
        if (existingMessage != null &&
            existingMessage.status != MessageStatus.FAILED &&
            finalFile.isFile &&
            (frame.fileSizeBytes == 0L || finalFile.length() == frame.fileSizeBytes)
        ) {
            Log.d(TAG, "Transfer ${frame.fileTransferId} already complete, re-acknowledging")
            earlyBuffers.remove(frame.fileTransferId)
            fileTransferRepository.updateLocalUri(frame.fileTransferId, finalFile.absolutePath)
            sendAck(contactId, frame.messageId)
            messageRepository.updateMessageStatus(frame.messageId, MessageStatus.DELIVERED)
            progressTracker.clearProgress(frame.fileTransferId)
            return
        }

        activeTransfers.remove(frame.fileTransferId)?.let { discardTransfer(frame.fileTransferId, it) }

        val partialDir = File(receivedDir, PARTIAL_DIR_NAME)
        if (!partialDir.exists()) partialDir.mkdirs()

        val partialFile = File(partialDir, "${frame.fileTransferId}.part")
        if (partialFile.exists()) partialFile.delete()

        val outputStream = BufferedOutputStream(FileOutputStream(partialFile), NetworkConstants.FILE_STREAM_BUFFER_SIZE)

        val newState = ActiveTransferState(
            messageId = frame.messageId,
            contactId = contactId,
            fileName = displayName,
            fileSizeBytes = frame.fileSizeBytes,
            expectedTotalChunks = frame.totalChunks,
            mimeType = frame.mimeType,
            aesKey = aesKey,
            baseNonce = baseNonce,
            outputStream = outputStream,
            partialFile = partialFile,
            finalFile = finalFile,
            senderTimestamp = frame.senderTimestamp
        )
        activeTransfers[frame.fileTransferId] = newState

        val isVoice = frame.mimeType.startsWith("audio/lanman-voice")
        val messageType = if (isVoice) MessageType.VOICE else MessageType.FILE

        val messageContent = if (isVoice) {
            val durationMs = frame.mimeType.substringAfter("duration=", "0")
            "voice:$durationMs".toByteArray(Charsets.UTF_8)
        } else {
            "File: $displayName".toByteArray(Charsets.UTF_8)
        }

        val message = Message(
            id = frame.messageId,
            contactId = contactId,
            direction = MessageDirection.INCOMING,
            type = messageType,
            content = messageContent,
            status = MessageStatus.PENDING,
            timestamp = frame.senderTimestamp
        )

        val fileTransfer = FileTransfer(
            id = frame.fileTransferId,
            messageId = frame.messageId,
            fileName = displayName,
            fileSizeBytes = frame.fileSizeBytes,
            mimeType = frame.mimeType,
            localUri = "",
            thumbnailUri = null
        )

        messageRepository.insertMessage(message)
        fileTransferRepository.insertFileTransfer(fileTransfer)

        Log.d(TAG, "Started receiving file transfer ${frame.fileTransferId}: $displayName")

        // Replay any early chunks that raced ahead of FileOfferFrame on the dual-channel transport.
        val early = earlyBuffers.remove(frame.fileTransferId)
        if (early != null && early.contactId == contactId) {
            early.chunks.sortedBy { it.chunkIndex }.forEach { chunk ->
                if (activeTransfers.containsKey(frame.fileTransferId)) {
                    handleFileChunk(contactId, chunk)
                }
            }
            early.hashFrame?.let { hash ->
                if (activeTransfers.containsKey(frame.fileTransferId)) {
                    handleFileHash(contactId, hash)
                }
            }
            early.completeFrame?.let { complete ->
                if (activeTransfers.containsKey(frame.fileTransferId)) {
                    handleFileComplete(contactId, complete)
                }
            }
        }
    }

    private suspend fun handleFileChunk(contactId: String, frame: Frame.FileChunkFrame) {
        val state = activeTransfers[frame.fileTransferId]
        if (state == null) {
            // Stage briefly in case FileOfferFrame on Control socket arrives right behind Data socket chunk 0.
            if (!blockedContactsStore.isBlocked(contactId) &&
                contactRepository.getContactById(contactId).firstOrNull() != null
            ) {
                val early = earlyBuffers.computeIfAbsent(frame.fileTransferId) { EarlyFrameBuffer(contactId) }
                if (early.contactId == contactId &&
                    early.bufferedBytes + frame.data.size <= MAX_EARLY_BUFFER_BYTES &&
                    early.chunks.size < MAX_EARLY_CHUNKS
                ) {
                    early.chunks.add(frame)
                    early.bufferedBytes += frame.data.size
                    return
                }
            }
            Log.w(TAG, "Received chunk for unknown transfer ${frame.fileTransferId}")
            return
        }

        if (state.contactId != contactId) {
            Log.w(TAG, "Rejected chunk from mismatched contact for transfer ${frame.fileTransferId}")
            return
        }

        if (frame.chunkIndex != state.expectedChunkIndex) {
            Log.e(
                TAG,
                "Out-of-order/duplicate chunk ${frame.chunkIndex} (expected ${state.expectedChunkIndex}) for ${frame.fileTransferId}"
            )
            activeTransfers.remove(frame.fileTransferId)?.let { failTransfer(frame.fileTransferId, it) }
            return
        }

        try {
            val plaintext = StreamingEncryption.decryptChunk(frame.data, frame.chunkIndex, state.aesKey, state.baseNonce)
            if (state.fileSizeBytes > 0 && state.bytesReceived + plaintext.size > state.fileSizeBytes) {
                Log.e(TAG, "Transfer ${frame.fileTransferId} exceeded declared size (${state.fileSizeBytes})")
                activeTransfers.remove(frame.fileTransferId)?.let { failTransfer(frame.fileTransferId, it) }
                return
            }

            state.outputStream.write(plaintext)
            state.sha256.update(plaintext)
            state.expectedChunkIndex++
            state.bytesReceived += plaintext.size
            state.lastActivityAt = System.currentTimeMillis()
            progressTracker.updateProgress(
                frame.fileTransferId,
                state.bytesReceived,
                state.fileSizeBytes.coerceAtLeast(state.bytesReceived)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt/write chunk ${frame.chunkIndex} for transfer ${frame.fileTransferId}", e)
            activeTransfers.remove(frame.fileTransferId)?.let { failTransfer(frame.fileTransferId, it) }
        }
    }

    private fun handleFileHash(contactId: String, frame: Frame.FileHashFrame) {
        val state = activeTransfers[frame.fileTransferId]
        if (state == null) {
            val early = earlyBuffers[frame.fileTransferId]
            if (early != null && early.contactId == contactId) {
                early.hashFrame = frame
            }
            return
        }
        if (state.contactId != contactId) return
        state.expectedSha256 = frame.sha256
        state.lastActivityAt = System.currentTimeMillis()
    }

    private suspend fun handleFileComplete(contactId: String, frame: Frame.FileCompleteFrame) {
        val state = activeTransfers.remove(frame.fileTransferId)
        if (state == null) {
            val early = earlyBuffers[frame.fileTransferId]
            if (early != null && early.contactId == contactId) {
                early.completeFrame = frame
                return
            }
            Log.w(TAG, "Received complete for unknown transfer ${frame.fileTransferId}")
            return
        }

        if (state.contactId != contactId) {
            Log.w(TAG, "Rejected FileComplete from mismatched contact for ${frame.fileTransferId}")
            failTransfer(frame.fileTransferId, state)
            return
        }

        try {
            state.outputStream.flush()
            state.outputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush transfer ${frame.fileTransferId}", e)
            failTransfer(frame.fileTransferId, state)
            return
        }

        if (state.expectedTotalChunks > 0 && state.expectedChunkIndex != state.expectedTotalChunks) {
            Log.w(
                TAG,
                "Chunk count mismatch for ${frame.fileTransferId}: expected ${state.expectedTotalChunks}, got ${state.expectedChunkIndex}"
            )
            failTransfer(frame.fileTransferId, state)
            return
        }

        val receivedBytes = state.partialFile.length()
        if (state.fileSizeBytes > 0 && receivedBytes != state.fileSizeBytes) {
            Log.w(TAG, "File size mismatch for ${frame.fileTransferId}. Expected ${state.fileSizeBytes}, got $receivedBytes")
            failTransfer(frame.fileTransferId, state)
            return
        }

        val expectedHash = state.expectedSha256
        if (expectedHash != null) {
            val actualHash = state.sha256.digest()
            if (!actualHash.contentEquals(expectedHash)) {
                Log.e(TAG, "SHA-256 integrity verification failed for ${frame.fileTransferId}")
                failTransfer(frame.fileTransferId, state)
                return
            }
        }

        try {
            val finalFile = state.finalFile
            if (finalFile.parentFile?.exists() == false) finalFile.parentFile?.mkdirs()
            if (finalFile.exists()) finalFile.delete()

            if (!state.partialFile.renameTo(finalFile)) {
                state.partialFile.copyTo(finalFile, overwrite = true)
                state.partialFile.delete()
            }

            fileTransferRepository.updateLocalUri(frame.fileTransferId, finalFile.absolutePath)
            sendAck(contactId, state.messageId)
            messageRepository.updateMessageStatus(state.messageId, MessageStatus.DELIVERED)
            progressTracker.clearProgress(frame.fileTransferId)
            Log.d(TAG, "Successfully received and verified complete file transfer ${frame.fileTransferId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete transfer ${frame.fileTransferId}", e)
            failTransfer(frame.fileTransferId, state)
        }
    }

    private suspend fun sendAck(contactId: String, messageId: String) {
        val result = connectionManager.sendFrame(contactId, Frame.AckFrame(messageId))
        if (result.isFailure) {
            Log.w(TAG, "Failed to send ACK for $messageId", result.exceptionOrNull())
        }
    }

    private fun safeDisplayName(originalName: String): String {
        return originalName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { !it.isISOControl() }
            .trim()
            .take(120)
            .ifBlank { "file" }
    }

    private fun uniqueDestination(receivedDir: File, transferId: String, originalName: String): File {
        val extension = safeDisplayName(originalName)
            .substringAfterLast('.', "")
            .takeIf { it.isNotEmpty() && it.length <= 10 && it.all { ch -> ch.isLetterOrDigit() } }
        val fileName = if (extension == null) transferId else "$transferId.$extension"
        return File(receivedDir, fileName)
    }

    private fun sweepOrphanedPartials() {
        val partialDir = File(File(context.filesDir, "received_files"), PARTIAL_DIR_NAME)
        if (!partialDir.isDirectory) return

        partialDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }
    }

    private suspend fun failTransfer(transferId: String, state: ActiveTransferState) {
        discardTransfer(transferId, state)
        messageRepository.updateMessageStatusIfCurrent(
            state.messageId,
            MessageStatus.PENDING,
            MessageStatus.FAILED
        )
    }

    private suspend fun CoroutineScope.monitorStalledTransfers() {
        while (isActive) {
            delay(STALL_CHECK_INTERVAL_MS)
            val now = System.currentTimeMillis()
            activeTransfers.entries.forEach { (transferId, state) ->
                val idleMs = now - state.lastActivityAt
                if (idleMs <= STALL_TIMEOUT_MS) return@forEach
                if (!activeTransfers.remove(transferId, state)) return@forEach
                Log.w(TAG, "Transfer $transferId stalled for $idleMs ms; marking as failed")
                failTransfer(transferId, state)
            }
            earlyBuffers.entries.forEach { (transferId, early) ->
                if (now - early.createdAt > EARLY_BUFFER_TIMEOUT_MS) {
                    earlyBuffers.remove(transferId, early)
                }
            }
        }
    }

    private fun discardTransfer(transferId: String, state: ActiveTransferState) {
        try { state.outputStream.close() } catch (_: Exception) {}
        try { if (state.partialFile.exists()) state.partialFile.delete() } catch (_: Exception) {}
        progressTracker.clearProgress(transferId)
    }

    fun cancelTransfersForContact(contactId: String) {
        activeTransfers.filterValues { it.contactId == contactId }.keys.forEach { transferId ->
            activeTransfers.remove(transferId)?.let { discardTransfer(transferId, it) }
        }
        earlyBuffers.filterValues { it.contactId == contactId }.keys.forEach { earlyBuffers.remove(it) }
    }

    private companion object {
        const val PARTIAL_DIR_NAME = ".incomplete"
        const val STALL_TIMEOUT_MS = 25_000L
        const val STALL_CHECK_INTERVAL_MS = 5_000L
        const val EARLY_BUFFER_TIMEOUT_MS = 5_000L
        const val MAX_EARLY_BUFFER_BYTES = 4 * 1024 * 1024 // 4 MB cap
        const val MAX_EARLY_CHUNKS = 8
        const val MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L * 1024L
        const val MIN_FREE_SPACE_BYTES = 50L * 1024L * 1024L
    }
}
