package com.kishan.attendmate.ui.ai

/**
 * Tracks multi-turn conversation context for follow-up resolution.
 * Entirely in-memory — no persistence needed.
 */
class ConversationContext {

    data class Turn(
        val userText: String,
        val intent: NlpEngine.NlpIntent,
        val subjectHint: String? = null,
        val dateHint: String? = null,
        val sentiment: NlpEngine.Sentiment = NlpEngine.Sentiment.NEUTRAL
    )

    private val history = mutableListOf<Turn>()
    private val maxHistory = 12

    val lastIntent: NlpEngine.NlpIntent?
        get() = history.lastOrNull()?.intent

    val lastSubject: String?
        get() = history.lastOrNull { it.subjectHint != null }?.subjectHint

    val lastDate: String?
        get() = history.lastOrNull { it.dateHint != null }?.dateHint

    val recentSentiment: NlpEngine.Sentiment
        get() = history.takeLast(3)
            .groupBy { it.sentiment }
            .maxByOrNull { it.value.size }
            ?.key ?: NlpEngine.Sentiment.NEUTRAL

    val turnCount: Int get() = history.size

    fun record(turn: Turn) {
        history.add(turn)
        if (history.size > maxHistory) history.removeFirst()
    }

    /**
     * Resolve a follow-up query using context.
     * e.g. "What about Math?" after "Show attendance" → fills in missing subject
     */
    fun resolveFollowUp(entities: NlpEngine.ExtractedEntities): NlpEngine.ExtractedEntities {
        return entities.copy(
            subjectHint = entities.subjectHint ?: lastSubject,
            dateHint    = entities.dateHint ?: lastDate
        )
    }

    /**
     * Check if the current query looks like a follow-up (short, reference-y).
     */
    fun isLikelyFollowUp(input: String): Boolean {
        if (history.isEmpty()) return false
        val lower = input.lowercase().trim()
        val words = lower.split(Regex("\\s+"))
        // Very short queries or ones starting with "what about", "and", "how about" etc.
        return words.size <= 4 ||
               lower.startsWith("what about") ||
               lower.startsWith("how about") ||
               lower.startsWith("and ") ||
               lower.startsWith("also ") ||
               lower.startsWith("same for") ||
               lower == "it" || lower == "that" || lower == "this"
    }

    fun clear() = history.clear()
}
