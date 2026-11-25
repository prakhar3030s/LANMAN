package com.example.lanman.domain.security

import android.util.Log
import com.example.lanman.data.security.SignalStoreImpl
import com.example.lanman.domain.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.state.PreKeyBundle
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val contactRepository: ContactRepository,
    private val signalStore: SignalStoreImpl
) {
    private val TAG = "SessionManager"

    suspend fun establishSession(contactId: String) = withContext(Dispatchers.IO) {
        val address = SignalProtocolAddress(contactId, 1) // Default device ID 1 for now

        // Check if session already exists
        if (signalStore.containsSession(address)) {
            Log.d(TAG, "Session already established for $contactId")
            return@withContext
        }

        val contact = contactRepository.getContactById(contactId).firstOrNull() 
            ?: throw IllegalArgumentException("Contact not found")
            
        val bundleBytes = contact.publicKeyBundle 
            ?: throw IllegalStateException("Contact $contactId has no public key bundle")

        val json = Json.parseToJsonElement(String(bundleBytes, Charsets.UTF_8)).jsonObject

        val identityKeyBase64 = json["identityKey"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing identityKey in contact bundle")
        val identityKeyBytes = Base64.getDecoder().decode(identityKeyBase64)
        val identityKey = IdentityKey(identityKeyBytes, 0)

        // Trust-On-First-Use (TOFU) validation
        // In a real app, you would compare this identityKey with a previously seen one for this contact.
        // Since we extract it directly from the contact record that was populated via QR, 
        // we inherently trust what's currently in the DB.
        
        val registrationId = json["registrationId"]?.jsonPrimitive?.intOrNull ?: -1
        val deviceId = json["deviceId"]?.jsonPrimitive?.intOrNull ?: -1
        
        if (registrationId <= 0 || registrationId > 16383) {
            throw IllegalArgumentException("Invalid or missing registrationId in contact bundle")
        }
        if (deviceId <= 0) {
            throw IllegalArgumentException("Invalid or missing deviceId in contact bundle")
        }

        val signedPreKeyId = json["signedPreKeyId"]?.jsonPrimitive?.intOrNull
        val signedPreKeyStr = json["signedPreKey"]?.jsonPrimitive?.contentOrNull
        val signedPreKeySignatureStr = json["signedPreKeySignature"]?.jsonPrimitive?.contentOrNull
        if (signedPreKeyId == null || signedPreKeyStr == null || signedPreKeySignatureStr == null) {
            throw IllegalArgumentException("Missing signed prekey information in contact bundle")
        }
        if (signedPreKeyId < 0) {
            throw IllegalArgumentException("Invalid signedPreKeyId in contact bundle")
        }
        
        val signedPreKeyBytes = Base64.getDecoder().decode(signedPreKeyStr)
        val signedPreKeySignature = Base64.getDecoder().decode(signedPreKeySignatureStr)
        
        // One-time prekeys from a QR bundle are ignored on purpose. A bundle is long-lived, so
        // its single prekey is consumed by the first session; every later session would then
        // fail on the peer with InvalidKeyIdException and the messages would queue forever.
        // X3DH/PQXDH still work from the signed and Kyber prekeys alone.
        val oneTimePreKeyId = PreKeyBundle.NULL_PRE_KEY_ID
        val oneTimePreKeyBytes: ByteArray? = null

        val kyberPreKeyId = json["kyberPreKeyId"]?.jsonPrimitive?.intOrNull
        val kyberPreKeyStr = json["kyberPreKey"]?.jsonPrimitive?.contentOrNull
        val kyberPreKeySignatureStr = json["kyberPreKeySignature"]?.jsonPrimitive?.contentOrNull
        if (kyberPreKeyId == null || kyberPreKeyStr == null || kyberPreKeySignatureStr == null) {
            throw IllegalArgumentException("Missing kyber prekey information in contact bundle")
        }
        if (kyberPreKeyId < 0) {
            throw IllegalArgumentException("Invalid kyberPreKeyId in contact bundle")
        }
        val kyberPreKeyBytes = Base64.getDecoder().decode(kyberPreKeyStr)
        val kyberPreKeySignature = Base64.getDecoder().decode(kyberPreKeySignatureStr)

        val preKeyBundle = PreKeyBundle(
            registrationId,
            deviceId,
            if (oneTimePreKeyBytes != null) oneTimePreKeyId else org.signal.libsignal.protocol.state.PreKeyBundle.NULL_PRE_KEY_ID,
            oneTimePreKeyBytes?.let { org.signal.libsignal.protocol.ecc.ECPublicKey(it) },
            signedPreKeyId,
            org.signal.libsignal.protocol.ecc.ECPublicKey(signedPreKeyBytes),
            signedPreKeySignature,
            identityKey,
            kyberPreKeyId,
            org.signal.libsignal.protocol.kem.KEMPublicKey(kyberPreKeyBytes),
            kyberPreKeySignature
        )

        val sessionBuilder = SessionBuilder(signalStore, address)
        sessionBuilder.process(preKeyBundle)
        
        Log.d(TAG, "Successfully established X3DH session for $contactId")
    }

    /** Drops a broken session so the next message starts from a fresh prekey handshake. */
    suspend fun resetSession(contactId: String) = withContext(Dispatchers.IO) {
        signalStore.deleteSession(SignalProtocolAddress(contactId, 1))
        Log.d(TAG, "Reset session for $contactId")
    }
}
