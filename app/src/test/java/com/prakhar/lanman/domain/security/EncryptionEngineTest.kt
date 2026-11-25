package com.prakhar.lanman.domain.security

import android.util.Log
import com.prakhar.lanman.data.local.dao.ContactDao
import com.prakhar.lanman.data.local.dao.MessageRequestDao
import com.prakhar.lanman.data.local.dao.SessionDao
import com.prakhar.lanman.data.local.entity.ContactEntity
import com.prakhar.lanman.data.local.entity.MessageRequestEntity
import com.prakhar.lanman.data.local.entity.SessionEntity
import com.prakhar.lanman.data.security.SecureKeyStore
import com.prakhar.lanman.data.security.SignalStoreImpl
import com.prakhar.lanman.domain.model.Contact
import com.prakhar.lanman.domain.repository.ContactRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class EncryptionEngineTest {

    private class FakePeerNode(val id: String, val registrationId: Int) {
        val identityKeyPair = IdentityKeyPair.generate()
        val signedPreKey: SignedPreKeyRecord
        val kyberPreKey: KyberPreKeyRecord
        val preKeys = ConcurrentHashMap<Int, PreKeyRecord>()
        val sessions = ConcurrentHashMap<Pair<String, Int>, SessionEntity>()
        val contacts = ConcurrentHashMap<String, ContactEntity>()
        val requests = ConcurrentHashMap<String, MessageRequestEntity>()

        init {
            val spkPair = ECKeyPair.generate()
            val spkSig = identityKeyPair.privateKey.calculateSignature(spkPair.publicKey.serialize())
            signedPreKey = SignedPreKeyRecord(1, System.currentTimeMillis(), spkPair, spkSig)

            val kyberPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
            val kyberSig = identityKeyPair.privateKey.calculateSignature(kyberPair.publicKey.serialize())
            kyberPreKey = KyberPreKeyRecord(1, System.currentTimeMillis(), kyberPair, kyberSig)
        }

        fun publicBundleBytes(identityOverride: IdentityKeyPair = identityKeyPair): ByteArray {
            return buildJsonObject {
                put("identityKey", Base64.getEncoder().encodeToString(identityOverride.publicKey.serialize()))
                put("signedPreKeyId", signedPreKey.id)
                put("signedPreKey", Base64.getEncoder().encodeToString(signedPreKey.keyPair.publicKey.serialize()))
                put("signedPreKeySignature", Base64.getEncoder().encodeToString(signedPreKey.signature))
                put("kyberPreKeyId", kyberPreKey.id)
                put("kyberPreKey", Base64.getEncoder().encodeToString(kyberPreKey.keyPair.publicKey.serialize()))
                put("kyberPreKeySignature", Base64.getEncoder().encodeToString(kyberPreKey.signature))
                put("registrationId", registrationId)
                put("deviceId", 1)
            }.toString().toByteArray(Charsets.UTF_8)
        }

        val secureKeyStore: SecureKeyStore = mockk(relaxed = true) {
            every { getIdentityKeyPair() } returns identityKeyPair
            every { getLocalRegistrationId() } returns registrationId
            every { getSignedPreKey(1) } returns signedPreKey
            every { loadSignedPreKeys() } returns listOf(signedPreKey)
            every { getKyberPreKey(1) } returns kyberPreKey
            every { loadKyberPreKeys() } returns listOf(kyberPreKey)
            every { containsKyberPreKey(1) } returns true
            every { getPreKey(any()) } answers { preKeys[firstArg()] }
            every { savePreKey(any(), any()) } answers { preKeys[firstArg()] = secondArg() }
            every { containsPreKey(any()) } answers { preKeys.containsKey(firstArg()) }
            every { removePreKey(any()) } answers { preKeys.remove(firstArg()); Unit }
        }

        val sessionDao: SessionDao = object : SessionDao {
            override fun getSession(addressName: String, deviceId: Int) = sessions[addressName to deviceId]
            override fun getSubDeviceSessions(addressName: String) =
                sessions.values.filter { it.addressName == addressName }
            override fun insertSession(session: SessionEntity) {
                sessions[session.addressName to session.deviceId] = session
            }
            override fun deleteSession(addressName: String, deviceId: Int) {
                sessions.remove(addressName to deviceId)
            }
            override fun deleteAllSessions(addressName: String) {
                sessions.keys.removeIf { it.first == addressName }
            }
        }

        val contactDao: ContactDao = mockk(relaxed = true) {
            every { getContactByIdSync(any()) } answers { contacts[firstArg()] }
        }

        val requestDao: MessageRequestDao = mockk(relaxed = true) {
            every { getRequestByContactIdSync(any()) } answers { requests[firstArg()] }
        }

        val signalStore = SignalStoreImpl(secureKeyStore, sessionDao, contactDao, requestDao)

        val contactRepository: ContactRepository = mockk(relaxed = true) {
            coEvery { getContactById(any()) } answers {
                val c = contacts[firstArg()]
                flowOf(c?.let { Contact(it.id, it.displayName, it.publicKeyBundle, it.lastKnownIp, it.lastSeenAt, it.addedAt) })
            }
        }

        val sessionManager = SessionManager(contactRepository, signalStore)
        val engine = EncryptionEngine(signalStore, sessionManager)

        fun addContact(peer: FakePeerNode, overrideBundle: ByteArray = peer.publicBundleBytes()) {
            contacts[peer.id] = ContactEntity(
                id = peer.id,
                displayName = peer.id,
                publicKeyBundle = overrideBundle,
                lastKnownIp = null,
                lastSeenAt = null,
                addedAt = 1000L
            )
        }
    }

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
    }

    @After
    fun teardown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `encrypt and decrypt full bidirectional and out-of-order Double Ratchet messages`() = runTest {
        val alice = FakePeerNode("alice", 1001)
        val bob = FakePeerNode("bob", 2002)
        alice.addContact(bob)
        bob.addContact(alice)

        val c1 = alice.engine.encrypt("bob", "hello bob 1".toByteArray())
        val c2 = alice.engine.encrypt("bob", "hello bob 2".toByteArray())

        // Out-of-order arrival: c2 arrives before c1
        val d2 = bob.engine.decrypt("alice", c2)
        val d1 = bob.engine.decrypt("alice", c1)
        assertEquals("hello bob 2", String(d2.getOrThrow()))
        assertEquals("hello bob 1", String(d1.getOrThrow()))

        // Bob replies on established ratchet
        val r1 = bob.engine.encrypt("alice", "reply from bob".toByteArray())
        assertEquals("reply from bob", String(alice.engine.decrypt("bob", r1).getOrThrow()))
    }

    @Test
    fun `signalStore enforces pinned identity key and blocks impersonator`() = runTest {
        val alice = FakePeerNode("alice", 1001)
        val bob = FakePeerNode("bob", 2002)
        val mallory = FakePeerNode("bob", 3003) // Claims Bob's ID with a different identity key!

        // Alice has pinned Bob's genuine key; Bob and Mallory each have Alice's public bundle
        alice.addContact(bob)
        bob.addContact(alice)
        mallory.addContact(alice)

        val genuineCipher = bob.engine.encrypt("alice", "genuine".toByteArray())
        // Ensure Alice also accepts Bob's identity key first
        val aliceInitial = alice.engine.decrypt("bob", genuineCipher)
        assertTrue(aliceInitial.isSuccess)

        // Forged message from Mallory claiming Bob's sender ID must fail with UntrustedIdentityException
        val forgedCipher = mallory.engine.encrypt("alice", "forged".toByteArray())
        val decryptAttempt = alice.engine.decrypt("bob", forgedCipher)

        assertTrue(decryptAttempt.isFailure)
        assertTrue(decryptAttempt.exceptionOrNull() is UntrustedIdentityException)

        // Direct identity-key check also rejects Mallory's key for Bob in SENDING direction
        val bobAddr = SignalProtocolAddress("bob", 1)
        assertFalse(
            alice.signalStore.isTrustedIdentity(
                bobAddr,
                mallory.identityKeyPair.publicKey,
                IdentityKeyStore.Direction.SENDING
            )
        )
    }

    @Test
    fun `signalStore enforces consistent identity key across unpaired message requests`() {
        val bob = FakePeerNode("bob", 2002)
        val sender1 = IdentityKeyPair.generate().publicKey
        val sender2 = IdentityKeyPair.generate().publicKey
        val senderAddr = SignalProtocolAddress("unknown-peer", 1)

        // First receiving packet from unknown peer: TOFU allowed for RECEIVING only
        assertTrue(bob.signalStore.isTrustedIdentity(senderAddr, sender1, IdentityKeyStore.Direction.RECEIVING))
        assertFalse(bob.signalStore.isTrustedIdentity(senderAddr, sender1, IdentityKeyStore.Direction.SENDING))

        // Persist message request with sender1 identity key
        bob.requests["unknown-peer"] = MessageRequestEntity(
            contactId = "unknown-peer",
            displayName = "Stranger",
            identityKey = Base64.getEncoder().encodeToString(sender1.serialize()),
            timestamp = 1L,
            firstMessageText = "hi"
        )

        // Second message with same key succeeds; different key impersonation is rejected
        assertTrue(bob.signalStore.isTrustedIdentity(senderAddr, sender1, IdentityKeyStore.Direction.RECEIVING))
        assertFalse(bob.signalStore.isTrustedIdentity(senderAddr, sender2, IdentityKeyStore.Direction.RECEIVING))
    }

    @Test
    fun `deleteSession removes session and subsequent encrypt establishes fresh handshake`() = runTest {
        val alice = FakePeerNode("alice", 1001)
        val bob = FakePeerNode("bob", 2002)
        alice.addContact(bob)
        bob.addContact(alice)

        val msg1 = alice.engine.encrypt("bob", "before reset".toByteArray())
        assertArrayEquals("before reset".toByteArray(), bob.engine.decrypt("alice", msg1).getOrThrow())

        alice.engine.deleteSession("bob")
        assertFalse(alice.signalStore.containsSession(SignalProtocolAddress("bob", 1)))

        val msg2 = alice.engine.encrypt("bob", "after reset".toByteArray())
        assertArrayEquals("after reset".toByteArray(), bob.engine.decrypt("alice", msg2).getOrThrow())
    }

    @Test
    fun `encrypt, decrypt, and deleteSession run session DAO calls off the caller thread`() = runTest {
        val alice = FakePeerNode("alice", 1001)
        val bob = FakePeerNode("bob", 2002)
        alice.addContact(bob)
        bob.addContact(alice)

        val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "fake-main-thread")
        }
        val fakeMainDispatcher = executor.asCoroutineDispatcher()
        try {
            val recordedThreads = java.util.Collections.synchronizedList(mutableListOf<String>())
            val trackedSessionDao = object : SessionDao by alice.sessionDao {
                override fun getSession(addressName: String, deviceId: Int): SessionEntity? {
                    recordedThreads += Thread.currentThread().name
                    return alice.sessionDao.getSession(addressName, deviceId)
                }

                override fun insertSession(session: SessionEntity) {
                    recordedThreads += Thread.currentThread().name
                    alice.sessionDao.insertSession(session)
                }

                override fun deleteSession(addressName: String, deviceId: Int) {
                    recordedThreads += Thread.currentThread().name
                    alice.sessionDao.deleteSession(addressName, deviceId)
                }
            }
            val store = SignalStoreImpl(alice.secureKeyStore, trackedSessionDao, alice.contactDao, alice.requestDao)
            val manager = SessionManager(alice.contactRepository, store)
            val engine = EncryptionEngine(store, manager)

            withContext(fakeMainDispatcher) {
                val cipher = engine.encrypt("bob", "hello".toByteArray())
                assertNotNull(cipher)
                val reply = bob.engine.encrypt("alice", "hi".toByteArray())
                assertArrayEquals("hi".toByteArray(), engine.decrypt("bob", reply).getOrThrow())
                engine.deleteSession("bob")
            }

            assertTrue(recordedThreads.isNotEmpty())
            assertTrue(recordedThreads.none { it == "fake-main-thread" })
        } finally {
            executor.shutdownNow()
        }
    }
}
