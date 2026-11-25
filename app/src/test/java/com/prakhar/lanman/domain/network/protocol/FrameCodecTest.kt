package com.prakhar.lanman.domain.network.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class FrameCodecTest {

    private fun roundTrip(frame: Frame): Frame? {
        val out = ByteArrayOutputStream()
        FrameCodec.writeFrame(out, frame)
        return FrameCodec.decode(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun `encode and decode Ping and Pong frames`() {
        assertEquals(Frame.PingFrame, roundTrip(Frame.PingFrame))
        assertEquals(Frame.PongFrame, roundTrip(Frame.PongFrame))
    }

    @Test
    fun `encode and decode HelloFrame v2 with challenge`() {
        val challenge = ByteArray(32) { it.toByte() }
        val frame = Frame.HelloFrame(
            contactId = "user-1234",
            displayName = "Alice",
            identityKey = "base64-key",
            protocolVersion = Frame.HelloFrame.CURRENT_PROTOCOL_VERSION,
            clientChallenge = challenge
        )
        assertEquals(frame, roundTrip(frame))
    }

    @Test
    fun `encode and decode AuthChallenge AuthResponse and AuthResult frames`() {
        val challenge = Frame.AuthChallengeFrame(
            serverChallenge = ByteArray(32) { (it + 10).toByte() },
            serverSignature = ByteArray(64) { (it + 20).toByte() }
        )
        assertEquals(challenge, roundTrip(challenge))

        val response = Frame.AuthResponseFrame(
            clientSignature = ByteArray(64) { (it + 5).toByte() }
        )
        assertEquals(response, roundTrip(response))

        val accepted = Frame.AuthResultFrame(accepted = true, reason = "")
        val rejected = Frame.AuthResultFrame(accepted = false, reason = "Untrusted identity key")
        assertEquals(accepted, roundTrip(accepted))
        assertEquals(rejected, roundTrip(rejected))
    }

    @Test
    fun `encode and decode MessageFrame with utf8 content and timestamp`() {
        val frame = Frame.MessageFrame(
            messageId = "msg-42",
            senderId = "sender-1",
            messageType = 0,
            timestamp = 1700000123456L,
            content = "Encrypted \uD83D\uDD12 hello".toByteArray(Charsets.UTF_8)
        )
        assertEquals(frame, roundTrip(frame))
    }

    @Test
    fun `encode and decode AckFrame ReadFrame and KeyBundle frames`() {
        val ack = Frame.AckFrame("msg-1")
        val read = Frame.ReadFrame("msg-2")
        val req = Frame.KeyBundleRequestFrame("contact-9")
        val resp = Frame.KeyBundleResponseFrame("""{"identityKey":"abc"}""".toByteArray())

        assertEquals(ack, roundTrip(ack))
        assertEquals(read, roundTrip(read))
        assertEquals(req, roundTrip(req))
        assertEquals(resp, roundTrip(resp))
    }

    @Test
    fun `encode and decode FileOffer FileChunk FileHash and FileComplete frames`() {
        val offer = Frame.FileOfferFrame(
            messageId = "msg-f1",
            fileTransferId = "ft-1",
            fileName = "archive.tar.gz",
            fileSizeBytes = 1_500_000_000L,
            mimeType = "application/gzip",
            chunkSize = 512 * 1024,
            totalChunks = 2861,
            encryptedKeyEnvelope = byteArrayOf(1, 2, 3, 4, 5),
            senderTimestamp = 1700000999999L
        )
        assertEquals(offer, roundTrip(offer))

        val chunk = Frame.FileChunkFrame("ft-1", 17, byteArrayOf(9, 8, 7, 6))
        assertEquals(chunk, roundTrip(chunk))

        val hash = Frame.FileHashFrame("ft-1", ByteArray(32) { it.toByte() })
        assertEquals(hash, roundTrip(hash))

        val complete = Frame.FileCompleteFrame("ft-1")
        assertEquals(complete, roundTrip(complete))
    }

    @Test
    fun `decode empty stream returns null`() {
        assertNull(FrameCodec.decode(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun `decode returns null when frame length exceeds MAX_FRAME_SIZE or is non-positive`() {
        val overMax = ByteBuffer.allocate(5).apply {
            putInt(FrameCodec.MAX_FRAME_SIZE + 1)
            put(FrameType.FILE_CHUNK.typeByte)
        }.array()
        assertNull(FrameCodec.decode(ByteArrayInputStream(overMax)))

        val zeroLen = ByteBuffer.allocate(4).putInt(0).array()
        assertNull(FrameCodec.decode(ByteArrayInputStream(zeroLen)))
    }

    @Test
    fun `decode returns null before allocation when control frame exceeds 256KB cap`() {
        val header = ByteBuffer.allocate(5).apply {
            putInt(512 * 1024 + 1)
            put(FrameType.HELLO.typeByte)
        }.array()
        assertNull(FrameCodec.decode(ByteArrayInputStream(header)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode rejects oversized utf8 string field`() {
        val huge = "a".repeat(70_000)
        FrameCodec.encode(Frame.AckFrame(huge))
    }

    @Test
    fun `decode returns null on malformed negative length inside FileChunk`() {
        val idBytes = "ft-1".toByteArray()
        val payload = ByteBuffer.allocate(1 + idBytes.size + 4 + 4).apply {
            put(idBytes.size.toByte())
            put(idBytes)
            putInt(0)
            putInt(-50)
        }.array()
        val full = ByteBuffer.allocate(4 + 1 + payload.size).apply {
            putInt(1 + payload.size)
            put(FrameType.FILE_CHUNK.typeByte)
            put(payload)
        }.array()
        assertNull(FrameCodec.decode(ByteArrayInputStream(full)))
    }

    @Test
    fun `decode unknown frame type returns UnknownFrame`() {
        val encoded = ByteBuffer.allocate(7).apply {
            putInt(3)
            put(99.toByte())
            putShort(42)
        }.array()

        val decoded = FrameCodec.decode(ByteArrayInputStream(encoded))
        assertTrue(decoded is Frame.UnknownFrame)
        assertEquals(99.toByte(), (decoded as Frame.UnknownFrame).unknownTypeByte)
    }

    @Test
    fun `multiple back to back frames decode cleanly from single stream`() {
        val out = ByteArrayOutputStream()
        val f1 = Frame.PingFrame
        val f2 = Frame.AckFrame("m-1")
        val f3 = Frame.FileHashFrame("ft-9", ByteArray(32) { 0x5A })
        FrameCodec.writeFrame(out, f1)
        FrameCodec.writeFrame(out, f2)
        FrameCodec.writeFrame(out, f3)

        val input = ByteArrayInputStream(out.toByteArray())
        assertEquals(f1, FrameCodec.decode(input))
        assertEquals(f2, FrameCodec.decode(input))
        val d3 = FrameCodec.decode(input) as Frame.FileHashFrame
        assertEquals("ft-9", d3.fileTransferId)
        assertArrayEquals(f3.sha256, d3.sha256)
        assertNull(FrameCodec.decode(input))
    }
}
