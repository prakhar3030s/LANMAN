package com.example.lanman.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.model.Profile
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.domain.usecase.ClearLocalDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val clearLocalDataUseCase: ClearLocalDataUseCase
) : ViewModel() {

    val profile: StateFlow<Profile?> = profileRepository.getProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                profileRepository.updateDisplayName(newName.trim())
            }
        }
    }

    fun clearLocalData(onCleared: () -> Unit) {
        viewModelScope.launch {
            clearLocalDataUseCase()
            onCleared()
        }
    }
}
