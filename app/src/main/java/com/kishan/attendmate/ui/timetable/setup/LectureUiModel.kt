package com.kishan.attendmate.ui.timetable.setup

import java.time.LocalTime

/**
 * Pure UI model.
 * No Firestore, no slotIndex, no Android dependencies.
 */
data class LectureUiModel(
    val id: String,
    val subjectId: String,
    val subjectName: String,
    val startTime: LocalTime,
    val durationHours: Int
) {
    val endTime: LocalTime
        get() = startTime.plusHours(durationHours.toLong())
}
