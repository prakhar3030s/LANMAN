package com.example.lanman.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.model.Profile
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.domain.usecase.ClearLocalDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val clearLocalDataUseCase: ClearLocalDataUseCase
) : ViewModel() {

    private val _receivedFilesSize = MutableStateFlow(0L)
    val receivedFilesSize: StateFlow<Long> = _receivedFilesSize.asStateFlow()

    private fun receivedFilesDir() = File(context.filesDir, "received_files")

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

    fun refreshReceivedFilesSize() {
        viewModelScope.launch {
            _receivedFilesSize.value = withContext(Dispatchers.IO) {
                receivedFilesDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        }
    }

    fun clearReceivedFiles() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                receivedFilesDir().deleteRecursively()
            }
            _receivedFilesSize.value = 0L
        }
    }

    fun clearLocalData(onCleared: () -> Unit) {
        viewModelScope.launch {
            clearLocalDataUseCase()
            onCleared()
        }
    }
}
