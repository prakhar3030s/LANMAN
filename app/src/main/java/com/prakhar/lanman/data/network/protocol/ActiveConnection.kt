package com.prakhar.lanman.data.network.protocol

import com.prakhar.lanman.util.network.NetworkConstants
import kotlinx.coroutines.sync.Mutex
import java.io.BufferedOutputStream
import java.net.Socket

class ActiveConnection(
    val socket: Socket,
    val isUnknown: Boolean = false,
    val unknownDisplayName: String? = null,
    val unknownIdentityKey: String? = null
) {
    val writeMutex = Mutex()
    val outputStream = BufferedOutputStream(socket.getOutputStream(), NetworkConstants.CONTROL_SOCKET_BUFFER_SIZE)

    @Volatile
    var lastActivityAt: Long = System.currentTimeMillis()

    // Optional Dedicated High-Speed Data Channel Socket (Port 47476)
    @Volatile
    var dataSocket: Socket? = null

    @Volatile
    var dataOutputStream: BufferedOutputStream? = null
    val dataWriteMutex = Mutex()

    fun setDataConnection(socket: Socket) {
        try { dataSocket?.close() } catch (_: Exception) {}
        dataSocket = socket
        dataOutputStream = BufferedOutputStream(socket.getOutputStream(), NetworkConstants.DATA_SOCKET_BUFFER_SIZE)
    }

    fun markActive() {
        lastActivityAt = System.currentTimeMillis()
    }

    fun close() {
        try { socket.close() } catch (_: Exception) {}
        try { dataSocket?.close() } catch (_: Exception) {}
    }
}
