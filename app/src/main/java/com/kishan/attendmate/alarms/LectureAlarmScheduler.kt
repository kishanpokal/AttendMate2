package com.kishan.attendmate.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kishan.attendmate.util.DebugLog

/**
 * Schedules lecture attendance alarms.
 *
 * Guarantees:
 * - One alarm per lecture PER USER
 * - No past alarms are ever scheduled
 * - Stable across login/logout
 * - Safe across Android 8–14
 */
object LectureAlarmScheduler {

    /**
     * Schedule lecture attendance notification.
     *
     * @param triggerAtMillis absolute UTC millis
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
        val now = System.currentTimeMillis()

        // ⛔ HARD GUARD — Android silently drops past alarms
        if (triggerAtMillis <= now) {
            DebugLog.d(
                "LectureAlarmScheduler: skipped past alarm " +
                        "(trigger=$triggerAtMillis, now=$now, date=$date, start=$startTime)"
            )
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
         * 🔐 USER + TIME SCOPED KEY
         */
        val lectureKey = "$userId|$date|$startTime|$endTime"
        val requestCode = lectureKey.hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ♻️ Idempotent — cancel existing alarm first
        alarmManager.cancel(pendingIntent)

        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                DebugLog.d(
                    "LectureAlarmScheduler: exact alarm not allowed, using inexact alarm"
                )

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

            DebugLog.d(
                "LectureAlarmScheduler: alarm scheduled successfully " +
                        "(trigger=$triggerAtMillis, requestCode=$requestCode)"
            )

        } catch (e: SecurityException) {
            DebugLog.d(
                "LectureAlarmScheduler: SecurityException, fallback used: ${e.message}"
            )

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

        val lectureKey = "$userId|$date|$startTime|$endTime"
        val requestCode = lectureKey.hashCode()

        val intent = Intent(context, LectureAlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)

        DebugLog.d(
            "LectureAlarmScheduler: alarm cancelled (requestCode=$requestCode)"
        )
    }
}
