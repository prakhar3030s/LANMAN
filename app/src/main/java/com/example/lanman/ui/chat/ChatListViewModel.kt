package com.example.lanman.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.data.security.MutedChatsStore
import com.example.lanman.domain.engine.MessagingEngine
import com.example.lanman.domain.model.ChatListContact
import com.example.lanman.domain.network.LanNetworkState
import com.example.lanman.domain.network.NetworkDiscoveryManager
import com.example.lanman.domain.network.PresenceTracker
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.repository.MessageRequestRepository
import com.example.lanman.domain.usecase.MarkMessageReadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val presenceTracker: PresenceTracker,
    private val messageRequestRepository: MessageRequestRepository,
    private val messageRepository: MessageRepository,
    private val mutedChatsStore: MutedChatsStore,
    private val markMessageReadUseCase: MarkMessageReadUseCase,
    private val networkDiscoveryManager: NetworkDiscoveryManager,
    private val messagingEngine: MessagingEngine
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

    val networkState: StateFlow<LanNetworkState> = networkDiscoveryManager.networkState
    val isEngineRunning: StateFlow<Boolean> = messagingEngine.isRunning

    private val _mutedIds = MutableStateFlow(mutedChatsStore.mutedIds())
    val mutedIds: StateFlow<Set<String>> = _mutedIds.asStateFlow()

    fun markRead(contactId: String) {
        viewModelScope.launch {
            val unreadIds = messageRepository.getUnreadDeliveredIncomingIds(contactId)
            unreadIds.forEach { messageId ->
                markMessageReadUseCase(messageId)
            }
            messageRepository.markConversationRead(contactId)
        }
    }

    fun deleteChat(contactId: String) {
        viewModelScope.launch { messageRepository.deleteConversation(contactId) }
    }

    fun toggleMute(contactId: String) {
        val muted = contactId in _mutedIds.value
        mutedChatsStore.setMuted(contactId, !muted)
        _mutedIds.value = mutedChatsStore.mutedIds()
    }

    fun refreshDiscovery() {
        networkDiscoveryManager.triggerRefreshBurst()
    }
}
