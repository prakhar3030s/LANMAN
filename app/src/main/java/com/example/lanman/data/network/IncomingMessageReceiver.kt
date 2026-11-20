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
import com.example.lanman.data.security.BlockedContactsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class IncomingMessageReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager,
    private val messageRepository: MessageRepository,
    private val fileTransferRepository: FileTransferRepository,
    private val contactRepository: ContactRepository,
    private val encryptionEngine: EncryptionEngine,
    private val notificationHelper: NotificationHelper,
    private val activeConversationTracker: ActiveConversationTracker,
    private val profileRepository: ProfileRepository,
    private val messageRequestRepository: MessageRequestRepository,
    private val blockedContactsStore: BlockedContactsStore
) {
    private val TAG = "IncomingMessageReceiver"
    private var job: Job? = null

    // Rate limit for asking a peer to re-handshake after a message we could not decrypt.
    private val lastKeyBundleRequest = java.util.concurrent.ConcurrentHashMap<String, Long>()

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

        if (blockedContactsStore.isBlocked(contactId)) {
            // Acknowledge so a blocked sender does not retry forever, but store nothing.
            Log.d(TAG, "Ignoring message from blocked contact $contactId")
            connectionManager.sendFrame(contactId, Frame.AckFrame(frame.messageId))
            return
        }

        // Decrypt before acknowledging: an ACK is a promise that the text is on this device, and
        // a message we cannot read must stay queued on the sender so it is retried once the
        // session is usable (instead of showing a double tick for a message that was dropped).
        val decryptResult = encryptionEngine.decrypt(contactId, frame.content)
        if (decryptResult.isFailure) {
            Log.e(TAG, "Failed to decrypt message ${frame.messageId} from $contactId", decryptResult.exceptionOrNull())
            // Ask for a fresh handshake so the sender starts a new ratchet; without it every
            // later message fails the same way and nothing ever arrives.
            val now = System.currentTimeMillis()
            if (now - (lastKeyBundleRequest[contactId] ?: 0L) > KEY_BUNDLE_REQUEST_INTERVAL_MS) {
                lastKeyBundleRequest[contactId] = now
                connectionManager.sendFrame(contactId, Frame.KeyBundleRequestFrame(contactId))
            }
            return
        }
        val plaintext = decryptResult.getOrThrow()

        // A connection is flagged unknown when it opened, but the user can accept the request or
        // pair the device while it is still open, so always re-check the contact table.
        if (contactRepository.getContactById(contactId).firstOrNull() == null) {
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
            connectionManager.sendFrame(contactId, Frame.AckFrame(frame.messageId))
            Log.d(TAG, "Received message from unknown contact $contactId. Saved as Message Request.")
            notificationHelper.showMessageRequestNotification(
                contactId = contactId,
                senderName = displayName,
                preview = request.firstMessageText
            )
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
        // ACK once the message is on disk (also re-ACK a duplicate whose ACK was lost).
        connectionManager.sendFrame(contactId, Frame.AckFrame(frame.messageId))
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
            
            if ((existing.type == MessageType.FILE || existing.type == MessageType.VOICE) && existing.direction == MessageDirection.OUTGOING) {
                val fileTransfer = fileTransferRepository.getByMessageId(frame.messageId).firstOrNull()
                if (fileTransfer != null) {
                    val keyFile = java.io.File(java.io.File(context.filesDir, "transfer_keys"), fileTransfer.id)
                    if (keyFile.exists()) {
                        keyFile.delete()
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
            // Either the peer just paired (it needs our bundle) or it could not decrypt what we
            // sent. Dropping our side of the session makes the next message a prekey message,
            // which is the only thing a peer with no usable session can read.
            encryptionEngine.deleteSession(contactId)
        }
    }

    private suspend fun handleKeyBundleResponse(contactId: String, frame: Frame.KeyBundleResponseFrame) {        val contact = contactRepository.getContactById(contactId).firstOrNull() ?: return
        
        try {
            val incomingJson = org.json.JSONObject(String(frame.bundle, Charsets.UTF_8))
            val incomingIdentityKey = incomingJson.optString("identityKey", null)
            
            if (contact.publicKeyBundle != null) {
                val savedJson = org.json.JSONObject(String(contact.publicKeyBundle, Charsets.UTF_8))
                val savedIdentityKey = savedJson.optString("identityKey", "")
                // An accepted message request starts with an identity-key-only bundle; in that
                // case there is nothing to compare against yet, so adopt the full bundle.
                val identityMatches = incomingIdentityKey != null &&
                    (savedIdentityKey.isBlank() || incomingIdentityKey == savedIdentityKey)
                
                if (identityMatches) {
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

    private companion object {
        const val KEY_BUNDLE_REQUEST_INTERVAL_MS = 15_000L
    }
}
