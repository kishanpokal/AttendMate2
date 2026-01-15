package com.kishan.attendmate.util

import android.util.Log
import com.kishan.attendmate.BuildConfig

object DebugLog {

    private const val TAG = "AttendMate"

    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, message, throwable)
        }
    }
}
