package com.kishan.attendmate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.components.AttendMateNavigationBar
import com.kishan.attendmate.ui.theme.AttendMateTheme
import com.kishan.attendmate.ui.auth.LoginActivity
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth = FirebaseAuth.getInstance()
        // 🔐 Auth guard
        if (auth.currentUser == null) {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            return
        }
        // 🔔 Ensure notification channels exist
        createNotificationChannels()
        enableEdgeToEdge()
        setContent {
            AttendMateTheme {
                Scaffold(
                    bottomBar = { AttendMateNavigationBar(selectedRoute = "home") }
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

/* -------------------- DATA MODELS -------------------- */
data class TodayLecture(
    val subjectName: String,
    val status: String,
    val startTime: String,
    val endTime: String
)

data class ActiveLecture(
    val subjectId: String,
    val subjectName: String,
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
    /* Popup state */
    var showPopup by remember { mutableStateOf(false) }
    var activeLecture by remember { mutableStateOf<ActiveLecture?>(null) }
    var isSavingPopup by remember { mutableStateOf(false) }
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
            /* -------- CHECK CURRENT LECTURE -------- */
            val todayDay = LocalDate.now().dayOfWeek.name
            val now = LocalTime.now()
            val timetableSnapshot = db.collection("users")
                .document(userId)
                .collection("timetable")
                .whereEqualTo("day", todayDay)
                .get()
                .await()
            for (doc in timetableSnapshot.documents) {
                val startTime = doc.getString("startTime") ?: continue
                val endTime = doc.getString("endTime") ?: continue
                val start = LocalTime.parse(startTime)
                val end = LocalTime.parse(endTime)
                if (now.isAfter(start) && now.isBefore(end)) {
                    val subjectId = doc.getString("subjectId") ?: continue
                    val subjectName = doc.getString("subjectName") ?: continue
                    val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val lectureId =
                        "${todayDate}_${startTime.replace(":", "")}_${endTime.replace(":", "")}"
                    val attendanceRef = db.collection("users")
                        .document(userId)
                        .collection("subjects")
                        .document(subjectId)
                        .collection("attendance")
                        .document(lectureId)
                    if (!attendanceRef.get().await().exists()) {
                        activeLecture = ActiveLecture(
                            subjectId,
                            subjectName,
                            startTime,
                            endTime
                        )
                        showPopup = true
                    }
                    break
                }
            }
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
                    val todayDate = LocalDate.now().toString()
                    val dateString: String = when (val rawDate = doc.get("date")) {
                        is String -> rawDate
                        is Timestamp -> rawDate.toDate()
                            .toInstant()
                            .atZone(ZoneId.systemDefault())
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

        /* -------- ERROR STATE -------- */
        if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Oops!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Unknown error",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            /* -------- CONTENT WITH SKELETON -------- */
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    if (isLoading) {
                        SkeletonSummaryCard()
                    } else {
                        AttendanceSummaryCard(
                            total = totalClasses,
                            attended = attendedClasses
                        )
                    }
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
                if (isLoading) {
                    items(3) {
                        SkeletonLectureCard()
                    }
                } else if (todayLectures.isEmpty()) {
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

    /* -------------------- ENHANCED POPUP -------------------- */
    if (showPopup && activeLecture != null) {
        AttendanceDialog(
            lecture = activeLecture!!,
            isSaving = isSavingPopup,
            onDismiss = {
                showPopup = false
                activeLecture = null
            },
            onPresent = {
                isSavingPopup = true
                savePopupAttendance(
                    db, userId, activeLecture!!, "Present"
                ) {
                    isSavingPopup = false
                    showPopup = false
                    isLoading = true
                }
            },
            onAbsent = {
                isSavingPopup = true
                savePopupAttendance(
                    db, userId, activeLecture!!, "Absent"
                ) {
                    isSavingPopup = false
                    showPopup = false
                    isLoading = true
                }
            }
        )
    }
}

/* -------------------- SKELETON LOADING -------------------- */
@Composable
fun SkeletonSummaryCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(2) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SkeletonLectureCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                )
            }
        }
    }
}

/* -------------------- ENHANCED ATTENDANCE DIALOG WITH CLOSE BUTTON -------------------- */
@Composable
fun AttendanceDialog(
    lecture: ActiveLecture,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onPresent: () -> Unit,
    onAbsent: () -> Unit
) {
    val scale = remember { Animatable(0.8f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .scale(scale.value),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close Button at Top Right
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { if (!isSaving) onDismiss() },
                        enabled = !isSaving,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-8).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title
                Text(
                    text = "Mark Attendance",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subject
                Text(
                    text = lecture.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Time Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lecture.startTime,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = " - ",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lecture.endTime,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Absent Button
                    OutlinedButton(
                        onClick = onAbsent,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.error,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            )
                        ),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Absent",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Present Button
                    Button(
                        onClick = onPresent,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Present",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
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

/* -------------------- SUMMARY CARD WITH DECIMAL PERCENTAGE -------------------- */
@Composable
fun AttendanceSummaryCard(total: Int, attended: Int) {
    val percentage = if (total == 0) 0f else (attended.toFloat() / total.toFloat()) * 100
    val animatedPercentage = remember { Animatable(0f) }
    LaunchedEffect(percentage) {
        animatedPercentage.animateTo(
            targetValue = percentage,
            animationSpec = tween(durationMillis = 1200, easing = EaseOutCubic)
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
                            text = String.format("%.2f%%", animatedPercentage.value),
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
    val statusColor = if (isPresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val statusBgColor = if (isPresent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
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

/* -------------------- SAVE ATTENDANCE -------------------- */
fun savePopupAttendance(
    db: FirebaseFirestore,
    userId: String,
    lecture: ActiveLecture,
    status: String,
    onDone: () -> Unit
) {
    val today = Calendar.getInstance()
    val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(today.time)
    val startCal = Calendar.getInstance().apply {
        val (h, m) = lecture.startTime.split(":").map { it.toInt() }
        set(Calendar.HOUR_OF_DAY, h)
        set(Calendar.MINUTE, m)
    }
    val endCal = Calendar.getInstance().apply {
        val (h, m) = lecture.endTime.split(":").map { it.toInt() }
        set(Calendar.HOUR_OF_DAY, h)
        set(Calendar.MINUTE, m)
    }
    val lectureId =
        "${dateKey}_${SimpleDateFormat("HHmm", Locale.getDefault()).format(startCal.time)}_" +
                SimpleDateFormat("HHmm", Locale.getDefault()).format(endCal.time)
    val subjectRef = db.collection("users")
        .document(userId)
        .collection("subjects")
        .document(lecture.subjectId)
    val attendanceRef = subjectRef.collection("attendance").document(lectureId)
    db.runTransaction { tx ->
        if (tx.get(attendanceRef).exists()) {
            throw Exception("Attendance already marked")
        }
        val subjectSnap = tx.get(subjectRef)
        val total = (subjectSnap.getLong("totalClasses") ?: 0) + 1
        val attended =
            if (status == "Present")
                (subjectSnap.getLong("attendedClasses") ?: 0) + 1
            else subjectSnap.getLong("attendedClasses") ?: 0
        tx.set(
            attendanceRef,
            mapOf(
                "status" to status,
                "date" to today.time,
                "startTime" to startCal.time,
                "endTime" to endCal.time,
                "createdAt" to Date()
            )
        )
        tx.update(
            subjectRef,
            mapOf(
                "totalClasses" to total,
                "attendedClasses" to attended
            )
        )
    }.addOnCompleteListener { onDone() }
}