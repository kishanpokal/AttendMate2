package com.kishan.attendmate.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kishan.attendmate.R
import com.kishan.attendmate.ui.timetable.LectureActionReceiver

/**
 * Builds lecture attendance notifications ONLY.
 * No Firestore, no business logic.
 */
object LectureNotificationHelper {

    /* ---------------- Channel ---------------- */

    const val CHANNEL_ID = "lecture_reminder_channel"

    /* ---------------- Actions ---------------- */

    const val ACTION_PRESENT = "lecture_action_present"
    const val ACTION_ABSENT = "lecture_action_absent"
    const val ACTION_CANCELLED = "lecture_action_cancelled"

    /* ---------------- Extras ---------------- */

    const val EXTRA_SUBJECT_ID = "extra_subject_id"
    const val EXTRA_DATE = "extra_date"             // yyyy-MM-dd
    const val EXTRA_START_TIME = "extra_start_time" // HH:mm
    const val EXTRA_END_TIME = "extra_end_time"     // HH:mm

    /* ---------------- Channel Setup ---------------- */

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lecture Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lecture attendance reminders"
            }

            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /* ------------------------------------------------ */
    /* LECTURE ATTENDANCE NOTIFICATION                  */
    /* ------------------------------------------------ */

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showLectureAttendanceNotification(
        context: Context,
        subjectName: String,
        subjectId: String,
        date: String,        // yyyy-MM-dd
        startTime: String,   // HH:mm
        endTime: String      // HH:mm
    ) {
        ensureChannel(context)

        val lectureKey = "${date}_${startTime}_$endTime"

        fun actionIntent(action: String): PendingIntent {
            val intent = Intent(context, LectureActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_SUBJECT_ID, subjectId)
                putExtra(EXTRA_DATE, date)
                putExtra(EXTRA_START_TIME, startTime)
                putExtra(EXTRA_END_TIME, endTime)
            }

            return PendingIntent.getBroadcast(
                context,
                (lectureKey + action).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // use your app icon
            .setContentTitle(subjectName)
            .setContentText("Lecture $startTime – $endTime")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Present", actionIntent(ACTION_PRESENT))
            .addAction(0, "Absent", actionIntent(ACTION_ABSENT))
            .addAction(0, "Cancelled", actionIntent(ACTION_CANCELLED))
            .build()

        NotificationManagerCompat
            .from(context)
            .notify(lectureKey.hashCode(), notification)
    }
}
