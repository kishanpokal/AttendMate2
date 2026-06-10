package com.kishan.attendmate.ui.ai

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.ui.analytics.AnalyticsActivity
import com.kishan.attendmate.ui.settings.SettingsActivity
import com.kishan.attendmate.ui.settings.CollegeSyncActivity
import com.kishan.attendmate.ui.timetable.setup.TimetableSetupActivity
import com.kishan.attendmate.ui.friends.FriendsActivity
import com.kishan.attendmate.ui.subjects.ManageSubjectsActivity
import com.kishan.attendmate.ui.attendance.AddAttendanceActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.kishan.attendmate.ui.theme.AttendMateTheme
import com.kishan.attendmate.ui.theme.SuccessColor
import com.kishan.attendmate.ui.theme.WarningColor
import com.kishan.attendmate.ui.theme.DangerColor
import com.kishan.attendmate.ui.theme.SpaceXXS
import com.kishan.attendmate.ui.theme.SpaceXS
import com.kishan.attendmate.ui.theme.SpaceSM
import com.kishan.attendmate.ui.theme.SpaceMD
import com.kishan.attendmate.ui.theme.SpaceLG
import com.kishan.attendmate.ui.theme.RadiusSM
import com.kishan.attendmate.ui.theme.RadiusMD
import com.kishan.attendmate.ui.theme.RadiusLG
import com.kishan.attendmate.ui.theme.RadiusXL
import com.kishan.attendmate.ui.theme.ElevationLow
import com.kishan.attendmate.ui.theme.CardStyle
import kotlinx.coroutines.delay
import kotlin.math.ceil

class AiChatActivity : ComponentActivity() {

    private val viewModel: AiChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            viewModel.navigationEvent.collect { target ->
                val navIntent = when (target) {
                    is NavigationTarget.Home -> Intent(this@AiChatActivity, MainActivity::class.java)
                    is NavigationTarget.Analytics -> Intent(this@AiChatActivity, AnalyticsActivity::class.java)
                    is NavigationTarget.Settings -> Intent(this@AiChatActivity, SettingsActivity::class.java)
                    is NavigationTarget.TimetableSetup -> Intent(this@AiChatActivity, TimetableSetupActivity::class.java)
                    is NavigationTarget.Friends -> Intent(this@AiChatActivity, FriendsActivity::class.java)
                    is NavigationTarget.ManageSubjects -> Intent(this@AiChatActivity, ManageSubjectsActivity::class.java)
                    is NavigationTarget.CollegeSync -> Intent(this@AiChatActivity, CollegeSyncActivity::class.java)
                    is NavigationTarget.AddAttendance -> Intent(this@AiChatActivity, AddAttendanceActivity::class.java)
                }
                startActivity(navIntent)
            }
        }

        setContent {
            AttendMateTheme {
                AiChatScreen(viewModel = viewModel, onNavigateBack = { finish() })
            }
        }
    }
}

/* ──────────────────────────────────────────
   Root Screen
────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(viewModel: AiChatViewModel, onNavigateBack: () -> Unit) {
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()
    val messages    = viewModel.messages
    val suggestions by viewModel.currentSuggestions.collectAsStateWithLifecycle()
    val auth        = FirebaseAuth.getInstance()
    val context     = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    val listState  = rememberLazyListState()

    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, uiState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size + 1)
        }
    }

    // Haptic on new bot reply
    val prevSize = remember { mutableIntStateOf(0) }
    LaunchedEffect(messages.size) {
        if (messages.size > prevSize.intValue && messages.isNotEmpty() && !messages.last().isUser) {
            triggerHaptic(context, HapticType.SOFT)
        }
        prevSize.intValue = messages.size
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Premium Header ──────────────────────────────────────
            PremiumChatHeader(
                onNavigateBack = onNavigateBack
            )

            // ── Messages ────────────────────────────────────────────
            LazyColumn(
                state    = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp) // increased spacing for breathing room
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                itemsIndexed(messages) { index, message ->
                    val shouldAnimate = !message.isUser && index == messages.lastIndex &&
                            uiState == AiChatUiState.Success

                    AnimatedVisibility(
                        visible = true,
                        enter   = slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f)
                        ) + fadeIn(tween(400)),
                        modifier = Modifier.animateItem() // smooth item repositioning
                    ) {
                        MessageBubble(
                            message       = message,
                            shouldAnimate = shouldAnimate,
                            onConfirm     = {
                                val uid = auth.currentUser?.uid ?: return@MessageBubble
                                triggerHaptic(context, HapticType.MEDIUM)
                                viewModel.confirmPendingAction(uid)
                            },
                            onCancel      = {
                                triggerHaptic(context, HapticType.SOFT)
                                viewModel.cancelPendingAction()
                            }
                        )
                    }
                }

                // Typing Indicator
                if (uiState is AiChatUiState.Loading) {
                    item { TypingIndicator() }
                }

                // Error
                if (uiState is AiChatUiState.Error) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Text(
                                text     = (uiState as AiChatUiState.Error).message,
                                color    = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                                style    = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // ── Smart Suggestion Chips ─────────────────────────────
            if (uiState !is AiChatUiState.Loading) {
                SmartSuggestionRow(
                    suggestions = suggestions,
                    onSuggestion = {
                        triggerHaptic(context, HapticType.SOFT)
                        viewModel.sendMessage(it)
                    }
                )
            }

            // ── Input Bar ────────────────────────────
            ModernInputBar(
                inputText  = inputText,
                isLoading  = uiState is AiChatUiState.Loading,
                onTextChange = { inputText = it },
                onSend     = {
                    if (inputText.isNotBlank() && uiState !is AiChatUiState.Loading) {
                        triggerHaptic(context, HapticType.MEDIUM)
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                }
            )
        }
    }
}

/* ──────────────────────────────────────────
   Premium Header
────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumChatHeader(
    onNavigateBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(Modifier.width(4.dp))

            // AI Avatar (no glow ring, just simple circle)
            Box(contentAlignment = Alignment.Center) {
                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "AI",
                        tint     = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Online dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(SuccessColor)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = "AttendMate AI",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(SuccessColor)
                    )
                    Text(
                        text  = "Always available for help",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // AI Badge
            Surface(
                shape  = RoundedCornerShape(4.dp),
                color  = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text     = "AI",
                    color    = MaterialTheme.colorScheme.onPrimary,
                    style    = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/* ──────────────────────────────────────────
   Pulsing 3-dot Typing Indicator
────────────────────────────────────────── */

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Box(
        modifier        = Modifier.fillMaxWidth().padding(start = 0.dp, bottom = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 150)
                        ), label = "dot_$index"
                    )
                    val offsetY by infiniteTransition.animateFloat(
                        initialValue = 0f, targetValue = -6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 150)
                        ), label = "dot_offset_$index"
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = offsetY.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

/* ──────────────────────────────────────────
   Message Bubble
────────────────────────────────────────── */

@Composable
fun MessageBubble(
    message: ChatMessage,
    shouldAnimate: Boolean = false,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val isUser = message.isUser
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier        = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            when (message.messageType) {
                MessageType.ATTENDANCE_CARD      -> AttendanceCard(message)
                MessageType.TIMETABLE_CARD       -> TimetableCard(message)
                MessageType.ANALYSIS_CARD        -> AnalysisCard(message)
                MessageType.CONFIRM_MARK         -> ConfirmMarkCard(message, onConfirm, onCancel)
                MessageType.CONFIRM_DELETE       -> ConfirmDeleteCard(message, onConfirm, onCancel)
                MessageType.PREDICTION_CARD      -> PredictionCard(message)
                MessageType.STUDY_TIPS_CARD      -> StudyTipsCard(message)
                MessageType.WEEKLY_SUMMARY_CARD  -> WeeklySummaryCard(message)
                MessageType.GOAL_CARD            -> GoalSettingCard(message)
                MessageType.TREND_CARD           -> TrendAnalysisCard(message)
                // ── Advanced Analytics Cards ──
                MessageType.COMPARE_CARD         -> CompareCard(message)
                MessageType.MONTHLY_REPORT_CARD  -> MonthlyReportCard(message)
                MessageType.SKIP_BUDGET_CARD     -> SkipBudgetCard(message)
                MessageType.STREAK_CARD          -> StreakCard(message)
                MessageType.SUBJECT_RANKING_CARD -> SubjectRankingCard(message)
                MessageType.EXAM_STATUS_CARD     -> ExamStatusCard(message)
                MessageType.COLLEGE_SYNC_CARD    -> CollegeSyncCard(message)
                else -> {
                    // Standard text bubble
                    if (!isUser) {
                        // Bot avatar icon
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            BotTextBubble(message, shouldAnimate)
                        }
                    } else {
                        UserTextBubble(message)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserTextBubble(message: ChatMessage) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(RadiusXL, RadiusXL, RadiusSM, RadiusXL))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = SpaceMD, vertical = SpaceSM)
    ) {
        Text(
            text  = message.text,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp)
        )
    }
}

@Composable
private fun BotTextBubble(message: ChatMessage, shouldAnimate: Boolean) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    var hasAnimated by androidx.compose.runtime.saveable.rememberSaveable(message.text) { mutableStateOf(!shouldAnimate) }
    var displayedText by remember(message.text) { mutableStateOf(if (hasAnimated) message.text else "") }

    LaunchedEffect(message.text, shouldAnimate) {
        if (!hasAnimated) {
            for (i in 1..message.text.length) {
                displayedText = message.text.substring(0, i)
                delay(6)
            }
            hasAnimated = true
        } else {
            displayedText = message.text
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(RadiusSM, RadiusXL, RadiusXL, RadiusXL))
            .background(surfaceVariant)
            .padding(horizontal = SpaceMD, vertical = SpaceSM)
    ) {
        Text(
            text  = parseMarkdown(displayedText),
            color = onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp)
        )
    }
}

/* ──────────────────────────────────────────
   Rich Cards
────────────────────────────────────────── */

@Composable
private fun AttendanceCard(message: ChatMessage) {
    val data = message.attendanceData ?: return
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        shape         = RoundedCornerShape(RadiusSM, RadiusLG, RadiusLG, RadiusLG),
        color         = MaterialTheme.colorScheme.surface,
        border        = CardStyle.border(),
        shadowElevation = ElevationLow,
        modifier      = Modifier.widthIn(max = 300.dp)
    ) {
        Column(modifier = Modifier.padding(SpaceMD), verticalArrangement = Arrangement.spacedBy(SpaceSM)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpaceXS)) {
                Icon(Icons.Filled.School, null, tint = primary, modifier = Modifier.size(18.dp))
                Text("Attendance Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = primary)
            }
            HorizontalDivider(color = primary.copy(alpha = 0.15f))

            // Each subject
            data.forEach { subject ->
                AttendanceSubjectRow(subject)
            }

            // Footer note
            if (message.text.isNotBlank()) {
                HorizontalDivider(color = primary.copy(alpha = 0.1f))
                Text(
                    text  = parseMarkdown(message.text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AttendanceSubjectRow(subject: SubjectAttendanceData) {
    val pct     = subject.percentage
    val color   = when { pct >= 75 -> SuccessColor; pct >= 60 -> WarningColor; else -> DangerColor }
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Circular progress ring
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            CircularProgressRing(progress = pct / 100f, color = color)
            Text("$pct%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), fontWeight = FontWeight.Bold, color = color)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(subject.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = onSurface)
            Text(
                "${subject.attended}/${subject.total} classes",
                style = MaterialTheme.typography.labelSmall,
                color = onSurface.copy(0.65f)
            )
        }

        val statusIcon = when { pct >= 75 -> "✅"; pct >= 60 -> "⚠️"; else -> "🚨" }
        Text(statusIcon, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CircularProgressRing(progress: Float, color: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ring_progress"
    )
    val bgColor = color.copy(alpha = 0.15f)

    Canvas(modifier = Modifier.size(44.dp)) {
        val strokeW = 4.dp.toPx()
        val radius  = (size.minDimension - strokeW) / 2
        val center  = Offset(size.width / 2, size.height / 2)
        // Background ring
        drawCircle(color = bgColor, radius = radius, center = center, style = Stroke(strokeW))
        // Progress arc
        drawArc(
            color = color, startAngle = -90f, sweepAngle = 360f * animatedProgress,
            useCenter = false,
            topLeft   = Offset(center.x - radius, center.y - radius),
            size      = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style     = Stroke(strokeW, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun TimetableCard(message: ChatMessage) {
    val slots   = message.timetableData ?: return
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        shape         = RoundedCornerShape(RadiusSM, RadiusLG, RadiusLG, RadiusLG),
        color         = MaterialTheme.colorScheme.surface,
        border        = CardStyle.border(),
        shadowElevation = ElevationLow,
        modifier      = Modifier.widthIn(max = 300.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CalendarToday, null, tint = primary, modifier = Modifier.size(18.dp))
                Text(message.text.removePrefix("🗓️ "), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = primary)
            }
            HorizontalDivider(color = primary.copy(alpha = 0.15f))

            slots.forEach { slot ->
                TimetableSlotRow(slot)
            }
        }
    }
}

@Composable
private fun TimetableSlotRow(slot: TimetableSlot) {
    val ongoing = slot.isOngoing
    val bg      = if (ongoing) MaterialTheme.colorScheme.primary.copy(0.12f) else Color.Transparent
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier  = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Time column
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
            Text(slot.startTime, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = textColor)
            Text("↓", style = MaterialTheme.typography.labelSmall, color = textColor.copy(0.5f))
            Text(slot.endTime, style = MaterialTheme.typography.labelSmall, color = textColor.copy(0.7f))
        }

        // Vertical separator
        Box(modifier = Modifier.width(2.dp).height(36.dp).clip(RoundedCornerShape(1.dp)).background(
            if (ongoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(0.3f)
        ))

        // Subject name
        Column(modifier = Modifier.weight(1f)) {
            Text(slot.subjectName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = textColor)
            if (ongoing) {
                Text("Ongoing", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }

        if (ongoing) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SuccessColor))
        }
    }
}

@Composable
private fun AnalysisCard(message: ChatMessage) {
    val data    = message.analysisData ?: return
    val primary = MaterialTheme.colorScheme.primary
    val overallColor = when { data.overallPct >= 75 -> SuccessColor; data.overallPct >= 60 -> WarningColor; else -> DangerColor }

    Surface(
        shape         = RoundedCornerShape(RadiusSM, RadiusLG, RadiusLG, RadiusLG),
        color         = MaterialTheme.colorScheme.surface,
        border        = CardStyle.border(),
        shadowElevation = ElevationLow,
        modifier      = Modifier.widthIn(max = 320.dp)
    ) {
        Column(modifier = Modifier.padding(SpaceMD), verticalArrangement = Arrangement.spacedBy(SpaceSM)) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpaceXS)) {
                Icon(Icons.Filled.Insights, null, tint = primary, modifier = Modifier.size(18.dp))
                Text("Attendance Analysis", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = primary)
            }
            HorizontalDivider(color = primary.copy(0.15f))

            // Overall ring + stats
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpaceMD)) {
                Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    CircularProgressRing(data.overallPct / 100f, overallColor)
                    Text("${data.overallPct}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = overallColor)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Overall Attendance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                    Text("${data.totalAttended}/${data.totalClasses} Classes", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (data.bestSubject.isNotEmpty())
                        Text("🏆 Best: ${data.bestSubject} (${data.bestPct}%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (data.worstSubject.isNotEmpty())
                        Text("📉 Worst: ${data.worstSubject} (${data.worstPct}%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // At risk subjects
            if (data.atRisk.isNotEmpty()) {
                HorizontalDivider(color = DangerColor.copy(0.2f))
                Text("⚠️ Subjects at Risk", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = DangerColor)
                data.atRisk.forEach { s ->
                    val needed = ceil((0.75 * s.total - s.attended) / 0.25).toInt().coerceAtLeast(0)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${s.percentage}% · need $needed more", style = MaterialTheme.typography.bodySmall, color = DangerColor)
                    }
                }
            } else {
                Text("🎉 All subjects safe!", style = MaterialTheme.typography.bodySmall, color = SuccessColor, fontWeight = FontWeight.Bold)
            }

            // Summary text
            if (message.text.isNotBlank()) {
                Text(message.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f))
            }
        }
    }
}

@Composable
private fun ConfirmMarkCard(
    message: ChatMessage,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val marks   = message.pendingMarks ?: return
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        shape         = RoundedCornerShape(RadiusSM, RadiusLG, RadiusLG, RadiusLG),
        color         = MaterialTheme.colorScheme.surface,
        border        = CardStyle.border(),
        shadowElevation = ElevationLow,
        modifier      = Modifier.widthIn(max = 300.dp)
    ) {
        Column(modifier = Modifier.padding(SpaceMD), verticalArrangement = Arrangement.spacedBy(SpaceSM)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpaceXS)) {
                Icon(Icons.Filled.EditCalendar, null, tint = primary, modifier = Modifier.size(18.dp))
                Text("Confirm Attendance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = primary)
            }
            HorizontalDivider(color = primary.copy(0.15f))

            marks.forEach { mark ->
                val icon = if (mark.status == "Present") "✅" else "❌"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("$icon ${mark.subjectName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${mark.date}  •  ${mark.startTime} – ${mark.endTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                    }
                    Surface(shape = RoundedCornerShape(RadiusSM), color = if (mark.status == "Present") SuccessColor.copy(0.15f) else DangerColor.copy(0.15f)) {
                        Text(mark.status, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = if (mark.status == "Present") SuccessColor else DangerColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            HorizontalDivider(color = primary.copy(0.1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpaceXS)) {
                OutlinedButton(
                    onClick = onCancel, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(RadiusMD),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Cancel", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onConfirm, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(RadiusMD)
                ) { Text("Confirm", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteCard(
    message: ChatMessage,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val deletes = message.pendingDeletes ?: return
    val errorColor = MaterialTheme.colorScheme.error

    Surface(
        shape         = RoundedCornerShape(RadiusSM, RadiusLG, RadiusLG, RadiusLG),
        color         = MaterialTheme.colorScheme.surface,
        border        = CardStyle.border(),
        shadowElevation = ElevationLow,
        modifier      = Modifier.widthIn(max = 300.dp)
    ) {
        Column(modifier = Modifier.padding(SpaceMD), verticalArrangement = Arrangement.spacedBy(SpaceSM)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpaceXS)) {
                Icon(Icons.Filled.DeleteForever, null, tint = errorColor, modifier = Modifier.size(18.dp))
                Text("Confirm Deletion", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = errorColor)
            }
            HorizontalDivider(color = errorColor.copy(0.2f))

            deletes.take(5).forEach { del ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("➖ ${del.subjectName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${del.startTime} – ${del.endTime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
            }
            if (deletes.size > 5) {
                Text("...and ${deletes.size - 5} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
            }

            HorizontalDivider(color = errorColor.copy(0.1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpaceXS)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(RadiusMD)) { Text("Cancel", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onConfirm, modifier = Modifier.weight(1f), shape = RoundedCornerShape(RadiusMD),
                    colors = ButtonDefaults.buttonColors(containerColor = errorColor)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/* ──────────────────────────────────────────
   Smart Suggestion Row
────────────────────────────────────────── */

@Composable
private fun SmartSuggestionRow(suggestions: List<String>, onSuggestion: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            AssistChip(
                onClick = { onSuggestion(suggestion) },
                label   = {
                    Text(
                        text  = suggestion,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                shape  = RoundedCornerShape(20.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor    = primary.copy(alpha = 0.08f),
                    labelColor        = primary,
                    leadingIconContentColor = primary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, primary.copy(alpha = 0.3f))
            )
        }
    }
}

/* ──────────────────────────────────────────
   Modern Input Bar
────────────────────────────────────────── */

@Composable
private fun ModernInputBar(
    inputText: String,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = ElevationLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpaceMD, vertical = SpaceXS)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Text Field
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(RadiusXL),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    BasicInputField(
                        value = inputText,
                        onValueChange = onTextChange
                    )
                }

                Spacer(Modifier.width(SpaceXS))

                // Send Button
                val canSend = inputText.isNotBlank() && !isLoading
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = primary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BasicInputField(value: String, onValueChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value          = value,
        onValueChange  = onValueChange,
        modifier       = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        maxLines  = 4,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text("Ask anything...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), style = MaterialTheme.typography.bodyLarge)
            }
            innerTextField()
        }
    )
}

/* ──────────────────────────────────────────
   Markdown Parser
────────────────────────────────────────── */

fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2; continue
                }
            }
            if (text.startsWith("*", i) && !text.startsWith("**", i)) {
                val end = text.indexOf("*", i + 1)
                if (end != -1 && !text.startsWith("**", end)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1; continue
                }
            }
            append(text[i])
            i++
        }
    }
}

/* ──────────────────────────────────────────
   Haptic Feedback
────────────────────────────────────────── */

enum class HapticType { SOFT, MEDIUM }

@SuppressLint("MissingPermission")
fun triggerHaptic(context: Context, type: HapticType) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = manager?.defaultVibrator
            val effect = when (type) {
                HapticType.SOFT   -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticType.MEDIUM -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            }
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val duration = if (type == HapticType.SOFT) 30L else 60L
            vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (_: Exception) { /* Safe ignore */ }
}

/* ──────────────────────────────────────────
   Prediction Card
────────────────────────────────────────── */

@Composable
private fun PredictionCard(message: ChatMessage) {
    val predictions = message.predictionData ?: return

    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(SpaceXS))
        predictions.forEach { pred ->
            val riskColor = when {
                pred.riskScore >= 70 -> DangerColor
                pred.riskScore >= 40 -> WarningColor
                else                -> SuccessColor
            }
            Surface(
                shape = RoundedCornerShape(RadiusLG),
                color = MaterialTheme.colorScheme.surface,
                border = CardStyle.border(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) {
                Row(
                    modifier = Modifier.padding(SpaceMD),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pred.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Now: ${pred.currentPct}% → Predicted: ${pred.predictedPct}%",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(pred.trend.arrow(), fontSize = 24.sp, color = riskColor)
                }
            }
        }
    }
}

/* ──────────────────────────────────────────
   Study Tips Card
────────────────────────────────────────── */

@Composable
private fun StudyTipsCard(message: ChatMessage) {
    val data = message.studyTipsData ?: return
    val urgencyColor = when (data.urgencyLevel) {
        "critical" -> DangerColor
        "warning"  -> WarningColor
        "caution"  -> WarningColor
        "safe"     -> SuccessColor
        else       -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(SpaceXS))
        Surface(
            shape = RoundedCornerShape(RadiusLG),
            color = MaterialTheme.colorScheme.surface,
            border = CardStyle.border(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(SpaceMD)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape).background(urgencyColor)
                    )
                    Spacer(modifier = Modifier.width(SpaceXS))
                    Text("${data.subjectName} — ${data.currentPct}%",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(SpaceSM))
                data.tips.forEach { tip ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(tip.icon, modifier = Modifier.width(24.dp))
                        Text(tip.text, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────
   Weekly Summary Card
────────────────────────────────────────── */

@Composable
private fun WeeklySummaryCard(message: ChatMessage) {
    val data = message.weeklySummaryData ?: return

    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(SpaceXS))
        Surface(
            shape = RoundedCornerShape(RadiusLG),
            color = MaterialTheme.colorScheme.surface,
            border = CardStyle.border(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(SpaceMD)) {
                // Stats row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatChip("✅", "${data.totalPresent}", "Present")
                    StatChip("❌", "${data.totalAbsent}", "Absent")
                    StatChip("📊", "${data.attendanceRate}%", "Rate")
                }
                Spacer(modifier = Modifier.height(SpaceSM))
                Text("🏆 Best: ${data.bestDay}", style = MaterialTheme.typography.bodySmall)
                Text("📉 Worst: ${data.worstDay}", style = MaterialTheme.typography.bodySmall)
                if (data.currentStreak > 0) {
                    Text("🔥 Streak: ${data.currentStreak} ${if (data.isPositiveStreak) "present" else "absent"}",
                        style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(SpaceXS))
                // Subject breakdown
                data.subjectBreakdown.forEach { sub ->
                    val pctColor = if (sub.pct >= 75) SuccessColor else DangerColor
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(sub.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${sub.pct}%", style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold, color = pctColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 18.sp)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ──────────────────────────────────────────
   Goal Setting Card
────────────────────────────────────────── */

@Composable
private fun GoalSettingCard(message: ChatMessage) {
    val data = message.goalData ?: return
    val progress = if (data.targetPct > 0) (data.currentPct.toFloat() / data.targetPct).coerceIn(0f, 1f) else 0f
    val progressColor = if (data.isAchieved) SuccessColor else MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(SpaceXS))
        Surface(
            shape = RoundedCornerShape(RadiusLG),
            color = MaterialTheme.colorScheme.surface,
            border = CardStyle.border(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(SpaceMD), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(data.subjectName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(SpaceSM))

                // Circular progress
                val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        drawArc(outlineColor, 0f, 360f, false, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(progressColor, -90f, 360f * progress, false, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Text("${data.currentPct}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(SpaceXS))
                Text("🎯 Target: ${data.targetPct}%", style = MaterialTheme.typography.bodySmall)
                if (!data.isAchieved) {
                    Text("📚 Need ${data.classesNeeded} more classes", style = MaterialTheme.typography.bodySmall, color = WarningColor)
                } else {
                    Text("✅ Goal achieved!", style = MaterialTheme.typography.bodySmall, color = SuccessColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/* ──────────────────────────────────────────
   Trend Analysis Card
────────────────────────────────────────── */

@Composable
private fun TrendAnalysisCard(message: ChatMessage) {
    val trends = message.trendData ?: return

    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(SpaceXS))
        trends.forEach { trend ->
            val trendColor = when (trend.trend) {
                PredictionEngine.TrendDirection.IMPROVING -> SuccessColor
                PredictionEngine.TrendDirection.DECLINING -> DangerColor
                PredictionEngine.TrendDirection.STABLE    -> MaterialTheme.colorScheme.outline
            }
            Surface(
                shape = RoundedCornerShape(RadiusLG),
                color = MaterialTheme.colorScheme.surface,
                border = CardStyle.border(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) {
                Row(
                    modifier = Modifier.padding(SpaceMD),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(trend.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        val delta = if (trend.weeklyChangePct >= 0) "+${trend.weeklyChangePct.toInt()}%" else "${trend.weeklyChangePct.toInt()}%"
                        Text("Current: ${trend.currentPct}% | 7d avg: ${trend.recentAvgPct}% ($delta)",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${trend.trend.emoji()} ${trend.trend.arrow()}", fontSize = 18.sp, color = trendColor)
                }
            }
        }
    }
}

/* ──────────────────────────────────────────
   Advanced Analytics Cards
────────────────────────────────────────── */

@Composable
private fun CompareCard(message: ChatMessage) {
    val data = message.compareData?.result ?: return
    Column(modifier = Modifier.widthIn(max = 320.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(SpaceXS))
        Surface(
            shape = RoundedCornerShape(RadiusLG),
            color = MaterialTheme.colorScheme.surface,
            border = CardStyle.border(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(SpaceMD), verticalArrangement = Arrangement.spacedBy(SpaceSM)) {
                // Headers
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(data.subjectA.take(12) + if(data.subjectA.length>12)".." else "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(RadiusSM), color = MaterialTheme.colorScheme.primary.copy(0.12f)) {
                        Text("vs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
                    }
                    Text(data.subjectB.take(12) + if(data.subjectB.length>12)".." else "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Attendance Pct
                CompareRow("Attendance", "${data.pctA}%", "${data.pctB}%", data.pctA > data.pctB, data.pctB > data.pctA)
                // Trend
                CompareRow("Trend", data.trendA.name, data.trendB.name, data.trendA < data.trendB, data.trendB < data.trendA)

                Text(data.recommendation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, val1: String, val2: String, v1Better: Boolean, v2Better: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        val c1 = if (v1Better) SuccessColor else MaterialTheme.colorScheme.onSurfaceVariant
        val c2 = if (v2Better) SuccessColor else MaterialTheme.colorScheme.onSurfaceVariant

        Text(val1, style = MaterialTheme.typography.bodyLarge, color = c1, fontWeight = if (v1Better) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), fontWeight = FontWeight.Bold)
        Text(val2, style = MaterialTheme.typography.bodyLarge, color = c2, fontWeight = if (v2Better) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
private fun MonthlyReportCard(message: ChatMessage) {
    val data = message.monthlyReportData?.report ?: return
    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(SpaceXS))
        Surface(
            shape = RoundedCornerShape(RadiusLG),
            color = MaterialTheme.colorScheme.surface,
            border = CardStyle.border(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(SpaceMD)) {
                Text("🗓️ ${data.monthName} Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(SpaceSM))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatChip("📅", "${data.totalClasses}", "Classes")
                    StatChip("✅", "${data.subjects.sumOf { it.present }}", "Attended")
                    StatChip("📊", "${data.overallPct}%", "Rate")
                }
                Spacer(modifier = Modifier.height(SpaceSM))
                if (data.bestSubject.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⭐ Best: ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = WarningColor)
                        Text(data.bestSubject, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
                if (data.worstSubject.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️ Needs work: ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = DangerColor)
                        Text(data.worstSubject, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipBudgetCard(message: ChatMessage) {
    val data = message.skipBudgetData?.budget ?: return
    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(SpaceXS))
        Surface(
            shape = RoundedCornerShape(RadiusLG),
            color = MaterialTheme.colorScheme.surface,
            border = CardStyle.border(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(SpaceMD), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(data.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(SpaceMD))

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    val color = if (data.canSkip > 0) SuccessColor else DangerColor
                    val animatedProgress by animateFloatAsState(targetValue = data.currentPct / 100f, animationSpec = spring(dampingRatio = 0.7f), label = "skip_prog")
                    Canvas(modifier = Modifier.size(100.dp)) {
                        drawArc(color.copy(0.15f), 0f, 360f, false, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(color, -90f, 360f * animatedProgress, false, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${data.canSkip}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
                        Text("Skips Left", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(SpaceMD))
                Surface(shape = RoundedCornerShape(RadiusSM), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("Target: ${data.targetPct}% • Current: ${data.currentPct}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(SpaceXS))
                }

                if (data.canSkip < 0) {
                    Spacer(modifier = Modifier.height(SpaceXS))
                    Text("Attend ${-data.canSkip} more classes to reach target!", style = MaterialTheme.typography.labelMedium, color = DangerColor, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun StreakCard(message: ChatMessage) {
    val data = message.streakData ?: return
    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(SpaceXS))
        Surface(
            shape = RoundedCornerShape(RadiusLG),
            color = MaterialTheme.colorScheme.surface,
            border = CardStyle.border(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(SpaceMD), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpaceMD)) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(if (data.isOnPresentStreak) SuccessColor.copy(0.15f) else DangerColor.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    val scale by animateFloatAsState(targetValue = 1.1f, animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
                    Text(if (data.isOnPresentStreak) "🔥" else "🧊", fontSize = (28 * scale).sp)
                }
                Column {
                    if (data.isOnPresentStreak) {
                        Text("${data.currentPresentStreak} Days", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = SuccessColor)
                        Text("Active Present Streak!", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    } else {
                        Text("${data.currentAbsentStreak} Days", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = DangerColor)
                        Text("Active Absent Streak", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(SpaceXXS))
                    Surface(shape = RoundedCornerShape(RadiusSM), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("All-Time Best: ${data.longestPresentStreak} 🔥", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(6.dp, 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectRankingCard(message: ChatMessage) {
    val data = message.rankingData?.ranking ?: return
    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(SpaceXS))
        Surface(
            shape = RoundedCornerShape(RadiusLG),
            color = MaterialTheme.colorScheme.surface,
            border = CardStyle.border(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(SpaceMD), verticalArrangement = Arrangement.spacedBy(SpaceXS)) {
                data.forEachIndexed { index, rank ->
                    val color = when (index) {
                        0 -> Color(0xFFFFD700) // Gold
                        1 -> Color(0xFFC0C0C0) // Silver
                        2 -> Color(0xFFCD7F32) // Bronze
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusMD)).background(color.copy(0.08f)).padding(SpaceMD), verticalAlignment = Alignment.CenterVertically) {
                        Text("#${index + 1}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(36.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rank.first, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            val riskColor = when(rank.third) {
                                PredictionEngine.RiskStatus.SAFE -> SuccessColor
                                PredictionEngine.RiskStatus.WARNING -> WarningColor
                                PredictionEngine.RiskStatus.CRITICAL -> DangerColor
                            }
                            Text(rank.third.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = riskColor)
                        }
                        Text("${rank.second}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamStatusCard(message: ChatMessage) {
    val data = message.examStatusData?.subjects ?: return
    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(SpaceXS))
        Surface(
            shape = RoundedCornerShape(RadiusLG),
            color = MaterialTheme.colorScheme.surface,
            border = CardStyle.border(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(SpaceSM), verticalArrangement = Arrangement.spacedBy(SpaceXS)) {
                data.forEach { sub ->
                    val color = if (sub.isEligible) SuccessColor else DangerColor
                    val bgColor = color.copy(0.12f)
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusMD)).background(bgColor).padding(SpaceMD), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(color).padding(4.dp), contentAlignment = Alignment.Center) {
                            Icon(if (sub.isEligible) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(SpaceMD))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            if (!sub.isEligible) {
                                Text("Need ${sub.classesNeeded} more classes", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Eligible (${sub.pct}%)", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollegeSyncCard(message: ChatMessage) {
    val data = message.collegeSyncData ?: return
    val primary = MaterialTheme.colorScheme.primary
    
    Column(modifier = Modifier.widthIn(max = 320.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(SpaceXS))
        Surface(
            shape = RoundedCornerShape(RadiusSM, RadiusLG, RadiusLG, RadiusLG),
            color = MaterialTheme.colorScheme.surface,
            border = CardStyle.border(),
            shadowElevation = ElevationLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(SpaceMD), verticalArrangement = Arrangement.spacedBy(SpaceSM)) {
                // Headers
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpaceXS)) {
                        Icon(Icons.Filled.School, null, tint = primary, modifier = Modifier.size(18.dp))
                        Text("College Grid", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = primary)
                    }
                    
                    Surface(shape = RoundedCornerShape(RadiusSM), color = SuccessColor.copy(0.12f)) {
                        Text("${data.overallCollegePct}%", style = MaterialTheme.typography.labelSmall, color = SuccessColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (data.mismatches.isNotEmpty()) {
                    Text("Mismatches", style = MaterialTheme.typography.labelSmall, color = WarningColor, fontWeight = FontWeight.Bold)
                    data.mismatches.take(3).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (data.collegeMissing.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text("Missing in App", style = MaterialTheme.typography.labelSmall, color = DangerColor, fontWeight = FontWeight.Bold)
                    data.collegeMissing.take(3).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (data.appMissing.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text("Missing in College", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    data.appMissing.take(3).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(parseMarkdown(data.syncSummaryText), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}