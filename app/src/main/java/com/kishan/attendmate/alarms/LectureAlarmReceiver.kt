package com.kishan.attendmate.alarms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.notifications.LectureNotificationHelper
import com.kishan.attendmate.util.DebugLog

/**
 * Receives lecture alarm and shows attendance notification.
 *
 * Responsibilities:
 * - Permission check
 * - Intent validation
 * - Check if attendance already exists
 * - Show notification only if NOT already marked
 */
class LectureAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        try {
            // 🔒 Android 13+ notification permission check
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                DebugLog.d("LectureAlarmReceiver: POST_NOTIFICATIONS not granted")
                pendingResult.finish()
                return
            }

            val subjectId =
                intent.getStringExtra(EXTRA_SUBJECT_ID) ?: run {
                    pendingResult.finish(); return
                }

            val subjectName =
                intent.getStringExtra(EXTRA_SUBJECT_NAME) ?: run {
                    pendingResult.finish(); return
                }

            val date =
                intent.getStringExtra(EXTRA_DATE) ?: run {
                    pendingResult.finish(); return
                }

            val startTime =
                intent.getStringExtra(EXTRA_START_TIME) ?: run {
                    pendingResult.finish(); return
                }

            val endTime =
                intent.getStringExtra(EXTRA_END_TIME) ?: run {
                    pendingResult.finish(); return
                }

            val user = FirebaseAuth.getInstance().currentUser ?: run {
                pendingResult.finish(); return
            }

            val db = FirebaseFirestore.getInstance()

            // 🔍 CHECK IF ATTENDANCE ALREADY EXISTS
            db.collection("users")
                .document(user.uid)
                .collection("subjects")
                .document(subjectId)
                .collection("attendance")
                .whereEqualTo("date", date)
                .whereEqualTo("startTime", startTime)
                .whereEqualTo("endTime", endTime)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->

                    // ❌ Attendance already added → skip notification
                    if (!snapshot.isEmpty) {
                        DebugLog.d("LectureAlarmReceiver: Attendance already exists, skipping notification")
                        pendingResult.finish()
                        return@addOnSuccessListener
                    }

                    // ✅ Attendance not added → show notification
                    LectureNotificationHelper.showLectureAttendanceNotification(
                        context = context,
                        subjectName = subjectName,
                        subjectId = subjectId,
                        date = date,
                        startTime = startTime,
                        endTime = endTime
                    )

                    pendingResult.finish()
                }
                .addOnFailureListener { e ->
                    // Fail-safe: still show notification if check fails
                    DebugLog.e("LectureAlarmReceiver: Attendance check failed", e)

                    LectureNotificationHelper.showLectureAttendanceNotification(
                        context = context,
                        subjectName = subjectName,
                        subjectId = subjectId,
                        date = date,
                        startTime = startTime,
                        endTime = endTime
                    )

                    pendingResult.finish()
                }

        } catch (e: Exception) {
            DebugLog.e("LectureAlarmReceiver failed", e)
            pendingResult.finish()
        }
    }

    companion object {
        const val EXTRA_SUBJECT_ID = "extra_subject_id"
        const val EXTRA_SUBJECT_NAME = "extra_subject_name"
        const val EXTRA_DATE = "extra_date"             // yyyy-MM-dd
        const val EXTRA_START_TIME = "extra_start_time" // HH:mm
        const val EXTRA_END_TIME = "extra_end_time"     // HH:mm
    }
}
