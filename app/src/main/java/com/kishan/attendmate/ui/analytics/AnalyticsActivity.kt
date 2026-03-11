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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.kishan.attendmate.ui.components.AttendMateNavigationBar
import com.kishan.attendmate.ui.theme.AttendMateTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.tasks.await

data class ResponsiveConfig(
        val screenWidth: Int,
        val isPhone: Boolean,
        val cornerRadiusLarge: androidx.compose.ui.unit.Dp,
        val cornerRadiusMedium: androidx.compose.ui.unit.Dp,
        val cornerRadiusSmall: androidx.compose.ui.unit.Dp,
        val cardPadding: PaddingValues,
        val itemSpacing: androidx.compose.ui.unit.Dp,
        val iconSizeLarge: androidx.compose.ui.unit.Dp,
        val iconSizeMedium: androidx.compose.ui.unit.Dp,
        val iconSizeSmall: androidx.compose.ui.unit.Dp,
        val titleSize: androidx.compose.ui.unit.TextUnit,
        val bodySize: androidx.compose.ui.unit.TextUnit,
        val labelSize: androidx.compose.ui.unit.TextUnit,
        val calendarCellSize: androidx.compose.ui.unit.Dp
)

@Composable
fun rememberResponsiveConfig(): ResponsiveConfig {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    return remember(screenWidth) {
        when {
            screenWidth < 360 ->
                    ResponsiveConfig(
                            screenWidth = screenWidth,
                            isPhone = true,
                            cornerRadiusLarge = 16.dp,
                            cornerRadiusMedium = 12.dp,
                            cornerRadiusSmall = 8.dp,
                            cardPadding = PaddingValues(12.dp),
                            itemSpacing = 8.dp,
                            iconSizeLarge = 28.dp,
                            iconSizeMedium = 18.dp,
                            iconSizeSmall = 14.dp,
                            titleSize = 16.sp,
                            bodySize = 13.sp,
                            labelSize = 11.sp,
                            calendarCellSize = 34.dp
                    )
            screenWidth < 720 ->
                    ResponsiveConfig(
                            screenWidth = screenWidth,
                            isPhone = screenWidth < 420,
                            cornerRadiusLarge = 20.dp,
                            cornerRadiusMedium = 14.dp,
                            cornerRadiusSmall = 10.dp,
                            cardPadding = PaddingValues(16.dp),
                            itemSpacing = 12.dp,
                            iconSizeLarge = 34.dp,
                            iconSizeMedium = 20.dp,
                            iconSizeSmall = 16.dp,
                            titleSize = 18.sp,
                            bodySize = 14.sp,
                            labelSize = 12.sp,
                            calendarCellSize = 44.dp
                    )
            else ->
                    ResponsiveConfig(
                            screenWidth = screenWidth,
                            isPhone = false,
                            cornerRadiusLarge = 24.dp,
                            cornerRadiusMedium = 16.dp,
                            cornerRadiusSmall = 12.dp,
                            cardPadding = PaddingValues(20.dp),
                            itemSpacing = 16.dp,
                            iconSizeLarge = 40.dp,
                            iconSizeMedium = 24.dp,
                            iconSizeSmall = 18.dp,
                            titleSize = 20.sp,
                            bodySize = 16.sp,
                            labelSize = 13.sp,
                            calendarCellSize = 52.dp
                    )
        }
    }
}

class AnalyticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendMateTheme {
                Scaffold(
                        bottomBar = {
                            Column {
                                com.kishan.attendmate.ui.components.BannerAd()
                                AttendMateNavigationBar("analytics")
                            }
                        }
                ) { padding -> Box(Modifier.padding(padding)) { AnalyticsScreen() } }
            }
        }
    }
}

data class AnalyticsAttendance(val subject: String, val date: LocalDate, val status: String)

fun lecturesNeededFor75(present: Int, total: Int): Int {
    if (total == 0) return 0
    if (present.toFloat() / total >= 0.75f) return 0
    return ceil((0.75 * total - present) / 0.25).toInt()
}

fun maxBunkableLectures(present: Int, total: Int): Int {
    if (total == 0) return 0
    return floor(present / 0.75 - total).toInt().coerceAtLeast(0)
}

fun subjectColor(percent: Int): Color =
        when {
            percent >= 75 -> Color(0xFF4CAF50)
            percent >= 60 -> Color(0xFFFFC107)
            else -> Color(0xFFF44336)
        }

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
        val observer =
                androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) refreshKey++
                }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /* -------- FIRESTORE LOAD (OFFLINE FIRST) -------- */
    LaunchedEffect(refreshKey) {
        if (!loading && attendance.isNotEmpty()) loading = true

        suspend fun fetchWithSource(source: Source) {
            val temp = mutableListOf<AnalyticsAttendance>()
            val subjects =
                    db.collection("users").document(uid).collection("subjects").get(source).await()
            for (subjectDoc in subjects.documents) {
                val subjectName = subjectDoc.getString("name") ?: continue
                val records = subjectDoc.reference.collection("attendance").get(source).await()
                for (doc in records.documents) {
                    val status = (doc.getString("status") ?: "ABSENT").uppercase()
                    val date: LocalDate? =
                            when (val rawDate = doc.get("date")) {
                                is String -> runCatching { LocalDate.parse(rawDate) }.getOrNull()
                                is Timestamp ->
                                        rawDate.toDate()
                                                .toInstant()
                                                .atZone(ZoneId.systemDefault())
                                                .toLocalDate()
                                else -> null
                            }
                    if (date != null) temp.add(AnalyticsAttendance(subjectName, date, status))
                }
            }
            attendance = temp
        }

        try {
            // 1. Instant Cache Load
            fetchWithSource(Source.CACHE)
            loading = false
        } catch (e: Exception) {
            // Wait for server if cache is empty
        }

        try {
            // 2. Silent Server Update
            fetchWithSource(Source.SERVER)
        } catch (e: Exception) {
            // Network failed, rely on cached data
        } finally {
            loading = false
        }
    }

    val total = attendance.size
    val present = attendance.count { it.status == "PRESENT" }
    val percentage = if (total == 0) 0 else (present * 100 / total)
    val neededFor75 = lecturesNeededFor75(present, total)
    val bunkable = maxBunkableLectures(present, total)
    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    val config = rememberResponsiveConfig()

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(
                                    Brush.verticalGradient(
                                            colors =
                                                    listOf(
                                                            MaterialTheme.colorScheme.surface,
                                                            MaterialTheme.colorScheme
                                                                    .surfaceContainerLowest
                                                    )
                                    )
                            )
    ) {
        /* -------- TOP HEADER -------- */
        Surface(modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                            modifier =
                                    Modifier.size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                Icons.Outlined.Analytics,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                                "Analytics",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 26.sp
                        )
                        Text(
                                "Attendance insights & predictions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (loading && attendance.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        } else {
            if (attendance.isEmpty()) {
                EmptyAnalyticsState()
            } else {
                LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item { ModernOverallCard(percentage, present, total, neededFor75, bunkable) }
                    item { AttendanceTrendLineChart(attendance) }
                    item { SubjectBarGraph(attendance) }
                    item { SmartAttendanceCalendar(config, attendance) { selectedDate = it } }
                    item { SubjectPieChart(attendance) }
                    item { SkipAttendancePrediction(present, total) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }

    selectedDate?.let { date ->
        ModernDateDialog(date, attendance, dateFormatter) { selectedDate = null }
    }
}

@Composable
fun EmptyAnalyticsState() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                    modifier =
                            Modifier.size(120.dp)
                                    .clip(CircleShape)
                                    .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        Icons.Outlined.Analytics,
                        null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
            Text(
                    "No Data Available",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
            )
            Text(
                    "Start tracking your attendance to see analytics here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
            )
        }
    }
}

/* ------------------------------------------------ */
/* ADVANCED TREND LINE CHART */
/* ------------------------------------------------ */
@Composable
fun AttendanceTrendLineChart(attendance: List<AnalyticsAttendance>) {
    val sortedDates = attendance.map { it.date }.distinct().sorted()
    if (sortedDates.size < 2) return

    val trendPoints =
            sortedDates.map { date ->
                val upToDate = attendance.filter { !it.date.isAfter(date) }
                val present = upToDate.count { it.status == "PRESENT" }
                val total = upToDate.size
                val percent = if (total > 0) (present.toFloat() / total * 100) else 0f
                Pair(date, percent)
            }

    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    val expandAnimation = remember { Animatable(0f) }
    LaunchedEffect(attendance) {
        expandAnimation.animateTo(
                1f,
                animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
        )
    }

    Card(
            modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                        modifier =
                                Modifier.size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                                Brush.linearGradient(
                                                        colors =
                                                                listOf(
                                                                        Color(0xFF8B5CF6),
                                                                        Color(0xFF6366F1)
                                                                )
                                                )
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            Icons.Default.Timeline,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            "All-Time Trend",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            "Since ${sortedDates.first().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxPercent =
                            (trendPoints.maxOfOrNull { it.second } ?: 100f).coerceAtMost(100f)
                    val minPercent = (trendPoints.minOfOrNull { it.second } ?: 0f).coerceAtLeast(0f)

                    val yPadding =
                            if (maxPercent == minPercent) 50f else (maxPercent - minPercent) * 0.2f
                    val displayMax = (maxPercent + yPadding).coerceAtMost(100f)
                    val displayMin = (minPercent - yPadding).coerceAtLeast(0f)
                    val yRange = if (displayMax == displayMin) 100f else (displayMax - displayMin)

                    val width = size.width
                    val height = size.height
                    val stepX = if (trendPoints.size > 1) width / (trendPoints.size - 1) else width

                    // 1. Draw Professional Horizontal Grid Lines (25%, 50%, 75%, 100%)
                    val gridSteps = listOf(25f, 50f, 75f, 100f)
                    gridSteps.forEach { step ->
                        val y = height - ((step - displayMin) / yRange * height)
                        if (y in 0f..height) {
                            drawLine(
                                    color = outlineColor.copy(alpha = 0.4f),
                                    start = Offset(0f, y),
                                    end = Offset(width, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect =
                                            PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                            )
                        }
                    }

                    // Map points
                    val offsets =
                            trendPoints.mapIndexed { index, pair ->
                                val x = index * stepX
                                val y = height - ((pair.second - displayMin) / yRange * height)
                                Offset(x, y.toFloat())
                            }

                    val path = Path()
                    path.moveTo(offsets.first().x, offsets.first().y)
                    for (i in 1 until offsets.size) {
                        val prev = offsets[i - 1]
                        val curr = offsets[i]
                        val controlPointX = (prev.x + curr.x) / 2
                        path.cubicTo(controlPointX, prev.y, controlPointX, curr.y, curr.x, curr.y)
                    }

                    val fillPath = Path()
                    fillPath.addPath(path)
                    fillPath.lineTo(offsets.last().x, height)
                    fillPath.lineTo(offsets.first().x, height)
                    fillPath.close()

                    clipRect(right = width * expandAnimation.value) {
                        // Fill Gradient
                        drawPath(
                                path = fillPath,
                                brush =
                                        Brush.verticalGradient(
                                                colors =
                                                        listOf(
                                                                primaryColor.copy(alpha = 0.25f),
                                                                Color.Transparent
                                                        ),
                                                startY = 0f,
                                                endY = height
                                        )
                        )

                        // Main Line Glow
                        drawPath(
                                path = path,
                                color = primaryColor.copy(alpha = 0.3f),
                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Main Line
                        drawPath(
                                path = path,
                                color = primaryColor,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Refined, smaller Professional Dots
                        offsets.forEach { offset ->
                            drawCircle(
                                    color = surfaceColor,
                                    radius = 2.5.dp.toPx(),
                                    center = offset
                            )
                            drawCircle(
                                    color = primaryColor,
                                    radius = 2.5.dp.toPx(),
                                    center = offset,
                                    style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val formatter = DateTimeFormatter.ofPattern("dd MMM")
            val numLabels = 5
            val labelIndices =
                    if (trendPoints.size <= numLabels) trendPoints.indices.toList()
                    else {
                        val step = trendPoints.size.toFloat() / (numLabels - 1)
                        (0 until numLabels)
                                .map { (it * step).toInt().coerceAtMost(trendPoints.lastIndex) }
                                .distinct()
                    }

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labelIndices.forEach { index ->
                    Text(
                            text = trendPoints[index].first.format(formatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------ */
/* MODERN OVERALL CARD */
/* ------------------------------------------------ */
@Composable
fun ModernOverallCard(percentage: Int, present: Int, total: Int, neededFor75: Int, bunkable: Int) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val successColor = Color(0xFF4CAF50)

    val animatedPercentage = remember { Animatable(0f) }
    LaunchedEffect(percentage) {
        animatedPercentage.animateTo(
                targetValue = percentage.toFloat(),
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
    }

    val statusColor =
            when {
                percentage >= 75 -> successColor
                percentage >= 60 -> Color(0xFFFFC107)
                else -> errorColor
            }

    Card(
            modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
                modifier =
                        Modifier.background(
                                Brush.verticalGradient(
                                        colors =
                                                listOf(
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
                                    imageVector =
                                            when {
                                                percentage >= 75 -> Icons.Default.TrendingUp
                                                percentage >= 60 -> Icons.Default.Remove
                                                else -> Icons.Default.TrendingDown
                                            },
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(16.dp)
                            )
                            Text(
                                    text =
                                            when {
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
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                        CircularProgressIndicator(
                                progress = { animatedPercentage.value / 100f },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 14.dp,
                                trackColor =
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                color = statusColor,
                                strokeCap = StrokeCap.Round
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        color =
                                if (percentage < 75) errorColor.copy(alpha = 0.1f)
                                else successColor.copy(alpha = 0.1f)
                ) {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                                imageVector =
                                        if (percentage < 75) Icons.Default.Warning
                                        else Icons.Default.CheckCircle,
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
fun StatColumn(
        label: String,
        value: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        color: Color
) {
    Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
                modifier =
                        Modifier.size(48.dp)
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
            modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors =
                    CardDefaults.cardColors(
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
                        modifier =
                                Modifier.size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                                Brush.linearGradient(
                                                        colors =
                                                                listOf(
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
                    modifier =
                            Modifier.fillMaxWidth()
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
            grouped.entries
                    .sortedByDescending { entry ->
                        val present = entry.value.count { it.status == "PRESENT" }
                        val total = entry.value.size
                        if (total == 0) 0 else (present * 100 / total)
                    }
                    .forEach { (subject, list) ->
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

    val statusIcon =
            when {
                percent >= 75 -> Icons.Default.CheckCircle
                percent >= 60 -> Icons.Default.Warning
                else -> Icons.Default.Error
            }

    val statusText =
            when {
                percent >= 75 -> "Safe"
                percent >= 60 -> "At Risk"
                else -> "Critical"
            }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
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
                        modifier =
                                Modifier.size(40.dp)
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

        // Progress Bar with PERFECTLY aligned 75% Marker using BoxWithConstraints
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val barWidth = maxWidth

            Column {
                Box(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                    // Background bar
                    Box(
                            modifier =
                                    Modifier.fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                    MaterialTheme.colorScheme
                                                            .surfaceContainerHighest
                                            )
                    )

                    // Progress bar with gradient
                    Box(
                            modifier =
                                    Modifier.fillMaxHeight()
                                            .fillMaxWidth(animatedProgress.value)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                    Brush.horizontalGradient(
                                                            colors =
                                                                    listOf(
                                                                            color,
                                                                            color.copy(alpha = 0.8f)
                                                                    )
                                                    )
                                            )
                    )

                    // 75% Threshold Line
                    Box(
                            modifier =
                                    Modifier.fillMaxHeight()
                                            .width(2.dp)
                                            .offset(x = barWidth * 0.75f - 1.dp)
                                            .background(Color(0xFFEF4444))
                    )
                }

                // 75% Label positioned right under the line
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                            modifier =
                                    Modifier.offset(x = barWidth * 0.75f)
                                            .wrapContentWidth(
                                                    align = Alignment.CenterHorizontally,
                                                    unbounded = true
                                            )
                                            .padding(top = 4.dp),
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
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Detailed Stats Row
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(color.copy(alpha = 0.05f))
                                .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatBadge(label = "Present", value = "$present", color = Color(0xFF10B981))

            Box(
                    modifier =
                            Modifier.width(1.dp)
                                    .height(30.dp)
                                    .background(
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )
            )

            StatBadge(label = "Absent", value = "${total - present}", color = Color(0xFFEF4444))

            Box(
                    modifier =
                            Modifier.width(1.dp)
                                    .height(30.dp)
                                    .background(
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )
            )

            StatBadge(
                    label = "Needed",
                    value =
                            if (percent >= 75) "0"
                            else {
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
private fun StatBadge(label: String, value: String, color: Color) {
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
        config: ResponsiveConfig,
        attendance: List<AnalyticsAttendance>,
        onDateClick: (LocalDate) -> Unit
) {
    var monthOffset by remember { mutableStateOf(0) }
    val currentMonth = remember(monthOffset) { YearMonth.now().plusMonths(monthOffset.toLong()) }
    val firstDayOfMonth = currentMonth.atDay(1)
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfWeek = (firstDayOfMonth.dayOfWeek.value - 1) % 7

    val title = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    val today = LocalDate.now()

    Card(
            modifier =
                    Modifier.fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(config.cornerRadiusLarge)),
            shape = RoundedCornerShape(config.cornerRadiusLarge),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
    ) {
        Column(Modifier.padding(config.cardPadding)) {
            // Header
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(config.itemSpacing),
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier =
                                Modifier.size(config.iconSizeLarge * 1.8f)
                                        .clip(RoundedCornerShape(config.cornerRadiusMedium))
                                        .background(
                                                Brush.linearGradient(
                                                        colors =
                                                                listOf(
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.8f
                                                                        )
                                                                )
                                                )
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(config.iconSizeMedium)
                    )
                }
                Text(
                        text = "Attendance Calendar",
                        fontWeight = FontWeight.Bold,
                        fontSize = config.titleSize,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(config.itemSpacing))

            // Month Navigation
            Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                        onClick = { monthOffset-- },
                        modifier =
                                Modifier.size(config.iconSizeLarge * 1.8f)
                                        .clip(RoundedCornerShape(config.cornerRadiusSmall))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Previous Month",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(config.iconSizeMedium)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = config.titleSize,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                    )
                }

                IconButton(
                        onClick = { monthOffset++ },
                        modifier =
                                Modifier.size(config.iconSizeLarge * 1.8f)
                                        .clip(RoundedCornerShape(config.cornerRadiusSmall))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Next Month",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(config.iconSizeMedium)
                    )
                }
            }

            Spacer(modifier = Modifier.height(config.itemSpacing))

            // Week Day Headers
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                    Box(
                            modifier = Modifier.size(config.calendarCellSize),
                            contentAlignment = Alignment.Center
                    ) {
                        Text(
                                text = day,
                                textAlign = TextAlign.Center,
                                fontSize = config.labelSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(config.itemSpacing / 2))

            val gridHeight = (config.calendarCellSize + 6.dp) * 6
            LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(gridHeight),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(firstDayOfWeek) { Box(modifier = Modifier.size(config.calendarCellSize)) }

                items(daysInMonth) { index ->
                    val date = firstDayOfMonth.plusDays(index.toLong())
                    val dayAttendance = attendance.filter { it.date == date }
                    val hasData = dayAttendance.isNotEmpty()
                    val isToday = date == today

                    val allPresent = dayAttendance.all { it.status == "PRESENT" }
                    val allAbsent = dayAttendance.all { it.status == "ABSENT" }

                    val bgColor =
                            when {
                                !hasData && isToday ->
                                        MaterialTheme.colorScheme.primaryContainer.copy(
                                                alpha = 0.3f
                                        )
                                !hasData -> Color.Transparent
                                allPresent -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                allAbsent -> Color(0xFFF44336).copy(alpha = 0.15f)
                                else -> Color(0xFF0CFDCD).copy(alpha = 0.15f)
                            }

                    val borderColor =
                            when {
                                isToday && !hasData -> MaterialTheme.colorScheme.primary
                                allPresent -> Color(0xFF4CAF50)
                                allAbsent -> Color(0xFFF44336)
                                hasData -> Color(0xFF0CFDCD)
                                else -> Color.Transparent
                            }

                    val textColor =
                            when {
                                allPresent -> Color(0xFF2E7D32)
                                allAbsent -> Color(0xFFC62828)
                                hasData -> Color(0xFF0CFDCD)
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            }

                    Box(
                            modifier =
                                    Modifier.size(config.calendarCellSize)
                                            .clip(RoundedCornerShape(config.cornerRadiusSmall))
                                            .background(bgColor)
                                            .clickable(enabled = hasData) { onDateClick(date) }
                                            .border(
                                                    width = if (hasData || isToday) 2.dp else 0.dp,
                                                    color = borderColor,
                                                    shape =
                                                            RoundedCornerShape(
                                                                    config.cornerRadiusSmall
                                                            )
                                            ),
                            contentAlignment = Alignment.Center
                    ) {
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                    text = "${index + 1}",
                                    fontSize = config.labelSize,
                                    fontWeight =
                                            if (hasData || isToday) FontWeight.Bold
                                            else FontWeight.Normal,
                                    color = textColor
                            )

                            if (hasData) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                        modifier =
                                                Modifier.size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(borderColor)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(config.itemSpacing))

            // Legend
            if (config.isPhone) {
                Column(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(config.cornerRadiusMedium))
                                        .background(
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                                        .copy(alpha = 0.5f)
                                        )
                                        .padding(config.itemSpacing),
                        verticalArrangement = Arrangement.spacedBy(config.itemSpacing / 2),
                        horizontalAlignment = Alignment.Start
                ) {
                    EnhancedLegendItem(config, Color(0xFF4CAF50), "Present")
                    EnhancedLegendItem(config, Color(0xFFF44336), "Absent")
                    EnhancedLegendItem(config, Color(0xFF0CFDCD), "Mixed")
                }
            } else {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(config.cornerRadiusMedium))
                                        .background(
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                                        .copy(alpha = 0.5f)
                                        )
                                        .padding(config.itemSpacing),
                        horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EnhancedLegendItem(config, Color(0xFF4CAF50), "Present")
                    EnhancedLegendItem(config, Color(0xFFF44336), "Absent")
                    EnhancedLegendItem(config, Color(0xFF0CFDCD), "Mixed")
                }
            }
        }
    }
}

@Composable
private fun EnhancedLegendItem(config: ResponsiveConfig, color: Color, label: String) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(config.itemSpacing / 2)
    ) {
        Box(
                modifier =
                        Modifier.size(config.iconSizeSmall)
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
                fontSize = config.labelSize,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CompactAttendanceCalendar(
        config: ResponsiveConfig,
        attendance: List<AnalyticsAttendance>,
        onDateClick: (LocalDate) -> Unit
) {
    var monthOffset by remember { mutableStateOf(0) }
    val currentMonth = remember(monthOffset) { YearMonth.now().plusMonths(monthOffset.toLong()) }
    val firstDayOfMonth = currentMonth.atDay(1)
    val daysInMonth = currentMonth.lengthOfMonth()

    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7

    val title = currentMonth.format(DateTimeFormatter.ofPattern("MMM yyyy"))
    val today = LocalDate.now()

    Card(
            modifier =
                    Modifier.fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(config.cornerRadiusLarge)),
            shape = RoundedCornerShape(config.cornerRadiusLarge),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
    ) {
        Column(Modifier.padding(config.cardPadding)) {
            // Compact Header
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier =
                                Modifier.size(config.iconSizeLarge * 1.5f)
                                        .clip(RoundedCornerShape(config.cornerRadiusMedium))
                                        .background(
                                                Brush.linearGradient(
                                                        colors =
                                                                listOf(
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.8f
                                                                        )
                                                                )
                                                )
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(config.iconSizeMedium)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = "Calendar",
                            fontWeight = FontWeight.Bold,
                            fontSize = config.bodySize,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                    )
                    Text(
                            text = "Attendance tracker",
                            fontSize = config.labelSize * 0.9f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(config.itemSpacing))

            // Compact Month Navigation
            Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                        onClick = { monthOffset-- },
                        modifier =
                                Modifier.size(config.iconSizeLarge * 1.5f)
                                        .clip(RoundedCornerShape(config.cornerRadiusSmall))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(config.iconSizeSmall)
                    )
                }

                Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = config.bodySize,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                )

                IconButton(
                        onClick = { monthOffset++ },
                        modifier =
                                Modifier.size(config.iconSizeLarge * 1.5f)
                                        .clip(RoundedCornerShape(config.cornerRadiusSmall))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(config.iconSizeSmall)
                    )
                }
            }

            Spacer(modifier = Modifier.height(config.itemSpacing / 2))

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Box(
                            modifier = Modifier.size(config.calendarCellSize * 0.9f),
                            contentAlignment = Alignment.Center
                    ) {
                        Text(
                                text = day,
                                textAlign = TextAlign.Center,
                                fontSize = config.labelSize * 0.9f,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val compactCellSize = config.calendarCellSize * 0.9f
            val gridHeight = (compactCellSize + 4.dp) * 6

            LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(gridHeight),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(firstDayOfWeek) { Box(modifier = Modifier.size(compactCellSize)) }

                items(daysInMonth) { index ->
                    val date = firstDayOfMonth.plusDays(index.toLong())
                    val dayAttendance = attendance.filter { it.date == date }
                    val hasData = dayAttendance.isNotEmpty()
                    val isToday = date == today

                    val allPresent = dayAttendance.all { it.status == "PRESENT" }
                    val allAbsent = dayAttendance.all { it.status == "ABSENT" }

                    val bgColor =
                            when {
                                !hasData && isToday ->
                                        MaterialTheme.colorScheme.primaryContainer.copy(
                                                alpha = 0.3f
                                        )
                                !hasData -> Color.Transparent
                                allPresent -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                allAbsent -> Color(0xFFF44336).copy(alpha = 0.15f)
                                else -> Color(0xFF0CFDCD).copy(alpha = 0.15f)
                            }

                    val borderColor =
                            when {
                                isToday && !hasData -> MaterialTheme.colorScheme.primary
                                allPresent -> Color(0xFF4CAF50)
                                allAbsent -> Color(0xFFF44336)
                                hasData -> Color(0xFF0CFDCD)
                                else -> Color.Transparent
                            }

                    val textColor =
                            when {
                                allPresent -> Color(0xFF2E7D32)
                                allAbsent -> Color(0xFFC62828)
                                hasData -> Color(0xFF0CFDCD)
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            }

                    Box(
                            modifier =
                                    Modifier.size(compactCellSize)
                                            .clip(
                                                    RoundedCornerShape(
                                                            config.cornerRadiusSmall * 0.7f
                                                    )
                                            )
                                            .background(bgColor)
                                            .clickable(enabled = hasData) { onDateClick(date) }
                                            .border(
                                                    width =
                                                            if (hasData || isToday) 1.5.dp
                                                            else 0.dp,
                                                    color = borderColor,
                                                    shape =
                                                            RoundedCornerShape(
                                                                    config.cornerRadiusSmall * 0.7f
                                                            )
                                            ),
                            contentAlignment = Alignment.Center
                    ) {
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                    text = "${index + 1}",
                                    fontSize = config.labelSize * 0.9f,
                                    fontWeight =
                                            if (hasData || isToday) FontWeight.Bold
                                            else FontWeight.Normal,
                                    color = textColor
                            )

                            if (hasData) {
                                Spacer(modifier = Modifier.height(1.dp))
                                Box(
                                        modifier =
                                                Modifier.size(3.dp)
                                                        .clip(CircleShape)
                                                        .background(borderColor)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(config.itemSpacing / 2))

            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(config.cornerRadiusMedium))
                                    .background(
                                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                                    alpha = 0.5f
                                            )
                                    )
                                    .padding(config.itemSpacing),
                    verticalArrangement = Arrangement.spacedBy(config.itemSpacing / 3),
                    horizontalAlignment = Alignment.Start
            ) {
                CompactLegendItem(config, Color(0xFF4CAF50), "Present")
                CompactLegendItem(config, Color(0xFFF44336), "Absent")
                CompactLegendItem(config, Color(0xFF0CFDCD), "Mixed")
            }
        }
    }
}

@Composable
private fun CompactLegendItem(config: ResponsiveConfig, color: Color, label: String) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(config.itemSpacing / 2)
    ) {
        Box(
                modifier =
                        Modifier.size(config.iconSizeSmall * 0.8f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color.copy(alpha = 0.3f))
                                .border(
                                        width = 1.5.dp,
                                        color = color,
                                        shape = RoundedCornerShape(3.dp)
                                )
        )
        Text(
                text = label,
                fontSize = config.labelSize * 0.9f,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
        )
    }
}

@Composable
fun SmartAttendanceCalendar(
        config: ResponsiveConfig,
        attendance: List<AnalyticsAttendance>,
        onDateClick: (LocalDate) -> Unit
) {
    if (config.screenWidth < 360) {
        CompactAttendanceCalendar(config, attendance, onDateClick)
    } else {
        AttendanceCalendar(config, attendance, onDateClick)
    }
}

/* ------------------------------------------------ */
/* PIE CHART - MODERN (ANIMATED) */
/* ------------------------------------------------ */
@Composable
fun SubjectPieChart(attendance: List<AnalyticsAttendance>) {
    val grouped = attendance.groupBy { it.subject }
    val total = attendance.size.toFloat().coerceAtLeast(1f)
    val colors = remember {
        listOf(
                Color(0xFF6366F1),
                Color(0xFF8B5CF6),
                Color(0xFFEC4899),
                Color(0xFFF59E0B),
                Color(0xFF10B981),
                Color(0xFF3B82F6),
                Color(0xFFEF4444),
                Color(0xFF14B8A6),
                Color(0xFFF97316),
                Color(0xFF06B6D4)
        )
    }

    // Expanding pie animation
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(attendance) {
        animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
        )
    }

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val surfaceColor2 = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline

    Card(
            modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors =
                    CardDefaults.cardColors(
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
                        modifier =
                                Modifier.size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                                Brush.linearGradient(
                                                        colors =
                                                                listOf(
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

            // Animated Pie Chart with 3D effect
            Box(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = size.minDimension / 2.6f

                    // Draw shadow/3D effect incrementally
                    grouped.entries.forEachIndexed { index, entry ->
                        val targetSweep = (entry.value.size / total) * 360f
                        val currentSweep = targetSweep * animProgress.value

                        drawArc(
                                color = colors[index % colors.size].copy(alpha = 0.3f),
                                startAngle = startAngle,
                                sweepAngle = currentSweep,
                                useCenter = true,
                                topLeft =
                                        androidx.compose.ui.geometry.Offset(
                                                centerX - radius + 4.dp.toPx(),
                                                centerY - radius + 4.dp.toPx()
                                        ),
                                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                        )
                        startAngle += currentSweep
                    }

                    // Reset start angle for main pie slices
                    startAngle = -90f
                    grouped.entries.forEachIndexed { index, entry ->
                        val targetSweep = (entry.value.size / total) * 360f
                        val currentSweep = targetSweep * animProgress.value

                        // Main arc
                        drawArc(
                                brush =
                                        Brush.radialGradient(
                                                colors =
                                                        listOf(
                                                                colors[index % colors.size].copy(
                                                                        alpha = 1f
                                                                ),
                                                                colors[index % colors.size].copy(
                                                                        alpha = 0.8f
                                                                )
                                                        ),
                                                center =
                                                        androidx.compose.ui.geometry.Offset(
                                                                centerX,
                                                                centerY
                                                        ),
                                                radius = radius
                                        ),
                                startAngle = startAngle,
                                sweepAngle = currentSweep,
                                useCenter = true,
                                topLeft =
                                        androidx.compose.ui.geometry.Offset(
                                                centerX - radius,
                                                centerY - radius
                                        ),
                                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                        )

                        // Add separator lines between slices (only if they are large enough and
                        // fully revealed)
                        if (currentSweep > 5) {
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
                        startAngle += currentSweep
                    }

                    // Center donut hole with gradient
                    drawCircle(
                            brush =
                                    Brush.radialGradient(
                                            colors = listOf(surfaceColor, surfaceColor2)
                                    ),
                            radius = radius * 0.55f,
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                    )

                    // Inner circle border
                    drawCircle(
                            color = outlineColor.copy(alpha = 0.1f),
                            radius = radius * 0.55f,
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                            style =
                                    androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 1.dp.toPx()
                                    )
                    )
                }

                // Center text
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                    Text(
                            text = "${(attendance.size * animProgress.value).toInt()}",
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                grouped.entries.sortedByDescending { it.value.size }.forEachIndexed { index, entry
                    ->
                    val count = entry.value.size
                    val percentage = ((count / total) * 100).toInt()
                    val colorIndex = grouped.entries.indexOf(entry)

                    Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = colors[colorIndex % colors.size].copy(alpha = 0.08f),
                            border =
                                    BorderStroke(
                                            width = 1.dp,
                                            color =
                                                    colors[colorIndex % colors.size].copy(
                                                            alpha = 0.2f
                                                    )
                                    )
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
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
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                                Brush.linearGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        colors[
                                                                                                colorIndex %
                                                                                                        colors.size],
                                                                                        colors[
                                                                                                        colorIndex %
                                                                                                                colors.size]
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.8f
                                                                                                )
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
                                            text =
                                                    "$count ${if (count == 1) "lecture" else "lectures"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Right side: Progress bar
                            Box(
                                    modifier =
                                            Modifier.width(80.dp)
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                            MaterialTheme.colorScheme
                                                                    .surfaceContainerHighest
                                                    )
                            ) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxHeight()
                                                        .fillMaxWidth(
                                                                (percentage / 100f) *
                                                                        animProgress.value
                                                        ) // Animate list bars too
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                                Brush.horizontalGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        colors[
                                                                                                colorIndex %
                                                                                                        colors.size],
                                                                                        colors[
                                                                                                        colorIndex %
                                                                                                                colors.size]
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.7f
                                                                                                )
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
                    modifier =
                            Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.3f
                                            )
                                    )
                                    .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStatItem(
                        label = "Subjects",
                        value = "${grouped.size}",
                        icon = Icons.Default.School
                )

                Box(
                        modifier =
                                Modifier.width(1.dp)
                                        .height(40.dp)
                                        .background(
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        )
                )

                SummaryStatItem(
                        label = "Total",
                        value = "${attendance.size}",
                        icon = Icons.Default.CalendarMonth
                )

                Box(
                        modifier =
                                Modifier.width(1.dp)
                                        .height(40.dp)
                                        .background(
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        )
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
private fun SummaryStatItem(label: String, value: String, icon: ImageVector) {
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
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.clip(RoundedCornerShape(28.dp))) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp)) {
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
                                modifier =
                                        Modifier.size(40.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        MaterialTheme.colorScheme.primaryContainer
                                                ),
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
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                ) {
                    dayAttendance.forEach { item ->
                        val isPresent = item.status == "PRESENT"
                        val statusColor = if (isPresent) Color(0xFF4CAF50) else Color(0xFFF44336)
                        Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                            imageVector =
                                                    if (isPresent) Icons.Default.CheckCircle
                                                    else Icons.Outlined.Cancel,
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                            text = item.subject,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = statusColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                            text = item.status,
                                            modifier =
                                                    Modifier.padding(
                                                            horizontal = 12.dp,
                                                            vertical = 6.dp
                                                    ),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            maxLines = 1,
                                            softWrap = false
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
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                )
                ) { Text("Close") }
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
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.1f)) {
        Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                    modifier =
                            Modifier.size(36.dp)
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
fun SkipAttendancePrediction(present: Int, total: Int) {
    val currentPercentage = if (total > 0) (present.toFloat() / total * 100).toInt() else 0

    Card(
            modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors =
                    CardDefaults.cardColors(
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
                        modifier =
                                Modifier.size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                                Brush.linearGradient(
                                                        colors =
                                                                listOf(
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
                                color =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.7f
                                        )
                        )
                    }

                    Box(
                            modifier =
                                    Modifier.clip(RoundedCornerShape(12.dp))
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
                AdvancedPredictionCard(skip = skip, present = present, total = total)
            }

            // Info Footer
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                                    alpha = 0.5f
                                            )
                                    )
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
private fun AdvancedPredictionCard(skip: Int, present: Int, total: Int) {
    val percent = percentageAfterSkipping(present, total, skip)
    val recoverLectures = lecturesNeededToReach75(present, total, skip)

    val (statusColor, statusText, statusIcon) =
            when {
                percent >= 75 -> Triple(Color(0xFF4CAF50), "Safe Zone", Icons.Default.CheckCircle)
                percent >= 65 -> Triple(Color(0xFFFF9800), "Warning", Icons.Default.Warning)
                percent >= 60 -> Triple(Color(0xFFFF6B6B), "Risk Zone", Icons.Default.Error)
                else -> Triple(Color(0xFFF44336), "Critical", Icons.Default.Dangerous)
            }

    var isExpanded by remember { mutableStateOf(false) }

    Surface(
            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(16.dp),
            color = statusColor.copy(alpha = 0.08f),
            border = BorderStroke(width = 1.5.dp, color = statusColor.copy(alpha = 0.3f))
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
                            modifier =
                                    Modifier.size(44.dp)
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
                    modifier =
                            Modifier.fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                        modifier =
                                Modifier.fillMaxHeight()
                                        .fillMaxWidth(percent / 100f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                                Brush.horizontalGradient(
                                                        colors =
                                                                listOf(
                                                                        statusColor,
                                                                        statusColor.copy(
                                                                                alpha = 0.8f
                                                                        )
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
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                    if (recoverLectures == 0)
                                                            Color(0xFF4CAF50).copy(alpha = 0.1f)
                                                    else
                                                            MaterialTheme.colorScheme
                                                                    .secondaryContainer.copy(
                                                                    alpha = 0.5f
                                                            )
                                            )
                                            .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector =
                                        if (recoverLectures == 0) Icons.Default.CheckCircle
                                        else Icons.Filled.ArrowUpward,
                                contentDescription = null,
                                tint =
                                        if (recoverLectures == 0) Color(0xFF4CAF50)
                                        else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    text =
                                            if (recoverLectures == 0) "You're safe!"
                                            else "Recovery Plan",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                    text =
                                            if (recoverLectures == 0)
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
                        val difference =
                                if (total > 0) {
                                    val currentPercent = present.toFloat() / total * 100
                                    String.format(
                                            Locale.getDefault(),
                                            "%.1f",
                                            currentPercent - percent
                                    )
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
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
private fun StatItem(label: String, value: String, icon: ImageVector, color: Color) {
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
