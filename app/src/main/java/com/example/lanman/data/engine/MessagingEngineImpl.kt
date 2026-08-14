package com.example.lanman.data.engine

import android.content.Context
import android.util.Log
import com.example.lanman.data.network.FileTransferReceiver
import com.example.lanman.data.network.IncomingMessageReceiver
import com.example.lanman.data.network.MessageDispatcher
import com.example.lanman.domain.engine.MessagingEngine
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.PresenceTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager,
    private val presenceTracker: PresenceTracker,
    private val messageDispatcher: MessageDispatcher,
    private val incomingMessageReceiver: IncomingMessageReceiver,
    private val fileTransferReceiver: FileTransferReceiver
) : MessagingEngine {

    private val TAG = "MessagingEngine"
    private var scope: CoroutineScope? = null
    private val lastConnectionAttempts = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Synchronized
    override fun start() {
        if (scope == null) {
            Log.d(TAG, "Starting MessagingEngine pipelines")
            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope = newScope
            
            // Start message pipeline components
            messageDispatcher.start(newScope)
            incomingMessageReceiver.start(newScope)
            fileTransferReceiver.start(newScope)
            // Proactive fetching of keys over TCP was removed; full bundle is back in the QR payload.
        }
        
        // Ensure networking components are started (they are safe to call multiple times)
        // If Wi-Fi is already connected, the callback might not trigger immediately on registration, 
        // or it might. Calling start() here ensures we try to connect. The callback handles actual availability.
        connectionManager.start()
        presenceTracker.start()
    }

    @Synchronized
    override fun stop() {
        Log.d(TAG, "Stopping MessagingEngine")
        // Stop high-level pipelines first
        fileTransferReceiver.stop()
        incomingMessageReceiver.stop()
        messageDispatcher.stop()
        
        // Stop networking components
        presenceTracker.stop()
        connectionManager.stop()
        
        scope?.cancel()
        scope = null
    }
}
