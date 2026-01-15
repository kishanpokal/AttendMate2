package com.kishan.attendmate.ui.timetable

const val LECTURE_CHANNEL_ID = "LECTURE_CHANNEL"

const val EXTRA_CELL_ID = "CELL_ID"
const val EXTRA_SUBJECT_ID = "SUBJECT_ID"
const val EXTRA_ALARM_TYPE = "ALARM_TYPE"

const val ACTION_KEY = "ACTION"
const val ACTION_PRESENT = "PRESENT"
const val ACTION_ABSENT = "ABSENT"
const val ACTION_CANCEL = "CANCEL"
const val ACTION_REGULAR = "REGULAR"
const val ACTION_CANCEL_LECTURE = "CANCEL_LECTURE"

enum class AlarmType {
    PRE_LECTURE,
    LECTURE_TIME
}
