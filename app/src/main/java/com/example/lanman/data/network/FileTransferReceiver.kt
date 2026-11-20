package com.example.lanman.data.network

import android.content.Context
import android.util.Log
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.FileTransferProgressTracker
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.repository.FileTransferRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.security.EncryptionEngine
import com.example.lanman.domain.security.StreamingEncryption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

import com.example.lanman.util.network.NetworkConstants

@Singleton
class FileTransferReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager,
    private val encryptionEngine: EncryptionEngine,
    private val messageRepository: MessageRepository,
    private val fileTransferRepository: FileTransferRepository,
    private val progressTracker: FileTransferProgressTracker
) {
    private val TAG = "FileTransferReceiver"
    private var job: Job? = null

    data class ActiveTransferState(
        val messageId: String,
        val contactId: String,
        val fileName: String,
        val fileSizeBytes: Long,
        val mimeType: String,
        val aesKey: ByteArray,
        val baseNonce: ByteArray,
        val outputStream: BufferedOutputStream,
        /** Hidden staging file that is only written while chunks are arriving. */
        val partialFile: File,
        /** Final, openable path. Published to the DB only once the transfer is verified. */
        val finalFile: File,
        val senderTimestamp: Long,
        var bytesReceived: Long = 0,
        /** Updated on every chunk; used by the watchdog to detect a vanished sender. */
        var lastActivityAt: Long = System.currentTimeMillis()
    )

    private val activeTransfers = ConcurrentHashMap<String, ActiveTransferState>()

    fun start(scope: CoroutineScope) {
        if (job != null) return

        job = scope.launch(Dispatchers.IO) {
            // Anything left in the staging directory belongs to a previous process: the
            // in-memory transfer state is gone, so those files can never be completed.
            sweepOrphanedPartials()

            // Fails transfers whose sender stopped sending mid-stream.
            launch { monitorStalledTransfers() }

            connectionManager.incomingFrames.collect { event ->
                val contactId = event.contactId
                val frame = event.frame
                when (frame) {
                    is Frame.FileOfferFrame -> handleFileOffer(contactId, frame)
                    is Frame.FileChunkFrame -> handleFileChunk(contactId, frame)
                    is Frame.FileCompleteFrame -> handleFileComplete(contactId, frame)
                    else -> {} // Handled by IncomingMessageReceiver or ignored
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null

        // Close all open streams and discard the unfinished files
        activeTransfers.entries.forEach { (transferId, state) ->
            discardTransfer(transferId, state)
        }
        activeTransfers.clear()
    }

    private suspend fun handleFileOffer(contactId: String, frame: Frame.FileOfferFrame) {
        // Decrypt envelope to get AES key and base nonce
        val decryptResult = encryptionEngine.decrypt(contactId, frame.encryptedKeyEnvelope)
        if (decryptResult.isFailure) {
            Log.e(TAG, "Failed to decrypt FileOffer envelope for transfer ${frame.fileTransferId}", decryptResult.exceptionOrNull())
            return
        }

        val keyAndNonce = decryptResult.getOrThrow()
        if (keyAndNonce.size != 44) {
            Log.e(TAG, "Decrypted envelope length is not 44 bytes, it is ${keyAndNonce.size}")
            return
        }

        val aesKey = keyAndNonce.copyOfRange(0, 32)
        val baseNonce = keyAndNonce.copyOfRange(32, 44)

        val receivedDir = File(context.filesDir, "received_files")
        if (!receivedDir.exists()) receivedDir.mkdirs()

        // Never write to the peer-supplied name directly: two transfers can share a name
        // (every voice note is "Voice Message"), and a name can contain path separators.
        val displayName = safeDisplayName(frame.fileName)
        val finalFile = uniqueDestination(receivedDir, frame.fileTransferId, frame.fileName)

        // The sender keeps retrying until it sees an ACK, so the same offer can arrive again
        // after we already finished. Re-acknowledge it instead of re-downloading (and
        // briefly truncating) a file that is already complete on disk.
        if (frame.fileSizeBytes > 0 &&
            finalFile.isFile &&
            finalFile.length() == frame.fileSizeBytes &&
            messageRepository.getMessageById(frame.messageId) != null
        ) {
            Log.d(TAG, "Transfer ${frame.fileTransferId} already complete, re-acknowledging")
            // The DB row may have lost its path (or the ACK was lost before it was written),
            // so restore the openable location before announcing delivery again.
            fileTransferRepository.updateLocalUri(frame.fileTransferId, finalFile.absolutePath)
            sendAck(contactId, frame.messageId)
            messageRepository.updateMessageStatus(frame.messageId, MessageStatus.DELIVERED)
            progressTracker.clearProgress(frame.fileTransferId)
            return
        }

        // A repeated offer for a still-running transfer replaces the old attempt.
        activeTransfers.remove(frame.fileTransferId)?.let { discardTransfer(frame.fileTransferId, it) }

        val partialDir = File(receivedDir, PARTIAL_DIR_NAME)
        if (!partialDir.exists()) partialDir.mkdirs()

        val partialFile = File(partialDir, "${frame.fileTransferId}.part")
        if (partialFile.exists()) partialFile.delete()

        val outputStream = BufferedOutputStream(FileOutputStream(partialFile), NetworkConstants.FILE_STREAM_BUFFER_SIZE)

        activeTransfers[frame.fileTransferId] = ActiveTransferState(
            messageId = frame.messageId,
            contactId = contactId,
            fileName = displayName,
            fileSizeBytes = frame.fileSizeBytes,
            mimeType = frame.mimeType,
            aesKey = aesKey,
            baseNonce = baseNonce,
            outputStream = outputStream,
            partialFile = partialFile,
            finalFile = finalFile,
            senderTimestamp = frame.senderTimestamp
        )

        val isVoice = frame.mimeType.startsWith("audio/lanman-voice")
        val messageType = if (isVoice) MessageType.VOICE else MessageType.FILE

        var messageContent = "File: $displayName".toByteArray(Charsets.UTF_8)
        if (isVoice) {
            // Extract duration from "audio/lanman-voice;duration=12400"
            val durationMs = frame.mimeType.substringAfter("duration=", "0")
            messageContent = "voice:$durationMs".toByteArray(Charsets.UTF_8)
        }

        // Insert PENDING message so UI can show progress circle
        val message = Message(
            id = frame.messageId,
            contactId = contactId,
            direction = MessageDirection.INCOMING,
            type = messageType,
            content = messageContent,
            status = MessageStatus.PENDING,
            timestamp = frame.senderTimestamp
        )

        // localUri stays empty until the file is verified and moved into place, so nothing
        // (UI, share sheet, Coil) can open a half-written file.
        val fileTransfer = com.example.lanman.domain.model.FileTransfer(
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

        Log.d(TAG, "Started receiving file transfer ${frame.fileTransferId}: ${frame.fileName}")
    }

    private suspend fun handleFileChunk(contactId: String, frame: Frame.FileChunkFrame) {
        val state = activeTransfers[frame.fileTransferId]
        if (state == null) {
            Log.w(TAG, "Received chunk for unknown transfer ${frame.fileTransferId}")
            return
        }

        try {
            val plaintext = StreamingEncryption.decryptChunk(frame.data, frame.chunkIndex, state.aesKey, state.baseNonce)
            state.outputStream.write(plaintext)

            state.bytesReceived += plaintext.size
            state.lastActivityAt = System.currentTimeMillis()
            progressTracker.updateProgress(frame.fileTransferId, state.bytesReceived, state.fileSizeBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt/write chunk ${frame.chunkIndex} for transfer ${frame.fileTransferId}", e)
            activeTransfers.remove(frame.fileTransferId)?.let { failTransfer(frame.fileTransferId, it) }
        }
    }

    private suspend fun handleFileComplete(contactId: String, frame: Frame.FileCompleteFrame) {
        val state = activeTransfers.remove(frame.fileTransferId)
        if (state == null) {
            Log.w(TAG, "Received complete for unknown transfer ${frame.fileTransferId}")
            return
        }

        try {
            state.outputStream.flush()
            state.outputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush transfer ${frame.fileTransferId}, discarding partial file", e)
            failTransfer(frame.fileTransferId, state)
            return
        }

        // Verify size when the sender declared one (0 means unknown, e.g. a provider that
        // does not report OpenableColumns.SIZE). Chunks and this frame share one ordered
        // stream, so reaching this point already means every chunk was written.
        val receivedBytes = state.partialFile.length()
        if (state.fileSizeBytes > 0 && receivedBytes != state.fileSizeBytes) {
            Log.w(TAG, "File size mismatch for transfer ${frame.fileTransferId}. Expected ${state.fileSizeBytes}, got $receivedBytes")
            failTransfer(frame.fileTransferId, state)
            return
        }

        try {
            // Move the finished file into place atomically: the final name only ever exists
            // once the content is complete, so a tap can never open a partial file.
            val finalFile = state.finalFile
            if (finalFile.parentFile?.exists() == false) finalFile.parentFile?.mkdirs()
            if (finalFile.exists()) finalFile.delete()

            if (!state.partialFile.renameTo(finalFile)) {
                state.partialFile.copyTo(finalFile, overwrite = true)
                state.partialFile.delete()
            }

            // Publish the openable path before announcing delivery.
            fileTransferRepository.updateLocalUri(frame.fileTransferId, finalFile.absolutePath)

            // Send ACK back for the whole file
            sendAck(contactId, state.messageId)

            // Mark message as DELIVERED
            messageRepository.updateMessageStatus(state.messageId, MessageStatus.DELIVERED)

            progressTracker.clearProgress(frame.fileTransferId)
            Log.d(TAG, "Successfully received and persisted complete file transfer ${frame.fileTransferId}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete transfer ${frame.fileTransferId}", e)
            failTransfer(frame.fileTransferId, state)
        }
    }

    private suspend fun sendAck(contactId: String, messageId: String) {
        val result = connectionManager.sendFrame(contactId, Frame.AckFrame(messageId))
        if (result.isFailure) {
            Log.w(TAG, "Failed to send ACK for $messageId to $contactId", result.exceptionOrNull())
        }
    }

    /** Strip any directory components and control characters from a peer-supplied name. */
    private fun safeDisplayName(originalName: String): String {
        return originalName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { !it.isISOControl() }
            .trim()
            .take(120)
            .ifBlank { "file" }
    }

    /** Unique, sanitized on-disk destination so transfers never overwrite each other. */
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
            if (file.isFile && file.delete()) {
                Log.d(TAG, "Deleted orphaned partial transfer ${file.name}")
            }
        }
    }

    /**
     * Terminal failure: drop the incomplete file and stop the progress UI. The row is kept so
     * the card can show "failed" instead of spinning forever; a retried offer resets it.
     */
    private suspend fun failTransfer(transferId: String, state: ActiveTransferState) {
        discardTransfer(transferId, state)
        messageRepository.updateMessageStatusIfCurrent(
            state.messageId,
            MessageStatus.PENDING,
            MessageStatus.FAILED
        )
    }

    /**
     * Watchdog for a sender that disappears mid-transfer: no chunks means no frames to react
     * to, so without this the card would show a frozen progress ring indefinitely.
     */
    private suspend fun CoroutineScope.monitorStalledTransfers() {
        while (isActive) {
            delay(STALL_CHECK_INTERVAL_MS)
            val now = System.currentTimeMillis()
            activeTransfers.entries.forEach { (transferId, state) ->
                val idleMs = now - state.lastActivityAt
                if (idleMs <= STALL_TIMEOUT_MS) return@forEach
                if (!activeTransfers.remove(transferId, state)) return@forEach
                Log.w(TAG, "Transfer $transferId stalled for ${idleMs} ms; marking as failed")
                failTransfer(transferId, state)
            }
        }
    }

    private fun discardTransfer(transferId: String, state: ActiveTransferState) {
        try { state.outputStream.close() } catch (e: Exception) {}
        try { if (state.partialFile.exists()) state.partialFile.delete() } catch (e: Exception) {}
        progressTracker.clearProgress(transferId)
    }

    fun cancelTransfersForContact(contactId: String) {
        // The contact is gone: drop the files without flagging them as failed transfers.
        activeTransfers.filterValues { it.contactId == contactId }.keys.forEach { transferId ->
            activeTransfers.remove(transferId)?.let { discardTransfer(transferId, it) }
        }
    }

    private companion object {
        /** Hidden staging directory; files here are never openable or shared. */
        const val PARTIAL_DIR_NAME = ".incomplete"

        /** No chunk for this long means the sender is gone. */
        const val STALL_TIMEOUT_MS = 20_000L
        const val STALL_CHECK_INTERVAL_MS = 5_000L
    }
}
