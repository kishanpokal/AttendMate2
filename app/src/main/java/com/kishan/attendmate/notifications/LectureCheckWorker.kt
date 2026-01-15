//package com.kishan.attendmate.workers
//
//import android.Manifest
//import android.content.Context
//import androidx.annotation.RequiresPermission
//import androidx.work.CoroutineWorker
//import androidx.work.WorkerParameters
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.FirebaseFirestore
//import com.kishan.attendmate.domain.timetable.TimetableSlotMapper
//import com.kishan.attendmate.notifications.LectureNotificationHelper
//import kotlinx.coroutines.tasks.await
//import java.time.DayOfWeek
//import java.time.LocalDate
//import java.time.LocalDateTime
//import java.time.LocalTime
//import java.util.Locale
//
//class LectureCheckWorker(
//    appContext: Context,
//    params: WorkerParameters
//) : CoroutineWorker(appContext, params) {
//
//    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
//    override suspend fun doWork(): Result {
//
//        /* ---------- Must be logged in ---------- */
//        val user = FirebaseAuth.getInstance().currentUser
//            ?: return Result.success()
//
//        val now = LocalDateTime.now()
//
//        /* ---------- Ignore Sunday ---------- */
//        if (now.dayOfWeek == DayOfWeek.SUNDAY) {
//            return Result.success()
//        }
//
//        val db = FirebaseFirestore.getInstance()
//
//        /* ---------- Load today's timetable ---------- */
//        val todayCode = now.dayOfWeek.name // MON, TUE...
//
//        val timetableSnap = db
//            .collection("users")
//            .document(user.uid)
//            .collection("timetable")
//            .whereEqualTo("day", todayCode)
//            .get()
//            .await()
//
//        if (timetableSnap.isEmpty) {
//            return Result.success()
//        }
//
//        /* ---------- Group slots into lectures ---------- */
//        val slots = timetableSnap.documents
//            .mapNotNull {
//                val slot = it.getLong("slotIndex")?.toInt() ?: return@mapNotNull null
//                val subjectId = it.getString("subjectId") ?: return@mapNotNull null
//                val subjectName = it.getString("subjectName") ?: return@mapNotNull null
//                Triple(slot, subjectId, subjectName)
//            }
//            .sortedBy { it.first }
//
//        var i = 0
//        while (i < slots.size) {
//
//            val (startSlot, subjectId, subjectName) = slots[i]
//            var duration = 1
//
//            while (
//                i + duration < slots.size &&
//                slots[i + duration].first == startSlot + duration &&
//                slots[i + duration].second == subjectId
//            ) {
//                duration++
//            }
//
//            val lectureStartTime =
//                TimetableSlotMapper.slotIndexToTime(startSlot)
//
//            val lectureStartDateTime =
//                LocalDateTime.of(LocalDate.now(), lectureStartTime)
//
//            /* ---------- Check T + 15 min ---------- */
//            if (now.isBefore(lectureStartDateTime.plusMinutes(15))) {
//                i += duration
//                continue
//            }
//
//            /* ---------- Attendance already marked? ---------- */
//            val lectureKey =
//                "${todayCode}_${startSlot}_${duration}"
//
//            val attendanceSnap = db
//                .collection("users")
//                .document(user.uid)
//                .collection("subjects")
//                .document(subjectId)
//                .collection("attendance")
//                .whereEqualTo("lectureKey", lectureKey)
//                .limit(1)
//                .get()
//                .await()
//
//            if (!attendanceSnap.isEmpty) {
//                i += duration
//                continue
//            }
//
//            /* ---------- Notify ---------- */
//            LectureNotificationHelper.showLectureNotification(
//                context = applicationContext,
//                subjectName = subjectName,
//                subjectId = subjectId,
//                lectureKey = lectureKey
//            )
//
//            i += duration
//        }
//
//        return Result.success()
//    }
//}
package com.kishan.attendmate.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * SAFETY / MAINTENANCE worker.
 *
 * IMPORTANT:
 * - ❌ Does NOT send notifications
 * - ❌ Does NOT check lecture times
 * - ❌ Does NOT read Firestore timetable
 *
 * AlarmManager handles all time-based notifications.
 * This worker exists only as a fallback hook (future use).
 */
class LectureCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Intentionally empty.
        // In future:
        // - re-schedule alarms after reboot
        // - sanity checks
        return Result.success()
    }
}
