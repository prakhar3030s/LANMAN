package com.example.lanman.domain.network.protocol

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class FrameCodecTest {

    @Test
    fun `test encode and decode PingFrame`() {
        val frame = Frame.PingFrame
        val bytes = FrameCodec.encode(frame)
        
        val decoded = FrameCodec.decode(ByteArrayInputStream(bytes))
        assertTrue(decoded is Frame.PingFrame)
    }

    @Test
    fun `test encode and decode HelloFrame`() {
        val contactId = java.util.UUID.randomUUID().toString()
        val frame = Frame.HelloFrame(contactId, displayName = "Alice", identityKey = "key123")
        val bytes = FrameCodec.encode(frame)
        
        val decoded = FrameCodec.decode(ByteArrayInputStream(bytes)) as Frame.HelloFrame
        assertEquals(contactId, decoded.contactId)
    }

    @Test
    fun `test decode malformed length exceeds max`() {
        // Construct a frame with an excessively large length (e.g. 11MB)
        val byteStream = ByteArrayOutputStream()
        val out = java.io.DataOutputStream(byteStream)
        out.writeInt(11 * 1024 * 1024)
        out.writeByte(FrameType.PING.typeByte.toInt())
        val bytes = byteStream.toByteArray()
        
        val decoded = FrameCodec.decode(ByteArrayInputStream(bytes))
        assertNull(decoded)
    }

    @Test
    fun `test decode unknown frame type falls back to UnknownFrame`() {
        val byteStream = ByteArrayOutputStream()
        val out = java.io.DataOutputStream(byteStream)
        out.writeInt(5) // length 5
        out.writeByte(99) // unknown type byte 99
        out.write(ByteArray(4)) // dummy payload
        val bytes = byteStream.toByteArray()
        
        val decoded = FrameCodec.decode(ByteArrayInputStream(bytes))
        assertTrue(decoded is Frame.UnknownFrame)
        assertEquals(99.toByte(), (decoded as Frame.UnknownFrame).unknownTypeByte)
    }

    @Test
    fun `test encode and decode MessageFrame`() {
        val frame = Frame.MessageFrame(
            messageId = "msg1",
            senderId = "sender1",
            messageType = 0.toByte(),
            timestamp = 1234567890L,
            content = "Hello World".toByteArray(StandardCharsets.UTF_8)
        )
        val bytes = FrameCodec.encode(frame)
        
        val decoded = FrameCodec.decode(ByteArrayInputStream(bytes)) as Frame.MessageFrame
        assertEquals(frame, decoded)
    }
}
