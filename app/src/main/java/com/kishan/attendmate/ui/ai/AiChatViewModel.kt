package com.kishan.attendmate.ui.ai

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /* ────────────────── UI State ────────────────── */

    private val _uiState = MutableStateFlow<AiChatUiState>(AiChatUiState.Initial)
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    val messages = mutableStateListOf<ChatMessage>()

    // No daily limit for rule-based AI — always available
    private val _remainingRequests = MutableStateFlow(999)
    val remainingRequests: StateFlow<Int> = _remainingRequests.asStateFlow()

    // Pending mark attendance state for confirmation flow
    private var pendingMark: PendingMarkAttendance? = null

    init {
        messages.add(
            ChatMessage(
                text = "👋 Hello! I'm your **AttendMate Assistant**.\n\n" +
                    "I can help you with:\n" +
                    "📊 \"Show my attendance\" — overall stats\n" +
                    "📚 \"Attendance in Math\" — specific subject\n" +
                    "📅 \"Attendance on 2026-03-10\" — specific date\n" +
                    "🗓️ \"My schedule today\" — today's timetable\n" +
                    "📈 \"Analysis\" — full attendance analysis\n" +
                    "✏️ \"Mark Present in Math on 2026-03-11 09:00-10:00\" — mark attendance\n\n" +
                    "How can I help you?",
                isUser = false
            )
        )
    }

    /* ────────────────── Intent Detection ────────────────── */

    private enum class Intent {
        GREETING,
        HELP,
        SUBJECT_SUMMARY,
        ATTENDANCE_FOR_DATE,
        TIMETABLE,
        OVERALL_ANALYSIS,
        MARK_ATTENDANCE,
        CONFIRM_YES,
        CONFIRM_NO,
        UNKNOWN
    }

    private fun detectIntent(input: String): Intent {
        val lower = input.lowercase().trim()

        // Confirmation for pending mark
        if (pendingMark != null) {
            if (lower in listOf("yes", "y", "confirm", "ok", "sure", "yeah", "yep", "do it", "go ahead")) return Intent.CONFIRM_YES
            if (lower in listOf("no", "n", "cancel", "nah", "nope", "stop", "nevermind")) return Intent.CONFIRM_NO
        }

        // Mark attendance (must check before general subject query)
        if (lower.contains("mark") && (lower.contains("present") || lower.contains("absent"))) return Intent.MARK_ATTENDANCE

        // Greetings
        if (lower.matches(Regex("^(hi|hello|hey|yo|sup|hii+|good morning|good afternoon|good evening).*"))) return Intent.GREETING

        // Help
        if (lower.contains("help") || lower.contains("what can you do") || lower.contains("commands")) return Intent.HELP

        // Overall analysis
        if (lower.contains("analysis") || lower.contains("analyze") || lower.contains("overview") ||
            lower.contains("how am i doing") || lower.contains("overall") || lower.contains("report")) return Intent.OVERALL_ANALYSIS

        // Attendance for a specific date
        if ((lower.contains("attendance") || lower.contains("record")) &&
            (lower.contains("on ") || lower.contains("today") || lower.contains("yesterday") ||
             lower.contains("date") || Regex("\\d{4}-\\d{2}-\\d{2}").containsMatchIn(lower))) return Intent.ATTENDANCE_FOR_DATE

        // Timetable / schedule
        if (lower.contains("timetable") || lower.contains("schedule") || lower.contains("classes today") ||
            lower.contains("lectures today") || lower.contains("what class") || lower.contains("my schedule")) return Intent.TIMETABLE

        // Subject-specific attendance
        if (lower.contains("attendance") || lower.contains("subject") || lower.contains("how many class") ||
            lower.contains("percentage") || lower.contains("show my")) return Intent.SUBJECT_SUMMARY

        return Intent.UNKNOWN
    }

    /* ────────────────── Send Message ────────────────── */

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        messages.add(ChatMessage(text = userMessage, isUser = true))
        _uiState.value = AiChatUiState.Loading

        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid
                if (userId == null) {
                    reply("❌ Please log in to use the assistant.")
                    return@launch
                }

                val intent = detectIntent(userMessage)
                when (intent) {
                    Intent.GREETING -> reply("👋 Hello! How can I help you with your attendance today?")
                    Intent.HELP -> reply(getHelpText())
                    Intent.SUBJECT_SUMMARY -> handleSubjectSummary(userId, userMessage)
                    Intent.ATTENDANCE_FOR_DATE -> handleAttendanceForDate(userId, userMessage)
                    Intent.TIMETABLE -> handleTimetable(userId, userMessage)
                    Intent.OVERALL_ANALYSIS -> handleOverallAnalysis(userId)
                    Intent.MARK_ATTENDANCE -> handleMarkAttendance(userId, userMessage)
                    Intent.CONFIRM_YES -> handleConfirmYes(userId)
                    Intent.CONFIRM_NO -> handleConfirmNo()
                    Intent.UNKNOWN -> reply(
                        "🤔 I didn't quite understand that. Here's what I can do:\n\n" +
                        "📊 \"Show my attendance\"\n" +
                        "📚 \"Attendance in [Subject]\"\n" +
                        "📅 \"Attendance on 2026-03-10\"\n" +
                        "🗓️ \"My schedule today\"\n" +
                        "📈 \"Analysis\"\n" +
                        "✏️ \"Mark Present in [Subject] on [date] [start]-[end]\""
                    )
                }
            } catch (e: Exception) {
                Log.e("AiChat", "Error processing message", e)
                reply("❌ Something went wrong: ${e.message}")
            }
        }
    }

    private fun reply(text: String) {
        messages.add(ChatMessage(text = text, isUser = false))
        _uiState.value = AiChatUiState.Success
    }

    /* ────────────────── Handlers ────────────────── */

    private suspend fun handleSubjectSummary(userId: String, input: String) {
        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) {
            reply("📭 You haven't added any subjects yet. Go to the app to add subjects first!")
            return
        }

        // Try to find a specific subject name in the input
        val lower = input.lowercase()
        val matchedSubject = subjects.entries.find { (_, name) ->
            lower.contains(name.lowercase())
        }

        if (matchedSubject != null) {
            // Specific subject
            val doc = db.collection("users").document(userId)
                .collection("subjects").document(matchedSubject.key).get().await()
            val name = doc.getString("name") ?: "Unknown"
            val total = doc.getLong("totalClasses")?.toInt() ?: 0
            val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
            val missed = total - attended
            val pct = if (total > 0) (attended * 100f / total).toInt() else 0
            val status = when {
                pct >= 75 -> "✅ SAFE"
                pct >= 60 -> "⚠️ WARNING"
                else -> "🚨 CRITICAL"
            }

            reply(
                "📚 **$name**\n\n" +
                "├ Total Classes: **$total**\n" +
                "├ Attended: **$attended**\n" +
                "├ Missed: **$missed**\n" +
                "├ Percentage: **$pct%**\n" +
                "└ Status: $status" +
                if (pct < 75 && total > 0) {
                    val needed = Math.ceil((0.75 * total - attended) / 0.25).toInt()
                    "\n\n💡 You need **$needed** more consecutive classes to reach 75%."
                } else ""
            )
        } else {
            // All subjects
            val sb = StringBuilder("📊 **All Subjects Summary**\n\n")
            for ((id, name) in subjects) {
                val doc = db.collection("users").document(userId)
                    .collection("subjects").document(id).get().await()
                val total = doc.getLong("totalClasses")?.toInt() ?: 0
                val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
                val pct = if (total > 0) (attended * 100f / total).toInt() else 0
                val icon = when {
                    pct >= 75 -> "✅"
                    pct >= 60 -> "⚠️"
                    else -> "🚨"
                }
                sb.append("$icon **$name** — $pct% ($attended/$total)\n")
            }
            reply(sb.toString().trimEnd())
        }
    }

    private suspend fun handleAttendanceForDate(userId: String, input: String) {
        val dateStr = extractDate(input)
        if (dateStr == null) {
            reply("📅 Please specify a date. Example: \"Attendance on 2026-03-10\" or \"Attendance today\"")
            return
        }

        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) {
            reply("📭 No subjects found.")
            return
        }

        val records = mutableListOf<String>()
        var presentCount = 0
        var absentCount = 0

        for ((subjectId, subjectName) in subjects) {
            val attSnap = db.collection("users").document(userId)
                .collection("subjects").document(subjectId)
                .collection("attendance").get().await()

            for (doc in attSnap.documents) {
                val recordDate = when (val raw = doc.get("date")) {
                    is String -> raw
                    is Timestamp -> {
                        val ld = raw.toDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        ld.toString()
                    }
                    else -> continue
                }
                if (recordDate == dateStr) {
                    val status = doc.getString("status")?.uppercase() ?: "ABSENT"
                    val startTime = readTimeField(doc, "startTime")
                    val endTime = readTimeField(doc, "endTime")
                    val icon = if (status == "PRESENT") "✅" else "❌"
                    if (status == "PRESENT") presentCount++ else absentCount++
                    records.add("$icon **$subjectName** ($startTime - $endTime) — $status")
                }
            }
        }

        if (records.isEmpty()) {
            reply("📅 No attendance records found for **$dateStr**.")
        } else {
            val sb = StringBuilder("📅 **Attendance on $dateStr**\n\n")
            records.forEach { sb.append("$it\n") }
            sb.append("\n✅ Present: **$presentCount** | ❌ Absent: **$absentCount**")
            reply(sb.toString())
        }
    }

    private suspend fun handleTimetable(userId: String, input: String) {
        val day = extractDayOfWeek(input)
        val timetableSnap = db.collection("users").document(userId)
            .collection("timetable")
            .whereEqualTo("day", day)
            .get().await()

        if (timetableSnap.isEmpty) {
            reply("🗓️ No lectures scheduled for **$day**.")
            return
        }

        val sb = StringBuilder("🗓️ **Schedule for $day**\n\n")
        val lectures = timetableSnap.documents.sortedBy { it.getString("startTime") ?: "" }
        for (doc in lectures) {
            val name = doc.getString("subjectName") ?: "Unknown"
            val start = doc.getString("startTime") ?: "--"
            val end = doc.getString("endTime") ?: "--"
            sb.append("📖 **$name** — $start to $end\n")
        }
        sb.append("\nTotal: **${lectures.size}** lectures")
        reply(sb.toString())
    }

    private suspend fun handleOverallAnalysis(userId: String) {
        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) {
            reply("📭 No subjects found. Add subjects first!")
            return
        }

        var totalAll = 0
        var attendedAll = 0
        var bestName = ""
        var bestPct = -1
        var worstName = ""
        var worstPct = 101
        val atRisk = mutableListOf<String>()

        for ((id, name) in subjects) {
            val doc = db.collection("users").document(userId)
                .collection("subjects").document(id).get().await()
            val total = doc.getLong("totalClasses")?.toInt() ?: 0
            val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
            val pct = if (total > 0) (attended * 100f / total).toInt() else 0

            totalAll += total
            attendedAll += attended

            if (pct > bestPct) { bestPct = pct; bestName = name }
            if (pct < worstPct && total > 0) { worstPct = pct; worstName = name }
            if (pct < 75 && total > 0) {
                val needed = Math.ceil((0.75 * total - attended) / 0.25).toInt()
                atRisk.add("🚨 **$name** — $pct% (need $needed more classes)")
            }
        }

        val overallPct = if (totalAll > 0) (attendedAll * 100f / totalAll).toInt() else 0
        val overallIcon = when {
            overallPct >= 75 -> "✅"
            overallPct >= 60 -> "⚠️"
            else -> "🚨"
        }

        val sb = StringBuilder("📈 **Attendance Analysis**\n\n")
        sb.append("$overallIcon Overall: **$overallPct%** ($attendedAll/$totalAll)\n")
        sb.append("📚 Subjects: **${subjects.size}**\n")
        sb.append("✅ Total Attended: **$attendedAll**\n")
        sb.append("❌ Total Missed: **${totalAll - attendedAll}**\n\n")

        if (bestName.isNotEmpty()) sb.append("🏆 Best: **$bestName** ($bestPct%)\n")
        if (worstName.isNotEmpty()) sb.append("📉 Worst: **$worstName** ($worstPct%)\n")

        if (atRisk.isNotEmpty()) {
            sb.append("\n⚠️ **Subjects at Risk (below 75%):**\n")
            atRisk.forEach { sb.append("$it\n") }
        } else {
            sb.append("\n🎉 All subjects are above 75%! Keep it up!")
        }

        reply(sb.toString().trimEnd())
    }

    /* ────────────────── Mark Attendance ────────────────── */

    private suspend fun handleMarkAttendance(userId: String, input: String) {
        val lower = input.lowercase()

        // Determine status
        val status = when {
            lower.contains("present") -> "Present"
            lower.contains("absent") -> "Absent"
            else -> {
                reply("❓ Please specify status: **Present** or **Absent**.\nExample: \"Mark Present in Math on 2026-03-11 09:00-10:00\"")
                return
            }
        }

        // Find date
        val dateStr = extractDate(input)
        if (dateStr == null) {
            reply("📅 Please include a date.\nExample: \"Mark $status in Math on 2026-03-11 09:00-10:00\"")
            return
        }

        // Find subject
        val subjects = fetchSubjects(userId)
        val matchedSubject = subjects.entries.find { (_, name) ->
            lower.contains(name.lowercase())
        }
        if (matchedSubject == null) {
            val subjectList = subjects.values.joinToString(", ")
            reply("📚 Couldn't find the subject. Your subjects are: **$subjectList**\n\nExample: \"Mark $status in [Subject] on $dateStr 09:00-10:00\"")
            return
        }

        // Find times (HH:mm-HH:mm or HH:mm to HH:mm)
        val timeRegex = Regex("(\\d{1,2}:\\d{2})\\s*[-–to]+\\s*(\\d{1,2}:\\d{2})")
        val timeMatch = timeRegex.find(input)
        val startTime = timeMatch?.groupValues?.get(1) ?: ""
        val endTime = timeMatch?.groupValues?.get(2) ?: ""

        if (startTime.isEmpty() || endTime.isEmpty()) {
            reply("⏰ Please include start and end time.\nExample: \"Mark $status in ${matchedSubject.value} on $dateStr **09:00-10:00**\"")
            return
        }

        // Store pending and ask confirmation
        pendingMark = PendingMarkAttendance(
            subjectId = matchedSubject.key,
            subjectName = matchedSubject.value,
            date = dateStr,
            startTime = startTime,
            endTime = endTime,
            status = status
        )

        reply(
            "✏️ **Confirm Attendance Mark:**\n\n" +
            "📚 Subject: **${matchedSubject.value}**\n" +
            "📅 Date: **$dateStr**\n" +
            "⏰ Time: **$startTime - $endTime**\n" +
            "📝 Status: **$status**\n\n" +
            "Type **Yes** to confirm or **No** to cancel."
        )
    }

    private suspend fun handleConfirmYes(userId: String) {
        val mark = pendingMark
        if (mark == null) {
            reply("🤔 Nothing to confirm.")
            return
        }

        try {
            val lectureId = "${mark.date}_${mark.startTime.replace(":", "")}_${mark.endTime.replace(":", "")}"

            val subjectRef = db.collection("users").document(userId)
                .collection("subjects").document(mark.subjectId)

            val attendanceRef = subjectRef.collection("attendance").document(lectureId)

            // Check if already exists
            val existing = attendanceRef.get().await()
            if (existing.exists()) {
                reply("⚠️ Attendance already marked for **${mark.subjectName}** on **${mark.date}** (${mark.startTime}-${mark.endTime}). Use the app to edit it.")
                pendingMark = null
                return
            }

            // Parse date
            val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(mark.date)
            val timestamp = if (parsedDate != null) Timestamp(parsedDate) else Timestamp.now()

            // Write attendance document
            val attendanceData = hashMapOf(
                "date" to timestamp,
                "status" to mark.status,
                "startTime" to mark.startTime,
                "endTime" to mark.endTime,
                "markedAt" to FieldValue.serverTimestamp()
            )
            attendanceRef.set(attendanceData).await()

            // Update subject counters
            val updateMap = mutableMapOf<String, Any>(
                "totalClasses" to FieldValue.increment(1)
            )
            if (mark.status == "Present") {
                updateMap["attendedClasses"] = FieldValue.increment(1)
            }
            subjectRef.update(updateMap).await()

            reply(
                "✅ **Attendance Marked!**\n\n" +
                "📚 ${mark.subjectName}\n" +
                "📅 ${mark.date} (${mark.startTime} - ${mark.endTime})\n" +
                "📝 ${mark.status}\n\n" +
                "Your attendance has been updated."
            )
        } catch (e: Exception) {
            Log.e("AiChat", "Error marking attendance", e)
            reply("❌ Failed to mark attendance: ${e.message}")
        } finally {
            pendingMark = null
        }
    }

    private fun handleConfirmNo() {
        pendingMark = null
        reply("❌ Cancelled. Attendance was not marked.")
    }

    /* ────────────────── Utility Functions ────────────────── */

    private suspend fun fetchSubjects(userId: String): Map<String, String> {
        val snap = try {
            val cached = db.collection("users").document(userId)
                .collection("subjects").get(Source.CACHE).await()
            if (cached.isEmpty) throw Exception("Cache empty")
            cached
        } catch (e: Exception) {
            db.collection("users").document(userId).collection("subjects").get(Source.SERVER).await()
        }
        return snap.documents.associate { it.id to (it.getString("name") ?: "Unknown") }
    }

    private fun extractDate(input: String): String? {
        val lower = input.lowercase()

        // Exact date match: yyyy-MM-dd
        val dateRegex = Regex("(\\d{4}-\\d{2}-\\d{2})")
        dateRegex.find(input)?.let { return it.value }

        // Today
        if (lower.contains("today")) return LocalDate.now().toString()

        // Yesterday
        if (lower.contains("yesterday")) return LocalDate.now().minusDays(1).toString()

        // Day names → most recent occurrence
        val dayMap = mapOf(
            "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY
        )
        for ((name, day) in dayMap) {
            if (lower.contains(name)) {
                var date = LocalDate.now()
                while (date.dayOfWeek != day) date = date.minusDays(1)
                return date.toString()
            }
        }

        return null
    }

    private fun extractDayOfWeek(input: String): String {
        val lower = input.lowercase()
        if (lower.contains("today")) return LocalDate.now().dayOfWeek.name
        if (lower.contains("tomorrow")) return LocalDate.now().plusDays(1).dayOfWeek.name

        val days = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        for (d in days) {
            if (lower.contains(d)) return d.uppercase()
        }
        return LocalDate.now().dayOfWeek.name
    }

    private fun readTimeField(doc: com.google.firebase.firestore.DocumentSnapshot, field: String): String {
        val value = doc.get(field) ?: return "--"
        return when (value) {
            is Timestamp -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(value.toDate())
            is String -> value
            else -> "--"
        }
    }

    private fun getHelpText(): String {
        return "🤖 **AttendMate AI Assistant — Help**\n\n" +
            "Here's what I can do:\n\n" +
            "📊 **\"Show my attendance\"** — View all subjects' attendance\n" +
            "📚 **\"Attendance in Math\"** — Specific subject stats\n" +
            "📅 **\"Attendance on 2026-03-10\"** — Records for a date\n" +
            "📅 **\"Attendance today\"** — Today's records\n" +
            "🗓️ **\"My schedule today\"** — Today's timetable\n" +
            "🗓️ **\"Schedule for Monday\"** — Specific day's timetable\n" +
            "📈 **\"Analysis\"** — Full attendance analysis\n" +
            "✏️ **\"Mark Present in Math on 2026-03-11 09:00-10:00\"** — Mark attendance\n\n" +
            "💡 **Tips:**\n" +
            "• Use exact subject names as they appear in your app\n" +
            "• Dates should be in YYYY-MM-DD format, or say \"today\"/\"yesterday\"\n" +
            "• Times should be in HH:mm format (e.g., 09:00-10:00)"
    }
}

/* ────────────────── Data Classes ────────────────── */

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

data class PendingMarkAttendance(
    val subjectId: String,
    val subjectName: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val status: String
)

sealed interface AiChatUiState {
    object Initial : AiChatUiState
    object Loading : AiChatUiState
    object Success : AiChatUiState
    data class Error(val message: String) : AiChatUiState
}
