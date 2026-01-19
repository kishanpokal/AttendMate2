package com.kishan.attendmate.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kishan.attendmate.alarms.DayConfirmationAlarmScheduler
import java.time.LocalTime

/**
 * Restores critical alarms after device reboot.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            // Restore daily day-confirmation alarm (08:00 AM)
            DayConfirmationAlarmScheduler.schedule(
                context = context,
                triggerTime = LocalTime.of(8, 0)
            )
        }
    }
}
