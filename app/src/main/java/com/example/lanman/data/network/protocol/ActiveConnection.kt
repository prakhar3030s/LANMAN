package com.example.lanman.data.network.protocol

import kotlinx.coroutines.sync.Mutex
import java.net.Socket

class ActiveConnection(
    val socket: Socket,
    val isUnknown: Boolean = false,
    val unknownDisplayName: String? = null,
    val unknownIdentityKey: String? = null
) {
    val writeMutex = Mutex()
    val outputStream = socket.getOutputStream().buffered(512 * 1024)
}
