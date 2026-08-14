package com.example.lanman.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.model.MessageRequest
import com.example.lanman.domain.repository.MessageRequestRepository
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.domain.model.Contact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageRequestsViewModel @Inject constructor(
    private val messageRequestRepository: MessageRequestRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {

    val requests: StateFlow<List<MessageRequest>> = messageRequestRepository.getAllMessageRequests()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun acceptRequest(request: MessageRequest) {
        viewModelScope.launch {
            val bundleBytes = """{"identityKey":"${request.identityKey}"}""".toByteArray(Charsets.UTF_8)
            val newContact = Contact(
                id = request.contactId,
                displayName = request.displayName,
                publicKeyBundle = bundleBytes,
                lastKnownIp = null,
                lastSeenAt = null,
                addedAt = System.currentTimeMillis()
            )
            contactRepository.upsertContact(newContact)
            // The message request logic handles moving messages in the background/receiver later or we just drop the first message and let normal sync take over.
            // But we have the first message text here. For simplicity, we just save the contact and let Phase 2 exchange happen on next connection.
            messageRequestRepository.deleteMessageRequest(request.contactId)
        }
    }

    fun rejectRequest(request: MessageRequest) {
        viewModelScope.launch {
            messageRequestRepository.deleteMessageRequest(request.contactId)
        }
    }
}
