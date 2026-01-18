package com.kishan.attendmate.domain.lectures

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.alarms.DayConfirmationAlarmScheduler
import com.kishan.attendmate.alarms.LectureAlarmScheduler
import com.kishan.attendmate.notifications.LectureNotificationHelper
import com.kishan.attendmate.util.DebugLog
import java.time.LocalDate

/**
 * App-launch safety net.
 *
 * Ensures today's lectures are handled correctly when:
 * - User opens app late
 * - Alarms were missed
 * - App was force-stopped
 */
object TodayScheduleBootstrapper {

    fun run(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid
        val db = FirebaseFirestore.getInstance()

        val today = LocalDate.now()
        val todayStr = today.toString()
        val todayCode = today.dayOfWeek.name

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

                db.collection("users")
                    .document(userId)
                    .collection("calendar")
                    .document(todayStr)
                    .get()
                    .addOnSuccessListener { calendarDoc ->

                        val dayType = calendarDoc.getString("type")

                        when (dayType) {
                            "DAY_OFF" -> {
                                DebugLog.d("Bootstrapper: Day off")
                            }

                            "REGULAR" -> {
                                DebugLog.d("Bootstrapper: Regular day")
                                handleLectures(
                                    context = context,
                                    userId = userId,
                                    lectures = lectures,
                                    date = todayStr
                                )
                            }

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

    private fun handleUnconfirmedDay(
        context: Context,
        userId: String,
        lectures: List<TodayLecturePlanner.Lecture>,
        date: String
    ) {
        val confirmationTrigger =
            TodayLecturePlanner.dayConfirmationTriggerMillis(lectures)

        if (confirmationTrigger != null) {
            DebugLog.d("Bootstrapper: Scheduling day confirmation")
            DayConfirmationAlarmScheduler.schedule(
                context = context,
                triggerAtMillis = confirmationTrigger
            )
        } else {
            DebugLog.d("Bootstrapper: Confirmation missed → assume REGULAR")
            handleLectures(
                context = context,
                userId = userId,
                lectures = lectures,
                date = date
            )
        }
    }

    /**
     * Future lectures → schedule alarms
     * Past lectures → recover via notification (permission-safe)
     */
    private fun handleLectures(
        context: Context,
        userId: String,
        lectures: List<TodayLecturePlanner.Lecture>,
        date: String
    ) {
        val now = System.currentTimeMillis()

        lectures.forEach { lecture ->
            val triggerAtMillis =
                TodayLecturePlanner.lectureTriggerMillis(lecture)

            if (triggerAtMillis <= now) {
                showMissedLectureNotification(
                    context = context,
                    lecture = lecture,
                    date = date
                )
            } else {
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

    /**
     * Permission-safe notification delivery.
     */
    private fun showMissedLectureNotification(
        context: Context,
        lecture: TodayLecturePlanner.Lecture,
        date: String
    ) {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            DebugLog.d(
                "Bootstrapper: Notification permission not granted, skipped missed lecture " +
                        "(subject=${lecture.subjectName})"
            )
            return
        }

        try {
            DebugLog.d(
                "Bootstrapper: Missed lecture recovered " +
                        "(subject=${lecture.subjectName}, start=${lecture.startTime})"
            )

            LectureNotificationHelper.showLectureAttendanceNotification(
                context = context,
                subjectName = lecture.subjectName,
                subjectId = lecture.subjectId,
                date = date,
                startTime = lecture.startTime.toString(),
                endTime = lecture.endTime.toString()
            )
        } catch (e: SecurityException) {
            DebugLog.d(
                "Bootstrapper: SecurityException while showing notification: ${e.message}"
            )
        }
    }
}
