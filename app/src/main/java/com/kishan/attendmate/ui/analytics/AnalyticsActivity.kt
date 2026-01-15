package com.kishan.attendmate.ui.analytics

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.components.AttendMateNavigationBar
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
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

fun percentageAfterSkipping(
    present: Int,
    total: Int,
    skip: Int
): Int {
    val newTotal = total + skip
    return if (newTotal <= 0) 0 else (present * 100) / newTotal
}

fun lecturesNeededToReach75(
    present: Int,
    total: Int,
    skip: Int
): Int {
    val newTotal = total + skip
    var attended = 0
    var currPresent = present
    var currTotal = newTotal
    while (currTotal > 0 && (currPresent * 100) / currTotal < 75) {
        attended++
        currPresent++
        currTotal++
    }
    return attended
}

data class SkipRecoveryRow(
    val skipped: Int,
    val percentageAfterSkip: Int,
    val lecturesToRecover: Int
)

fun buildSkipRecoveryTable(
    present: Int,
    total: Int
): List<SkipRecoveryRow> {
    return (1..4).map { skip ->
        SkipRecoveryRow(
            skipped = skip,
            percentageAfterSkip = percentageAfterSkipping(present, total, skip),
            lecturesToRecover = lecturesNeededToReach75(present, total, skip)
        )
    }
}

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
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Subject-wise Analysis",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            grouped.forEach { (subject, list) ->
                val present = list.count { it.status == "PRESENT" }
                val total = list.size
                val percent = if (total == 0) 0 else (present * 100 / total)
                val color = subjectColor(percent)
                val animatedProgress = remember { Animatable(0f) }
                LaunchedEffect(percent) {
                    animatedProgress.animateTo(
                        targetValue = percent / 100f,
                        animationSpec = tween(durationMillis = 800, easing = EaseOutCubic)
                    )
                }
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = subject,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = color.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$percent%",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress.value)
                                .clip(RoundedCornerShape(6.dp))
                                .background(color)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$present / $total lectures",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (grouped.keys.last() != subject) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                }
            }
        }
    }
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
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.get(ChronoField.DAY_OF_WEEK) - 1 // 0 = Monday, but for Sunday start, adjust if needed
    val title = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Attendance Calendar",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Month Navigation
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { monthOffset-- },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Previous Month",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { monthOffset++ },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Next Month",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Week Day Headers (Sun - Sat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Calendar Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height(280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    val allPresent = dayAttendance.all { it.status == "PRESENT" }
                    val allAbsent = dayAttendance.all { it.status == "ABSENT" }
                    val bgColor = when {
                        !hasData -> Color.Transparent
                        allPresent -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        allAbsent -> Color(0xFFF44336).copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    }
                    val textColor = when {
                        !hasData -> MaterialTheme.colorScheme.onSurface
                        allPresent -> Color(0xFF4CAF50)
                        allAbsent -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .clickable(enabled = hasData) { onDateClick(date) }
                            .border(
                                width = if (hasData) 1.dp else 0.dp,
                                color = textColor,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (hasData) FontWeight.Bold else FontWeight.Normal,
                            color = textColor
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem(color = Color(0xFF4CAF50), label = "Present")
                LegendItem(color = Color(0xFFF44336), label = "Absent")
                LegendItem(color = MaterialTheme.colorScheme.primary, label = "Mixed")
            }
        }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PieChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Distribution by Subject",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            // Pie Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                var startAngle = -90f
                val centerX = size.width / 2
                val centerY = size.height / 2
                val radius = size.minDimension / 2.5f
                grouped.entries.forEachIndexed { index, entry ->
                    val sweep = (entry.value.size / total) * 360f
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            centerX - radius,
                            centerY - radius
                        ),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                    )
                    startAngle += sweep
                }
                // Center circle for donut effect
                drawCircle(
                    color = Color.White,
                    radius = radius * 0.5f,
                    center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            // Legend with detailed stats
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                grouped.entries.forEachIndexed { index, entry ->
                    val count = entry.value.size
                    val percentage = ((count / total) * 100).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(colors[index % colors.size])
                            )
                            Text(
                                text = entry.key,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$count lectures",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors[index % colors.size].copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "$percentage%",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors[index % colors.size]
                                )
                            }
                        }
                    }
                    if (grouped.entries.last() != entry) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Attendance After Skipping",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            (1..4).forEach { skip ->
                val percent = percentageAfterSkipping(present, total, skip)
                val recoverLectures = lecturesNeededToReach75(present, total, skip)
                val statusColor = when {
                    percent >= 75 -> Color(0xFF4CAF50)
                    percent >= 60 -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }
                val statusText = when {
                    percent >= 75 -> "Safe"
                    percent >= 60 -> "Risk"
                    else -> "Unsafe"
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Skip $skip lecture${if (skip > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$percent%",
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = statusColor.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = statusText,
                                        modifier = Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 4.dp
                                        ),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                }
                            }
                        }
                        // Recovery Info
                        Text(
                            text = if (recoverLectures == 0) "Already ≥ 75% attendance" else "Attend $recoverLectures lecture${if (recoverLectures > 1) "s" else ""} to reach 75%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}