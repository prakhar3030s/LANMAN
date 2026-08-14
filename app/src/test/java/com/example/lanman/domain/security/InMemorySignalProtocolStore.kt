package com.example.lanman.domain.security

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

class InMemorySignalProtocolStore(
    private val identityKeyPair: IdentityKeyPair,
    private val registrationId: Int
) : SignalProtocolStore {

    private val preKeys = mutableMapOf<Int, PreKeyRecord>()
    private val signedPreKeys = mutableMapOf<Int, SignedPreKeyRecord>()
    private val sessions = mutableMapOf<SignalProtocolAddress, SessionRecord>()
    private val identities = mutableMapOf<SignalProtocolAddress, IdentityKey>()

    override fun getIdentityKeyPair(): IdentityKeyPair = identityKeyPair
    override fun getLocalRegistrationId(): Int = registrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey?): IdentityKeyStore.IdentityChange {
        if (identityKey == null) return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
        val existing = identities[address]
        if (identityKey != existing) {
            identities[address] = identityKey
            return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
        }
        return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey?,
        direction: IdentityKeyStore.Direction
    ): Boolean = true

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? = identities[address]

    override fun loadSession(address: SignalProtocolAddress): SessionRecord = sessions[address] ?: SessionRecord()
    override fun getSubDeviceSessions(name: String): List<Int> = emptyList()
    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) { sessions[address] = record }
    override fun containsSession(address: SignalProtocolAddress): Boolean = sessions.containsKey(address)
    override fun deleteSession(address: SignalProtocolAddress) { sessions.remove(address) }
    override fun deleteAllSessions(name: String) { sessions.keys.removeIf { it.name == name } }

    override fun loadPreKey(preKeyId: Int): PreKeyRecord = preKeys[preKeyId] ?: throw IllegalStateException("PreKey $preKeyId not found")
    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) { preKeys[preKeyId] = record }
    override fun containsPreKey(preKeyId: Int): Boolean = preKeys.containsKey(preKeyId)
    override fun removePreKey(preKeyId: Int) { preKeys.remove(preKeyId) }

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord = signedPreKeys[signedPreKeyId] ?: throw IllegalStateException("SignedPreKey $signedPreKeyId not found")
    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = signedPreKeys.values.toList()
    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) { signedPreKeys[signedPreKeyId] = record }
    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = signedPreKeys.containsKey(signedPreKeyId)
    override fun removeSignedPreKey(signedPreKeyId: Int) { signedPreKeys.remove(signedPreKeyId) }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<org.signal.libsignal.protocol.state.SessionRecord> = mutableListOf()
    override fun storeSenderKey(address: SignalProtocolAddress, uuid: java.util.UUID, record: org.signal.libsignal.protocol.groups.state.SenderKeyRecord) {}
    override fun loadSenderKey(address: SignalProtocolAddress, uuid: java.util.UUID): org.signal.libsignal.protocol.groups.state.SenderKeyRecord? = null
    override fun loadKyberPreKey(preKeyId: Int): org.signal.libsignal.protocol.state.KyberPreKeyRecord = throw IllegalStateException("Not implemented")
    override fun loadKyberPreKeys(): MutableList<org.signal.libsignal.protocol.state.KyberPreKeyRecord> = mutableListOf()
    override fun storeKyberPreKey(preKeyId: Int, record: org.signal.libsignal.protocol.state.KyberPreKeyRecord) {}
    override fun containsKyberPreKey(preKeyId: Int): Boolean = false
    override fun markKyberPreKeyUsed(preKeyId: Int, usedAt: Int, pubKey: org.signal.libsignal.protocol.ecc.ECPublicKey) {}
}
