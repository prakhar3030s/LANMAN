package com.example.lanman.domain.security

import android.util.Base64
import android.util.Log
import com.example.lanman.data.security.SignalStoreImpl
import com.example.lanman.domain.repository.ContactRepository
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.state.PreKeyBundle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val contactRepository: ContactRepository,
    private val signalStore: SignalStoreImpl
) {
    private val TAG = "SessionManager"

    suspend fun establishSession(contactId: String) {
        val address = SignalProtocolAddress(contactId, 1) // Default device ID 1 for now

        // Check if session already exists
        if (signalStore.containsSession(address)) {
            Log.d(TAG, "Session already established for $contactId")
            return
        }

        val contact = contactRepository.getContactById(contactId).firstOrNull() 
            ?: throw IllegalArgumentException("Contact not found")
            
        val bundleBytes = contact.publicKeyBundle 
            ?: throw IllegalStateException("Contact $contactId has no public key bundle")

        val json = JSONObject(String(bundleBytes, Charsets.UTF_8))
        
        val identityKeyBase64 = json.getString("identityKey")
        val identityKeyBytes = Base64.decode(identityKeyBase64, Base64.NO_WRAP)
        val identityKey = IdentityKey(identityKeyBytes, 0)

        // Trust-On-First-Use (TOFU) validation
        // In a real app, you would compare this identityKey with a previously seen one for this contact.
        // Since we extract it directly from the contact record that was populated via QR, 
        // we inherently trust what's currently in the DB.
        
        val registrationId = json.optInt("registrationId", -1)
        val deviceId = json.optInt("deviceId", -1)
        
        if (registrationId <= 0 || registrationId > 16383) {
            throw IllegalArgumentException("Invalid or missing registrationId in contact bundle")
        }
        if (deviceId <= 0) {
            throw IllegalArgumentException("Invalid or missing deviceId in contact bundle")
        }

        if (!json.has("signedPreKeyId") || !json.has("signedPreKey") || !json.has("signedPreKeySignature")) {
            throw IllegalArgumentException("Missing signed prekey information in contact bundle")
        }
        val signedPreKeyId = json.getInt("signedPreKeyId")
        if (signedPreKeyId < 0) {
            throw IllegalArgumentException("Invalid signedPreKeyId in contact bundle")
        }
        
        val signedPreKeyBytes = Base64.decode(json.getString("signedPreKey"), Base64.NO_WRAP)
        val signedPreKeySignature = Base64.decode(json.getString("signedPreKeySignature"), Base64.NO_WRAP)
        
        val oneTimePreKeyId = json.optInt("oneTimePreKeyId", org.signal.libsignal.protocol.state.PreKeyBundle.NULL_PRE_KEY_ID)
        val oneTimePreKeyBytes = if (json.has("oneTimePreKey")) {
            Base64.decode(json.getString("oneTimePreKey"), Base64.NO_WRAP)
        } else null
        
        if (oneTimePreKeyBytes != null && oneTimePreKeyId < 0) {
            throw IllegalArgumentException("oneTimePreKey is present but oneTimePreKeyId is invalid")
        }

        if (!json.has("kyberPreKeyId") || !json.has("kyberPreKey") || !json.has("kyberPreKeySignature")) {
            throw IllegalArgumentException("Missing kyber prekey information in contact bundle")
        }
        val kyberPreKeyId = json.getInt("kyberPreKeyId")
        if (kyberPreKeyId < 0) {
            throw IllegalArgumentException("Invalid kyberPreKeyId in contact bundle")
        }
        val kyberPreKeyBytes = Base64.decode(json.getString("kyberPreKey"), Base64.NO_WRAP)
        val kyberPreKeySignature = Base64.decode(json.getString("kyberPreKeySignature"), Base64.NO_WRAP)

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
}
