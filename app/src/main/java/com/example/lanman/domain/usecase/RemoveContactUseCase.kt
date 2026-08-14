package com.example.lanman.domain.usecase

import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.domain.security.EncryptionEngine
import com.example.lanman.data.network.FileTransferReceiver
import javax.inject.Inject

class RemoveContactUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val connectionManager: ConnectionManager,
    private val encryptionEngine: EncryptionEngine,
    private val fileTransferReceiver: FileTransferReceiver
) {
    suspend operator fun invoke(contactId: String) {
        // 1. Explicitly close any active socket connection
        connectionManager.disconnectFrom(contactId)
        
        // 1.5. Cancel any in-progress file transfers
        fileTransferReceiver.cancelTransfersForContact(contactId)
        
        // 2. Clear Double Ratchet session state
        encryptionEngine.deleteSession(contactId)
        
        // 3. Delete contact from local database (which cascades to messages & file transfers)
        contactRepository.deleteContact(contactId)
    }
}
