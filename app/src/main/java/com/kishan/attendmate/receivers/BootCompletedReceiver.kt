package com.kishan.attendmate.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.alarms.DayConfirmationAlarmScheduler
import com.kishan.attendmate.domain.lectures.TodayLecturePlanner
import java.time.LocalDate

/**
 * Restores Day Confirmation alarm after device reboot.
 *
 * IMPORTANT:
 * - Does NOT schedule lecture alarms
 * - Day confirmation remains the gatekeeper
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        val today = LocalDate.now()
        val todayCode = today.dayOfWeek.name // MONDAY, TUESDAY, ...

        db.collection("users")
            .document(user.uid)
            .collection("timetable")
            .whereEqualTo("day", todayCode)
            .get()
            .addOnSuccessListener { snapshot ->

                if (snapshot.isEmpty) return@addOnSuccessListener

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

                val lectures =
                    TodayLecturePlanner.buildLectures(slots)

                val confirmationTriggerMillis =
                    TodayLecturePlanner.dayConfirmationTriggerMillis(lectures)
                        ?: return@addOnSuccessListener

                DayConfirmationAlarmScheduler.schedule(
                    context = context,
                    triggerAtMillis = confirmationTriggerMillis
                )
            }
    }
}
