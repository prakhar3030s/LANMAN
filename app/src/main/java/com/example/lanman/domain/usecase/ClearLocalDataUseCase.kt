package com.example.lanman.domain.usecase

import com.example.lanman.data.local.LocalLinkDatabase
import com.example.lanman.data.security.SecureKeyStore
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

class ClearLocalDataUseCase @Inject constructor(
    private val database: LocalLinkDatabase,
    private val secureKeyStore: SecureKeyStore,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke() {
        withContext(Dispatchers.IO) {
            // 1. Wipe Room Database
            database.clearAllTables()
            
            // 2. Wipe EncryptedSharedPreferences (keys and profile config)
            secureKeyStore.clearAll()
            
            // 3. Clear file transfers directory
            val receivedDir = java.io.File(context.filesDir, "received_files")
            if (receivedDir.exists()) receivedDir.deleteRecursively()
            val uploadsDir = java.io.File(context.filesDir, "encrypted_uploads")
            if (uploadsDir.exists()) uploadsDir.deleteRecursively()
        }
    }
}
