package com.example.lanman.domain.qr

import kotlinx.serialization.Serializable

@Serializable
data class ContactQrPayload(
    val version: Int = 3,
    val id: String,
    val displayName: String,
    val publicKey: String? = null,
    val identityKey: String? = null,
    val fullBundleBase64: String? = null
)
