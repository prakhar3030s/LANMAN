package com.example.lanman.domain.security

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object StreamingEncryption {
    const val CHUNK_SIZE = 512 * 1024 // 512 KB
    private const val GCM_TAG_LENGTH_BIT = 128
    private const val ALGORITHM = "AES/GCM/NoPadding"
    
    private val encryptCipherThreadLocal = object : ThreadLocal<Cipher>() {
        override fun initialValue(): Cipher {
            return Cipher.getInstance(ALGORITHM)
        }
    }

    private val decryptCipherThreadLocal = object : ThreadLocal<Cipher>() {
        override fun initialValue(): Cipher {
            return Cipher.getInstance(ALGORITHM)
        }
    }

    /**
     * Encrypts a single chunk of plaintext data (up to 512 KB) using the given chunkIndex.
     */
    fun encryptChunk(
        plaintext: ByteArray,
        offset: Int,
        length: Int,
        chunkIndex: Int,
        aesKey: ByteArray,
        baseNonce: ByteArray
    ): ByteArray {
        val secretKey = SecretKeySpec(aesKey, "AES")
        val chunkNonce = deriveNonce(baseNonce, chunkIndex)
        
        val cipher = encryptCipherThreadLocal.get()!!
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BIT, chunkNonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        return cipher.doFinal(plaintext, offset, length)
    }

    /**
     * Decrypts a single chunk of data (from a FileChunkFrame) using the given chunkIndex.
     */
    fun decryptChunk(
        ciphertext: ByteArray,
        chunkIndex: Int,
        aesKey: ByteArray,
        baseNonce: ByteArray
    ): ByteArray {
        val secretKey = SecretKeySpec(aesKey, "AES")
        val chunkNonce = deriveNonce(baseNonce, chunkIndex)
        
        val cipher = decryptCipherThreadLocal.get()!!
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BIT, chunkNonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Streams data from [inputStream], encrypts it in chunks using AES-GCM, and writes to [outputStream].
     * The nonce for each chunk is derived by XORing the baseNonce with the chunk index.
     */
    fun encryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        aesKey: ByteArray,
        baseNonce: ByteArray
    ) {
        val secretKey = SecretKeySpec(aesKey, "AES")
        val cipher = encryptCipherThreadLocal.get()!!
        val buffer = com.example.lanman.util.network.ByteArrayPool.acquireChunkBuffer()
        var chunkIndex = 0

        try {
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                val chunkNonce = deriveNonce(baseNonce, chunkIndex)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH_BIT, chunkNonce)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

                val ciphertext = cipher.doFinal(buffer, 0, bytesRead)
                
                // Prepend 4-byte length
                val lengthBuffer = ByteBuffer.allocate(4).putInt(ciphertext.size).array()
                outputStream.write(lengthBuffer)
                outputStream.write(ciphertext)

                chunkIndex++
            }
            outputStream.flush()
        } finally {
            com.example.lanman.util.network.ByteArrayPool.releaseChunkBuffer(buffer)
        }
    }

    /**
     * Derives a 12-byte (96-bit) nonce by XORing the base nonce with the chunk index.
     */
    fun deriveNonce(baseNonce: ByteArray, chunkIndex: Int): ByteArray {
        require(baseNonce.size == 12) { "GCM base nonce must be exactly 12 bytes" }
        val derived = baseNonce.copyOf()
        val indexBytes = ByteBuffer.allocate(4).putInt(chunkIndex).array()
        
        // XOR the last 4 bytes of the nonce with the chunk index
        for (i in 0 until 4) {
            derived[8 + i] = (derived[8 + i].toInt() xor indexBytes[i].toInt()).toByte()
        }
        return derived
    }
}
