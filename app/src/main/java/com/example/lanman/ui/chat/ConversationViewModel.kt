package com.example.lanman.ui.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.model.ContactPresence
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.domain.network.ActiveConversationTracker
import com.example.lanman.domain.network.FileTransferProgressTracker
import com.example.lanman.domain.network.PresenceTracker
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.domain.repository.FileTransferRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.usecase.MarkMessageReadUseCase
import com.example.lanman.domain.usecase.SendFileUseCase
import com.example.lanman.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val fileTransferRepository: FileTransferRepository,
    private val presenceTracker: PresenceTracker,
    private val progressTracker: FileTransferProgressTracker,
    private val activeConversationTracker: ActiveConversationTracker,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sendFileUseCase: SendFileUseCase,
    private val markMessageReadUseCase: MarkMessageReadUseCase
) : ViewModel() {

    private val contactId: String = checkNotNull(savedStateHandle["contactId"])

    private val progressMapFlow = progressTracker.progress
        .scan(emptyMap<String, Float>()) { map, progressUpdate ->
            val percentage = if (progressUpdate.totalBytes > 0) {
                progressUpdate.bytesTransferred.toFloat() / progressUpdate.totalBytes.toFloat()
            } else 0f
            map + (progressUpdate.fileTransferId to percentage)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageUiModel>> = messageRepository.getMessagesForContact(contactId)
        .flatMapLatest { msgs ->
            val fileMessageIds = msgs.filter { it.type == MessageType.FILE }.map { it.id }
            if (fileMessageIds.isEmpty()) {
                kotlinx.coroutines.flow.combine(kotlinx.coroutines.flow.flowOf(emptyList<com.example.lanman.domain.model.FileTransfer>()), progressMapFlow) { _, progressMap ->
                    msgs.map { MessageUiModel(it, null, null) }
                }
            } else {
                kotlinx.coroutines.flow.combine(fileTransferRepository.getByMessageIds(fileMessageIds), progressMapFlow) { transfers, progressMap ->
                    val transferMap = transfers.associateBy { it.messageId }
                    msgs.map { msg ->
                        if (msg.type == MessageType.FILE) {
                            val transfer = transferMap[msg.id]
                            val progress = transfer?.let { progressMap[it.id] }
                            MessageUiModel(msg, transfer, progress)
                        } else {
                            MessageUiModel(msg, null, null)
                        }
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isTransferActive: StateFlow<Boolean> = messageRepository.getMessagesForContact(contactId)
        .map { msgs ->
            msgs.any { it.direction == MessageDirection.OUTGOING && it.type == MessageType.FILE && (it.status == MessageStatus.PENDING || it.status == MessageStatus.SENT) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val contactPresence: StateFlow<ContactPresence?> = presenceTracker.contactPresence
        .map { presences -> presences.find { it.contact.id == contactId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _errorFlow = MutableSharedFlow<String>()
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    fun sendMessage(content: String) {
        viewModelScope.launch {
            try {
                sendMessageUseCase(contactId, content)
            } catch (e: Exception) {
                _errorFlow.emit("Failed to send message: ${e.message}")
            }
        }
    }
    
    fun sendFile(uri: Uri) {
        viewModelScope.launch {
            try {
                sendFileUseCase(contactId, uri)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorFlow.emit("Failed to send file: ${e.message}")
            }
        }
    }

    fun markAsRead(messageId: String) {
        viewModelScope.launch {
            markMessageReadUseCase(messageId)
        }
    }

    fun onMessagesVisible(visibleMessages: List<MessageUiModel>) {
        visibleMessages.forEach { uiModel ->
            val msg = uiModel.message
            if (msg.direction == MessageDirection.INCOMING && msg.status != MessageStatus.READ) {
                markAsRead(msg.id)
            }
        }
    }

    fun setActive(isActive: Boolean) {
        activeConversationTracker.setActiveContactId(if (isActive) contactId else null)
    }
}
