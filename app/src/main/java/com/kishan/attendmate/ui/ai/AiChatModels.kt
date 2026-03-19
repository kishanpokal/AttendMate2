package com.kishan.attendmate.ui.ai

/* ────────────────── Message Types ────────────────── */

enum class MessageType {
    TEXT,
    CONFIRM_MARK,
    CONFIRM_DELETE,
    ATTENDANCE_CARD,
    TIMETABLE_CARD,
    ANALYSIS_CARD,
    // ── New advanced cards ──
    PREDICTION_CARD,
    STUDY_TIPS_CARD,
    WEEKLY_SUMMARY_CARD,
    GOAL_CARD,
    TREND_CARD
}

/* ────────────────── Chat Message ────────────────── */

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val messageType: MessageType = MessageType.TEXT,
    val attendanceData: List<SubjectAttendanceData>? = null,
    val timetableData: List<TimetableSlot>? = null,
    val analysisData: AttendanceAnalysisData? = null,
    val pendingMarks: List<PendingMarkAttendance>? = null,
    val pendingDeletes: List<PendingDeleteAttendance>? = null,
    // ── New card data ──
    val predictionData: List<PredictionEngine.SubjectPrediction>? = null,
    val studyTipsData: StudyTipsCardData? = null,
    val weeklySummaryData: PredictionEngine.WeeklySummaryData? = null,
    val goalData: GoalCardData? = null,
    val trendData: List<PredictionEngine.SubjectTrend>? = null
)

/* ────────────────── Rich Data Payloads ────────────────── */

data class SubjectAttendanceData(
    val name: String,
    val attended: Int,
    val total: Int,
    val percentage: Int
)

data class TimetableSlot(
    val subjectName: String,
    val startTime: String,
    val endTime: String,
    val isOngoing: Boolean = false
)

data class AttendanceAnalysisData(
    val overallPct: Int,
    val totalAttended: Int,
    val totalClasses: Int,
    val bestSubject: String,
    val bestPct: Int,
    val worstSubject: String,
    val worstPct: Int,
    val subjects: List<SubjectAttendanceData>,
    val atRisk: List<SubjectAttendanceData>
)

/* ────────────────── Pending Actions ────────────────── */

data class PendingMarkAttendance(
    val subjectId: String,
    val subjectName: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val status: String
)

data class PendingDeleteAttendance(
    val subjectId: String,
    val subjectName: String,
    val attendanceId: String,
    val startTime: String,
    val endTime: String,
    val status: String
)

/* ────────────────── NEW: Study Tips Card Data ────────────────── */

data class StudyTipsCardData(
    val subjectName: String,
    val currentPct: Int,
    val urgencyLevel: String,   // "critical", "warning", "safe", "excellent"
    val tips: List<PredictionEngine.StudyTip>
)

/* ────────────────── NEW: Goal Card Data ────────────────── */

data class GoalCardData(
    val subjectName: String,
    val targetPct: Int,
    val currentPct: Int,
    val classesNeeded: Int,
    val totalClasses: Int,
    val attendedClasses: Int,
    val isAchieved: Boolean
)

/* ────────────────── UI State ────────────────── */

sealed interface AiChatUiState {
    object Initial : AiChatUiState
    object Loading : AiChatUiState
    object Success : AiChatUiState
    data class Error(val message: String) : AiChatUiState
}
