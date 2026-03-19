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
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /* ────────────────── UI State ────────────────── */

    private val _uiState = MutableStateFlow<AiChatUiState>(AiChatUiState.Initial)
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    val messages = mutableStateListOf<ChatMessage>()

    // Pending state for confirmation flow
    private var pendingMarksList: List<PendingMarkAttendance>? = null
    private var pendingDeletesList: List<PendingDeleteAttendance>? = null

    // Conversation context for multi-turn memory
    private val conversationCtx = ConversationContext()

    // Smart suggestion chips based on context
    private val _currentSuggestions = MutableStateFlow(getTimeBasedSuggestions())
    val currentSuggestions: StateFlow<List<String>> = _currentSuggestions.asStateFlow()

    init {
        messages.add(
            ChatMessage(
                text = "👋 Hello! I'm your **AttendMate AI Assistant**.\n\n" +
                        "I understand natural language — just ask me anything!\n\n" +
                        "📊 **Attendance** — \"Show my attendance\", \"How's my Math?\"\n" +
                        "📈 **Predict** — \"Predict my attendance\", \"Where will I end up?\"\n" +
                        "📉 **Trends** — \"Show my trend\", \"Am I improving?\"\n" +
                        "🔍 **Patterns** — \"When do I miss most?\"\n" +
                        "🏆 **Weekly** — \"Weekly summary\", \"How was my week?\"\n" +
                        "💡 **Tips** — \"Study tips for Math\", \"How to improve?\"\n" +
                        "💪 **Motivation** — \"Motivate me\", \"I feel lazy\"\n" +
                        "🎯 **Goals** — \"Set goal 85% for Math\"\n" +
                        "❓ **Q&A** — \"Why is 75% important?\"\n" +
                        "✏️ **Actions** — \"Mark present\", \"Delete attendance\"\n" +
                        "🗓️ **Schedule** — \"My schedule today\"\n\n" +
                        "I also understand follow-ups like \"What about Math?\" 🧠",
                isUser = false
            )
        )
    }

    /* ────────────────── Time-Based Suggestions ────────────────── */

    private fun getTimeBasedSuggestions(): List<String> {
        val hour = LocalTime.now().hour
        return when {
            hour in 6..10  -> listOf("My schedule today", "Mark all present today", "Predict my attendance", "Weekly summary")
            hour in 11..14 -> listOf("Mark present now", "Show my attendance", "Study tips", "Weekly summary")
            hour in 15..18 -> listOf("Show my attendance", "Show my trend", "Predict my attendance", "Motivate me")
            else           -> listOf("Analysis", "Weekly summary", "Show my trend", "When do I miss most?")
        }
    }

    private fun updateSuggestionsAfterIntent(lastIntent: Intent) {
        _currentSuggestions.value = when (lastIntent) {
            Intent.TIMETABLE         -> listOf("Mark all present today", "Show my attendance", "Predict my attendance", "Mark present now")
            Intent.SUBJECT_SUMMARY   -> listOf("Study tips", "Show my trend", "Predict my attendance", "When do I miss most?")
            Intent.OVERALL_ANALYSIS  -> listOf("Predict my attendance", "Show my trend", "Weekly summary", "Motivate me")
            Intent.MARK_ATTENDANCE   -> listOf("Show my attendance", "Mark all present today", "Weekly summary", "Analysis")
            Intent.MARK_BULK_ATTENDANCE -> listOf("Show my attendance", "Analysis", "Weekly summary", "Show my trend")
            Intent.DELETE_ATTENDANCE -> listOf("Show my attendance", "Mark all present today", "Analysis", "My schedule today")
            Intent.PREDICTION        -> listOf("Show my trend", "Study tips", "Weekly summary", "Set goal 85%")
            Intent.STUDY_TIPS        -> listOf("Predict my attendance", "Analysis", "Motivate me", "Show my trend")
            Intent.WEEKLY_SUMMARY    -> listOf("Predict my attendance", "Show my trend", "When do I miss most?", "Study tips")
            Intent.GOAL_SETTING      -> listOf("Show my attendance", "Predict my attendance", "Study tips", "Motivate me")
            Intent.MOTIVATION        -> listOf("Show my attendance", "Study tips", "Mark all present today", "Set goal 85%")
            Intent.TREND_ANALYSIS    -> listOf("Predict my attendance", "When do I miss most?", "Weekly summary", "Study tips")
            Intent.PATTERN_ANALYSIS  -> listOf("Show my trend", "Predict my attendance", "Study tips", "Weekly summary")
            Intent.SMART_QA          -> listOf("Show my attendance", "Predict my attendance", "Study tips", "Analysis")
            else                     -> getTimeBasedSuggestions()
        }
    }

    /* ────────────────── NLP-Powered Intent Detection ────────────────── */

    // Legacy Intent enum kept for updateSuggestionsAfterIntent compatibility
    private enum class Intent {
        GREETING, HELP, SUBJECT_SUMMARY, ATTENDANCE_FOR_DATE, TIMETABLE,
        OVERALL_ANALYSIS, MARK_BULK_ATTENDANCE, MARK_ATTENDANCE,
        DELETE_ATTENDANCE, CONFIRM_YES, CONFIRM_NO, WHATIF, NEXT_CLASS,
        PREDICTION, STUDY_TIPS, WEEKLY_SUMMARY, GOAL_SETTING,
        MOTIVATION, TREND_ANALYSIS, PATTERN_ANALYSIS, SMART_QA, UNKNOWN
    }

    private fun nlpToLegacy(nlp: NlpEngine.NlpIntent): Intent = when (nlp) {
        NlpEngine.NlpIntent.GREETING -> Intent.GREETING
        NlpEngine.NlpIntent.HELP -> Intent.HELP
        NlpEngine.NlpIntent.SUBJECT_SUMMARY -> Intent.SUBJECT_SUMMARY
        NlpEngine.NlpIntent.ATTENDANCE_FOR_DATE -> Intent.ATTENDANCE_FOR_DATE
        NlpEngine.NlpIntent.TIMETABLE -> Intent.TIMETABLE
        NlpEngine.NlpIntent.NEXT_CLASS -> Intent.NEXT_CLASS
        NlpEngine.NlpIntent.OVERALL_ANALYSIS -> Intent.OVERALL_ANALYSIS
        NlpEngine.NlpIntent.MARK_ATTENDANCE -> Intent.MARK_ATTENDANCE
        NlpEngine.NlpIntent.MARK_BULK_ATTENDANCE -> Intent.MARK_BULK_ATTENDANCE
        NlpEngine.NlpIntent.DELETE_ATTENDANCE -> Intent.DELETE_ATTENDANCE
        NlpEngine.NlpIntent.CONFIRM_YES -> Intent.CONFIRM_YES
        NlpEngine.NlpIntent.CONFIRM_NO -> Intent.CONFIRM_NO
        NlpEngine.NlpIntent.WHATIF -> Intent.WHATIF
        NlpEngine.NlpIntent.PREDICTION -> Intent.PREDICTION
        NlpEngine.NlpIntent.STUDY_TIPS -> Intent.STUDY_TIPS
        NlpEngine.NlpIntent.WEEKLY_SUMMARY -> Intent.WEEKLY_SUMMARY
        NlpEngine.NlpIntent.GOAL_SETTING -> Intent.GOAL_SETTING
        NlpEngine.NlpIntent.MOTIVATION -> Intent.MOTIVATION
        NlpEngine.NlpIntent.TREND_ANALYSIS -> Intent.TREND_ANALYSIS
        NlpEngine.NlpIntent.PATTERN_ANALYSIS -> Intent.PATTERN_ANALYSIS
        NlpEngine.NlpIntent.SMART_QA -> Intent.SMART_QA
        NlpEngine.NlpIntent.UNKNOWN -> Intent.UNKNOWN
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

                // Fetch subject names for NLP entity extraction
                val subjects = fetchSubjects(userId)
                val subjectNames = subjects.values.toList()

                // Use NLP Engine for intent classification
                val hasPending = pendingMarksList != null || pendingDeletesList != null
                val nlpResult = NlpEngine.analyse(
                    rawInput = userMessage,
                    hasPendingAction = hasPending,
                    lastIntent = conversationCtx.lastIntent,
                    subjectNames = subjectNames
                )

                // If pending and user typed something unrelated, clear pending
                if (hasPending && nlpResult.intent != NlpEngine.NlpIntent.CONFIRM_YES
                    && nlpResult.intent != NlpEngine.NlpIntent.CONFIRM_NO) {
                    pendingMarksList = null
                    pendingDeletesList = null
                }

                // Record conversation turn
                conversationCtx.record(ConversationContext.Turn(
                    userText = userMessage,
                    intent = nlpResult.intent,
                    subjectHint = nlpResult.entities.subjectHint,
                    dateHint = nlpResult.entities.dateHint,
                    sentiment = nlpResult.sentiment
                ))

                val intent = nlpToLegacy(nlpResult.intent)
                updateSuggestionsAfterIntent(intent)

                // Handle follow-up context resolution
                val effectiveInput = if (conversationCtx.isLikelyFollowUp(userMessage)
                    && nlpResult.confidence < 0.4f && conversationCtx.lastIntent != null) {
                    // Re-route to last intent with resolved entities
                    val resolved = conversationCtx.resolveFollowUp(nlpResult.entities)
                    if (resolved.subjectHint != null) {
                        "attendance in ${resolved.subjectHint}"  // synthetic input for follow-up
                    } else userMessage
                } else userMessage

                when (intent) {
                    Intent.GREETING  -> handleGreeting(nlpResult.sentiment)
                    Intent.HELP      -> reply(getHelpText())
                    Intent.SUBJECT_SUMMARY      -> handleSubjectSummary(userId, effectiveInput)
                    Intent.ATTENDANCE_FOR_DATE  -> handleAttendanceForDate(userId, effectiveInput)
                    Intent.TIMETABLE            -> handleTimetable(userId, effectiveInput)
                    Intent.OVERALL_ANALYSIS     -> handleOverallAnalysis(userId)
                    Intent.MARK_BULK_ATTENDANCE -> handleBulkMarkAttendance(userId, userMessage)
                    Intent.MARK_ATTENDANCE      -> handleMarkAttendance(userId, userMessage)
                    Intent.DELETE_ATTENDANCE    -> handleDeleteAttendance(userId, userMessage)
                    Intent.CONFIRM_YES          -> handleConfirmYes(userId)
                    Intent.CONFIRM_NO           -> handleConfirmNo()
                    Intent.WHATIF               -> handleWhatIf(userId, effectiveInput)
                    Intent.NEXT_CLASS           -> handleNextClass(userId)
                    // ── NEW ADVANCED INTENTS ──
                    Intent.PREDICTION           -> handlePrediction(userId)
                    Intent.STUDY_TIPS           -> handleStudyTips(userId, effectiveInput)
                    Intent.WEEKLY_SUMMARY       -> handleWeeklySummary(userId)
                    Intent.GOAL_SETTING         -> handleGoalSetting(userId, userMessage, nlpResult.entities)
                    Intent.MOTIVATION           -> handleMotivation(userId, nlpResult.sentiment)
                    Intent.TREND_ANALYSIS       -> handleTrend(userId)
                    Intent.PATTERN_ANALYSIS     -> handlePattern(userId, effectiveInput)
                    Intent.SMART_QA             -> handleSmartQA(userMessage)
                    Intent.UNKNOWN              -> handleUnknown(userMessage)
                }
            } catch (e: Exception) {
                Log.e("AiChat", "Error processing message", e)
                reply("❌ Something went wrong: ${e.message}")
            }
        }
    }

    fun confirmPendingAction(userId: String) {
        viewModelScope.launch {
            _uiState.value = AiChatUiState.Loading
            try {
                handleConfirmYes(userId)
            } catch (e: Exception) {
                reply("❌ Failed to complete action: ${e.message}")
            }
        }
    }

    fun cancelPendingAction() {
        handleConfirmNo()
    }

    private fun reply(
        text: String,
        type: MessageType = MessageType.TEXT,
        attendanceData: List<SubjectAttendanceData>? = null,
        timetableData: List<TimetableSlot>? = null,
        analysisData: AttendanceAnalysisData? = null,
        pendingMarks: List<PendingMarkAttendance>? = null,
        pendingDeletes: List<PendingDeleteAttendance>? = null,
        predictionData: List<PredictionEngine.SubjectPrediction>? = null,
        studyTipsData: StudyTipsCardData? = null,
        weeklySummaryData: PredictionEngine.WeeklySummaryData? = null,
        goalData: GoalCardData? = null,
        trendData: List<PredictionEngine.SubjectTrend>? = null
    ) {
        messages.add(
            ChatMessage(
                text = text,
                isUser = false,
                messageType = type,
                attendanceData = attendanceData,
                timetableData = timetableData,
                analysisData = analysisData,
                pendingMarks = pendingMarks,
                pendingDeletes = pendingDeletes,
                predictionData = predictionData,
                studyTipsData = studyTipsData,
                weeklySummaryData = weeklySummaryData,
                goalData = goalData,
                trendData = trendData
            )
        )
        _uiState.value = AiChatUiState.Success
    }

    /* ────────────────── Handlers ────────────────── */

    private suspend fun handleSubjectSummary(userId: String, input: String) {
        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) {
            reply("📭 You haven't added any subjects yet. Go to the app to add subjects first!")
            return
        }

        val lower = input.lowercase()
        val matchedSubject = subjects.entries.find { (_, name) -> lower.contains(name.lowercase()) }

        if (matchedSubject != null) {
            val doc = db.collection("users").document(userId)
                .collection("subjects").document(matchedSubject.key).get().await()
            val name = doc.getString("name") ?: "Unknown"
            val total = doc.getLong("totalClasses")?.toInt() ?: 0
            val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
            val pct = if (total > 0) (attended * 100f / total).toInt() else 0
            val safeToMiss = if (pct >= 75 && total > 0) {
                Math.floor((attended - 0.75 * total) / 0.75).toInt().coerceAtLeast(0)
            } else 0
            val needMore = if (pct < 75 && total > 0) {
                Math.ceil((0.75 * total - attended) / 0.25).toInt()
            } else 0

            // Calculate streak
            val attSnap = db.collection("users").document(userId)
                .collection("subjects").document(matchedSubject.key)
                .collection("attendance").get().await()
            val sortedAtt = attSnap.documents.sortedByDescending { it.getDate("date") ?: Date() }
            var currentStreak = 0
            var isAbsentStreak = false
            for (i in sortedAtt.indices) {
                val stat = sortedAtt[i].getString("status") ?: "Absent"
                if (i == 0) isAbsentStreak = (stat != "Present")
                
                if (isAbsentStreak && stat != "Present") currentStreak++
                else if (!isAbsentStreak && stat == "Present") currentStreak++
                else break
            }
            
            val streakMsg = if (currentStreak >= 3) {
                if (isAbsentStreak) "\n⚠️ You've missed the last **$currentStreak** classes."
                else "\n🔥 On a **$currentStreak** class streak! Keep it going!"
            } else ""

            val data = listOf(SubjectAttendanceData(name, attended, total, pct))
            val summaryText = if (pct < 75 && total > 0)
                "💡 You need **$needMore** more classes to reach 75%.$streakMsg"
            else if (safeToMiss > 0)
                "✅ You can safely miss **$safeToMiss** more class(es).$streakMsg"
            else "✅ Keep it up!$streakMsg"

            reply(
                text = summaryText,
                type = MessageType.ATTENDANCE_CARD,
                attendanceData = data
            )
        } else {
            val dataList = mutableListOf<SubjectAttendanceData>()
            for ((id, name) in subjects) {
                val doc = db.collection("users").document(userId)
                    .collection("subjects").document(id).get().await()
                val total = doc.getLong("totalClasses")?.toInt() ?: 0
                val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
                val pct = if (total > 0) (attended * 100f / total).toInt() else 0
                dataList.add(SubjectAttendanceData(name, attended, total, pct))
            }
            reply(
                text = "📊 Here's your full attendance summary:",
                type = MessageType.ATTENDANCE_CARD,
                attendanceData = dataList
            )
        }
    }

    private suspend fun handleAttendanceForDate(userId: String, input: String) {
        val dateStr = extractDate(input)
        if (dateStr == null) {
            reply("📅 Please specify a date. Example: \"Attendance on 2026-03-10\" or \"Attendance today\"")
            return
        }

        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) { reply("📭 No subjects found."); return }

        val records = mutableListOf<String>()
        var presentCount = 0
        var absentCount = 0

        for ((subjectId, subjectName) in subjects) {
            val attSnap = db.collection("users").document(userId)
                .collection("subjects").document(subjectId)
                .collection("attendance").get().await()

            for (doc in attSnap.documents) {
                val recordDate = when (val raw = doc.get("date")) {
                    is String    -> raw
                    is Timestamp -> raw.toDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                    else         -> continue
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
        val now = LocalTime.now()
        val isToday = day == LocalDate.now().dayOfWeek.name

        val timetableSnap = db.collection("users").document(userId)
            .collection("timetable").whereEqualTo("day", day).get().await()

        if (timetableSnap.isEmpty) {
            reply("🗓️ No lectures scheduled for **$day**.")
            return
        }

        val lectures = timetableSnap.documents.sortedBy { it.getString("startTime") ?: "" }
        val slots = lectures.mapNotNull { doc ->
            val name  = doc.getString("subjectName") ?: return@mapNotNull null
            val start = doc.getString("startTime") ?: "--"
            val end   = doc.getString("endTime") ?: "--"
            val ongoing = if (isToday) {
                val st = runCatching { LocalTime.parse(start.padStart(5, '0')) }.getOrNull()
                val et = runCatching { LocalTime.parse(end.padStart(5, '0')) }.getOrNull()
                st != null && et != null && !now.isBefore(st) && !now.isAfter(et)
            } else false
            TimetableSlot(name, start, end, ongoing)
        }

        reply(
            text = "🗓️ **Schedule for $day** — ${slots.size} lectures",
            type = MessageType.TIMETABLE_CARD,
            timetableData = slots
        )
    }

    private suspend fun handleOverallAnalysis(userId: String) {
        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) { reply("📭 No subjects found. Add subjects first!"); return }

        var totalAll = 0; var attendedAll = 0
        var bestName = ""; var bestPct = -1
        var worstName = ""; var worstPct = 101
        val atRisk = mutableListOf<SubjectAttendanceData>()
        val allSubjects = mutableListOf<SubjectAttendanceData>()

        for ((id, name) in subjects) {
            val doc = db.collection("users").document(userId)
                .collection("subjects").document(id).get().await()
            val total    = doc.getLong("totalClasses")?.toInt() ?: 0
            val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
            val pct      = if (total > 0) (attended * 100f / total).toInt() else 0
            totalAll += total; attendedAll += attended
            if (pct > bestPct)  { bestPct = pct;  bestName = name }
            if (pct < worstPct && total > 0) { worstPct = pct; worstName = name }
            allSubjects.add(SubjectAttendanceData(name, attended, total, pct))
            if (pct < 75 && total > 0) atRisk.add(SubjectAttendanceData(name, attended, total, pct))
        }

        val overallPct = if (totalAll > 0) (attendedAll * 100f / totalAll).toInt() else 0
        val summary = if (atRisk.isEmpty()) "🎉 All subjects are above 75%! Keep it up!"
                      else "⚠️ ${atRisk.size} subject(s) are at risk."

        val analysisData = AttendanceAnalysisData(
            overallPct    = overallPct,
            totalAttended = attendedAll,
            totalClasses  = totalAll,
            bestSubject   = bestName,
            bestPct       = bestPct,
            worstSubject  = worstName,
            worstPct      = if (worstPct == 101) 0 else worstPct,
            subjects      = allSubjects,
            atRisk        = atRisk
        )

        reply(
            text    = summary,
            type    = MessageType.ANALYSIS_CARD,
            analysisData = analysisData
        )
    }

    private suspend fun handleWhatIf(userId: String, input: String) {
        val lower = input.lowercase()
        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) { reply("📭 No subjects found."); return }

        val matchedSubject = findSubjectFuzzy(lower, subjects)

        if (matchedSubject == null) {
            // Global what-if
            val sb = StringBuilder("🔮 **What-If Summary**\n\n")
            var hasRisk = false
            for ((id, name) in subjects) {
                val doc = db.collection("users").document(userId)
                    .collection("subjects").document(id).get().await()
                val total    = doc.getLong("totalClasses")?.toInt() ?: 0
                val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
                val pct      = if (total > 0) (attended * 100f / total).toInt() else 0
                if (pct >= 75 && total > 0) {
                    val canMiss = Math.floor((attended - 0.75 * total) / 0.75).toInt().coerceAtLeast(0)
                    sb.append("✅ **$name** ($pct%) — can miss **$canMiss** class(es)\n")
                } else if (total > 0) {
                    val need = Math.ceil((0.75 * total - attended) / 0.25).toInt()
                    sb.append("🚨 **$name** ($pct%) — need **$need** more class(es)\n")
                    hasRisk = true
                }
            }
            if (!hasRisk) sb.append("\n🎉 You're safe across all subjects!")
            reply(sb.toString().trimEnd())
        } else {
            val doc = db.collection("users").document(userId)
                .collection("subjects").document(matchedSubject.key).get().await()
            val total    = doc.getLong("totalClasses")?.toInt() ?: 0
            val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
            val name     = matchedSubject.value
            val pct      = if (total > 0) (attended * 100f / total).toInt() else 0

            if (total == 0) { reply("📭 No classes recorded for **$name** yet."); return }

            if (pct >= 75) {
                val canMiss = Math.floor((attended - 0.75 * total) / 0.75).toInt().coerceAtLeast(0)
                reply("🔮 **What-If: $name**\n\n" +
                      "Current: **$pct%** ($attended/$total)\n" +
                      "✅ You can safely miss **$canMiss** more class(es) and stay above 75%.")
            } else {
                val need = Math.ceil((0.75 * total - attended) / 0.25).toInt()
                reply("🔮 **What-If: $name**\n\n" +
                      "Current: **$pct%** ($attended/$total)\n" +
                      "🚨 You need to attend **$need** consecutive classes to reach 75%.")
            }
        }
    }

    private suspend fun handleMarkAttendance(userId: String, input: String) {
        val lower = input.lowercase()

        val status = when {
            lower.contains("present") -> "Present"
            lower.contains("absent")  -> "Absent"
            else -> {
                reply("❓ Please specify status: **Present** or **Absent**.\nExample: \"Mark Present in Math\"")
                return
            }
        }

        val subjects = fetchSubjects(userId)
        var matchedSubject: Map.Entry<String, String>? = findSubjectFuzzy(lower, subjects)
        var dateStr = extractDate(input)

        var startTime = ""
        var endTime   = ""

        // Extract explicit times if given
        val timeRegex = Regex("(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|a|p)?)\\s*(?:-|to|till|until)\\s*(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|a|p)?)", RegexOption.IGNORE_CASE)
        val timeMatch = timeRegex.find(lower)
        if (timeMatch != null) {
            startTime = normalizeTime(timeMatch.groupValues[1])
            endTime   = normalizeTime(timeMatch.groupValues[2])
        }

        // ── Auto-fill from timetable ──────────────────────────────────────────
        // Determine the day to look up: if date given use that day, else use today
        val targetDate  = runCatching { LocalDate.parse(dateStr ?: LocalDate.now().toString()) }.getOrDefault(LocalDate.now())
        val dayName     = targetDate.dayOfWeek.name
        val now         = LocalTime.now()

        val timetableSnap = db.collection("users").document(userId)
            .collection("timetable").whereEqualTo("day", dayName).get().await()

        // Try auto-fill subject from ongoing/first class
        if (matchedSubject == null) {
            // First try ongoing class
            var used = false
            for (doc in timetableSnap.documents) {
                val st = runCatching { LocalTime.parse(readTimeField(doc, "startTime").takeIf { it != "--" }?.padStart(5, '0') ?: "") }.getOrNull()
                val et = runCatching { LocalTime.parse(readTimeField(doc, "endTime").takeIf { it != "--" }?.padStart(5, '0') ?: "") }.getOrNull()
                if (st != null && et != null && !now.isBefore(st) && !now.isAfter(et)) {
                    val sId = doc.getString("subjectId") ?: continue
                    val sName = doc.getString("subjectName") ?: continue
                    matchedSubject = mapOf(sId to sName).entries.first()
                    if (dateStr == null) dateStr = targetDate.toString()
                    if (startTime.isEmpty()) startTime = readTimeField(doc, "startTime").takeIf { it != "--" } ?: ""
                    if (endTime.isEmpty())   endTime   = readTimeField(doc, "endTime").takeIf { it != "--" } ?: ""
                    used = true; break
                }
            }

            // If no ongoing class, take the most recent past class
            if (!used && timetableSnap.documents.isNotEmpty()) {
                val sorted = timetableSnap.documents.sortedByDescending { readTimeField(it, "startTime") }
                for (doc in sorted) {
                    val et = runCatching { LocalTime.parse(readTimeField(doc, "endTime").takeIf { it != "--" }?.padStart(5, '0') ?: "") }.getOrNull()
                    if (et != null && now.isAfter(et)) {
                        val sId = doc.getString("subjectId") ?: continue
                        val sName = doc.getString("subjectName") ?: continue
                        matchedSubject = mapOf(sId to sName).entries.first()
                        if (dateStr == null) dateStr = targetDate.toString()
                        if (startTime.isEmpty()) startTime = readTimeField(doc, "startTime").takeIf { it != "--" } ?: ""
                        if (endTime.isEmpty())   endTime   = readTimeField(doc, "endTime").takeIf { it != "--" } ?: ""
                        break
                    }
                }
            }
        } else {
            // We have the subject — try to fill missing date/time from its timetable slot
            if (dateStr == null) dateStr = targetDate.toString()
            if (startTime.isEmpty() || endTime.isEmpty()) {
                // Find all slots for this subject on this day
                val subjectSlots = timetableSnap.documents
                    .filter { it.getString("subjectId") == matchedSubject?.key }
                    .sortedBy { it.getString("startTime") ?: "" }

                // Pick the closest slot to current time
                val bestSlot = if (subjectSlots.size <= 1) {
                    subjectSlots.firstOrNull()
                } else {
                    // Prefer: 1) ongoing slot, 2) most recently ended, 3) next upcoming
                    subjectSlots.firstOrNull { doc ->
                        val st = runCatching { LocalTime.parse(readTimeField(doc, "startTime").takeIf { it != "--" }?.padStart(5, '0') ?: "") }.getOrNull()
                        val et = runCatching { LocalTime.parse(readTimeField(doc, "endTime").takeIf { it != "--" }?.padStart(5, '0') ?: "") }.getOrNull()
                        st != null && et != null && !now.isBefore(st) && !now.isAfter(et)
                    } ?: subjectSlots.sortedByDescending { readTimeField(it, "endTime") }
                        .firstOrNull { doc ->
                            val et = runCatching { LocalTime.parse(readTimeField(doc, "endTime").takeIf { it != "--" }?.padStart(5, '0') ?: "") }.getOrNull()
                            et != null && now.isAfter(et)
                        }
                    ?: subjectSlots.firstOrNull()  // fallback to first upcoming
                }

                if (bestSlot != null) {
                    if (startTime.isEmpty()) startTime = readTimeField(bestSlot, "startTime").takeIf { it != "--" } ?: ""
                    if (endTime.isEmpty())   endTime   = readTimeField(bestSlot, "endTime").takeIf { it != "--" } ?: ""
                }
            }
        }

        if (matchedSubject == null) {
            val subjectList = subjects.values.joinToString(", ")
            reply("📚 Couldn't detect which subject.\nYour subjects are: **$subjectList**\n\nExample: \"Mark $status in [Subject]\"")
            return
        }
        if (dateStr == null) {
            reply("📅 Please include a date.\nExample: \"Mark $status in ${matchedSubject.value} on 2026-03-11\"")
            return
        }
        if (startTime.isEmpty() || endTime.isEmpty()) {
            reply("⏰ Couldn't find this class in the timetable for **$dayName**.\nPlease include time: \"Mark $status in ${matchedSubject.value} on $dateStr **09:00 to 10:00**\"")
            return
        }

        val pending = listOf(
            PendingMarkAttendance(
                subjectId   = matchedSubject.key,
                subjectName = matchedSubject.value,
                date        = dateStr,
                startTime   = startTime,
                endTime     = endTime,
                status      = status
            )
        )
        pendingMarksList = pending

        reply(
            text = "✏️ Please confirm this attendance mark:",
            type = MessageType.CONFIRM_MARK,
            pendingMarks = pending
        )
    }

    private suspend fun handleBulkMarkAttendance(userId: String, input: String) {
        val lower = input.lowercase()

        val baseStatus = when {
            lower.contains("present") -> "Present"
            lower.contains("absent")  -> "Absent"
            else -> {
                reply("❓ Please specify status: **Present** or **Absent**.\nExample: \"Mark all present today\"")
                return
            }
        }

        val dateStr = extractDate(input) ?: LocalDate.now().toString()
        val subjects = fetchSubjects(userId)

        val exclusions = mutableListOf<String>()
        val excludeRegex = Regex("(?:except|but not|exclude|not)\\s+([a-zA-Z\\s,]+)")
        excludeRegex.find(lower)?.let { match ->
            val excludeStr = match.groupValues[1].trim()
            val individualExclusions = excludeStr.split(Regex("\\s+and\\s+|,")).map { it.trim() }.filter { it.isNotBlank() }
            for (ex in individualExclusions) {
                findSubjectFuzzy(ex, subjects)?.let { exclusions.add(it.key) }
            }
        }

        var tillLocalTime: LocalTime? = null
        val timeRegex = Regex("till\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|a|p)?)", RegexOption.IGNORE_CASE)
        timeRegex.find(lower)?.let { match ->
            tillLocalTime = runCatching { LocalTime.parse(normalizeTime(match.groupValues[1]).padStart(5, '0')) }.getOrNull()
        }

        val targetDate    = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: LocalDate.now()
        val dayOfWeekName = targetDate.dayOfWeek.name

        val timetableSnap = db.collection("users").document(userId)
            .collection("timetable").whereEqualTo("day", dayOfWeekName).get().await()

        if (timetableSnap.isEmpty) {
            reply("🗓️ You don't have any classes in your timetable for **$dayOfWeekName**.")
            return
        }

        val pendingList = mutableListOf<PendingMarkAttendance>()
        val sortedDocs  = timetableSnap.documents.sortedBy { it.getString("startTime") ?: "" }

        for (doc in sortedDocs) {
            val sId   = doc.getString("subjectId") ?: continue
            val sName = doc.getString("subjectName") ?: continue
            val st    = readTimeField(doc, "startTime").takeIf { it != "--" } ?: ""
            val et    = readTimeField(doc, "endTime").takeIf { it != "--" } ?: ""

            var finalStatus = baseStatus

            if (exclusions.contains(sId)) {
                finalStatus = if (baseStatus == "Present") "Absent" else "Present"
            } else if (tillLocalTime != null) {
                val stParsed = runCatching { LocalTime.parse(st.padStart(5, '0')) }.getOrNull()
                if (stParsed != null && !stParsed.isBefore(tillLocalTime)) {
                    finalStatus = if (baseStatus == "Present") "Absent" else "Present"
                }
            }

            pendingList.add(PendingMarkAttendance(sId, sName, dateStr, st, et, finalStatus))
        }

        if (pendingList.isEmpty()) {
            reply("All classes were excluded or no valid classes found.")
            return
        }

        pendingMarksList = pendingList

        reply(
            text = "✏️ Please confirm bulk mark for **$dateStr**:",
            type = MessageType.CONFIRM_MARK,
            pendingMarks = pendingList
        )
    }

    private suspend fun handleDeleteAttendance(userId: String, input: String) {
        val dateStr   = extractDate(input) ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr) ?: Date()
        val cal = Calendar.getInstance().apply {
            time = parsedDate
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val startOfDay = cal.time
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        val endOfDay = cal.time

        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) { reply("📭 You haven't added any subjects yet."); return }

        val lower = input.lowercase()
        val targetSubjects = mutableMapOf<String, String>()
        if (lower.contains("all attendance") || lower.contains("everything")) {
            targetSubjects.putAll(subjects)
        } else {
            findSubjectFuzzy(input, subjects)?.let { targetSubjects[it.key] = it.value }
                ?: targetSubjects.putAll(subjects)
        }

        val pendingList = mutableListOf<PendingDeleteAttendance>()
        for ((sId, sName) in targetSubjects) {
            val recordsSnap = db.collection("users").document(userId)
                .collection("subjects").document(sId)
                .collection("attendance")
                .whereGreaterThanOrEqualTo("date", startOfDay)
                .whereLessThanOrEqualTo("date", endOfDay)
                .get().await()

            for (doc in recordsSnap.documents) {
                val status = doc.getString("status") ?: "Unknown"
                val st = readTimeField(doc, "startTime")
                val et = readTimeField(doc, "endTime")
                pendingList.add(PendingDeleteAttendance(sId, sName, doc.id, st, et, status))
            }
        }

        if (pendingList.isEmpty()) {
            reply("Hmm, I couldn't find any attendance records for **$dateStr**."); return
        }

        pendingDeletesList = pendingList

        reply(
            text      = "🗑️ Confirm deletion for **$dateStr**:",
            type      = MessageType.CONFIRM_DELETE,
            pendingDeletes = pendingList
        )
    }

    private suspend fun handleConfirmYes(userId: String) {
        val marks   = pendingMarksList
        val deletes = pendingDeletesList

        if (!marks.isNullOrEmpty()) {
            handleConfirmMarkYes(userId, marks)
        } else if (!deletes.isNullOrEmpty()) {
            handleConfirmDeleteYes(userId, deletes)
        } else {
            reply("🤔 Nothing to confirm.")
        }
    }

    private suspend fun handleConfirmMarkYes(userId: String, marks: List<PendingMarkAttendance>) {
        try {
            var addedCount = 0; var skippedCount = 0
            val subjectUpdates = mutableMapOf<String, Pair<Int, Int>>()
            val resultSb = StringBuilder("✅ **Attendance Marked!**\n\n")

            for (mark in marks) {
                val lectureId   = "${mark.date}_${mark.startTime.replace(":", "")}_${mark.endTime.replace(":", "")}"
                val subjectRef   = db.collection("users").document(userId).collection("subjects").document(mark.subjectId)
                val attendanceRef = subjectRef.collection("attendance").document(lectureId)

                val existing = attendanceRef.get().await()
                if (existing.exists()) {
                    resultSb.append("⏭️ ${mark.subjectName} (${mark.startTime}) — Already marked\n")
                    skippedCount++; continue
                }

                val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(mark.date)
                val cal = Calendar.getInstance(); cal.time = parsedDate ?: Date()
                val stParts = mark.startTime.split(":")
                cal.set(Calendar.HOUR_OF_DAY, stParts.getOrNull(0)?.toIntOrNull() ?: 0)
                cal.set(Calendar.MINUTE, stParts.getOrNull(1)?.toIntOrNull() ?: 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val startDateTime = cal.time

                val etParts = mark.endTime.split(":")
                cal.set(Calendar.HOUR_OF_DAY, etParts.getOrNull(0)?.toIntOrNull() ?: 0)
                cal.set(Calendar.MINUTE, etParts.getOrNull(1)?.toIntOrNull() ?: 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val endDateTime = cal.time

                val dayName = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "MONDAY"; Calendar.TUESDAY -> "TUESDAY"
                    Calendar.WEDNESDAY -> "WEDNESDAY"; Calendar.THURSDAY -> "THURSDAY"
                    Calendar.FRIDAY -> "FRIDAY"; Calendar.SATURDAY -> "SATURDAY"
                    Calendar.SUNDAY -> "SUNDAY"; else -> null
                }
                val startHour = stParts.getOrNull(0)?.toIntOrNull() ?: 0
                val endHour   = etParts.getOrNull(0)?.toIntOrNull() ?: 0
                val slotIndex = startHour - 9
                val durationHours = endHour - startHour
                val lectureKey = if (dayName != null && slotIndex >= 0 && durationHours > 0)
                    "${dayName}_${slotIndex}_${durationHours}" else null

                val attendanceData = mutableMapOf<String, Any>(
                    "date" to (parsedDate ?: Date()), "status" to mark.status,
                    "startTime" to startDateTime, "endTime" to endDateTime, "createdAt" to Date()
                )
                if (lectureKey != null) attendanceData["lectureKey"] = lectureKey
                attendanceRef.set(attendanceData).await()

                val cur = subjectUpdates.getOrDefault(mark.subjectId, Pair(0, 0))
                subjectUpdates[mark.subjectId] = Pair(cur.first + 1, cur.second + if (mark.status == "Present") 1 else 0)

                val icon = if (mark.status == "Present") "✅" else "❌"
                resultSb.append("$icon ${mark.subjectName} (${mark.startTime}) — ${mark.status}\n")
                addedCount++
            }

            for ((subId, stats) in subjectUpdates) {
                if (stats.first > 0) {
                    val ref = db.collection("users").document(userId).collection("subjects").document(subId)
                    val updateMap = mutableMapOf<String, Any>("totalClasses" to FieldValue.increment(stats.first.toLong()))
                    if (stats.second > 0) updateMap["attendedClasses"] = FieldValue.increment(stats.second.toLong())
                    ref.update(updateMap).await()
                }
            }

            if (marks.size > 1) resultSb.append("\nAdded: **$addedCount** | Skipped (duplicate): **$skippedCount**")
            reply(resultSb.toString())
        } catch (e: Exception) {
            Log.e("AiChat", "Error marking attendance", e)
            reply("❌ Failed to mark attendance: ${e.message}")
        } finally {
            pendingMarksList = null
        }
    }

    private suspend fun handleConfirmDeleteYes(userId: String, deletes: List<PendingDeleteAttendance>) {
        try {
            var deletedCount = 0
            val subjectUpdates = mutableMapOf<String, Pair<Int, Int>>()
            val resultSb = StringBuilder("🗑️ **Attendance Deleted!**\n\n")

            for (del in deletes) {
                db.collection("users").document(userId)
                    .collection("subjects").document(del.subjectId)
                    .collection("attendance").document(del.attendanceId).delete().await()

                val cur = subjectUpdates.getOrDefault(del.subjectId, Pair(0, 0))
                subjectUpdates[del.subjectId] = Pair(cur.first + 1, cur.second + if (del.status.equals("Present", true)) 1 else 0)
                resultSb.append("➖ ${del.subjectName} (${del.startTime}) — Deleted\n")
                deletedCount++
            }

            for ((subId, stats) in subjectUpdates) {
                if (stats.first > 0) {
                    val ref = db.collection("users").document(userId).collection("subjects").document(subId)
                    val updateMap = mutableMapOf<String, Any>("totalClasses" to FieldValue.increment(-stats.first.toLong()))
                    if (stats.second > 0) updateMap["attendedClasses"] = FieldValue.increment(-stats.second.toLong())
                    ref.update(updateMap).await()
                }
            }

            if (deletes.size > 1) resultSb.append("\nTotal Deleted: **$deletedCount**")
            reply(resultSb.toString())
        } catch (e: Exception) {
            Log.e("AiChat", "Error deleting attendance", e)
            reply("❌ Failed to delete attendance: ${e.message}")
        } finally {
            pendingDeletesList = null
        }
    }

    private suspend fun handleNextClass(userId: String) {
        val today = LocalDate.now().dayOfWeek.name
        val now = LocalTime.now()
        val timetableSnap = db.collection("users").document(userId)
            .collection("timetable").whereEqualTo("day", today).get().await()

        if (timetableSnap.isEmpty) {
            reply("🗓️ You don't have any classes today!")
            return
        }

        var nextSlot: TimetableSlot? = null
        var nextTime: LocalTime? = null

        for (doc in timetableSnap.documents) {
            val name = doc.getString("subjectName") ?: continue
            val start = readTimeField(doc, "startTime").takeIf { it != "--" } ?: continue
            val end = readTimeField(doc, "endTime").takeIf { it != "--" } ?: continue
            val st = runCatching { LocalTime.parse(start.padStart(5, '0')) }.getOrNull()
            
            if (st != null && st.isAfter(now)) {
                if (nextTime == null || st.isBefore(nextTime)) {
                    nextTime = st
                    nextSlot = TimetableSlot(name, start, end, false)
                }
            }
        }

        if (nextSlot != null) {
            reply(
                text = "⏰ Your next class is **${nextSlot.subjectName}** at **${nextSlot.startTime}**.",
                type = MessageType.TIMETABLE_CARD,
                timetableData = listOf(nextSlot)
            )
        } else {
            reply("🎉 You have no more classes for today!")
        }
    }

    private fun handleConfirmNo() {
        when {
            pendingMarksList != null   -> { pendingMarksList = null;  reply("❌ Cancelled. Attendance was not marked.") }
            pendingDeletesList != null -> { pendingDeletesList = null; reply("❌ Cancelled. Attendance was not deleted.") }
            else                       -> reply("❌ Cancelled.")
        }
    }

    /* ════════════════════ NEW ADVANCED HANDLERS ════════════════════ */

    private fun handleGreeting(sentiment: NlpEngine.Sentiment) {
        val hour = LocalTime.now().hour
        val timeGreet = when {
            hour in 5..11  -> "Good morning"
            hour in 12..16 -> "Good afternoon"
            hour in 17..20 -> "Good evening"
            else           -> "Hey there"
        }
        val moodReply = when (sentiment) {
            NlpEngine.Sentiment.FRUSTRATED -> " I sense you're having a tough time. I'm here to help! 💙"
            NlpEngine.Sentiment.NEGATIVE   -> " Let's make things better together! 🌟"
            else -> " How can I help you with your attendance today? 😊"
        }
        reply("👋 $timeGreet!$moodReply")
    }

    private suspend fun handlePrediction(userId: String) {
        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) { reply("📭 No subjects found."); return }

        val predictions = mutableListOf<PredictionEngine.SubjectPrediction>()
        for ((id, name) in subjects) {
            val doc = db.collection("users").document(userId).collection("subjects").document(id).get().await()
            val total = doc.getLong("totalClasses")?.toInt() ?: 0
            val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
            val pct = if (total > 0) (attended * 100f / total).toInt() else 0
            val records = fetchAttendanceRecords(userId, id)
            predictions.add(PredictionEngine.predictAttendance(records, pct, total, name))
        }

        val atRisk = predictions.count { it.predictedPct < 75 }
        val summary = if (atRisk == 0) "🎉 All subjects predicted to stay safe!"
                      else "⚠️ **$atRisk subject(s)** predicted to drop below 75%."

        reply(text = summary, type = MessageType.PREDICTION_CARD, predictionData = predictions)
    }

    private suspend fun handleStudyTips(userId: String, input: String) {
        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) { reply("📭 No subjects found."); return }
        val matched = findSubjectFuzzy(input, subjects)
        val targetId = matched?.key ?: subjects.keys.first()
        val targetName = matched?.value ?: subjects.values.first()

        val doc = db.collection("users").document(userId).collection("subjects").document(targetId).get().await()
        val total = doc.getLong("totalClasses")?.toInt() ?: 0
        val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
        val pct = if (total > 0) (attended * 100f / total).toInt() else 0

        val records = fetchAttendanceRecords(userId, targetId)
        val trend = PredictionEngine.analyzeTrend(records, pct, targetName).trend
        val patterns = PredictionEngine.analyzePatterns(records, targetName)

        val tips = PredictionEngine.generateStudyTips(targetName, pct, trend, patterns.worstDay?.dayOfWeek, patterns.currentStreak, patterns.isPositiveStreak)
        val urgency = when { pct < 50 -> "critical"; pct < 65 -> "warning"; pct < 75 -> "caution"; pct < 85 -> "safe"; else -> "excellent" }

        reply(
            text = "💡 Smart tips for **$targetName** ($pct%):",
            type = MessageType.STUDY_TIPS_CARD,
            studyTipsData = StudyTipsCardData(targetName, pct, urgency, tips)
        )
    }

    private suspend fun handleWeeklySummary(userId: String) {
        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) { reply("📭 No subjects found."); return }

        val allRecords = mutableMapOf<String, List<PredictionEngine.AttendanceRecord>>()
        for ((id, name) in subjects) {
            allRecords[name] = fetchAttendanceRecords(userId, id)
        }
        val summary = PredictionEngine.weeklySummary(allRecords)
        val txt = if (summary.totalPresent + summary.totalAbsent == 0) "📭 No attendance data for this week yet."
        else {
            val comp = if (summary.comparedToLastWeek > 0) "📈 **+${summary.comparedToLastWeek}%** vs last week!"
                       else if (summary.comparedToLastWeek < 0) "📉 **${summary.comparedToLastWeek}%** vs last week."
                       else "➡️ Same as last week."
            "🏆 **Weekly Summary** — $comp"
        }
        if (summary.totalPresent + summary.totalAbsent == 0) { reply(txt); return }
        reply(text = txt, type = MessageType.WEEKLY_SUMMARY_CARD, weeklySummaryData = summary)
    }

    private suspend fun handleGoalSetting(userId: String, input: String, entities: NlpEngine.ExtractedEntities) {
        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) { reply("📭 No subjects found."); return }

        val targetPct = entities.percentageHint ?: 75
        val matched = findSubjectFuzzy(input, subjects)
        val subjectId = matched?.key ?: subjects.keys.first()
        val subjectName = matched?.value ?: subjects.values.first()

        val doc = db.collection("users").document(userId).collection("subjects").document(subjectId).get().await()
        val total = doc.getLong("totalClasses")?.toInt() ?: 0
        val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
        val pct = if (total > 0) (attended * 100f / total).toInt() else 0
        val isAchieved = pct >= targetPct

        val classesNeeded = if (pct < targetPct && total > 0) {
            kotlin.math.ceil((targetPct / 100.0 * total - attended) / (1 - targetPct / 100.0)).toInt().coerceAtLeast(0)
        } else 0

        reply(
            text = if (isAchieved) "🎉 You've already reached **$targetPct%** in **$subjectName**!"
                   else "🎯 Goal set: **$targetPct%** in **$subjectName** — need **$classesNeeded** more classes.",
            type = MessageType.GOAL_CARD,
            goalData = GoalCardData(subjectName, targetPct, pct, classesNeeded, total, attended, isAchieved)
        )
    }

    private suspend fun handleMotivation(userId: String, sentiment: NlpEngine.Sentiment) {
        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) { reply("💪 Add some subjects first and let's start your journey!"); return }

        var totalAll = 0; var attendedAll = 0
        var bestName = ""; var bestPct = -1; var worstName = ""; var worstPct = 101; var atRiskCount = 0
        for ((id, name) in subjects) {
            val doc = db.collection("users").document(userId).collection("subjects").document(id).get().await()
            val total = doc.getLong("totalClasses")?.toInt() ?: 0
            val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
            val pct = if (total > 0) (attended * 100f / total).toInt() else 0
            totalAll += total; attendedAll += attended
            if (pct > bestPct) { bestPct = pct; bestName = name }
            if (pct < worstPct && total > 0) { worstPct = pct; worstName = name }
            if (pct < 75 && total > 0) atRiskCount++
        }
        val overallPct = if (totalAll > 0) (attendedAll * 100f / totalAll).toInt() else 0

        // Get streak from all records
        val allRecords = subjects.flatMap { (id, _) -> runCatching { fetchAttendanceRecords(userId, id) }.getOrDefault(emptyList()) }
        val sorted = allRecords.sortedByDescending { it.date }
        var streak = 0; var isPositive = true
        if (sorted.isNotEmpty()) {
            isPositive = sorted[0].isPresent
            for (r in sorted) { if (r.isPresent == isPositive) streak++ else break }
        }

        val msg = PredictionEngine.generateMotivation(overallPct, bestName, bestPct, worstName, worstPct.let { if (it == 101) 0 else it }, streak, isPositive, atRiskCount, sentiment)
        reply(msg)
    }

    private suspend fun handleTrend(userId: String) {
        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) { reply("📭 No subjects found."); return }

        val trends = mutableListOf<PredictionEngine.SubjectTrend>()
        for ((id, name) in subjects) {
            val doc = db.collection("users").document(userId).collection("subjects").document(id).get().await()
            val total = doc.getLong("totalClasses")?.toInt() ?: 0
            val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
            val pct = if (total > 0) (attended * 100f / total).toInt() else 0
            val records = fetchAttendanceRecords(userId, id)
            trends.add(PredictionEngine.analyzeTrend(records, pct, name))
        }

        val improving = trends.count { it.trend == PredictionEngine.TrendDirection.IMPROVING }
        val declining = trends.count { it.trend == PredictionEngine.TrendDirection.DECLINING }
        val summary = when {
            improving > declining -> "📈 Overall you're **improving**! $improving subject(s) trending up."
            declining > improving -> "📉 Heads up — $declining subject(s) are **declining**."
            else -> "➡️ Your attendance is mostly **stable** across subjects."
        }
        reply(text = summary, type = MessageType.TREND_CARD, trendData = trends)
    }

    private suspend fun handlePattern(userId: String, input: String) {
        val subjects = fetchSubjects(userId)
        if (subjects.isEmpty()) { reply("📭 No subjects found."); return }
        val matched = findSubjectFuzzy(input, subjects)

        if (matched != null) {
            val records = fetchAttendanceRecords(userId, matched.key)
            val analysis = PredictionEngine.analyzePatterns(records, matched.value)
            val sb = StringBuilder("🔍 **Attendance Patterns: ${matched.value}**\n\n")
            analysis.worstDay?.let {
                val dayName = it.dayOfWeek.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                sb.append("📉 **Worst day**: $dayName (${(it.presentRate * 100).roundToInt()}% attendance)\n")
            }
            analysis.bestDay?.let {
                val dayName = it.dayOfWeek.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                sb.append("📈 **Best day**: $dayName (${(it.presentRate * 100).roundToInt()}% attendance)\n")
            }
            sb.append("\n🔥 **Current streak**: ${analysis.currentStreak} ${if (analysis.isPositiveStreak) "present" else "absent"}\n")
            sb.append("🏆 **Longest present streak**: ${analysis.longestStreak}\n\n")
            sb.append("**Day breakdown:**\n")
            analysis.dayPatterns.forEach { dp ->
                val dayName = dp.dayOfWeek.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                val bar = "█".repeat((dp.presentRate * 10).toInt()) + "░".repeat(10 - (dp.presentRate * 10).toInt())
                sb.append("$dayName: $bar ${(dp.presentRate * 100).roundToInt()}%\n")
            }
            reply(sb.toString())
        } else {
            // Global pattern
            val sb = StringBuilder("🔍 **Overall Attendance Patterns**\n\n")
            val allRecords = subjects.flatMap { (id, _) -> fetchAttendanceRecords(userId, id) }
            val byDay = allRecords.groupBy { it.date.dayOfWeek }
            val dayStats = byDay.map { (dow, recs) ->
                val rate = recs.count { it.isPresent } * 100f / recs.size
                dow to rate.roundToInt()
            }.sortedBy { it.first }

            val worst = dayStats.minByOrNull { it.second }
            val best = dayStats.maxByOrNull { it.second }
            worst?.let { sb.append("📉 **You miss most on**: ${it.first.name.lowercase().replaceFirstChar { c -> c.uppercase() }} (${it.second}%)\n") }
            best?.let { sb.append("📈 **Your best day**: ${it.first.name.lowercase().replaceFirstChar { c -> c.uppercase() }} (${it.second}%)\n\n") }

            dayStats.forEach { (dow, pct) ->
                val dayName = dow.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                val bar = "█".repeat((pct / 10).coerceIn(0, 10)) + "░".repeat((10 - pct / 10).coerceIn(0, 10))
                sb.append("$dayName: $bar $pct%\n")
            }
            reply(sb.toString())
        }
    }

    private fun handleSmartQA(input: String) {
        val result = KnowledgeBase.findBestAnswer(input)
        if (result != null) {
            reply(result.first)
        } else {
            reply("🤔 I don't have a specific answer for that, but here are some things you can ask:\n\n" +
                  "❓ \"Why is 75% important?\"\n❓ \"What happens if attendance drops?\"\n" +
                  "❓ \"How to improve my attendance?\"\n❓ \"Does attendance affect placements?\"\n" +
                  "❓ \"How does AttendMate work?\"")
        }
    }

    private fun handleUnknown(input: String) {
        // Try knowledge base as fallback
        val qaResult = KnowledgeBase.findBestAnswer(input)
        if (qaResult != null) { reply(qaResult.first); return }

        reply("🤔 I didn't quite understand that. Here's what I can do:\n\n" +
              "📊 \"Show my attendance\" — Stats\n" +
              "📈 \"Predict my attendance\" — AI Forecast\n" +
              "📉 \"Show my trend\" — Trend Analysis\n" +
              "🔍 \"When do I miss most?\" — Patterns\n" +
              "🏆 \"Weekly summary\" — Week Overview\n" +
              "💡 \"Study tips\" — Smart Advice\n" +
              "💪 \"Motivate me\" — Encouragement\n" +
              "🎯 \"Set goal 85%\" — Goal Setting\n" +
              "❓ \"Why is 75% important?\" — Q&A\n" +
              "✏️ \"Mark Present\" — Actions")
    }

    /* ════════════════════ HELPER: Fetch Attendance Records ════════════════════ */

    private suspend fun fetchAttendanceRecords(userId: String, subjectId: String): List<PredictionEngine.AttendanceRecord> {
        val attSnap = db.collection("users").document(userId)
            .collection("subjects").document(subjectId)
            .collection("attendance").get().await()
        return attSnap.documents.mapNotNull { doc ->
            val dateObj = when (val raw = doc.get("date")) {
                is Timestamp -> raw.toDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                is String    -> runCatching { LocalDate.parse(raw) }.getOrNull()
                is Date      -> raw.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                else -> null
            } ?: return@mapNotNull null
            val status = doc.getString("status")?.uppercase() ?: "ABSENT"
            PredictionEngine.AttendanceRecord(dateObj, status == "PRESENT")
        }
    }

    /* ────────────────── Utility Functions ────────────────── */

    private suspend fun fetchSubjects(userId: String): Map<String, String> {
        val snap = try {
            val cached = db.collection("users").document(userId).collection("subjects").get(Source.CACHE).await()
            if (cached.isEmpty) throw Exception("Cache empty")
            cached
        } catch (e: Exception) {
            db.collection("users").document(userId).collection("subjects").get(Source.SERVER).await()
        }
        return snap.documents.associate { it.id to (it.getString("name") ?: "Unknown") }
    }

    private fun extractDate(input: String): String? {
        val lower = input.lowercase()
        Regex("(\\d{4}-\\d{2}-\\d{2})").find(input)?.let { return it.value }

        val monthMap = mapOf(
            "january" to 1, "february" to 2, "march" to 3, "april" to 4,
            "may" to 5, "june" to 6, "july" to 7, "august" to 8,
            "september" to 9, "october" to 10, "november" to 11, "december" to 12,
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
            "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9,
            "oct" to 10, "nov" to 11, "dec" to 12
        )
        val monthNames = monthMap.keys.toList()

        Regex("(\\d{1,2})(?:st|nd|rd|th)?\\s+(${monthNames.joinToString("|")})").find(lower)?.let { m ->
            val day = m.groupValues[1].toInt(); val month = monthMap[m.groupValues[2]] ?: 1
            return String.format(Locale.US, "%04d-%02d-%02d", LocalDate.now().year, month, day)
        }
        Regex("(${monthNames.joinToString("|")})\\s+(\\d{1,2})(?:st|nd|rd|th)?").find(lower)?.let { m ->
            val month = monthMap[m.groupValues[1]] ?: 1; val day = m.groupValues[2].toInt()
            return String.format(Locale.US, "%04d-%02d-%02d", LocalDate.now().year, month, day)
        }

        if (lower.contains("today"))     return LocalDate.now().toString()
        if (lower.contains("yesterday")) return LocalDate.now().minusDays(1).toString()

        val dayMap = mapOf("monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY)
        for ((name, day) in dayMap) {
            if (lower.contains(name)) {
                var date = LocalDate.now()
                while (date.dayOfWeek != day) date = date.minusDays(1)
                return date.toString()
            }
        }
        return null
    }

    private fun normalizeTime(t: String): String {
        if (t.isEmpty()) return ""
        var clean = t.lowercase().replace(".", "").replace(" ", "")
        val isPm = clean.endsWith("pm") || clean.endsWith("p")
        val isAm = clean.endsWith("am") || clean.endsWith("a")
        clean = clean.replace(Regex("[a-z]+"), "")
        val parts = clean.split(":")
        var hour = parts[0].toIntOrNull() ?: return ""
        val min = String.format(Locale.US, "%02d", if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0)
        if (isPm && hour < 12) hour += 12
        if (isAm && hour == 12) hour = 0
        // Handle bare numbers > 12 as 24h time (e.g., "16" -> 16:00)
        if (!isPm && !isAm && hour > 12) { /* already 24h */ }
        return String.format(Locale.US, "%02d:%s", hour, min)
    }

    private fun extractDayOfWeek(input: String): String {
        val lower = input.lowercase()
        if (lower.contains("today"))    return LocalDate.now().dayOfWeek.name
        if (lower.contains("tomorrow")) return LocalDate.now().plusDays(1).dayOfWeek.name
        val days = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        for (d in days) { if (lower.contains(d)) return d.uppercase() }
        return LocalDate.now().dayOfWeek.name
    }

    private fun readTimeField(doc: com.google.firebase.firestore.DocumentSnapshot, field: String): String {
        val value = doc.get(field) ?: return "--"
        return when (value) {
            is Timestamp -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(value.toDate())
            is String    -> value
            else         -> "--"
        }
    }

    private fun getHelpText(): String = """
        ✨ **AttendMate AI — Full Command Guide**
        
        📊 **ATTENDANCE**
        • "Show my attendance" — All subjects overview
        • "Attendance in [Subject]" — Specific stats
        • "Attendance on 2026-03-10" — Date history
        
        🧠 **AI INSIGHTS**
        • "Predict my attendance" — End-of-semester forecast
        • "Show my trend" — Improvement/decline analysis
        • "When do I miss most?" — Day-of-week patterns
        • "Weekly summary" — Last 7 days overview
        • "Analysis" — Deep dive report
        
        🔮 **PLANNING**
        • "How many can I miss in Math?" — Safe-to-miss count
        • "Set goal 85% for Math" — Set targets
        • "Study tips for Math" — Personalized advice
        • "Motivate me" — Data-driven encouragement
        
        🗓️ **SCHEDULE**
        • "My schedule today" — Today's classes
        • "Next class" — Upcoming lecture
        
        ⚙️ **ACTIONS**
        • "Mark Present in [Subject]" — Auto-fills from timetable
        • "Mark all present today" — Bulk mark
        • "Mark all present except Math" — With exclusions
        • "Delete attendance for today" — Undo records
        
        ❓ **Q&A**
        • "Why is 75% important?" — Attendance knowledge
        • "What happens if attendance drops?" — Consequences
        
        💡 **PRO TIPS**
        🔄 Follow-ups work: "What about Math?" after any query
        📍 Subjects: Use names like **Deep Learning**
        📅 Dates: "today", "yesterday", or **YYYY-MM-DD**
        ⏰ Time auto-fills from your timetable!
    """.trimIndent()

    private fun findSubjectFuzzy(input: String, subjects: Map<String, String>): Map.Entry<String, String>? {
        val lower = input.lowercase()

        // 1) Exact full-name match (case-insensitive) — highest priority
        subjects.entries.find { it.value.equals(lower.trim(), ignoreCase = true) }?.let { return it }

        // 2) Exact contains match — prefer longest match to avoid
        //    "Deep Learning" matching before "Deep Learning Practical"
        val containsMatches = subjects.entries.filter { lower.contains(it.value.lowercase()) }
        if (containsMatches.isNotEmpty()) {
            return containsMatches.maxByOrNull { it.value.length }
        }

        // 3) Fuzzy Levenshtein matching
        val words = lower.split(Regex("\\W+")).filter { it.isNotBlank() }
        var bestMatch: Map.Entry<String, String>? = null
        var bestDist = Int.MAX_VALUE

        for (entry in subjects) {
            val targetWords = entry.value.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }
            for (w in words) {
                for (tw in targetWords) {
                    val dist = levenshtein(w, tw)
                    val threshold = when { tw.length <= 3 -> 0; tw.length <= 5 -> 1; else -> 2 }
                    if (dist <= threshold && dist < bestDist) { bestDist = dist; bestMatch = entry }
                }
            }
        }
        return bestMatch
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        var cost = IntArray(lhs.length + 1) { it }
        var newCost = IntArray(lhs.length + 1) { 0 }
        for (i in 1..rhs.length) {
            newCost[0] = i
            for (j in 1..lhs.length) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                newCost[j] = minOf(cost[j] + 1, newCost[j - 1] + 1, cost[j - 1] + match)
            }
            val swap = cost; cost = newCost; newCost = swap
        }
        return cost[lhs.length]
    }
}
