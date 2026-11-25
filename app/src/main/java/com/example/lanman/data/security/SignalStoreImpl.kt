package com.example.lanman.data.security

import com.example.lanman.data.local.dao.ContactDao
import com.example.lanman.data.local.dao.MessageRequestDao
import com.example.lanman.data.local.dao.SessionDao
import com.example.lanman.data.local.entity.SessionEntity
import com.example.lanman.domain.security.PeerAuth
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalStoreImpl @Inject constructor(
    private val secureKeyStore: SecureKeyStore,
    private val sessionDao: SessionDao,
    private val contactDao: ContactDao,
    private val messageRequestDao: MessageRequestDao
) : SignalProtocolStore {

    override fun getIdentityKeyPair(): IdentityKeyPair {
        return secureKeyStore.getIdentityKeyPair() ?: throw IllegalStateException("IdentityKeyPair not generated")
    }

    override fun getLocalRegistrationId(): Int {
        return secureKeyStore.getLocalRegistrationId()
    }

    override fun saveIdentity(address: SignalProtocolAddress?, identityKey: IdentityKey?): IdentityKeyStore.IdentityChange {
        val existing = getIdentity(address)
        return if (existing == null || existing == identityKey) {
            IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
        } else {
            // Never silently replace a paired contact's pinned identity key outside QR pairing.
            IdentityKeyStore.IdentityChange.REPLACED_EXISTING
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress?,
        identityKey: IdentityKey?,
        direction: IdentityKeyStore.Direction?
    ): Boolean {
        if (address == null || identityKey == null) return false

        val pinned = getIdentity(address)
        if (pinned != null) {
            return pinned == identityKey
        }

        // For an unpaired peer sending a message request, require consistency across multiple
        // messages from the same sender ID so a second device cannot hijack an open request.
        val existingRequest = messageRequestDao.getRequestByContactIdSync(address.name)
        if (existingRequest != null && existingRequest.identityKey.isNotBlank()) {
            val requestKey = PeerAuth.parseIdentityKey(existingRequest.identityKey) ?: return false
            return requestKey == identityKey
        }

        // First-contact message request (receiving only): allow TOFU for request review.
        return direction == IdentityKeyStore.Direction.RECEIVING
    }

    override fun getIdentity(address: SignalProtocolAddress?): IdentityKey? {
        val contactId = address?.name ?: return null
        val contact = contactDao.getContactByIdSync(contactId) ?: return null
        val keyBase64 = PeerAuth.extractIdentityKeyBase64(contact.publicKeyBundle) ?: return null
        return PeerAuth.parseIdentityKey(keyBase64)
    }

    override fun loadPreKey(preKeyId: Int): org.signal.libsignal.protocol.state.PreKeyRecord {
        return secureKeyStore.getPreKey(preKeyId) ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No such prekey $preKeyId")
    }

    override fun storePreKey(preKeyId: Int, record: org.signal.libsignal.protocol.state.PreKeyRecord?) {
        if (record != null) {
            secureKeyStore.savePreKey(preKeyId, record)
        }
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return secureKeyStore.getPreKey(preKeyId) != null
    }

    override fun removePreKey(preKeyId: Int) {
        secureKeyStore.removePreKey(preKeyId)
    }

    private val sessionCache = java.util.concurrent.ConcurrentHashMap<String, SessionRecord>()

    /** Drops cached session records so a local-data reset cannot reuse them. */
    fun clearInMemorySessions() {
        sessionCache.clear()
    }

    private fun sessionKey(address: SignalProtocolAddress?): String {
        return if (address == null) "" else "${address.name}_${address.deviceId}"
    }

    override fun loadSession(address: SignalProtocolAddress?): SessionRecord {
        address ?: return SessionRecord()
        val key = sessionKey(address)
        
        val cached = sessionCache[key]
        if (cached != null) {
            return cached
        }

        val entity = sessionDao.getSession(address.name, address.deviceId)
        val record = if (entity != null) {
            SessionRecord(entity.sessionRecord)
        } else {
            SessionRecord()
        }
        sessionCache[key] = record
        return record
    }

    override fun getSubDeviceSessions(name: String?): MutableList<Int> {
        val sessions = sessionDao.getSubDeviceSessions(name ?: "")
        return sessions.map { it.deviceId }.toMutableList()
    }

    override fun storeSession(address: SignalProtocolAddress?, record: SessionRecord?) {
        if (address != null && record != null) {
            val key = sessionKey(address)
            sessionCache[key] = record

            val entity = SessionEntity(
                addressName = address.name,
                deviceId = address.deviceId,
                sessionRecord = record.serialize()
            )
            sessionDao.insertSession(entity)
        }
    }

    override fun containsSession(address: SignalProtocolAddress?): Boolean {
        if (address == null) return false
        val key = sessionKey(address)
        if (sessionCache.containsKey(key)) return true
        return sessionDao.getSession(address.name, address.deviceId) != null
    }

    override fun deleteSession(address: SignalProtocolAddress?) {
        if (address != null) {
            val key = sessionKey(address)
            sessionCache.remove(key)
            sessionDao.deleteSession(address.name, address.deviceId)
        }
    }

    override fun deleteAllSessions(name: String?) {
        if (name != null) {
            sessionCache.keys.filter { it.startsWith("${name}_") }.forEach { sessionCache.remove(it) }
            sessionDao.deleteAllSessions(name)
        }
    }

    override fun loadSignedPreKey(signedPreKeyId: Int): org.signal.libsignal.protocol.state.SignedPreKeyRecord {
        return secureKeyStore.getSignedPreKey(signedPreKeyId) ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No such signed prekey $signedPreKeyId")
    }

    override fun loadSignedPreKeys(): MutableList<org.signal.libsignal.protocol.state.SignedPreKeyRecord> {
        return secureKeyStore.loadSignedPreKeys().toMutableList()
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: org.signal.libsignal.protocol.state.SignedPreKeyRecord?) {
        if (record != null) {
            secureKeyStore.saveSignedPreKey(signedPreKeyId, record)
        }
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return secureKeyStore.getSignedPreKey(signedPreKeyId) != null
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        // Typically not needed to remove signed pre keys actively except during rotation
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<org.signal.libsignal.protocol.state.SessionRecord> {
        return mutableListOf()
    }

    override fun storeSenderKey(address: SignalProtocolAddress, uuid: java.util.UUID, record: org.signal.libsignal.protocol.groups.state.SenderKeyRecord) {}

    override fun loadSenderKey(address: SignalProtocolAddress, uuid: java.util.UUID): org.signal.libsignal.protocol.groups.state.SenderKeyRecord? {
        return null
    }

    override fun loadKyberPreKey(preKeyId: Int): org.signal.libsignal.protocol.state.KyberPreKeyRecord {
        return secureKeyStore.getKyberPreKey(preKeyId) ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No such kyber prekey $preKeyId")
    }

    override fun loadKyberPreKeys(): MutableList<org.signal.libsignal.protocol.state.KyberPreKeyRecord> {
        return secureKeyStore.loadKyberPreKeys().toMutableList()
    }

    override fun storeKyberPreKey(preKeyId: Int, record: org.signal.libsignal.protocol.state.KyberPreKeyRecord) {
        secureKeyStore.saveKyberPreKey(preKeyId, record)
    }

    override fun containsKyberPreKey(preKeyId: Int): Boolean {
        return secureKeyStore.containsKyberPreKey(preKeyId)
    }

    override fun markKyberPreKeyUsed(preKeyId: Int, usedAt: Int, pubKey: org.signal.libsignal.protocol.ecc.ECPublicKey) {}
}
