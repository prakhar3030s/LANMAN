package com.example.lanman.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.lanman.MainActivity
import com.example.lanman.R
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: NotificationPreferences,
    private val mutedChatsStore: com.example.lanman.data.security.MutedChatsStore
) {
    companion object {
        const val CHANNEL_ID = "messages_channel"
        const val CHANNEL_NAME = "Messages"
        private const val REQUEST_NOTIFICATION_BASE = 100_000
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Notifications for new incoming messages and message requests"
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun showNewMessageNotification(contactId: String, senderName: String, message: Message) {
        if (!canNotify()) return
        if (!preferences.enabled) return
        if (!preferences.messagesEnabled) return
        if (mutedChatsStore.isMuted(contactId)) return

        val contentText = when (message.type) {
            MessageType.FILE -> "Sent a file" // Per requirements, no raw content preview for files
            MessageType.VOICE -> "Sent a voice message"
            else -> String(message.content, java.nio.charset.StandardCharsets.UTF_8)
        }

        val pendingIntent = conversationPendingIntent(contactId, contactId.hashCode())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(contactId)
            .build()

        NotificationManagerCompat.from(context).notify(contactId.hashCode(), notification)
    }

    /** Tells the user that someone they have not paired with is trying to message them. */
    fun showMessageRequestNotification(contactId: String, senderName: String, preview: String) {
        if (!canNotify()) return
        if (!preferences.enabled) return
        if (!preferences.requestsEnabled) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$senderName wants to message you")
            .setContentText(preview.ifBlank { "Tap to review the request" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(requestsPendingIntent(contactId))
            .setAutoCancel(true)
            .setGroup("message_requests")
            .build()

        NotificationManagerCompat.from(context).notify(REQUEST_NOTIFICATION_BASE + contactId.hashCode(), notification)
    }

    private fun conversationPendingIntent(contactId: String, requestCode: Int): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("lanman://conversation/$contactId"),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestsPendingIntent(contactId: String): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("lanman://requests"),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_NOTIFICATION_BASE + contactId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

}
