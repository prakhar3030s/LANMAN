package com.example.lanman.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.model.ContactPresence
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.usecase.MarkMessageReadUseCase
import com.example.lanman.domain.usecase.SendMessageUseCase
import com.example.lanman.domain.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val presenceTracker: com.example.lanman.domain.network.PresenceTracker,
    private val sendMessageUseCase: SendMessageUseCase,
    private val markMessageReadUseCase: MarkMessageReadUseCase,
    private val messageRepository: MessageRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _onlineContacts = MutableStateFlow<List<ContactPresence>>(emptyList())
    val onlineContacts: StateFlow<List<ContactPresence>> = _onlineContacts.asStateFlow()
    
    private val _selectedContactId = MutableStateFlow<String?>(null)
    val selectedContactId: StateFlow<String?> = _selectedContactId.asStateFlow()
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    init {
        viewModelScope.launch {
            presenceTracker.contactPresence.collectLatest {
                _onlineContacts.value = it
            }
        }
    }

    fun selectContact(contactId: String?) {
        _selectedContactId.value = contactId
        if (contactId != null) {
            viewModelScope.launch {
                messageRepository.getMessagesForContact(contactId).collectLatest { msgs ->
                    _messages.value = msgs
                }
            }
        } else {
            _messages.value = emptyList()
        }
    }
    
    fun sendMessage(text: String) {
        val contactId = _selectedContactId.value ?: return
        viewModelScope.launch {
            sendMessageUseCase(contactId, text)
        }
    }

    fun markAsRead(messageId: String) {
        viewModelScope.launch {
            markMessageReadUseCase(messageId)
        }
    }

    fun establishSession() {
        val contactId = _selectedContactId.value ?: return
        viewModelScope.launch {
            try {
                sessionManager.establishSession(contactId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
