package com.example.lanman.data.network

import android.content.Context
import android.util.Log
import com.example.lanman.data.security.SecureKeyStore
import com.example.lanman.domain.model.FileTransfer
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.domain.model.Profile
import com.example.lanman.domain.model.network.DiscoveredPeer
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.FileTransferProgressTracker
import com.example.lanman.domain.network.NetworkDiscoveryManager
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.repository.FileTransferRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.domain.security.EncryptionEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Collections

class MessageDispatcherTest {

    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var networkDiscoveryManager: NetworkDiscoveryManager
    private lateinit var connectionManager: ConnectionManager
    private lateinit var messageRepository: MessageRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var fileTransferRepository: FileTransferRepository
    private lateinit var progressTracker: FileTransferProgressTracker
    private lateinit var encryptionEngine: EncryptionEngine
    private lateinit var secureKeyStore: SecureKeyStore
    private lateinit var dispatcher: MessageDispatcher
    private lateinit var scope: CoroutineScope

    private lateinit var contactsFlow: MutableStateFlow<List<String>>
    private lateinit var peersFlow: MutableSharedFlow<DiscoveredPeer>

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        tempDir = Files.createTempDirectory("lanman_dispatcher_test").toFile()
        context = mockk(relaxed = true) {
            every { filesDir } returns tempDir
        }

        contactsFlow = MutableStateFlow(emptyList())
        peersFlow = MutableSharedFlow(extraBufferCapacity = 16)

        networkDiscoveryManager = mockk(relaxed = true) {
            every { discoveredPeers } returns peersFlow
        }
        connectionManager = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true) {
            every { getContactsWithUnacknowledgedMessagesFlow() } returns contactsFlow
            coEvery { getMessageById(any()) } returns null
        }
        profileRepository = mockk {
            every { getProfile() } returns flowOf(Profile("my-id", "Me", null, 1L))
        }
        fileTransferRepository = mockk(relaxed = true)
        progressTracker = FileTransferProgressTracker()
        encryptionEngine = mockk(relaxed = true) {
            coEvery { encrypt(any(), any()) } returns byteArrayOf(1, 2, 3)
        }
        secureKeyStore = mockk(relaxed = true)

        dispatcher = MessageDispatcher(
            context = context,
            networkDiscoveryManager = networkDiscoveryManager,
            connectionManager = connectionManager,
            messageRepository = messageRepository,
            profileRepository = profileRepository,
            fileTransferRepository = fileTransferRepository,
            progressTracker = progressTracker,
            encryptionEngine = encryptionEngine,
            secureKeyStore = secureKeyStore
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun teardown() {
        dispatcher.stop()
        scope.cancel()
        tempDir.deleteRecursively()
        unmockkStatic(Log::class)
    }

    @Test
    fun `dispatcher flushes pending text messages when peer is discovered and transitions to SENT`() = runBlocking {
        val pending = Message(
            id = "m-1",
            contactId = "peer-1",
            direction = MessageDirection.OUTGOING,
            content = "hello".toByteArray(),
            type = MessageType.TEXT,
            status = MessageStatus.PENDING,
            timestamp = 100L
        )
        coEvery { messageRepository.getUnacknowledgedMessagesForContact("peer-1") } returns listOf(pending)
        coEvery { messageRepository.getMessageById("m-1") } returns pending

        val sentDone = CompletableDeferred<Unit>()
        coEvery { connectionManager.sendFrame("peer-1", any()) } returns Result.success(Unit)
        coEvery {
            messageRepository.updateMessageStatusIfCurrent("m-1", MessageStatus.PENDING, MessageStatus.SENT)
        } answers {
            sentDone.complete(Unit)
            Unit
        }

        dispatcher.start(scope)
        withTimeout(5_000) { peersFlow.subscriptionCount.first { it > 0 } }
        peersFlow.emit(DiscoveredPeer("peer-1", "192.168.1.50", 47475, System.currentTimeMillis()))

        withTimeout(5_000) { sentDone.await() }
        coVerify(exactly = 1) {
            connectionManager.sendFrame(
                "peer-1",
                match { it is Frame.MessageFrame && it.messageId == "m-1" && it.senderId == "my-id" }
            )
        }
    }

    @Test
    fun `dispatcher prioritizes TEXT messages ahead of queued FILE transfers`() = runBlocking {
        val srcFile = File(tempDir, "payload.bin").apply { writeBytes(ByteArray(1024) { 7 }) }
        val fileMsg = Message(
            id = "file-msg-1",
            contactId = "peer-1",
            direction = MessageDirection.OUTGOING,
            content = "File".toByteArray(),
            type = MessageType.FILE,
            status = MessageStatus.PENDING,
            timestamp = 100L // older timestamp!
        )
        val textMsg = Message(
            id = "text-msg-2",
            contactId = "peer-1",
            direction = MessageDirection.OUTGOING,
            content = "urgent".toByteArray(),
            type = MessageType.TEXT,
            status = MessageStatus.PENDING,
            timestamp = 200L
        )
        val transfer = FileTransfer("ft-1", "file-msg-1", "payload.bin", 1024L, "application/octet-stream", srcFile.absolutePath, null)

        coEvery { messageRepository.getUnacknowledgedMessagesForContact("peer-1") } returns listOf(fileMsg, textMsg)
        coEvery { messageRepository.getMessageById("file-msg-1") } returns fileMsg
        coEvery { messageRepository.getMessageById("text-msg-2") } returns textMsg
        coEvery { fileTransferRepository.getByMessageId("file-msg-1") } returns flowOf(transfer)

        val sentOrder = Collections.synchronizedList(mutableListOf<String>())
        val bothFinished = CompletableDeferred<Unit>()
        coEvery { connectionManager.sendFrame("peer-1", any(), any()) } answers {
            when (val f = secondArg<Frame>()) {
                is Frame.MessageFrame -> sentOrder.add("TEXT:${f.messageId}")
                else -> {}
            }
            Result.success(Unit)
        }
        coEvery { connectionManager.sendDataFrame("peer-1", any(), any()) } answers {
            when (val f = secondArg<Frame>()) {
                is Frame.FileOfferFrame -> sentOrder.add("OFFER:${f.fileTransferId}")
                is Frame.FileCompleteFrame -> {
                    sentOrder.add("COMPLETE:${f.fileTransferId}")
                    bothFinished.complete(Unit)
                }
                else -> {}
            }
            Result.success(Unit)
        }

        dispatcher.start(scope)
        contactsFlow.value = listOf("peer-1")

        withTimeout(5_000) { bothFinished.await() }
        assertTrue(sentOrder.indexOf("TEXT:text-msg-2") >= 0)
        assertTrue(sentOrder.indexOf("OFFER:ft-1") > sentOrder.indexOf("TEXT:text-msg-2"))
    }

    @Test
    fun `cancelTransfer skips sending cancelled queued message and send failure halts queue`() = runBlocking {
        val cancelledMsg = Message(
            id = "m-cancel",
            contactId = "peer-1",
            direction = MessageDirection.OUTGOING,
            content = "skip".toByteArray(),
            type = MessageType.TEXT,
            status = MessageStatus.PENDING,
            timestamp = 100L
        )
        val failingMsg = Message(
            id = "m-fail",
            contactId = "peer-1",
            direction = MessageDirection.OUTGOING,
            content = "fail".toByteArray(),
            type = MessageType.TEXT,
            status = MessageStatus.PENDING,
            timestamp = 200L
        )
        val queuedBehind = Message(
            id = "m-behind",
            contactId = "peer-1",
            direction = MessageDirection.OUTGOING,
            content = "behind".toByteArray(),
            type = MessageType.TEXT,
            status = MessageStatus.PENDING,
            timestamp = 300L
        )

        coEvery { messageRepository.getUnacknowledgedMessagesForContact("peer-1") } returns
            listOf(cancelledMsg, failingMsg, queuedBehind)
        coEvery { messageRepository.getMessageById("m-cancel") } returns cancelledMsg.copy(status = MessageStatus.FAILED)
        coEvery { messageRepository.getMessageById("m-fail") } returns failingMsg
        coEvery { messageRepository.getMessageById("m-behind") } returns queuedBehind

        val attemptedIds = Collections.synchronizedList(mutableListOf<String>())
        val failedOnce = CompletableDeferred<Unit>()
        coEvery { connectionManager.sendFrame("peer-1", any()) } answers {
            val f = secondArg<Frame>()
            if (f is Frame.MessageFrame) {
                attemptedIds.add(f.messageId)
                if (f.messageId == "m-fail") failedOnce.complete(Unit)
            }
            Result.failure(RuntimeException("socket offline"))
        }

        dispatcher.cancelTransfer("m-cancel")
        dispatcher.start(scope)
        contactsFlow.value = listOf("peer-1")

        withTimeout(5_000) { failedOnce.await() }
        // Only m-fail should have been attempted: m-cancel was skipped, and failure paused m-behind
        assertEquals(listOf("m-fail"), attemptedIds)
        coVerify(exactly = 0) { messageRepository.updateMessageStatusIfCurrent(any(), any(), MessageStatus.SENT) }
    }
}
