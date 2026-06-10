package com.kishan.attendmate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Source
import com.kishan.attendmate.ui.auth.LoginActivity
import com.kishan.attendmate.ui.components.*
import com.kishan.attendmate.ui.theme.*
import com.kishan.attendmate.ui.widget.WidgetUpdateWorker
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.kishan.attendmate.ui.widget.WidgetSyncScheduler

class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                val auth = FirebaseAuth.getInstance()

                // 🔐 Auth guard
                if (auth.currentUser == null) {
                        startActivity(
                                Intent(this, LoginActivity::class.java).apply {
                                        flags =
                                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                        )
                        return
                }

                // 🔔 Ensure notification channels exist
                createNotificationChannels()

                enableEdgeToEdge()

                // Enqueue background work to keep the widget up to date
                WidgetSyncScheduler.schedulePeriodicUpdate(applicationContext)
                setContent {
                        AttendMateTheme {
                                Scaffold(
                                        bottomBar = {
                                                AttendMateNavigationBar(selectedRoute = "home")
                                        }
                                ) { paddingValues ->
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxSize()
                                                                .padding(paddingValues)
                                        ) { HomeScreen() }
                                }
                        }
                }
        }

        private fun createNotificationChannels() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val dayConfirmationChannel =
                                NotificationChannel(
                                        "day_confirmation_channel",
                                        "Day Confirmation",
                                        NotificationManager.IMPORTANCE_HIGH
                                )
                        val lectureReminderChannel =
                                NotificationChannel(
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
        val endTime: String,
        val lectureId: String // Added property to handle snooze effectively
)

data class FetchResult(val lectures: List<TodayLecture>, val total: Int, val attended: Int)

/* -------------------- HOME SCREEN -------------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
        val haptic = LocalHapticFeedback.current
        val configuration = LocalConfiguration.current
        val context = LocalContext.current
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid ?: return

        // Shared Preferences for 10-minute snooze logic
        val prefs = remember { context.getSharedPreferences("AttendMatePrefs", Context.MODE_PRIVATE) }

        var todayLectures by remember { mutableStateOf<List<TodayLecture>>(emptyList()) }
        var totalClasses by remember { mutableIntStateOf(0) }
        var attendedClasses by remember { mutableIntStateOf(0) }

        // UI States
        var isLoading by remember { mutableStateOf(true) }
        var isRefreshing by remember { mutableStateOf(false) } // For swipe down
        var username by remember { mutableStateOf("User") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        // Logic States
        var refreshTrigger by remember { mutableIntStateOf(0) }
        var forceServerFetch by remember { mutableStateOf(false) }

        /* Popup state */
        var showPopup by remember { mutableStateOf(false) }
        var activeLecture by remember { mutableStateOf<ActiveLecture?>(null) }
        var isSavingPopup by remember { mutableStateOf(false) }
        var popupNote by remember { mutableStateOf("") }

        val lifecycleOwner = LocalLifecycleOwner.current

        // Refresh on resume
        DisposableEffect(lifecycleOwner) {
                val observer =
                        androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                        refreshTrigger++
                                }
                        }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // ── Safe Offline-First & Server-First Data Loading ─────────────────────────────
        LaunchedEffect(refreshTrigger, userId) {
                if (todayLectures.isEmpty() && !isRefreshing) {
                        isLoading = true
                }
                errorMessage = null

                suspend fun fetchAttendanceData(source: Source): FetchResult {
                        val userDoc = db.collection("users").document(userId).get(source).await()
                        username = userDoc.getString("username") ?: "User"

                        /* -------- CHECK CURRENT LECTURE -------- */
                        val todayDay = LocalDate.now().dayOfWeek.name
                        val now = LocalTime.now()

                        val timetableSnapshot =
                                db.collection("users")
                                        .document(userId)
                                        .collection("timetable")
                                        .whereEqualTo("day", todayDay)
                                        .get(source)
                                        .await()

                        timetableSnapshot.documents.forEach { doc ->
                                val startTimeRaw = doc.getString("startTime") ?: return@forEach
                                val endTimeRaw = doc.getString("endTime") ?: return@forEach

                                val start =
                                        runCatching {
                                                LocalTime.parse(
                                                        startTimeRaw.padStart(5, '0')
                                                )
                                        }.getOrNull() ?: return@forEach
                                val end =
                                        runCatching { LocalTime.parse(endTimeRaw.padStart(5, '0')) }
                                                .getOrNull() ?: return@forEach

                                if (now.isAfter(start) && now.isBefore(end)) {
                                        val subjectId = doc.getString("subjectId") ?: return@forEach
                                        val subjectName = doc.getString("subjectName") ?: return@forEach

                                        // Safely construct ID similar to AddAttendanceActivity
                                        val startCalSafe = Calendar.getInstance().apply {
                                                val (h, m) = startTimeRaw.split(":").map { it.trim().toInt() }
                                                set(Calendar.HOUR_OF_DAY, h)
                                                set(Calendar.MINUTE, m)
                                        }
                                        val endCalSafe = Calendar.getInstance().apply {
                                                val (h, m) = endTimeRaw.split(":").map { it.trim().toInt() }
                                                set(Calendar.HOUR_OF_DAY, h)
                                                set(Calendar.MINUTE, m)
                                        }
                                        val safeDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                        val safeStartKey = SimpleDateFormat("HHmm", Locale.getDefault()).format(startCalSafe.time)
                                        val safeEndKey = SimpleDateFormat("HHmm", Locale.getDefault()).format(endCalSafe.time)
                                        val lectureId = "${safeDateKey}_${safeStartKey}_${safeEndKey}"

                                        val attendanceExists =
                                                db.collection("users")
                                                        .document(userId)
                                                        .collection("subjects")
                                                        .document(subjectId)
                                                        .collection("attendance")
                                                        .document(lectureId)
                                                        .get(source)
                                                        .await()
                                                        .exists()

                                        if (!attendanceExists) {
                                                // Check snooze
                                                val snoozeKey = "snooze_$lectureId"
                                                val snoozeTime = prefs.getLong(snoozeKey, 0L)
                                                val currentTimeMillis = System.currentTimeMillis()
                                                val tenMinutesInMillis = 10 * 60 * 1000L

                                                if (currentTimeMillis - snoozeTime > tenMinutesInMillis) {
                                                        activeLecture = ActiveLecture(
                                                                subjectId,
                                                                subjectName,
                                                                startTimeRaw,
                                                                endTimeRaw,
                                                                lectureId
                                                        )
                                                        showPopup = true
                                                }
                                        }
                                }
                        }

                        // 2. Get all subjects
                        val subjects =
                                db.collection("users")
                                        .document(userId)
                                        .collection("subjects")
                                        .get(source)
                                        .await()

                        if (subjects.isEmpty) {
                                return FetchResult(emptyList(), 0, 0)
                        }

                        val todayList = mutableListOf<TodayLecture>()
                        var total = 0
                        var attended = 0

                        // 3. For each subject → get attendance
                        for (subjectDoc in subjects.documents) {
                                val subjectName = subjectDoc.getString("name") ?: continue

                                val attendanceSnapshot =
                                        subjectDoc
                                                .reference
                                                .collection("attendance")
                                                .get(source)
                                                .await()

                                for (doc in attendanceSnapshot.documents) {
                                        val status =
                                                doc.getString("status")?.uppercase() ?: "ABSENT"
                                        total++
                                        if (status == "PRESENT") attended++

                                        val todayDate = LocalDate.now().toString()
                                        val dateString: String =
                                                when (val rawDate = doc.get("date")) {
                                                        is String -> rawDate
                                                        is Timestamp ->
                                                                rawDate.toDate()
                                                                        .toInstant()
                                                                        .atZone(
                                                                                ZoneId.systemDefault()
                                                                        )
                                                                        .toLocalDate()
                                                                        .toString()
                                                        else -> continue
                                                }
                                        if (dateString != todayDate) continue

                                        val formatter =
                                                SimpleDateFormat("HH:mm", Locale.getDefault())
                                        val startTime =
                                                doc.get("startTime")?.let { raw ->
                                                        when (raw) {
                                                                is Timestamp ->
                                                                        formatter.format(
                                                                                raw.toDate()
                                                                        )
                                                                is String ->
                                                                        raw
                                                                                .takeIf {
                                                                                        it.length >=
                                                                                                5
                                                                                }
                                                                                ?.substring(0, 5)
                                                                                ?: "--"
                                                                else -> "--"
                                                        }
                                                }
                                                        ?: "--"

                                        val endTime =
                                                doc.get("endTime")?.let { raw ->
                                                        when (raw) {
                                                                is Timestamp ->
                                                                        formatter.format(
                                                                                raw.toDate()
                                                                        )
                                                                is String ->
                                                                        raw
                                                                                .takeIf {
                                                                                        it.length >=
                                                                                                5
                                                                                }
                                                                                ?.substring(0, 5)
                                                                                ?: "--"
                                                                else -> "--"
                                                        }
                                                }
                                                        ?: "--"

                                        val note = doc.getString("note")

                                        todayList.add(
                                                TodayLecture(
                                                        subjectName,
                                                        status,
                                                        startTime,
                                                        endTime,
                                                        note
                                                )
                                        )
                                }
                        }

                        return FetchResult(todayList.sortedBy { it.startTime }, total, attended)
                }

                try {
                        if (!forceServerFetch && !isRefreshing) {
                                val cacheData = fetchAttendanceData(Source.CACHE)
                                if (cacheData.total > 0 || cacheData.lectures.isNotEmpty()) {
                                        todayLectures = cacheData.lectures
                                        totalClasses = cacheData.total
                                        attendedClasses = cacheData.attended
                                        isLoading = false
                                }
                        }
                } catch (e: Exception) { }

                try {
                        val serverData = fetchAttendanceData(Source.SERVER)

                        todayLectures = serverData.lectures
                        totalClasses = serverData.total
                        attendedClasses = serverData.attended

                        saveDailySnapshot(
                                db = db,
                                userId = userId,
                                todayLectures = serverData.lectures,
                                total = serverData.total,
                                attended = serverData.attended
                        )

                        errorMessage = null
                } catch (e: Exception) {
                        Log.e("DATA_LOAD_ERROR", "Failed to load data from server", e)

                        if (todayLectures.isEmpty() && totalClasses == 0) {
                                errorMessage =
                                        if (e.message?.contains("offline", ignoreCase = true) == true ||
                                                e.message?.contains("network", ignoreCase = true) == true ||
                                                e is com.google.firebase.firestore.FirebaseFirestoreException) {
                                                "Unable to connect. Please check your internet."
                                        } else {
                                                "An unexpected data error occurred. Please try again."
                                        }
                        }
                } finally {
                        isLoading = false
                        isRefreshing = false
                        forceServerFetch = false
                }
        }

        /* ================= UI ================= */
        Box(modifier = Modifier.fillMaxSize()) {
                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .background(
                                                Brush.verticalGradient(
                                                        colors =
                                                                listOf(
                                                                        MaterialTheme.colorScheme.surface,
                                                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                                                )
                                                )
                                        )
                )

                PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isRefreshing = true
                                forceServerFetch = true
                                refreshTrigger++
                        },
                        modifier = Modifier.fillMaxSize()
                ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                                EnhancedHeader(username = username)

                                if (errorMessage != null && todayLectures.isEmpty()) {
                                        Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                EnhancedErrorState(
                                                        errorMessage = errorMessage ?: "Unknown error",
                                                        onRetry = {
                                                                forceServerFetch = true
                                                                refreshTrigger++
                                                        }
                                                )
                                        }
                                } else {
                                        LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding =
                                                        PaddingValues(
                                                                horizontal = 20.dp,
                                                                vertical = 16.dp
                                                        ),
                                                verticalArrangement = Arrangement.spacedBy(20.dp)
                                        ) {
                                                item {
                                                        AnimatedVisibility(
                                                                visible = true,
                                                                enter = fadeIn() + slideInVertically()
                                                        ) {
                                                                if (isLoading && todayLectures.isEmpty()) {
                                                                        SkeletonSummaryCard()
                                                                } else {
                                                                        ModernAttendanceSummaryCard(
                                                                                total = totalClasses,
                                                                                attended = attendedClasses,
                                                                                screenWidth = configuration.screenWidthDp.dp
                                                                        )
                                                                }
                                                        }
                                                }

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

                                                if (isLoading && todayLectures.isEmpty()) {
                                                        items(3) { SkeletonLectureCard() }
                                                } else if (todayLectures.isEmpty()) {
                                                        item { EnhancedEmptyState() }
                                                } else {
                                                        items(todayLectures) { lecture ->
                                                                EnhancedTodayLectureCard(
                                                                        item = lecture,
                                                                        onClick = {
                                                                                haptic.performHapticFeedback(
                                                                                        HapticFeedbackType.LongPress
                                                                                )
                                                                        }
                                                                )
                                                        }
                                                }

                                                item { Spacer(modifier = Modifier.height(16.dp)) }
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
                        onNoteChange = { if (it.length <= 200) popupNote = it },
                        isSaving = isSavingPopup,
                        onDismiss = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                // Activate 10-Minute Snooze
                                prefs.edit().putLong("snooze_${activeLecture!!.lectureId}", System.currentTimeMillis()).apply()

                                showPopup = false
                                activeLecture = null
                                popupNote = ""
                        },
                        onPresent = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isSavingPopup = true
                                savePopupAttendance(
                                        db,
                                        userId,
                                        activeLecture!!,
                                        "Present",
                                        popupNote
                                ) {
                                        isSavingPopup = false
                                        showPopup = false
                                        popupNote = ""
                                        forceServerFetch = true
                                        refreshTrigger++
                                }
                        },
                        onAbsent = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isSavingPopup = true
                                savePopupAttendance(
                                        db,
                                        userId,
                                        activeLecture!!,
                                        "Absent",
                                        popupNote
                                ) {
                                        isSavingPopup = false
                                        showPopup = false
                                        popupNote = ""
                                        forceServerFetch = true
                                        refreshTrigger++
                                }
                        }
                )
        }
}

/* -------------------- COMPACT HEADER -------------------- */
@Composable
fun EnhancedHeader(username: String) {
        Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                text = getGreetingMessage(),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                                text = "Hello, $username",
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                }

                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                        val context = LocalContext.current
                                        Box(
                                                modifier = Modifier.size(48.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                                        .clickable {
                                                                context.startActivity(
                                                                        Intent(context, com.kishan.attendmate.ui.ai.AiChatActivity::class.java)
                                                                )
                                                        },
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.AutoAwesome,
                                                        contentDescription = "AI Chat Assistant",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }

                                        Box(
                                                modifier = Modifier.size(48.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Text(
                                                        text = username.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                )
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                )
                                Text(
                                        text = getCurrentDateString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                }
        }
}

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
                                modifier = Modifier.size(40.dp)
                                        .clip(RoundedCornerShape(RadiusSM))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                )
                        }
                        Column {
                                Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

/* -------------------- PRO ERROR STATE -------------------- */
@Composable
fun EnhancedErrorState(errorMessage: String, onRetry: () -> Unit) {
        StandardErrorState(
                title = "Oops!",
                subtitle = errorMessage,
                action = {
                        Spacer(modifier = Modifier.height(SpaceMD))
                        Button(
                                onClick = onRetry,
                                shape = RoundedCornerShape(RadiusMD),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(SpaceXS))
                                Text("Try Again", style = MaterialTheme.typography.labelLarge)
                        }
                }
        )
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

/* -------------------- SLEEK TODAY LECTURE CARD -------------------- */
@Composable
fun EnhancedTodayLectureCard(item: TodayLecture, onClick: () -> Unit) {
        val isPresent = item.status == "PRESENT"
        val statusColor = if (isPresent) SuccessColor else DangerColor

        Card(
                modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick),
                shape = RoundedCornerShape(RadiusLG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
                Row(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SpaceMD, vertical = SpaceSM),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpaceMD)
                ) {
                        Box(
                                modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                        )

                        Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(SpaceXXS)
                        ) {
                                Text(
                                        text = item.subjectName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(SpaceXXS)
                                ) {
                                        Icon(
                                                imageVector = Icons.Outlined.Schedule,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                                text = "${item.startTime} - ${item.endTime}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!item.note.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.width(SpaceXXS))
                                                Icon(
                                                        imageVector = Icons.Outlined.EditNote,
                                                        contentDescription = "Has note",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                )
                                        }
                                }
                        }

                        Surface(
                                shape = RoundedCornerShape(RadiusSM),
                                color = statusColor.copy(alpha = 0.1f),
                                border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.2f))
                        ) {
                                Text(
                                        text = if (isPresent) "Present" else "Absent",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = SpaceXS, vertical = SpaceXXS)
                                )
                        }
                }
        }
}

/* -------------------- ENHANCED EMPTY STATE -------------------- */
@Composable
fun EnhancedEmptyState() {
        StandardEmptyState(
                icon = Icons.Outlined.EventNote,
                title = "No Lectures Today",
                subtitle = "Enjoy your day off! 🎉\nRelax and recharge for tomorrow."
        )
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
        val scale = remember { Animatable(0.7f) }
        val alpha = remember { Animatable(0f) }
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp

        LaunchedEffect(Unit) {
                launch {
                        alpha.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(300, easing = EaseOutCubic)
                        )
                }
                launch {
                        scale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
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
                        modifier = Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.65f * alpha.value))
                                .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                ) { if (!isSaving) onDismiss() },
                        contentAlignment = Alignment.Center
                ) {
                        Card(
                                modifier = Modifier.fillMaxWidth(if (screenWidth > 600.dp) 0.85f else 0.92f)
                                        .scale(scale.value)
                                        .alpha(alpha.value)
                                        .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() }
                                        ) { },
                                shape = RoundedCornerShape(RadiusXL),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                                Box(
                                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                                ) {
                                        Column(
                                                modifier = Modifier.fillMaxWidth().padding(SpaceLG),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                        Surface(
                                                                onClick = { if (!isSaving) onDismiss() },
                                                                enabled = !isSaving,
                                                                modifier = Modifier.align(Alignment.TopEnd)
                                                                        .offset(x = 8.dp, y = (-8).dp)
                                                                        .size(44.dp),
                                                                shape = CircleShape,
                                                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                                                        ) {
                                                                Box(
                                                                        contentAlignment = Alignment.Center,
                                                                        modifier = Modifier.fillMaxSize()
                                                                ) {
                                                                        Icon(
                                                                                imageVector = Icons.Outlined.Close,
                                                                                contentDescription = "Close",
                                                                                tint = MaterialTheme.colorScheme.onSurface,
                                                                                modifier = Modifier.size(22.dp)
                                                                        )
                                                                }
                                                        }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                AnimatedDialogIcon()
                                                Spacer(modifier = Modifier.height(SpaceLG))

                                                Text(
                                                        text = "Mark Attendance",
                                                        style = MaterialTheme.typography.headlineMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                )

                                                Spacer(modifier = Modifier.height(12.dp))

                                                Surface(
                                                        shape = RoundedCornerShape(RadiusMD),
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                        border = BorderStroke(
                                                                width = 1.dp,
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                        )
                                                ) {
                                                        Row(
                                                                modifier = Modifier.padding(horizontal = SpaceLG, vertical = 14.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                                Icon(
                                                                        imageVector = Icons.Default.MenuBook,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.size(22.dp)
                                                                )
                                                                Text(
                                                                        text = lecture.subjectName,
                                                                        style = MaterialTheme.typography.titleLarge,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                )
                                                        }
                                                }

                                                Spacer(modifier = Modifier.height(SpaceLG))

                                                Surface(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(RadiusMD),
                                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                                ) {
                                                        Row(
                                                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.Center
                                                        ) {
                                                                Box(
                                                                        modifier = Modifier.size(44.dp)
                                                                                .clip(RoundedCornerShape(RadiusSM))
                                                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                                                        contentAlignment = Alignment.Center
                                                                ) {
                                                                        Icon(
                                                                                imageVector = Icons.Filled.Schedule,
                                                                                contentDescription = null,
                                                                                tint = MaterialTheme.colorScheme.primary,
                                                                                modifier = Modifier.size(24.dp)
                                                                        )
                                                                }

                                                                Spacer(modifier = Modifier.width(SpaceMD))

                                                                Text(
                                                                        text = lecture.startTime,
                                                                        style = MaterialTheme.typography.titleLarge,
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                )

                                                                Spacer(modifier = Modifier.width(12.dp))

                                                                Box(
                                                                        modifier = Modifier.size(8.dp)
                                                                                .clip(CircleShape)
                                                                                .background(MaterialTheme.colorScheme.primary)
                                                                )

                                                                Spacer(modifier = Modifier.width(12.dp))

                                                                Text(
                                                                        text = lecture.endTime,
                                                                        style = MaterialTheme.typography.titleLarge,
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                        }
                                                }

                                                Spacer(modifier = Modifier.height(SpaceLG))

                                                OutlinedTextField(
                                                        value = note,
                                                        onValueChange = onNoteChange,
                                                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                                                        label = {
                                                                Text(
                                                                        "Add a Note (Optional)",
                                                                        style = MaterialTheme.typography.bodyMedium
                                                                )
                                                        },
                                                        placeholder = {
                                                                Text(
                                                                        "e.g., Medical emergency, Family function, Late arrival...",
                                                                        style = MaterialTheme.typography.bodyMedium
                                                                )
                                                        },
                                                        maxLines = 4,
                                                        leadingIcon = {
                                                                Box(
                                                                        modifier = Modifier.padding(start = 4.dp).size(40.dp)
                                                                                .clip(RoundedCornerShape(RadiusSM))
                                                                                .background(
                                                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                                                ),
                                                                        contentAlignment = Alignment.Center
                                                                ) {
                                                                        Icon(
                                                                                Icons.Default.EditNote,
                                                                                contentDescription = null,
                                                                                tint = MaterialTheme.colorScheme.primary,
                                                                                modifier = Modifier.size(24.dp)
                                                                        )
                                                                }
                                                        },
                                                        supportingText = {
                                                                Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                        Text(
                                                                                "Keep it brief and relevant",
                                                                                style = MaterialTheme.typography.labelSmall,
                                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                                        )
                                                                        Text(
                                                                                "${note.length}/200",
                                                                                style = MaterialTheme.typography.labelSmall,
                                                                                color = if (note.length > 180) DangerColor
                                                                                else if (note.length > 150) WarningColor
                                                                                else MaterialTheme.colorScheme.primary
                                                                        )
                                                                }
                                                        },
                                                        enabled = !isSaving,
                                                        shape = RoundedCornerShape(RadiusMD),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                                        )
                                                )

                                                Spacer(modifier = Modifier.height(SpaceXL))

                                                Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                                ) {
                                                        ModernActionButton(
                                                                text = "Absent",
                                                                icon = Icons.Outlined.Close,
                                                                backgroundColor = DangerColor,
                                                                isEnabled = !isSaving,
                                                                isLoading = false,
                                                                modifier = Modifier.weight(1f),
                                                                onClick = onAbsent
                                                        )

                                                        ModernActionButton(
                                                                text = "Present",
                                                                icon = Icons.Filled.CheckCircle,
                                                                backgroundColor = SuccessColor,
                                                                isEnabled = !isSaving,
                                                                isLoading = isSaving,
                                                                modifier = Modifier.weight(1f),
                                                                onClick = onPresent
                                                        )
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                        }
                                }
                        }
                }
        }
}

object AttendanceColors {
        val Present = Color(0xFF10B981)
        val PresentLight = Color(0xFF34D399)
        val PresentBg = Color(0xFFD1FAE5)

        val Absent = Color(0xFFEF4444)
        val AbsentLight = Color(0xFFF87171)
        val AbsentBg = Color(0xFFFEE2E2)

        val Warning = Color(0xFFF59E0B)
        val WarningLight = Color(0xFFFBBF24)
        val WarningBg = Color(0xFFFEF3C7)

        val Info = Color(0xFF3B82F6)
        val InfoLight = Color(0xFF60A5FA)
        val InfoBg = Color(0xFFDBEAFE)
}

/* -------------------- MODERN ICON -------------------- */
@Composable
private fun AnimatedDialogIcon() {
        Box(
                modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
        ) {
                Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                )
        }
}

/* -------------------- MODERN ACTION BUTTON -------------------- */
@Composable
private fun ModernActionButton(
        text: String,
        icon: ImageVector,
        backgroundColor: Color,
        isEnabled: Boolean,
        isLoading: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val buttonScale by animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1f,
                animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                ),
                label = "button_scale"
        )

        Button(
                onClick = onClick,
                enabled = isEnabled,
                modifier = modifier.height(64.dp)
                        .scale(buttonScale)
                        .shadow(
                                elevation = if (isEnabled && !isLoading) 12.dp else 4.dp,
                                shape = RoundedCornerShape(20.dp),
                                spotColor = backgroundColor.copy(alpha = 0.4f)
                        ),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                contentPadding = PaddingValues(0.dp),
                interactionSource = interactionSource
        ) {
                Box(
                        modifier = Modifier.fillMaxSize()
                                .background(
                                        if (isEnabled && !isLoading) {
                                                Brush.linearGradient(
                                                        colors = listOf(
                                                                backgroundColor,
                                                                backgroundColor.copy(alpha = 0.85f)
                                                        ),
                                                        start = Offset(0f, 0f),
                                                        end = Offset.Infinite
                                                )
                                        } else {
                                                Brush.linearGradient(
                                                        colors = listOf(
                                                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                                        )
                                                )
                                        }
                                ),
                        contentAlignment = Alignment.Center
                ) {
                        if (isLoading) {
                                CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        } else {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                        Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                                text = text,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 17.sp,
                                                letterSpacing = 0.5.sp
                                        )
                                }
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
                val (h, m) = lecture.startTime.split(":").map { it.trim().toInt() }
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
        }

        val endCal = Calendar.getInstance().apply {
                val (h, m) = lecture.endTime.split(":").map { it.trim().toInt() }
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
        }

        // Creating identical lecture ID Format to AddAttendanceActivity
        val startKey = SimpleDateFormat("HHmm", Locale.getDefault()).format(startCal.time)
        val endKey = SimpleDateFormat("HHmm", Locale.getDefault()).format(endCal.time)
        val lectureId = "${dateKey}_${startKey}_${endKey}"

        val dayName = when (today.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "MONDAY"
                Calendar.TUESDAY -> "TUESDAY"
                Calendar.WEDNESDAY -> "WEDNESDAY"
                Calendar.THURSDAY -> "THURSDAY"
                Calendar.FRIDAY -> "FRIDAY"
                Calendar.SATURDAY -> "SATURDAY"
                Calendar.SUNDAY -> "SUNDAY"
                else -> null
        }

        val startHour = startCal.get(Calendar.HOUR_OF_DAY)
        val endHour = endCal.get(Calendar.HOUR_OF_DAY)
        val slotIndex = startHour - 9
        val durationHours = endHour - startHour

        val lectureKey = if (dayName != null && slotIndex >= 0 && durationHours > 0) {
                "${dayName}_${slotIndex}_${durationHours}"
        } else null

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
                else subjectSnap.getLong("attendedClasses") ?: 0

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

                lectureKey?.let { attendanceData["lectureKey"] = it }

                tx.set(attendanceRef, attendanceData)
                tx.update(
                        subjectRef,
                        mapOf("totalClasses" to total, "attendedClasses" to attended)
                )
        }.addOnCompleteListener { onDone() }
}

suspend fun saveDailySnapshot(
        db: FirebaseFirestore,
        userId: String,
        todayLectures: List<TodayLecture>,
        total: Int,
        attended: Int
) {
        val todayDate = LocalDate.now().toString()

        val snapshotRef =
                db.collection("users")
                        .document(userId)
                        .collection("dailySnapshot")
                        .document(todayDate)

        val percentage = if (total == 0) 0.0 else (attended.toDouble() / total.toDouble()) * 100.0

        val lectureMap =
                todayLectures.associate { lecture ->
                        val uniqueKey = "${lecture.subjectName}_${lecture.startTime.replace(":", "")}"
                        uniqueKey to
                                mapOf(
                                        "subjectName" to lecture.subjectName,
                                        "status" to lecture.status,
                                        "startTime" to lecture.startTime,
                                        "endTime" to lecture.endTime,
                                        "note" to (lecture.note ?: "")
                                )
                }

        val data =
                mapOf(
                        "date" to todayDate,
                        "totalClasses" to total,
                        "attendedClasses" to attended,
                        "percentage" to percentage,
                        "lectures" to lectureMap,
                        "updatedAt" to FieldValue.serverTimestamp()
                )

        try {
                snapshotRef.set(data).await()
        } catch (e: Exception) {
                Log.e("SNAPSHOT_ERROR", "Failed to save snapshot", e)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).shadow(4.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
                Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Box(
                                modifier = Modifier.width(180.dp).height(24.dp).clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(
                                modifier = Modifier.size(160.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                repeat(3) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(
                                                        modifier = Modifier.width(70.dp).height(36.dp).clip(RoundedCornerShape(10.dp))
                                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Box(
                                                        modifier = Modifier.width(60.dp).height(18.dp).clip(RoundedCornerShape(9.dp))
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier = Modifier.size(72.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                                Box(
                                        modifier = Modifier.fillMaxWidth(0.7f).height(22.dp).clip(RoundedCornerShape(11.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                        modifier = Modifier.fillMaxWidth(0.5f).height(18.dp).clip(RoundedCornerShape(9.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                        modifier = Modifier.width(100.dp).height(28.dp).clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                                )
                        }
                }
        }
}

/* -------------------- MODERN ATTENDANCE SUMMARY CARD -------------------- */
@Composable
fun ModernAttendanceSummaryCard(
        total: Int,
        attended: Int,
        screenWidth: androidx.compose.ui.unit.Dp
) {
        val percentage = if (total == 0) 0f else (attended.toFloat() / total.toFloat()) * 100
        val animatedPercentage = remember { Animatable(0f) }

        LaunchedEffect(percentage) {
                animatedPercentage.animateTo(
                        targetValue = percentage,
                        animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                        )
                )
        }

        val statusColor = when {
                percentage >= 75 -> SuccessColor
                percentage >= 60 -> WarningColor
                else -> DangerColor
        }

        val statusText = when {
                percentage >= 75 -> "Excellent"
                percentage >= 60 -> "Good"
                else -> "Needs Attention"
        }

        val statusIcon = when {
                percentage >= 75 -> Icons.Default.CheckCircle
                percentage >= 60 -> Icons.Default.Warning
                else -> Icons.Default.ErrorOutline
        }

        val deviceType = rememberDeviceType()
        val progressSize = deviceType.circularProgressIndicatorSize
        val padding = deviceType.contentPadding

        Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RadiusLG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(padding),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                        Box(
                                                modifier = Modifier.size(40.dp)
                                                        .clip(RoundedCornerShape(RadiusSM))
                                                        .background(statusColor.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.School,
                                                        contentDescription = null,
                                                        tint = statusColor,
                                                        modifier = Modifier.size(22.dp)
                                                )
                                        }

                                        Column {
                                                Text(
                                                        text = "Attendance",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                        text = "Overall Performance",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                        }
                                }

                                Surface(
                                        shape = RoundedCornerShape(RadiusSM),
                                        color = statusColor.copy(alpha = 0.15f),
                                        border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.3f))
                                ) {
                                        Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = statusColor
                                                )
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(SpaceLG))

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(progressSize)) {
                                CircularProgressIndicator(
                                        progress = { animatedPercentage.value / 100f },
                                        modifier = Modifier.size(progressSize - 20.dp),
                                        strokeWidth = 14.dp,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        color = statusColor,
                                        strokeCap = StrokeCap.Round
                                )

                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                        Row(
                                                verticalAlignment = Alignment.Bottom,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                                Text(
                                                        text = String.format(Locale.getDefault(), "%.1f", animatedPercentage.value),
                                                        style = MaterialTheme.typography.displayLarge,
                                                        color = statusColor
                                                )
                                                Text(
                                                        text = "%",
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color = statusColor.copy(alpha = 0.7f),
                                                        modifier = Modifier.padding(bottom = 12.dp)
                                                )
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(SpaceLG))

                        Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(RadiusMD),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                                Row(
                                        modifier = Modifier.padding(SpaceMD),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        ModernStatItem(
                                                label = "Present",
                                                value = attended.toString(),
                                                icon = Icons.Default.CheckCircle,
                                                color = SuccessColor
                                        )

                                        VerticalDivider(
                                                modifier = Modifier.height(60.dp),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant
                                        )

                                        ModernStatItem(
                                                label = "Total",
                                                value = total.toString(),
                                                icon = Icons.Default.CalendarMonth,
                                                color = MaterialTheme.colorScheme.primary
                                        )

                                        VerticalDivider(
                                                modifier = Modifier.height(60.dp),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant
                                        )

                                        ModernStatItem(
                                                label = "Absent",
                                                value = (total - attended).toString(),
                                                icon = Icons.Default.Cancel,
                                                color = DangerColor
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(SpaceMD))

                        if (percentage < 75) {
                                val lecturesNeeded = calculateLecturesNeededFor75Percent(attended, total)
                                MotivationalCard(
                                        icon = "💪",
                                        title = "Keep Going!",
                                        message = "Attend $lecturesNeeded more ${if (lecturesNeeded == 1) "class" else "classes"} to reach 75%",
                                        color = WarningColor
                                )
                        } else {
                                MotivationalCard(
                                        icon = "🗿",
                                        title = "Amazing Work!",
                                        message = "You're maintaining excellent attendance",
                                        color = SuccessColor
                                )
                        }
                }
        }
}

/* -------------------- MODERN STAT ITEM -------------------- */
@Composable
private fun ModernStatItem(label: String, value: String, icon: ImageVector, color: Color) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpaceXS)
        ) {
                Box(
                        modifier = Modifier.size(40.dp)
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
                        style = MaterialTheme.typography.titleMedium,
                        color = color
                )

                Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
        }
}

@Composable
private fun MotivationalCard(icon: String, title: String, message: String, color: Color) {
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RadiusMD),
                color = color.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
                shadowElevation = ElevationNone
        ) {
                Row(
                        modifier = Modifier.padding(SpaceMD),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier = Modifier.size(36.dp)
                                        .clip(CircleShape)
                                        .background(color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                        ) {
                                Text(
                                        text = icon,
                                        style = MaterialTheme.typography.titleLarge
                                )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                }
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