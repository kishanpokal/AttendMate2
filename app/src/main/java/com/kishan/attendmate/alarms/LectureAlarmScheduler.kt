package com.kishan.attendmate.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules lecture attendance alarms.
 *
 * Rules:
 * - One alarm per lecture PER USER
 * - Trigger time is absolute (millis)
 * - Never schedules past alarms
 * - Safe across login / logout / multi-user
 */
object LectureAlarmScheduler {

    /**
     * Schedule lecture attendance notification.
     *
     * @param userId Firebase UID of the logged-in user
     * @param triggerAtMillis absolute time in millis
     */
    fun scheduleLecture(
        context: Context,
        userId: String,
        subjectId: String,
        subjectName: String,
        date: String,        // yyyy-MM-dd
        startTime: String,   // HH:mm
        endTime: String,     // HH:mm
        triggerAtMillis: Long
    ) {
        // ❌ Never schedule past alarms
        if (triggerAtMillis <= System.currentTimeMillis()) {
            return
        }

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, LectureAlarmReceiver::class.java).apply {
            putExtra(LectureAlarmReceiver.EXTRA_SUBJECT_ID, subjectId)
            putExtra(LectureAlarmReceiver.EXTRA_SUBJECT_NAME, subjectName)
            putExtra(LectureAlarmReceiver.EXTRA_DATE, date)
            putExtra(LectureAlarmReceiver.EXTRA_START_TIME, startTime)
            putExtra(LectureAlarmReceiver.EXTRA_END_TIME, endTime)
        }

        /**
         * 🔐 USER-SCOPED KEY
         * Prevents alarms from leaking between accounts
         */
        val lectureKey = "${userId}_${date}_${startTime}_$endTime"
        val requestCode = lectureKey.hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Idempotent: remove any existing alarm
        alarmManager.cancel(pendingIntent)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // OEM / policy fallback
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    /**
     * Cancel a scheduled lecture alarm (user-scoped).
     */
    fun cancelLecture(
        context: Context,
        userId: String,
        date: String,
        startTime: String,
        endTime: String
    ) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val lectureKey = "${userId}_${date}_${startTime}_$endTime"

        val intent = Intent(context, LectureAlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lectureKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }
}
