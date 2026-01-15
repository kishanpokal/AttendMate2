package com.kishan.attendmate.domain.timetable

import java.time.LocalTime

/**
 * Single source of truth for time ↔ slotIndex mapping.
 *
 * Slot rules:
 * - Each slot = 1 hour
 * - Day starts at 09:00
 *
 * 09:00 → slot 0
 * 10:00 → slot 1
 * ...
 */
object TimetableSlotMapper {

    private val DAY_START: LocalTime = LocalTime.of(9, 0)

    fun timeToSlotIndex(time: LocalTime): Int {
        val diffHours = time.hour - DAY_START.hour
        require(diffHours >= 0) {
            "Time must be >= 09:00"
        }
        return diffHours
    }

    fun slotIndexToTime(slotIndex: Int): LocalTime {
        require(slotIndex >= 0) {
            "slotIndex must be >= 0"
        }
        return DAY_START.plusHours(slotIndex.toLong())
    }
}
