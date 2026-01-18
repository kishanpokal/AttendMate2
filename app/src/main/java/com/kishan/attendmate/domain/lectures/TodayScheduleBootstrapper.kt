package com.kishan.attendmate.domain.lectures

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.alarms.DayConfirmationAlarmScheduler
import com.kishan.attendmate.alarms.LectureAlarmScheduler
import com.kishan.attendmate.util.DebugLog
import java.time.LocalDate

/**
 * App-launch safety net.
 *
 * Ensures today's alarms are correctly scheduled when:
 * - User opens app late
 * - Phone never rebooted
 * - App was force-stopped
 *
 * Runs ONLY on app open.
 */
object TodayScheduleBootstrapper {

    fun run(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid
        val db = FirebaseFirestore.getInstance()

        val today = LocalDate.now()
        val todayStr = today.toString()
        val todayCode = today.dayOfWeek.name

        // 1️⃣ Load today's timetable
        db.collection("users")
            .document(userId)
            .collection("timetable")
            .whereEqualTo("day", todayCode)
            .get()
            .addOnSuccessListener { timetableSnap ->

                if (timetableSnap.isEmpty) {
                    DebugLog.d("Bootstrapper: No lectures today")
                    return@addOnSuccessListener
                }

                val slots = timetableSnap.documents.mapNotNull { doc ->
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

                if (lectures.isEmpty()) return@addOnSuccessListener

                // 2️⃣ Check if day is already confirmed
                db.collection("users")
                    .document(userId)
                    .collection("calendar")
                    .document(todayStr)
                    .get()
                    .addOnSuccessListener { calendarDoc ->

                        val dayType = calendarDoc.getString("type")

                        when (dayType) {

                            // ❌ Day off → do nothing
                            "DAY_OFF" -> {
                                DebugLog.d("Bootstrapper: Day off")
                            }

                            // ✅ Regular day → ensure lecture alarms exist
                            "REGULAR" -> {
                                DebugLog.d("Bootstrapper: Regular day → schedule lectures")
                                scheduleLectures(
                                    context = context,
                                    userId = userId,
                                    lectures = lectures,
                                    date = todayStr
                                )
                            }

                            // ⚠️ Not confirmed yet
                            else -> {
                                handleUnconfirmedDay(
                                    context = context,
                                    userId = userId,
                                    lectures = lectures,
                                    date = todayStr
                                )
                            }
                        }
                    }
            }
    }

    /**
     * Handle day not yet confirmed.
     */
    private fun handleUnconfirmedDay(
        context: Context,
        userId: String,
        lectures: List<TodayLecturePlanner.Lecture>,
        date: String
    ) {
        val confirmationTrigger =
            TodayLecturePlanner.dayConfirmationTriggerMillis(lectures)

        when {
            // ⏰ Confirmation still in future → schedule it
            confirmationTrigger != null -> {
                DebugLog.d("Bootstrapper: Scheduling day confirmation")
                DayConfirmationAlarmScheduler.schedule(
                    context = context,
                    triggerAtMillis = confirmationTrigger
                )
            }

            // ⏱ Confirmation time passed → assume REGULAR day
            else -> {
                DebugLog.d("Bootstrapper: Confirmation missed → assume REGULAR")
                scheduleLectures(
                    context = context,
                    userId = userId,
                    lectures = lectures,
                    date = date
                )
            }
        }
    }

    /**
     * Schedule lecture attendance alarms (+15 min).
     */
    private fun scheduleLectures(
        context: Context,
        userId: String,
        lectures: List<TodayLecturePlanner.Lecture>,
        date: String
    ) {
        lectures.forEach { lecture ->
            val triggerAtMillis =
                TodayLecturePlanner.lectureTriggerMillis(lecture)

            // ❌ Never schedule past alarms
            if (triggerAtMillis <= System.currentTimeMillis()) return@forEach

            LectureAlarmScheduler.scheduleLecture(
                context = context,
                userId = userId,
                subjectId = lecture.subjectId,
                subjectName = lecture.subjectName,
                date = date,
                startTime = lecture.startTime.toString(),
                endTime = lecture.endTime.toString(),
                triggerAtMillis = triggerAtMillis
            )
        }
    }
}
