package com.kishan.attendmate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.components.AttendMateNavigationBar
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannels()
        enableEdgeToEdge()
        setContent {
            AttendMateTheme {
                Scaffold(
                    bottomBar = {
                        AttendMateNavigationBar(selectedRoute = "home")
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        HomeScreen()
                    }
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val dayConfirmationChannel = NotificationChannel(
                "day_confirmation_channel",
                "Day Confirmation",
                NotificationManager.IMPORTANCE_HIGH
            )
            val lectureReminderChannel = NotificationChannel(
                "lecture_reminder_channel",
                "Lecture Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(dayConfirmationChannel)
            manager.createNotificationChannel(lectureReminderChannel)
        }
    }
}

/* -------------------- DATA MODEL -------------------- */
data class TodayLecture(
    val subjectName: String,
    val status: String,
    val startTime: String,
    val endTime: String
)

/* -------------------- HOME SCREEN -------------------- */
@Composable
fun HomeScreen() {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid ?: return

    var todayLectures by remember { mutableStateOf<List<TodayLecture>>(emptyList()) }
    var totalClasses by remember { mutableStateOf(0) }
    var attendedClasses by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("User") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh on resume
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isLoading = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Data Loading ─────────────────────────────────────────────────────────
    LaunchedEffect(isLoading, userId) {
        if (!isLoading) return@LaunchedEffect

        isLoading = true
        errorMessage = null

        try {
            // 1. Get user info
            val userDoc = db.collection("users").document(userId).get().await()
            username = userDoc.getString("username") ?: "User"

            // 2. Get all subjects
            val subjects = db.collection("users")
                .document(userId)
                .collection("subjects")
                .get()
                .await()

            if (subjects.isEmpty) {
                todayLectures = emptyList()
                totalClasses = 0
                attendedClasses = 0
                isLoading = false
                return@LaunchedEffect
            }

            val todayList = mutableListOf<TodayLecture>()
            var total = 0
            var attended = 0

            /* -------- TODAY RANGE -------- */
            val calendar = Calendar.getInstance()
            val startOfDay = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val endOfDay = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.time

            // 3. For each subject → get attendance
            for (subjectDoc in subjects.documents) {
                val subjectName = subjectDoc.getString("name") ?: continue

                val attendanceSnapshot = subjectDoc.reference
                    .collection("attendance")
                    .get()
                    .await()

                for (doc in attendanceSnapshot.documents) {
                    val status = doc.getString("status")?.uppercase() ?: "ABSENT"

                    total++

                    if (status == "PRESENT") attended++

                    // Handle date
                    val todayDate = java.time.LocalDate.now().toString()

                    val dateString: String = when (val rawDate = doc.get("date")) {
                        is String -> rawDate
                        is Timestamp -> rawDate.toDate()
                            .toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                        else -> continue
                    }

                    if (dateString != todayDate) continue


                    // Handle time
                    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val startTime = doc.get("startTime")?.let { raw ->
                        when (raw) {
                            is Timestamp -> formatter.format(raw.toDate())
                            is String -> raw.takeIf { it.length >= 5 }?.substring(0, 5) ?: "--"
                            else -> "--"
                        }
                    } ?: "--"
                    val endTime = doc.get("endTime")?.let { raw ->
                        when (raw) {
                            is Timestamp -> formatter.format(raw.toDate())
                            is String -> raw.takeIf { it.length >= 5 }?.substring(0, 5) ?: "--"
                            else -> "--"
                        }
                    } ?: "--"

                    todayList.add(
                        TodayLecture(
                            subjectName = subjectName,
                            status = status,
                            startTime = startTime,
                            endTime = endTime
                        )
                    )
                }
            }

            todayLectures = todayList.sortedBy { it.startTime }
            totalClasses = total
            attendedClasses = attended

        } catch (e: Exception) {
            errorMessage = "Failed to load data: ${e.localizedMessage}"
        } finally {
            isLoading = false
        }
    }

    /* ================= UI ================= */
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

                Text(
                    text = "Hello, $username 👋",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = getGreetingMessage(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        /* -------- LOADING STATE -------- */
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading your data...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        /* -------- ERROR STATE -------- */
        if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorMessage ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }

        /* -------- CONTENT -------- */
        AnimatedVisibility(
            visible = !isLoading && errorMessage == null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                item {
                    AttendanceSummaryCard(
                        total = totalClasses,
                        attended = attendedClasses
                    )
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Today's Lectures",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (todayLectures.isEmpty()) {
                    item { EmptyLecturesCard() }
                } else {
                    items(todayLectures) { lecture ->
                        TodayLectureCard(lecture)
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

/* -------------------- HELPER FUNCTION -------------------- */

fun getGreetingMessage(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 0..11 -> "Good Morning!"
        in 12..16 -> "Good Afternoon!"
        in 17..20 -> "Good Evening!"
        else -> "Good Night!"
    }
}

/* -------------------- SUMMARY CARD -------------------- */

@Composable
fun AttendanceSummaryCard(total: Int, attended: Int) {

    val percentage =
        if (total == 0) 0 else ((attended.toFloat() / total.toFloat()) * 100).toInt()

    val animatedPercentage = remember { Animatable(0f) }

    LaunchedEffect(percentage) {
        animatedPercentage.animateTo(
            targetValue = percentage.toFloat(),
            animationSpec = tween(durationMillis = 1000, easing = EaseOutCubic)
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val gradientColors = listOf(
        primaryColor.copy(alpha = 0.15f),
        tertiaryColor.copy(alpha = 0.1f)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = gradientColors
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Overall Attendance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Circular progress indicator
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(140.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { animatedPercentage.value / 100f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 12.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${animatedPercentage.value.toInt()}%",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        label = "Attended",
                        value = attended.toString(),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Divider(
                        modifier = Modifier
                            .height(40.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    StatItem(
                        label = "Total",
                        value = total.toString(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
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

/* -------------------- TODAY LECTURE CARD -------------------- */

@Composable
fun TodayLectureCard(item: TodayLecture) {

    val isPresent = item.status == "PRESENT"

    val statusColor = if (isPresent)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.error

    val statusBgColor = if (isPresent)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.errorContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Status Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(statusBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPresent) Icons.Filled.CheckCircle else Icons.Outlined.Cancel,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.startTime} - ${item.endTime}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBgColor
                ) {
                    Text(
                        text = if (isPresent) "Present" else "Absent",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/* -------------------- EMPTY STATE -------------------- */

@Composable
fun EmptyLecturesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.EventNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No lectures today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enjoy your day off!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}