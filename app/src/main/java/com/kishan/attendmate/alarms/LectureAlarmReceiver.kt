package com.kishan.attendmate.alarms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.kishan.attendmate.notifications.LectureNotificationHelper

class LectureAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        // 🔒 Android 13+ permission safety
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        /* ---------- Extract required extras ---------- */

        val subjectId =
            intent.getStringExtra(EXTRA_SUBJECT_ID) ?: return

        val subjectName =
            intent.getStringExtra(EXTRA_SUBJECT_NAME) ?: return

        val date =
            intent.getStringExtra(EXTRA_DATE) ?: return

        val startTime =
            intent.getStringExtra(EXTRA_START_TIME) ?: return

        val endTime =
            intent.getStringExtra(EXTRA_END_TIME) ?: return

        /* ---------- Show notification ---------- */

        LectureNotificationHelper.showLectureAttendanceNotification(
            context = context,
            subjectName = subjectName,
            subjectId = subjectId,
            date = date,
            startTime = startTime,
            endTime = endTime
        )
    }

    companion object {
        const val EXTRA_SUBJECT_ID = "extra_subject_id"
        const val EXTRA_SUBJECT_NAME = "extra_subject_name"
        const val EXTRA_DATE = "extra_date"             // yyyy-MM-dd
        const val EXTRA_START_TIME = "extra_start_time" // HH:mm
        const val EXTRA_END_TIME = "extra_end_time"     // HH:mm
    }
}
