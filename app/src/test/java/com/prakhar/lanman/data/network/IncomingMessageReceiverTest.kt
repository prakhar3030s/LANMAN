package com.prakhar.lanman.data.network

import android.content.Context
import android.util.Log
import com.prakhar.lanman.data.notification.NotificationHelper
import com.prakhar.lanman.data.security.BlockedContactsStore
import com.prakhar.lanman.data.security.SecureKeyStore
import com.prakhar.lanman.domain.model.Contact
import com.prakhar.lanman.domain.model.FileTransfer
import com.prakhar.lanman.domain.model.Message
import com.prakhar.lanman.domain.model.MessageDirection
import com.prakhar.lanman.domain.model.MessageRequest
import com.prakhar.lanman.domain.model.MessageStatus
import com.prakhar.lanman.domain.model.MessageType
import com.prakhar.lanman.domain.model.Profile
import com.prakhar.lanman.domain.network.ActiveConversationTracker
import com.prakhar.lanman.domain.network.ConnectionManager
import com.prakhar.lanman.domain.network.IncomingFrameEvent
import com.prakhar.lanman.domain.network.protocol.Frame
import com.prakhar.lanman.domain.repository.ContactRepository
import com.prakhar.lanman.domain.repository.FileTransferRepository
import com.prakhar.lanman.domain.repository.MessageRepository
import com.prakhar.lanman.domain.repository.MessageRequestRepository
import com.prakhar.lanman.domain.repository.ProfileRepository
import com.prakhar.lanman.domain.security.EncryptionEngine
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class IncomingMessageReceiverTest {

    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var incomingFlow: MutableSharedFlow<IncomingFrameEvent>
    private lateinit var connectionManager: ConnectionManager
    private lateinit var messageRepository: MessageRepository
    private lateinit var fileTransferRepository: FileTransferRepository
    private lateinit var contactRepository: ContactRepository
    private lateinit var encryptionEngine: EncryptionEngine
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var activeConversationTracker: ActiveConversationTracker
    private lateinit var profileRepository: ProfileRepository
    private lateinit var messageRequestRepository: MessageRequestRepository
    private lateinit var blockedContactsStore: BlockedContactsStore
    private lateinit var secureKeyStore: SecureKeyStore
    private lateinit var receiver: IncomingMessageReceiver
    private lateinit var scope: CoroutineScope

    private val validBundle = """{"identityKey":"SAME_KEY","registrationId":42}""".toByteArray()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        tempDir = Files.createTempDirectory("lanman_receiver_test").toFile()
        context = mockk(relaxed = true) {
            every { filesDir } returns tempDir
        }

        incomingFlow = MutableSharedFlow(extraBufferCapacity = 16)
        connectionManager = mockk(relaxed = true) {
            every { incomingFrames } returns incomingFlow
            coEvery { sendFrame(any(), any()) } returns Result.success(Unit)
        }
        messageRepository = mockk(relaxed = true)
        fileTransferRepository = mockk(relaxed = true)
        contactRepository = mockk(relaxed = true)
        encryptionEngine = mockk(relaxed = true)
        notificationHelper = mockk(relaxed = true)
        activeConversationTracker = ActiveConversationTracker()
        profileRepository = mockk {
            every { getProfile() } returns flowOf(Profile("me", "MyName", validBundle, 1L))
        }
        messageRequestRepository = mockk(relaxed = true) {
            every { getAllMessageRequests() } returns flowOf(emptyList())
        }
        blockedContactsStore = mockk(relaxed = true) {
            every { isBlocked(any()) } returns false
        }
        secureKeyStore = mockk(relaxed = true)

        receiver = IncomingMessageReceiver(
            context = context,
            connectionManager = connectionManager,
            messageRepository = messageRepository,
            fileTransferRepository = fileTransferRepository,
            contactRepository = contactRepository,
            encryptionEngine = encryptionEngine,
            notificationHelper = notificationHelper,
            activeConversationTracker = activeConversationTracker,
            profileRepository = profileRepository,
            messageRequestRepository = messageRequestRepository,
            blockedContactsStore = blockedContactsStore,
            secureKeyStore = secureKeyStore
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        receiver.start(scope)
    }

    @After
    fun teardown() {
        receiver.stop()
        scope.cancel()
        tempDir.deleteRecursively()
        unmockkStatic(Log::class)
    }

    @Test
    fun `paired message persists, ACKs, notifies when inactive, and skips notify on duplicate`() = runBlocking {
        val contact = Contact("alice", "Alice", validBundle, null, null, 1L)
        every { contactRepository.getContactById("alice") } returns flowOf(contact)
        coEvery { encryptionEngine.decrypt("alice", any()) } returns Result.success("hi!".toByteArray())
        coEvery { messageRepository.insertIfNotExists(any()) } returnsMany listOf(true, false)

        val ackCount = java.util.concurrent.atomic.AtomicInteger(0)
        val twoAcks = CompletableDeferred<Unit>()
        coEvery { connectionManager.sendFrame("alice", match { it is Frame.AckFrame }) } answers {
            if (ackCount.incrementAndGet() >= 2) twoAcks.complete(Unit)
            Result.success(Unit)
        }

        val frame = Frame.MessageFrame("m-1", "alice", 0, 1000L, byteArrayOf(1))
        incomingFlow.emit(IncomingFrameEvent("alice", frame))
        incomingFlow.emit(IncomingFrameEvent("alice", frame))

        withTimeout(5_000) { twoAcks.await() }
        coVerify(exactly = 1) { notificationHelper.showNewMessageNotification("alice", "Alice", any()) }
    }

    @Test
    fun `blocked sender message is acknowledged without decrypting or persisting`() = runBlocking {
        every { blockedContactsStore.isBlocked("bad-peer") } returns true
        val ackSent = CompletableDeferred<Unit>()
        coEvery { connectionManager.sendFrame("bad-peer", Frame.AckFrame("m-b")) } answers {
            ackSent.complete(Unit)
            Result.success(Unit)
        }

        incomingFlow.emit(IncomingFrameEvent("bad-peer", Frame.MessageFrame("m-b", "bad-peer", 0, 1L, byteArrayOf(9))))
        withTimeout(5_000) { ackSent.await() }

        coVerify(exactly = 0) { encryptionEngine.decrypt(any(), any()) }
        coVerify(exactly = 0) { messageRepository.insertIfNotExists(any()) }
    }

    @Test
    fun `unknown sender messages combine into MessageRequest preview`() = runBlocking {
        every { contactRepository.getContactById("stranger") } returns flowOf(null)
        coEvery { encryptionEngine.decrypt("stranger", any()) } returnsMany listOf(
            Result.success("First line".toByteArray()),
            Result.success("First line".toByteArray()),
            Result.success("Second line".toByteArray())
        )

        val existing = MessageRequest("stranger", "Stranger", "ID_KEY", 10L, "First line")
        every { messageRequestRepository.getAllMessageRequests() } returnsMany listOf(
            flowOf(emptyList()),
            flowOf(listOf(existing))
        )

        val savedRequests = mutableListOf<MessageRequest>()
        val secondDone = CompletableDeferred<Unit>()
        coEvery { messageRequestRepository.addMessageRequest(capture(savedRequests)) } answers {
            if (savedRequests.size >= 2) secondDone.complete(Unit)
            Unit
        }

        incomingFlow.emit(
            IncomingFrameEvent("stranger", Frame.MessageFrame("r-1", "stranger", 0, 10L, byteArrayOf(1)), isUnknown = true, unknownDisplayName = "Stranger", unknownIdentityKey = "ID_KEY")
        )
        // Re-transmitted duplicate r-1 must re-ACK without appending duplicate text
        incomingFlow.emit(
            IncomingFrameEvent("stranger", Frame.MessageFrame("r-1", "stranger", 0, 10L, byteArrayOf(1)), isUnknown = true, unknownDisplayName = "Stranger", unknownIdentityKey = "ID_KEY")
        )
        incomingFlow.emit(
            IncomingFrameEvent("stranger", Frame.MessageFrame("r-2", "stranger", 0, 20L, byteArrayOf(2)), isUnknown = true, unknownDisplayName = "Stranger", unknownIdentityKey = "ID_KEY")
        )

        withTimeout(5_000) { secondDone.await() }
        assertEquals(2, savedRequests.size)
        assertEquals("First line", savedRequests[0].firstMessageText)
        assertEquals("First line\nSecond line", savedRequests[1].firstMessageText)
        coVerify(exactly = 2) { connectionManager.sendFrame("stranger", Frame.AckFrame("r-1")) }
    }

    @Test
    fun `decrypt failure sends KeyBundleRequestFrame and never sends ACK`() = runBlocking {
        coEvery { encryptionEngine.decrypt("alice", any()) } returns Result.failure(RuntimeException("bad ratchet"))
        val reqSent = CompletableDeferred<Unit>()
        coEvery { connectionManager.sendFrame("alice", Frame.KeyBundleRequestFrame("alice")) } answers {
            reqSent.complete(Unit)
            Result.success(Unit)
        }

        incomingFlow.emit(IncomingFrameEvent("alice", Frame.MessageFrame("m-fail", "alice", 0, 1L, byteArrayOf(5))))
        withTimeout(5_000) { reqSent.await() }

        coVerify(exactly = 0) { connectionManager.sendFrame("alice", match { it is Frame.AckFrame }) }
    }

    @Test
    fun `ack cleans staged file artifacts and updates status while read marks READ`() = runBlocking {
        val outgoingDir = File(tempDir, "outgoing_files").apply { mkdirs() }
        val stagedFile = File(outgoingDir, "ft-1").apply { writeText("staged") }
        val outgoingMsg = Message("m-file", "alice", MessageDirection.OUTGOING, ByteArray(0), MessageType.FILE, MessageStatus.SENT, 1L)
        val outgoingText = Message("m-txt", "alice", MessageDirection.OUTGOING, "hi".toByteArray(), MessageType.TEXT, MessageStatus.DELIVERED, 2L)
        val incomingText = Message("m-incoming", "alice", MessageDirection.INCOMING, "yo".toByteArray(), MessageType.TEXT, MessageStatus.DELIVERED, 3L)

        coEvery { messageRepository.getMessageById("m-file") } returns outgoingMsg
        coEvery { messageRepository.getMessageById("m-txt") } returns outgoingText
        coEvery { messageRepository.getMessageById("m-incoming") } returns incomingText
        every { fileTransferRepository.getByMessageId("m-file") } returns flowOf(
            FileTransfer("ft-1", "m-file", "doc.pdf", 6L, "application/pdf", stagedFile.absolutePath, null)
        )

        val readDone = CompletableDeferred<Unit>()
        coEvery { messageRepository.updateMessageStatus("m-file", MessageStatus.DELIVERED) } returns Unit
        coEvery { messageRepository.updateMessageStatus("m-txt", MessageStatus.READ) } answers {
            readDone.complete(Unit)
            Unit
        }

        incomingFlow.emit(IncomingFrameEvent("alice", Frame.AckFrame("m-file")))
        incomingFlow.emit(IncomingFrameEvent("alice", Frame.ReadFrame("m-incoming")))
        incomingFlow.emit(IncomingFrameEvent("alice", Frame.ReadFrame("m-txt")))

        withTimeout(5_000) { readDone.await() }
        coVerify(exactly = 0) { messageRepository.updateMessageStatus("m-incoming", MessageStatus.READ) }
        coVerify { secureKeyStore.removeTransferKey("ft-1") }
        assertFalse(stagedFile.exists())
    }

    @Test
    fun `keyBundleResponse rejects mismatched identityKey and accepts matching identityKey`() = runBlocking {
        val contact = Contact("alice", "Alice", validBundle, null, null, 1L)
        every { contactRepository.getContactById("alice") } returns flowOf(contact)

        val matchingNewBundle = """{"identityKey":"SAME_KEY","registrationId":99}""".toByteArray()
        val forgedBundle = """{"identityKey":"DIFFERENT_KEY","registrationId":99}""".toByteArray()

        val upserted = slot<Contact>()
        val accepted = CompletableDeferred<Unit>()
        coEvery { contactRepository.upsertContact(capture(upserted)) } answers {
            accepted.complete(Unit)
            Unit
        }

        // First send forged key -> must be ignored
        incomingFlow.emit(IncomingFrameEvent("alice", Frame.KeyBundleResponseFrame(forgedBundle)))
        // Then send matching key -> must update bundle
        incomingFlow.emit(IncomingFrameEvent("alice", Frame.KeyBundleResponseFrame(matchingNewBundle)))

        withTimeout(5_000) { accepted.await() }
        coVerify(exactly = 1) { contactRepository.upsertContact(any()) }
        assertTrue(upserted.captured.publicKeyBundle!!.contentEquals(matchingNewBundle))
    }
}
