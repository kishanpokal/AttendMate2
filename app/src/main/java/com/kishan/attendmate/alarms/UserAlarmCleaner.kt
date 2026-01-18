package com.kishan.attendmate.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kishan.attendmate.alarms.LectureAlarmReceiver

object UserAlarmCleaner {

    fun clearAllLectureAlarms(
        context: Context,
        userId: String,
        lectures: List<Pair<String, String>> // (startTime, endTime)
    ) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        lectures.forEach { (startTime, endTime) ->
            val key = "${userId}_${startTime}_$endTime"

            val intent = Intent(context, LectureAlarmReceiver::class.java)

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                key.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
        }
    }
}
