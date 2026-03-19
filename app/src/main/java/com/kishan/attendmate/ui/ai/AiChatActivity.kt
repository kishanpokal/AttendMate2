package com.kishan.attendmate.ui.ai

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.delay

class AiChatActivity : ComponentActivity() {

    private val viewModel: AiChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                primaryColor   = primaryColor,
                secondaryColor = secondaryColor,
                onNavigateBack = onNavigateBack
            )

            // ── Messages ────────────────────────────────────────────
            LazyColumn(
                state    = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                itemsIndexed(messages) { index, message ->
                    val shouldAnimate = !message.isUser && index == messages.lastIndex &&
                                        uiState == AiChatUiState.Success

                    AnimatedVisibility(
                        visible = true,
                        enter   = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()
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

            // ── Glassmorphism Input Bar ────────────────────────────
            GlassInputBar(
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
    primaryColor: Color,
    secondaryColor: Color,
    onNavigateBack: () -> Unit
) {
    // Animated glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "header_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation    = tween(2000, easing = FastOutSlowInEasing),
            repeatMode   = RepeatMode.Reverse
        ), label = "glow_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.95f),
                        secondaryColor.copy(alpha = 0.85f)
                    )
                )
            )
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(Modifier.width(4.dp))

            // AI Avatar with glow ring
            Box(contentAlignment = Alignment.Center) {
                // Glow ring
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = glowAlpha * 0.3f))
                )
                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "AI",
                        tint     = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                // Online dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                        .border(2.dp, Color.White, CircleShape)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = "AttendMate AI",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Text(
                        text  = "Always available for help",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // AI Sparkle Badge
            Surface(
                shape  = RoundedCornerShape(8.dp),
                color  = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text     = "✨ AI",
                    color    = Color.White,
                    style    = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
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
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 200)
                        ), label = "dot_$index"
                    )
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.7f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 200)
                        ), label = "dot_scale_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size((8 * scale).dp)
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
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(primaryColor, primaryColor.copy(alpha = 0.7f))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint     = Color.White,
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
    val primaryColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(primaryColor, primaryColor.copy(0.85f)),
                    start  = Offset(0f, 0f), end = Offset(300f, 0f)
                )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text  = message.text,
            color = Color.White,
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
            .clip(RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
            .background(surfaceVariant.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
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
        shape         = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
        color         = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shadowElevation = 4.dp,
        modifier      = Modifier.widthIn(max = 300.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun AttendanceSubjectRow(subject: SubjectAttendanceData) {
    val pct     = subject.percentage
    val color   = when { pct >= 75 -> Color(0xFF4CAF50); pct >= 60 -> Color(0xFFFF9800); else -> Color(0xFFF44336) }
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
    val now     = remember { java.time.LocalTime.now() }

    Surface(
        shape         = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
        color         = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shadowElevation = 4.dp,
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
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
        }
    }
}

@Composable
private fun AnalysisCard(message: ChatMessage) {
    val data    = message.analysisData ?: return
    val primary = MaterialTheme.colorScheme.primary
    val overallColor = when { data.overallPct >= 75 -> Color(0xFF4CAF50); data.overallPct >= 60 -> Color(0xFFFF9800); else -> Color(0xFFF44336) }

    Surface(
        shape         = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
        color         = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shadowElevation = 6.dp,
        modifier      = Modifier.widthIn(max = 320.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Insights, null, tint = primary, modifier = Modifier.size(18.dp))
                Text("Attendance Analysis", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall, color = primary)
            }
            HorizontalDivider(color = primary.copy(0.15f))

            // Overall ring + stats
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    CircularProgressRing(data.overallPct / 100f, overallColor)
                    Text("${data.overallPct}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = overallColor)
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
                HorizontalDivider(color = Color(0xFFF44336).copy(0.2f))
                Text("⚠️ Subjects at Risk", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                data.atRisk.forEach { s ->
                    val needed = Math.ceil((0.75 * s.total - s.attended) / 0.25).toInt().coerceAtLeast(0)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${s.percentage}% · need $needed more", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336))
                    }
                }
            } else {
                Text("🎉 All subjects safe!", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
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
        shape         = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
        color         = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shadowElevation = 4.dp,
        modifier      = Modifier.widthIn(max = 300.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Surface(shape = RoundedCornerShape(6.dp), color = if (mark.status == "Present") Color(0xFF4CAF50).copy(0.15f) else Color(0xFFF44336).copy(0.15f)) {
                        Text(mark.status, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = if (mark.status == "Present") Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            HorizontalDivider(color = primary.copy(0.1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCancel, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Cancel") }
                Button(
                    onClick = onConfirm, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Confirm ✓") }
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
        shape         = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
        color         = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shadowElevation = 4.dp,
        modifier      = Modifier.widthIn(max = 300.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                Button(
                    onClick = onConfirm, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = errorColor)
                ) { Text("Delete ✓") }
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
   Glassmorphism Input Bar
────────────────────────────────────────── */

@Composable
private fun GlassInputBar(
    inputText: String,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        color           = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation  = 8.dp,
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Text Field
            Surface(
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(24.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ) {
                BasicInputField(
                    value       = inputText,
                    onValueChange = onTextChange,
                    placeholder = "Ask anything…"
                )
            }

            Spacer(Modifier.width(10.dp))

            // Send Button
            val canSend = inputText.isNotBlank() && !isLoading
            val buttonScale by animateFloatAsState(targetValue = if (canSend) 1f else 0.85f, label = "send_scale")

            Box(modifier = Modifier.size(48.dp)) {
                FilledIconButton(
                    onClick  = onSend,
                    enabled  = canSend,
                    modifier = Modifier
                        .size((48 * buttonScale).dp)
                        .align(Alignment.Center),
                    shape    = CircleShape,
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = primary,
                        disabledContainerColor = primary.copy(0.35f)
                    )
                ) {
                    Icon(
                        imageVector      = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint             = Color.White,
                        modifier         = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BasicInputField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
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
                Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), style = MaterialTheme.typography.bodyLarge)
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
/* ──────────────────────────────────────────
   Prediction Card
────────────────────────────────────────── */

@Composable
private fun PredictionCard(message: ChatMessage) {
    val predictions = message.predictionData ?: return
    val primary = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(8.dp))
        predictions.forEach { pred ->
            val riskColor = when {
                pred.riskScore >= 70 -> Color(0xFFEF4444)
                pred.riskScore >= 40 -> Color(0xFFF59E0B)
                else                -> Color(0xFF22C55E)
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
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
        "critical" -> Color(0xFFEF4444)
        "warning"  -> Color(0xFFF59E0B)
        "caution"  -> Color(0xFFEAB308)
        "safe"     -> Color(0xFF22C55E)
        else       -> Color(0xFF3B82F6)
    }

    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape).background(urgencyColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${data.subjectName} — ${data.currentPct}%",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
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
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Stats row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatChip("✅", "${data.totalPresent}", "Present")
                    StatChip("❌", "${data.totalAbsent}", "Absent")
                    StatChip("📊", "${data.attendanceRate}%", "Rate")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("📈 Best: ${data.bestDay}", style = MaterialTheme.typography.bodySmall)
                Text("📉 Worst: ${data.worstDay}", style = MaterialTheme.typography.bodySmall)
                if (data.currentStreak > 0) {
                    Text("🔥 Streak: ${data.currentStreak} ${if (data.isPositiveStreak) "present" else "absent"}",
                        style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Subject breakdown
                data.subjectBreakdown.forEach { sub ->
                    val pctColor = if (sub.pct >= 75) Color(0xFF22C55E) else Color(0xFFEF4444)
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
    val progressColor = if (data.isAchieved) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.widthIn(max = 300.dp)) {
        BotTextBubble(message.copy(text = message.text), false)
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(data.subjectName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                // Circular progress
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        drawArc(Color.Gray.copy(alpha = 0.2f), 0f, 360f, false, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(progressColor, -90f, 360f * progress, false, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Text("${data.currentPct}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("🎯 Target: ${data.targetPct}%", style = MaterialTheme.typography.bodySmall)
                if (!data.isAchieved) {
                    Text("📚 Need ${data.classesNeeded} more classes", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF59E0B))
                } else {
                    Text("✅ Goal achieved!", style = MaterialTheme.typography.bodySmall, color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
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
        Spacer(modifier = Modifier.height(8.dp))
        trends.forEach { trend ->
            val trendColor = when (trend.trend) {
                PredictionEngine.TrendDirection.IMPROVING -> Color(0xFF22C55E)
                PredictionEngine.TrendDirection.DECLINING -> Color(0xFFEF4444)
                PredictionEngine.TrendDirection.STABLE    -> Color(0xFF6B7280)
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
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
   Haptics
────────────────────────────────────────── */

enum class HapticType { SOFT, MEDIUM }

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        }
    } catch (_: Exception) { /* Safe ignore */ }
}
