package com.prakhar.lanman.domain.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

class StreamingEncryptionTest {

    private val random = SecureRandom()

    @Test
    fun `deriveNonce produces distinct nonces per chunk and leaves baseNonce unchanged`() {
        val baseNonce = ByteArray(12) { 0x11 }
        val copy = baseNonce.copyOf()

        val n0 = StreamingEncryption.deriveNonce(baseNonce, 0)
        val n1 = StreamingEncryption.deriveNonce(baseNonce, 1)
        val n2 = StreamingEncryption.deriveNonce(baseNonce, 2)

        assertArrayEquals(copy, baseNonce)
        assertArrayEquals(baseNonce, n0)
        assertFalse(n0.contentEquals(n1))
        assertFalse(n1.contentEquals(n2))
    }

    @Test
    fun `encryptChunk and decryptChunk round-trip with subrange offset`() {
        val aesKey = ByteArray(32).also { random.nextBytes(it) }
        val baseNonce = ByteArray(12).also { random.nextBytes(it) }
        val raw = "HEADER-payload data for chunk 7-TAIL".toByteArray()

        val encrypted = StreamingEncryption.encryptChunk(raw, offset = 7, length = 24, chunkIndex = 7, aesKey = aesKey, baseNonce = baseNonce)
        val decrypted = StreamingEncryption.decryptChunk(encrypted, chunkIndex = 7, aesKey = aesKey, baseNonce = baseNonce)

        assertEquals("payload data for chunk 7", String(decrypted))
    }

    @Test(expected = AEADBadTagException::class)
    fun `decryptChunk throws on wrong chunk index`() {
        val aesKey = ByteArray(32).also { random.nextBytes(it) }
        val baseNonce = ByteArray(12).also { random.nextBytes(it) }
        val encrypted = StreamingEncryption.encryptChunk("secret".toByteArray(), 0, 6, 3, aesKey, baseNonce)

        StreamingEncryption.decryptChunk(encrypted, chunkIndex = 4, aesKey = aesKey, baseNonce = baseNonce)
    }

    @Test(expected = AEADBadTagException::class)
    fun `decryptChunk throws on tampered ciphertext`() {
        val aesKey = ByteArray(32).also { random.nextBytes(it) }
        val baseNonce = ByteArray(12).also { random.nextBytes(it) }
        val encrypted = StreamingEncryption.encryptChunk("secret".toByteArray(), 0, 6, 0, aesKey, baseNonce)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()

        StreamingEncryption.decryptChunk(encrypted, chunkIndex = 0, aesKey = aesKey, baseNonce = baseNonce)
    }

    @Test
    fun `encryptStream splits multi-chunk payload and decryptChunk reconstructs exact bytes`() {
        val aesKey = ByteArray(32).also { random.nextBytes(it) }
        val baseNonce = ByteArray(12).also { random.nextBytes(it) }
        val original = ByteArray(StreamingEncryption.CHUNK_SIZE + 12345).also { random.nextBytes(it) }

        val encryptedOut = ByteArrayOutputStream()
        StreamingEncryption.encryptStream(ByteArrayInputStream(original), encryptedOut, aesKey, baseNonce)

        val buffer = ByteBuffer.wrap(encryptedOut.toByteArray())
        val recovered = ByteArrayOutputStream()
        var chunkIndex = 0
        while (buffer.hasRemaining()) {
            val len = buffer.int
            val cipherChunk = ByteArray(len)
            buffer.get(cipherChunk)
            recovered.write(StreamingEncryption.decryptChunk(cipherChunk, chunkIndex++, aesKey, baseNonce))
        }

        assertEquals(2, chunkIndex)
        assertArrayEquals(original, recovered.toByteArray())
    }
}
