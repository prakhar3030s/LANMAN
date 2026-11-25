package com.example.lanman.data.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM;

    companion object {
        fun fromStored(value: String?): AppThemeMode =
            entries.find { it.name == value } ?: DARK
    }
}

/**
 * Local user preferences for notifications, privacy toggles, discovery, and appearance.
 */
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(AppThemeMode.fromStored(prefs.getString(KEY_THEME_MODE, AppThemeMode.DARK.name)))
    val themeModeFlow: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var messagesEnabled: Boolean
        get() = prefs.getBoolean(KEY_MESSAGES, true)
        set(value) = prefs.edit().putBoolean(KEY_MESSAGES, value).apply()

    var requestsEnabled: Boolean
        get() = prefs.getBoolean(KEY_REQUESTS, true)
        set(value) = prefs.edit().putBoolean(KEY_REQUESTS, value).apply()

    var readReceiptsEnabled: Boolean
        get() = prefs.getBoolean(KEY_READ_RECEIPTS, true)
        set(value) = prefs.edit().putBoolean(KEY_READ_RECEIPTS, value).apply()

    var discoveryEnabled: Boolean
        get() = prefs.getBoolean(KEY_DISCOVERY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DISCOVERY_ENABLED, value).apply()

    var themeMode: AppThemeMode
        get() = _themeMode.value
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
            _themeMode.value = value
        }

    private companion object {
        const val KEY_ENABLED = "notifications_enabled"
        const val KEY_MESSAGES = "messages_enabled"
        const val KEY_REQUESTS = "requests_enabled"
        const val KEY_READ_RECEIPTS = "read_receipts_enabled"
        const val KEY_DISCOVERY_ENABLED = "discovery_enabled"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
