package com.example.lanman.data.repository

import android.util.Base64
import com.example.lanman.data.local.dao.ProfileDao
import com.example.lanman.data.local.entity.ProfileEntity
import com.example.lanman.data.security.SecureKeyStore
import com.example.lanman.domain.model.Profile
import com.example.lanman.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import org.signal.libsignal.protocol.util.KeyHelper
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val secureKeyStore: SecureKeyStore
) : ProfileRepository {

    override fun getProfile(): Flow<Profile?> {
        return profileDao.getProfile().map { entity ->
            entity?.let { Profile(it.id, it.displayName, it.publicKeyBundle, it.createdAt) }
        }
    }

    override suspend fun createProfile(displayName: String) {
        val identityKeyPair = org.signal.libsignal.protocol.IdentityKeyPair.generate()
        val registrationId = (1..16383).random()
        
        val signedPreKeyKeyPair = org.signal.libsignal.protocol.ecc.ECKeyPair.generate()
        val signedPreKeySignature = identityKeyPair.privateKey.calculateSignature(signedPreKeyKeyPair.publicKey.serialize())
        val signedPreKey = org.signal.libsignal.protocol.state.SignedPreKeyRecord(1, System.currentTimeMillis(), signedPreKeyKeyPair, signedPreKeySignature)
        
        val kyberKeyPair = org.signal.libsignal.protocol.kem.KEMKeyPair.generate(org.signal.libsignal.protocol.kem.KEMKeyType.KYBER_1024)
        val kyberSignature = identityKeyPair.privateKey.calculateSignature(kyberKeyPair.publicKey.serialize())
        val kyberPreKey = org.signal.libsignal.protocol.state.KyberPreKeyRecord(1, System.currentTimeMillis(), kyberKeyPair, kyberSignature)
        
        val oneTimePreKeys = (1..10).map { org.signal.libsignal.protocol.state.PreKeyRecord(it, org.signal.libsignal.protocol.ecc.ECKeyPair.generate()) }

        secureKeyStore.saveIdentityKeyPair(identityKeyPair)
        secureKeyStore.saveLocalRegistrationId(registrationId)
        secureKeyStore.saveSignedPreKey(signedPreKey.id, signedPreKey)
        secureKeyStore.saveKyberPreKey(kyberPreKey.id, kyberPreKey)
        oneTimePreKeys.forEach { secureKeyStore.savePreKey(it.id, it) }

        val json = JSONObject().apply {
            put("identityKey", Base64.encodeToString(identityKeyPair.publicKey.serialize(), Base64.NO_WRAP))
            put("signedPreKeyId", signedPreKey.id)
            put("signedPreKey", Base64.encodeToString(signedPreKey.keyPair.publicKey.serialize(), Base64.NO_WRAP))
            put("signedPreKeySignature", Base64.encodeToString(signedPreKey.signature, Base64.NO_WRAP))
            put("kyberPreKeyId", kyberPreKey.id)
            put("kyberPreKey", Base64.encodeToString(kyberPreKey.keyPair.publicKey.serialize(), Base64.NO_WRAP))
            put("kyberPreKeySignature", Base64.encodeToString(kyberPreKey.signature, Base64.NO_WRAP))
            put("oneTimePreKeyId", oneTimePreKeys[0].id)
            put("oneTimePreKey", Base64.encodeToString(oneTimePreKeys[0].keyPair.publicKey.serialize(), Base64.NO_WRAP))
            put("registrationId", registrationId)
            put("deviceId", 1)
        }

        val profile = ProfileEntity(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            publicKeyBundle = json.toString().toByteArray(Charsets.UTF_8),
            createdAt = System.currentTimeMillis()
        )
        profileDao.upsertProfile(profile)
    }

    override suspend fun upsertProfile(profile: Profile) {
        // Implementation for upserting would use entity mapping
    }

    override suspend fun hasProfile(): Boolean {
        return profileDao.hasProfile()
    }

    override suspend fun updateDisplayName(name: String) {
        profileDao.updateDisplayName(name)
    }
}
