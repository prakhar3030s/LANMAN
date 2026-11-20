package com.example.lanman.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.model.ContactPresence
import com.example.lanman.domain.network.PresenceTracker
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.usecase.RemoveContactUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactListViewModel @Inject constructor(
    private val presenceTracker: PresenceTracker,
    private val removeContactUseCase: RemoveContactUseCase,
    private val messageRepository: MessageRepository
) : ViewModel() {

    val contacts: StateFlow<List<ContactPresence>> = presenceTracker.contactPresence
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Message count and attachment count for the contact-info metadata block. */
    fun stats(contactId: String): Flow<Pair<Int, Int>> = combine(
        messageRepository.getMessageCount(contactId),
        messageRepository.getAttachmentCount(contactId)
    ) { messages, attachments -> messages to attachments }

    fun removeContact(contactId: String) {
        viewModelScope.launch {
            removeContactUseCase(contactId)
        }
    }
}
