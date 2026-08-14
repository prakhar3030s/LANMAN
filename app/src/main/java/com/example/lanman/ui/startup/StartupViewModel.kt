package com.example.lanman.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StartupState {
    object Loading : StartupState()
    object NeedsOnboarding : StartupState()
    object Ready : StartupState()
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        checkStartupState()
    }

    private fun checkStartupState() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (profileRepository.hasProfile()) {
                _state.value = StartupState.Ready
            } else {
                _state.value = StartupState.NeedsOnboarding
            }
        }
    }
}
