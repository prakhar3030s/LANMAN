package com.example.lanman.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.model.Contact
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageRequest
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.repository.MessageRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class MessageRequestsViewModel @Inject constructor(
    private val messageRequestRepository: MessageRequestRepository,
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val connectionManager: ConnectionManager
) : ViewModel() {

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
                val bundleBytes = JSONObject()
                    .put("identityKey", request.identityKey)
                    .toString()
                    .toByteArray(Charsets.UTF_8)

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
            messageRequestRepository.deleteMessageRequest(request.contactId)
        }
    }

    companion object {
        private const val TAG = "MessageRequestsVM"
    }
}
