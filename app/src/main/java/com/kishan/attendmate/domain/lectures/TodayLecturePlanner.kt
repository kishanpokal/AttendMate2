package com.kishan.attendmate.domain.lectures

import com.kishan.attendmate.domain.timetable.TimetableSlotMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure business logic.
 *
 * Converts timetable slots into lectures
 * and computes all notification trigger times for TODAY.
 *
 * No Android dependencies.
 */
object TodayLecturePlanner {

    data class Lecture(
        val subjectId: String,
        val subjectName: String,
        val startTime: LocalTime,
        val endTime: LocalTime
    )

    /**
     * Build today's lectures from slot-based timetable.
     */
    fun buildLectures(
        slots: List<Slot>
    ): List<Lecture> {
        if (slots.isEmpty()) return emptyList()

        val sorted = slots.sortedBy { it.slotIndex }

        val lectures = mutableListOf<Lecture>()

        var i = 0
        while (i < sorted.size) {
            val start = sorted[i]
            var duration = 1

            while (
                i + duration < sorted.size &&
                sorted[i + duration].slotIndex == start.slotIndex + duration &&
                sorted[i + duration].subjectId == start.subjectId
            ) {
                duration++
            }

            val startTime =
                TimetableSlotMapper.slotIndexToTime(start.slotIndex)

            val endTime =
                TimetableSlotMapper.slotIndexToTime(start.slotIndex + duration)

            lectures += Lecture(
                subjectId = start.subjectId,
                subjectName = start.subjectName,
                startTime = startTime,
                endTime = endTime
            )

            i += duration
        }

        return lectures
    }

    /**
     * Computes trigger millis for day confirmation:
     * first lecture start - 90 minutes.
     *
     * Returns null if no valid trigger exists.
     */
    fun dayConfirmationTriggerMillis(
        lectures: List<Lecture>
    ): Long? {
        if (lectures.isEmpty()) return null

        val firstLectureStart = lectures
            .minBy { it.startTime }
            .startTime

        val triggerTime =
            LocalDateTime.of(LocalDate.now(), firstLectureStart)
                .minusMinutes(90)

        val triggerMillis = triggerTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        return triggerMillis.takeIf {
            it > System.currentTimeMillis()
        }
    }

    /**
     * Computes lecture notification trigger millis:
     * lecture start + 15 minutes.
     */
    fun lectureTriggerMillis(
        lecture: Lecture
    ): Long {
        return LocalDateTime
            .of(LocalDate.now(), lecture.startTime)
            .plusMinutes(15)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Simple slot representation (Firestore-mapped).
     */
    data class Slot(
        val slotIndex: Int,
        val subjectId: String,
        val subjectName: String
    )
}
