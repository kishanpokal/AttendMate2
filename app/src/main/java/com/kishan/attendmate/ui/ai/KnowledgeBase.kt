package com.kishan.attendmate.ui.ai

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * On-device knowledge base for attendance-related Q&A.
 * Uses TF-IDF-like scoring to match user queries to the best answer.
 * No external API — fully offline.
 */
object KnowledgeBase {

    data class QaEntry(
        val keywords: List<String>,   // terms for matching
        val question: String,         // human-readable question
        val answer: String            // detailed answer
    )

    private val ENTRIES = listOf(
        QaEntry(
            listOf("why", "important", "attendance", "matter"),
            "Why is attendance important?",
            "📚 **Why Attendance Matters**\n\n" +
            "• Most universities require **75% minimum** attendance to be eligible for exams.\n" +
            "• Regular attendance improves understanding — studies show students who attend 90%+ score **15-20% higher** on average.\n" +
            "• It builds discipline and preparation habits that benefit your career.\n" +
            "• Many companies check college attendance records during placements."
        ),
        QaEntry(
            listOf("75", "percent", "rule", "minimum", "requirement", "threshold"),
            "What is the 75% attendance rule?",
            "📏 **The 75% Rule**\n\n" +
            "• Most Indian universities mandate a **minimum of 75%** attendance in each subject.\n" +
            "• Falling below 75% can result in:\n  — Being **debarred from exams**\n  — **Losing grades** or getting detained\n  — Extra assignments or re-enrollment\n" +
            "• Some institutions allow medical exemptions with valid documentation."
        ),
        QaEntry(
            listOf("what", "happens", "low", "below", "less", "drop", "consequence"),
            "What happens if attendance drops below threshold?",
            "⚠️ **Consequences of Low Attendance**\n\n" +
            "• **Exam debarment** — You may not be allowed to sit for exams.\n" +
            "• **Grade penalty** — Some colleges deduct internal marks.\n" +
            "• **Detention** — In severe cases, you may need to repeat the year.\n" +
            "• **Placement issues** — Companies see attendance as a reliability indicator.\n\n" +
            "💡 *Use the **prediction** feature here to forecast your attendance and plan ahead!*"
        ),
        QaEntry(
            listOf("how", "improve", "increase", "raise", "better", "boost"),
            "How can I improve my attendance?",
            "📈 **Tips to Improve Attendance**\n\n" +
            "1. **Set daily alarms** 30 mins before your first class.\n" +
            "2. **Sit in the front row** — it increases engagement significantly.\n" +
            "3. **Find a study buddy** — peer accountability works.\n" +
            "4. **Track your streaks** — use this app to see your streak and keep it going.\n" +
            "5. **Plan your leaves** — use the what-if calculator before bunking.\n" +
            "6. **Review notes before class** — it makes attending feel worthwhile.\n" +
            "7. **Reward yourself** — set milestone rewards for attendance goals."
        ),
        QaEntry(
            listOf("bunk", "safe", "miss", "skip", "safely"),
            "Is it safe to bunk/miss a class?",
            "🤔 **Should You Bunk?**\n\n" +
            "• Use the **\"How many can I miss?\"** command to check your exact safe-miss count.\n" +
            "• General rule: If you're above **80%**, you usually have some buffer.\n" +
            "• Below 75%? Every class counts — **don't risk it**.\n" +
            "• Between 75-80%? You have very little margin. Think carefully.\n\n" +
            "💡 *Ask me: \"How many can I miss in [Subject]?\" for a personalized calculation.*"
        ),
        QaEntry(
            listOf("streak", "maintain", "consecutive", "row"),
            "How do streaks work?",
            "🔥 **Attendance Streaks**\n\n" +
            "• A streak counts **consecutive present classes** (or consecutive absences).\n" +
            "• Maintaining a streak is psychologically motivating — it creates positive momentum.\n" +
            "• Research shows that **habits form after ~21 consecutive days** of repetition.\n" +
            "• Try to maintain at least a **5-class streak** in each subject.\n\n" +
            "💡 *Ask me: \"Show my trend\" or \"Attendance pattern\" to see your streaks!*"
        ),
        QaEntry(
            listOf("semester", "end", "pass", "fail", "exam", "eligibility"),
            "Will I pass the semester attendance requirement?",
            "🎓 **Semester Attendance Check**\n\n" +
            "• Use the **\"Predict my attendance\"** command — I'll use your data to forecast your end-of-semester percentage.\n" +
            "• The prediction uses **linear regression** on your actual attendance history.\n" +
            "• I'll also show you which subjects are at risk and how many more classes you need.\n\n" +
            "💡 *Try: \"Predict my attendance\" for a full forecast report!*"
        ),
        QaEntry(
            listOf("medical", "leave", "sick", "absent", "exemption", "certificate"),
            "Does medical leave count as attendance?",
            "🏥 **Medical Leave & Attendance**\n\n" +
            "• Most colleges accept medical certificates to **condone absences**.\n" +
            "• You typically need a **valid medical certificate** from a registered doctor.\n" +
            "• The leave must usually be **applied within 7 days** of returning.\n" +
            "• Check your college's specific policy — rules vary by institution.\n\n" +
            "💡 *In this app, medical leaves are counted as absences. Factor them into your planning.*"
        ),
        QaEntry(
            listOf("internal", "marks", "assessment", "grade", "affect"),
            "Does attendance affect internal marks?",
            "📝 **Attendance & Internal Assessment**\n\n" +
            "• Many colleges allocate **5-15 marks** of internal assessment based on attendance.\n" +
            "• Typical grade scale:\n  — 90%+ attendance → Full marks\n  — 80-90% → 80% of marks\n  — 75-80% → 60% of marks\n  — Below 75% → Zero or debarment\n" +
            "• These marks directly affect your GPA — don't underestimate them!"
        ),
        QaEntry(
            listOf("placement", "company", "interview", "job", "career"),
            "Does attendance matter for placements?",
            "💼 **Attendance & Placements**\n\n" +
            "• Many companies check attendance records as a **reliability indicator**.\n" +
            "• Companies like TCS, Infosys, and Wipro have **minimum attendance criteria** for placement eligibility.\n" +
            "• Good attendance → Better internal assessment marks → Higher GPA → Better placements.\n" +
            "• It demonstrates **discipline and commitment** — qualities employers value."
        ),
        QaEntry(
            listOf("app", "attendmate", "use", "work", "feature"),
            "How does AttendMate work?",
            "📱 **About AttendMate**\n\n" +
            "AttendMate is your intelligent attendance companion that:\n" +
            "• 📊 Tracks attendance across all subjects with detailed stats\n" +
            "• 🤖 Uses on-device AI for smart insights and predictions\n" +
            "• 📈 Predicts your end-of-semester attendance percentage\n" +
            "• 💡 Provides personalized study tips and motivation\n" +
            "• 🔮 Calculates safe-to-miss counts per subject\n" +
            "• 🗓️ Shows your timetable and upcoming classes\n" +
            "• 📉 Detects attendance trends and patterns\n" +
            "• 🎯 Helps you set and track attendance goals\n\n" +
            "All AI features run **100% offline** — no internet needed!"
        ),
        QaEntry(
            listOf("calculate", "formula", "how", "computed", "math", "work"),
            "How is attendance percentage calculated?",
            "🔢 **Attendance Calculation**\n\n" +
            "```\nAttendance % = (Classes Attended / Total Classes) × 100\n```\n\n" +
            "• Each lecture slot counts as **one class**.\n" +
            "• Both present and absent count toward total.\n" +
            "• The what-if calculator uses: `(attended ÷ total) ≥ 0.75` to check safety.\n" +
            "• Safe misses = `floor((attended - 0.75 × total) / 0.75)`"
        )
    )

    /**
     * Find the best matching answer for a given user query using TF-IDF-like scoring.
     */
    fun findBestAnswer(query: String): Pair<String, Float>? {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return null

        // Compute IDF: how rare each keyword is across all entries
        val totalDocs = ENTRIES.size
        val idf = mutableMapOf<String, Float>()
        val allKeywords = ENTRIES.flatMap { it.keywords }.toSet()
        for (kw in allKeywords) {
            val docsContaining = ENTRIES.count { entry -> entry.keywords.contains(kw) }
            idf[kw] = ln((totalDocs + 1f) / (docsContaining + 1f)) + 1f
        }

        // Score each entry
        var bestScore = 0f
        var bestEntry: QaEntry? = null

        for (entry in ENTRIES) {
            var score = 0f
            for (qt in queryTokens) {
                for (kw in entry.keywords) {
                    val matchScore = when {
                        qt == kw            -> 1.0f             // exact match
                        qt.startsWith(kw) || kw.startsWith(qt) -> 0.7f  // prefix match
                        levenshtein(qt, kw) <= 2 && kw.length > 3 -> 0.5f // fuzzy match
                        else -> 0f
                    }
                    if (matchScore > 0) {
                        score += matchScore * (idf[kw] ?: 1f)
                    }
                }
            }
            // Normalize by query size to avoid bias toward longer queries
            score /= queryTokens.size

            if (score > bestScore) {
                bestScore = score; bestEntry = entry
            }
        }

        val threshold = 0.4f
        return if (bestScore >= threshold && bestEntry != null) {
            Pair(bestEntry.answer, bestScore)
        } else null
    }

    /* ═══════════════════ HELPERS ═══════════════════ */

    private val STOP_WORDS = setOf("a", "an", "the", "is", "are", "i", "my", "me", "do", "does",
        "can", "will", "to", "of", "in", "for", "and", "or", "it", "what", "why", "how", "when")

    private fun tokenize(text: String): List<String> =
        text.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in STOP_WORDS }

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
