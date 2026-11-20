package com.example.lanman.data.security

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the ids of contacts the user blocked from the message-request screen. Kept in a
 * small file so blocking does not need a database migration.
 */
@Singleton
class BlockedContactsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val file = File(context.filesDir, "blocked_contacts.txt")

    @Synchronized
    fun isBlocked(contactId: String): Boolean {
        return read().contains(contactId)
    }

    @Synchronized
    fun block(contactId: String) {
        val current = read()
        if (contactId in current) return
        try {
            file.appendText(contactId + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist blocked contact $contactId", e)
        }
    }

    @Synchronized
    fun unblock(contactId: String) {
        val updated = read() - contactId
        try {
            file.writeText(updated.joinToString("\n"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unblock contact $contactId", e)
        }
    }

    @Synchronized
    fun blockedIds(): Set<String> = read()

    private fun read(): Set<String> {
        return try {
            if (!file.exists()) emptySet()
            else file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read blocked contacts", e)
            emptySet()
        }
    }

    private companion object {
        const val TAG = "BlockedContactsStore"
    }
}
