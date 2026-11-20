package com.example.lanman.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.data.security.BlockedContactsStore
import com.example.lanman.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlockedContact(val id: String, val displayName: String)

@HiltViewModel
class BlockedContactsViewModel @Inject constructor(
    private val blockedContactsStore: BlockedContactsStore,
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _blocked = MutableStateFlow<List<BlockedContact>>(emptyList())
    val blocked: StateFlow<List<BlockedContact>> = _blocked.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val ids = blockedContactsStore.blockedIds()
            val names = contactRepository.getAllContacts().firstOrNull()
                ?.associate { it.id to it.displayName }
                ?: emptyMap()
            _blocked.value = ids.map { id ->
                BlockedContact(id = id, displayName = names[id] ?: "Unknown contact")
            }.sortedBy { it.displayName }
        }
    }

    fun unblock(contactId: String) {
        blockedContactsStore.unblock(contactId)
        refresh()
    }
}
