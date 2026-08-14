package com.example.lanman.data.network

import android.content.Context
import android.util.Log
import com.example.lanman.domain.model.FileTransfer
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
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
        val outputStream: FileOutputStream,
        val destinationFile: File,
        val senderTimestamp: Long,
        var bytesReceived: Long = 0
    )
    
    private val activeTransfers = ConcurrentHashMap<String, ActiveTransferState>()

    fun start(scope: CoroutineScope) {
        if (job != null) return
        
        job = scope.launch(Dispatchers.IO) {
            connectionManager.incomingFrames.collect { (contactId, frame) ->
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
        
        // Close all open streams
        activeTransfers.values.forEach { 
            try { it.outputStream.close() } catch (e: Exception) {} 
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
        
        // Prepare output file
        val receivedDir = File(context.filesDir, "received_files")
        if (!receivedDir.exists()) receivedDir.mkdirs()
        
        val destinationFile = File(receivedDir, frame.fileName) // TODO: Handle filename collisions securely in future
        val outputStream = FileOutputStream(destinationFile)
        
        activeTransfers[frame.fileTransferId] = ActiveTransferState(
            messageId = frame.messageId,
            contactId = contactId,
            fileName = frame.fileName,
            fileSizeBytes = frame.fileSizeBytes,
            mimeType = frame.mimeType,
            aesKey = aesKey,
            baseNonce = baseNonce,
            outputStream = outputStream,
            destinationFile = destinationFile,
            senderTimestamp = frame.senderTimestamp
        )
        
        // Insert PENDING message so UI can show progress circle
        val message = com.example.lanman.domain.model.Message(
            id = frame.messageId,
            contactId = contactId,
            direction = com.example.lanman.domain.model.MessageDirection.INCOMING,
            type = com.example.lanman.domain.model.MessageType.FILE,
            content = "File: ${frame.fileName}".toByteArray(Charsets.UTF_8),
            status = com.example.lanman.domain.model.MessageStatus.PENDING,
            timestamp = frame.senderTimestamp
        )
        
        val fileTransfer = com.example.lanman.domain.model.FileTransfer(
            id = frame.fileTransferId,
            messageId = frame.messageId,
            fileName = frame.fileName,
            fileSizeBytes = frame.fileSizeBytes,
            mimeType = frame.mimeType,
            localUri = destinationFile.absolutePath,
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
            progressTracker.updateProgress(frame.fileTransferId, state.bytesReceived, state.fileSizeBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt/write chunk ${frame.chunkIndex} for transfer ${frame.fileTransferId}", e)
            cleanupTransfer(frame.fileTransferId)
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
            
            // Verify size
            if (state.destinationFile.length() != state.fileSizeBytes) {
                Log.w(TAG, "File size mismatch for transfer ${frame.fileTransferId}. Expected ${state.fileSizeBytes}, got ${state.destinationFile.length()}")
                // Clean up partial file, do not save to DB
                state.destinationFile.delete()
                return
            }
            
            // Send ACK back for the whole file
            connectionManager.sendFrame(contactId, Frame.AckFrame(state.messageId))
            
            // Mark message as DELIVERED
            messageRepository.updateMessageStatus(state.messageId, com.example.lanman.domain.model.MessageStatus.DELIVERED)
            
            Log.d(TAG, "Successfully received and persisted complete file transfer ${frame.fileTransferId}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete transfer ${frame.fileTransferId}", e)
            state.destinationFile.delete()
        }
    }
    
    private fun cleanupTransfer(transferId: String) {
        val state = activeTransfers.remove(transferId) ?: return
        try { state.outputStream.close() } catch (e: Exception) {}
        try { state.destinationFile.delete() } catch (e: Exception) {}
    }
    
    fun cancelTransfersForContact(contactId: String) {
        val transfersToCancel = activeTransfers.filterValues { it.contactId == contactId }.keys
        transfersToCancel.forEach { cleanupTransfer(it) }
    }
}
