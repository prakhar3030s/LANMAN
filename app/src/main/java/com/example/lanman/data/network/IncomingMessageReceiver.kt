package com.example.lanman.data.network

import android.util.Log
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.repository.FileTransferRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.security.EncryptionEngine
import com.example.lanman.domain.network.ActiveConversationTracker
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.domain.repository.MessageRequestRepository
import com.example.lanman.domain.model.MessageRequest
import com.example.lanman.domain.network.IncomingFrameEvent
import com.example.lanman.data.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncomingMessageReceiver @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val messageRepository: MessageRepository,
    private val fileTransferRepository: FileTransferRepository,
    private val contactRepository: ContactRepository,
    private val encryptionEngine: EncryptionEngine,
    private val notificationHelper: NotificationHelper,
    private val activeConversationTracker: ActiveConversationTracker,
    private val profileRepository: ProfileRepository,
    private val messageRequestRepository: MessageRequestRepository
) {
    private val TAG = "IncomingMessageReceiver"
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        
        job = scope.launch(Dispatchers.IO) {
            connectionManager.incomingFrames.collect { event ->
                val contactId = event.contactId
                val frame = event.frame
                when (frame) {
                    is Frame.MessageFrame -> handleMessageFrame(event)
                    is Frame.AckFrame -> handleAckFrame(contactId, frame)
                    is Frame.ReadFrame -> handleReadFrame(contactId, frame)
                    is Frame.KeyBundleRequestFrame -> handleKeyBundleRequest(contactId, frame)
                    is Frame.KeyBundleResponseFrame -> handleKeyBundleResponse(contactId, frame)
                    else -> {}
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun handleMessageFrame(event: IncomingFrameEvent) {
        val contactId = event.contactId
        val frame = event.frame as Frame.MessageFrame
        
        // Immediately send ACK, even if it might already exist (in case the previous ACK was lost)
        connectionManager.sendFrame(contactId, Frame.AckFrame(frame.messageId))
        
        // Decrypt the content first, then transact
        val decryptResult = encryptionEngine.decrypt(contactId, frame.content)
        if (decryptResult.isFailure) {
            Log.e(TAG, "Failed to decrypt message ${frame.messageId} from $contactId", decryptResult.exceptionOrNull())
            return
        }
        val plaintext = decryptResult.getOrThrow()

        if (event.isUnknown) {
            val displayName = event.unknownDisplayName ?: "Unknown"
            val identityKey = event.unknownIdentityKey ?: ""
            val request = MessageRequest(
                contactId = contactId,
                displayName = displayName,
                identityKey = identityKey,
                timestamp = frame.timestamp,
                firstMessageText = String(plaintext, Charsets.UTF_8)
            )
            messageRequestRepository.addMessageRequest(request)
            Log.d(TAG, "Received message from unknown contact $contactId. Saved as Message Request.")
            // Ideally fire a local notification for the new request here
            return
        }

        val message = Message(
            id = frame.messageId,
            contactId = contactId, // The sender is the contact
            direction = MessageDirection.INCOMING,
            type = MessageType.TEXT, // Map from frame.messageType in the future
            content = plaintext,
            status = MessageStatus.DELIVERED,
            timestamp = frame.timestamp
        )

        val inserted = messageRepository.insertIfNotExists(message)
        if (inserted) {
            Log.d(TAG, "Received and persisted new message ${frame.messageId} from $contactId. ACK sent.")
            
            if (activeConversationTracker.activeContactId.value != contactId) {
                val contact = contactRepository.getContactById(contactId).firstOrNull()
                val senderName = contact?.displayName ?: "Unknown"
                notificationHelper.showNewMessageNotification(contactId, senderName, message)
            }
        } else {
            Log.d(TAG, "Message ${frame.messageId} already exists, ignoring duplicate but re-sent ACK.")
        }
    }

    private suspend fun handleAckFrame(contactId: String, frame: Frame.AckFrame) {
        val existing = messageRepository.getMessageById(frame.messageId) ?: return
        if (existing.status == MessageStatus.PENDING || existing.status == MessageStatus.SENT) {
            messageRepository.updateMessageStatus(frame.messageId, MessageStatus.DELIVERED)
            Log.d(TAG, "Message ${frame.messageId} marked as DELIVERED from $contactId")
            
            if (existing.type == MessageType.FILE && existing.direction == MessageDirection.OUTGOING) {
                // Sender-side cleanup: once delivered, delete the local encrypted temp file
                val fileTransfer = fileTransferRepository.getByMessageId(frame.messageId).firstOrNull()
                if (fileTransfer != null) {
                    val file = java.io.File(fileTransfer.localUri)
                    if (file.exists() && file.delete()) {
                        Log.d(TAG, "Deleted local encrypted temp file for delivered transfer ${fileTransfer.id}")
                    }
                }
            }
        }
    }

    private suspend fun handleReadFrame(contactId: String, frame: Frame.ReadFrame) {
        val existing = messageRepository.getMessageById(frame.messageId) ?: return
        if (existing.status != MessageStatus.READ) {
            messageRepository.updateMessageStatus(frame.messageId, MessageStatus.READ)
            Log.d(TAG, "Message ${frame.messageId} marked as READ from $contactId")
        }
    }

    private suspend fun handleKeyBundleRequest(contactId: String, frame: Frame.KeyBundleRequestFrame) {
        val myProfile = profileRepository.getProfile().firstOrNull() ?: return
        myProfile.publicKeyBundle?.let { bundle ->
            connectionManager.sendFrame(contactId, Frame.KeyBundleResponseFrame(bundle))
            Log.d(TAG, "Sent KeyBundleResponseFrame to $contactId")
        }
    }

    private suspend fun handleKeyBundleResponse(contactId: String, frame: Frame.KeyBundleResponseFrame) {
        val contact = contactRepository.getContactById(contactId).firstOrNull() ?: return
        
        try {
            val incomingJson = org.json.JSONObject(String(frame.bundle, Charsets.UTF_8))
            val incomingIdentityKey = incomingJson.optString("identityKey", null)
            
            if (contact.publicKeyBundle != null) {
                val savedJson = org.json.JSONObject(String(contact.publicKeyBundle, Charsets.UTF_8))
                val savedIdentityKey = savedJson.optString("identityKey", null)
                
                if (incomingIdentityKey != null && incomingIdentityKey == savedIdentityKey) {
                    contactRepository.upsertContact(contact.copy(publicKeyBundle = frame.bundle))
                    Log.d(TAG, "Successfully received and verified full key bundle for $contactId")
                } else {
                    Log.e(TAG, "KeyBundleResponseFrame identityKey mismatch for $contactId. Expected $savedIdentityKey, got $incomingIdentityKey")
                }
            } else {
                Log.e(TAG, "Cannot verify KeyBundleResponseFrame for $contactId: No partial bundle saved.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse KeyBundleResponseFrame for $contactId", e)
        }
    }
}
