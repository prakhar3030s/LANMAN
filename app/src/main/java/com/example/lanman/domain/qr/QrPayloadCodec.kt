package com.example.lanman.domain.qr

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object QrPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(payload: ContactQrPayload): String {
        return json.encodeToString(payload)
    }

    fun decode(encoded: String): ContactQrPayload? {
        return try {
            json.decodeFromString<ContactQrPayload>(encoded)
        } catch (e: Exception) {
            null
        }
    }
}
