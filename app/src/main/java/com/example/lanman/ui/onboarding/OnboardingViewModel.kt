package com.example.lanman.ui.onboarding

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OnboardingEvent {
    object NavigateToHome : OnboardingEvent()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _displayName = mutableStateOf("")
    val displayName: State<String> = _displayName

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage

    private val _isSaving = mutableStateOf(false)
    val isSaving: State<Boolean> = _isSaving

    private val eventChannel = Channel<OnboardingEvent>()
    val events = eventChannel.receiveAsFlow()

    fun onDisplayNameChanged(newName: String) {
        _displayName.value = newName
        if (_errorMessage.value != null) {
            _errorMessage.value = null
        }
    }

    fun submitProfile() {
        val name = _displayName.value.trim()
        
        if (name.isBlank()) {
            _errorMessage.value = "Name cannot be empty"
            return
        }
        
        if (name.length > 30) {
            _errorMessage.value = "Name must be 30 characters or less"
            return
        }

        _isSaving.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // Ensure we don't accidentally create multiple profiles if submit is called twice
                if (profileRepository.hasProfile()) {
                    eventChannel.send(OnboardingEvent.NavigateToHome)
                    return@launch
                }

                profileRepository.createProfile(name)
                eventChannel.send(OnboardingEvent.NavigateToHome)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create profile. Please try again."
            } finally {
                _isSaving.value = false
            }
        }
    }
}
