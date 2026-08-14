package com.example.lanman.data.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "signal_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getIdentityKeyPair(): IdentityKeyPair? {
        val bytes = getBytes("identity_key_pair") ?: return null
        return try {
            IdentityKeyPair(bytes)
        } catch (e: Exception) {
            null
        }
    }

    fun saveIdentityKeyPair(keyPair: IdentityKeyPair) {
        saveBytes("identity_key_pair", keyPair.serialize())
    }

    fun getLocalRegistrationId(): Int {
        return prefs.getInt("local_registration_id", 0)
    }

    fun saveLocalRegistrationId(id: Int) {
        prefs.edit().putInt("local_registration_id", id).apply()
    }

    fun getSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord? {
        val bytes = getBytes("signed_prekey_$signedPreKeyId") ?: return null
        return try {
            SignedPreKeyRecord(bytes)
        } catch (e: Exception) {
            null
        }
    }

    fun saveSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        saveBytes("signed_prekey_$signedPreKeyId", record.serialize())
    }

    fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        val keys = mutableListOf<SignedPreKeyRecord>()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("signed_prekey_")) {
                val bytes = getBytes(key)
                if (bytes != null) {
                    try {
                        keys.add(SignedPreKeyRecord(bytes))
                    } catch (e: Exception) {
                        // ignore corrupt record
                    }
                }
            }
        }
        return keys
    }

    fun getPreKey(preKeyId: Int): PreKeyRecord? {
        val bytes = getBytes("prekey_$preKeyId") ?: return null
        return try {
            PreKeyRecord(bytes)
        } catch (e: Exception) {
            null
        }
    }

    fun savePreKey(preKeyId: Int, record: PreKeyRecord) {
        saveBytes("prekey_$preKeyId", record.serialize())
    }

    fun removePreKey(preKeyId: Int) {
        prefs.edit().remove("prekey_$preKeyId").apply()
    }

    fun containsPreKey(preKeyId: Int): Boolean {
        return prefs.contains("prekey_$preKeyId")
    }

    fun getKyberPreKey(preKeyId: Int): KyberPreKeyRecord? {
        val bytes = getBytes("kyber_prekey_$preKeyId") ?: return null
        return try {
            KyberPreKeyRecord(bytes)
        } catch (e: Exception) {
            android.util.Log.e("SecureKeyStore", "Failed to decode KyberPreKeyRecord", e)
            null
        }
    }

    fun saveKyberPreKey(preKeyId: Int, record: KyberPreKeyRecord) {
        saveBytes("kyber_prekey_$preKeyId", record.serialize())
    }

    fun loadKyberPreKeys(): List<KyberPreKeyRecord> {
        val keys = mutableListOf<KyberPreKeyRecord>()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("kyber_prekey_")) {
                val bytes = getBytes(key)
                if (bytes != null) {
                    try {
                        keys.add(KyberPreKeyRecord(bytes))
                    } catch (e: Exception) {
                        // ignore corrupt record
                    }
                }
            }
        }
        return keys
    }

    fun containsKyberPreKey(preKeyId: Int): Boolean {
        return prefs.contains("kyber_prekey_$preKeyId")
    }

    private fun getBytes(key: String): ByteArray? {
        val base64 = prefs.getString(key, null) ?: return null
        return Base64.decode(base64, Base64.DEFAULT)
    }

    private fun saveBytes(key: String, bytes: ByteArray) {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        prefs.edit().putString(key, base64).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
