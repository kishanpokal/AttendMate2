package com.kishan.attendmate.receivers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kishan.attendmate.R

/**
 * Shows the daily Day Confirmation notification.
 *
 * Responsibilities:
 * - Build notification
 * - Provide REGULAR / DAY OFF actions
 *
 * Does NOT:
 * - Touch Firestore
 * - Schedule lectures
 * - Read timetable
 */
class DayConfirmationAlarmReceiver : BroadcastReceiver() {

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {

        ensureChannel(context)

        val regularIntent = Intent(context, DayConfirmationActionReceiver::class.java).apply {
            action = ACTION_REGULAR
        }

        val dayOffIntent = Intent(context, DayConfirmationActionReceiver::class.java).apply {
            action = ACTION_DAY_OFF
        }

        val regularPendingIntent = PendingIntent.getBroadcast(
            context,
            ACTION_REGULAR.hashCode(),
            regularIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dayOffPendingIntent = PendingIntent.getBroadcast(
            context,
            ACTION_DAY_OFF.hashCode(),
            dayOffIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // use your app icon
            .setContentTitle("Today's Schedule")
            .setContentText("Is today a regular day?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_check,
                "Regular",
                regularPendingIntent
            )
            .addAction(
                R.drawable.ic_close,
                "Day Off",
                dayOffPendingIntent
            )
            .build()

        NotificationManagerCompat
            .from(context)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Day Confirmation",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Confirm whether today is a regular working day"
            }

            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_REGULAR =
            "com.kishan.attendmate.action.REGULAR_DAY"

        const val ACTION_DAY_OFF =
            "com.kishan.attendmate.action.DAY_OFF"

        const val CHANNEL_ID = "day_confirmation_channel"
        const val NOTIFICATION_ID = 2001
    }
}
