package com.prakhar.lanman.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakhar.lanman.data.security.BlockedContactsStore
import com.prakhar.lanman.data.security.MutedChatsStore
import com.prakhar.lanman.domain.model.ContactPresence
import com.prakhar.lanman.domain.network.LanNetworkState
import com.prakhar.lanman.domain.network.NetworkDiscoveryManager
import com.prakhar.lanman.domain.network.PresenceTracker
import com.prakhar.lanman.domain.repository.MessageRepository
import com.prakhar.lanman.domain.repository.ProfileRepository
import com.prakhar.lanman.domain.security.PeerAuth
import com.prakhar.lanman.domain.usecase.RemoveContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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
class ContactListViewModel @Inject constructor(
    private val presenceTracker: PresenceTracker,
    private val networkDiscoveryManager: NetworkDiscoveryManager,
    private val removeContactUseCase: RemoveContactUseCase,
    private val messageRepository: MessageRepository,
    private val profileRepository: ProfileRepository,
    private val mutedChatsStore: MutedChatsStore,
    private val blockedContactsStore: BlockedContactsStore
) : ViewModel() {

    val contacts: StateFlow<List<ContactPresence>> = presenceTracker.contactPresence
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val networkState: StateFlow<LanNetworkState> = networkDiscoveryManager.networkState

    val myIdentityKeyBase64: StateFlow<String?> = profileRepository.getProfile()
        .map { PeerAuth.extractIdentityKeyBase64(it?.publicKeyBundle) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _mutedIds = MutableStateFlow(mutedChatsStore.mutedIds())
    val mutedIds: StateFlow<Set<String>> = _mutedIds.asStateFlow()

    private val _blockedIds = MutableStateFlow(blockedContactsStore.blockedIds())
    val blockedIds: StateFlow<Set<String>> = _blockedIds.asStateFlow()

    fun stats(contactId: String): Flow<Pair<Int, Int>> = combine(
        messageRepository.getMessageCount(contactId),
        messageRepository.getAttachmentCount(contactId)
    ) { messages, attachments -> messages to attachments }

    fun toggleMute(contactId: String) {
        val next = contactId !in _mutedIds.value
        mutedChatsStore.setMuted(contactId, next)
        _mutedIds.value = mutedChatsStore.mutedIds()
    }

    fun toggleBlock(contactId: String) {
        if (contactId in _blockedIds.value) {
            blockedContactsStore.unblock(contactId)
        } else {
            blockedContactsStore.block(contactId)
        }
        _blockedIds.value = blockedContactsStore.blockedIds()
    }

    fun refreshDiscovery() {
        networkDiscoveryManager.triggerRefreshBurst()
    }

    fun removeContact(contactId: String) {
        viewModelScope.launch {
            removeContactUseCase(contactId)
        }
    }
}
