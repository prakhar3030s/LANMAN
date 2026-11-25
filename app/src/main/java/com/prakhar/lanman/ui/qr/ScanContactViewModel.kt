package com.prakhar.lanman.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakhar.lanman.domain.model.Contact
import com.prakhar.lanman.domain.qr.ContactQrPayload
import com.prakhar.lanman.domain.qr.QrPayloadCodec
import com.prakhar.lanman.domain.repository.ContactRepository
import com.prakhar.lanman.domain.repository.MessageRequestRepository
import com.prakhar.lanman.domain.repository.ProfileRepository
import com.prakhar.lanman.domain.security.EncryptionEngine
import com.prakhar.lanman.domain.security.PeerAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import javax.inject.Inject

sealed class ScanContactState {
    object Scanning : ScanContactState()
    data class Validating(val code: String) : ScanContactState()
    data class ConfirmationRequired(
        val payload: ContactQrPayload,
        val isUpdate: Boolean,
        val fingerprint: String? = null,
        val identityKeyChanged: Boolean = false
    ) : ScanContactState()
    data class Error(val message: String) : ScanContactState()
    object Success : ScanContactState()
}

@HiltViewModel
class ScanContactViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val contactRepository: ContactRepository,
    private val messageRequestRepository: MessageRequestRepository,
    private val encryptionEngine: EncryptionEngine
) : ViewModel() {

    private val _state = MutableStateFlow<ScanContactState>(ScanContactState.Scanning)
    val state: StateFlow<ScanContactState> = _state.asStateFlow()

    fun onCodeScannedOrEntered(code: String) {
        if (_state.value !is ScanContactState.Scanning && _state.value !is ScanContactState.Error) {
            return
        }

        _state.value = ScanContactState.Validating(code)

        viewModelScope.launch {
            val payload = QrPayloadCodec.decode(code.trim())
            if (payload == null || payload.id.isBlank() || payload.displayName.isBlank()) {
                _state.value = ScanContactState.Error("Invalid QR code format.")
                return@launch
            }

            val myProfile = profileRepository.getProfile().firstOrNull()
            if (myProfile?.id == payload.id) {
                _state.value = ScanContactState.Error("You cannot add yourself.")
                return@launch
            }

            val decodedBundle = withContext(Dispatchers.Default) { decodeBundleBytes(payload) }
            val newIdentityKey = PeerAuth.extractIdentityKeyBase64(decodedBundle)
            if (PeerAuth.parseIdentityKey(newIdentityKey) == null) {
                _state.value = ScanContactState.Error("Incomplete QR code — missing valid encryption keys.")
                return@launch
            }

            val existingContact = contactRepository.getContactById(payload.id).firstOrNull()
            val existingIdentityKey = PeerAuth.extractIdentityKeyBase64(existingContact?.publicKeyBundle)
            val isUpdate = existingContact != null
            val keyChanged = isUpdate && existingIdentityKey != null && existingIdentityKey != newIdentityKey
            val fingerprint = PeerAuth.keyFingerprint(newIdentityKey)

            _state.value = ScanContactState.ConfirmationRequired(
                payload = payload,
                isUpdate = isUpdate,
                fingerprint = fingerprint,
                identityKeyChanged = keyChanged
            )
        }
    }

    fun confirmAddContact(payload: ContactQrPayload) {
        viewModelScope.launch {
            try {
                val publicKeyBundleBytes = withContext(Dispatchers.Default) { decodeBundleBytes(payload) }
                if (publicKeyBundleBytes == null) {
                    _state.value = ScanContactState.Error("Incomplete QR code — missing encryption keys.")
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    val existing = contactRepository.getContactById(payload.id).firstOrNull()
                    val contact = Contact(
                        id = payload.id,
                        displayName = payload.displayName.trim(),
                        publicKeyBundle = publicKeyBundleBytes,
                        lastKnownIp = existing?.lastKnownIp,
                        lastSeenAt = existing?.lastSeenAt,
                        addedAt = existing?.addedAt ?: System.currentTimeMillis()
                    )
                    contactRepository.upsertContact(contact)
                    if (existing != null) {
                        // Reset ratchet session when updating a contact's key bundle so new messages re-handshake.
                        encryptionEngine.deleteSession(payload.id)
                    }
                    messageRequestRepository.deleteMessageRequest(payload.id)
                }
                _state.value = ScanContactState.Success
            } catch (e: Exception) {
                _state.value = ScanContactState.Error("Failed to save contact.")
            }
        }
    }

    fun resetToScanning() {
        _state.value = ScanContactState.Scanning
    }

    private fun decodeBundleBytes(payload: ContactQrPayload): ByteArray? {
        return if (payload.fullBundleBase64 != null) {
            val gzippedBytes = try {
                Base64.getDecoder().decode(payload.fullBundleBase64.trim())
            } catch (_: Exception) {
                return null
            }
            try {
                val bis = java.io.ByteArrayInputStream(gzippedBytes)
                val out = java.io.ByteArrayOutputStream()
                java.util.zip.GZIPInputStream(bis).use { it.copyTo(out) }
                out.toByteArray()
            } catch (_: Exception) {
                gzippedBytes
            }
        } else if (payload.identityKey != null) {
            buildJsonObject {
                put("identityKey", payload.identityKey)
            }.toString().toByteArray(Charsets.UTF_8)
        } else {
            null
        }
    }
}
