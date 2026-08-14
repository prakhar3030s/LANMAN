package com.example.lanman.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.model.ChatListContact
import com.example.lanman.domain.network.PresenceTracker
import com.example.lanman.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import com.example.lanman.domain.repository.MessageRequestRepository
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val presenceTracker: PresenceTracker,
    private val messageRequestRepository: MessageRequestRepository
) : ViewModel() {

    val chatList: StateFlow<List<ChatListContact>> = combine(
        contactRepository.getChatListContacts(),
        presenceTracker.contactPresence
    ) { chatListContacts, presences ->
        val presenceMap = presences.associateBy { it.contact.id }
        chatListContacts.map { chatContact ->
            val isOnline = presenceMap[chatContact.contact.id]?.isOnline ?: false
            chatContact.copy(isOnline = isOnline)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val messageRequestCount: StateFlow<Int> = messageRequestRepository.getAllMessageRequests()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
}
