package com.example.lanman.data.network

import android.content.Context
import android.util.Log
import com.example.lanman.data.security.BlockedContactsStore
import com.example.lanman.domain.model.Contact
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.FileTransferProgressTracker
import com.example.lanman.domain.network.IncomingFrameEvent
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.domain.repository.FileTransferRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.security.EncryptionEngine
import com.example.lanman.domain.security.StreamingEncryption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom

class FileTransferReceiverTest {

    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var incomingFlow: MutableSharedFlow<IncomingFrameEvent>
    private lateinit var connectionManager: ConnectionManager
    private lateinit var encryptionEngine: EncryptionEngine
    private lateinit var messageRepository: MessageRepository
    private lateinit var fileTransferRepository: FileTransferRepository
    private lateinit var progressTracker: FileTransferProgressTracker
    private lateinit var contactRepository: ContactRepository
    private lateinit var blockedContactsStore: BlockedContactsStore
    private lateinit var receiver: FileTransferReceiver
    private lateinit var scope: CoroutineScope

    private val contact = Contact("alice", "Alice", null, null, null, 1L)
    private val keyAndNonce = ByteArray(44).also { SecureRandom().nextBytes(it) }
    private val aesKey get() = keyAndNonce.copyOfRange(0, 32)
    private val baseNonce get() = keyAndNonce.copyOfRange(32, 44)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        tempDir = Files.createTempDirectory("lanman_ft_receiver_test").toFile()
        context = mockk(relaxed = true) {
            every { filesDir } returns tempDir
        }

        incomingFlow = MutableSharedFlow(extraBufferCapacity = 32)
        connectionManager = mockk(relaxed = true) {
            every { incomingFrames } returns incomingFlow
            coEvery { sendFrame(any(), any()) } returns Result.success(Unit)
        }
        encryptionEngine = mockk {
            coEvery { decrypt("alice", any()) } returns Result.success(keyAndNonce)
        }
        messageRepository = mockk(relaxed = true) {
            coEvery { getMessageById(any()) } returns null
        }
        fileTransferRepository = mockk(relaxed = true)
        progressTracker = FileTransferProgressTracker()
        contactRepository = mockk {
            every { getContactById("alice") } returns flowOf(contact)
            every { getContactById("unknown") } returns flowOf(null)
            every { getContactById("blocked") } returns flowOf(contact.copy(id = "blocked"))
        }
        blockedContactsStore = mockk {
            every { isBlocked("blocked") } returns true
            every { isBlocked(match { it != "blocked" }) } returns false
        }

        receiver = FileTransferReceiver(
            context = context,
            connectionManager = connectionManager,
            encryptionEngine = encryptionEngine,
            messageRepository = messageRepository,
            fileTransferRepository = fileTransferRepository,
            progressTracker = progressTracker,
            contactRepository = contactRepository,
            blockedContactsStore = blockedContactsStore
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        receiver.start(scope)
        runBlocking { incomingFlow.subscriptionCount.first { it > 0 } }
    }

    @After
    fun teardown() {
        receiver.stop()
        scope.cancel()
        tempDir.deleteRecursively()
        unmockkStatic(Log::class)
    }

    @Test
    fun `unpaired or blocked file offer is ignored safely without touching repositories`() = runBlocking {
        val ackSentinel = CompletableDeferred<Unit>()
        coEvery { encryptionEngine.decrypt("unknown", any()) } returns Result.success(keyAndNonce)
        coEvery { encryptionEngine.decrypt("blocked", any()) } returns Result.success(keyAndNonce)

        // Emit invalid offers first, followed by a valid ping-style offer probe on alice that records decrypt
        coEvery { encryptionEngine.decrypt("alice", byteArrayOf(99)) } answers {
            ackSentinel.complete(Unit)
            Result.failure(RuntimeException("stop sentinel"))
        }

        incomingFlow.emit(
            IncomingFrameEvent(
                "unknown",
                Frame.FileOfferFrame("m1", "ft1", "a.txt", 10, "text/plain", 512 * 1024, 1, byteArrayOf(1), 1L)
            )
        )
        incomingFlow.emit(
            IncomingFrameEvent(
                "blocked",
                Frame.FileOfferFrame("m2", "ft2", "b.txt", 10, "text/plain", 512 * 1024, 1, byteArrayOf(2), 2L)
            )
        )
        incomingFlow.emit(
            IncomingFrameEvent(
                "alice",
                Frame.FileOfferFrame("m-sentinel", "ft-s", "s.txt", 10, "text/plain", 512 * 1024, 1, byteArrayOf(99), 3L)
            )
        )

        withTimeout(5_000) { ackSentinel.await() }
        coVerify(exactly = 0) { messageRepository.insertMessage(match { it.id == "m1" || it.id == "m2" }) }
        coVerify(exactly = 0) { fileTransferRepository.insertFileTransfer(match { it.id == "ft1" || it.id == "ft2" }) }
    }

    @Test
    fun `complete transfer decrypts chunks, verifies sha256, writes file, and sends ACK`() = runBlocking {
        val chunk0 = "Hello LANMAN ".toByteArray()
        val chunk1 = "secure transfer!".toByteArray()
        val fullPayload = chunk0 + chunk1
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(fullPayload)

        val savedPathSlot = slot<String>()
        val deliveredDone = CompletableDeferred<Unit>()
        coEvery { fileTransferRepository.updateLocalUri("ft-ok", capture(savedPathSlot)) } returns Unit
        coEvery { messageRepository.updateMessageStatus("msg-ok", MessageStatus.DELIVERED) } answers {
            deliveredDone.complete(Unit)
            Unit
        }

        incomingFlow.emit(
            IncomingFrameEvent(
                "alice",
                Frame.FileOfferFrame(
                    messageId = "msg-ok",
                    fileTransferId = "ft-ok",
                    fileName = "../evil/report.txt",
                    fileSizeBytes = fullPayload.size.toLong(),
                    mimeType = "text/plain",
                    chunkSize = StreamingEncryption.CHUNK_SIZE,
                    totalChunks = 2,
                    encryptedKeyEnvelope = byteArrayOf(1, 2),
                    senderTimestamp = 100L
                )
            )
        )
        incomingFlow.emit(
            IncomingFrameEvent(
                "alice",
                Frame.FileChunkFrame("ft-ok", 0, StreamingEncryption.encryptChunk(chunk0, 0, chunk0.size, 0, aesKey, baseNonce))
            )
        )
        incomingFlow.emit(
            IncomingFrameEvent(
                "alice",
                Frame.FileChunkFrame("ft-ok", 1, StreamingEncryption.encryptChunk(chunk1, 0, chunk1.size, 1, aesKey, baseNonce))
            )
        )
        incomingFlow.emit(IncomingFrameEvent("alice", Frame.FileHashFrame("ft-ok", expectedHash)))
        incomingFlow.emit(IncomingFrameEvent("alice", Frame.FileCompleteFrame("ft-ok")))

        withTimeout(5_000) { deliveredDone.await() }
        val finalFile = File(savedPathSlot.captured)
        assertTrue(finalFile.exists())
        // Path traversal in original file name must be sanitized to within received_files
        assertTrue(finalFile.canonicalPath.startsWith(File(tempDir, "received_files").canonicalPath))
        assertArrayEquals(fullPayload, finalFile.readBytes())
        coVerify { connectionManager.sendFrame("alice", Frame.AckFrame("msg-ok")) }
    }

    @Test
    fun `early chunks racing ahead of FileOfferFrame replay cleanly and complete`() = runBlocking {
        val payload = "Early-arrival payload".toByteArray()
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(payload)

        val savedPathSlot = slot<String>()
        val deliveredDone = CompletableDeferred<Unit>()
        coEvery { fileTransferRepository.updateLocalUri("ft-early", capture(savedPathSlot)) } returns Unit
        coEvery { messageRepository.updateMessageStatus("msg-early", MessageStatus.DELIVERED) } answers {
            deliveredDone.complete(Unit)
            Unit
        }

        // Data channel delivers chunk + hash + complete BEFORE control channel delivers offer
        incomingFlow.emit(
            IncomingFrameEvent(
                "alice",
                Frame.FileChunkFrame("ft-early", 0, StreamingEncryption.encryptChunk(payload, 0, payload.size, 0, aesKey, baseNonce)),
                isDataChannel = true
            )
        )
        incomingFlow.emit(IncomingFrameEvent("alice", Frame.FileHashFrame("ft-early", expectedHash), isDataChannel = true))
        incomingFlow.emit(IncomingFrameEvent("alice", Frame.FileCompleteFrame("ft-early"), isDataChannel = true))
        incomingFlow.emit(
            IncomingFrameEvent(
                "alice",
                Frame.FileOfferFrame(
                    messageId = "msg-early",
                    fileTransferId = "ft-early",
                    fileName = "voice.m4a",
                    fileSizeBytes = payload.size.toLong(),
                    mimeType = "audio/lanman-voice;duration=1500",
                    chunkSize = StreamingEncryption.CHUNK_SIZE,
                    totalChunks = 1,
                    encryptedKeyEnvelope = byteArrayOf(7),
                    senderTimestamp = 200L
                )
            )
        )

        withTimeout(5_000) { deliveredDone.await() }
        assertArrayEquals(payload, File(savedPathSlot.captured).readBytes())
        assertEquals(0L, progressTracker.progressMap.value["ft-early"]?.toLong() ?: 0L)
    }

    @Test
    fun `sha256 mismatch fails transfer cleanly without sending ACK`() = runBlocking {
        val payload = "tampered test bytes".toByteArray()
        val wrongHash = ByteArray(32) { 0x42 }

        val failedDone = CompletableDeferred<Unit>()
        coEvery {
            messageRepository.updateMessageStatusIfCurrent("msg-bad", MessageStatus.PENDING, MessageStatus.FAILED)
        } answers {
            failedDone.complete(Unit)
            Unit
        }

        incomingFlow.emit(
            IncomingFrameEvent(
                "alice",
                Frame.FileOfferFrame("msg-bad", "ft-bad", "bad.bin", payload.size.toLong(), "application/octet-stream", StreamingEncryption.CHUNK_SIZE, 1, byteArrayOf(3), 300L)
            )
        )
        incomingFlow.emit(
            IncomingFrameEvent(
                "alice",
                Frame.FileChunkFrame("ft-bad", 0, StreamingEncryption.encryptChunk(payload, 0, payload.size, 0, aesKey, baseNonce))
            )
        )
        incomingFlow.emit(IncomingFrameEvent("alice", Frame.FileHashFrame("ft-bad", wrongHash)))
        incomingFlow.emit(IncomingFrameEvent("alice", Frame.FileCompleteFrame("ft-bad")))

        withTimeout(5_000) { failedDone.await() }
        coVerify(exactly = 0) { connectionManager.sendFrame("alice", Frame.AckFrame("msg-bad")) }
        assertFalse(File(File(tempDir, "received_files"), "ft-bad.bin").exists())
    }
}
