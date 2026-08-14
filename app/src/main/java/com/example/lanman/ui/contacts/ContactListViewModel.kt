package com.example.lanman.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.model.ContactPresence
import com.example.lanman.domain.network.PresenceTracker
import com.example.lanman.domain.usecase.RemoveContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactListViewModel @Inject constructor(
    private val presenceTracker: PresenceTracker,
    private val removeContactUseCase: RemoveContactUseCase
) : ViewModel() {

    val contacts: StateFlow<List<ContactPresence>> = presenceTracker.contactPresence
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun removeContact(contactId: String) {
        viewModelScope.launch {
            removeContactUseCase(contactId)
        }
    }
}
