package com.prakhar.lanman.domain.security

import android.util.Log
import com.prakhar.lanman.data.security.SignalStoreImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionEngine @Inject constructor(
    private val signalStore: SignalStoreImpl,
    private val sessionManager: SessionManager
) {
    private val TAG = "EncryptionEngine"
    private val contactMutexes = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(contactId: String): Mutex {
        return contactMutexes.computeIfAbsent(contactId) { Mutex() }
    }

    suspend fun encrypt(contactId: String, plaintext: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        lockFor(contactId).withLock {
            sessionManager.establishSession(contactId)

            try {
                encryptOnce(contactId, plaintext)
            } catch (e: UntrustedIdentityException) {
                // Never bypass an untrusted identity error by resetting the session.
                throw e
            } catch (e: NoSessionException) {
                rebuildAndEncrypt(contactId, plaintext, e)
            } catch (e: InvalidKeyException) {
                rebuildAndEncrypt(contactId, plaintext, e)
            } catch (e: IllegalStateException) {
                rebuildAndEncrypt(contactId, plaintext, e)
            }
        }
    }

    private suspend fun rebuildAndEncrypt(
        contactId: String,
        plaintext: ByteArray,
        cause: Exception
    ): ByteArray {
        Log.w(TAG, "Encrypt session stale for contact, rebuilding: ${cause.javaClass.simpleName}")
        sessionManager.resetSession(contactId)
        sessionManager.establishSession(contactId)
        return encryptOnce(contactId, plaintext)
    }

    private fun encryptOnce(contactId: String, plaintext: ByteArray): ByteArray {
        val address = SignalProtocolAddress(contactId, 1)
        val cipher = SessionCipher(signalStore, address)

        val ciphertextMessage = cipher.encrypt(plaintext)
        val type = ciphertextMessage.type
        val ciphertextBytes = ciphertextMessage.serialize()

        return ByteBuffer.allocate(1 + ciphertextBytes.size)
            .put(type.toByte())
            .put(ciphertextBytes)
            .array()
    }

    suspend fun decrypt(contactId: String, ciphertext: ByteArray): Result<ByteArray> = withContext(Dispatchers.IO) {
        lockFor(contactId).withLock {
            try {
                if (ciphertext.isEmpty()) {
                    return@withLock Result.failure(IllegalArgumentException("Empty ciphertext"))
                }
                val address = SignalProtocolAddress(contactId, 1)
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

                Result.success(plaintext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt message: ${e.javaClass.simpleName}")
                Result.failure(e)
            }
        }
    }

    suspend fun deleteSession(contactId: String) = withContext(Dispatchers.IO) {
        lockFor(contactId).withLock {
            val address = SignalProtocolAddress(contactId, 1)
            signalStore.deleteSession(address)
        }
    }
}
