package com.example.lanman.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.model.Contact
import com.example.lanman.domain.qr.ContactQrPayload
import com.example.lanman.domain.qr.QrPayloadCodec
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScanContactState {
    object Scanning : ScanContactState()
    data class Validating(val code: String) : ScanContactState()
    data class ConfirmationRequired(
        val payload: ContactQrPayload,
        val isUpdate: Boolean
    ) : ScanContactState()
    data class Error(val message: String) : ScanContactState()
    object Success : ScanContactState()
}

@HiltViewModel
class ScanContactViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ScanContactState>(ScanContactState.Scanning)
    val state: StateFlow<ScanContactState> = _state.asStateFlow()

    fun onCodeScannedOrEntered(code: String) {
        if (_state.value !is ScanContactState.Scanning && _state.value !is ScanContactState.Error) {
            return // Prevent duplicate processing
        }
        
        _state.value = ScanContactState.Validating(code)

        viewModelScope.launch {
            val payload = QrPayloadCodec.decode(code)
            if (payload == null) {
                _state.value = ScanContactState.Error("Invalid QR code format.")
                return@launch
            }

            val myProfile = profileRepository.getProfile().firstOrNull()
            if (myProfile?.id == payload.id) {
                _state.value = ScanContactState.Error("You cannot add yourself.")
                return@launch
            }

            val existingContact = contactRepository.getContactById(payload.id).firstOrNull()
            val isUpdate = existingContact != null

            _state.value = ScanContactState.ConfirmationRequired(payload, isUpdate)
        }
    }

    fun confirmAddContact(payload: ContactQrPayload) {
        viewModelScope.launch {
            try {
                if (payload.fullBundleBase64 == null && payload.identityKey == null) {
                    _state.value = ScanContactState.Error("Incomplete QR code — missing encryption keys")
                    return@launch
                }
                
                val publicKeyBundleBytes = if (payload.fullBundleBase64 != null) {
                    val gzippedBytes = android.util.Base64.decode(payload.fullBundleBase64, android.util.Base64.NO_WRAP)
                    try {
                        val bis = java.io.ByteArrayInputStream(gzippedBytes)
                        val out = java.io.ByteArrayOutputStream()
                        java.util.zip.GZIPInputStream(bis).use { it.copyTo(out) }
                        out.toByteArray()
                    } catch (e: Exception) {
                        // Fallback in case it wasn't gzipped (e.g. older testing version)
                        gzippedBytes
                    }
                } else if (payload.identityKey != null) {
                    // Fallback for v3 QR codes
                    val json = org.json.JSONObject().apply {
                        put("identityKey", payload.identityKey)
                    }
                    json.toString().toByteArray(Charsets.UTF_8)
                } else {
                    null
                }

                val contact = Contact(
                    id = payload.id,
                    displayName = payload.displayName,
                    publicKeyBundle = publicKeyBundleBytes,
                    lastKnownIp = null,
                    lastSeenAt = null,
                    addedAt = System.currentTimeMillis()
                )
                contactRepository.upsertContact(contact)
                _state.value = ScanContactState.Success
            } catch (e: Exception) {
                _state.value = ScanContactState.Error("Failed to save contact.")
            }
        }
    }

    fun resetToScanning() {
        _state.value = ScanContactState.Scanning
    }
}
