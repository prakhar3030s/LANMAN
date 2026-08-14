package com.example.lanman.ui.qr

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.domain.qr.ContactQrPayload
import com.example.lanman.domain.qr.QrPayloadCodec
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.util.qr.QrCodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MyQrCodeState {
    object Loading : MyQrCodeState()
    data class Generated(
        val bitmap: Bitmap,
        val matrix: Array<BooleanArray>?,
        val payloadString: String
    ) : MyQrCodeState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Generated

            if (bitmap != other.bitmap) return false
            if (matrix != null) {
                if (other.matrix == null) return false
                if (!matrix.contentDeepEquals(other.matrix)) return false
            } else if (other.matrix != null) return false
            if (payloadString != other.payloadString) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bitmap.hashCode()
            result = 31 * result + (matrix?.contentDeepHashCode() ?: 0)
            result = 31 * result + payloadString.hashCode()
            return result
        }
    }
    object Error : MyQrCodeState()
}

@HiltViewModel
class MyQrCodeViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow<MyQrCodeState>(MyQrCodeState.Loading)
    val state: StateFlow<MyQrCodeState> = _state.asStateFlow()

    init {
        loadQrCode()
    }

    private fun loadQrCode() {
        viewModelScope.launch {
            profileRepository.getProfile().collect { profile ->
                if (profile == null) {
                    _state.value = MyQrCodeState.Error
                } else {
                    var fullBundleBase64: String? = null

                    profile.publicKeyBundle?.let { bytes ->
                        val bos = java.io.ByteArrayOutputStream()
                        java.util.zip.GZIPOutputStream(bos).use { it.write(bytes) }
                        val gzippedBytes = bos.toByteArray()
                        fullBundleBase64 = android.util.Base64.encodeToString(gzippedBytes, android.util.Base64.NO_WRAP)
                    }

                    val payload = ContactQrPayload(
                        version = 4,
                        id = profile.id,
                        displayName = profile.displayName,
                        publicKey = null,
                        identityKey = null,
                        fullBundleBase64 = fullBundleBase64
                    )
                    val payloadString = QrPayloadCodec.encode(payload)
                    android.util.Log.d("QR_DEBUG", "Payload length: ${payloadString.length}")
                    
                    try {
                        val bitmap = QrCodeGenerator.generateQrCode(payloadString)
                        val matrix = QrCodeGenerator.generateQrBooleanMatrix(payloadString)
                        
                        if (bitmap != null) {
                            _state.value = MyQrCodeState.Generated(bitmap, matrix, payloadString)
                        } else {
                            android.util.Log.e("QR_DEBUG", "Bitmap generation returned null")
                            _state.value = MyQrCodeState.Error
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("QR_DEBUG", "Exception generating QR", e)
                        _state.value = MyQrCodeState.Error
                    }
                }
            }
        }
    }
}
