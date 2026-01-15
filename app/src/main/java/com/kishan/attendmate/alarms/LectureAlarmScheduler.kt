package com.kishan.attendmate.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kishan.attendmate.alarms.LectureAlarmReceiver
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object LectureAlarmScheduler {

    /**
     * Schedule a lecture reminder alarm.
     *
     * @param reminderMinutes minutes BEFORE lecture start
     */
    fun scheduleLecture(
        context: Context,
        subjectId: String,
        subjectName: String,
        date: String,        // yyyy-MM-dd
        startTime: String,   // HH:mm
        endTime: String,     // HH:mm
        reminderMinutes: Long = 15
    ) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        /* ---------- Parse lecture start ---------- */
        val lectureDate = LocalDate.parse(date)
        val lectureStart = LocalTime.parse(startTime)

        val lectureStartMillis = lectureDate
            .atTime(lectureStart)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val triggerAtMillis =
            lectureStartMillis - (reminderMinutes * 60_000)

        /* ---------- Build intent ---------- */
        val intent = Intent(context, LectureAlarmReceiver::class.java).apply {
            putExtra(LectureAlarmReceiver.EXTRA_SUBJECT_ID, subjectId)
            putExtra(LectureAlarmReceiver.EXTRA_SUBJECT_NAME, subjectName)
            putExtra(LectureAlarmReceiver.EXTRA_DATE, date)
            putExtra(LectureAlarmReceiver.EXTRA_START_TIME, startTime)
            putExtra(LectureAlarmReceiver.EXTRA_END_TIME, endTime)
        }

        val lectureKey = "${date}_${startTime}_$endTime"
        val requestCode = lectureKey.hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /* ---------- Idempotent scheduling ---------- */
        alarmManager.cancel(pendingIntent)

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
    }

    /**
     * Cancel a scheduled lecture reminder.
     */
    fun cancelLecture(
        context: Context,
        date: String,
        startTime: String,
        endTime: String
    ) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val lectureKey = "${date}_${startTime}_$endTime"

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
