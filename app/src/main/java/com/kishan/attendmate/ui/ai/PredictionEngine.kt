package com.kishan.attendmate.ui.ai

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * On-device statistical prediction and analytics engine.
 * Uses linear regression, moving averages, and pattern analysis — no API calls.
 */
object PredictionEngine {

    /* ═══════════════════ DATA CLASSES ═══════════════════ */

    data class AttendanceRecord(
        val date: LocalDate,
        val isPresent: Boolean
    )

    data class SubjectPrediction(
        val name: String,
        val currentPct: Int,
        val predictedPct: Int,
        val trend: TrendDirection,
        val riskScore: Int,           // 0-100 (higher = more risk)
        val daysAnalyzed: Int
    )

    data class SubjectTrend(
        val name: String,
        val trend: TrendDirection,
        val weeklyChangePct: Float,   // +3.5 means improving by 3.5% per week
        val currentPct: Int,
        val recentAvgPct: Int         // last 7-day average
    )

    data class DayPattern(
        val dayOfWeek: DayOfWeek,
        val presentRate: Float,       // 0..1
        val totalClasses: Int
    )

    data class PatternAnalysis(
        val subjectName: String,
        val bestDay: DayPattern?,
        val worstDay: DayPattern?,
        val dayPatterns: List<DayPattern>,
        val currentStreak: Int,
        val isPositiveStreak: Boolean,
        val longestStreak: Int
    )

    data class WeeklySummaryData(
        val totalPresent: Int,
        val totalAbsent: Int,
        val attendanceRate: Int,
        val bestDay: String,         // e.g. "Monday (100%)"
        val worstDay: String,        // e.g. "Friday (0%)"
        val currentStreak: Int,
        val isPositiveStreak: Boolean,
        val subjectBreakdown: List<SubjectWeekEntry>,
        val comparedToLastWeek: Int   // +5 means 5% better than last week
    )

    data class SubjectWeekEntry(
        val name: String,
        val present: Int,
        val absent: Int,
        val pct: Int
    )

    enum class TrendDirection {
        IMPROVING, DECLINING, STABLE;

        fun arrow(): String = when (this) {
            IMPROVING -> "↑"
            DECLINING -> "↓"
            STABLE    -> "→"
        }

        fun emoji(): String = when (this) {
            IMPROVING -> "📈"
            DECLINING -> "📉"
            STABLE    -> "➡️"
        }
    }

    /* ═══════════════════ PREDICTION (Linear Regression) ═══════════════════ */

    /**
     * Predict future attendance percentage using Simple Linear Regression
     * on a rolling attendance window.
     *
     * X = day index (0, 1, 2, ...)
     * Y = cumulative attendance rate at that day
     */
    fun predictAttendance(
        records: List<AttendanceRecord>,
        currentPct: Int,
        totalClasses: Int,
        subjectName: String,
        daysAhead: Int = 30
    ): SubjectPrediction {
        if (records.size < 3) {
            return SubjectPrediction(subjectName, currentPct, currentPct, TrendDirection.STABLE, riskScore(currentPct, TrendDirection.STABLE), records.size)
        }

        val sorted = records.sortedBy { it.date }
        // Build cumulative attendance rate series
        var presentCount = 0
        var totalCount = 0
        val dataPoints = sorted.mapIndexed { idx, rec ->
            totalCount++
            if (rec.isPresent) presentCount++
            Pair(idx.toFloat(), (presentCount * 100f / totalCount))
        }

        // Simple Linear Regression
        val n = dataPoints.size
        val sumX  = dataPoints.sumOf { it.first.toDouble() }
        val sumY  = dataPoints.sumOf { it.second.toDouble() }
        val sumXY = dataPoints.sumOf { (it.first * it.second).toDouble() }
        val sumX2 = dataPoints.sumOf { (it.first * it.first).toDouble() }

        val denom = n * sumX2 - sumX * sumX
        val slope = if (denom != 0.0) (n * sumXY - sumX * sumY) / denom else 0.0
        val intercept = (sumY - slope * sumX) / n

        // Predict future
        val futureX = n + daysAhead
        val predictedPct = (slope * futureX + intercept).roundToInt().coerceIn(0, 100)

        val trend = when {
            slope > 0.15  -> TrendDirection.IMPROVING
            slope < -0.15 -> TrendDirection.DECLINING
            else          -> TrendDirection.STABLE
        }

        return SubjectPrediction(
            name = subjectName,
            currentPct = currentPct,
            predictedPct = predictedPct,
            trend = trend,
            riskScore = riskScore(currentPct, trend),
            daysAnalyzed = records.size
        )
    }

    /* ═══════════════════ TREND ANALYSIS (Moving Average) ═══════════════════ */

    fun analyzeTrend(
        records: List<AttendanceRecord>,
        currentPct: Int,
        subjectName: String
    ): SubjectTrend {
        if (records.size < 5) {
            return SubjectTrend(subjectName, TrendDirection.STABLE, 0f, currentPct, currentPct)
        }

        val sorted = records.sortedBy { it.date }
        val recent7 = sorted.takeLast(7)
        val recentRate = (recent7.count { it.isPresent } * 100f / recent7.size).roundToInt()

        // Compare two halves
        val mid = sorted.size / 2
        val firstHalf = sorted.subList(0, mid)
        val secondHalf = sorted.subList(mid, sorted.size)

        val firstRate = if (firstHalf.isNotEmpty()) firstHalf.count { it.isPresent } * 100f / firstHalf.size else 50f
        val secondRate = if (secondHalf.isNotEmpty()) secondHalf.count { it.isPresent } * 100f / secondHalf.size else 50f

        val weeklyChange = secondRate - firstRate

        val trend = when {
            weeklyChange > 5   -> TrendDirection.IMPROVING
            weeklyChange < -5  -> TrendDirection.DECLINING
            else               -> TrendDirection.STABLE
        }

        return SubjectTrend(subjectName, trend, weeklyChange, currentPct, recentRate)
    }

    /* ═══════════════════ PATTERN ANALYSIS ═══════════════════ */

    fun analyzePatterns(
        records: List<AttendanceRecord>,
        subjectName: String
    ): PatternAnalysis {
        // Day-of-week patterns
        val byDay = records.groupBy { it.date.dayOfWeek }
        val dayPatterns = byDay.map { (dow, recs) ->
            val rate = recs.count { it.isPresent }.toFloat() / recs.size
            DayPattern(dow, rate, recs.size)
        }.sortedBy { it.dayOfWeek }

        val bestDay = dayPatterns.filter { it.totalClasses >= 2 }.maxByOrNull { it.presentRate }
        val worstDay = dayPatterns.filter { it.totalClasses >= 2 }.minByOrNull { it.presentRate }

        // Streak analysis
        val sorted = records.sortedByDescending { it.date }
        var streak = 0
        var isPositiveStreak = true
        if (sorted.isNotEmpty()) {
            isPositiveStreak = sorted[0].isPresent
            for (rec in sorted) {
                if (rec.isPresent == isPositiveStreak) streak++ else break
            }
        }

        // Longest streak ever
        val chronological = records.sortedBy { it.date }
        var longestStreak = 0
        var currentRun = 0
        var lastStatus: Boolean? = null
        for (rec in chronological) {
            if (rec.isPresent == lastStatus) {
                currentRun++
            } else {
                currentRun = 1
                lastStatus = rec.isPresent
            }
            if (currentRun > longestStreak && lastStatus == true) longestStreak = currentRun
        }

        return PatternAnalysis(subjectName, bestDay, worstDay, dayPatterns, streak, isPositiveStreak, longestStreak)
    }

    /* ═══════════════════ WEEKLY SUMMARY ═══════════════════ */

    fun weeklySummary(
        allRecords: Map<String, List<AttendanceRecord>>  // subjectName -> records
    ): WeeklySummaryData {
        val today = LocalDate.now()
        val weekAgo = today.minusDays(7)
        val twoWeeksAgo = today.minusDays(14)

        var totalPresent = 0; var totalAbsent = 0
        val dailyMap = mutableMapOf<DayOfWeek, Pair<Int, Int>>() // present, total
        val subjectBreakdown = mutableListOf<SubjectWeekEntry>()

        var lastWeekPresent = 0; var lastWeekTotal = 0

        for ((name, records) in allRecords) {
            val thisWeek = records.filter { it.date.isAfter(weekAgo) && !it.date.isAfter(today) }
            val lastWeek = records.filter { it.date.isAfter(twoWeeksAgo) && !it.date.isAfter(weekAgo) }

            val pres = thisWeek.count { it.isPresent }
            val abs = thisWeek.size - pres
            totalPresent += pres; totalAbsent += abs
            lastWeekPresent += lastWeek.count { it.isPresent }
            lastWeekTotal += lastWeek.size

            if (thisWeek.isNotEmpty()) {
                subjectBreakdown.add(SubjectWeekEntry(name, pres, abs, (pres * 100f / thisWeek.size).roundToInt()))
            }

            for (rec in thisWeek) {
                val cur = dailyMap.getOrDefault(rec.date.dayOfWeek, Pair(0, 0))
                dailyMap[rec.date.dayOfWeek] = Pair(
                    cur.first + if (rec.isPresent) 1 else 0,
                    cur.second + 1
                )
            }
        }

        val totalAll = totalPresent + totalAbsent
        val attendanceRate = if (totalAll > 0) (totalPresent * 100f / totalAll).roundToInt() else 0

        val bestDayEntry = dailyMap.maxByOrNull { if (it.value.second > 0) it.value.first * 100f / it.value.second else 0f }
        val worstDayEntry = dailyMap.minByOrNull { if (it.value.second > 0) it.value.first * 100f / it.value.second else 100f }

        val bestDayStr = if (bestDayEntry != null && bestDayEntry.value.second > 0)
            "${bestDayEntry.key.name.lowercase().replaceFirstChar { it.uppercase() }} (${(bestDayEntry.value.first * 100f / bestDayEntry.value.second).roundToInt()}%)"
        else "—"

        val worstDayStr = if (worstDayEntry != null && worstDayEntry.value.second > 0)
            "${worstDayEntry.key.name.lowercase().replaceFirstChar { it.uppercase() }} (${(worstDayEntry.value.first * 100f / worstDayEntry.value.second).roundToInt()}%)"
        else "—"

        val lastWeekRate = if (lastWeekTotal > 0) (lastWeekPresent * 100f / lastWeekTotal).roundToInt() else 0
        val comparedToLast = attendanceRate - lastWeekRate

        // Streak from all records combined
        val allSorted = allRecords.values.flatten().sortedByDescending { it.date }
        var streak = 0; var isPositive = true
        if (allSorted.isNotEmpty()) {
            isPositive = allSorted[0].isPresent
            for (r in allSorted) {
                if (r.isPresent == isPositive) streak++ else break
            }
        }

        return WeeklySummaryData(
            totalPresent, totalAbsent, attendanceRate,
            bestDayStr, worstDayStr,
            streak, isPositive,
            subjectBreakdown.sortedBy { it.pct },
            comparedToLast
        )
    }

    /* ═══════════════════ RISK SCORE ═══════════════════ */

    private fun riskScore(currentPct: Int, trend: TrendDirection): Int {
        val baseRisk = when {
            currentPct >= 85 -> 10
            currentPct >= 75 -> 30
            currentPct >= 65 -> 55
            currentPct >= 50 -> 75
            else -> 95
        }
        val trendMod = when (trend) {
            TrendDirection.IMPROVING -> -10
            TrendDirection.DECLINING -> +15
            TrendDirection.STABLE    -> 0
        }
        return (baseRisk + trendMod).coerceIn(0, 100)
    }

    /* ═══════════════════ STUDY TIPS GENERATOR ═══════════════════ */

    data class StudyTip(val icon: String, val text: String)

    fun generateStudyTips(
        subjectName: String,
        currentPct: Int,
        trend: TrendDirection,
        worstDay: DayOfWeek? = null,
        currentStreak: Int = 0,
        isPositiveStreak: Boolean = true
    ): List<StudyTip> {
        val tips = mutableListOf<StudyTip>()

        // Universal tips
        tips.add(StudyTip("📅", "Set a recurring alarm 30 minutes before each $subjectName class."))

        // Percentage-based tips
        when {
            currentPct < 50 -> {
                tips.add(StudyTip("🚨", "Critical! Attend every $subjectName class from now — no exceptions."))
                tips.add(StudyTip("🤝", "Find a study buddy in $subjectName to keep you accountable."))
                tips.add(StudyTip("📝", "Sit in the front row — research shows it increases engagement by 35%."))
            }
            currentPct < 65 -> {
                tips.add(StudyTip("⚠️", "You're in the danger zone. Prioritize $subjectName over optional activities."))
                tips.add(StudyTip("📖", "Review yesterday's notes before each class — it makes attending feel more worthwhile."))
                tips.add(StudyTip("🎯", "Set a mini-goal: attend the next 5 $subjectName classes in a row."))
            }
            currentPct < 75 -> {
                tips.add(StudyTip("💪", "You're close to safe! Just a few more classes and you'll be above 75%."))
                tips.add(StudyTip("📊", "Track your daily attendance streak — maintaining streaks is psychologically motivating."))
            }
            currentPct < 85 -> {
                tips.add(StudyTip("✅", "You're safe but could improve. Aim for 85%+ for academic excellence."))
                tips.add(StudyTip("💡", "Use class time to ask questions — active participation boosts retention."))
            }
            else -> {
                tips.add(StudyTip("🌟", "Excellent attendance! Consider helping classmates who struggle."))
                tips.add(StudyTip("🏆", "You're a role model for $subjectName. Keep this outstanding record!"))
            }
        }

        // Trend-based tips
        when (trend) {
            TrendDirection.DECLINING -> {
                tips.add(StudyTip("📉", "Your attendance is trending down. Identify what changed and address it."))
                tips.add(StudyTip("🧠", "Sometimes declining interest means the material is getting harder — consider extra support."))
            }
            TrendDirection.IMPROVING -> {
                tips.add(StudyTip("📈", "Great progress! Your attendance is improving. Keep up this momentum!"))
            }
            TrendDirection.STABLE -> {}
        }

        // Day-specific tip
        if (worstDay != null) {
            val dayName = worstDay.name.lowercase().replaceFirstChar { it.uppercase() }
            tips.add(StudyTip("📌", "You tend to miss ${dayName}s — try scheduling something enjoyable after class that day."))
        }

        // Streak tip
        if (!isPositiveStreak && currentStreak >= 3) {
            tips.add(StudyTip("🔥", "You've missed $currentStreak in a row. Breaking this streak tomorrow is your #1 priority."))
        } else if (isPositiveStreak && currentStreak >= 5) {
            tips.add(StudyTip("🔥", "Amazing $currentStreak-class streak! Don't break it — each class adds to your momentum."))
        }

        return tips.take(6) // Cap at 6 most relevant tips
    }

    /* ═══════════════════ MOTIVATION GENERATOR ═══════════════════ */

    fun generateMotivation(
        overallPct: Int,
        bestSubject: String,
        bestPct: Int,
        worstSubject: String,
        worstPct: Int,
        currentStreak: Int,
        isPositiveStreak: Boolean,
        atRiskCount: Int,
        sentiment: NlpEngine.Sentiment
    ): String {
        val sb = StringBuilder()

        // Opening based on sentiment
        when (sentiment) {
            NlpEngine.Sentiment.FRUSTRATED -> {
                sb.append("💙 I understand it can feel tough. Let's look at this together.\n\n")
            }
            NlpEngine.Sentiment.NEGATIVE -> {
                sb.append("🌟 Hey, everyone has rough patches. Let me show you the bright side.\n\n")
            }
            else -> {
                sb.append("💪 Let's check in on your journey!\n\n")
            }
        }

        // Highlight achievements
        if (bestPct >= 85) {
            sb.append("🏆 You're a champion in **$bestSubject** with **$bestPct%**! That's outstanding.\n\n")
        } else if (bestPct >= 75) {
            sb.append("✅ **$bestSubject** is your strongest at **$bestPct%** — you're keeping it safe.\n\n")
        }

        // Streak motivation
        if (isPositiveStreak && currentStreak >= 3) {
            sb.append("🔥 You're on a **$currentStreak-class streak**! Every class you attend makes the next one easier.\n\n")
        } else if (!isPositiveStreak && currentStreak >= 2) {
            sb.append("💫 You've had **$currentStreak** absences recently, but today could be the start of your comeback streak.\n\n")
        }

        // Overall encouragement
        when {
            overallPct >= 85 -> sb.append("🎯 With **$overallPct%** overall, you're performing exceptionally. Keep being consistent!\n\n")
            overallPct >= 75 -> sb.append("📊 You're at **$overallPct%** overall — you're in the safe zone. A little push and you could be at 85%!\n\n")
            overallPct >= 60 -> sb.append("⚡ At **$overallPct%**, you're not far from 75%. A few consecutive days of attendance can change everything.\n\n")
            else -> sb.append("💪 At **$overallPct%**, every single class you attend from now is progress. Start with today.\n\n")
        }

        // Actionable next step
        if (atRiskCount > 0 && worstSubject.isNotEmpty()) {
            sb.append("📌 **Your next win**: Attend the next **$worstSubject** class. Taking action beats overthinking.")
        } else {
            sb.append("📌 **Keep going**: Consistency beats perfection. Just show up!")
        }

        return sb.toString()
    }
}
