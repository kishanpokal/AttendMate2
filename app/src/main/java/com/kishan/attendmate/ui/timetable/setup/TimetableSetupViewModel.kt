package com.kishan.attendmate.ui.timetable.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Date
import java.util.UUID

/* ---------------- Result Models ---------------- */

sealed class AddLectureResult {
    object Success : AddLectureResult()
    data class Error(val message: String) : AddLectureResult()
}

sealed class SaveTimetableResult {
    object Success : SaveTimetableResult()
    data class Error(val message: String) : SaveTimetableResult()
}

/* ---------------- ViewModel ---------------- */

/**
 * Manages WEEKLY timetable TEMPLATE only.
 *
 * Responsibilities:
 * - Weekly timetable CRUD (day + time + duration)
 * - Slot-based storage for UI rendering
 *
 * NOT responsible for:
 * - Calendar dates
 * - Lecture instances
 * - Notifications
 * - Attendance
 *
 * Lecture instances and notifications are derived later
 * based on calendar date + day confirmation.
 */
class TimetableSetupViewModel : ViewModel() {

    /* ---------------- Days ---------------- */

    val setupDays: List<DayOfWeek> = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY
    )

    private val _selectedDay =
        MutableStateFlow(DayOfWeek.MONDAY)
    val selectedDay: StateFlow<DayOfWeek> =
        _selectedDay.asStateFlow()

    /* ---------------- Timetable State ---------------- */

    private val _dayLectures =
        MutableStateFlow<Map<DayOfWeek, List<LectureUiModel>>>(emptyMap())
    val dayLectures: StateFlow<Map<DayOfWeek, List<LectureUiModel>>> =
        _dayLectures.asStateFlow()

    /* ---------------- Subjects ---------------- */

    private val _subjects =
        MutableStateFlow<List<SubjectUiModel>>(emptyList())
    val subjects: StateFlow<List<SubjectUiModel>> =
        _subjects.asStateFlow()

    fun setSubjects(list: List<SubjectUiModel>) {
        _subjects.value = list
    }

    /* ---------------- Init ---------------- */

    init {
        viewModelScope.launch {
            loadTimetableFromFirestore()
        }
    }

    /* ---------------- Public API ---------------- */

    fun selectDay(day: DayOfWeek) {
        _selectedDay.value = day
    }

    fun getLectures(day: DayOfWeek): List<LectureUiModel> =
        _dayLectures.value[day] ?: emptyList()

    fun isDayOff(day: DayOfWeek): Boolean =
        getLectures(day).isEmpty()

    fun isSetupComplete(): Boolean = true

    /* ---------------- Lecture CRUD ---------------- */

    fun addLecture(
        day: DayOfWeek,
        subjectId: String,
        subjectName: String,
        startTime: LocalTime,
        durationHours: Int
    ): AddLectureResult {

        if (durationHours < 1) {
            return AddLectureResult.Error("Lecture duration must be at least 1 hour")
        }

        val endTime = startTime.plusHours(durationHours.toLong())
        val newRange = startTime to endTime

        val overlap = getLectures(day).any { lecture ->
            rangesOverlap(
                newRange,
                lecture.startTime to lecture.endTime
            )
        }

        if (overlap) {
            return AddLectureResult.Error("This lecture overlaps with an existing one")
        }

        val newLecture = LectureUiModel(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            subjectName = subjectName,
            startTime = startTime,
            durationHours = durationHours
        )

        val updated = getLectures(day)
            .toMutableList()
            .apply { add(newLecture) }
            .sortedBy { it.startTime }

        _dayLectures.value =
            _dayLectures.value.toMutableMap().apply {
                put(day, updated)
            }

        return AddLectureResult.Success
    }

    fun removeLecture(day: DayOfWeek, lectureId: String) {
        _dayLectures.value =
            _dayLectures.value.toMutableMap().apply {
                put(day, getLectures(day).filterNot { it.id == lectureId })
            }
    }

    fun copyPreviousDay(day: DayOfWeek) {
        val index = setupDays.indexOf(day)
        if (index <= 0) return

        val copied = getLectures(setupDays[index - 1])
            .map { it.copy(id = UUID.randomUUID().toString()) }

        _dayLectures.value =
            _dayLectures.value.toMutableMap().apply {
                put(day, copied)
            }
    }

    /* ---------------- Save Timetable ---------------- */

    suspend fun saveTimetable(
        appContext: Context
    ): SaveTimetableResult {

        val user = FirebaseAuth.getInstance().currentUser
            ?: return SaveTimetableResult.Error("User not logged in")

        val db = FirebaseFirestore.getInstance()
        val timetableRef = db
            .collection("users")
            .document(user.uid)
            .collection("timetable")

        return try {
            val batch: WriteBatch = db.batch()

            // Delete old timetable
            timetableRef.get().await().documents.forEach {
                batch.delete(it.reference)
            }

            // Save weekly timetable TEMPLATE (per-lecture)
            _dayLectures.value.forEach { (day, lectures) ->
                lectures.forEach { lecture ->
                    val docId = "${day.name}_${lecture.id}"
                    batch.set(
                        timetableRef.document(docId),
                        mapOf(
                            "day" to day.name,
                            "subjectId" to lecture.subjectId,
                            "subjectName" to lecture.subjectName,
                            "startTime" to lecture.startTime.toString(),
                            "endTime" to lecture.endTime.toString(),
                            "durationHours" to lecture.durationHours,
                            "createdAt" to Date()
                        )
                    )
                }
            }

            batch.commit().await()

            SaveTimetableResult.Success

        } catch (e: Exception) {
            SaveTimetableResult.Error(
                e.message ?: "Failed to save timetable"
            )
        }
    }

    /* ---------------- Load Timetable ---------------- */

    suspend fun loadTimetableFromFirestore() {

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        val snapshot = db
            .collection("users")
            .document(user.uid)
            .collection("timetable")
            .get()
            .await()

        if (snapshot.isEmpty) {
            _dayLectures.value = emptyMap()
            return
        }

        val result = mutableMapOf<DayOfWeek, MutableList<LectureUiModel>>()

        snapshot.documents.forEach { doc ->
            val dayStr = doc.getString("day") ?: return@forEach
            val day = DayOfWeek.valueOf(dayStr)
            val subjectId = doc.getString("subjectId") ?: return@forEach
            val subjectName = doc.getString("subjectName") ?: return@forEach
            val startTimeStr = doc.getString("startTime") ?: return@forEach
            val duration = doc.getLong("durationHours")?.toInt() ?: return@forEach

            val startTime = LocalTime.parse(startTimeStr)

            val lecture = LectureUiModel(
                id = doc.id,
                subjectId = subjectId,
                subjectName = subjectName,
                startTime = startTime,
                durationHours = duration
            )

            result.getOrPut(day) { mutableListOf() }.add(lecture)
        }

        // Sort lectures by start time for each day
        result.forEach { (_, lectures) ->
            lectures.sortBy { it.startTime }
        }

        _dayLectures.value = result
    }

    /* ---------------- Utils ---------------- */

    private fun rangesOverlap(
        a: Pair<LocalTime, LocalTime>,
        b: Pair<LocalTime, LocalTime>
    ): Boolean =
        a.first < b.second && b.first < a.second
}