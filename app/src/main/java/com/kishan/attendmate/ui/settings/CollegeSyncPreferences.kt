package com.kishan.attendmate.ui.settings

import android.content.Context
import android.content.SharedPreferences

class CollegeSyncPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("college_sync_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SELECTED_SEMESTER = "selected_semester"
        private const val KEY_TARGET_SUBJECTS = "target_subjects"
        private const val KEY_IS_CONFIGURED = "is_configured"
    }

    var isConfigured: Boolean
        get() = prefs.getBoolean(KEY_IS_CONFIGURED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_CONFIGURED, value).apply()

    var selectedSemester: String?
        get() = prefs.getString(KEY_SELECTED_SEMESTER, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_SEMESTER, value).apply()

    var targetSubjects: Set<String>?
        get() = prefs.getStringSet(KEY_TARGET_SUBJECTS, null)
        set(value) = prefs.edit().putStringSet(KEY_TARGET_SUBJECTS, value).apply()

    fun clearConfig() {
        prefs.edit().clear().apply()
    }
}
