package com.example.lanman.domain.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveConversationTracker @Inject constructor() {
    private val _activeContactId = MutableStateFlow<String?>(null)
    val activeContactId: StateFlow<String?> = _activeContactId.asStateFlow()

    fun setActiveContactId(contactId: String?) {
        _activeContactId.value = contactId
    }
}
