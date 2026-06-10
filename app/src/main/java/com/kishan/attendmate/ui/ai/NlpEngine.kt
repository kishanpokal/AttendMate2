package com.kishan.attendmate.ui.ai

/**
 * On-device NLP Engine for intent classification and entity extraction.
 *
 * Pipeline:  Input → Tokenize → Remove Stop Words → Stem → Synonym Expand
 *           → N-gram → Score Intents → Extract Entities → Return Result
 *
 * No API calls — fully offline.
 */
object NlpEngine {

    /* ═══════════════════ PUBLIC API ═══════════════════ */

    data class NlpResult(
        val intent: NlpIntent,
        val confidence: Float,          // 0..1
        val entities: ExtractedEntities,
        val sentiment: Sentiment
    )

    data class ExtractedEntities(
        val subjectHint: String? = null,       // raw text fragment that looks like a subject
        val subject2: String? = null,          // second subject for comparison
        val dateHint: String? = null,           // raw date fragment
        val monthHint: Int? = null,             // 0-indexed month
        val statusHint: String? = null,         // "present" / "absent"
        val percentageHint: Int? = null,        // e.g. 85 from "set goal 85%"
        val timeRangeHint: String? = null       // e.g. "9am to 10am"
    )

    enum class Sentiment { POSITIVE, NEGATIVE, NEUTRAL, FRUSTRATED }

    enum class NlpIntent {
        GREETING, HELP,
        SUBJECT_SUMMARY, ATTENDANCE_FOR_DATE, TIMETABLE, NEXT_CLASS,
        OVERALL_ANALYSIS, MARK_ATTENDANCE, MARK_BULK_ATTENDANCE,
        DELETE_ATTENDANCE, CONFIRM_YES, CONFIRM_NO, WHATIF,
        // ── New advanced intents ──
        PREDICTION, STUDY_TIPS, WEEKLY_SUMMARY, GOAL_SETTING,
        MOTIVATION, TREND_ANALYSIS, PATTERN_ANALYSIS, SMART_QA,
        COMPARE_SUBJECTS, MONTHLY_REPORT, SUBJECT_SKIP_CALC,
        GET_STREAK, GET_BEST_SUBJECT, GET_WORST_SUBJECT,
        EXAM_MODE_CHECK, CLARIFY,
        // ── Navigation intents ──
        NAVIGATE_ANALYTICS,
        NAVIGATE_SETTINGS,
        NAVIGATE_TIMETABLE_SETUP,
        NAVIGATE_FRIENDS,
        NAVIGATE_MANAGE_SUBJECTS,
        NAVIGATE_COLLEGE_SYNC,
        NAVIGATE_ADD_ATTENDANCE,
        NAVIGATE_HOME,
        COLLEGE_ATTENDANCE,
        UNKNOWN
    }

    /**
     * Analyse raw user text and return structured NLP result.
     */
    fun analyse(
        rawInput: String,
        hasPendingAction: Boolean = false,
        lastIntent: NlpIntent? = null,
        subjectNames: List<String> = emptyList()
    ): NlpResult {
        val lower = rawInput.lowercase().trim()

        // ── Fast-path: confirmation while pending ──
        if (hasPendingAction) {
            if (lower in CONFIRM_YES_SET) return quick(NlpIntent.CONFIRM_YES, 1f, lower, subjectNames)
            if (lower in CONFIRM_NO_SET)  return quick(NlpIntent.CONFIRM_NO, 1f, lower, subjectNames)
        }

        val tokens        = tokenize(lower)
        val stemmed       = tokens.map { stem(it) }
        val expanded      = tokens.map { synonymExpand(it) } + tokens
        val bigrams       = ngrams(tokens, 2)
        val trigrams      = ngrams(tokens, 3)
        val allSignals    = (expanded + bigrams + trigrams).toSet()

        // Score every intent
        val scores = NlpIntent.entries
            .filter { it != NlpIntent.CONFIRM_YES && it != NlpIntent.CONFIRM_NO }
            .map { intent ->
                val base = scoreIntent(intent, tokens, stemmed, allSignals, lower)
                // Context bonus: if this is a follow-up of the same category, +0.05
                val contextBonus = if (lastIntent != null && intentGroup(intent) == intentGroup(lastIntent)) 0.05f else 0f
                intent to (base + contextBonus).coerceAtMost(1f)
            }

        val sortedScores = scores.sortedByDescending { it.second }
        val bestIntent = sortedScores.firstOrNull()?.first ?: NlpIntent.UNKNOWN
        var bestScore = sortedScores.firstOrNull()?.second ?: 0f
        val secondBestScore = sortedScores.getOrNull(1)?.second ?: 0f

        bestScore = confidenceBoost(bestScore, secondBestScore, bestIntent, lastIntent)

        val finalIntent = if (bestScore < 0.15f) NlpIntent.UNKNOWN else bestIntent

        val entities  = extractEntities(lower, tokens, subjectNames, finalIntent)
        val sentiment = detectSentiment(tokens, expanded)

        return NlpResult(finalIntent, bestScore, entities, sentiment)
    }

    /* ═══════════════════ TOKENIZER ═══════════════════ */

    private val STOP_WORDS = setOf(
        "a", "an", "the", "is", "are", "was", "were", "am", "be", "been",
        "do", "does", "did", "i", "me", "my", "we", "our", "you", "your",
        "he", "she", "it", "they", "them", "this", "that", "of", "to",
        "and", "or", "but", "so", "for", "with", "at", "by", "from",
        "up", "on", "in", "if", "can", "will", "just", "please", "pls",
        "could", "would", "should", "let", "also", "very", "too", "really"
    )

    private fun tokenize(text: String): List<String> =
        text.replace(Regex("[^a-z0-9%\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in STOP_WORDS }

    /* ═══════════════════ PORTER STEMMER (simplified) ═══════════════════ */

    private fun stem(word: String): String {
        var w = word
        if (w == "classes") return "class"
        if (w.startsWith("lectur")) return "lect"
        if (w == "attended" || w == "attending") return "attend"
        if (w == "predicted") return "predict"
        if (w == "analyzing") return "analyz"
        if (w == "comparing") return "compar"

        // Step 1: plurals / past tense
        if (w.endsWith("sses")) w = w.dropLast(2)
        else if (w.endsWith("ies")) w = w.dropLast(2)
        else if (w.endsWith("ing") && w.length > 5) w = w.dropLast(3)
        else if (w.endsWith("ed") && w.length > 4) w = w.dropLast(2)
        else if (w.endsWith("tion")) w = w.dropLast(4) + "t"
        else if (w.endsWith("ness")) w = w.dropLast(4)
        else if (w.endsWith("ment")) w = w.dropLast(4)
        else if (w.endsWith("able")) w = w.dropLast(4)
        else if (w.endsWith("ful")) w = w.dropLast(3)
        else if (w.endsWith("ly") && w.length > 4) w = w.dropLast(2)
        else if (w.endsWith("s") && !w.endsWith("ss") && w.length > 3) w = w.dropLast(1)
        return w
    }

    /* ═══════════════════ SYNONYM DICTIONARY ═══════════════════ */

    private val SYNONYMS: Map<String, String> = mapOf(
        // Attendance / status
        "bunk" to "absent", "bunked" to "absent", "bunking" to "absent",
        "skip" to "absent", "skipped" to "absent", "skipping" to "absent",
        "ditch" to "absent", "ditched" to "absent",
        "miss" to "absent", "missed" to "absent", "missing" to "absent",
        "attend" to "present", "attended" to "present", "attending" to "present",
        "went" to "present", "go" to "present", "gone" to "present",
        // Query synonyms
        "show" to "display", "view" to "display", "see" to "display",
        "check" to "display", "give" to "display", "tell" to "display",
        "get" to "display",
        // Analysis
        "analyze" to "analysis", "overview" to "analysis", "report" to "analysis",
        "insight" to "analysis", "insights" to "analysis", "stats" to "analysis",
        "statistics" to "analysis", "summary" to "analysis", "summarize" to "analysis",
        // Prediction
        "predict" to "prediction", "forecast" to "prediction", "future" to "prediction",
        "end" to "prediction", "projection" to "prediction", "estimate" to "prediction",
        "expected" to "prediction", "semester" to "prediction",
        // Study
        "tip" to "tips", "advice" to "tips", "suggest" to "tips",
        "suggestion" to "tips", "suggestions" to "tips", "recommend" to "tips",
        "recommendation" to "tips", "improve" to "tips", "improving" to "tips",
        "better" to "tips", "help" to "tips",
        // Motivation
        "motivate" to "motivation", "encourage" to "motivation",
        "inspire" to "motivation", "lazy" to "motivation",
        "unmotivated" to "motivation", "demotivated" to "motivation",
        "tired" to "motivation", "boost" to "motivation",
        // Goal
        "target" to "goal", "aim" to "goal", "objective" to "goal",
        // Trend
        "trend" to "trending", "direction" to "trending", "progress" to "trending",
        "improving" to "trending", "declining" to "trending", "growing" to "trending",
        // Pattern
        "pattern" to "pattern", "habit" to "pattern", "routine" to "pattern",
        "frequently" to "pattern", "often" to "pattern", "usually" to "pattern",
        // Schedule
        "schedule" to "timetable", "class" to "timetable", "classes" to "timetable",
        "lecture" to "timetable", "lectures" to "timetable", "today" to "timetable",
        // Delete
        "delete" to "remove", "undo" to "remove", "clear" to "remove",
        "erase" to "remove",
        // Greeting
        "hi" to "hello", "hey" to "hello", "hii" to "hello", "yo" to "hello",
        "sup" to "hello", "hiii" to "hello", "hiiii" to "hello",
        // Mark
        "mark" to "mark", "record" to "mark", "log" to "mark", "add" to "mark",
        // Week
        "week" to "weekly", "7days" to "weekly", "last7" to "weekly",
        "past7" to "weekly", "thisweek" to "weekly",
        // New intents
        "compare" to "compare", "versus" to "compare", "vs" to "compare",
        "difference" to "compare", "better" to "compare",
        "monthly" to "monthly", "last month" to "monthly",
        "this month" to "monthly", "30 days" to "monthly",
        "skip budget" to "skipbudget", "how many can" to "skipbudget",
        "safe to bunk" to "skipbudget", "afford" to "skipbudget",
        "streak" to "streak", "row" to "streak", "consecutive" to "streak",
        "worst subject" to "worst", "weakest" to "worst", "failing" to "worst",
        "best subject" to "best", "strongest" to "best", "highest" to "best",
        "exam" to "exam", "debarred" to "exam", "eligible" to "exam",
        "detained" to "exam",
        "college" to "clg", "portal" to "clg", "system" to "clg", "sync" to "clg",
        "mismatch" to "mismatch", "mismatched" to "mismatch", "mismatches" to "mismatch",
        // Navigation
        "open" to "navigate", "goto" to "navigate",
        "page" to "screen", "section" to "screen",
        "view" to "show",
        "stats" to "statistics", "graph" to "chart",
        "report" to "analytics",
        "portal" to "college",
        "manage" to "edit", "modify" to "edit", "update" to "edit",
        "configure" to "settings", "setup" to "edit", "profile" to "settings",
        "mates" to "friends", "buddy" to "friends", "buddies" to "friends",
        "classes" to "subjects", "course" to "subject", "courses" to "subjects",
        "record" to "attendance", "log" to "attendance", "entry" to "attendance"
    )

    private fun synonymExpand(word: String): String = SYNONYMS[word] ?: word

    /* ═══════════════════ N-GRAM GENERATOR ═══════════════════ */

    private fun ngrams(tokens: List<String>, n: Int): List<String> {
        if (tokens.size < n) return emptyList()
        return (0..tokens.size - n).map { tokens.subList(it, it + n).joinToString(" ") }
    }

    /* ═══════════════════ INTENT SCORING ═══════════════════ */

    /**
     * Each intent has a set of weighted keyword/phrase signals.
     * Score = sum of matched signals / max possible score, clamped to [0, 1].
     */
    private data class Signal(val text: String, val weight: Float)

    private val INTENT_SIGNALS: Map<NlpIntent, List<Signal>> = mapOf(

        NlpIntent.GREETING to listOf(
            Signal("hello", 0.8f), Signal("hi", 0.6f), Signal("hey", 0.6f),
            Signal("good morning", 0.8f), Signal("good afternoon", 0.8f),
            Signal("good evening", 0.8f), Signal("yo", 0.5f)
        ),

        NlpIntent.HELP to listOf(
            Signal("help", 0.8f), Signal("what can you do", 1f),
            Signal("commands", 0.7f), Signal("menu", 0.6f),
            Signal("how to use", 0.9f), Signal("guide", 0.7f),
            Signal("features", 0.6f), Signal("options", 0.5f)
        ),

        NlpIntent.SUBJECT_SUMMARY to listOf(
            Signal("attendance", 0.5f), Signal("display", 0.3f),
            Signal("percentage", 0.6f), Signal("subject", 0.4f),
            Signal("how many class", 0.7f), Signal("show attendance", 0.8f),
            Signal("my attendance", 0.8f), Signal("show my", 0.5f)
        ),

        NlpIntent.ATTENDANCE_FOR_DATE to listOf(
            Signal("attendance", 0.3f), Signal("record", 0.3f),
            Signal("on", 0.2f), Signal("today", 0.3f),
            Signal("yesterday", 0.4f), Signal("date", 0.4f),
            Signal("attendance on", 0.7f), Signal("attendance today", 0.8f),
            Signal("record on", 0.6f), Signal("attendance yesterday", 0.8f)
        ),

        NlpIntent.TIMETABLE to listOf(
            Signal("timetable", 0.9f), Signal("schedule", 0.8f),
            Signal("classes today", 0.9f), Signal("lectures today", 0.9f),
            Signal("what class", 0.7f), Signal("my schedule", 0.9f),
            Signal("classes tomorrow", 0.9f), Signal("schedule today", 0.9f),
            Signal("schedule tomorrow", 0.9f), Signal("today schedule", 0.8f)
        ),

        NlpIntent.NEXT_CLASS to listOf(
            Signal("next class", 1f), Signal("upcoming class", 0.9f),
            Signal("next lecture", 1f), Signal("upcoming lecture", 0.9f),
            Signal("whats next", 0.7f), Signal("what next", 0.7f)
        ),

        NlpIntent.OVERALL_ANALYSIS to listOf(
            Signal("analysis", 0.8f), Signal("analyze", 0.8f),
            Signal("overview", 0.7f), Signal("how am doing", 0.8f),
            Signal("overall", 0.7f), Signal("report", 0.6f),
            Signal("stats", 0.6f), Signal("insights", 0.6f),
            Signal("full analysis", 1f), Signal("deep analysis", 1f)
        ),

        NlpIntent.MARK_ATTENDANCE to listOf(
            Signal("mark", 0.5f), Signal("present", 0.4f), Signal("absent", 0.4f),
            Signal("mark present", 0.9f), Signal("mark absent", 0.9f),
            Signal("mark bunk", 0.8f), Signal("log attendance", 0.8f),
            Signal("record attendance", 0.7f)
        ),

        NlpIntent.MARK_BULK_ATTENDANCE to listOf(
            Signal("mark all", 1f), Signal("mark everything", 0.9f),
            Signal("mark present till", 0.9f), Signal("mark absent till", 0.9f),
            Signal("bulk mark", 0.9f), Signal("all present", 0.8f),
            Signal("mark all present", 1f), Signal("mark all absent", 1f)
        ),

        NlpIntent.DELETE_ATTENDANCE to listOf(
            Signal("remove", 0.5f), Signal("delete", 0.6f),
            Signal("clear", 0.4f), Signal("undo", 0.5f),
            Signal("delete attendance", 1f), Signal("remove attendance", 1f),
            Signal("clear attendance", 0.9f), Signal("undo attendance", 0.9f),
            Signal("delete record", 0.8f)
        ),

        NlpIntent.WHATIF to listOf(
            Signal("how many", 0.3f), Signal("can miss", 0.6f),
            Signal("can bunk", 0.6f), Signal("can skip", 0.6f),
            Signal("can ditch", 0.5f), Signal("how many can miss", 1f),
            Signal("how many can bunk", 1f), Signal("how many can skip", 1f),
            Signal("safe to bunk", 0.9f), Signal("safely miss", 0.9f),
            Signal("what if miss", 0.9f), Signal("what if bunk", 0.9f),
            Signal("need to attend", 0.7f), Signal("how many need", 0.6f)
        ),

        // ── NEW ADVANCED INTENTS ──

        NlpIntent.PREDICTION to listOf(
            Signal("prediction", 0.9f), Signal("predict", 0.9f),
            Signal("forecast", 0.9f), Signal("where will", 0.6f),
            Signal("end up", 0.5f), Signal("end of semester", 0.9f),
            Signal("predict attendance", 1f), Signal("future attendance", 0.9f),
            Signal("expected attendance", 0.8f), Signal("what will", 0.4f),
            Signal("projected", 0.7f), Signal("semester end", 0.8f)
        ),

        NlpIntent.STUDY_TIPS to listOf(
            Signal("tips", 0.7f), Signal("advice", 0.6f),
            Signal("suggest", 0.5f), Signal("recommendation", 0.6f),
            Signal("study tips", 1f), Signal("improve attendance", 0.9f),
            Signal("how improve", 0.8f), Signal("how to improve", 0.9f),
            Signal("tips for", 0.8f), Signal("advice for", 0.8f),
            Signal("how better", 0.7f), Signal("get better", 0.6f)
        ),

        NlpIntent.WEEKLY_SUMMARY to listOf(
            Signal("weekly", 0.7f), Signal("week summary", 1f),
            Signal("weekly summary", 1f), Signal("this week", 0.7f),
            Signal("past week", 0.8f), Signal("last week", 0.8f),
            Signal("how was week", 0.9f), Signal("week report", 0.9f),
            Signal("weekly report", 0.9f), Signal("7 day", 0.6f),
            Signal("how my week", 0.9f)
        ),

        NlpIntent.GOAL_SETTING to listOf(
            Signal("goal", 0.7f), Signal("set goal", 1f),
            Signal("target", 0.6f), Signal("set target", 1f),
            Signal("aim", 0.5f), Signal("objective", 0.5f),
            Signal("my goal", 0.8f), Signal("goal for", 0.8f),
            Signal("target for", 0.8f), Signal("%", 0.2f)
        ),

        NlpIntent.MOTIVATION to listOf(
            Signal("motivation", 0.9f), Signal("motivate", 0.9f),
            Signal("encourage", 0.8f), Signal("inspire", 0.7f),
            Signal("lazy", 0.6f), Signal("unmotivated", 0.8f),
            Signal("demotivated", 0.8f), Signal("tired", 0.5f),
            Signal("motivate me", 1f), Signal("boost", 0.6f),
            Signal("feel lazy", 0.9f), Signal("dont want", 0.5f),
            Signal("dont feel like", 0.7f), Signal("no mood", 0.6f),
            Signal("not feeling", 0.5f), Signal("pump me up", 0.8f)
        ),

        NlpIntent.TREND_ANALYSIS to listOf(
            Signal("trending", 0.7f), Signal("trend", 0.8f),
            Signal("direction", 0.5f), Signal("progress", 0.6f),
            Signal("improving", 0.6f), Signal("declining", 0.6f),
            Signal("show trend", 1f), Signal("my trend", 0.9f),
            Signal("am improving", 0.9f), Signal("getting better", 0.7f),
            Signal("getting worse", 0.7f), Signal("going up", 0.6f),
            Signal("going down", 0.6f)
        ),

        NlpIntent.PATTERN_ANALYSIS to listOf(
            Signal("pattern", 0.8f), Signal("habit", 0.6f),
            Signal("routine", 0.5f), Signal("frequently", 0.5f),
            Signal("often miss", 0.8f), Signal("usually miss", 0.8f),
            Signal("when miss", 0.8f), Signal("which day", 0.7f),
            Signal("miss most", 0.9f), Signal("when do miss", 0.9f),
            Signal("day miss", 0.7f), Signal("worst day", 0.7f),
            Signal("attendance pattern", 1f)
        ),

        NlpIntent.SMART_QA to listOf(
            Signal("why", 0.3f), Signal("what is", 0.3f),
            Signal("explain", 0.4f), Signal("important", 0.3f),
            Signal("why important", 0.6f), Signal("what happens", 0.5f),
            Signal("rules", 0.4f), Signal("policy", 0.4f),
            Signal("consequence", 0.5f), Signal("benefit", 0.4f),
            Signal("why attendance", 0.8f), Signal("attendance important", 0.7f)
        ),
        
        NlpIntent.COMPARE_SUBJECTS to listOf(
            Signal("compare", 0.9f), Signal("vs", 0.9f), Signal("versus", 0.9f),
            Signal("compare subjects", 1f), Signal("which better", 0.8f),
            Signal("difference between", 0.8f), Signal("compare attendance", 1f)
        ),
        
        NlpIntent.MONTHLY_REPORT to listOf(
            Signal("monthly", 0.8f), Signal("monthly report", 1f),
            Signal("this month", 0.8f), Signal("last month", 0.8f),
            Signal("30 day", 0.7f), Signal("month summary", 0.9f),
            Signal("monthly summary", 1f)
        ),
        
        NlpIntent.SUBJECT_SKIP_CALC to listOf(
            Signal("skip budget", 1f), Signal("skipbudget", 0.9f),
            Signal("how many can miss", 0.9f), Signal("safe to bunk", 0.9f),
            Signal("how many can bunk", 0.9f), Signal("can afford", 0.8f),
            Signal("how many more", 0.6f), Signal("bunk calculator", 1f),
            Signal("miss calculator", 1f)
        ),
        
        NlpIntent.GET_STREAK to listOf(
            Signal("streak", 0.9f), Signal("consecutive", 0.8f),
            Signal("in a row", 0.9f), Signal("my streak", 1f),
            Signal("current streak", 1f), Signal("longest streak", 0.9f)
        ),
        
        NlpIntent.GET_BEST_SUBJECT to listOf(
            Signal("best subject", 1f), Signal("top subject", 1f),
            Signal("strongest", 0.8f), Signal("highest attendance", 0.9f),
            Signal("which subject best", 1f), Signal("where doing best", 0.9f)
        ),
        
        NlpIntent.GET_WORST_SUBJECT to listOf(
            Signal("worst subject", 1f), Signal("weakest", 0.8f),
            Signal("lowest attendance", 0.9f), Signal("which subject worst", 1f),
            Signal("failing in", 0.8f), Signal("danger zone", 0.7f),
            Signal("critical subject", 0.9f), Signal("at risk", 0.7f)
        ),
        
        NlpIntent.EXAM_MODE_CHECK to listOf(
            Signal("exam", 0.7f), Signal("debarred", 0.9f),
            Signal("eligible", 0.7f), Signal("detained", 0.9f),
            Signal("exam eligible", 1f), Signal("sit exam", 0.9f),
            Signal("allowed exam", 0.9f), Signal("pass semester", 0.7f),
            Signal("debar", 0.9f)
        ),

        // ── Navigation intents ──

        NlpIntent.NAVIGATE_ANALYTICS to listOf(
            Signal("open analytics", 1f), Signal("go to analytics", 1f),
            Signal("analytics screen", 1f), Signal("show analytics page", 1f),
            Signal("statistics page", 1f), Signal("charts page", 1f),
            Signal("open charts", 1f), Signal("view analytics", 1f),
            Signal("navigate to analytics", 1f), Signal("take me to analytics", 1f),
            Signal("show me analytics", 1f),
            Signal("analytics", 0.7f), Signal("statistics", 0.7f),
            Signal("charts", 0.7f)
        ),

        NlpIntent.NAVIGATE_SETTINGS to listOf(
            Signal("open settings", 1f), Signal("go to settings", 1f),
            Signal("settings screen", 1f), Signal("preferences", 1f),
            Signal("account settings", 1f), Signal("profile settings", 1f),
            Signal("app settings", 1f),
            Signal("navigate to settings", 1f), Signal("take me to settings", 1f),
            Signal("show me settings", 1f),
            Signal("settings", 0.7f), Signal("preferences", 0.7f)
        ),

        NlpIntent.NAVIGATE_TIMETABLE_SETUP to listOf(
            Signal("edit timetable", 1f), Signal("setup timetable", 1f),
            Signal("manage timetable", 1f), Signal("change timetable", 1f),
            Signal("timetable setup", 1f), Signal("edit schedule", 1f),
            Signal("modify schedule", 1f), Signal("change schedule", 1f),
            Signal("update timetable", 1f),
            Signal("navigate to timetable setup", 1f), Signal("take me to timetable setup", 1f),
            Signal("open timetable setup", 1f), Signal("show me timetable setup", 1f),
            Signal("timetable", 0.7f), Signal("schedule", 0.7f)
        ),

        NlpIntent.NAVIGATE_FRIENDS to listOf(
            Signal("open friends", 1f), Signal("go to friends", 1f),
            Signal("friend list", 1f), Signal("my friends", 1f),
            Signal("see friends", 1f), Signal("friends page", 1f),
            Signal("friend screen", 1f), Signal("show friends", 1f),
            Signal("navigate to friends", 1f), Signal("take me to friends", 1f),
            Signal("show me friends", 1f),
            Signal("friends", 0.7f)
        ),

        NlpIntent.NAVIGATE_MANAGE_SUBJECTS to listOf(
            Signal("manage subjects", 1f), Signal("edit subjects", 1f),
            Signal("add subject", 1f), Signal("remove subject", 1f),
            Signal("my subjects", 1f), Signal("subject list", 1f),
            Signal("manage classes", 1f), Signal("subjects page", 1f),
            Signal("open subjects", 1f),
            Signal("navigate to subjects", 1f), Signal("take me to subjects", 1f),
            Signal("show me subjects", 1f),
            Signal("subjects", 0.7f)
        ),

        NlpIntent.NAVIGATE_COLLEGE_SYNC to listOf(
            Signal("college sync", 1f), Signal("sync attendance", 1f),
            Signal("sync college", 1f), Signal("open college sync", 1f),
            Signal("portal sync", 1f), Signal("college portal", 1f),
            Signal("sync portal", 1f), Signal("sync data", 1f),
            Signal("navigate to college sync", 1f), Signal("take me to college sync", 1f),
            Signal("show me college sync", 1f),
            Signal("sync", 0.7f), Signal("portal", 0.7f)
        ),

        NlpIntent.NAVIGATE_ADD_ATTENDANCE to listOf(
            Signal("add attendance", 1f), Signal("record attendance", 1f),
            Signal("log attendance", 1f), Signal("enter attendance", 1f),
            Signal("new attendance record", 1f), Signal("add record", 1f),
            Signal("navigate to add attendance", 1f), Signal("take me to add attendance", 1f),
            Signal("open add attendance", 1f), Signal("show me add attendance", 1f),
            Signal("attendance", 0.7f)
        ),

        NlpIntent.NAVIGATE_HOME to listOf(
            Signal("go home", 1f), Signal("main screen", 1f),
            Signal("dashboard", 1f), Signal("go back home", 1f),
            Signal("home screen", 1f), Signal("home page", 1f),
            Signal("main page", 1f), Signal("open home", 1f),
            Signal("navigate to home", 1f), Signal("take me to home", 1f),
            Signal("show me home", 1f),
            Signal("home", 0.7f), Signal("dashboard", 0.7f)
        ),

        NlpIntent.COLLEGE_ATTENDANCE to listOf(
            Signal("clg", 0.9f), Signal("mismatch", 0.9f),
            Signal("college attendance", 1f), Signal("my attendance in clg", 1f),
            Signal("clg system", 0.9f), Signal("in app and not in clg", 1f),
            Signal("in clg and not in app", 1f), Signal("sync data", 0.8f),
            Signal("portal", 0.7f)
        )
    )

    private fun scoreIntent(
        intent: NlpIntent,
        tokens: List<String>,
        stemmed: List<String>,
        allSignals: Set<String>,
        rawLower: String
    ): Float {
        val signals = INTENT_SIGNALS[intent] ?: return 0f
        var score = 0f
        val maxScore = signals.sumOf { it.weight.toDouble() }.toFloat()
        if (maxScore == 0f) return 0f

        for (sig in signals) {
            // Check raw lowercase, bigrams/trigrams, and stemmed
            val matched = rawLower.contains(sig.text) ||
                          sig.text in allSignals ||
                          stemmed.any { sig.text.startsWith(it) || it.startsWith(sig.text) }
            if (matched) score += sig.weight
        }

        return (score / maxScore).coerceIn(0f, 1f)
    }

    /* ═══════════════════ ENTITY EXTRACTION ═══════════════════ */

    private fun extractEntities(lower: String, tokens: List<String>, subjectNames: List<String>, finalIntent: NlpIntent): ExtractedEntities {
        var subjectHint: String? = null
        var subject2: String? = null
        
        if (finalIntent == NlpIntent.COMPARE_SUBJECTS) {
            val matches = mutableListOf<String>()
            for (sn in subjectNames) {
                if (lower.contains(sn.lowercase())) {
                    matches.add(sn)
                } else {
                    val words = sn.lowercase().split(Regex("\\s+"))
                    if (words.any { w -> tokens.any { t -> levenshtein(t, w) <= 2 && w.length > 3 } }) {
                        matches.add(sn)
                    }
                }
            }
            if (matches.isNotEmpty()) subjectHint = matches[0]
            if (matches.size > 1) subject2 = matches.first { it != subjectHint }
        } else {
            // Subject hint: find best fuzzy match
            for (sn in subjectNames) {
                if (lower.contains(sn.lowercase())) {
                    subjectHint = sn; break
                }
            }
            if (subjectHint == null) {
                // Try partial match
                for (sn in subjectNames) {
                    val words = sn.lowercase().split(Regex("\\s+"))
                    if (words.any { w -> tokens.any { t -> levenshtein(t, w) <= 2 && w.length > 3 } }) {
                        subjectHint = sn; break
                    }
                }
            }
        }

        // Date hint
        val dateHint = Regex("\\d{4}-\\d{2}-\\d{2}").find(lower)?.value
            ?: if (lower.contains("today")) "today"
            else if (lower.contains("yesterday")) "yesterday"
            else if (lower.contains("tomorrow")) "tomorrow"
            else null

        // Month hint
        val monthNames = listOf("january" to 0, "february" to 1, "march" to 2, "april" to 3, "may" to 4, "june" to 5, "july" to 6, "august" to 7, "september" to 8, "october" to 9, "november" to 10, "december" to 11,
                                "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "jun" to 5, "jul" to 6, "aug" to 7, "sep" to 8, "sept" to 8, "oct" to 9, "nov" to 10, "dec" to 11)
        var monthHint: Int? = null
        for ((name, index) in monthNames) {
            if (tokens.contains(name)) { monthHint = index; break }
        }

        // Status hint
        val statusHint = when {
            lower.contains("present") -> "present"
            lower.contains("absent") || lower.contains("bunk") || lower.contains("skip") -> "absent"
            else -> null
        }

        // Percentage hint
        val pctWords = mapOf("ten" to 10, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90, "seventy five" to 75, "eighty five" to 85, "ninety five" to 95)
        var pctHint = Regex("(\\d{1,3})\\s*%").find(lower)?.groupValues?.get(1)?.toIntOrNull()
        if (pctHint == null) {
            for ((word, num) in pctWords) {
                if (lower.contains("$word percent") || lower.contains(word)) { pctHint = num; break }
            }
        }

        // Time range hint
        val timeHintMatch = Regex("(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|in the morning|in the evening|in the afternoon)?)\\s*(?:-|to)\\s*(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|in the morning|in the evening|in the afternoon)?)", RegexOption.IGNORE_CASE)
            .find(lower)
        
        var timeHint: String? = timeHintMatch?.value
        
        // Also match single times like "4 pm" or "9 in the morning" if no range matched
        if (timeHint == null) {
            val singleTimeRegex = Regex("(?:at |around |by )?(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|in the morning|in the evening|in the afternoon)|half past \\d{1,2})", RegexOption.IGNORE_CASE)
            timeHint = singleTimeRegex.find(lower)?.groupValues?.getOrNull(1)
        }

        return ExtractedEntities(subjectHint, subject2, dateHint, monthHint, statusHint, pctHint, timeHint)
    }

    /* ═══════════════════ SENTIMENT DETECTION ═══════════════════ */

    private val POSITIVE_WORDS = setOf("good", "great", "awesome", "nice", "happy", "excellent", "love", "amazing", "fantastic", "wonderful", "yay", "cool")
    private val NEGATIVE_WORDS = setOf("bad", "terrible", "awful", "hate", "sad", "worst", "horrible", "poor", "sucks")
    private val FRUSTRATED_WORDS = setOf("frustrated", "annoyed", "angry", "ugh", "argh", "wtf", "stupid", "dumb", "confused", "stuck", "impossible", "cant", "useless")

    private fun detectSentiment(tokens: List<String>, expanded: List<String>): Sentiment {
        val all = (tokens + expanded).toSet()
        val posCount = all.count { it in POSITIVE_WORDS }
        val negCount = all.count { it in NEGATIVE_WORDS }
        val fruCount = all.count { it in FRUSTRATED_WORDS }
        return when {
            fruCount > 0 -> Sentiment.FRUSTRATED
            posCount > negCount -> Sentiment.POSITIVE
            negCount > posCount -> Sentiment.NEGATIVE
            else -> Sentiment.NEUTRAL
        }
    }

    /* ═══════════════════ HELPERS ═══════════════════ */

    private val CONFIRM_YES_SET = setOf("yes", "y", "confirm", "ok", "sure", "yeah", "yep", "do it", "go ahead", "correct", "right")
    private val CONFIRM_NO_SET  = setOf("no", "n", "cancel", "nah", "nope", "stop", "nevermind", "abort", "dont", "don't")

    private fun quick(intent: NlpIntent, confidence: Float, lower: String, subjects: List<String>): NlpResult =
        NlpResult(intent, confidence, extractEntities(lower, tokenize(lower), subjects, intent), Sentiment.NEUTRAL)

    private fun confidenceBoost(bestScore: Float, secondBestScore: Float, bestIntent: NlpIntent, lastIntent: NlpIntent?): Float {
        if (bestScore < 0.25f && (bestScore - secondBestScore) <= 0.05f) {
            if (lastIntent != null && intentGroup(bestIntent) == intentGroup(lastIntent)) {
                return bestScore + 0.1f
            }
        }
        return bestScore
    }

    private fun intentGroup(intent: NlpIntent): Int = when (intent) {
        NlpIntent.SUBJECT_SUMMARY, NlpIntent.ATTENDANCE_FOR_DATE, NlpIntent.OVERALL_ANALYSIS -> 1
        NlpIntent.MARK_ATTENDANCE, NlpIntent.MARK_BULK_ATTENDANCE -> 2
        NlpIntent.DELETE_ATTENDANCE -> 3
        NlpIntent.TIMETABLE, NlpIntent.NEXT_CLASS -> 4
        NlpIntent.PREDICTION, NlpIntent.TREND_ANALYSIS, NlpIntent.PATTERN_ANALYSIS, 
        NlpIntent.COMPARE_SUBJECTS, NlpIntent.MONTHLY_REPORT, NlpIntent.SUBJECT_SKIP_CALC,
        NlpIntent.GET_STREAK, NlpIntent.GET_BEST_SUBJECT, NlpIntent.GET_WORST_SUBJECT,
        NlpIntent.EXAM_MODE_CHECK -> 5
        NlpIntent.STUDY_TIPS, NlpIntent.MOTIVATION, NlpIntent.GOAL_SETTING -> 6
        NlpIntent.NAVIGATE_ANALYTICS, NlpIntent.NAVIGATE_SETTINGS, NlpIntent.NAVIGATE_TIMETABLE_SETUP,
        NlpIntent.NAVIGATE_FRIENDS, NlpIntent.NAVIGATE_MANAGE_SUBJECTS, NlpIntent.NAVIGATE_COLLEGE_SYNC,
        NlpIntent.NAVIGATE_ADD_ATTENDANCE, NlpIntent.NAVIGATE_HOME -> 7
        else -> 0
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
