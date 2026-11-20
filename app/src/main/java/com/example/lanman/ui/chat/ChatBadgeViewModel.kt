package com.example.lanman.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Total unread messages, shown on the Chats tab so the count is visible from any tab. */
@HiltViewModel
class ChatBadgeViewModel @Inject constructor(
    contactRepository: ContactRepository
) : ViewModel() {

    val unreadTotal: StateFlow<Int> = contactRepository.getChatListContacts()
        .map { chats -> chats.sumOf { it.unreadCount } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
}
