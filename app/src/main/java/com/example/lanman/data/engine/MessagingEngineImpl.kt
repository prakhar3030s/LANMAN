package com.example.lanman.data.engine

import android.content.Context
import android.util.Log
import com.example.lanman.data.network.FileTransferReceiver
import com.example.lanman.data.network.IncomingMessageReceiver
import com.example.lanman.data.network.MessageDispatcher
import com.example.lanman.domain.engine.MessagingEngine
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.NetworkDiscoveryManager
import com.example.lanman.domain.network.PresenceTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager,
    private val presenceTracker: PresenceTracker,
    private val networkDiscoveryManager: NetworkDiscoveryManager,
    private val messageDispatcher: MessageDispatcher,
    private val incomingMessageReceiver: IncomingMessageReceiver,
    private val fileTransferReceiver: FileTransferReceiver
) : MessagingEngine {

    private val TAG = "MessagingEngine"
    private var scope: CoroutineScope? = null

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    @Synchronized
    override fun start() {
        if (scope == null) {
            Log.d(TAG, "Starting MessagingEngine pipelines")
            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope = newScope

            messageDispatcher.start(newScope)
            incomingMessageReceiver.start(newScope)
            fileTransferReceiver.start(newScope)

            newScope.launch {
                networkDiscoveryManager.networkTransitions.collect {
                    connectionManager.closeAllConnections()
                }
            }
        }

        connectionManager.start()
        presenceTracker.start()
        _isRunning.value = true
    }

    @Synchronized
    override fun stop() {
        Log.d(TAG, "Stopping MessagingEngine")
        fileTransferReceiver.stop()
        incomingMessageReceiver.stop()
        messageDispatcher.stop()

        presenceTracker.stop()
        connectionManager.stop()

        scope?.cancel()
        scope = null
        _isRunning.value = false
    }
}
