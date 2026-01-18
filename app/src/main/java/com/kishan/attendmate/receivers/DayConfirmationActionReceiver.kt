package com.kishan.attendmate.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.alarms.LectureAlarmScheduler
import com.kishan.attendmate.domain.lectures.TodayLecturePlanner
import java.time.LocalDate

/**
 * Handles user response to Day Confirmation notification.
 *
 * Responsibilities:
 * - Persist day state (REGULAR / DAY_OFF)
 * - Cancel confirmation notification
 * - If REGULAR → schedule today's lecture alarms
 */
class DayConfirmationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        try {
            val action = intent.action ?: run {
                pendingResult.finish()
                return
            }

            val user = FirebaseAuth.getInstance().currentUser ?: run {
                pendingResult.finish()
                return
            }

            // Cancel confirmation notification
            NotificationManagerCompat.from(context)
                .cancel(DayConfirmationAlarmReceiver.NOTIFICATION_ID)

            val today = LocalDate.now()
            val todayStr = today.toString()

            val dayType = when (action) {
                DayConfirmationAlarmReceiver.ACTION_REGULAR -> "REGULAR"
                DayConfirmationAlarmReceiver.ACTION_DAY_OFF -> "DAY_OFF"
                else -> {
                    pendingResult.finish()
                    return
                }
            }

            val db = FirebaseFirestore.getInstance()

            db.collection("users")
                .document(user.uid)
                .collection("calendar")
                .document(todayStr)
                .set(
                    mapOf(
                        "date" to todayStr,
                        "type" to dayType,
                        "confirmedAt" to FieldValue.serverTimestamp()
                    )
                )
                .addOnSuccessListener {

                    if (dayType == "REGULAR") {
                        scheduleTodayLectures(
                            context = context,
                            db = db,
                            userId = user.uid,
                            today = today
                        )
                    }

                    pendingResult.finish()
                }
                .addOnFailureListener {
                    pendingResult.finish()
                }

        } catch (_: Exception) {
            pendingResult.finish()
        }
    }

    private fun scheduleTodayLectures(
        context: Context,
        db: FirebaseFirestore,
        userId: String,
        today: LocalDate
    ) {
        val todayCode = today.dayOfWeek.name

        db.collection("users")
            .document(userId)
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

                lectures.forEach { lecture ->

                    val triggerAtMillis =
                        TodayLecturePlanner.lectureTriggerMillis(lecture)

                    // ❌ Never schedule past alarms
                    if (triggerAtMillis <= System.currentTimeMillis()) return@forEach

                    LectureAlarmScheduler.scheduleLecture(
                        context = context,
                        userId = userId, // FirebaseAuth.getInstance().currentUser!!.uid
                        subjectId = lecture.subjectId,
                        subjectName = lecture.subjectName,
                        date = today.toString(),
                        startTime = lecture.startTime.toString(),
                        endTime = lecture.endTime.toString(),
                        triggerAtMillis = triggerAtMillis
                    )
                }
            }
    }
}
