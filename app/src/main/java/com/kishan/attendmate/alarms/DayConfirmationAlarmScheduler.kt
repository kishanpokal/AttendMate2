package com.kishan.attendmate.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kishan.attendmate.receivers.DayConfirmationAlarmReceiver

/**
 * Schedules the Day Confirmation alarm for TODAY only.
 *
 * Rules:
 * - Exactly one confirmation alarm at any time
 * - Alarm time is computed elsewhere (business logic)
 * - Never schedules for tomorrow automatically
 * - Safe to call multiple times
 */
object DayConfirmationAlarmScheduler {

    private const val REQUEST_CODE = 1001

    /**
     * Schedule day confirmation at an exact timestamp (millis).
     *
     * @param triggerAtMillis absolute time in millis
     */
    fun schedule(
        context: Context,
        triggerAtMillis: Long
    ) {
        // ❌ Never schedule past alarms
        if (triggerAtMillis <= System.currentTimeMillis()) {
            return
        }

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, DayConfirmationAlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Idempotent: remove any previous alarm
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
     * Cancel any scheduled day confirmation alarm.
     */
    fun cancel(context: Context) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, DayConfirmationAlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }
}
