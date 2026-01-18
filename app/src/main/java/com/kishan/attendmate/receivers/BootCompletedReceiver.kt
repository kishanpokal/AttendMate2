package com.kishan.attendmate.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.alarms.DayConfirmationAlarmScheduler
import com.kishan.attendmate.domain.lectures.TodayLecturePlanner
import com.kishan.attendmate.util.DebugLog
import java.time.LocalDate

/**
 * Restores Day Confirmation alarm after device reboot.
 *
 * Notes:
 * - FirebaseAuth user may NOT be ready after boot
 * - Lecture recovery is handled elsewhere
 * - Must never crash or block boot
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        DebugLog.d("BootCompletedReceiver: BOOT_COMPLETED received")

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            DebugLog.d(
                "BootCompletedReceiver: No logged-in user after boot. " +
                        "Day confirmation will be restored later."
            )
            return
        }

        val db = FirebaseFirestore.getInstance()

        val today = LocalDate.now()
        val todayCode = today.dayOfWeek.name

        db.collection("users")
            .document(user.uid)
            .collection("timetable")
            .whereEqualTo("day", todayCode)
            .get()
            .addOnSuccessListener { snapshot ->

                if (snapshot.isEmpty) {
                    DebugLog.d(
                        "BootCompletedReceiver: No timetable entries for today"
                    )
                    return@addOnSuccessListener
                }

                val slots = snapshot.documents.mapNotNull { doc ->
                    val slotIndex = doc.getLong("slotIndex")?.toInt()
                        ?: return@mapNotNull null

                    val subjectId = doc.getString("subjectId")
                        ?: return@mapNotNull null

                    val subjectName = doc.getString("subjectName")
                        ?: return@mapNotNull null

                    TodayLecturePlanner.Slot(
                        slotIndex = slotIndex,
                        subjectId = subjectId,
                        subjectName = subjectName
                    )
                }

                if (slots.isEmpty()) {
                    DebugLog.d(
                        "BootCompletedReceiver: Slots empty after parsing"
                    )
                    return@addOnSuccessListener
                }

                val lectures =
                    TodayLecturePlanner.buildLectures(slots)

                val confirmationTriggerMillis =
                    TodayLecturePlanner.dayConfirmationTriggerMillis(lectures)

                if (confirmationTriggerMillis == null) {
                    DebugLog.d(
                        "BootCompletedReceiver: Confirmation time already passed"
                    )
                    return@addOnSuccessListener
                }

                DayConfirmationAlarmScheduler.schedule(
                    context = context,
                    triggerAtMillis = confirmationTriggerMillis
                )

                DebugLog.d(
                    "BootCompletedReceiver: Day confirmation alarm restored"
                )
            }
            .addOnFailureListener { e ->
                DebugLog.d(
                    "BootCompletedReceiver: Firestore failed: ${e.message}"
                )
            }
    }
}
