package com.kishan.attendmate.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "AttendMatePrefs"
        const val KEY_THEME = "key_theme"
        const val KEY_AI_REQUEST_COUNT = "key_ai_request_count"
        const val KEY_AI_LAST_REQUEST_DATE = "key_ai_last_request_date"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val MAX_AI_REQUESTS_PER_DAY = 15
    }

    // --- Theme Settings ---

    fun getThemePreference(): String {
        return prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    fun setThemePreference(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    // --- AI Rate Limiting ---

    fun getAiRequestCount(currentDateString: String): Int {
        val lastDate = prefs.getString(KEY_AI_LAST_REQUEST_DATE, "")
        if (lastDate != currentDateString) {
            // It's a new day, reset the counter
            resetAiRequestCount(currentDateString)
            return 0
        }
        return prefs.getInt(KEY_AI_REQUEST_COUNT, 0)
    }

    fun incrementAiRequestCount(currentDateString: String) {
        val count = getAiRequestCount(currentDateString)
        prefs.edit()
                .putInt(KEY_AI_REQUEST_COUNT, count + 1)
                .putString(KEY_AI_LAST_REQUEST_DATE, currentDateString)
                .apply()
    }

    private fun resetAiRequestCount(currentDateString: String) {
        prefs.edit()
                .putInt(KEY_AI_REQUEST_COUNT, 0)
                .putString(KEY_AI_LAST_REQUEST_DATE, currentDateString)
                .apply()
    }
}
