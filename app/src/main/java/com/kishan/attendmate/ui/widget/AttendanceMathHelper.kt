package com.kishan.attendmate.ui.widget

object AttendanceMathHelper {

    sealed class MathInsight {
        object NoData : MathInsight()
        data class OnTrack(val canSkip: Int) : MathInsight()
        data class NeedsAttention(val mustAttendNext: Int) : MathInsight()
    }

    /**
     * Calculates actionable attendance insights based on a 75% criteria.
     * If attendance is < 75%: How many consecutive classes to attend to reach exactly 75%.
     * If attendance is >= 75%: How many classes can be skipped before dropping below 75%.
     */
    fun getInsight(total: Int, attended: Int): MathInsight {
        if (total == 0) {
            return MathInsight.NoData
        }

        val percentage = (attended.toFloat() / total) * 100

        return if (percentage >= 75.0f) {
            val skippable = Math.floor((4.0 * attended - 3.0 * total) / 3.0).toInt()
            MathInsight.OnTrack(skippable)
        } else {
            val required = Math.max(0, 3 * total - 4 * attended)
            MathInsight.NeedsAttention(required)
        }
    }
}
