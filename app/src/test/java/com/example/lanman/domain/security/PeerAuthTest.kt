package com.example.lanman.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import java.security.SecureRandom
import java.util.Base64

class PeerAuthTest {

    private fun b64(pair: IdentityKeyPair): String =
        Base64.getEncoder().encodeToString(pair.publicKey.serialize())

    @Test
    fun `challenge sign and verify binds signer, verifier, and channel tag`() {
        val alice = IdentityKeyPair.generate()
        val bob = IdentityKeyPair.generate()
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }

        val sig = PeerAuth.signChallenge(alice, challenge, signerId = "alice", verifierId = "bob", channelTag = "CTRL")

        assertTrue(PeerAuth.verifyChallenge(b64(alice), challenge, sig, "alice", "bob", "CTRL"))
        assertFalse(PeerAuth.verifyChallenge(b64(bob), challenge, sig, "alice", "bob", "CTRL"))
        assertFalse(PeerAuth.verifyChallenge(b64(alice), challenge, sig, "alice", "bob", "DATA"))
        assertFalse(PeerAuth.verifyChallenge(b64(alice), challenge, sig, "mallory", "bob", "CTRL"))
        assertFalse(PeerAuth.verifyChallenge(b64(alice), ByteArray(8), sig, "alice", "bob", "CTRL"))

        val tamperedSig = sig.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        assertFalse(PeerAuth.verifyChallenge(b64(alice), challenge, tamperedSig, "alice", "bob", "CTRL"))
    }

    @Test
    fun `extractIdentityKeyBase64 and parseIdentityKey handle valid and invalid bundles`() {
        val pair = IdentityKeyPair.generate()
        val keyStr = b64(pair)
        val bundle = """{"identityKey":"$keyStr","registrationId":42}""".toByteArray()

        assertEquals(keyStr, PeerAuth.extractIdentityKeyBase64(bundle))
        assertEquals(pair.publicKey, PeerAuth.parseIdentityKey(keyStr))

        assertNull(PeerAuth.extractIdentityKeyBase64(null))
        assertNull(PeerAuth.extractIdentityKeyBase64("not json".toByteArray()))
        assertNull(PeerAuth.extractIdentityKeyBase64("""{"registrationId":42}""".toByteArray()))
        assertNull(PeerAuth.parseIdentityKey("not-valid-base64!!!"))
    }

    @Test
    fun `keyFingerprint and pairwiseSafetyCode are deterministic and order independent`() {
        val alice = b64(IdentityKeyPair.generate())
        val bob = b64(IdentityKeyPair.generate())

        val fpAlice = PeerAuth.keyFingerprint(alice)
        val fpBob = PeerAuth.keyFingerprint(bob)
        assertNotNull(fpAlice)
        assertNotEquals(fpAlice, fpBob)
        assertEquals(6, fpAlice!!.split(" ").size)

        val codeAB = PeerAuth.pairwiseSafetyCode(alice, bob)
        val codeBA = PeerAuth.pairwiseSafetyCode(bob, alice)
        assertNotNull(codeAB)
        assertEquals(codeAB, codeBA)
        assertEquals(8, codeAB!!.split(" ").size)
    }
}
