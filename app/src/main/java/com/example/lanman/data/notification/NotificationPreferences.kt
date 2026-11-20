package com.example.lanman.data.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-type notification switches. The system permission still gates everything; these let the
 * user silence one kind of notification (requests, say) without turning notifications off.
 */
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var messagesEnabled: Boolean
        get() = prefs.getBoolean(KEY_MESSAGES, true)
        set(value) = prefs.edit().putBoolean(KEY_MESSAGES, value).apply()

    var requestsEnabled: Boolean
        get() = prefs.getBoolean(KEY_REQUESTS, true)
        set(value) = prefs.edit().putBoolean(KEY_REQUESTS, value).apply()

    private companion object {
        const val KEY_ENABLED = "notifications_enabled"
        const val KEY_MESSAGES = "messages_enabled"
        const val KEY_REQUESTS = "requests_enabled"
    }
}
