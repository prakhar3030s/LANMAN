package com.prakhar.lanman.domain.network

import com.prakhar.lanman.util.network.NetworkConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HeartbeatCodecTest {

    @Test
    fun `encode and decode round-trip preserves sender id and port`() {
        val ownId = "550e8400-e29b-41d4-a716-446655440000"
        val packet = HeartbeatCodec.encode(ownId)
        val decoded = HeartbeatCodec.decode(packet, packet.size)

        assertNotNull(decoded)
        assertEquals(ownId, decoded?.id)
        assertEquals(NetworkConstants.TCP_MESSAGE_PORT, decoded?.tcpPort)
    }

    @Test
    fun `decode returns null on short buffer wrong magic or wrong version`() {
        val valid = HeartbeatCodec.encode("peer-1")

        assertNull(HeartbeatCodec.decode(valid, 4))

        val badMagic = valid.copyOf().also { it[0] = 'X'.code.toByte() }
        assertNull(HeartbeatCodec.decode(badMagic, badMagic.size))

        val badVersion = valid.copyOf().also { it[4] = (NetworkConstants.PROTOCOL_VERSION + 5).toByte() }
        assertNull(HeartbeatCodec.decode(badVersion, badVersion.size))

        // Valid header + length claim, but declared length truncated before full payload + port
        assertNull(HeartbeatCodec.decode(valid, valid.size - 2))
    }
}
