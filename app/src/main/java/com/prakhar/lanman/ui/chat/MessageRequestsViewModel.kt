package com.prakhar.lanman.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakhar.lanman.domain.model.Contact
import com.prakhar.lanman.domain.model.Message
import com.prakhar.lanman.domain.model.MessageDirection
import com.prakhar.lanman.domain.model.MessageRequest
import com.prakhar.lanman.domain.model.MessageStatus
import com.prakhar.lanman.domain.model.MessageType
import com.prakhar.lanman.domain.network.ConnectionManager
import com.prakhar.lanman.domain.network.protocol.Frame
import com.prakhar.lanman.domain.repository.ContactRepository
import com.prakhar.lanman.domain.repository.MessageRepository
import com.prakhar.lanman.domain.repository.MessageRequestRepository
import com.prakhar.lanman.data.security.BlockedContactsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

@HiltViewModel
class MessageRequestsViewModel @Inject constructor(
    private val messageRequestRepository: MessageRequestRepository,
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val connectionManager: ConnectionManager,
    private val blockedContactsStore: BlockedContactsStore
) : ViewModel() {

    /** Kept only so a declined request can be restored from the undo snackbar. */
    private var lastDeclined: MessageRequest? = null

    val requests: StateFlow<List<MessageRequest>> = messageRequestRepository.getAllMessageRequests()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun acceptRequest(request: MessageRequest) {
        viewModelScope.launch {
            try {
                // A request only carries the sender's identity key. Store it as a partial bundle:
                // the key-bundle exchange fills in registration id and prekeys afterwards, and
                // SessionManager can only build a session from a complete bundle.
                val bundleBytes = buildJsonObject {
                    put("identityKey", request.identityKey)
                }.toString().toByteArray(Charsets.UTF_8)

                contactRepository.upsertContact(
                    Contact(
                        id = request.contactId,
                        displayName = request.displayName,
                        publicKeyBundle = bundleBytes,
                        lastKnownIp = null,
                        lastSeenAt = null,
                        addedAt = System.currentTimeMillis()
                    )
                )

                // Keep the message that created the request instead of discarding it.
                val messageId = "message_request_${request.contactId}_${request.timestamp}"
                messageRepository.insertIfNotExists(
                    Message(
                        id = messageId,
                        contactId = request.contactId,
                        direction = MessageDirection.INCOMING,
                        type = MessageType.TEXT,
                        content = request.firstMessageText.toByteArray(Charsets.UTF_8),
                        status = MessageStatus.DELIVERED,
                        timestamp = request.timestamp
                    )
                )

                // Only drop the request once the contact and its message are safely stored.
                messageRequestRepository.deleteMessageRequest(request.contactId)

                // Ask for the full prekey bundle now. If no channel is open this is a no-op and
                // ConnectionManagerImpl requests the bundle on the next connect, because the
                // stored bundle has no kyberPreKey.
                connectionManager.sendFrame(
                    request.contactId,
                    Frame.KeyBundleRequestFrame(request.contactId)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to accept message request from ${request.contactId}", e)
            }
        }
    }

    fun rejectRequest(request: MessageRequest) {
        viewModelScope.launch {
            lastDeclined = request
            messageRequestRepository.deleteMessageRequest(request.contactId)
        }
    }

    fun undoReject() {
        val request = lastDeclined ?: return
        lastDeclined = null
        viewModelScope.launch {
            messageRequestRepository.addMessageRequest(request)
        }
    }

    /** Declines the request and drops every future message from that contact. */
    fun blockRequest(request: MessageRequest) {
        viewModelScope.launch {
            blockedContactsStore.block(request.contactId)
            lastDeclined = null
            messageRequestRepository.deleteMessageRequest(request.contactId)
        }
    }

    companion object {
        private const val TAG = "MessageRequestsVM"
    }
}
