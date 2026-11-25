package com.example.lanman.domain.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * Cryptographic helpers for peer handshake authentication and out-of-band fingerprint
 * verification using Signal IdentityKeys (Ed25519 / XEdDSA).
 */
object PeerAuth {

    private const val DOMAIN_SEPARATOR = "LANMAN_AUTH_V2"

    fun extractIdentityKeyBase64(publicKeyBundle: ByteArray?): String? {
        if (publicKeyBundle == null || publicKeyBundle.isEmpty()) return null
        return try {
            val obj = Json.parseToJsonElement(String(publicKeyBundle, StandardCharsets.UTF_8)).jsonObject
            obj["identityKey"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun parseIdentityKey(identityKeyBase64: String?): IdentityKey? {
        if (identityKeyBase64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.getDecoder().decode(identityKeyBase64.trim())
            IdentityKey(bytes, 0)
        } catch (_: Exception) {
            null
        }
    }

    fun buildAuthTranscript(
        challenge: ByteArray,
        signerId: String,
        verifierId: String,
        channelTag: String
    ): ByteArray {
        val prefix = "$DOMAIN_SEPARATOR|$channelTag|$signerId|$verifierId|".toByteArray(StandardCharsets.UTF_8)
        return prefix + challenge
    }

    fun signChallenge(
        identityKeyPair: IdentityKeyPair,
        challenge: ByteArray,
        signerId: String,
        verifierId: String,
        channelTag: String
    ): ByteArray {
        val transcript = buildAuthTranscript(challenge, signerId, verifierId, channelTag)
        return identityKeyPair.privateKey.calculateSignature(transcript)
    }

    fun verifyChallenge(
        identityKeyBase64: String,
        challenge: ByteArray,
        signature: ByteArray,
        signerId: String,
        verifierId: String,
        channelTag: String
    ): Boolean {
        if (challenge.size < 16 || signature.isEmpty()) return false
        val identityKey = parseIdentityKey(identityKeyBase64) ?: return false
        val transcript = buildAuthTranscript(challenge, signerId, verifierId, channelTag)
        return try {
            identityKey.publicKey.verifySignature(transcript, signature)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Produces a short, human-readable verification fingerprint (24 uppercase hex digits grouped
     * by 4) for a single identity key so users can compare codes during QR or manual pairing.
     */
    fun keyFingerprint(identityKeyBase64: String?): String? {
        val key = parseIdentityKey(identityKeyBase64) ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(key.serialize())
        val hex = digest.take(12).joinToString("") { "%02X".format(it) }
        return hex.chunked(4).joinToString(" ")
    }

    /**
     * Symmetric pairwise safety code derived deterministically from both peers' identity keys
     * (order-independent) so both devices show the identical code in Contact Info.
     */
    fun pairwiseSafetyCode(localIdentityKeyBase64: String?, peerIdentityKeyBase64: String?): String? {
        val local = parseIdentityKey(localIdentityKeyBase64)?.serialize() ?: return null
        val peer = parseIdentityKey(peerIdentityKeyBase64)?.serialize() ?: return null
        val first: ByteArray
        val second: ByteArray
        if (compareBytes(local, peer) <= 0) {
            first = local
            second = peer
        } else {
            first = peer
            second = local
        }
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(first)
            update(second)
            digest()
        }
        val hex = digest.take(16).joinToString("") { "%02X".format(it) }
        return hex.chunked(4).joinToString(" ")
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        val min = minOf(a.size, b.size)
        for (i in 0 until min) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }
}
