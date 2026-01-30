package com.kishan.attendmate.ui.analytics

import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyScopeMarker
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.attendance.StatItem
import com.kishan.attendmate.ui.components.AttendMateNavigationBar
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/* ------------------------------------------------ */
/* ACTIVITY */
/* ------------------------------------------------ */
class AnalyticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendMateTheme {
                Scaffold(
                    bottomBar = { AttendMateNavigationBar("analytics") }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        AnalyticsScreen()
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------ */
/* DATA MODEL */
/* ------------------------------------------------ */
data class AnalyticsAttendance(
    val subject: String,
    val date: LocalDate,
    val status: String // PRESENT / ABSENT
)

/* ------------------------------------------------ */
/* CORE MATH LOGIC */
/* ------------------------------------------------ */
fun lecturesNeededFor75(present: Int, total: Int): Int {
    if (total == 0) return 0
    if (present.toFloat() / total >= 0.75f) return 0
    return ceil((0.75 * total - present) / 0.25).toInt()
}

fun maxBunkableLectures(present: Int, total: Int): Int {
    if (total == 0) return 0
    return floor(present / 0.75 - total).toInt().coerceAtLeast(0)
}

fun subjectColor(percent: Int): Color = when {
    percent >= 75 -> Color(0xFF4CAF50) // green
    percent >= 60 -> Color(0xFFFFC107) // yellow
    else -> Color(0xFFF44336) // red
}

data class SkipRecoveryRow(
    val skipped: Int,
    val percentageAfterSkip: Int,
    val lecturesToRecover: Int
)



/* ------------------------------------------------ */
/* SCREEN */
/* ------------------------------------------------ */
@Composable
fun AnalyticsScreen() {
    val db = FirebaseFirestore.getInstance()
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var loading by remember { mutableStateOf(true) }
    var attendance by remember { mutableStateOf<List<AnalyticsAttendance>>(emptyList()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    /* -------- FIRESTORE LOAD -------- */
    LaunchedEffect(refreshKey) {
        loading = true
        val temp = mutableListOf<AnalyticsAttendance>()
        val subjects = db.collection("users")
            .document(uid)
            .collection("subjects")
            .get()
            .await()
        subjects.documents.forEach { subjectDoc ->
            val subjectName = subjectDoc.getString("name") ?: return@forEach
            val records = subjectDoc.reference
                .collection("attendance")
                .get()
                .await()
            records.documents.forEach { doc ->
                val statusRaw = doc.getString("status") ?: "ABSENT"
                val status = statusRaw.uppercase()
                val date: LocalDate? = when (val rawDate = doc.get("date")) {
                    is String -> runCatching { LocalDate.parse(rawDate) }.getOrNull()
                    is Timestamp -> rawDate.toDate()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    else -> null
                }
                date?.let {
                    temp.add(
                        AnalyticsAttendance(
                            subject = subjectName,
                            date = it,
                            status = status
                        )
                    )
                }
            }
        }
        attendance = temp
        loading = false
    }

    val total = attendance.size
    val present = attendance.count { it.status == "PRESENT" }
    val percentage = if (total == 0) 0 else (present * 100 / total)
    val neededFor75 = lecturesNeededFor75(present, total)
    val bunkable = maxBunkableLectures(present, total)
    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

    Column(modifier = Modifier.fillMaxSize()) {
        /* -------- TOP HEADER -------- */
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Analytics",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Attendance insights & predictions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Analyzing your data...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !loading,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            if (attendance.isEmpty()) {
                EmptyAnalyticsState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    /* -------- OVERALL CARD -------- */
                    item {
                        ModernOverallCard(
                            percentage = percentage,
                            present = present,
                            total = total,
                            neededFor75 = neededFor75,
                            bunkable = bunkable
                        )
                    }
                    /* -------- SUBJECT BAR GRAPH -------- */
                    item {
                        SubjectBarGraph(attendance)
                    }
                    /* -------- CALENDAR -------- */
                    item {
                        AttendanceCalendar(attendance) { selectedDate = it }
                    }
                    /* -------- PIE CHART -------- */
                    item {
                        SubjectPieChart(attendance)
                    }
                    item {
                        SkipAttendancePrediction(
                            present = present,
                            total = total
                        )
                    }
                    // Bottom padding
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }

    /* -------- DATE DIALOG -------- */
    selectedDate?.let { date ->
        ModernDateDialog(
            date = date,
            attendance = attendance,
            dateFormatter = dateFormatter,
            onDismiss = { selectedDate = null }
        )
    }
}

/* ------------------------------------------------ */
/* EMPTY STATE */
/* ------------------------------------------------ */
@Composable
fun EmptyAnalyticsState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
            Text(
                text = "No Data Available",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Start tracking your attendance to see analytics here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/* ------------------------------------------------ */
/* MODERN OVERALL CARD */
/* ------------------------------------------------ */
@Composable
fun ModernOverallCard(
    percentage: Int,
    present: Int,
    total: Int,
    neededFor75: Int,
    bunkable: Int
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val successColor = Color(0xFF4CAF50)
    val animatedPercentage = remember { Animatable(0f) }
    LaunchedEffect(percentage) {
        animatedPercentage.animateTo(
            targetValue = percentage.toFloat(),
            animationSpec = tween(durationMillis = 1000, easing = EaseOutCubic)
        )
    }
    val statusColor = when {
        percentage >= 75 -> successColor
        percentage >= 60 -> Color(0xFFFFC107)
        else -> errorColor
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        statusColor.copy(alpha = 0.12f),
                        statusColor.copy(alpha = 0.04f)
                    )
                )
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Overall Performance",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = statusColor
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    percentage >= 75 -> Icons.Default.TrendingUp
                                    percentage >= 60 -> Icons.Default.Remove
                                    else -> Icons.Default.TrendingDown
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when {
                                    percentage >= 75 -> "Good"
                                    percentage >= 60 -> "Average"
                                    else -> "Low"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                // Circular Progress
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(160.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedPercentage.value / 100f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 14.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            color = statusColor
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${animatedPercentage.value.toInt()}%",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                            Text(
                                text = "Attendance",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatColumn(
                        label = "Present",
                        value = present.toString(),
                        icon = Icons.Default.CheckCircle,
                        color = successColor
                    )
                    VerticalDivider(
                        modifier = Modifier.height(50.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    StatColumn(
                        label = "Total",
                        value = total.toString(),
                        icon = Icons.Default.EventNote,
                        color = primaryColor
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Prediction Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (percentage < 75) errorColor.copy(alpha = 0.1f) else successColor.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (percentage < 75) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (percentage < 75) errorColor else successColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            if (percentage < 75) {
                                Text(
                                    text = "Action Required",
                                    fontWeight = FontWeight.Bold,
                                    color = errorColor,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Attend $neededFor75 more lectures to reach 75%",
                                    color = errorColor.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    text = "Great Performance!",
                                    fontWeight = FontWeight.Bold,
                                    color = successColor,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "You can skip $bunkable lectures safely",
                                    color = successColor.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ------------------------------------------------ */
/* SUBJECT BAR GRAPH - MODERN */
/* ------------------------------------------------ */
@Composable
fun SubjectBarGraph(attendance: List<AnalyticsAttendance>) {
    val grouped = attendance.groupBy { it.subject }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(24.dp)) {
            // Enhanced Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF3B82F6),
                                    Color(0xFF2563EB)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Subject-wise Analysis",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${grouped.size} subjects tracked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 75% Threshold Info Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.1f))
                    .border(
                        width = 1.dp,
                        color = Color(0xFFEF4444).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Red line indicates 75% threshold",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFEF4444)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Subject Progress Bars
            grouped.entries.sortedByDescending { entry ->
                val present = entry.value.count { it.status == "PRESENT" }
                val total = entry.value.size
                if (total == 0) 0 else (present * 100 / total)
            }.forEach { (subject, list) ->
                EnhancedSubjectBar(
                    subject = subject,
                    attendanceList = list,
                    isLast = grouped.keys.last() == subject
                )
            }
        }
    }
}

@Composable
private fun EnhancedSubjectBar(
    subject: String,
    attendanceList: List<AnalyticsAttendance>,
    isLast: Boolean
) {
    val present = attendanceList.count { it.status == "PRESENT" }
    val total = attendanceList.size
    val percent = if (total == 0) 0 else (present * 100 / total)
    val color = subjectColor(percent)

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(percent) {
        animatedProgress.animateTo(
            targetValue = percent / 100f,
            animationSpec = tween(durationMillis = 1000, easing = EaseOutCubic)
        )
    }

    val statusIcon = when {
        percent >= 75 -> Icons.Default.CheckCircle
        percent >= 60 -> Icons.Default.Warning
        else -> Icons.Default.Error
    }

    val statusText = when {
        percent >= 75 -> "Safe"
        percent >= 60 -> "At Risk"
        else -> "Critical"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Subject Header with Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Subject Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = subject,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$present / $total lectures",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Percentage Badge
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = color.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "$percent%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress Bar with 75% Marker
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            // Background bar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )

            // Progress bar with gradient
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress.value)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                color,
                                color.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            // 75% Threshold Line
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .offset(x = (0.75f * LocalConfiguration.current.screenWidthDp.dp) - 48.dp)
                    .background(Color(0xFFEF4444))
                    .align(Alignment.CenterStart)
            )

            // 75% Label
            Box(
                modifier = Modifier
                    .offset(x = (0.75f * LocalConfiguration.current.screenWidthDp.dp) - 48.dp)
                    .align(Alignment.BottomStart)
            ) {
                Surface(
                    modifier = Modifier.offset(y = 22.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFEF4444)
                ) {
                    Text(
                        text = "75%",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Detailed Stats Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.05f))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatBadge(
                label = "Present",
                value = "$present",
                color = Color(0xFF10B981)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )

            StatBadge(
                label = "Absent",
                value = "${total - present}",
                color = Color(0xFFEF4444)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )

            StatBadge(
                label = "Needed",
                value = if (percent >= 75) "0" else {
                    val needed = calculateLecturesNeededFor75(present, total)
                    "$needed"
                },
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Divider
        if (!isLast) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun StatBadge(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Helper function to calculate lectures needed to reach 75%
private fun calculateLecturesNeededFor75(present: Int, total: Int): Int {
    if (total == 0) return 0
    val currentPercent = (present.toFloat() / total) * 100
    if (currentPercent >= 75) return 0

    var tempPresent = present
    var tempTotal = total
    var needed = 0

    while ((tempPresent.toFloat() / tempTotal * 100) < 75 && needed < 100) {
        tempPresent++
        tempTotal++
        needed++
    }

    return needed
}

/* ------------------------------------------------ */
/* CALENDAR - MODERN (USING JAVA.TIME) */
/* ------------------------------------------------ */
@Composable
fun AttendanceCalendar(
    attendance: List<AnalyticsAttendance>,
    onDateClick: (LocalDate) -> Unit
) {
    var monthOffset by remember { mutableStateOf(0) }
    val currentMonth = remember(monthOffset) {
        YearMonth.now().plusMonths(monthOffset.toLong())
    }
    val firstDayOfMonth = currentMonth.atDay(1)
    val daysInMonth = currentMonth.lengthOfMonth()

    // Adjust for Monday start (0 = Monday, 6 = Sunday)
    val firstDayOfWeek = (firstDayOfMonth.dayOfWeek.value - 1) % 7

    val title = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    val today = LocalDate.now()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(24.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "Attendance Calendar",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Month Navigation
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { monthOffset-- },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Previous Month",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { monthOffset++ },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Next Month",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Week Day Headers (Mon - Sun)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                    Box(
                        modifier = Modifier
                            .size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Calendar Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height(280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Empty cells for first week
                items(firstDayOfWeek) {
                    Box(modifier = Modifier.size(40.dp))
                }

                // Days of month
                items(daysInMonth) { index ->
                    val date = firstDayOfMonth.plusDays(index.toLong())
                    val dayAttendance = attendance.filter { it.date == date }
                    val hasData = dayAttendance.isNotEmpty()
                    val isToday = date == today

                    val allPresent = dayAttendance.all { it.status == "PRESENT" }
                    val allAbsent = dayAttendance.all { it.status == "ABSENT" }

                    val bgColor = when {
                        !hasData && isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        !hasData -> Color.Transparent
                        allPresent -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        allAbsent -> Color(0xFFF44336).copy(alpha = 0.15f)
                        else -> Color(0xFF0CFDCD).copy(alpha = 0.15f)
                    }

                    val borderColor = when {
                        isToday && !hasData -> MaterialTheme.colorScheme.primary
                        allPresent -> Color(0xFF4CAF50)
                        allAbsent -> Color(0xFFF44336)
                        hasData -> Color(0xFF0CFDCD)
                        else -> Color.Transparent
                    }

                    val textColor = when {
                        allPresent -> Color(0xFF2E7D32)
                        allAbsent -> Color(0xFFC62828)
                        hasData -> Color(0xFF0CFDCD)
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .clickable(enabled = hasData) { onDateClick(date) }
                            .border(
                                width = if (hasData || isToday) 2.dp else 0.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (hasData || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                fontSize = 14.sp
                            )

                            // Small indicator dot for attendance
                            if (hasData) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(borderColor)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Enhanced Legend with better design
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EnhancedLegendItem(
                    color = Color(0xFF4CAF50),
                    label = "Present"
                )
                EnhancedLegendItem(
                    color = Color(0xFFF44336),
                    label = "Absent"
                )
                EnhancedLegendItem(
                    color = Color(0xFF0CFDCD),
                    label = "Mixed"
                )
            }
        }
    }
}

@Composable
private fun EnhancedLegendItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.3f))
                .border(
                    width = 2.dp,
                    color = color,
                    shape = RoundedCornerShape(4.dp)
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
@Composable
fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.3f))
                .border(1.dp, color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ------------------------------------------------ */
/* PIE CHART - MODERN */
/* ------------------------------------------------ */
@Composable
fun SubjectPieChart(attendance: List<AnalyticsAttendance>) {
    val grouped = attendance.groupBy { it.subject }
    val total = attendance.size.toFloat().coerceAtLeast(1f)
    val colors = remember {
        listOf(
            Color(0xFF6366F1), // Indigo
            Color(0xFF8B5CF6), // Purple
            Color(0xFFEC4899), // Pink
            Color(0xFFF59E0B), // Amber
            Color(0xFF10B981), // Emerald
            Color(0xFF3B82F6), // Blue
            Color(0xFFEF4444), // Red
            Color(0xFF14B8A6), // Teal
            Color(0xFFF97316), // Orange
            Color(0xFF06B6D4)  // Cyan
        )
    }

    // Get theme colors outside Canvas
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val surfaceColor2 = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(24.dp)) {
            // Enhanced Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF6366F1),
                                    Color(0xFF8B5CF6)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Subject Distribution",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${grouped.size} subjects tracked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Enhanced Pie Chart with 3D effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    var startAngle = -90f
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = size.minDimension / 2.6f

                    // Draw shadow/3D effect
                    grouped.entries.forEachIndexed { index, entry ->
                        val sweep = (entry.value.size / total) * 360f
                        drawArc(
                            color = colors[index % colors.size].copy(alpha = 0.3f),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = true,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                centerX - radius + 4.dp.toPx(),
                                centerY - radius + 4.dp.toPx()
                            ),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                        )
                        startAngle += sweep
                    }

                    // Draw main pie chart
                    startAngle = -90f
                    grouped.entries.forEachIndexed { index, entry ->
                        val sweep = (entry.value.size / total) * 360f

                        // Main arc
                        drawArc(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors[index % colors.size].copy(alpha = 1f),
                                    colors[index % colors.size].copy(alpha = 0.8f)
                                ),
                                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                                radius = radius
                            ),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = true,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                centerX - radius,
                                centerY - radius
                            ),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                        )

                        // Add separator lines between slices
                        if (sweep > 5) {
                            val angle = Math.toRadians((startAngle).toDouble())
                            val lineEndX = centerX + (radius * kotlin.math.cos(angle)).toFloat()
                            val lineEndY = centerY + (radius * kotlin.math.sin(angle)).toFloat()
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = androidx.compose.ui.geometry.Offset(centerX, centerY),
                                end = androidx.compose.ui.geometry.Offset(lineEndX, lineEndY),
                                strokeWidth = 2.dp.toPx()
                            )
                        }

                        startAngle += sweep
                    }

                    // Center donut hole with gradient
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                surfaceColor,
                                surfaceColor2
                            )
                        ),
                        radius = radius * 0.55f,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                    )

                    // Inner circle border
                    drawCircle(
                        color = outlineColor.copy(alpha = 0.1f),
                        radius = radius * 0.55f,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }

                // Center text
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${attendance.size}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Total Lectures",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Enhanced Legend with cards
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                grouped.entries.sortedByDescending { it.value.size }.forEachIndexed { index, entry ->
                    val count = entry.value.size
                    val percentage = ((count / total) * 100).toInt()
                    val colorIndex = grouped.entries.indexOf(entry)

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = colors[colorIndex % colors.size].copy(alpha = 0.08f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = colors[colorIndex % colors.size].copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left side: Color and subject
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    colors[colorIndex % colors.size],
                                                    colors[colorIndex % colors.size].copy(alpha = 0.8f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$percentage%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Column {
                                    Text(
                                        text = entry.key,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$count ${if (count == 1) "lecture" else "lectures"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Right side: Progress bar
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(percentage / 100f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    colors[colorIndex % colors.size],
                                                    colors[colorIndex % colors.size].copy(alpha = 0.7f)
                                                )
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary Stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStatItem(
                    label = "Subjects",
                    value = "${grouped.size}",
                    icon = Icons.Default.School
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )

                SummaryStatItem(
                    label = "Total",
                    value = "${attendance.size}",
                    icon = Icons.Default.CalendarMonth
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )

                SummaryStatItem(
                    label = "Average",
                    value = "${(attendance.size / grouped.size.toFloat()).toInt()}",
                    icon = Icons.Default.BarChart
                )
            }
        }
    }
}

@Composable
private fun SummaryStatItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ------------------------------------------------ */
/* MODERN DATE DIALOG */
/* ------------------------------------------------ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernDateDialog(
    date: LocalDate,
    attendance: List<AnalyticsAttendance>,
    dateFormatter: DateTimeFormatter,
    onDismiss: () -> Unit
) {
    val dayAttendance = attendance.filter { it.date == date }
    val presentCount = dayAttendance.count { it.status == "PRESENT" }
    val absentCount = dayAttendance.count { it.status == "ABSENT" }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clip(RoundedCornerShape(28.dp))
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = date.format(dateFormatter),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${dayAttendance.size} lectures",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                // Stats Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SmallStatCard(
                        label = "Present",
                        value = presentCount.toString(),
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF4CAF50)
                    )
                    SmallStatCard(
                        label = "Absent",
                        value = absentCount.toString(),
                        icon = Icons.Outlined.Cancel,
                        color = Color(0xFFF44336)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Attendance List
                Text(
                    text = "Lecture Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dayAttendance.forEach { item ->
                        val isPresent = item.status == "PRESENT"
                        val statusColor = if (isPresent) Color(0xFF4CAF50) else Color(0xFFF44336)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPresent) Icons.Default.CheckCircle else Icons.Outlined.Cancel,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = item.subject,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = statusColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = item.status,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun SmallStatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ------------------------------------------------ */
/* SKIP PREDICTION */
/* ------------------------------------------------ */
@Composable
fun SkipAttendancePrediction(
    present: Int,
    total: Int
) {
    val currentPercentage = if (total > 0) (present.toFloat() / total * 100).toInt() else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enhanced Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF6B6B),
                                    Color(0xFFEE5A6F)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Skip Predictor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "See impact before you skip",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Current Status Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Current Attendance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$present / $total lectures",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "$currentPercentage%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Prediction Cards
            (1..4).forEach { skip ->
                AdvancedPredictionCard(
                    skip = skip,
                    present = present,
                    total = total
                )
            }

            // Info Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "75% attendance recommended for eligibility",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AdvancedPredictionCard(
    skip: Int,
    present: Int,
    total: Int
) {
    val percent = percentageAfterSkipping(present, total, skip)
    val recoverLectures = lecturesNeededToReach75(present, total, skip)

    val (statusColor, statusText, statusIcon) = when {
        percent >= 75 -> Triple(Color(0xFF4CAF50), "Safe Zone", Icons.Default.CheckCircle)
        percent >= 65 -> Triple(Color(0xFFFF9800), "Warning", Icons.Default.Warning)
        percent >= 60 -> Triple(Color(0xFFFF6B6B), "Risk Zone", Icons.Default.Error)
        else -> Triple(Color(0xFFF44336), "Critical", Icons.Default.Dangerous)
    }

    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(16.dp),
        color = statusColor.copy(alpha = 0.08f),
        border = BorderStroke(
            width = 1.5.dp,
            color = statusColor.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main Content
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Skip Info
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(statusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$skip",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }

                    Column {
                        Text(
                            text = "Skip ${if (skip == 1) "lecture" else "$skip lectures"}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${total + skip} total after skip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Right: Result
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(percent / 100f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    statusColor,
                                    statusColor.copy(alpha = 0.8f)
                                )
                            )
                        )
                )
            }

            // Expandable Recovery Info
            androidx.compose.animation.AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (recoverLectures == 0)
                                    Color(0xFF4CAF50).copy(alpha = 0.1f)
                                else
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (recoverLectures == 0)
                                Icons.Default.CheckCircle
                            else
                                Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            tint = if (recoverLectures == 0)
                                Color(0xFF4CAF50)
                            else
                                MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (recoverLectures == 0)
                                    "You're safe!"
                                else
                                    "Recovery Plan",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (recoverLectures == 0)
                                    "Already maintaining ≥75% attendance"
                                else
                                    "Attend $recoverLectures more ${if (recoverLectures == 1) "lecture" else "lectures"} to reach 75%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Additional Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Present",
                            value = "$present",
                            icon = Icons.Default.CheckCircle,
                            color = Color(0xFF4CAF50)
                        )
                        StatItem(
                            label = "After Skip",
                            value = "${total + skip}",
                            icon = Icons.Default.CalendarMonth,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val difference = if (total > 0) {
                            val currentPercent = present.toFloat() / total * 100
                            String.format(Locale.getDefault(), "%.1f", currentPercent - percent)
                        } else {
                            "0.0"
                        }
                        StatItem(
                            label = "Diff.",
                            value = "-$difference%",
                            icon = Icons.Filled.ArrowDownward,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }
            }

            // Tap to expand indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isExpanded) "Tap to collapse" else "Tap for details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Helper functions
private fun percentageAfterSkipping(present: Int, total: Int, skip: Int): Int {
    val newTotal = total + skip
    return if (newTotal > 0) ((present.toFloat() / newTotal) * 100).toInt() else 0
}

private fun lecturesNeededToReach75(present: Int, total: Int, skip: Int): Int {
    val newTotal = total + skip
    val currentPercent = if (newTotal > 0) (present.toFloat() / newTotal) * 100 else 0f
    if (currentPercent >= 75) return 0

    var lecturesNeeded = 0
    var tempPresent = present
    var tempTotal = newTotal

    while (tempTotal > 0 && (tempPresent.toFloat() / tempTotal * 100) < 75) {
        tempPresent++
        tempTotal++
        lecturesNeeded++
        if (lecturesNeeded > 100) break
    }

    return lecturesNeeded
}