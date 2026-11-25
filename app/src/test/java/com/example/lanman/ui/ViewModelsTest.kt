package com.example.lanman.ui

import android.util.Log
import com.example.lanman.data.security.BlockedContactsStore
import com.example.lanman.data.security.MutedChatsStore
import com.example.lanman.domain.engine.MessagingEngine
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageRequest
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.domain.model.Profile
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.LanNetworkState
import com.example.lanman.domain.network.NetworkDiscoveryManager
import com.example.lanman.domain.network.PresenceTracker
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.qr.ContactQrPayload
import com.example.lanman.domain.qr.QrPayloadCodec
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.repository.MessageRequestRepository
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.domain.security.EncryptionEngine
import com.example.lanman.domain.usecase.MarkMessageReadUseCase
import com.example.lanman.ui.chat.ChatListViewModel
import com.example.lanman.ui.chat.MessageRequestsViewModel
import com.example.lanman.ui.onboarding.OnboardingEvent
import com.example.lanman.ui.onboarding.OnboardingViewModel
import com.example.lanman.ui.qr.ScanContactState
import com.example.lanman.ui.qr.ScanContactViewModel
import com.example.lanman.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun teardown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `OnboardingViewModel validates name rules and prevents duplicate profile creation`() = runTest {
        val repo = mockk<ProfileRepository>(relaxed = true) {
            coEvery { hasProfile() } returnsMany listOf(false, true)
        }
        val vm = OnboardingViewModel(repo)

        vm.onDisplayNameChanged("   ")
        vm.submitProfile()
        assertEquals("Name cannot be empty", vm.errorMessage.value)

        vm.onDisplayNameChanged("A".repeat(31))
        vm.submitProfile()
        assertEquals("Name must be 30 characters or less", vm.errorMessage.value)

        vm.onDisplayNameChanged("  Alice  ")
        vm.submitProfile()
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.createProfile("Alice") }
        assertTrue(vm.events.first() is OnboardingEvent.NavigateToHome)

        // Second submission sees hasProfile() == true and skips createProfile
        vm.submitProfile()
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.createProfile(any()) }
    }

    @Test
    fun `ScanContactViewModel rejects self, parses valid QR, flags changed identity key, and deletes old session`() = runBlocking {
        val myKey = IdentityKeyPair.generate()
        val peerKey1 = IdentityKeyPair.generate()
        val peerKey2 = IdentityKeyPair.generate()

        fun gzipBundleB64(pair: IdentityKeyPair): String {
            val json = buildJsonObject {
                put("identityKey", Base64.getEncoder().encodeToString(pair.publicKey.serialize()))
                put("registrationId", 101)
            }.toString().toByteArray()
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { it.write(json) }
            return Base64.getEncoder().encodeToString(bos.toByteArray())
        }

        val myProfile = Profile("me", "Me", null, 1L)
        val profileRepo = mockk<ProfileRepository> { every { getProfile() } returns flowOf(myProfile) }
        val contactRepo = mockk<ContactRepository>(relaxed = true)
        val reqRepo = mockk<MessageRequestRepository>(relaxed = true)
        val engine = mockk<EncryptionEngine>(relaxed = true)

        val vm = ScanContactViewModel(profileRepo, contactRepo, reqRepo, engine)

        // 1. Invalid code
        vm.onCodeScannedOrEntered("garbage")
        withTimeout(5_000) { vm.state.first { it !is ScanContactState.Validating } }
        assertTrue(vm.state.value is ScanContactState.Error)

        // 2. Self scan
        val selfQr = QrPayloadCodec.encode(
            ContactQrPayload(3, "me", "Me", identityKey = Base64.getEncoder().encodeToString(myKey.publicKey.serialize()))
        )
        vm.onCodeScannedOrEntered(selfQr)
        withTimeout(5_000) { vm.state.first { it !is ScanContactState.Validating } }
        assertTrue((vm.state.value as ScanContactState.Error).message.contains("yourself"))

        // 3. New peer scan
        vm.resetToScanning()
        val existingBundle1 = buildJsonObject {
            put("identityKey", Base64.getEncoder().encodeToString(peerKey1.publicKey.serialize()))
        }.toString().toByteArray()
        every { contactRepo.getContactById("bob") } returns flowOf(null)
        val bobQr1 = QrPayloadCodec.encode(ContactQrPayload(3, "bob", "Bob", fullBundleBase64 = gzipBundleB64(peerKey1)))
        vm.onCodeScannedOrEntered(bobQr1)
        withTimeout(5_000) { vm.state.first { it !is ScanContactState.Validating } }
        val newState = vm.state.value as ScanContactState.ConfirmationRequired
        assertFalse(newState.isUpdate)
        assertFalse(newState.identityKeyChanged)
        assertNotNull(newState.fingerprint)

        // 4. Existing contact re-scan with a DIFFERENT identity key -> security flag + session reset on confirm
        every { contactRepo.getContactById("bob") } returns flowOf(
            com.example.lanman.domain.model.Contact("bob", "Bob", existingBundle1, null, null, 1L)
        )
        vm.resetToScanning()
        val bobPayload2 = ContactQrPayload(3, "bob", "Bob New", fullBundleBase64 = gzipBundleB64(peerKey2))
        vm.onCodeScannedOrEntered(QrPayloadCodec.encode(bobPayload2))
        withTimeout(5_000) { vm.state.first { it !is ScanContactState.Validating } }
        val changedState = vm.state.value as ScanContactState.ConfirmationRequired
        assertTrue(changedState.isUpdate)
        assertTrue(changedState.identityKeyChanged)

        vm.confirmAddContact(bobPayload2)
        withTimeout(5_000) { vm.state.first { it is ScanContactState.Success || it is ScanContactState.Error } }
        assertTrue(vm.state.value is ScanContactState.Success)
        coVerify { contactRepo.upsertContact(match { it.id == "bob" && it.displayName == "Bob New" }) }
        coVerify { engine.deleteSession("bob") }
        coVerify { reqRepo.deleteMessageRequest("bob") }
    }

    @Test
    fun `MessageRequestsViewModel accept, reject-with-undo, and block preserve integrity`() = runTest {
        val reqRepo = mockk<MessageRequestRepository>(relaxed = true) {
            every { getAllMessageRequests() } returns flowOf(emptyList())
        }
        val contactRepo = mockk<ContactRepository>(relaxed = true)
        val msgRepo = mockk<MessageRepository>(relaxed = true)
        val connMgr = mockk<ConnectionManager>(relaxed = true) {
            coEvery { sendFrame(any(), any()) } returns Result.success(Unit)
        }
        val blockedStore = mockk<BlockedContactsStore>(relaxed = true)

        val vm = MessageRequestsViewModel(reqRepo, contactRepo, msgRepo, connMgr, blockedStore)
        val request = MessageRequest("peer-x", "Xavier", "KEY_B64", 12345L, "Hey there!")

        vm.acceptRequest(request)
        advanceUntilIdle()
        coVerify { contactRepo.upsertContact(match { it.id == "peer-x" && it.displayName == "Xavier" }) }
        coVerify {
            msgRepo.insertIfNotExists(
                match {
                    it.contactId == "peer-x" &&
                        String(it.content) == "Hey there!" &&
                        it.direction == MessageDirection.INCOMING &&
                        it.status == MessageStatus.DELIVERED
                }
            )
        }
        coVerify { reqRepo.deleteMessageRequest("peer-x") }
        coVerify { connMgr.sendFrame("peer-x", Frame.KeyBundleRequestFrame("peer-x")) }

        vm.rejectRequest(request)
        advanceUntilIdle()
        coVerify(exactly = 2) { reqRepo.deleteMessageRequest("peer-x") }

        vm.undoReject()
        advanceUntilIdle()
        coVerify(exactly = 1) { reqRepo.addMessageRequest(request) }

        vm.blockRequest(request)
        advanceUntilIdle()
        verify { blockedStore.block("peer-x") }
        // After blocking, undoReject must do nothing
        vm.undoReject()
        advanceUntilIdle()
        coVerify(exactly = 1) { reqRepo.addMessageRequest(request) }
    }

    @Test
    fun `ChatListViewModel markRead triggers read receipts for delivered incoming messages and toggleMute flips state`() = runTest {
        val contactRepo = mockk<ContactRepository> { every { getChatListContacts() } returns flowOf(emptyList()) }
        val presenceTracker = mockk<PresenceTracker> { every { contactPresence } returns flowOf(emptyList()) }
        val reqRepo = mockk<MessageRequestRepository> { every { getAllMessageRequests() } returns flowOf(emptyList()) }
        val msgRepo = mockk<MessageRepository>(relaxed = true) {
            coEvery { getUnreadDeliveredIncomingIds("peer-1") } returns listOf("m-1", "m-2")
        }
        val mutedSet = mutableSetOf<String>()
        val mutedStore = mockk<MutedChatsStore> {
            every { mutedIds() } answers { mutedSet.toSet() }
            every { setMuted(any(), any()) } answers {
                val id = firstArg<String>()
                if (secondArg()) mutedSet.add(id) else mutedSet.remove(id)
            }
        }
        val markReadUseCase = mockk<MarkMessageReadUseCase>(relaxed = true)
        val discovery = mockk<NetworkDiscoveryManager>(relaxed = true) {
            every { networkState } returns MutableStateFlow(LanNetworkState())
        }
        val engine = mockk<MessagingEngine> {
            every { isRunning } returns MutableStateFlow(true)
        }

        val vm = ChatListViewModel(contactRepo, presenceTracker, reqRepo, msgRepo, mutedStore, markReadUseCase, discovery, engine)

        vm.markRead("peer-1")
        advanceUntilIdle()
        coVerify { markReadUseCase("m-1") }
        coVerify { markReadUseCase("m-2") }
        coVerify { msgRepo.markConversationRead("peer-1") }

        vm.toggleMute("peer-1")
        assertTrue("peer-1" in vm.mutedIds.value)
        vm.toggleMute("peer-1")
        assertFalse("peer-1" in vm.mutedIds.value)
    }
}
