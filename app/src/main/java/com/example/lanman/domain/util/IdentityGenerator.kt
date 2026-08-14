package com.example.lanman.domain.util

import java.util.UUID

object IdentityGenerator {
    fun generateId(): String {
        return UUID.randomUUID().toString()
    }
}
