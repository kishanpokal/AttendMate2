package com.kishan.attendmate.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

/**
 * Handles user response to Day Confirmation notification.
 *
 * Responsibilities:
 * - Persist day state (REGULAR / DAY_OFF)
 * - Cancel confirmation notification
 *
 * Does NOT:
 * - Schedule lecture alarms
 * - Read timetable slots
 * - Compute times
 */
class DayConfirmationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        try {
            val action = intent.action ?: return pendingResult.finish()
            val user = FirebaseAuth.getInstance().currentUser
                ?: return pendingResult.finish()

            // Cancel the confirmation notification
            NotificationManagerCompat.from(context)
                .cancel(DayConfirmationAlarmReceiver.NOTIFICATION_ID)

            val today = LocalDate.now().toString() // yyyy-MM-dd

            val dayType = when (action) {
                DayConfirmationAlarmReceiver.ACTION_REGULAR -> "REGULAR"
                DayConfirmationAlarmReceiver.ACTION_DAY_OFF -> "DAY_OFF"
                else -> return pendingResult.finish()
            }

            val db = FirebaseFirestore.getInstance()

            // Persist day state
            db.collection("users")
                .document(user.uid)
                .collection("calendar")
                .document(today)
                .set(
                    mapOf(
                        "date" to today,
                        "type" to dayType,
                        "confirmedAt" to FieldValue.serverTimestamp()
                    )
                )
                .addOnCompleteListener {
                    pendingResult.finish()
                }

        } catch (e: Exception) {
            pendingResult.finish()
        }
    }
}
