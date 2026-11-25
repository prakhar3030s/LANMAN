package com.prakhar.lanman.domain.engine

import kotlinx.coroutines.flow.StateFlow

interface MessagingEngine {
    val isRunning: StateFlow<Boolean>
    fun start()
    fun stop()
}
