package com.kishan.attendmate

import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.kishan.attendmate.ui.components.AttendMateNavigationBar
import com.kishan.attendmate.ui.theme.AttendMateTheme
import com.kishan.attendmate.ui.widget.WidgetUpdateWorker
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

                // Firestore persistence is now configured in AttendMateApplication.kt

                // 🔔 Ensure notification channels exist
                createNotificationChannels()

                // 📌 Initialize Google Mobile Ads SDK
                com.google.android.gms.ads.MobileAds.initialize(this) {}

                enableEdgeToEdge()

                // Enqueue background work to keep the widget up to date
                val widgetWorkRequest =
                        PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                                        repeatInterval = 1,
                                        repeatIntervalTimeUnit = TimeUnit.HOURS
                                )
                                .build()
                WorkManager.getInstance(applicationContext)
                        .enqueueUniquePeriodicWork(
                                "widget_update_work",
                                ExistingPeriodicWorkPolicy.UPDATE,
                                widgetWorkRequest
                        )
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
        val endTime: String
)

data class FetchResult(val lectures: List<TodayLecture>, val total: Int, val attended: Int)

/* -------------------- HOME SCREEN -------------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
        val haptic = LocalHapticFeedback.current
        val configuration = LocalConfiguration.current
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid ?: return

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
                                        // Instantly triggers the LaunchedEffect to run seamlessly
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
                        // 1. Get user info safely
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
                                                }
                                                .getOrNull()
                                                ?: return@forEach
                                val end =
                                        runCatching { LocalTime.parse(endTimeRaw.padStart(5, '0')) }
                                                .getOrNull()
                                                ?: return@forEach

                                if (now.isAfter(start) && now.isBefore(end)) {
                                        val subjectId = doc.getString("subjectId") ?: return@forEach
                                        val subjectName =
                                                doc.getString("subjectName") ?: return@forEach

                                        val todayDate =
                                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                        .format(Date())
                                        val lectureId =
                                                "${todayDate}_${startTimeRaw.replace(":", "")}_${endTimeRaw.replace(":", "")}"

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
                                                activeLecture =
                                                        ActiveLecture(
                                                                subjectId,
                                                                subjectName,
                                                                startTimeRaw,
                                                                endTimeRaw
                                                        )
                                                showPopup = true
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
                        // SKIP CACHE if user explicitly swiped down or marked attendance
                        if (!forceServerFetch && !isRefreshing) {
                                val cacheData = fetchAttendanceData(Source.CACHE)
                                if (cacheData.total > 0 || cacheData.lectures.isNotEmpty()) {
                                        todayLectures = cacheData.lectures
                                        totalClasses = cacheData.total
                                        attendedClasses = cacheData.attended
                                        isLoading = false
                                }
                        }
                } catch (e: Exception) {
                        // Cache miss - totally normal
                }

                try {
                        // ALWAYS hit the server to ensure background changes/new marks are grabbed
                        val serverData = fetchAttendanceData(Source.SERVER)

                        todayLectures = serverData.lectures
                        totalClasses = serverData.total
                        attendedClasses = serverData.attended

                        // GUARANTEED SAVE: Instantly update daily snapshot with pristine server
                        // data
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
                                        if (e.message?.contains("offline", ignoreCase = true) ==
                                                        true ||
                                                        e.message?.contains(
                                                                "network",
                                                                ignoreCase = true
                                                        ) == true ||
                                                        e is
                                                                com.google.firebase.firestore.FirebaseFirestoreException
                                        ) {
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
                                                                        MaterialTheme.colorScheme
                                                                                .surface,
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceContainerLowest
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
                                /* -------- COMPACT HEADER -------- */
                                EnhancedHeader(username = username)

                                /* -------- ERROR STATE -------- */
                                if (errorMessage != null && todayLectures.isEmpty()) {
                                        Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                EnhancedErrorState(
                                                        errorMessage = errorMessage
                                                                        ?: "Unknown error",
                                                        onRetry = {
                                                                forceServerFetch = true
                                                                refreshTrigger++
                                                        }
                                                )
                                        }
                                } else {
                                        /* -------- CONTENT -------- */
                                        LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding =
                                                        PaddingValues(
                                                                horizontal = 20.dp,
                                                                vertical = 16.dp
                                                        ),
                                                verticalArrangement = Arrangement.spacedBy(20.dp)
                                        ) {
                                                // Summary Card
                                                item {
                                                        AnimatedVisibility(
                                                                visible = true,
                                                                enter =
                                                                        fadeIn() +
                                                                                slideInVertically()
                                                        ) {
                                                                if (isLoading &&
                                                                                todayLectures
                                                                                        .isEmpty()
                                                                ) {
                                                                        SkeletonSummaryCard()
                                                                } else {
                                                                        ModernAttendanceSummaryCard(
                                                                                total =
                                                                                        totalClasses,
                                                                                attended =
                                                                                        attendedClasses,
                                                                                screenWidth =
                                                                                        configuration
                                                                                                .screenWidthDp
                                                                                                .dp
                                                                        )
                                                                }
                                                        }
                                                }

                                                // Section Header
                                                item {
                                                        AnimatedVisibility(
                                                                visible = true,
                                                                enter =
                                                                        fadeIn() +
                                                                                slideInVertically()
                                                        ) {
                                                                EnhancedSectionHeader(
                                                                        icon =
                                                                                Icons.Outlined
                                                                                        .EventNote,
                                                                        title = "Today's Lectures",
                                                                        subtitle =
                                                                                getCurrentDateString(),
                                                                        onActionClick = null
                                                                )
                                                        }
                                                }

                                                // Lectures List
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
                                                                                        HapticFeedbackType
                                                                                                .LongPress
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

                // 📌 Bottom Banner Ad
                com.kishan.attendmate.ui.components.BannerAd(
                        modifier = Modifier.align(Alignment.BottomCenter)
                )
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
                                        // Force complete server override
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
                                        // Force complete server override
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
                modifier =
                        Modifier.fillMaxWidth()
                                .shadow(
                                        elevation = 4.dp,
                                        shape =
                                                RoundedCornerShape(
                                                        bottomStart = 24.dp,
                                                        bottomEnd = 24.dp
                                                ),
                                        spotColor =
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(
                                                Brush.linearGradient(
                                                        colors =
                                                                listOf(
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer
                                                                                .copy(alpha = 0.3f),
                                                                        MaterialTheme.colorScheme
                                                                                .surface
                                                                )
                                                )
                                        )
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
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                        text = "Hello, $username",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .headlineMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 24.sp
                                                )
                                        }

                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                                val context =
                                                        androidx.compose.ui.platform.LocalContext
                                                                .current

                                                // AI Chat Button
                                                Box(
                                                        modifier =
                                                                Modifier.size(48.dp)
                                                                        .shadow(
                                                                                elevation = 6.dp,
                                                                                shape = CircleShape,
                                                                                spotColor =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.3f
                                                                                                )
                                                                        )
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primaryContainer
                                                                        )
                                                                        .clickable {
                                                                                context.startActivity(
                                                                                        android.content
                                                                                                .Intent(
                                                                                                        context,
                                                                                                        com.kishan
                                                                                                                        .attendmate
                                                                                                                        .ui
                                                                                                                        .ai
                                                                                                                        .AiChatActivity::class
                                                                                                                .java
                                                                                                )
                                                                                )
                                                                        },
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Icon(
                                                                imageVector =
                                                                        Icons.Default.AutoAwesome,
                                                                contentDescription =
                                                                        "AI Chat Assistant",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                }

                                                // Profile Avatar
                                                Box(
                                                        modifier =
                                                                Modifier.size(48.dp)
                                                                        .shadow(
                                                                                elevation = 6.dp,
                                                                                shape = CircleShape,
                                                                                spotColor =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.3f
                                                                                                )
                                                                        )
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                Brush.linearGradient(
                                                                                        colors =
                                                                                                listOf(
                                                                                                        MaterialTheme
                                                                                                                .colorScheme
                                                                                                                .primary,
                                                                                                        MaterialTheme
                                                                                                                .colorScheme
                                                                                                                .tertiary
                                                                                                )
                                                                                )
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Text(
                                                                text =
                                                                        username.firstOrNull()
                                                                                ?.uppercaseChar()
                                                                                ?.toString()
                                                                                ?: "U",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleLarge,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                fontSize = 20.sp
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Date and Day
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
                                                style = MaterialTheme.typography.bodySmall,
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
                                modifier =
                                        Modifier.size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                        MaterialTheme.colorScheme.primaryContainer
                                                ),
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
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 18.sp
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

/* -------------------- PRO ERROR STATE -------------------- */
@Composable
fun EnhancedErrorState(errorMessage: String, onRetry: () -> Unit) {
        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 32.dp)
                                .shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(28.dp),
                                        spotColor =
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                ),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(
                                                Brush.verticalGradient(
                                                        colors =
                                                                listOf(
                                                                        MaterialTheme.colorScheme
                                                                                .errorContainer
                                                                                .copy(alpha = 0.3f),
                                                                        MaterialTheme.colorScheme
                                                                                .surface
                                                                )
                                                )
                                        )
                ) {
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                                Box(
                                        modifier =
                                                Modifier.size(100.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .errorContainer.copy(
                                                                        alpha = 0.5f
                                                                )
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector = Icons.Outlined.ErrorOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(56.dp),
                                                tint = MaterialTheme.colorScheme.error
                                        )
                                }

                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        Text(
                                                text = "Oops!",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                                text = errorMessage,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.bodyMedium,
                                                lineHeight = 20.sp
                                        )
                                }

                                Button(
                                        onClick = onRetry,
                                        modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.error
                                                )
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Try Again", fontWeight = FontWeight.Bold)
                                }
                        }
                }
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

/* -------------------- SLEEK TODAY LECTURE CARD -------------------- */
@Composable
fun EnhancedTodayLectureCard(item: TodayLecture, onClick: () -> Unit) {
        val isPresent = item.status == "PRESENT"
        val statusColor = if (isPresent) Color(0xFF10B981) else Color(0xFFEF4444)
        val statusBgColor = statusColor.copy(alpha = 0.08f)
        val scale by
                animateFloatAsState(
                        targetValue = 1f,
                        animationSpec =
                                spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                ),
                        label = "lecture_card_scale"
                )

        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .scale(scale)
                                .shadow(
                                        elevation = 2.dp,
                                        shape = RoundedCornerShape(20.dp),
                                        spotColor = statusColor.copy(alpha = 0.1f)
                                )
                                .clickable(onClick = onClick),
                shape = RoundedCornerShape(20.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(width = 1.dp, color = statusColor.copy(alpha = 0.2f))
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(
                                                Brush.horizontalGradient(
                                                        colors =
                                                                listOf(
                                                                        statusBgColor,
                                                                        MaterialTheme.colorScheme
                                                                                .surface,
                                                                        MaterialTheme.colorScheme
                                                                                .surface
                                                                ),
                                                        startX = 0f,
                                                        endX = 800f
                                                )
                                        )
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                Box(
                                        modifier =
                                                Modifier.size(48.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(
                                                                statusColor.copy(alpha = 0.15f)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector =
                                                        if (isPresent) Icons.Filled.CheckCircle
                                                        else Icons.Filled.Cancel,
                                                contentDescription = null,
                                                tint = statusColor,
                                                modifier = Modifier.size(28.dp)
                                        )
                                }

                                Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                        Text(
                                                text = item.subjectName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 17.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(6.dp)
                                                ) {
                                                        Icon(
                                                                Icons.Outlined.Schedule,
                                                                contentDescription = null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                                modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                                text =
                                                                        "${item.startTime} - ${item.endTime}",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                                fontWeight = FontWeight.Medium
                                                        )
                                                }

                                                Row(
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(6.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        if (!item.note.isNullOrBlank()) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Outlined
                                                                                        .EditNote,
                                                                        contentDescription =
                                                                                "Has note",
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary,
                                                                        modifier =
                                                                                Modifier.size(18.dp)
                                                                )
                                                        }
                                                        Text(
                                                                text =
                                                                        if (isPresent) "Present"
                                                                        else "Absent",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelMedium,
                                                                color = statusColor,
                                                                fontWeight = FontWeight.Bold
                                                        )
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
        val floatOffset by
                infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 15f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(2000, easing = EaseInOutCubic),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "float_animation"
                )

        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .shadow(
                                        elevation = 4.dp,
                                        shape = RoundedCornerShape(28.dp),
                                        spotColor =
                                                MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.15f
                                                )
                                ),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(
                                                Brush.verticalGradient(
                                                        colors =
                                                                listOf(
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer
                                                                                .copy(alpha = 0.3f),
                                                                        MaterialTheme.colorScheme
                                                                                .surface
                                                                )
                                                )
                                        )
                ) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                                Box(
                                        modifier =
                                                Modifier.size(120.dp)
                                                        .offset(y = floatOffset.dp)
                                                        .shadow(
                                                                elevation = 16.dp,
                                                                shape = CircleShape,
                                                                spotColor =
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.3f
                                                                        )
                                                        )
                                                        .clip(CircleShape)
                                                        .background(
                                                                Brush.radialGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primaryContainer,
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .secondaryContainer
                                                                                )
                                                                )
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector = Icons.Outlined.EventNote,
                                                contentDescription = null,
                                                modifier = Modifier.size(60.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                }

                                Text(
                                        text = "No Lectures Today",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                )

                                Text(
                                        text =
                                                "Enjoy your day off! 🎉\nRelax and recharge for tomorrow.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                )
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
                                animationSpec =
                                        spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                        )
                        )
                }
        }

        Dialog(
                onDismissRequest = { if (!isSaving) onDismiss() },
                properties =
                        DialogProperties(
                                usePlatformDefaultWidth = false,
                                dismissOnBackPress = !isSaving,
                                dismissOnClickOutside = !isSaving
                        )
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.65f * alpha.value))
                                        .clickable(
                                                indication = null,
                                                interactionSource =
                                                        remember { MutableInteractionSource() }
                                        ) { if (!isSaving) onDismiss() },
                        contentAlignment = Alignment.Center
                ) {
                        Card(
                                modifier =
                                        Modifier.fillMaxWidth(
                                                        if (screenWidth > 600.dp) 0.85f else 0.92f
                                                )
                                                .scale(scale.value)
                                                .alpha(alpha.value)
                                                .clickable(
                                                        indication = null,
                                                        interactionSource =
                                                                remember {
                                                                        MutableInteractionSource()
                                                                }
                                                ) { /* Prevent dismissal */},
                                shape = RoundedCornerShape(36.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
                        ) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .background(
                                                                Brush.verticalGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primaryContainer
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.25f
                                                                                                ),
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .surface
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.95f
                                                                                                ),
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .surface
                                                                                )
                                                                )
                                                        )
                                ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                                drawCircle(
                                                        color =
                                                                Color(0xFF667EEA)
                                                                        .copy(alpha = 0.04f),
                                                        radius = 150f,
                                                        center =
                                                                Offset(
                                                                        size.width * 0.2f,
                                                                        size.height * 0.15f
                                                                )
                                                )
                                                drawCircle(
                                                        color =
                                                                Color(0xFFF093FB)
                                                                        .copy(alpha = 0.05f),
                                                        radius = 180f,
                                                        center =
                                                                Offset(
                                                                        size.width * 0.85f,
                                                                        size.height * 0.75f
                                                                )
                                                )
                                        }

                                        Column(
                                                modifier = Modifier.fillMaxWidth().padding(28.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                        Surface(
                                                                onClick = {
                                                                        if (!isSaving) onDismiss()
                                                                },
                                                                enabled = !isSaving,
                                                                modifier =
                                                                        Modifier.align(
                                                                                        Alignment
                                                                                                .TopEnd
                                                                                )
                                                                                .offset(
                                                                                        x = 8.dp,
                                                                                        y = (-8).dp
                                                                                )
                                                                                .size(44.dp),
                                                                shape = CircleShape,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceContainerHighest,
                                                                shadowElevation = 6.dp
                                                        ) {
                                                                Box(
                                                                        contentAlignment =
                                                                                Alignment.Center,
                                                                        modifier =
                                                                                Modifier.fillMaxSize()
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Outlined
                                                                                                .Close,
                                                                                contentDescription =
                                                                                        "Close",
                                                                                tint =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurface,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                22.dp
                                                                                        )
                                                                        )
                                                                }
                                                        }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                AnimatedDialogIcon()
                                                Spacer(modifier = Modifier.height(28.dp))

                                                Text(
                                                        text = "Mark Attendance",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .headlineMedium,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 28.sp,
                                                        letterSpacing = 0.5.sp
                                                )

                                                Spacer(modifier = Modifier.height(12.dp))

                                                Surface(
                                                        shape = RoundedCornerShape(18.dp),
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .primaryContainer.copy(
                                                                        alpha = 0.6f
                                                                ),
                                                        shadowElevation = 4.dp,
                                                        border =
                                                                BorderStroke(
                                                                        width = 1.5.dp,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.3f
                                                                                        )
                                                                )
                                                ) {
                                                        Row(
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 24.dp,
                                                                                vertical = 14.dp
                                                                        ),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                horizontalArrangement =
                                                                        Arrangement.spacedBy(10.dp)
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default
                                                                                        .MenuBook,
                                                                        contentDescription = null,
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary,
                                                                        modifier =
                                                                                Modifier.size(22.dp)
                                                                )
                                                                Text(
                                                                        text = lecture.subjectName,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleLarge,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary,
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        fontSize = 19.sp,
                                                                        maxLines = 1,
                                                                        overflow =
                                                                                TextOverflow
                                                                                        .Ellipsis
                                                                )
                                                        }
                                                }

                                                Spacer(modifier = Modifier.height(24.dp))

                                                Surface(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(22.dp),
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .surfaceContainerHigh,
                                                        shadowElevation = 3.dp
                                                ) {
                                                        Row(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .padding(20.dp),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                horizontalArrangement =
                                                                        Arrangement.Center
                                                        ) {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.size(44.dp)
                                                                                        .clip(
                                                                                                RoundedCornerShape(
                                                                                                        12.dp
                                                                                                )
                                                                                        )
                                                                                        .background(
                                                                                                Brush.linearGradient(
                                                                                                        colors =
                                                                                                                listOf(
                                                                                                                        AttendanceColors
                                                                                                                                .Info
                                                                                                                                .copy(
                                                                                                                                        alpha =
                                                                                                                                                0.2f
                                                                                                                                ),
                                                                                                                        AttendanceColors
                                                                                                                                .InfoLight
                                                                                                                                .copy(
                                                                                                                                        alpha =
                                                                                                                                                0.15f
                                                                                                                                )
                                                                                                                )
                                                                                                )
                                                                                        ),
                                                                        contentAlignment =
                                                                                Alignment.Center
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Filled
                                                                                                .Schedule,
                                                                                contentDescription =
                                                                                        null,
                                                                                tint =
                                                                                        AttendanceColors
                                                                                                .Info,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                24.dp
                                                                                        )
                                                                        )
                                                                }

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.width(
                                                                                        16.dp
                                                                                )
                                                                )

                                                                Text(
                                                                        text = lecture.startTime,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleLarge,
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface,
                                                                        fontSize = 20.sp
                                                                )

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.width(
                                                                                        12.dp
                                                                                )
                                                                )

                                                                Box(
                                                                        modifier =
                                                                                Modifier.size(8.dp)
                                                                                        .clip(
                                                                                                CircleShape
                                                                                        )
                                                                                        .background(
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .primary
                                                                                        )
                                                                )

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.width(
                                                                                        12.dp
                                                                                )
                                                                )

                                                                Text(
                                                                        text = lecture.endTime,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleLarge,
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface,
                                                                        fontSize = 20.sp
                                                                )
                                                        }
                                                }

                                                Spacer(modifier = Modifier.height(28.dp))

                                                OutlinedTextField(
                                                        value = note,
                                                        onValueChange = onNoteChange,
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .heightIn(min = 120.dp),
                                                        label = {
                                                                Text(
                                                                        "Add a Note (Optional)",
                                                                        fontWeight =
                                                                                FontWeight.SemiBold,
                                                                        fontSize = 14.sp
                                                                )
                                                        },
                                                        placeholder = {
                                                                Text(
                                                                        "e.g., Medical emergency, Family function, Late arrival...",
                                                                        fontSize = 13.sp
                                                                )
                                                        },
                                                        maxLines = 4,
                                                        leadingIcon = {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                                start =
                                                                                                        4.dp
                                                                                        )
                                                                                        .size(40.dp)
                                                                                        .clip(
                                                                                                RoundedCornerShape(
                                                                                                        10.dp
                                                                                                )
                                                                                        )
                                                                                        .background(
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .primaryContainer
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.5f
                                                                                                        )
                                                                                        ),
                                                                        contentAlignment =
                                                                                Alignment.Center
                                                                ) {
                                                                        Icon(
                                                                                Icons.Default
                                                                                        .EditNote,
                                                                                contentDescription =
                                                                                        null,
                                                                                tint =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                24.dp
                                                                                        )
                                                                        )
                                                                }
                                                        },
                                                        supportingText = {
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .SpaceBetween,
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Text(
                                                                                "Keep it brief and relevant",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurfaceVariant
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.7f
                                                                                                )
                                                                        )
                                                                        Text(
                                                                                "${note.length}/200",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                color =
                                                                                        if (note.length >
                                                                                                        180
                                                                                        )
                                                                                                AttendanceColors
                                                                                                        .Absent
                                                                                        else if (note.length >
                                                                                                        150
                                                                                        )
                                                                                                AttendanceColors
                                                                                                        .Warning
                                                                                        else
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .primary
                                                                        )
                                                                }
                                                        },
                                                        enabled = !isSaving,
                                                        shape = RoundedCornerShape(18.dp),
                                                        colors =
                                                                OutlinedTextFieldDefaults.colors(
                                                                        focusedBorderColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary,
                                                                        unfocusedBorderColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .outline
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.5f
                                                                                        ),
                                                                        focusedContainerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surface,
                                                                        unfocusedContainerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceContainerLowest
                                                                )
                                                )

                                                Spacer(modifier = Modifier.height(32.dp))

                                                Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(14.dp)
                                                ) {
                                                        ModernActionButton(
                                                                text = "Absent",
                                                                icon = Icons.Outlined.Close,
                                                                backgroundColor =
                                                                        AttendanceColors.Absent,
                                                                isEnabled = !isSaving,
                                                                isLoading = false,
                                                                modifier = Modifier.weight(1f),
                                                                onClick = onAbsent
                                                        )

                                                        ModernActionButton(
                                                                text = "Present",
                                                                icon = Icons.Filled.CheckCircle,
                                                                backgroundColor =
                                                                        AttendanceColors.Present,
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

/* -------------------- ANIMATED DIALOG ICON -------------------- */
@Composable
private fun AnimatedDialogIcon() {
        val infiniteTransition = rememberInfiniteTransition(label = "dialog_icon")
        val rotation by
                infiniteTransition.animateFloat(
                        initialValue = -10f,
                        targetValue = 10f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(3000, easing = EaseInOutCubic),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "rotation"
                )
        val scale by
                infiniteTransition.animateFloat(
                        initialValue = 0.95f,
                        targetValue = 1.05f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(2000, easing = EaseInOutCubic),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "scale"
                )

        Box(contentAlignment = Alignment.Center) {
                repeat(3) { index ->
                        Box(
                                modifier =
                                        Modifier.size((120 + index * 25).dp)
                                                .scale(scale)
                                                .alpha(0.2f - (index * 0.05f))
                                                .clip(CircleShape)
                                                .background(
                                                        Brush.radialGradient(
                                                                colors =
                                                                        listOf(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.4f
                                                                                        ),
                                                                                Color.Transparent
                                                                        )
                                                        )
                                                )
                        )
                }

                Box(
                        modifier =
                                Modifier.size(110.dp)
                                        .scale(scale)
                                        .shadow(
                                                elevation = 24.dp,
                                                shape = CircleShape,
                                                spotColor =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.5f
                                                        )
                                        )
                                        .clip(CircleShape)
                                        .background(
                                                Brush.linearGradient(
                                                        colors =
                                                                listOf(
                                                                        Color(0xFF667EEA),
                                                                        Color(0xFF764BA2),
                                                                        Color(0xFFF093FB)
                                                                ),
                                                        start = Offset.Zero,
                                                        end = Offset.Infinite
                                                )
                                        )
                                        .border(
                                                width = 4.dp,
                                                brush =
                                                        Brush.linearGradient(
                                                                colors =
                                                                        listOf(
                                                                                Color.White.copy(
                                                                                        alpha = 0.5f
                                                                                ),
                                                                                Color.White.copy(
                                                                                        alpha = 0.2f
                                                                                )
                                                                        )
                                                        ),
                                                shape = CircleShape
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(
                                                        Brush.radialGradient(
                                                                colors =
                                                                        listOf(
                                                                                Color.White.copy(
                                                                                        alpha = 0.3f
                                                                                ),
                                                                                Color.Transparent
                                                                        )
                                                        )
                                                )
                        )

                        Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier =
                                        Modifier.size(56.dp).graphicsLayer { rotationZ = rotation }
                        )
                }
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
        val buttonScale by
                animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f,
                        animationSpec =
                                spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                ),
                        label = "button_scale"
                )

        Button(
                onClick = onClick,
                enabled = isEnabled,
                modifier =
                        modifier.height(64.dp)
                                .scale(buttonScale)
                                .shadow(
                                        elevation = if (isEnabled && !isLoading) 12.dp else 4.dp,
                                        shape = RoundedCornerShape(20.dp),
                                        spotColor = backgroundColor.copy(alpha = 0.4f)
                                ),
                shape = RoundedCornerShape(20.dp),
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor =
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                contentPadding = PaddingValues(0.dp),
                interactionSource = interactionSource
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .background(
                                                if (isEnabled && !isLoading) {
                                                        Brush.linearGradient(
                                                                colors =
                                                                        listOf(
                                                                                backgroundColor,
                                                                                backgroundColor
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.85f
                                                                                        )
                                                                        ),
                                                                start = Offset(0f, 0f),
                                                                end = Offset.Infinite
                                                        )
                                                } else {
                                                        Brush.linearGradient(
                                                                colors =
                                                                        listOf(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceContainerHighest,
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceContainerHigh
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
                                                tint =
                                                        if (isEnabled) Color.White
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                        )
                                        Text(
                                                text = text,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color =
                                                        if (isEnabled) Color.White
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
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

        val startCal =
                Calendar.getInstance().apply {
                        val (h, m) = lecture.startTime.split(":").map { it.toInt() }
                        set(Calendar.HOUR_OF_DAY, h)
                        set(Calendar.MINUTE, m)
                }

        val endCal =
                Calendar.getInstance().apply {
                        val (h, m) = lecture.endTime.split(":").map { it.toInt() }
                        set(Calendar.HOUR_OF_DAY, h)
                        set(Calendar.MINUTE, m)
                }

        val lectureId =
                "${dateKey}_${SimpleDateFormat("HHmm", Locale.getDefault()).format(startCal.time)}_" +
                        SimpleDateFormat("HHmm", Locale.getDefault()).format(endCal.time)

        val subjectRef =
                db.collection("users")
                        .document(userId)
                        .collection("subjects")
                        .document(lecture.subjectId)

        val attendanceRef = subjectRef.collection("attendance").document(lectureId)

        db
                .runTransaction { tx ->
                        if (tx.get(attendanceRef).exists()) {
                                throw Exception("Attendance already marked")
                        }

                        val subjectSnap = tx.get(subjectRef)
                        val total = (subjectSnap.getLong("totalClasses") ?: 0) + 1
                        val attended =
                                if (status == "Present")
                                        (subjectSnap.getLong("attendedClasses") ?: 0) + 1
                                else subjectSnap.getLong("attendedClasses") ?: 0

                        val attendanceData =
                                mutableMapOf<String, Any>(
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
                                mapOf("totalClasses" to total, "attendedClasses" to attended)
                        )
                }
                .addOnCompleteListener { onDone() }
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

        // ✅ FIXED: Create a unique key using "SubjectName_StartTime"
        // This ensures that two lectures of the same subject at different times are both saved.
        val lectureMap =
                todayLectures.associate { lecture ->
                        val uniqueKey =
                                "${lecture.subjectName}_${lecture.startTime.replace(":", "")}"
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
                        "lectures" to
                                lectureMap, // Now contains nested objects instead of just strings
                        "updatedAt" to FieldValue.serverTimestamp()
                )

        try {
                snapshotRef.set(data).await()
                Log.d(
                        "SNAPSHOT_SUCCESS",
                        "Successfully saved snapshot with multiple same-subject lectures!"
                )
        } catch (e: Exception) {
                Log.e("SNAPSHOT_ERROR", "Failed to save snapshot", e)
        }
}

/* -------------------- SKELETON LOADING COMPONENTS -------------------- */
@Composable
fun SkeletonSummaryCard() {
        val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
        val alpha by
                infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 0.7f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(1000),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "skeleton alpha"
                )

        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .shadow(4.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
        ) {
                Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Box(
                                modifier =
                                        Modifier.width(180.dp)
                                                .height(24.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(alpha = alpha)
                                                )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                                modifier =
                                        Modifier.size(160.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(alpha = alpha)
                                                )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                                repeat(3) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(
                                                        modifier =
                                                                Modifier.width(70.dp)
                                                                        .height(36.dp)
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        10.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceVariant
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        alpha
                                                                                        )
                                                                        )
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Box(
                                                        modifier =
                                                                Modifier.width(60.dp)
                                                                        .height(18.dp)
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        9.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceVariant
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        alpha
                                                                                        )
                                                                        )
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
        val alpha by
                infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 0.7f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation = tween(1000),
                                        repeatMode = RepeatMode.Reverse
                                ),
                        label = "skeleton alpha"
                )

        Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier =
                                        Modifier.size(72.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(alpha = alpha)
                                                )
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth(0.7f)
                                                        .height(22.dp)
                                                        .clip(RoundedCornerShape(11.dp))
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = alpha
                                                                )
                                                        )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth(0.5f)
                                                        .height(18.dp)
                                                        .clip(RoundedCornerShape(9.dp))
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = alpha
                                                                )
                                                        )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Box(
                                        modifier =
                                                Modifier.width(100.dp)
                                                        .height(28.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = alpha
                                                                )
                                                        )
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
                        animationSpec =
                                spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                )
                )
        }

        val statusColor =
                when {
                        percentage >= 75 -> AttendanceColors.Present
                        percentage >= 60 -> AttendanceColors.Warning
                        else -> AttendanceColors.Absent
                }

        val statusText =
                when {
                        percentage >= 75 -> "Excellent"
                        percentage >= 60 -> "Good"
                        else -> "Needs Attention"
                }

        val statusIcon =
                when {
                        percentage >= 75 -> Icons.Default.CheckCircle
                        percentage >= 60 -> Icons.Default.Warning
                        else -> Icons.Default.ErrorOutline
                }

        val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .shadow(
                                        elevation = 12.dp,
                                        shape = RoundedCornerShape(32.dp),
                                        spotColor = statusColor.copy(alpha = 0.25f)
                                ),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(
                                                Brush.verticalGradient(
                                                        colors =
                                                                listOf(
                                                                        statusColor.copy(
                                                                                alpha = 0.08f
                                                                        ),
                                                                        MaterialTheme.colorScheme
                                                                                .surface.copy(
                                                                                alpha = 0.95f
                                                                        ),
                                                                        MaterialTheme.colorScheme
                                                                                .surface
                                                                )
                                                )
                                        )
                ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                                val circleRadius = 100f
                                drawCircle(
                                        color = statusColor.copy(alpha = 0.03f),
                                        radius = circleRadius,
                                        center = Offset(size.width * 0.85f, size.height * 0.2f)
                                )
                                drawCircle(
                                        color = statusColor.copy(alpha = 0.05f),
                                        radius = circleRadius * 1.5f,
                                        center = Offset(size.width * 0.15f, size.height * 0.8f)
                                )
                        }

                        Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
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
                                                        modifier =
                                                                Modifier.size(48.dp)
                                                                        .shadow(
                                                                                elevation = 8.dp,
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                14.dp
                                                                                        ),
                                                                                spotColor =
                                                                                        statusColor
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.3f
                                                                                                )
                                                                        )
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        14.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                Brush.linearGradient(
                                                                                        colors =
                                                                                                listOf(
                                                                                                        statusColor,
                                                                                                        statusColor
                                                                                                                .copy(
                                                                                                                        alpha =
                                                                                                                                0.8f
                                                                                                                )
                                                                                                )
                                                                                )
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.School,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(26.dp)
                                                        )
                                                }

                                                Column {
                                                        Text(
                                                                text = "Attendance",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleLarge,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 20.sp
                                                        )
                                                        Text(
                                                                text = "Overall Performance",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                                fontSize = 12.sp
                                                        )
                                                }
                                        }

                                        Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = statusColor.copy(alpha = 0.15f),
                                                border =
                                                        BorderStroke(
                                                                1.5.dp,
                                                                statusColor.copy(alpha = 0.3f)
                                                        )
                                        ) {
                                                Row(
                                                        modifier =
                                                                Modifier.padding(
                                                                        horizontal = 12.dp,
                                                                        vertical = 8.dp
                                                                ),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(6.dp)
                                                ) {
                                                        Icon(
                                                                imageVector = statusIcon,
                                                                contentDescription = null,
                                                                tint = statusColor,
                                                                modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                                text = statusText,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelMedium,
                                                                color = statusColor,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(220.dp)
                                ) {
                                        repeat(3) { index ->
                                                Canvas(
                                                        modifier =
                                                                Modifier.size(
                                                                                220.dp -
                                                                                        (index * 20)
                                                                                                .dp
                                                                        )
                                                                        .alpha(
                                                                                0.3f -
                                                                                        (index *
                                                                                                0.1f)
                                                                        )
                                                ) {
                                                        drawCircle(
                                                                brush =
                                                                        Brush.radialGradient(
                                                                                colors =
                                                                                        listOf(
                                                                                                statusColor
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.3f
                                                                                                        ),
                                                                                                Color.Transparent
                                                                                        )
                                                                        ),
                                                                radius = size.width / 2
                                                        )
                                                }
                                        }

                                        Canvas(modifier = Modifier.size(190.dp)) {
                                                drawCircle(
                                                        brush =
                                                                Brush.radialGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        surfaceVariantColor
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.3f
                                                                                                ),
                                                                                        surfaceVariantColor
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.1f
                                                                                                )
                                                                                )
                                                                ),
                                                        style = Stroke(width = 20.dp.toPx())
                                                )
                                        }

                                        CircularProgressIndicator(
                                                progress = { animatedPercentage.value / 100f },
                                                modifier = Modifier.size(190.dp),
                                                strokeWidth = 20.dp,
                                                trackColor = Color.Transparent,
                                                color = statusColor,
                                                strokeCap = StrokeCap.Round
                                        )

                                        Canvas(modifier = Modifier.size(150.dp)) {
                                                drawCircle(
                                                        brush =
                                                                Brush.radialGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.1f
                                                                                                ),
                                                                                        Color.Transparent
                                                                                )
                                                                )
                                                )
                                        }

                                        Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                                Row(
                                                        verticalAlignment = Alignment.Bottom,
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(2.dp)
                                                ) {
                                                        Text(
                                                                text =
                                                                        String.format(
                                                                                Locale.getDefault(),
                                                                                "%.1f",
                                                                                animatedPercentage
                                                                                        .value
                                                                        ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .displayLarge,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = statusColor,
                                                                fontSize = 56.sp
                                                        )
                                                        Text(
                                                                text = "%",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .headlineLarge,
                                                                fontWeight = FontWeight.Bold,
                                                                color =
                                                                        statusColor.copy(
                                                                                alpha = 0.7f
                                                                        ),
                                                                fontSize = 32.sp,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                bottom = 10.dp
                                                                        )
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        color =
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                                        .copy(alpha = 0.5f),
                                        shadowElevation = 4.dp
                                ) {
                                        Row(
                                                modifier = Modifier.padding(20.dp),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                ModernStatItem(
                                                        label = "Present",
                                                        value = attended.toString(),
                                                        icon = Icons.Default.CheckCircle,
                                                        color = AttendanceColors.Present
                                                )

                                                VerticalDivider(
                                                        modifier = Modifier.height(60.dp),
                                                        thickness = 2.dp,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant.copy(
                                                                        alpha = 0.5f
                                                                )
                                                )

                                                ModernStatItem(
                                                        label = "Total",
                                                        value = total.toString(),
                                                        icon = Icons.Default.CalendarMonth,
                                                        color = AttendanceColors.Info
                                                )

                                                VerticalDivider(
                                                        modifier = Modifier.height(60.dp),
                                                        thickness = 2.dp,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant.copy(
                                                                        alpha = 0.5f
                                                                )
                                                )

                                                ModernStatItem(
                                                        label = "Absent",
                                                        value = (total - attended).toString(),
                                                        icon = Icons.Default.Cancel,
                                                        color = AttendanceColors.Absent
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                if (percentage < 75) {
                                        val lecturesNeeded =
                                                calculateLecturesNeededFor75Percent(attended, total)
                                        MotivationalCard(
                                                icon = "💪",
                                                title = "Keep Going!",
                                                message =
                                                        "Attend $lecturesNeeded more ${if (lecturesNeeded == 1) "class" else "classes"} to reach 75%",
                                                color = AttendanceColors.Warning
                                        )
                                } else {
                                        MotivationalCard(
                                                icon = "🗿",
                                                title = "Amazing Work!",
                                                message = "You're maintaining excellent attendance",
                                                color = AttendanceColors.Present
                                        )
                                }
                        }
                }
        }
}

/* -------------------- MODERN STAT ITEM -------------------- */
@Composable
private fun ModernStatItem(label: String, value: String, icon: ImageVector, color: Color) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                Box(
                        modifier =
                                Modifier.size(46.dp)
                                        .shadow(
                                                elevation = 6.dp,
                                                shape = CircleShape,
                                                spotColor = color.copy(alpha = 0.3f)
                                        )
                                        .clip(CircleShape)
                                        .background(
                                                Brush.radialGradient(
                                                        colors =
                                                                listOf(
                                                                        color.copy(alpha = 0.2f),
                                                                        color.copy(alpha = 0.1f)
                                                                )
                                                )
                                        ),
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = color,
                        fontSize = 24.sp
                )

                Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                )
        }
}

/* -------------------- MOTIVATIONAL CARD -------------------- */
@Composable
private fun MotivationalCard(icon: String, title: String, message: String, color: Color) {
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = color.copy(alpha = 0.1f),
                border = BorderStroke(1.5.dp, color.copy(alpha = 0.25f)),
                shadowElevation = 2.dp
        ) {
                Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier =
                                        Modifier.size(44.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        Brush.linearGradient(
                                                                colors =
                                                                        listOf(
                                                                                color.copy(
                                                                                        alpha = 0.3f
                                                                                ),
                                                                                color.copy(
                                                                                        alpha = 0.2f
                                                                                )
                                                                        )
                                                        )
                                                ),
                                contentAlignment = Alignment.Center
                        ) { Text(text = icon, fontSize = 22.sp) }

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
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
