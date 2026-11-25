package com.prakhar.lanman.domain.model

data class ContactPresence(
    val contact: Contact,
    val isOnline: Boolean,
    val lastKnownIp: String?
)
