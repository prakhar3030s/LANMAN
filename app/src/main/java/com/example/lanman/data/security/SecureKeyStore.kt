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

    private var cachedIdentityKeyPair: IdentityKeyPair? = null
    private val signedPreKeyCache = java.util.concurrent.ConcurrentHashMap<Int, SignedPreKeyRecord>()
    private val preKeyCache = java.util.concurrent.ConcurrentHashMap<Int, PreKeyRecord>()
    private val kyberPreKeyCache = java.util.concurrent.ConcurrentHashMap<Int, KyberPreKeyRecord>()

    fun getIdentityKeyPair(): IdentityKeyPair? {
        cachedIdentityKeyPair?.let { return it }
        val bytes = getBytes("identity_key_pair") ?: return null
        return try {
            val keyPair = IdentityKeyPair(bytes)
            cachedIdentityKeyPair = keyPair
            keyPair
        } catch (e: Exception) {
            null
        }
    }

    fun saveIdentityKeyPair(keyPair: IdentityKeyPair) {
        cachedIdentityKeyPair = keyPair
        saveBytes("identity_key_pair", keyPair.serialize())
    }

    fun getLocalRegistrationId(): Int {
        return prefs.getInt("local_registration_id", 0)
    }

    fun saveLocalRegistrationId(id: Int) {
        prefs.edit().putInt("local_registration_id", id).apply()
    }

    fun getSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord? {
        signedPreKeyCache[signedPreKeyId]?.let { return it }
        val bytes = getBytes("signed_prekey_$signedPreKeyId") ?: return null
        return try {
            val record = SignedPreKeyRecord(bytes)
            signedPreKeyCache[signedPreKeyId] = record
            record
        } catch (e: Exception) {
            null
        }
    }

    fun saveSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signedPreKeyCache[signedPreKeyId] = record
        saveBytes("signed_prekey_$signedPreKeyId", record.serialize())
    }

    fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        val keys = mutableListOf<SignedPreKeyRecord>()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("signed_prekey_")) {
                val id = key.removePrefix("signed_prekey_").toIntOrNull()
                if (id != null) {
                    val record = getSignedPreKey(id)
                    if (record != null) keys.add(record)
                }
            }
        }
        return keys
    }

    fun getPreKey(preKeyId: Int): PreKeyRecord? {
        preKeyCache[preKeyId]?.let { return it }
        val bytes = getBytes("prekey_$preKeyId") ?: return null
        return try {
            val record = PreKeyRecord(bytes)
            preKeyCache[preKeyId] = record
            record
        } catch (e: Exception) {
            null
        }
    }

    fun savePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeyCache[preKeyId] = record
        saveBytes("prekey_$preKeyId", record.serialize())
    }

    fun removePreKey(preKeyId: Int) {
        preKeyCache.remove(preKeyId)
        prefs.edit().remove("prekey_$preKeyId").apply()
    }

    fun containsPreKey(preKeyId: Int): Boolean {
        if (preKeyCache.containsKey(preKeyId)) return true
        return prefs.contains("prekey_$preKeyId")
    }

    fun getKyberPreKey(preKeyId: Int): KyberPreKeyRecord? {
        kyberPreKeyCache[preKeyId]?.let { return it }
        val bytes = getBytes("kyber_prekey_$preKeyId") ?: return null
        return try {
            val record = KyberPreKeyRecord(bytes)
            kyberPreKeyCache[preKeyId] = record
            record
        } catch (e: Exception) {
            android.util.Log.e("SecureKeyStore", "Failed to decode KyberPreKeyRecord", e)
            null
        }
    }

    fun saveKyberPreKey(preKeyId: Int, record: KyberPreKeyRecord) {
        kyberPreKeyCache[preKeyId] = record
        saveBytes("kyber_prekey_$preKeyId", record.serialize())
    }

    fun loadKyberPreKeys(): List<KyberPreKeyRecord> {
        val keys = mutableListOf<KyberPreKeyRecord>()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("kyber_prekey_")) {
                val id = key.removePrefix("kyber_prekey_").toIntOrNull()
                if (id != null) {
                    val record = getKyberPreKey(id)
                    if (record != null) keys.add(record)
                }
            }
        }
        return keys
    }

    fun containsKyberPreKey(preKeyId: Int): Boolean {
        if (kyberPreKeyCache.containsKey(preKeyId)) return true
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
        cachedIdentityKeyPair = null
        signedPreKeyCache.clear()
        preKeyCache.clear()
        kyberPreKeyCache.clear()
        prefs.edit().clear().apply()
    }
}
