package com.example.lanman.domain.network

import com.example.lanman.domain.network.protocol.Frame
import kotlinx.coroutines.flow.Flow

data class IncomingFrameEvent(
    val contactId: String,
    val frame: Frame,
    val isUnknown: Boolean = false,
    val unknownDisplayName: String? = null,
    val unknownIdentityKey: String? = null,
    val isDataChannel: Boolean = false
)

interface ConnectionManager {
    val incomingFrames: Flow<IncomingFrameEvent>
    
    fun start()
    fun stop()
    
    fun hasActiveConnection(contactId: String): Boolean
    suspend fun connectTo(contactId: String): Result<Unit>
    suspend fun connectDataChannelTo(contactId: String): Result<Unit>
    suspend fun sendFrame(contactId: String, frame: Frame, flush: Boolean = true): Result<Unit>
    suspend fun sendDataFrame(contactId: String, frame: Frame, flush: Boolean = true): Result<Unit>
    fun disconnectFrom(contactId: String)
}
