package com.kishan.attendmate.ui.timetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.notifications.LectureNotificationHelper
import java.util.Date

class LectureActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        try {
            /* ---------- Validate user ---------- */
            val user = FirebaseAuth.getInstance().currentUser
                ?: return pendingResult.finish()

            /* ---------- Extract action ---------- */
            val action = intent.action ?: return pendingResult.finish()

            /* ---------- CANCELLED = NO-OP ---------- */
            if (action == LectureNotificationHelper.ACTION_CANCELLED) {
                cancelNotification(context, intent)
                return pendingResult.finish()
            }

            /* ---------- Extract required extras ---------- */
            val subjectId =
                intent.getStringExtra(LectureNotificationHelper.EXTRA_SUBJECT_ID)
                    ?: return pendingResult.finish()

            val date =
                intent.getStringExtra(LectureNotificationHelper.EXTRA_DATE)
                    ?: return pendingResult.finish()

            val startTime =
                intent.getStringExtra(LectureNotificationHelper.EXTRA_START_TIME)
                    ?: return pendingResult.finish()

            val endTime =
                intent.getStringExtra(LectureNotificationHelper.EXTRA_END_TIME)
                    ?: return pendingResult.finish()

            val status = when (action) {
                LectureNotificationHelper.ACTION_PRESENT -> "PRESENT"
                LectureNotificationHelper.ACTION_ABSENT -> "ABSENT"
                else -> return pendingResult.finish()
            }

            val lectureKey = "${date}_${startTime}_$endTime"

            /* ---------- Firestore refs ---------- */
            val db = FirebaseFirestore.getInstance()

            val attendanceRef = db
                .collection("users")
                .document(user.uid)
                .collection("subjects")
                .document(subjectId)
                .collection("attendance")
                .document(lectureKey)

            val subjectRef = db
                .collection("users")
                .document(user.uid)
                .collection("subjects")
                .document(subjectId)

            /* ---------- Transaction ---------- */
            db.runTransaction { transaction ->

                if (transaction.get(attendanceRef).exists()) {
                    return@runTransaction
                }

                transaction.set(
                    attendanceRef,
                    mapOf(
                        "date" to date,                 // yyyy-MM-dd
                        "startTime" to startTime,       // HH:mm
                        "endTime" to endTime,           // HH:mm
                        "status" to status,             // PRESENT / ABSENT
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                transaction.update(
                    subjectRef,
                    "totalClasses",
                    FieldValue.increment(1)
                )

                if (status == "PRESENT") {
                    transaction.update(
                        subjectRef,
                        "attendedClasses",
                        FieldValue.increment(1)
                    )
                }
            }.addOnCompleteListener {
                cancelNotification(context, intent)
                pendingResult.finish()
            }

        } catch (e: Exception) {
            pendingResult.finish()
        }
    }

    private fun cancelNotification(context: Context, intent: Intent) {
        val date = intent.getStringExtra(LectureNotificationHelper.EXTRA_DATE) ?: return
        val startTime = intent.getStringExtra(LectureNotificationHelper.EXTRA_START_TIME) ?: return
        val endTime = intent.getStringExtra(LectureNotificationHelper.EXTRA_END_TIME) ?: return

        val lectureKey = "${date}_${startTime}_$endTime"

        NotificationManagerCompat
            .from(context)
            .cancel(lectureKey.hashCode())
    }
}
