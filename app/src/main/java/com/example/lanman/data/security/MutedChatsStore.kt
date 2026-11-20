package com.example.lanman.data.security

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers which conversations the user muted, so notifications can skip them. */
@Singleton
class MutedChatsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val file = File(context.filesDir, "muted_chats.txt")

    @Synchronized
    fun isMuted(contactId: String): Boolean = read().contains(contactId)

    @Synchronized
    fun setMuted(contactId: String, muted: Boolean) {
        val current = read()
        val updated = if (muted) current + contactId else current - contactId
        if (updated == current) return
        try {
            file.writeText(updated.joinToString("\n"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist mute state for $contactId", e)
        }
    }

    @Synchronized
    fun mutedIds(): Set<String> = read()

    private fun read(): Set<String> = try {
        if (!file.exists()) emptySet()
        else file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read muted chats", e)
        emptySet()
    }

    private companion object {
        const val TAG = "MutedChatsStore"
    }
}
