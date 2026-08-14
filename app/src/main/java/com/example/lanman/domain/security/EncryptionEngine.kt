package com.example.lanman.domain.security

import android.util.Log
import com.example.lanman.data.security.SignalStoreImpl
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionEngine @Inject constructor(
    private val signalStore: SignalStoreImpl,
    private val sessionManager: SessionManager
) {
    private val TAG = "EncryptionEngine"

    suspend fun encrypt(contactId: String, plaintext: ByteArray): ByteArray {
        // Ensure session exists
        sessionManager.establishSession(contactId)

        val address = SignalProtocolAddress(contactId, 1) // Default device ID
        val cipher = SessionCipher(signalStore, address)

        val ciphertextMessage = cipher.encrypt(plaintext)
        val type = ciphertextMessage.type // WHISPER_TYPE (2) or PREKEY_TYPE (3)
        val ciphertextBytes = ciphertextMessage.serialize()

        Log.d(TAG, "Encrypted message for $contactId. Plaintext size: ${plaintext.size}, Ciphertext size: ${ciphertextBytes.size}, Type: $type")

        // Wire format: [1 byte type][ciphertext bytes]
        return ByteBuffer.allocate(1 + ciphertextBytes.size)
            .put(type.toByte())
            .put(ciphertextBytes)
            .array()
    }

    suspend fun decrypt(contactId: String, ciphertext: ByteArray): Result<ByteArray> {
        return try {
            val address = SignalProtocolAddress(contactId, 1) // Default device ID
            val cipher = SessionCipher(signalStore, address)

            val buffer = ByteBuffer.wrap(ciphertext)
            val type = buffer.get().toInt()
            val messageBytes = ByteArray(buffer.remaining())
            buffer.get(messageBytes)

            val plaintext = when (type) {
                CiphertextMessage.WHISPER_TYPE -> {
                    val signalMessage = SignalMessage(messageBytes)
                    cipher.decrypt(signalMessage)
                }
                CiphertextMessage.PREKEY_TYPE -> {
                    val preKeySignalMessage = PreKeySignalMessage(messageBytes)
                    cipher.decrypt(preKeySignalMessage)
                }
                else -> {
                    throw IllegalArgumentException("Unknown message type: $type")
                }
            }
            
            Log.d(TAG, "Successfully decrypted message from $contactId. Plaintext size: ${plaintext.size}")
            Result.success(plaintext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt message from $contactId: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun deleteSession(contactId: String) {
        val address = SignalProtocolAddress(contactId, 1)
        signalStore.deleteSession(address)
    }
}
