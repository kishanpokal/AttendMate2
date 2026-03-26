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
    private var pendingClarification: String? = null

    // Conversation context for multi-turn memory
    private val conversationCtx = ConversationContext()

    // Smart suggestion chips based on context
    private val _currentSuggestions = MutableStateFlow(getTimeBasedSuggestions())
    val currentSuggestions: StateFlow<List<String>> = _currentSuggestions.asStateFlow()

    init {
        messages.add(
            ChatMessage(
                text = "ur AI bestie is online and locked in fr 💜\n\n" +
                        "I understand natural language — just ask me anything!\n\n" +
                        "📊 **Attendance** — \"show my attendance bestie\"\n" +
                        "🎓 **College** — \"my attendance in clg\"\n" +
                        "📈 **Predict** — \"Predict my attendance\"\n" +
                        "📉 **Trends** — \"Show my trend\"\n" +
                        "💀 **Alerts** — \"DBMS is lowkey cooked\"\n" +
                        "🗓️ **Schedule** — \"My schedule today\"",
                isUser = false
            )
        )
    }

    /* ────────────────── Time-Based Suggestions ────────────────── */

    private fun getTimeBasedSuggestions(): List<String> {
        val hour = LocalTime.now().hour
        return when {
            hour in 6..10  -> listOf("My schedule today", "show my attendance bestie", "🫡 skip budget", "my attendance in clg")
            hour in 11..14 -> listOf("Mark present now", "show my attendance bestie", "Study tips", "my attendance in clg")
            hour in 15..18 -> listOf("show my attendance bestie", "Show my trend", "Predict my attendance", "DBMS is lowkey cooked")
            else           -> listOf("Analysis", "🫡 skip budget", "Show my trend", "When do I miss most?")
        }
    }

    private fun updateSuggestionsAfterIntent(lastIntent: Intent) {
        _currentSuggestions.value = when (lastIntent) {
            Intent.TIMETABLE         -> listOf("Mark all present today", "Show my attendance", "Predict my attendance", "Mark present now")
            Intent.SUBJECT_SUMMARY   -> listOf("Study tips", "Show my trend", "Predict my attendance", "Skip budget")
            Intent.OVERALL_ANALYSIS  -> listOf("Predict my attendance", "Compare subjects", "Weekly summary", "Motivate me")
            Intent.MARK_ATTENDANCE   -> listOf("Show my attendance", "Mark all present today", "Weekly summary", "Analysis")
            Intent.MARK_BULK_ATTENDANCE -> listOf("Show my attendance", "Analysis", "Weekly summary", "Show my trend")
            Intent.DELETE_ATTENDANCE -> listOf("Show my attendance", "Mark all present today", "Analysis", "My schedule today")
            Intent.PREDICTION        -> listOf("Show my trend", "Study tips", "Weekly summary", "Set goal 85%")
            Intent.STUDY_TIPS        -> listOf("Predict my attendance", "Analysis", "Motivate me", "Show my trend")
            Intent.WEEKLY_SUMMARY    -> listOf("Monthly report", "Show my trend", "When do I miss most?", "Study tips")
            Intent.GOAL_SETTING      -> listOf("Show my attendance", "Predict my attendance", "Study tips", "Motivate me")
            Intent.MOTIVATION        -> listOf("Show my attendance", "Study tips", "Mark all present today", "Set goal 85%")
            Intent.TREND_ANALYSIS    -> listOf("Predict my attendance", "When do I miss most?", "Weekly summary", "Study tips")
            Intent.PATTERN_ANALYSIS  -> listOf("Show my trend", "Predict my attendance", "Study tips", "Monthly report")
            Intent.SMART_QA          -> listOf("Show my attendance", "Predict my attendance", "Study tips", "Analysis")
            Intent.COMPARE_SUBJECTS  -> listOf("Best subject", "Worst subject", "Monthly report", "Study tips")
            Intent.MONTHLY_REPORT    -> listOf("Weekly summary", "Show my trend", "Analyze pattern", "Predict my attendance")
            Intent.SUBJECT_SKIP_CALC -> listOf("Set goal 85%", "Show my attendance", "Study tips", "Predict my attendance")
            Intent.GET_STREAK        -> listOf("When do I miss most?", "Show my attendance", "Study tips", "Motivate me")
            Intent.GET_BEST_SUBJECT  -> listOf("Worst subject", "Show my trend", "Weekly summary", "Motivate me")
            Intent.GET_WORST_SUBJECT -> listOf("Best subject", "Study tips", "Predict my attendance", "Skip budget")
            Intent.EXAM_MODE_CHECK   -> listOf("Study tips", "Predict my attendance", "Show my attendance", "Motivate me")
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
        MOTIVATION, TREND_ANALYSIS, PATTERN_ANALYSIS, SMART_QA,
        COMPARE_SUBJECTS, MONTHLY_REPORT, SUBJECT_SKIP_CALC,
        GET_STREAK, GET_BEST_SUBJECT, GET_WORST_SUBJECT,
        EXAM_MODE_CHECK, CLARIFY, COLLEGE_ATTENDANCE, UNKNOWN
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
        NlpEngine.NlpIntent.COMPARE_SUBJECTS -> Intent.COMPARE_SUBJECTS
        NlpEngine.NlpIntent.MONTHLY_REPORT -> Intent.MONTHLY_REPORT
        NlpEngine.NlpIntent.SUBJECT_SKIP_CALC -> Intent.SUBJECT_SKIP_CALC
        NlpEngine.NlpIntent.GET_STREAK -> Intent.GET_STREAK
        NlpEngine.NlpIntent.GET_BEST_SUBJECT -> Intent.GET_BEST_SUBJECT
        NlpEngine.NlpIntent.GET_WORST_SUBJECT -> Intent.GET_WORST_SUBJECT
        NlpEngine.NlpIntent.EXAM_MODE_CHECK -> Intent.EXAM_MODE_CHECK
        NlpEngine.NlpIntent.COLLEGE_ATTENDANCE -> Intent.COLLEGE_ATTENDANCE
        NlpEngine.NlpIntent.CLARIFY -> Intent.CLARIFY
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

                if (pendingClarification != null) {
                    val pIntent = pendingClarification
                    pendingClarification = null
                    
                    val nlpResult = NlpEngine.analyse(userMessage, subjectNames = subjectNames)
                    val s1 = nlpResult.entities.subjectHint ?: userMessage
                    val s2 = nlpResult.entities.subject2 ?: conversationCtx.lastSubject
                    
                    val entities = NlpEngine.ExtractedEntities(subjectHint = s1, subject2 = s2)
                    
                    when (pIntent) {
                        "COMPARE" -> handleCompareSubjects(userId, entities, subjects)
                        "SKIP_BUDGET" -> handleSkipBudget(userId, entities, subjects)
                    }
                    return@launch
                }

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
                    Intent.GREETING  -> handleGreeting(userId, nlpResult.sentiment)
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
                    Intent.COMPARE_SUBJECTS     -> handleCompareSubjects(userId, nlpResult.entities, subjects)
                    Intent.MONTHLY_REPORT       -> handleMonthlyReport(userId, subjects)
                    Intent.SUBJECT_SKIP_CALC    -> handleSkipBudget(userId, nlpResult.entities, subjects)
                    Intent.GET_STREAK           -> handleGetStreak(userId, nlpResult.entities, subjects)
                    Intent.GET_BEST_SUBJECT     -> handleGetBestSubject(userId, subjects)
                    Intent.GET_WORST_SUBJECT    -> handleGetWorstSubject(userId, subjects)
                    Intent.EXAM_MODE_CHECK      -> handleExamModeCheck(userId, subjects)
                    Intent.COLLEGE_ATTENDANCE   -> handleCollegeAttendance(userId)
                    Intent.CLARIFY              -> handleUnknown(userId, userMessage)
                    Intent.UNKNOWN              -> handleUnknown(userId, userMessage)
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
        trendData: List<PredictionEngine.SubjectTrend>? = null,
        compareData: CompareCardData? = null,
        monthlyReportData: MonthlyReportCardData? = null,
        skipBudgetData: SkipBudgetCardData? = null,
        streakData: StreakCardData? = null,
        rankingData: SubjectRankingCardData? = null,
        examStatusData: ExamStatusCardData? = null,
        collegeSyncData: CollegeSyncCardData? = null
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
                trendData = trendData,
                compareData = compareData,
                monthlyReportData = monthlyReportData,
                skipBudgetData = skipBudgetData,
                streakData = streakData,
                rankingData = rankingData,
                examStatusData = examStatusData,
                collegeSyncData = collegeSyncData
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
                    .filter { it.getString("subjectId") == matchedSubject.key }
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

    private suspend fun handleCollegeAttendance(userId: String) {
        // Load scraped data
        val context = getApplication<Application>().applicationContext
        val file = java.io.File(context.filesDir, "scraped_attendance.json")
        if (!file.exists()) {
            reply("📭 I couldn't find any college data. Please go to the College Sync screen, login, and sync your attendance first!")
            return
        }

        val scrapedRecords = try {
            val arr = org.json.JSONArray(file.readText())
            val list = mutableListOf<com.kishan.attendmate.ui.settings.CollegeAttendanceRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(com.kishan.attendmate.ui.settings.CollegeAttendanceRecord(
                    subject = obj.optString("subject", ""),
                    date = obj.optString("date", ""),
                    fromTime = obj.optString("fromTime", ""),
                    toTime = obj.optString("toTime", ""),
                    topic = obj.optString("topic", ""),
                    status = obj.optString("status", "")
                ))
            }
            list
        } catch (e: Exception) {
            reply("❌ Error reading your college data. Try syncing again.")
            return
        }

        if (scrapedRecords.isEmpty()) {
            reply("📭 Your college attendance data is empty.")
            return
        }

        // Calculate overall college percentages
        val totalScraped = scrapedRecords.size
        val attendedScraped = scrapedRecords.count { it.status.equals("Present", true) }
        val overallPct = if (totalScraped > 0) (attendedScraped * 100) / totalScraped else 0

        // load app data
        val appData = mutableListOf<com.kishan.attendmate.ui.settings.CollegeAttendanceRecord>()
        try {
            val subjectsSnap = db.collection("users").document(userId).collection("subjects").get().await()
            val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val dateFmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())

            for (doc in subjectsSnap.documents) {
                val subName = doc.getString("name") ?: continue
                val attSnap = doc.reference.collection("attendance").get().await()
                for (attDoc in attSnap.documents) {
                    val status = attDoc.getString("status") ?: continue
                    val note = attDoc.getString("note") ?: ""
                    
                    val stStr = readTimeField(attDoc, "startTime")
                    val etStr = readTimeField(attDoc, "endTime")
                    val stParsed = runCatching { LocalTime.parse(stStr.padStart(5, '0')) }.getOrNull()
                    val stFormatted = if (stParsed != null) timeFmt.format(java.util.Date.from(stParsed.atDate(LocalDate.now()).atZone(java.time.ZoneId.systemDefault()).toInstant())) else stStr
                    val etParsed = runCatching { LocalTime.parse(etStr.padStart(5, '0')) }.getOrNull()
                    val etFormatted = if (etParsed != null) timeFmt.format(java.util.Date.from(etParsed.atDate(LocalDate.now()).atZone(java.time.ZoneId.systemDefault()).toInstant())) else etStr

                    var dString = ""
                    val dVal = attDoc.get("date")
                    if (dVal is Timestamp) {
                        dString = dateFmt.format(dVal.toDate())
                    } else if (dVal is String) {
                        val parsed = runCatching { LocalDate.parse(dVal) }.getOrNull()
                        if (parsed != null) dString = dateFmt.format(java.util.Date.from(parsed.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))
                    }
                    
                    if (dString.isNotBlank() && stFormatted.isNotBlank() && etFormatted.isNotBlank()) {
                        appData.add(
                            com.kishan.attendmate.ui.settings.CollegeAttendanceRecord(
                                subject = subName,
                                date = dString,
                                fromTime = stFormatted,
                                toTime = etFormatted,
                                topic = note,
                                status = status
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AiChat", "Failed to load app records for comparison", e)
        }

        // Generate mapping
        val allScrapedSubjects = scrapedRecords.map { it.subject }.distinct()
        val appToScrapedMap = appData.map { it.subject }.distinct().associateWith { appSubj ->
            val cleanApp = appSubj.lowercase().replace(Regex("[^a-z0-9]"), "")
            var bestMatch: String? = null
            for (ss in allScrapedSubjects) {
                val cleanScraped = ss.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (cleanApp == cleanScraped || cleanScraped.contains(cleanApp) || cleanApp.contains(cleanScraped)) {
                    bestMatch = ss; break
                }
            }
            bestMatch ?: appSubj
        }

        val mismatches = mutableListOf<String>()
        val collegeMissing = mutableListOf<String>()
        val matchedAppRecords = mutableListOf<com.kishan.attendmate.ui.settings.CollegeAttendanceRecord>()

        for (scraped in scrapedRecords) {
            val potentialAppSubjects = appToScrapedMap.filter { it.value == scraped.subject }.keys
            val matchedApp = appData.find { app ->
                potentialAppSubjects.contains(app.subject) &&
                app.date == scraped.date &&
                app.fromTime == scraped.fromTime &&
                app.toTime == scraped.toTime
            }
            if (matchedApp != null) {
                matchedAppRecords.add(matchedApp)
                if (!matchedApp.status.equals(scraped.status, true)) {
                    mismatches.add("❗ **${scraped.subject}** on ${scraped.date}: College says **${scraped.status}**, App says **${matchedApp.status}**")
                }
            } else {
                collegeMissing.add("🚨 **${scraped.subject}** on ${scraped.date}: Exists in college, missing in App")
            }
        }

        val unmatchedAppRecords = appData.filter { it !in matchedAppRecords }
        val appMissing = unmatchedAppRecords.map { app ->
            "🤔 **${app.subject}** on ${app.date}: Exists in App, missing in college"
        }

        val summaryText = "Your overall college system attendance is **$overallPct%**.\n\n" +
            "You have **${mismatches.size}** mismatched records, **${collegeMissing.size}** missing from the app, and **${appMissing.size}** missing from the college."

        reply(
            text = "Here's the exclusive scoop on your college attendance 🔥",
            type = MessageType.COLLEGE_SYNC_CARD,
            collegeSyncData = CollegeSyncCardData(
                overallCollegePct = overallPct,
                mismatches = mismatches,
                appMissing = appMissing,
                collegeMissing = collegeMissing,
                syncSummaryText = summaryText
            )
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

    private suspend fun handleGreeting(userId: String, sentiment: NlpEngine.Sentiment) {
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
        val subjects = runCatching { fetchSubjects(userId) }.getOrDefault(emptyMap())
        if (subjects.isNotEmpty()) {
            var total = 0
            var attended = 0
            for ((id, _) in subjects) {
                val doc = db.collection("users").document(userId).collection("subjects").document(id).get().await()
                total += doc.getLong("totalClasses")?.toInt() ?: 0
                attended += doc.getLong("attendedClasses")?.toInt() ?: 0
            }
            if (total > 0) {
                val overall = (attended * 100f / total).roundToInt()
                val remark = if (overall >= 75) "Great job, it's above 75%!" else "Let's work on getting it above 75%."
                reply("👋 $timeGreet! Your overall attendance is **$overall%**. $remark $moodReply")
                return
            }
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

    private suspend fun handleUnknown(userId: String, input: String) {
        // Try knowledge base as fallback
        val qaResult = KnowledgeBase.findBestAnswer(input)
        if (qaResult != null) { reply(qaResult.first); return }

        val subjects = fetchSubjects(userId)
        val matched = findSubjectFuzzy(input, subjects)
        if (matched != null) {
            reply("🤔 I didn't quite understand, but it looks like you mentioned **${matched.value}**.\nTry saying:\n- 'Show attendance for ${matched.value}'\n- 'Predict ${matched.value}'\n- 'Study tips for ${matched.value}'")
            return
        }

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

    private suspend fun handleCompareSubjects(userId: String, entities: NlpEngine.ExtractedEntities, subjects: Map<String, String>) {
        val s1Name = entities.subjectHint
        val s2Name = entities.subject2 ?: conversationCtx.lastSubject

        if (s1Name == null || s2Name == null || s1Name == s2Name) {
            pendingClarification = "COMPARE"
            reply("Which two subjects do you want to compare? Example: 'Compare Maths vs Physics'")
            return
        }

        val s1Entry = findSubjectFuzzy(s1Name, subjects)
        val s2Entry = findSubjectFuzzy(s2Name, subjects)

        if (s1Entry == null || s2Entry == null) {
            reply("Couldn't find one or both of those subjects. Please check the spellings.")
            return
        }

        val recordsA = fetchAttendanceRecords(userId, s1Entry.key)
        val recordsB = fetchAttendanceRecords(userId, s2Entry.key)

        val docA = db.collection("users").document(userId).collection("subjects").document(s1Entry.key).get().await()
        val docB = db.collection("users").document(userId).collection("subjects").document(s2Entry.key).get().await()

        val pctA = calculatePct(docA.getLong("attendedClasses")?.toInt() ?: 0, docA.getLong("totalClasses")?.toInt() ?: 0)
        val pctB = calculatePct(docB.getLong("attendedClasses")?.toInt() ?: 0, docB.getLong("totalClasses")?.toInt() ?: 0)

        val result = PredictionEngine.compareSubjects(recordsA, s1Entry.value, pctA, recordsB, s2Entry.value, pctB)

        reply(
            text = "Here is the comparison between **${s1Entry.value}** and **${s2Entry.value}**:",
            type = MessageType.COMPARE_CARD,
            compareData = CompareCardData(result)
        )
    }

    private fun calculatePct(attended: Int, total: Int): Int {
        return if (total > 0) (attended * 100f / total).roundToInt() else 0
    }

    private suspend fun handleMonthlyReport(userId: String, subjects: Map<String, String>) {
        if (subjects.isEmpty()) { reply("📭 No subjects found."); return }
        val allRecords = mutableMapOf<String, List<PredictionEngine.AttendanceRecord>>()
        for ((id, name) in subjects) {
            allRecords[name] = fetchAttendanceRecords(userId, id)
        }
        val report = PredictionEngine.getMonthlyReport(allRecords)
        reply(
            text = "Here is your **${report.monthName}** attendance report:",
            type = MessageType.MONTHLY_REPORT_CARD,
            monthlyReportData = MonthlyReportCardData(report)
        )
    }

    private suspend fun handleSkipBudget(userId: String, entities: NlpEngine.ExtractedEntities, subjects: Map<String, String>) {
        val sName = entities.subjectHint ?: conversationCtx.lastSubject
        if (sName == null) {
            pendingClarification = "SKIP_BUDGET"
            reply("For which subject do you want to check the skip budget?")
            return
        }
        val match = findSubjectFuzzy(sName, subjects) ?: run { reply("Subject not found."); return }
        
        val doc = db.collection("users").document(userId).collection("subjects").document(match.key).get().await()
        val total = doc.getLong("totalClasses")?.toInt() ?: 0
        val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
        val target = entities.percentageHint ?: 75
        val records = fetchAttendanceRecords(userId, match.key)
        
        val budget = PredictionEngine.calculateSkipBudget(records, match.value, calculatePct(attended, total), target)
        reply(
            text = "Skip budget details for **${match.value}**:",
            type = MessageType.SKIP_BUDGET_CARD,
            skipBudgetData = SkipBudgetCardData(budget)
        )
    }

    private suspend fun handleGetStreak(userId: String, entities: NlpEngine.ExtractedEntities, subjects: Map<String, String>) {
        val sName = entities.subjectHint ?: conversationCtx.lastSubject
        val targetSubject = if (sName != null) findSubjectFuzzy(sName, subjects) else null
        
        if (targetSubject != null) {
            val records = fetchAttendanceRecords(userId, targetSubject.key)
            val analysis = PredictionEngine.analyzePatterns(records, targetSubject.value)
            
            val cp = if (analysis.isPositiveStreak) analysis.currentStreak else 0
            val ca = if (!analysis.isPositiveStreak) analysis.currentStreak else 0
            
            reply(
                text = "Streak stats for **${targetSubject.value}**:",
                type = MessageType.STREAK_CARD,
                streakData = StreakCardData(cp, analysis.longestStreak, ca, analysis.isPositiveStreak)
            )
        } else {
            // Overall
            val allRecords = subjects.flatMap { fetchAttendanceRecords(userId, it.key) }.sortedByDescending { it.date }
            var currentP = 0; var currentA = 0; var longestP = 0; var tempP = 0
            val isPos = allRecords.firstOrNull()?.isPresent == true
            
            if (isPos) {
                for (r in allRecords) { if (r.isPresent) currentP++ else break }
            } else {
                for (r in allRecords) { if (!r.isPresent) currentA++ else break }
            }
            
            for (r in allRecords.reversed()) {
                if (r.isPresent) tempP++ else { longestP = kotlin.math.max(longestP, tempP); tempP = 0 }
            }
            longestP = kotlin.math.max(longestP, tempP)
            
            reply(
                text = "Your **overall** streak stats across all subjects:",
                type = MessageType.STREAK_CARD,
                streakData = StreakCardData(currentP, longestP, currentA, isPos)
            )
        }
    }

    private suspend fun handleGetBestSubject(userId: String, subjects: Map<String, String>) {
        if (subjects.isEmpty()) { reply("📭 No subjects found."); return }
        val counts = mutableMapOf<String, Pair<Int, Int>>()
        for ((id, name) in subjects) {
            val doc = db.collection("users").document(userId).collection("subjects").document(id).get().await()
            counts[name] = Pair(doc.getLong("attendedClasses")?.toInt() ?: 0, doc.getLong("totalClasses")?.toInt() ?: 0)
        }
        val rank = PredictionEngine.getSubjectRanking(counts)
        val best = rank.firstOrNull()
        reply(
            text = "🏆 Your best subject is **${best?.first}** with **${best?.second}%** attendance!",
            type = MessageType.SUBJECT_RANKING_CARD,
            rankingData = SubjectRankingCardData(rank.take(3))
        )
    }

    private suspend fun handleGetWorstSubject(userId: String, subjects: Map<String, String>) {
        if (subjects.isEmpty()) { reply("📭 No subjects found."); return }
        val counts = mutableMapOf<String, Pair<Int, Int>>()
        for ((id, name) in subjects) {
            val doc = db.collection("users").document(userId).collection("subjects").document(id).get().await()
            counts[name] = Pair(doc.getLong("attendedClasses")?.toInt() ?: 0, doc.getLong("totalClasses")?.toInt() ?: 0)
        }
        val rank = PredictionEngine.getSubjectRanking(counts).reversed()
        val worst = rank.firstOrNull()
        reply(
            text = "⚠️ You are struggling most in **${worst?.first}** with **${worst?.second}%** attendance.",
            type = MessageType.SUBJECT_RANKING_CARD,
            rankingData = SubjectRankingCardData(rank.take(3))
        )
    }

    private suspend fun handleExamModeCheck(userId: String, subjects: Map<String, String>) {
        if (subjects.isEmpty()) { reply("📭 No subjects found."); return }
        val examStatus = mutableListOf<SubjectExamStatus>()
        var safe = 0
        for ((id, name) in subjects) {
            val doc = db.collection("users").document(userId).collection("subjects").document(id).get().await()
            val total = doc.getLong("totalClasses")?.toInt() ?: 0
            val attended = doc.getLong("attendedClasses")?.toInt() ?: 0
            val pct = calculatePct(attended, total)
            val isEligible = pct >= 75
            val needed = if (!isEligible) kotlin.math.ceil((0.75 * total - attended) / 0.25).toInt() else 0
            if (isEligible) safe++
            examStatus.add(SubjectExamStatus(name, pct, isEligible, needed))
        }
        
        reply(
            text = "📋 **Exam Eligibility Check**\nYou are safe in $safe out of ${subjects.size} subjects.",
            type = MessageType.EXAM_STATUS_CARD,
            examStatusData = ExamStatusCardData(examStatus)
        )
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
        
        📊 **ATTENDANCE TRACKING**
        • "Show my attendance" — All subjects overview
        • "Attendance in [Subject]" — Specific stats
        • "Attendance on [Date/Today]" — Date history
        • "My attendance in clg" — College portal sync status
        
        🧠 **AI INSIGHTS & REPORTS**
        • "Analysis" — Deep dive overall report
        • "Weekly summary" — Last 7 days overview
        • "Monthly report" — 30-day attendance report
        • "When do I miss most?" — Day-of-week patterns
        • "Show my trend" — Improvement/decline analysis
        • "Compare [Subj A] vs [Subj B]" — Subject comparison
        
        🔮 **PREDICTIONS & PLANNING**
        • "Predict my attendance" — End-of-semester forecast
        • "Skip budget" / "How many can I miss in Math?" — Safe-to-miss count
        • "Set goal 85% for Math" — Set targets & track them
        • "Study tips for Math" — Personalized advice
        • "Motivate me" — Data-driven encouragement
        
        🏆 **RANKINGS & STREAKS**
        • "My current streak" — Consecutive classes attended
        • "Best subject" — Highest attendance subject
        • "Worst subject" — Lowest attendance / critical subjects
        • "Am I exam eligible?" — Exam debarment status

        🗓️ **SCHEDULE**
        • "My schedule today" — Today's classes
        • "Next class" — Upcoming lecture
        
        ⚙️ **ACTIONS**
        • "Mark Present in [Subject]" — Auto-fills from timetable
        • "Mark all present today" — Bulk mark
        • "Mark all present except Math" — With exclusions
        • "Delete attendance for today" — Undo records
        
        ❓ **KNOWLEDGE & Q&A**
        • "Why is 75% important?" — General knowledge
        • "What happens if attendance drops?" — Rules/policies
        
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
