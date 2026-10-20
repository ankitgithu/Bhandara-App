package com.aistudio.bhandara.gwlmbd.data.preferences

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bhandara_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CITY = "key_selected_city"
        private const val KEY_THEME = "key_theme_setting"
        private const val KEY_NOTIF = "key_notifications_enabled"
        private const val KEY_WELCOME = "key_welcome_completed"
        private const val KEY_LAST_UPDATE_CHECK = "key_last_update_check"
        private const val KEY_DISMISSED_UPDATE_VERSION = "key_dismissed_update_version"
    }

    var lastUpdateCheckTime: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    var lastDismissedUpdateVersionCode: Int
        get() = prefs.getInt(KEY_DISMISSED_UPDATE_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_DISMISSED_UPDATE_VERSION, value).apply()

    var selectedCity: String
        get() = prefs.getString(KEY_CITY, "gwalior") ?: "gwalior"
        set(value) = prefs.edit().putString(KEY_CITY, value).apply()

    var themeSetting: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIF, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF, value).apply()

    var isWelcomeCompleted: Boolean
        get() = prefs.getBoolean(KEY_WELCOME, false)
        set(value) = prefs.edit().putBoolean(KEY_WELCOME, value).apply()

    fun getCityHindiName(cityKey: String = selectedCity): String {
        return when (cityKey.lowercase()) {
            "gwalior" -> "ग्वालियर"
            "morena" -> "मुरैना"
            "bhind" -> "भिंड"
            else -> "ग्वालियर"
        }
    }
}
