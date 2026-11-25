package com.prakhar.lanman.domain.usecase

import android.content.Context
import com.prakhar.lanman.data.network.FileTransferReceiver
import com.prakhar.lanman.data.security.SecureKeyStore
import com.prakhar.lanman.domain.network.ConnectionManager
import com.prakhar.lanman.domain.network.FileTransferProgressTracker
import com.prakhar.lanman.domain.repository.ContactRepository
import com.prakhar.lanman.domain.repository.FileTransferRepository
import com.prakhar.lanman.domain.security.EncryptionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class RemoveContactUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactRepository: ContactRepository,
    private val fileTransferRepository: FileTransferRepository,
    private val connectionManager: ConnectionManager,
    private val encryptionEngine: EncryptionEngine,
    private val fileTransferReceiver: FileTransferReceiver,
    private val secureKeyStore: SecureKeyStore,
    private val progressTracker: FileTransferProgressTracker
) {
    suspend operator fun invoke(contactId: String) = withContext(Dispatchers.IO) {
        // 1. Explicitly close any active socket connection
        connectionManager.disconnectFrom(contactId)

        // 2. Cancel any in-progress incoming file transfers
        fileTransferReceiver.cancelTransfersForContact(contactId)

        // 3. Delete app-private files, thumbnails, and encryption keys owned by this contact
        val transfers = fileTransferRepository.getForContactSync(contactId)
        val appFilesRoot = context.filesDir.canonicalFile
        transfers.forEach { transfer ->
            progressTracker.clearProgress(transfer.id)
            secureKeyStore.removeTransferKey(transfer.id)
            runCatching { File(File(context.filesDir, "transfer_keys"), transfer.id).delete() }
            deleteIfAppPrivateFile(transfer.localUri, appFilesRoot)
            transfer.thumbnailUri?.let { deleteIfAppPrivateFile(it, appFilesRoot) }
        }

        // 4. Clear Double Ratchet session state
        encryptionEngine.deleteSession(contactId)

        // 5. Delete contact from local database (cascades to messages & file transfers)
        contactRepository.deleteContact(contactId)
    }

    private fun deleteIfAppPrivateFile(path: String, appFilesRoot: File) {
        if (path.isBlank() || path.startsWith("content://")) return
        runCatching {
            val file = File(path).canonicalFile
            if (file.path.startsWith(appFilesRoot.path) && file.exists()) {
                file.delete()
            }
        }
    }
}
