package com.kishan.attendmate.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.kishan.attendmate.MainActivity
import java.util.Locale

// ---------------------------------------------------------------------------
// Refresh callback
// ---------------------------------------------------------------------------

class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(
            context: Context,
            glanceId: GlanceId,
            parameters: ActionParameters
    ) {
        WidgetSyncScheduler.triggerManualUpdate(context)
    }
}

// ---------------------------------------------------------------------------
// Widget
// ---------------------------------------------------------------------------

class TotalAttendanceWidget : GlanceAppWidget() {

    companion object {
        // DataStore keys
        val TOTAL_CLASSES_KEY = intPreferencesKey("total_classes")
        val ATTENDED_CLASSES_KEY = intPreferencesKey("attended_classes")
        val INSIGHT_MSG_KEY = stringPreferencesKey("insight_msg")
        val IS_ON_TRACK_KEY = booleanPreferencesKey("is_on_track")

        // ------------------------------------------------------------------
        // Design tokens  — dark glass palette
        // ------------------------------------------------------------------
        private val Surface = Color(0xFF151519) // widget card bg
        private val Teal = Color(0xFF2DD4BF) // accent (on-track)
        private val TealGlow = Color(0x182DD4BF) // ~10% teal for pill bg
        private val TealBorder = Color(0x382DD4BF) // pill border
        private val Red = Color(0xFFFF453A) // at-risk accent
        private val RedGlow = Color(0x18FF453A)
        private val RedBorder = Color(0x38FF453A)
        private val White85 = Color(0xD9FFFFFF) // primary text
        private val White55 = Color(0x8CFFFFFF) // secondary text
        private val White35 = Color(0x59FFFFFF) // tertiary / labels
        private val White25 = Color(0x40FFFFFF) // very muted
        private val Track = Color(0x12FFFFFF) // progress track bg
        private val TickLine = Color(0x66FFD060) // 75% tick: amber

        // Size breakpoints
        val SMALL_SIZE = DpSize(110.dp, 40.dp)
        val MEDIUM_SIZE = DpSize(110.dp, 110.dp)
        val LARGE_SIZE = DpSize(200.dp, 200.dp)
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun accent(total: Int, pct: Float) = if (total == 0 || pct < 75f) Red else Teal
    private fun accentGlow(total: Int, pct: Float) =
            if (total == 0 || pct < 75f) RedGlow else TealGlow
    private fun accentBorder(total: Int, pct: Float) =
            if (total == 0 || pct < 75f) RedBorder else TealBorder
    private fun statusLabel(total: Int, pct: Float) =
            when {
                total == 0 -> "No Data"
                pct >= 75f -> "On Track"
                else -> "At Risk"
            }
    private fun canMiss(total: Int, attended: Int): Int {
        if (total == 0) return 0
        return maxOf(0, attended - (0.75f * total).toInt())
    }

    // ------------------------------------------------------------------
    // provideGlance
    // ------------------------------------------------------------------

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val total = prefs[TOTAL_CLASSES_KEY] ?: 0
            val attended = prefs[ATTENDED_CLASSES_KEY] ?: 0
            val insight = prefs[INSIGHT_MSG_KEY] ?: "Tap to open AttendMate"

            val pct = if (total > 0) attended.toFloat() / total * 100f else 0f
            val pctFmt = String.format(Locale.getDefault(), "%.1f%%", pct)
            val missed = total - attended
            val can = canMiss(total, attended)

            GlanceTheme {
                val size = LocalSize.current
                when {
                    size.height < 110.dp -> SmallWidget(total, attended, pctFmt, pct)
                    size.height < 200.dp || size.width < 200.dp ->
                            MediumWidget(total, attended, pct, pctFmt, insight)
                    else -> LargeWidget(total, attended, pct, pctFmt, missed, can, insight)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Shared container — dark surface + tap-to-open
    // ------------------------------------------------------------------

    @Composable
    private fun WidgetRoot(radius: Int = 26, content: @Composable ColumnScope.() -> Unit) {
        val ctx = LocalContext.current
        Box(
                modifier =
                        GlanceModifier.fillMaxSize()
                                .background(ColorProvider(Surface))
                                .cornerRadius(radius.dp)
                                .clickable(
                                        actionStartActivity(
                                                Intent(ctx, MainActivity::class.java).apply {
                                                    flags =
                                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                }
                                        )
                                ),
                contentAlignment = Alignment.TopStart
        ) {
            Column(
                    modifier =
                            GlanceModifier.fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                    content = content
            )
        }
    }

    // ==================================================================
    //  SMALL  —  "Attendance | 87/102   [dot]  85.3%"
    // ==================================================================

    // ==================================================================
    //  SMALL  —  Dot + Attendance | 87/102   |  85.3% + Mini Bar
    // ==================================================================

    @Composable
    private fun SmallWidget(total: Int, attended: Int, pctFmt: String, pct: Float) {
        val ac = accent(total, pct)
        // 32.dp accounts for the 16.dp horizontal padding on both sides
        val barW = LocalSize.current.width - 32.dp
        val fill = barW * (pct / 100f).coerceIn(0f, 1f)

        Box(
                modifier =
                        GlanceModifier.fillMaxSize()
                                .background(ColorProvider(Surface))
                                .cornerRadius(20.dp),
                contentAlignment = Alignment.Center
        ) {
            Column(
                    modifier =
                            GlanceModifier.fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // Top Content area
                Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Label + Dot + Count
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Premium Status Dot
                            Box(
                                    modifier =
                                            GlanceModifier.size(6.dp)
                                                    .background(ColorProvider(ac))
                                                    .cornerRadius(3.dp)
                            ) {}
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Text(
                                    text = "Attendance",
                                    style =
                                            TextStyle(
                                                    color = ColorProvider(White55),
                                                    fontWeight = FontWeight.Medium
                                            )
                            )
                        }
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Text(
                                text = "$attended / $total",
                                style =
                                        TextStyle(
                                                color = ColorProvider(White85),
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                        )
                        )
                    }
                    // Right: Huge Percentage
                    Text(
                            text = pctFmt,
                            style =
                                    TextStyle(
                                            color = ColorProvider(ac),
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                    )
                    )
                }

                // Bottom: Mini Progress Bar
                Spacer(modifier = GlanceModifier.height(6.dp))
                Row(
                        modifier =
                                GlanceModifier.fillMaxWidth()
                                        .height(4.dp)
                                        .background(ColorProvider(Track))
                                        .cornerRadius(100.dp)
                ) {
                    if (fill.value > 0f)
                            Box(
                                    modifier =
                                            GlanceModifier.width(fill)
                                                    .fillMaxHeight()
                                                    .background(ColorProvider(ac))
                                                    .cornerRadius(100.dp)
                            ) {}
                    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {}
                }
            }
        }
    }

    // ==================================================================
    //  MEDIUM  —  title + pill | big % | slim bar | insight + fraction
    // ==================================================================

    // ==================================================================
    //  MEDIUM  —  Title | Big % + Pill |  Insight + Fraction | Bar
    // ==================================================================

    @Composable
    private fun MediumWidget(
            total: Int,
            attended: Int,
            pct: Float,
            pctFmt: String,
            insight: String
    ) {
        val ac = accent(total, pct)
        val barW = LocalSize.current.width - 32.dp
        val fill = barW * (pct / 100f).coerceIn(0f, 1f)

        WidgetRoot(radius = 24) {
            // Row 1 — Title + Refresh
            Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = "Total Attendance",
                        style =
                                TextStyle(
                                        color = ColorProvider(White55),
                                        fontWeight = FontWeight.Medium
                                ),
                        modifier = GlanceModifier.defaultWeight()
                )
                Image(
                        provider = ImageProvider(android.R.drawable.ic_popup_sync),
                        contentDescription = "Refresh",
                        modifier =
                                GlanceModifier.size(14.dp)
                                        .clickable(actionRunCallback<RefreshActionCallback>())
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Row 2 — Big Percentage with Status Pill inline
            Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = pctFmt,
                        style =
                                TextStyle(
                                        color = ColorProvider(ac),
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                )
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                StatusPill(total, pct)
            }

            // Flexible spacer to push the bottom elements down to the edge of the card
            Spacer(modifier = GlanceModifier.defaultWeight())

            // Row 3 — Insight on left, Fraction on right
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                        text = insight,
                        style = TextStyle(color = ColorProvider(White35)),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                        text = "$attended / $total",
                        style =
                                TextStyle(
                                        color = ColorProvider(White85),
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Row 4 — Thicker, cleaner progress bar
            Row(
                    modifier =
                            GlanceModifier.fillMaxWidth()
                                    .height(5.dp)
                                    .background(ColorProvider(Track))
                                    .cornerRadius(100.dp)
            ) {
                if (fill.value > 0f)
                        Box(
                                modifier =
                                        GlanceModifier.width(fill)
                                                .fillMaxHeight()
                                                .background(ColorProvider(ac))
                                                .cornerRadius(100.dp)
                        ) {}
                Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {}
            }
        }
    }

    // ==================================================================
    //  LARGE  — full dashboard: big % | pill+fraction | arc+stats | bar | insight
    // ==================================================================

    @Composable
    private fun LargeWidget(
            total: Int,
            attended: Int,
            pct: Float,
            pctFmt: String,
            missed: Int,
            canMissMore: Int,
            insight: String
    ) {
        val ac = accent(total, pct)
        val totalW = LocalSize.current.width - 32.dp
        val fill = totalW * (pct / 100f).coerceIn(0f, 1f)
        val tickPos = totalW * 0.75f

        WidgetRoot(radius = 28) {
            // Row 1 — title + refresh
            Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = "Total Attendance",
                        style =
                                TextStyle(
                                        color = ColorProvider(White35),
                                        fontWeight = FontWeight.Medium
                                ),
                        modifier = GlanceModifier.defaultWeight()
                )
                Image(
                        provider = ImageProvider(android.R.drawable.ic_popup_sync),
                        contentDescription = "Refresh",
                        modifier =
                                GlanceModifier.size(14.dp)
                                        .clickable(actionRunCallback<RefreshActionCallback>())
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Row 2 — giant percentage
            Text(
                    text = pctFmt,
                    style =
                            TextStyle(
                                    color = ColorProvider(ac),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                            )
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Row 3 — pill + fraction
            Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(total, pct)
                Spacer(modifier = GlanceModifier.width(10.dp))
                Text(
                        text = "$attended / $total classes",
                        style =
                                TextStyle(
                                        color = ColorProvider(White55),
                                        fontFamily = FontFamily.Monospace
                                )
                )
            }

            Spacer(modifier = GlanceModifier.height(16.dp))

            // Row 4 — 2-column stat grid (Attended | Missed | Target | Can miss)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    StatCell("Attended", "$attended")
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    StatCell("Target", "75.0%")
                }
                Spacer(modifier = GlanceModifier.width(16.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    StatCell("Missed", "$missed")
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    StatCell(
                            label = "Can miss",
                            value = "$canMissMore more",
                            valueColor = if (canMissMore > 5) ac else Red
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(16.dp))

            // Row 5 — thick bar with tick at 75%
            Box(modifier = GlanceModifier.fillMaxWidth().height(7.dp)) {
                // Track
                Box(
                        modifier =
                                GlanceModifier.fillMaxSize()
                                        .background(ColorProvider(Track))
                                        .cornerRadius(100.dp)
                ) {}
                // Fill
                Row(modifier = GlanceModifier.fillMaxSize()) {
                    if (fill.value > 0f)
                            Box(
                                    modifier =
                                            GlanceModifier.width(fill)
                                                    .fillMaxHeight()
                                                    .background(ColorProvider(ac))
                                                    .cornerRadius(100.dp)
                            ) {}
                    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {}
                }
                // 75% tick
                Row(modifier = GlanceModifier.fillMaxSize()) {
                    Box(modifier = GlanceModifier.width(tickPos)) {}
                    Box(
                            modifier =
                                    GlanceModifier.width(1.5.dp)
                                            .fillMaxHeight()
                                            .background(ColorProvider(TickLine))
                    ) {}
                    Box(modifier = GlanceModifier.defaultWeight()) {}
                }
            }
            // Tick label "75% target"
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Box(modifier = GlanceModifier.width(tickPos - 14.dp)) {}
                Text(
                        text = "75% target",
                        style =
                                TextStyle(
                                        color = ColorProvider(TickLine),
                                        fontWeight = FontWeight.Medium
                                )
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Row 6 — insight
            Text(
                    text = insight,
                    style = TextStyle(color = ColorProvider(White25)),
                    maxLines = 2
            )
        }
    }

    // ------------------------------------------------------------------
    // Shared composables
    // ------------------------------------------------------------------

    /**
     * Two-line cell: small ALL-CAPS label + bold monospace value. Used in the 2-column stat grid of
     * the Large widget.
     */
    @Composable
    private fun StatCell(label: String, value: String, valueColor: Color = White85) {
        Column {
            Text(
                    text = label.uppercase(),
                    style =
                            TextStyle(
                                    color = ColorProvider(White25),
                                    fontWeight = FontWeight.Medium
                            )
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                    text = value,
                    style =
                            TextStyle(
                                    color = ColorProvider(valueColor),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                            )
            )
        }
    }

    /**
     * Rounded pill badge — "On Track" (teal) / "At Risk" (red) / "No Data" (gray). Background is
     * accent colour at ~10% opacity to stay subtle on the dark surface.
     */
    @Composable
    private fun StatusPill(total: Int, pct: Float) {
        val ac = accent(total, pct)
        val label = statusLabel(total, pct)
        Box(
                modifier =
                        GlanceModifier.background(ColorProvider(accentGlow(total, pct)))
                                .cornerRadius(100.dp)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                    text = label,
                    style =
                            TextStyle(
                                    color = ColorProvider(ac),
                                    fontWeight = FontWeight.Bold
                            )
            )
        }
    }
}
