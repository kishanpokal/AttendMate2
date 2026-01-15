package com.kishan.attendmate.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kishan.attendmate.receivers.DayConfirmationAlarmReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules the DAILY Day Confirmation alarm.
 *
 * Guarantees:
 * - Exactly one upcoming confirmation alarm exists
 * - Works across app restarts, reboots, OEM quirks
 * - Safe to call multiple times
 */
object DayConfirmationAlarmScheduler {

    private const val REQUEST_CODE = 1001

    /**
     * Schedule the NEXT occurrence of the confirmation alarm.
     *
     * Example: triggerTime = 08:00
     */
    fun schedule(
        context: Context,
        triggerTime: LocalTime
    ) {
        val triggerMillis = nextTriggerMillis(triggerTime)

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
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // OEM / policy safety net
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent
            )
        }
    }

    /**
     * Returns millis for the NEXT occurrence of triggerTime.
     * If today's time has passed → schedules for tomorrow.
     */
    private fun nextTriggerMillis(triggerTime: LocalTime): Long {
        val now = LocalDateTime.now()

        val todayTrigger = LocalDateTime.of(LocalDate.now(), triggerTime)

        val nextTrigger = if (todayTrigger.isAfter(now)) {
            todayTrigger
        } else {
            todayTrigger.plusDays(1)
        }

        return nextTrigger
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
