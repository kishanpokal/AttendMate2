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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.launch
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

/* -------------------- DATA MODELS -------------------- */
data class TodayLecture(
    val subjectName: String,
    val status: String,
    val startTime: String,
    val endTime: String,
    val note: String? = null
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
    val haptic = LocalHapticFeedback.current
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
    var popupNote by remember { mutableStateOf("") }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh on resume
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isLoading = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ── Data Loading (keep your existing logic) ─────────────────────────────
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
                    val lectureId = "${todayDate}_${startTime.replace(":", "")}_${endTime.replace(":", "")}"

                    val attendanceRef = db.collection("users")
                        .document(userId)
                        .collection("subjects")
                        .document(subjectId)
                        .collection("attendance")
                        .document(lectureId)

                    if (!attendanceRef.get().await().exists()) {
                        activeLecture = ActiveLecture(
                            subjectId, subjectName, startTime, endTime
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

                    val note = doc.getString("note")

                    todayList.add(
                        TodayLecture(
                            subjectName = subjectName,
                            status = status,
                            startTime = startTime,
                            endTime = endTime,
                            note = note
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
    Box(modifier = Modifier.fillMaxSize()) {
        // Animated gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            /* -------- MODERN HEADER -------- */
            EnhancedHeader(username = username)

            /* -------- ERROR STATE -------- */
            if (errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EnhancedErrorState(errorMessage = errorMessage ?: "Unknown error")
                }
            } else {
                /* -------- CONTENT -------- */
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Summary Card
                    item {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically()
                        ) {
                            if (isLoading) {
                                SkeletonSummaryCard()
                            } else {
                                AttendanceSummaryCard(
                                    total = totalClasses,
                                    attended = attendedClasses
                                )
                            }
                        }
                    }

                    // Section Header
                    item {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically()
                        ) {
                            EnhancedSectionHeader(
                                icon = Icons.Outlined.EventNote,
                                title = "Today's Lectures",
                                subtitle = getCurrentDateString(),
                                onActionClick = null
                            )
                        }
                    }

                    // Lectures List
                    if (isLoading) {
                        items(3) {
                            SkeletonLectureCard()
                        }
                    } else if (todayLectures.isEmpty()) {
                        item {
                            EnhancedEmptyState()
                        }
                    } else {
                        items(todayLectures) { lecture ->
                            EnhancedTodayLectureCard(
                                item = lecture,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Handle click if needed
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    /* -------------------- POPUP -------------------- */
    if (showPopup && activeLecture != null) {
        ModernAttendanceDialog(
            lecture = activeLecture!!,
            note = popupNote,
            onNoteChange = {
                if (it.length <= 200) popupNote = it
            },
            isSaving = isSavingPopup,
            onDismiss = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showPopup = false
                activeLecture = null
                popupNote = ""
            },
            onPresent = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isSavingPopup = true
                savePopupAttendance(
                    db, userId, activeLecture!!, "Present", popupNote
                ) {
                    isSavingPopup = false
                    showPopup = false
                    popupNote = ""
                    isLoading = true
                }
            },
            onAbsent = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isSavingPopup = true
                savePopupAttendance(
                    db, userId, activeLecture!!, "Absent", popupNote
                ) {
                    isSavingPopup = false
                    showPopup = false
                    popupNote = ""
                    isLoading = true
                }
            }
        )
    }
}

/* -------------------- ENHANCED HEADER -------------------- */
@Composable
fun EnhancedHeader(username: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = getGreetingMessage(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hello, $username",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 28.sp
                        )
                    }

                    // Profile Avatar
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = username.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 24.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date and Day
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = getCurrentDateString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/* -------------------- ENHANCED SECTION HEADER -------------------- */
@Composable
fun EnhancedSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onActionClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        if (onActionClick != null) {
            IconButton(onClick = onActionClick) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* -------------------- ENHANCED ERROR STATE -------------------- */
@Composable
fun EnhancedErrorState(errorMessage: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }

        Text(
            text = "Oops!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/* -------------------- HELPER FUNCTIONS -------------------- */
fun getGreetingMessage(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }
}

fun getCurrentDateString(): String {
    val formatter = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
    return formatter.format(Date())
}

/* -------------------- ENHANCED TODAY LECTURE CARD -------------------- */
@Composable
fun EnhancedTodayLectureCard(
    item: TodayLecture,
    onClick: () -> Unit
) {
    val isPresent = item.status == "PRESENT"
    val statusColor = if (isPresent) Color(0xFF10B981) else Color(0xFFEF4444)
    val statusBgColor = statusColor.copy(alpha = 0.12f)

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "lecture_card_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = statusColor.copy(alpha = 0.2f)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.5.dp,
            color = statusColor.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            statusBgColor,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        ),
                        startX = 0f,
                        endX = 1200f
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enhanced Status Icon with Glow
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            spotColor = statusColor.copy(alpha = 0.5f)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    statusColor,
                                    statusColor.copy(alpha = 0.85f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPresent)
                            Icons.Filled.CheckCircle
                        else
                            Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Content Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Subject Name with Gradient
                    Text(
                        text = item.subjectName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Time Row with Enhanced Icon
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "${item.startTime} - ${item.endTime}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Status Badge Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Main Status Badge
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = statusColor,
                            shadowElevation = 3.dp,
                            modifier = Modifier.shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(14.dp),
                                spotColor = statusColor.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Text(
                                    text = if (isPresent) "Present" else "Absent",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Note Indicator (if exists)
                        if (!item.note.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = CircleShape,
                                        spotColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                    )
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.tertiaryContainer,
                                                MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EditNote,
                                    contentDescription = "Has note",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* -------------------- ENHANCED EMPTY STATE -------------------- */
@Composable
fun EnhancedEmptyState() {
    val infiniteTransition = rememberInfiniteTransition(label = "empty_state")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_animation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Animated Icon Container
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .offset(y = floatOffset.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EventNote,
                        contentDescription = null,
                        modifier = Modifier.size(70.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Title
                Text(
                    text = "No Lectures Today",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp
                )

                // Subtitle
                Text(
                    text = "Enjoy your day off! 🎉\nRelax and recharge for tomorrow.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Decorative Element
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        }
    }
}

/* -------------------- MODERN ATTENDANCE DIALOG -------------------- */
@Composable
fun ModernAttendanceDialog(
    lecture: ActiveLecture,
    note: String,
    onNoteChange: (String) -> Unit,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onPresent: () -> Unit,
    onAbsent: () -> Unit
) {
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(200)
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isSaving,
            dismissOnClickOutside = !isSaving
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f * alpha.value))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { if (!isSaving) onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* Prevent dismissal when clicking card */ },
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Close Button
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = { if (!isSaving) onDismiss() },
                                enabled = !isSaving,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 12.dp, y = (-12).dp)
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = CircleShape,
                                        spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                    )
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Animated Icon
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .shadow(
                                    elevation = 20.dp,
                                    shape = CircleShape,
                                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                )
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(50.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Title
                        Text(
                            text = "Mark Attendance",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 26.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Subject Name
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = lecture.subjectName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                fontSize = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Time Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = lecture.startTime,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                                Text(
                                    text = lecture.endTime,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Note Field
                        OutlinedTextField(
                            value = note,
                            onValueChange = onNoteChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    "Reason / Note (optional)",
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            placeholder = {
                                Text("e.g. Sick, Late arrival, Personal work")
                            },
                            maxLines = 3,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        "${note.length}/200",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (note.length > 180)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Absent Button
                            Button(
                                onClick = onAbsent,
                                enabled = !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .shadow(
                                        elevation = if (!isSaving) 8.dp else 2.dp,
                                        shape = RoundedCornerShape(18.dp),
                                        spotColor = Color(0xFFEF4444).copy(alpha = 0.3f)
                                    ),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                border = BorderStroke(
                                    width = 2.dp,
                                    color = if (!isSaving) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (!isSaving) {
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFFEF4444).copy(alpha = 0.1f),
                                                        Color(0xFFDC2626).copy(alpha = 0.1f)
                                                    )
                                                )
                                            } else {
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                                    )
                                                )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.5.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp),
                                                tint = Color(0xFFEF4444)
                                            )
                                            Text(
                                                text = "Absent",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFEF4444),
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Present Button
                            Button(
                                onClick = onPresent,
                                enabled = !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .shadow(
                                        elevation = if (!isSaving) 12.dp else 2.dp,
                                        shape = RoundedCornerShape(18.dp),
                                        spotColor = Color(0xFF10B981).copy(alpha = 0.4f)
                                    ),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (!isSaving) {
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFF10B981),
                                                        Color(0xFF059669)
                                                    )
                                                )
                                            } else {
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                                    )
                                                )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.5.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp),
                                                tint = Color.White
                                            )
                                            Text(
                                                text = "Present",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* -------------------- SKELETON LOADING COMPONENTS -------------------- */
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(4.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(9.dp))
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
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
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                )
            }
        }
    }
}

/* -------------------- SAVE ATTENDANCE FUNCTION -------------------- */
fun savePopupAttendance(
    db: FirebaseFirestore,
    userId: String,
    lecture: ActiveLecture,
    status: String,
    note: String,
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

    val lectureId = "${dateKey}_${SimpleDateFormat("HHmm", Locale.getDefault()).format(startCal.time)}_" +
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
        val attended = if (status == "Present")
            (subjectSnap.getLong("attendedClasses") ?: 0) + 1
        else
            subjectSnap.getLong("attendedClasses") ?: 0

        val attendanceData = mutableMapOf<String, Any>(
            "status" to status,
            "date" to today.time,
            "startTime" to startCal.time,
            "endTime" to endCal.time,
            "createdAt" to Date()
        )

        // Save note only if user entered it
        if (note.isNotBlank()) {
            attendanceData["note"] = note.trim()
        }

        tx.set(attendanceRef, attendanceData)
        tx.update(
            subjectRef,
            mapOf(
                "totalClasses" to total,
                "attendedClasses" to attended
            )
        )
    }.addOnCompleteListener {
        onDone()
    }
}

/* -------------------- ATTENDANCE SUMMARY CARD (from Part 1) -------------------- */

@Composable
fun AttendanceSummaryCard(total: Int, attended: Int) {
    val percentage = if (total == 0) 0f else (attended.toFloat() / total.toFloat()) * 100
    val animatedPercentage = remember { Animatable(0f) }

    LaunchedEffect(percentage) {
        animatedPercentage.animateTo(
            targetValue = percentage,
            animationSpec = tween(durationMillis = 1500, easing = EaseOutCubic)
        )
    }

    val statusColor = when {
        percentage >= 75 -> Color(0xFF10B981) // Green
        percentage >= 60 -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFEF4444) // Red
    }

    val statusText = when {
        percentage >= 75 -> "Excellent"
        percentage >= 60 -> "Good"
        else -> "Needs Improvement"
    }

    val statusIcon = when {
        percentage >= 75 -> Icons.Default.CheckCircle
        percentage >= 60 -> Icons.Default.Warning
        else -> Icons.Default.Error
    }

    // Get surface color outside Canvas
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(8.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            statusColor.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        statusColor,
                                        statusColor.copy(alpha = 0.8f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Overall Attendance",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Enhanced Circular Progress
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(200.dp)
                ) {
                    // Outer glow effect
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    statusColor.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.width / 2
                        )
                    }

                    // Background circle
                    Canvas(modifier = Modifier.size(180.dp)) {
                        drawCircle(
                            color = surfaceVariantColor.copy(alpha = 0.3f),
                            style = Stroke(width = 16.dp.toPx())
                        )
                    }

                    // Progress circle
                    CircularProgressIndicator(
                        progress = { animatedPercentage.value / 100f },
                        modifier = Modifier.size(180.dp),
                        strokeWidth = 16.dp,
                        trackColor = Color.Transparent,
                        color = statusColor
                    )

                    // Center content
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f", animatedPercentage.value),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                fontSize = 48.sp
                            )
                            Text(
                                text = "%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = statusColor.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelLarge,
                                color = statusColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Stats Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EnhancedStatItemMain(
                        label = "Attended",
                        value = attended.toString(),
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF10B981)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(50.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    EnhancedStatItemMain(
                        label = "Total",
                        value = total.toString(),
                        icon = Icons.Default.CalendarMonth,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(50.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    EnhancedStatItemMain(
                        label = "Missed",
                        value = (total - attended).toString(),
                        icon = Icons.Default.Cancel,
                        color = Color(0xFFEF4444)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress indicator
                if (percentage < 75) {
                    val lecturesNeeded = calculateLecturesNeededFor75Percent(attended, total)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF9800).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "To reach 75%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Attend $lecturesNeeded more ${if (lecturesNeeded == 1) "lecture" else "lectures"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Great job!",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "You're maintaining excellent attendance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun EnhancedStatItemMain(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
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
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun calculateLecturesNeededFor75Percent(attended: Int, total: Int): Int {
    if (total == 0) return 0
    val currentPercent = (attended.toFloat() / total) * 100
    if (currentPercent >= 75) return 0

    var tempAttended = attended
    var tempTotal = total
    var needed = 0

    while ((tempAttended.toFloat() / tempTotal * 100) < 75 && needed < 100) {
        tempAttended++
        tempTotal++
        needed++
    }
    return needed
}