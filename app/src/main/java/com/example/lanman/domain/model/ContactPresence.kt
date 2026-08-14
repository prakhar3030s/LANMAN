package com.example.lanman.domain.model

data class ContactPresence(
    val contact: Contact,
    val isOnline: Boolean,
    val lastKnownIp: String?
)
