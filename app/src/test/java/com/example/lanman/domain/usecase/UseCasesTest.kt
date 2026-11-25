package com.example.lanman.domain.usecase

import android.content.Context
import android.util.Log
import com.example.lanman.data.local.LanmanDatabase
import com.example.lanman.data.network.FileTransferReceiver
import com.example.lanman.data.notification.NotificationPreferences
import com.example.lanman.data.security.SecureKeyStore
import com.example.lanman.data.security.SignalStoreImpl
import com.example.lanman.domain.engine.MessagingEngine
import com.example.lanman.domain.model.FileTransfer
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.domain.model.Profile
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.FileTransferProgressTracker
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.repository.ContactRepository
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
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class UseCasesTest {

    private lateinit var tempDir: File
    private lateinit var tempCacheDir: File
    private lateinit var context: Context

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        tempDir = Files.createTempDirectory("lanman_usecases_files").toFile()
        tempCacheDir = Files.createTempDirectory("lanman_usecases_cache").toFile()
        context = mockk(relaxed = true) {
            every { filesDir } returns tempDir
            every { cacheDir } returns tempCacheDir
        }
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
        tempCacheDir.deleteRecursively()
        unmockkStatic(Log::class)
    }

    @Test
    fun `SendMessageUseCase persists pending message, uses active connection fast path, and debounces double taps`() = runTest {
        val messageRepository = mockk<MessageRepository>(relaxed = true)
        val profileRepository = mockk<ProfileRepository> {
            every { getProfile() } returns flowOf(Profile("me", "Me", null, 1L))
        }
        val connectionManager = mockk<ConnectionManager>(relaxed = true) {
            every { hasActiveConnection("c-online") } returns true
            every { hasActiveConnection("c-offline") } returns false
            coEvery { sendFrame("c-online", any()) } returns Result.success(Unit)
        }
        val encryptionEngine = mockk<EncryptionEngine> {
            coEvery { encrypt("c-online", any()) } returns byteArrayOf(1, 2, 3)
        }

        val useCase = SendMessageUseCase(messageRepository, profileRepository, connectionManager, encryptionEngine)

        // Rapid double tap for same contact: second call within 300ms is debounced
        useCase("c-online", "first")
        useCase("c-online", "second")

        coVerify(exactly = 1) { messageRepository.insertMessage(match { it.status == MessageStatus.PENDING }) }
        coVerify(exactly = 1) { connectionManager.sendFrame("c-online", match { it is Frame.MessageFrame }) }
        coVerify(exactly = 1) {
            messageRepository.updateMessageStatusIfCurrent(any(), MessageStatus.PENDING, MessageStatus.SENT)
        }

        // Offline peer persists locally without attempting socket send
        useCase("c-offline", "queued")
        coVerify(exactly = 2) { messageRepository.insertMessage(match { it.status == MessageStatus.PENDING }) }
        coVerify(exactly = 0) { connectionManager.sendFrame("c-offline", any()) }
    }

    @Test
    fun `MarkMessageReadUseCase respects readReceiptsEnabled preference`() = runTest {
        val incomingDelivered = Message(
            "m-in", "peer-1", MessageDirection.INCOMING, "hi".toByteArray(), MessageType.TEXT, MessageStatus.DELIVERED, 1L
        )
        val messageRepository = mockk<MessageRepository>(relaxed = true) {
            coEvery { getMessageById("m-in") } returns incomingDelivered
        }
        val connectionManager = mockk<ConnectionManager>(relaxed = true) {
            coEvery { sendFrame(any(), any()) } returns Result.success(Unit)
        }
        val prefs = mockk<NotificationPreferences>()
        val useCase = MarkMessageReadUseCase(messageRepository, connectionManager, prefs)

        every { prefs.readReceiptsEnabled } returns false
        useCase("m-in")
        coVerify(exactly = 1) { messageRepository.updateMessageStatus("m-in", MessageStatus.READ) }
        coVerify(exactly = 0) { connectionManager.sendFrame("peer-1", Frame.ReadFrame("m-in")) }

        every { prefs.readReceiptsEnabled } returns true
        useCase("m-in")
        coVerify(exactly = 1) { connectionManager.sendFrame("peer-1", Frame.ReadFrame("m-in")) }
    }

    @Test
    fun `RemoveContactUseCase cleans app private files and leaves external paths intact`() = runTest {
        val receivedDir = File(tempDir, "received_files").apply { mkdirs() }
        val thumbDir = File(tempDir, "thumbnails").apply { mkdirs() }
        val keyDir = File(tempDir, "transfer_keys").apply { mkdirs() }
        val outsideDir = Files.createTempDirectory("lanman_outside").toFile()

        val appFile = File(receivedDir, "doc.txt").apply { writeText("data") }
        val thumbFile = File(thumbDir, "thumb.jpg").apply { writeText("img") }
        val keyFile = File(keyDir, "ft-1").apply { writeText("key") }
        val outsideFile = File(outsideDir, "keep_me.txt").apply { writeText("user file") }

        val contactRepo = mockk<ContactRepository>(relaxed = true)
        val fileRepo = mockk<FileTransferRepository> {
            coEvery { getForContactSync("peer-1") } returns listOf(
                FileTransfer("ft-1", "m-1", "doc.txt", 4L, "text/plain", appFile.absolutePath, thumbFile.absolutePath),
                FileTransfer("ft-2", "m-2", "ext.txt", 9L, "text/plain", outsideFile.absolutePath, null)
            )
        }
        val connMgr = mockk<ConnectionManager>(relaxed = true)
        val engine = mockk<EncryptionEngine>(relaxed = true)
        val receiver = mockk<FileTransferReceiver>(relaxed = true)
        val keyStore = mockk<SecureKeyStore>(relaxed = true)
        val progressTracker = FileTransferProgressTracker()

        val useCase = RemoveContactUseCase(context, contactRepo, fileRepo, connMgr, engine, receiver, keyStore, progressTracker)
        useCase("peer-1")

        try {
            verify { connMgr.disconnectFrom("peer-1") }
            verify { receiver.cancelTransfersForContact("peer-1") }
            verify { keyStore.removeTransferKey("ft-1") }
            coVerify { engine.deleteSession("peer-1") }
            coVerify { contactRepo.deleteContact("peer-1") }

            assertFalse(appFile.exists())
            assertFalse(thumbFile.exists())
            assertFalse(keyFile.exists())
            assertTrue("External file outside app filesDir must not be deleted", outsideFile.exists())
        } finally {
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun `ClearLocalDataUseCase wipes database, keystore, and all private directories`() = runTest {
        val subDirs = listOf("received_files", "outgoing_files", "encrypted_uploads", "transfer_keys", "thumbnails", "voice_recordings")
        val created = subDirs.map { name -> File(tempDir, name).apply { mkdirs(); File(this, "artifact").writeText("x") } }
        val cacheVoice = File(tempCacheDir, "voice_recordings").apply { mkdirs(); File(this, "v.m4a").writeText("x") }
        val cacheImg = File(tempCacheDir, "image_cache").apply { mkdirs(); File(this, "i.png").writeText("x") }

        val db = mockk<LanmanDatabase>(relaxed = true)
        val keyStore = mockk<SecureKeyStore>(relaxed = true)
        val signalStore = mockk<SignalStoreImpl>(relaxed = true)
        val engine = mockk<MessagingEngine>(relaxed = true)
        val tracker = FileTransferProgressTracker()

        val useCase = ClearLocalDataUseCase(db, keyStore, signalStore, engine, tracker, context)
        useCase()

        verify { engine.stop() }
        verify { signalStore.clearInMemorySessions() }
        verify { db.clearAllTables() }
        verify { keyStore.clearAll() }
        created.forEach { assertFalse("${it.name} should be deleted", it.exists()) }
        assertFalse(cacheVoice.exists())
        assertFalse(cacheImg.exists())
    }
}
