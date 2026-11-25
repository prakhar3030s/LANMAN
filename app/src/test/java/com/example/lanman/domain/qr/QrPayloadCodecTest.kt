package com.example.lanman.domain.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrPayloadCodecTest {

    @Test
    fun `encode and decode v3 payload preserves all fields`() {
        val payload = ContactQrPayload(
            version = 3,
            id = "peer-123",
            displayName = "Alice",
            identityKey = "id-key-b64",
            fullBundleBase64 = "bundle-b64"
        )
        val encoded = QrPayloadCodec.encode(payload)
        assertEquals(payload, QrPayloadCodec.decode(encoded))
    }

    @Test
    fun `decode ignores unknown forward-compatible fields and rejects invalid json`() {
        val json = """{"version":4,"id":"peer-1","displayName":"Bob","futureField":true}"""
        val decoded = QrPayloadCodec.decode(json)
        assertEquals("peer-1", decoded?.id)
        assertEquals("Bob", decoded?.displayName)

        assertNull(QrPayloadCodec.decode(""))
        assertNull(QrPayloadCodec.decode("not-json"))
        assertNull(QrPayloadCodec.decode("""{"version":3}"""))
    }
}
