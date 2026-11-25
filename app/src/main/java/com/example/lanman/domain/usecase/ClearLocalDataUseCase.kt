package com.example.lanman.domain.usecase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.lanman.data.engine.RadarService
import com.example.lanman.data.local.LanmanDatabase
import com.example.lanman.data.security.SecureKeyStore
import com.example.lanman.data.security.SignalStoreImpl
import com.example.lanman.domain.engine.MessagingEngine
import com.example.lanman.domain.network.FileTransferProgressTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClearLocalDataUseCase @Inject constructor(
    private val database: LanmanDatabase,
    private val secureKeyStore: SecureKeyStore,
    private val signalStore: SignalStoreImpl,
    private val messagingEngine: MessagingEngine,
    private val progressTracker: FileTransferProgressTracker,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke() {
        withContext(Dispatchers.IO) {
            // 1. Stop discovery, sockets and pipelines before deleting the state they hold.
            //    Otherwise they keep sessions/sockets for contacts that no longer exist and
            //    can write stale rows back into the freshly cleared database.
            runCatching { context.stopService(Intent(context, RadarService::class.java)) }
                .onFailure { Log.w(TAG, "Failed to stop RadarService", it) }
            runCatching { messagingEngine.stop() }
                .onFailure { Log.w(TAG, "Failed to stop MessagingEngine", it) }
            runCatching { signalStore.clearInMemorySessions() }
                .onFailure { Log.w(TAG, "Failed to clear cached Signal sessions", it) }
            runCatching { progressTracker.clearAll() }
                .onFailure { Log.w(TAG, "Failed to clear transfer progress", it) }

            // 2. Wipe Room Database
            database.clearAllTables()

            // 3. Wipe EncryptedSharedPreferences (keys and profile config)
            secureKeyStore.clearAll()

            // 4. Delete every file the old identity produced. If one of these fails the data is
            //    already unusable (the keys are gone), so log instead of aborting the reset.
            val privateDirs = listOf(
                "received_files",
                "outgoing_files",
                "encrypted_uploads",
                "transfer_keys",
                "thumbnails",
                "voice_recordings"
            )
            privateDirs.forEach { name ->
                runCatching { File(context.filesDir, name).deleteRecursively() }
                    .onFailure { Log.w(TAG, "Failed to delete $name", it) }
            }
            runCatching { File(context.cacheDir, "voice_recordings").deleteRecursively() }
                .onFailure { Log.w(TAG, "Failed to delete cached voice recordings", it) }
            runCatching { File(context.cacheDir, "image_cache").deleteRecursively() }
                .onFailure { Log.w(TAG, "Failed to delete image cache", it) }
        }
    }

    companion object {
        private const val TAG = "ClearLocalData"
    }
}
