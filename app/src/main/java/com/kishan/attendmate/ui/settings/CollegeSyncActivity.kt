package com.kishan.attendmate.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.theme.AttendMateTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

// ─── NEW IMPORTS FOR BACKGROUND SERVICE + EVENT BUS ───────────────────────
import android.app.ActivityManager
import android.content.Intent
import androidx.core.content.ContextCompat

// ─── Activity ────────────────────────────────────────────────────────────────

class CollegeSyncActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AttendMateTheme { CollegeSyncScreen(onBack = { finish() }) } }
    }
}

// ─── State machine ───────────────────────────────────────────────────────────

enum class ScrapePhase {
    IDLE,
    LOGIN,
    LOGIN_INJECTED,
    SCRAPING,
    EXTRACTING
}

// ─── JS bridge (proper class so @JavascriptInterface is visible) ─────────────

class ScraperBridge(
    private val progressCb: (String) -> Unit,
    private val errorCb: (String) -> Unit,
    private val dataCb: (String) -> Unit,
    private val loginSuccessCb: () -> Unit
) {
    @JavascriptInterface
    fun onProgressUpdate(msg: String) {
        Log.d("CollegeSync", "JS Progress: $msg")
        progressCb(msg)
    }

    @JavascriptInterface
    fun onError(error: String) {
        Log.e("CollegeSync", "JS Error: $error")
        errorCb(error)
    }

    @JavascriptInterface
    fun onDataExtracted(json: String) {
        Log.d("CollegeSync", "JS Data extracted, length=${json.length}")
        dataCb(json)
    }

    @JavascriptInterface
    fun onLoginSuccess() {
        Log.d("CollegeSync", "JS Login success callback")
        loginSuccessCb()
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CollegeSyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("CollegeSyncPrefs", Context.MODE_PRIVATE)

    var email by remember { mutableStateOf(prefs.getString("email", "") ?: "") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberCredentials by remember {
        mutableStateOf(prefs.getBoolean("rememberCredentials", true))
    }

    val phaseState = remember { mutableStateOf(ScrapePhase.IDLE) }

    var statusText by remember { mutableStateOf("Ready to sync") }
    var scrapedData by remember { mutableStateOf<List<CollegeAttendanceRecord>>(emptyList()) }
    var appData by remember { mutableStateOf<List<CollegeAttendanceRecord>>(emptyList()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showLoginSheet by remember { mutableStateOf(false) }

    // Tab & Pager state
    val pagerState = rememberPagerState(pageCount = { 2 })
    val haptic = LocalHapticFeedback.current

    var appSubjectMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Subject filter
    var subjects by remember { mutableStateOf<List<String>>(listOf("All")) }
    var selectedSubject by remember { mutableStateOf("All") }

    // Status filter
    var statusFilter by remember { mutableStateOf("All") } // "All", "Present", "Absent"
    // Sort filter
    var sortOrder by remember { mutableStateOf("Newest") } // "Newest", "Oldest"

    // Compare filter
    var compareFilter by remember {
        mutableStateOf<String?>(null)
    } // null, "Match", "Mismatch", "App", "College"

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listStateTab0 = rememberLazyListState()
    val listStateTab1 = rememberLazyListState()
    var isRefreshing by remember { mutableStateOf(false) }
    var isInitialLoading by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()
    val isScraping: Boolean = phaseState.value != ScrapePhase.IDLE

    // Load persisted scraped data + app data from Firestore on start
    LaunchedEffect(Unit) {
        isInitialLoading = true
        val saved = loadScrapedData(context)
        scrapedData = saved
        subjects = buildSubjectList(saved)

        // Wait gracefully to ensure the 3D loader looks cinematic, while fetching from network
        val startTime = System.currentTimeMillis()
        appData = loadAppAttendanceFromFirestore()
        appSubjectMap = loadAppSubjectMapFromFirestore()

        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed < 2000) {
            kotlinx.coroutines.delay(2000 - elapsed)
        }

        isInitialLoading = false
    }

    suspend fun onRefresh() {
        isRefreshing = true
        val saved = loadScrapedData(context)
        scrapedData = saved
        subjects = buildSubjectList(saved)
        appData = loadAppAttendanceFromFirestore()
        appSubjectMap = loadAppSubjectMapFromFirestore()
        isRefreshing = false
    }

    // Reload app data after scraping completes
    LaunchedEffect(isScraping) {
        if (!isScraping && scrapedData.isNotEmpty()) {
            appData = loadAppAttendanceFromFirestore()
        }
    }

    // Rebuild subject filter whenever data changes
    LaunchedEffect(scrapedData) {
        subjects = buildSubjectList(scrapedData)
        if (selectedSubject !in subjects) selectedSubject = "All"
    }

    val filteredData: List<CollegeAttendanceRecord> =
        remember(scrapedData, selectedSubject, statusFilter, sortOrder) {
            var list = scrapedData
            if (selectedSubject != "All") {
                list = list.filter { it.subject == selectedSubject }
            }
            if (statusFilter != "All") {
                list = list.filter { it.status.equals(statusFilter, ignoreCase = true) }
            }

            list.sortedWith { a, b ->
                val tA =
                    try {
                        java.text.SimpleDateFormat(
                            "dd/MM/yyyy",
                            java.util.Locale.getDefault()
                        )
                            .parse(a.date)
                            ?.time
                            ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                val tB =
                    try {
                        java.text.SimpleDateFormat(
                            "dd/MM/yyyy",
                            java.util.Locale.getDefault()
                        )
                            .parse(b.date)
                            ?.time
                            ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                if (sortOrder == "Oldest") tA.compareTo(tB) else tB.compareTo(tA)
            }
        }

    val displayItems =
        remember(filteredData, appData, selectedSubject, compareFilter) {
            val list = mutableListOf<CompareDisplayItem>()
            val matchedAppRecords = mutableSetOf<CollegeAttendanceRecord>()

            // Build a strict 1-to-1 mapping from App Subject to the BEST Scraped Subject
            val allScrapedSubjects = scrapedData.map { it.subject }.distinct()
            val appToScrapedSubjectMap =
                appData.map { it.subject }.distinct().associateWith { appSubj ->
                    val s1 = appSubj.lowercase().replace(Regex("[^a-z0-9]"), "")

                    // Exact alphanumeric match first
                    var bestMatch =
                        allScrapedSubjects.find { scrapedSubj ->
                            val s2 =
                                scrapedSubj
                                    .lowercase()
                                    .replace(Regex("[^a-z0-9]"), "")
                            s1 == s2
                        }

                    // If no exact match, find the longest containing match
                    if (bestMatch == null) {
                        bestMatch =
                            allScrapedSubjects
                                .filter { scrapedSubj ->
                                    val s2 =
                                        scrapedSubj
                                            .lowercase()
                                            .replace(Regex("[^a-z0-9]"), "")
                                    s1.contains(s2) || s2.contains(s1)
                                }
                                .maxByOrNull { it.length }
                    }

                    bestMatch ?: appSubj // Fallback
                }

            for (scraped in filteredData) {
                // Pass 1: Match Subject, Date, AND Status
                var matchedApp =
                    appData.find { app ->
                        app !in matchedAppRecords &&
                                appToScrapedSubjectMap[app.subject] == scraped.subject &&
                                app.date == scraped.date &&
                                app.status.equals(scraped.status, ignoreCase = true)
                    }

                // Pass 2: Subject and Date match (status differs)
                if (matchedApp == null) {
                    matchedApp =
                        appData.find { app ->
                            app !in matchedAppRecords &&
                                    appToScrapedSubjectMap[app.subject] ==
                                    scraped.subject &&
                                    app.date == scraped.date
                        }
                }

                if (matchedApp != null) {
                    matchedAppRecords.add(matchedApp)
                }
                val matchedAppSubjName =
                    appToScrapedSubjectMap.entries
                        .firstOrNull { it.value == scraped.subject }
                        ?.key
                list.add(CompareDisplayItem(scraped, matchedApp, matchedAppSubjName))
            }

            val unmatchedAppRecords = appData.filter { it !in matchedAppRecords }
            val relevantUnmatched =
                unmatchedAppRecords.filter { app ->
                    if (selectedSubject == "All") true
                    else {
                        appToScrapedSubjectMap[app.subject] == selectedSubject
                    }
                }

            for (app in relevantUnmatched) {
                list.add(CompareDisplayItem(null, app, app.subject))
            }

            var filteredList = list.toList()
            if (compareFilter != null) {
                filteredList =
                    filteredList.filter { item ->
                        val sRec = item.scrapedRecord
                        val aRec = item.appRecord
                        val hasMismatch =
                            sRec != null &&
                                    aRec != null &&
                                    !aRec.status.equals(sRec.status, ignoreCase = true)
                        when (compareFilter) {
                            "Match" -> sRec != null && aRec != null && !hasMismatch
                            "Mismatch" -> hasMismatch
                            "App" -> sRec == null
                            "College" -> aRec == null
                            else -> true
                        }
                    }
            }

            filteredList.sortedByDescending {
                val dateStr = it.scrapedRecord?.date ?: it.appRecord?.date ?: ""
                try {
                    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        .parse(dateStr)
                        ?.time
                        ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }
        }

    val lastSyncTime = prefs.getString("lastSyncTime", "Never")

    var previousIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    var showFab by remember { mutableStateOf(true) }

    val currentListState = if (pagerState.currentPage == 0) listStateTab0 else listStateTab1

    LaunchedEffect(currentListState) {
        snapshotFlow {
            currentListState.firstVisibleItemIndex to currentListState.firstVisibleItemScrollOffset
        }
            .collect { (index, offset) ->
                if (index > previousIndex ||
                    (index == previousIndex && offset > previousScrollOffset + 10)
                ) {
                    showFab = false
                } else if (index < previousIndex ||
                    (index == previousIndex && offset < previousScrollOffset - 10)
                ) {
                    showFab = true
                }
                if (index == 0 && offset == 0) showFab = true
                previousIndex = index
                previousScrollOffset = offset
            }
    }

    // ── NEW: Live updates from background service via EventBus (planets + progress) ──
    LaunchedEffect(Unit) {
        ScrapingEventBus.events.collect { event ->
            when (event) {
                is ScrapingEvent.SetPhase -> phaseState.value = event.phase
                is ScrapingEvent.UpdateProgress -> statusText = event.text
                // Explicitly ignore the 3D events to satisfy the compiler
                is ScrapingEvent.FinishSubject -> {}
                is ScrapingEvent.RecordExtracted -> {}
                is ScrapingEvent.SpawnSubject -> {}
                is ScrapingEvent.StartExtraction -> {}
            }
        }
    }

    // ── NEW: Auto-restore scraping overlay + status if service is still running when app is reopened ──
    LaunchedEffect(Unit) {
        val isRunning = isServiceRunning(context, CollegeSyncService::class.java)
        if (isRunning && phaseState.value == ScrapePhase.IDLE) {
            phaseState.value = ScrapePhase.SCRAPING
            statusText = prefs.getString("currentSyncStatus", "Syncing in background...") ?: ""
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text("College Sync", fontWeight = FontWeight.Bold)
                            Text(
                                "${scrapedData.size} records · Last synced $lastSyncTime",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor =
                                MaterialTheme.colorScheme.surfaceContainer
                        ),
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButtonPosition = FabPosition.End,
            floatingActionButton = {
                AnimatedVisibility(
                    visible = showFab,
                    enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut()
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { if (!isScraping) showLoginSheet = true },
                        containerColor =
                            if (isScraping) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.primaryContainer,
                        contentColor =
                            if (isScraping)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        if (isScraping) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sync Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { coroutineScope.launch { onRefresh() } },
                modifier =
                    Modifier.fillMaxSize()
                        .padding(padding)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme
                                        .surfaceContainerLowest
                                )
                            )
                        )
            ) {
                LazyColumn(
                    state = currentListState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // ── Header ───────────────────────────────────────────────
                    if (scrapedData.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor =
                                            MaterialTheme.colorScheme.primaryContainer
                                                .copy(alpha = 0.3f)
                                    )
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        "📊 Attendance Portal",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Sync your college attendance and compare with your app records.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Spacer(Modifier.height(8.dp))
                            val total = scrapedData.size
                            val present = scrapedData.count { it.status.equals("Present", true) }
                            val absent = scrapedData.count { it.status.equals("Absent", true) }

                            // 1. Calculate as a Float instead of rounding to Int
                            val pctFloat = if (total > 0) (present.toFloat() / total) * 100 else 0f
                            // 2. Format it to exactly 2 decimal places (e.g., "74.85")
                            val pctDisplay = String.format(Locale.getDefault(), "%.2f", pctFloat)

                            val barColor = when {
                                pctFloat >= 75f -> Color(0xFF4CAF50)
                                pctFloat >= 60f -> Color(0xFFFFC107)
                                else -> Color(0xFFEF5350)
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor =
                                            MaterialTheme.colorScheme
                                                .surfaceContainerHigh
                                    )
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                "$total",
                                                style = MaterialTheme.typography.headlineLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "Total Records",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Box(
                                            modifier =
                                                Modifier.width(1.dp)
                                                    .height(50.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme
                                                            .outlineVariant
                                                    )
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                "$present",
                                                style = MaterialTheme.typography.headlineLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4CAF50)
                                            )
                                            Text(
                                                "Present",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                "$absent",
                                                style = MaterialTheme.typography.headlineLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFEF5350)
                                            )
                                            Text(
                                                "Absent",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Box(
                                            modifier =
                                                Modifier.width(1.dp)
                                                    .height(50.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme
                                                            .outlineVariant
                                                    )
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "$pctDisplay%", // <-- Use pctDisplay here
                                                style = MaterialTheme.typography.headlineLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = barColor
                                            )
                                            Text(
                                                "Attendance",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(20.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            "Overall: $pctDisplay%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = barColor
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    val progressFloat by
                                    animateFloatAsState(
                                        targetValue =
                                            if (total > 0) present / total.toFloat()
                                            else 0f,
                                        animationSpec = tween(800),
                                        label = "prog"
                                    )
                                    LinearProgressIndicator(
                                        progress = { progressFloat },
                                        modifier =
                                            Modifier.fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                        color = barColor,
                                        trackColor =
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                }
                            }

                            if (selectedSubject != "All") {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        "Subject: $selectedSubject",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier =
                                            Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 4.dp
                                            ),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // ── Status Banner ───────────────────────────────────────────────
                    val hasError = statusText.contains("error", ignoreCase = true)
                    item {
                        AnimatedVisibility(
                            visible = isScraping || hasError,
                            enter = slideInVertically() + fadeIn(),
                            exit = slideOutVertically() + fadeOut()
                        ) {
                            val color =
                                if (hasError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            val bgColor = color.copy(alpha = 0.1f)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = bgColor)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier =
                                            Modifier.fillMaxHeight()
                                                .width(3.dp)
                                                .background(color)
                                                .align(Alignment.CenterStart)
                                    )
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isScraping) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = color
                                                )
                                            } else if (hasError) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = color,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                statusText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = color,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        if (isScraping && statusText.contains("Expecting") ||
                                            statusText.contains("page")
                                        ) {
                                            Spacer(Modifier.height(8.dp))
                                            val parts = statusText.split(":", "—")
                                            if (parts.size >= 2) {
                                                Text(
                                                    parts[0].trim(),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = color
                                                )
                                                Text(
                                                    parts[1].trim(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = color.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Data Section (only when there is data) ───────────────
                    if (scrapedData.isNotEmpty()) {

                        // Tab Row Upgrade
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor =
                                            MaterialTheme.colorScheme.surfaceVariant
                                                .copy(alpha = 0.5f)
                                    )
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                    val mismatches = displayItems.count { it.hasMismatch() }
                                    SegmentedTab(
                                        selected = pagerState.currentPage == 0,
                                        text = "Subject Data",
                                        icon = Icons.Default.List,
                                        badgeCount = filteredData.size,
                                        badgeColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(0)
                                            }
                                        }
                                    )
                                    SegmentedTab(
                                        selected = pagerState.currentPage == 1,
                                        text = "Compare",
                                        icon = Icons.Default.CompareArrows,
                                        badgeCount = mismatches,
                                        badgeColor =
                                            if (mismatches > 0) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(1)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Filter Row Upgrade
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Filter", style = MaterialTheme.typography.labelLarge)
                                    // Sort Order toggle
                                    TextButton(
                                        onClick = {
                                            sortOrder =
                                                if (sortOrder == "Newest") "Oldest"
                                                else "Newest"
                                        },
                                        contentPadding =
                                            PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Sort,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(sortOrder, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(subjects) { subj ->
                                        FilterChip(
                                            selected = selectedSubject == subj,
                                            onClick = {
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                selectedSubject = subj
                                            },
                                            label = { Text(subj) }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val statusOptions = listOf("All", "Present", "Absent")
                                    items(statusOptions) { status ->
                                        FilterChip(
                                            selected = statusFilter == status,
                                            onClick = {
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                statusFilter = status
                                            },
                                            label = { Text(status) }
                                        )
                                    }
                                }

                                // Active filters summary
                                if (selectedSubject != "All" || statusFilter != "All") {
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (selectedSubject != "All") {
                                                FilterChip(
                                                    selected = true,
                                                    onClick = { selectedSubject = "All" },
                                                    label = { Text("Subject: $selectedSubject") },
                                                    trailingIcon = {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            null,
                                                            Modifier.size(14.dp)
                                                        )
                                                    }
                                                )
                                            }
                                            if (statusFilter != "All") {
                                                FilterChip(
                                                    selected = true,
                                                    onClick = { statusFilter = "All" },
                                                    label = { Text("Status: $statusFilter") },
                                                    trailingIcon = {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            null,
                                                            Modifier.size(14.dp)
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                selectedSubject = "All"
                                                statusFilter = "All"
                                            }
                                        ) {
                                            Text(
                                                "Clear all",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        "${filteredData.size} of ${scrapedData.size} records",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Pager Content
                        item {
                            HorizontalPager(
                                state = pagerState,
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) { page ->
                                if (page == 0) {
                                    // ── Tab 0: Subject Data ──────────────────────────
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        if (filteredData.isEmpty()) {
                                            EmptyStateCardFilter(
                                                "No records match your filters",
                                                onClear = {
                                                    selectedSubject = "All"
                                                    statusFilter = "All"
                                                }
                                            )
                                        } else {
                                            var currentDate = ""
                                            filteredData.forEach { record ->
                                                val recordDateObj =
                                                    try {
                                                        java.text.SimpleDateFormat(
                                                            "dd/MM/yyyy",
                                                            Locale.getDefault()
                                                        )
                                                            .parse(record.date)
                                                    } catch (e: Exception) {
                                                        null
                                                    }
                                                val recordDateStr =
                                                    recordDateObj?.let {
                                                        java.text.SimpleDateFormat(
                                                            "EEEE, dd MMM yyyy",
                                                            Locale.getDefault()
                                                        )
                                                            .format(it)
                                                    }
                                                        ?: record.date

                                                if (currentDate != recordDateStr) {
                                                    currentDate = recordDateStr
                                                    Row(
                                                        modifier =
                                                            Modifier.fillMaxWidth()
                                                                .padding(vertical = 8.dp),
                                                        verticalAlignment =
                                                            Alignment.CenterVertically
                                                    ) {
                                                        HorizontalDivider(
                                                            modifier = Modifier.weight(1f),
                                                            color =
                                                                MaterialTheme.colorScheme
                                                                    .outlineVariant
                                                        )
                                                        Surface(
                                                            shape = RoundedCornerShape(16.dp),
                                                            color =
                                                                MaterialTheme.colorScheme
                                                                    .surfaceContainerHigh,
                                                            modifier =
                                                                Modifier.padding(
                                                                    horizontal = 12.dp
                                                                )
                                                        ) {
                                                            Text(
                                                                currentDate,
                                                                style =
                                                                    MaterialTheme.typography
                                                                        .labelSmall,
                                                                fontWeight = FontWeight.SemiBold,
                                                                modifier =
                                                                    Modifier.padding(
                                                                        horizontal = 12.dp,
                                                                        vertical = 6.dp
                                                                    )
                                                            )
                                                        }
                                                        HorizontalDivider(
                                                            modifier = Modifier.weight(1f),
                                                            color =
                                                                MaterialTheme.colorScheme
                                                                    .outlineVariant
                                                        )
                                                    }
                                                }

                                                Box(modifier = Modifier.animateItem()) {
                                                    SubjectDataCard(record)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // ── Tab 1: Compare Data ──────────────────────────
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        val matched =
                                            displayItems.count {
                                                it.scrapedRecord != null &&
                                                        it.appRecord != null &&
                                                        it.scrapedRecord.status.equals(
                                                            it.appRecord.status,
                                                            ignoreCase = true
                                                        )
                                            }
                                        val mismatched = displayItems.count { it.hasMismatch() }
                                        val missingInApp = displayItems.count { it.appRecord == null }
                                        val missingInCollege =
                                            displayItems.count { it.scrapedRecord == null }

                                        // Horizontally scrollable summary bar
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            item {
                                                SummaryChipCard(
                                                    "Match",
                                                    matched,
                                                    Color(0xFF4CAF50),
                                                    Icons.Default.CheckCircle,
                                                    compareFilter == "Match"
                                                ) {
                                                    compareFilter =
                                                        if (compareFilter == "Match") null
                                                        else "Match"
                                                }
                                            }
                                            item {
                                                SummaryChipCard(
                                                    "Mismatch",
                                                    mismatched,
                                                    MaterialTheme.colorScheme.error,
                                                    Icons.Default.Warning,
                                                    compareFilter == "Mismatch"
                                                ) {
                                                    compareFilter =
                                                        if (compareFilter == "Mismatch") null
                                                        else "Mismatch"
                                                }
                                            }
                                            item {
                                                SummaryChipCard(
                                                    "App Only",
                                                    missingInCollege,
                                                    MaterialTheme.colorScheme.tertiary,
                                                    Icons.Default.PhoneAndroid,
                                                    compareFilter == "App"
                                                ) {
                                                    compareFilter =
                                                        if (compareFilter == "App") null else "App"
                                                }
                                            }
                                            item {
                                                SummaryChipCard(
                                                    "College Only",
                                                    missingInApp,
                                                    MaterialTheme.colorScheme.secondary,
                                                    Icons.Default.School,
                                                    compareFilter == "College"
                                                ) {
                                                    compareFilter =
                                                        if (compareFilter == "College") null
                                                        else "College"
                                                }
                                            }
                                        }

                                        if (displayItems.isEmpty()) {
                                            EmptyStateCardFilter("No records to compare", null)
                                        } else {
                                            displayItems.forEach { item ->
                                                Box(modifier = Modifier.animateItem()) {
                                                    CompareDataCard(
                                                        item,
                                                        onAddToApp = { record, matchedSubj ->
                                                            addScrapedRecordToApp(
                                                                context,
                                                                record,
                                                                matchedSubj,
                                                                appSubjectMap
                                                            ) {
                                                                Toast.makeText(
                                                                    context,
                                                                    "Added to app!",
                                                                    Toast.LENGTH_SHORT
                                                                )
                                                                    .show()
                                                                coroutineScope.launch {
                                                                    onRefresh()
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                            }

                                            if (mismatched > 0) {
                                                Spacer(Modifier.height(8.dp))
                                                Button(
                                                    onClick = {
                                                        Toast.makeText(
                                                            context,
                                                            "Fix mismatches not fully implemented.",
                                                            Toast.LENGTH_SHORT
                                                        )
                                                            .show()
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors =
                                                        ButtonDefaults.buttonColors(
                                                            containerColor =
                                                                MaterialTheme
                                                                    .colorScheme
                                                                    .errorContainer,
                                                            contentColor =
                                                                MaterialTheme
                                                                    .colorScheme
                                                                    .onErrorContainer
                                                        )
                                                ) {
                                                    Text(
                                                        "Fix $mismatched Mismatches",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(48.dp)) }
                }

                // ── Invisible WebView (scraping engine) ──────────────────────
                Box(modifier = Modifier.size(1.dp).alpha(0f)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString =
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                                addJavascriptInterface(
                                    ScraperBridge(
                                        progressCb = { msg: String ->
                                            coroutineScope.launch(Dispatchers.Main) {
                                                statusText = msg

                                                // --- NEW: Parse scraper text and trigger 3D planet animations! ---
                                                Regex("Processing:\\s*([^(]+)\\s*\\((\\d+)/(\\d+)\\)").find(msg)?.let {
                                                    val name = it.groupValues[1].trim()
                                                    ScrapingEventBus.tryEmit(ScrapingEvent.SpawnSubject(name))
                                                    ScrapingEventBus.tryEmit(ScrapingEvent.StartExtraction(name))
                                                }

                                                Regex("(.*?)\\s*—\\s*page\\s*\\d+\\s*\\((\\d+)/(\\d+)\\)").find(msg)?.let {
                                                    val pct = ((it.groupValues[2].toFloat() / it.groupValues[3].toFloat()) * 100).coerceIn(0f, 100f)
                                                    ScrapingEventBus.tryEmit(ScrapingEvent.UpdateProgress(pct, msg))
                                                }

                                                Regex("(.*?):\\s*Scraped\\s*(\\d+)\\s*records").find(msg)?.let {
                                                    val name = it.groupValues[1].trim()
                                                    val count = it.groupValues[2].toIntOrNull() ?: 0
                                                    if (count > 0) ScrapingEventBus.tryEmit(ScrapingEvent.RecordExtracted(count))
                                                    ScrapingEventBus.tryEmit(ScrapingEvent.FinishSubject(name))
                                                }

                                                if (msg.contains("No attendance data") || msg.contains("Skipping")) {
                                                    Regex("(.*?):\\s*(No attendance|Skipping)").find(msg)?.let {
                                                        ScrapingEventBus.tryEmit(ScrapingEvent.FinishSubject(it.groupValues[1].trim()))
                                                    }
                                                }
                                            }
                                        },
                                        errorCb = { error: String ->
                                            coroutineScope.launch(Dispatchers.Main) {
                                                Log.e("CollegeSync", "Scrape error: $error")
                                                statusText = "Error: $error"
                                                phaseState.value = ScrapePhase.IDLE
                                            }
                                        },
                                        dataCb = { json: String ->
                                            coroutineScope.launch(Dispatchers.Main) {
                                                statusText = "Processing data..."
                                                try {
                                                    val list =
                                                        mutableListOf<
                                                                CollegeAttendanceRecord>()
                                                    val arr = JSONArray(json)
                                                    if (arr.length() == 0) {
                                                        Log.d(
                                                            "CollegeSync",
                                                            "Ignoring empty extraction from premature SPA load"
                                                        )
                                                        return@launch
                                                    }
                                                    for (i in 0 until arr.length()) {
                                                        val o = arr.getJSONObject(i)
                                                        list.add(
                                                            CollegeAttendanceRecord(
                                                                subject =
                                                                    o.optString(
                                                                        "subject",
                                                                        ""
                                                                    ),
                                                                date =
                                                                    o.optString(
                                                                        "date",
                                                                        ""
                                                                    ),
                                                                fromTime =
                                                                    o.optString(
                                                                        "fromTime",
                                                                        ""
                                                                    ),
                                                                toTime =
                                                                    o.optString(
                                                                        "toTime",
                                                                        ""
                                                                    ),
                                                                topic =
                                                                    o.optString(
                                                                        "topic",
                                                                        ""
                                                                    ),
                                                                status =
                                                                    o.optString(
                                                                        "status",
                                                                        ""
                                                                    )
                                                            )
                                                        )
                                                    }
                                                    scrapedData = list
                                                    saveScrapedData(context, list)
                                                    statusText =
                                                        "Sync complete — ${list.size} records."
                                                    Log.d(
                                                        "CollegeSync",
                                                        "Saved ${list.size} records"
                                                    )
                                                    phaseState.value = ScrapePhase.IDLE
                                                    Toast.makeText(
                                                        context,
                                                        "Sync finished!",
                                                        Toast.LENGTH_SHORT
                                                    )
                                                        .show()
                                                } catch (ex: Exception) {
                                                    Log.e("CollegeSync", "Parse error", ex)
                                                    statusText =
                                                        "Parse error: ${ex.message}"
                                                    phaseState.value = ScrapePhase.IDLE
                                                }
                                            }
                                        },
                                        loginSuccessCb = {
                                            coroutineScope.launch(Dispatchers.Main) {
                                                if (phaseState.value == ScrapePhase.LOGIN_INJECTED) {
                                                    statusText = "Logged in! Loading attendance page..."
                                                    Log.d("CollegeSync", "Login success — navigating to attendance")
                                                    phaseState.value = ScrapePhase.SCRAPING
                                                    webViewRef?.loadUrl("https://attendence-system-1910.vercel.app/students/current/attendances")
                                                }
                                            }
                                        }
                                    ),
                                    "Android"
                                )

                                webViewClient =
                                    object : WebViewClient() {
                                        override fun onPageFinished(
                                            view: WebView,
                                            url: String
                                        ) {
                                            super.onPageFinished(view, url)
                                            val phase = phaseState.value
                                            Log.d(
                                                "CollegeSync",
                                                "onPageFinished url=$url phase=$phase"
                                            )

                                            when {
                                                // ── LOGIN phase + login page → fill form ──
                                                phase == ScrapePhase.LOGIN && url.contains("/users/login") -> {
                                                    statusText = "Filling login form..."
                                                    phaseState.value = ScrapePhase.LOGIN_INJECTED // Lock to prevent double injection

                                                    val safeEmail = email.replace("\\", "\\\\").replace("'", "\\'")
                                                    val safePassword = password.replace("\\", "\\\\").replace("'", "\\'")
                                                    val js = ScraperScripts.buildLoginScript(safeEmail, safePassword)
                                                    Log.d("CollegeSync", "Injecting login script")
                                                    view.evaluateJavascript(js, null)
                                                }

                                                // ── SCRAPING phase + attendance page → scrape ──
                                                phase == ScrapePhase.SCRAPING && url.contains("/students/current/attendances") -> {
                                                    statusText = "Extracting data..."
                                                    phaseState.value = ScrapePhase.EXTRACTING // Lock to prevent double injection

                                                    Log.d("CollegeSync", "Injecting scraping script")
                                                    view.evaluateJavascript(ScraperScripts.buildScrapingScript(), null)
                                                }

                                                // ── SCRAPING phase + bounced to login → expired ──
                                                (phase == ScrapePhase.SCRAPING || phase == ScrapePhase.EXTRACTING) && url.contains("/users/login") -> {
                                                    coroutineScope.launch(Dispatchers.Main) {
                                                        statusText = "Session expired — please sync again."
                                                        phaseState.value = ScrapePhase.IDLE
                                                    }
                                                }
                                            }
                                        }
                                    }

                                webViewRef = this
                            }
                        },
                        onRelease = { webView ->
                            webView.removeJavascriptInterface("Android")
                            webView.destroy()
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = phaseState.value != ScrapePhase.IDLE,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(initialScale = 0.92f, animationSpec = tween(400, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 1.05f, animationSpec = tween(300))
        ) {
            ScrapingAnimationOverlay(
                phase = phaseState.value,
                statusText = statusText,
                onCancel = {
                    webViewRef?.stopLoading()
                    phaseState.value = ScrapePhase.IDLE

                    // ADD THIS NEW LINE RIGHT HERE:
                    ScrapingEventBus.clearHistory()

                    val stopIntent = Intent(context, CollegeSyncService::class.java).apply {
                        action = CollegeSyncService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                }
            )
        }

        // ── Full-screen Premium 3D Loader Overlay ─────────────────────────
        AnimatedVisibility(
            visible = isInitialLoading,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(600))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF030712)) // Deep dark space
                    .pointerInput(Unit) {}, // Block touches
                contentAlignment = Alignment.Center
            ) {
                Cyber3DDataLoader()
            }
        }
    }

    // ── Login Dialog ──────────────────────────────────────────────
    if (showLoginSheet) {
        val safeEmail = email.replace("'", "\\'").replace("\"", "\\\"")
        val safePassword = password.replace("'", "\\'").replace("\"", "\\\"")

        ModalBottomSheet(
            onDismissRequest = { showLoginSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp).padding(bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Sync Credentials",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter your college portal login. Credentials are saved locally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))

                val isEmailValid =
                    email.isEmpty() ||
                            android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("College Email (Optional Format Check)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    isError = email.isNotEmpty() && !isEmailValid,
                    supportingText =
                        if (email.isNotEmpty() && !isEmailValid) {
                            { Text("Doesn't look like an email") }
                        } else null
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation =
                        if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image =
                            if (passwordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = image,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberCredentials,
                        onCheckedChange = { rememberCredentials = it }
                    )
                    Text(
                        "Remember credentials",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier =
                            Modifier.clickable {
                                rememberCredentials = !rememberCredentials
                            }
                    )
                }

                Spacer(Modifier.height(24.dp))

                var isPressed by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "btn_scale")

                Button(
                    onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                            showLoginSheet = false
                        }

                        val editor = prefs.edit()
                        if (rememberCredentials) {
                            editor.putString("email", email).putString("password", password)
                        } else {
                            editor.remove("email").remove("password")
                        }
                        editor.putBoolean("rememberCredentials", rememberCredentials).apply()

                        ScrapingEventBus.clearHistory()

                        phaseState.value = ScrapePhase.LOGIN
                        statusText = "Starting login..."
                        Log.d("CollegeSync", "Sync started from bottom sheet")
                        webViewRef?.loadUrl(
                            "https://attendence-system-1910.vercel.app/users/login"
                        )
                        // ── NEW: Start Foreground Service for background-safe scraping (planets will still spawn via EventBus) ──
                        val serviceIntent = Intent(context, CollegeSyncService::class.java).apply {
                            putExtra("EMAIL", email)
                            putExtra("PASSWORD", password)
                        }
                        ContextCompat.startForegroundService(context, serviceIntent)
                        // Old webView path is kept (no deletion) for immediate foreground feedback
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when (event.type) {
                                            androidx.compose.ui.input.pointer
                                                .PointerEventType.Press ->
                                                isPressed = true
                                            androidx.compose.ui.input.pointer
                                                .PointerEventType.Release ->
                                                isPressed = false
                                        }
                                    }
                                }
                            },
                    enabled = email.isNotEmpty() && password.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Default.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Start Sync",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

// ── NEW HELPER: Check if CollegeSyncService is still running (used on app reopen) ──
private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) return true
    }
    return false
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun buildSubjectList(data: List<CollegeAttendanceRecord>): List<String> =
    listOf("All") + data.map { it.subject }.distinct().sorted()

/**
 * Loads all attendance records from Firestore for the current user. Converts them to
 * CollegeAttendanceRecord format for comparison.
 */
suspend fun loadAppAttendanceFromFirestore(): List<CollegeAttendanceRecord> {
    val uid =
        FirebaseAuth.getInstance().currentUser?.uid
            ?: run {
                Log.w("CollegeSync", "No user logged in — can't load app records")
                return emptyList()
            }
    val db = FirebaseFirestore.getInstance()
    val result = mutableListOf<CollegeAttendanceRecord>()
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    try {
        val subjectsSnap = db.collection("users").document(uid).collection("subjects").get().await()

        Log.d("CollegeSync", "Found ${subjectsSnap.documents.size} subjects in Firestore")

        for (subjectDoc in subjectsSnap.documents) {
            val subjectName = subjectDoc.getString("name")
            if (subjectName == null) {
                Log.w("CollegeSync", "Subject ${subjectDoc.id} has no name, skipping")
                continue
            }

            val attendanceSnap = subjectDoc.reference.collection("attendance").get().await()

            Log.d(
                "CollegeSync",
                "Subject '$subjectName': ${attendanceSnap.documents.size} attendance docs"
            )

            for (doc in attendanceSnap.documents) {
                // Handle date — could be Timestamp, Date, or Long
                val dateValue = doc.get("date")
                val formattedDate =
                    when (dateValue) {
                        is com.google.firebase.Timestamp ->
                            dateFormat.format(dateValue.toDate())
                        is java.util.Date -> dateFormat.format(dateValue)
                        is Long -> dateFormat.format(java.util.Date(dateValue))
                        else -> null
                    }

                if (formattedDate == null) {
                    Log.w(
                        "CollegeSync",
                        "  Doc ${doc.id}: unknown date type: ${dateValue?.javaClass?.name}"
                    )
                    continue
                }

                // Handle status — normalize to "Present" or "Absent"
                val rawStatus = doc.getString("status") ?: "Absent"
                val status =
                    if (rawStatus.equals("Present", ignoreCase = true)) "Present" else "Absent"

                // Handle time — could be Timestamp, Date, String, or missing
                val startTime = readFirestoreTime(doc.get("startTime"), timeFormat)
                val endTime = readFirestoreTime(doc.get("endTime"), timeFormat)

                result.add(
                    CollegeAttendanceRecord(
                        subject = subjectName,
                        date = formattedDate,
                        fromTime = startTime,
                        toTime = endTime,
                        topic = doc.getString("note") ?: "",
                        status = status
                    )
                )
            }
        }
        Log.d("CollegeSync", "Total app records loaded: ${result.size}")
    } catch (e: Exception) {
        Log.e("CollegeSync", "Failed to load app records", e)
    }
    return result
}

suspend fun loadAppSubjectMapFromFirestore(): Map<String, String> {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyMap()
    val db = FirebaseFirestore.getInstance()
    val map = mutableMapOf<String, String>()
    try {
        val subjectsSnap = db.collection("users").document(uid).collection("subjects").get().await()
        for (doc in subjectsSnap.documents) {
            val name = doc.getString("name")
            if (name != null) map[name] = doc.id
        }
    } catch (e: Exception) {
        Log.e("CollegeSync", "Failed to load subject map", e)
    }
    return map
}

/** Safely reads a time value from Firestore that could be Timestamp, Date, or String */
private fun readFirestoreTime(value: Any?, timeFormat: SimpleDateFormat): String {
    return when (value) {
        is com.google.firebase.Timestamp -> timeFormat.format(value.toDate())
        is java.util.Date -> timeFormat.format(value)
        is String -> value
        is Long -> timeFormat.format(java.util.Date(value))
        else -> ""
    }
}

// ─── UI Components ───────────────────────────────────────────────────────────

@Composable
fun SegmentedTab(
    selected: Boolean,
    text: String,
    icon: ImageVector,
    badgeCount: Int,
    badgeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (badgeCount > 0) {
                Spacer(Modifier.width(6.dp))
                Surface(color = badgeColor, shape = CircleShape) {
                    Text(
                        badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryChipCard(
    label: String,
    count: Int,
    color: Color,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) color else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (selected) color else Color.Transparent),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) MaterialTheme.colorScheme.surface else color
            )
            Spacer(Modifier.width(8.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color =
                    if (selected) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (selected) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyStateCardFilter(message: String, onClear: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.FilterListOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onClear != null) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onClear) {
                    Text("Clear Filters", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String, icon: ImageVector = Icons.Default.CloudOff) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SummaryChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

@Composable
private fun SubjectDataCard(record: CollegeAttendanceRecord) {
    val isPresent = record.status.equals("Present", ignoreCase = true)
    val statusColor = if (isPresent) Color(0xFF4CAF50) else Color(0xFFEF5350)
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(statusColor))
            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            record.subject,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (record.fromTime.isNotBlank() && record.toTime.isNotBlank())
                                "${record.fromTime} – ${record.toTime}"
                            else record.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.15f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector =
                                    if (isPresent) Icons.Default.Check
                                    else Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = statusColor
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                record.status,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                }

                if (record.topic.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Topic covered",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    AnimatedVisibility(visible = expanded) {
                        Text(
                            record.topic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

val CompareRecordMatchColor = Color(0xFF4CAF50)
val CompareRecordMismatchColor = Color(0xFFEF5350)

data class CompareDisplayItem(
    val scrapedRecord: CollegeAttendanceRecord?,
    val appRecord: CollegeAttendanceRecord?,
    val matchedAppSubjectName: String? = null
) {
    fun hasMismatch() =
        scrapedRecord != null &&
                appRecord != null &&
                !appRecord.status.equals(scrapedRecord.status, ignoreCase = true)
}

@Composable
private fun CompareDataCard(
    item: CompareDisplayItem,
    onAddToApp: (CollegeAttendanceRecord, String?) -> Unit
) {
    val scrapedRecord = item.scrapedRecord
    val appRecord = item.appRecord

    val displaySubject = scrapedRecord?.subject ?: appRecord?.subject ?: ""
    val displayDate = scrapedRecord?.date ?: appRecord?.date ?: ""
    val displayFromTime = scrapedRecord?.fromTime ?: appRecord?.fromTime ?: ""
    val displayToTime = scrapedRecord?.toTime ?: appRecord?.toTime ?: ""

    val hasMismatch = item.hasMismatch()

    val cardColor =
        when {
            scrapedRecord == null ->
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            appRecord == null -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            hasMismatch -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }

    // Mismatch pulsing effect
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by
    infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
        label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border =
            if (hasMismatch)
                BorderStroke(2.dp, MaterialTheme.colorScheme.error.copy(alpha = alpha))
            else null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.03f))
                        .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        displaySubject,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(2.dp))
                    val timeString =
                        if (displayFromTime.isNotBlank() && displayToTime.isNotBlank())
                            "  •  $displayFromTime – $displayToTime"
                        else ""
                    Text(
                        "$displayDate$timeString",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasMismatch) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            "MISMATCH",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Side by side comparison
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // College side
                Column(
                    modifier = Modifier.weight(1f).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "COLLEGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    if (scrapedRecord != null) {
                        val isPresent = scrapedRecord.status.equals("Present", true)
                        StatusBadge(scrapedRecord.status, isPresent)
                    } else {
                        Text(
                            "No Record",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier.fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // App side
                Column(
                    modifier = Modifier.weight(1f).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "MY APP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    if (appRecord != null) {
                        val isPresent = appRecord.status.equals("Present", true)
                        StatusBadge(appRecord.status, isPresent)
                    } else {
                        Text(
                            "No Record",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                if (scrapedRecord != null) {
                                    onAddToApp(scrapedRecord, item.matchedAppSubjectName)
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Add to app", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, present: Boolean) {
    val bg = if (present) CompareRecordMatchColor else CompareRecordMismatchColor
    Surface(shape = CircleShape, color = bg.copy(alpha = 0.15f)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (present) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = bg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = bg,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─── Persistence ─────────────────────────────────────────────────────────────

fun saveScrapedData(context: Context, data: List<CollegeAttendanceRecord>) {
    val array = JSONArray()
    for (r in data) {
        array.put(
            JSONObject().apply {
                put("subject", r.subject)
                put("date", r.date)
                put("fromTime", r.fromTime)
                put("toTime", r.toTime)
                put("topic", r.topic)
                put("status", r.status)
            }
        )
    }
    File(context.filesDir, "scraped_attendance.json").writeText(array.toString())
}

fun loadScrapedData(context: Context): List<CollegeAttendanceRecord> {
    val file = File(context.filesDir, "scraped_attendance.json")
    if (!file.exists()) return emptyList()
    return try {
        val array = JSONArray(file.readText())
        (0 until array.length()).map { i ->
            array.getJSONObject(i).let { o ->
                CollegeAttendanceRecord(
                    subject = o.optString("subject", ""),
                    date = o.optString("date", ""),
                    fromTime = o.optString("fromTime", ""),
                    toTime = o.optString("toTime", ""),
                    topic = o.optString("topic", ""),
                    status = o.optString("status", "")
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun addScrapedRecordToApp(
    context: Context,
    record: CollegeAttendanceRecord,
    matchedAppSubjectName: String?,
    appSubjectDetailsMap: Map<String, String>,
    onSuccess: () -> Unit
) {
    if (matchedAppSubjectName == null) {
        Toast.makeText(
            context,
            "No matching subject found in app for '${record.subject}'",
            Toast.LENGTH_SHORT
        )
            .show()
        return
    }

    val subjectId = appSubjectDetailsMap[matchedAppSubjectName]
    if (subjectId == null) {
        Toast.makeText(context, "Subject ID not found.", Toast.LENGTH_SHORT).show()
        return
    }

    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = FirebaseFirestore.getInstance()

    val dateFormatter = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
    val timeFormatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

    val dateObj =
        try {
            dateFormatter.parse(record.date)
        } catch (e: Exception) {
            null
        }
    val fromTimeObj =
        try {
            timeFormatter.parse(record.fromTime)
        } catch (e: Exception) {
            null
        }
    val toTimeObj =
        try {
            timeFormatter.parse(record.toTime)
        } catch (e: Exception) {
            null
        }

    if (dateObj == null || fromTimeObj == null || toTimeObj == null) {
        Toast.makeText(context, "Invalid date/time format in record.", Toast.LENGTH_SHORT).show()
        return
    }

    val lectureDate = java.util.Calendar.getInstance().apply { time = dateObj }
    val startTime = java.util.Calendar.getInstance().apply { time = fromTimeObj }
    val endTime = java.util.Calendar.getInstance().apply { time = toTimeObj }

    val dateKeyFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    val timeKeyFormatter = java.text.SimpleDateFormat("HHmm", java.util.Locale.getDefault())

    val dateKey = dateKeyFormatter.format(lectureDate.time)
    val startKey = timeKeyFormatter.format(startTime.time)
    val endKey = timeKeyFormatter.format(endTime.time)
    val lectureId = "${dateKey}_${startKey}_${endKey}"

    val dayName =
        when (lectureDate.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> "MONDAY"
            java.util.Calendar.TUESDAY -> "TUESDAY"
            java.util.Calendar.WEDNESDAY -> "WEDNESDAY"
            java.util.Calendar.THURSDAY -> "THURSDAY"
            java.util.Calendar.FRIDAY -> "FRIDAY"
            java.util.Calendar.SATURDAY -> "SATURDAY"
            java.util.Calendar.SUNDAY -> "SUNDAY"
            else -> null
        }

    val startHour = startTime.get(java.util.Calendar.HOUR_OF_DAY)
    val endHour = endTime.get(java.util.Calendar.HOUR_OF_DAY)
    val slotIndex = startHour - 9
    val durationHours = endHour - startHour

    val lectureKey =
        if (dayName != null && slotIndex >= 0 && durationHours > 0) {
            "${dayName}_${slotIndex}_${durationHours}"
        } else null

    val subjectRef = db.collection("users").document(uid).collection("subjects").document(subjectId)
    val attendanceRef = subjectRef.collection("attendance").document(lectureId)
    val isPresent = record.status.equals("Present", ignoreCase = true)
    val finalStatus = if (isPresent) "Present" else "Absent"

    db
        .runTransaction { transaction ->
            val attendanceSnap = transaction.get(attendanceRef)
            if (attendanceSnap.exists()) {
                throw Exception("Attendance already marked for this lecture in app")
            }

            val subjectSnap = transaction.get(subjectRef)
            val totalClasses = (subjectSnap.getLong("totalClasses") ?: 0) + 1
            val attendedClasses =
                if (isPresent) (subjectSnap.getLong("attendedClasses") ?: 0) + 1
                else subjectSnap.getLong("attendedClasses") ?: 0

            val attendanceData =
                mutableMapOf(
                    "status" to finalStatus,
                    "date" to lectureDate.time,
                    "startTime" to startTime.time,
                    "endTime" to endTime.time,
                    "createdAt" to java.util.Date()
                )
            if (record.topic.isNotBlank()) {
                attendanceData["note"] = "Synced from College Portal: " + record.topic
            } else {
                attendanceData["note"] = "Synced from College Portal"
            }

            lectureKey?.let { attendanceData["lectureKey"] = it }

            transaction.set(attendanceRef, attendanceData)
            transaction.update(
                subjectRef,
                mapOf("totalClasses" to totalClasses, "attendedClasses" to attendedClasses)
            )
        }
        .addOnSuccessListener { onSuccess() }
        .addOnFailureListener { e ->
            Toast.makeText(context, e.message ?: "Failed to add to app", Toast.LENGTH_SHORT)
                .show()
        }
}

// ─── 3D Premium Loader ───────────────────────────────────────────────────────

@Composable
fun Cyber3DDataLoader() {
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_loader_infinite")

    // Smooth time for mathematical rotations
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "time_rot"
    )

    // Core breathing effect
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = EaseInOutSine), RepeatMode.Reverse
        ), label = "core_pulse"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = size.width * 0.35f

                // --- Ambient Glow ---
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF06B6D4).copy(alpha = 0.15f * corePulse), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = radius * 2f
                    )
                )

                // --- 3D Rotating Wireframe Rings ---
                // Draw 3 primary intersecting rings representing dimensional scanning
                val rings = 3
                for (r in 0 until rings) {
                    val angleOffset = r * (180f / rings)
                    val currentRot = time + angleOffset

                    val path = androidx.compose.ui.graphics.Path()
                    val segments = 40

                    for (i in 0..segments) {
                        val angle = Math.toRadians((i * 360.0) / segments)

                        // Create circle in 3D
                        val x3d = kotlin.math.cos(angle) * radius
                        val y3d = kotlin.math.sin(angle) * radius
                        val z3d = 0.0

                        // Rotate on X axis mapping to the current time rotation
                        val thX = Math.toRadians((currentRot).toDouble())
                        val y2 = y3d * kotlin.math.cos(thX) - z3d * kotlin.math.sin(thX)
                        val z2 = y3d * kotlin.math.sin(thX) + z3d * kotlin.math.cos(thX)

                        // Rotate on Y axis to stagger the rings
                        val thY = Math.toRadians(angleOffset.toDouble() + (time*0.5))
                        val x3 = x3d * kotlin.math.cos(thY) + z2 * kotlin.math.sin(thY)
                        val y3 = y2
                        val z3 = -x3d * kotlin.math.sin(thY) + z2 * kotlin.math.cos(thY)

                        // Project onto 2D
                        val scale = 600.0 / (600.0 + z3) // simple perspective
                        val px = cx + (x3 * scale).toFloat()
                        val py = cy + (y3 * scale).toFloat()

                        if (i == 0) path.moveTo(px, py)
                        else path.lineTo(px, py)
                    }

                    // Style the rings alternating colors
                    val ringColor = if (r % 2 == 0) Color(0xFF06B6D4) else Color(0xFFA78BFA)
                    drawPath(
                        path = path,
                        color = ringColor.copy(alpha = 0.5f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.dp.toPx()
                        )
                    )

                    // Add spinning data particles along the rings
                    val pAngle = Math.toRadians(time.toDouble() * (r+1))
                    val pX3d = kotlin.math.cos(pAngle) * radius
                    val pY3d = kotlin.math.sin(pAngle) * radius
                    // Apply same rotation projection...
                    val pThX = Math.toRadians((currentRot).toDouble())
                    val pY2 = pY3d * kotlin.math.cos(pThX)
                    val pZ2 = pY3d * kotlin.math.sin(pThX)
                    val pThY = Math.toRadians(angleOffset.toDouble() + (time*0.5))
                    val pX3 = pX3d * kotlin.math.cos(pThY) + pZ2 * kotlin.math.sin(pThY)
                    val pY3 = pY2
                    val pZ3 = -pX3d * kotlin.math.sin(pThY) + pZ2 * kotlin.math.cos(pThY)
                    val pScale = 600.0 / (600.0 + pZ3)
                    val dpX = cx + (pX3 * pScale).toFloat()
                    val dpY = cy + (pY3 * pScale).toFloat()

                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx() * pScale.toFloat(),
                        center = Offset(dpX, dpY)
                    )
                }

                // --- Core Energy Sphere ---
                drawCircle(
                    color = Color(0xFFA78BFA).copy(alpha = 0.8f),
                    radius = 12.dp.toPx() * corePulse,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx() * corePulse,
                    center = Offset(cx, cy)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Digital Loading Text
        Text(
            text = "DECRYPTING LOGS...",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF06B6D4),
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Synchronizing local and cloud states",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
            letterSpacing = 1.sp
        )
    }
}

// ─── SHARED SCRAPER SCRIPTS ──────────────────────────────────────────────────

object ScraperScripts {

    fun buildLoginScript(safeEmail: String, safePassword: String): String =
        """
    (async function() {
        try {
            Android.onProgressUpdate('Looking for login fields...');

            function sleep(ms) { return new Promise(function(r) { setTimeout(r, ms); }); }

            // Simulate typing character by character — React picks up InputEvent
            function simulateTyping(input, text) {
                input.focus();
                input.value = '';
                input.dispatchEvent(new Event('focus', { bubbles: true }));
                
                // Set value via native setter to bypass React's controlled input
                var nativeSetter = Object.getOwnPropertyDescriptor(
                    window.HTMLInputElement.prototype, 'value'
                ).set;
                nativeSetter.call(input, text);
                
                // Dispatch the events React actually listens to
                input.dispatchEvent(new InputEvent('input', {
                    bubbles: true, cancelable: true, inputType: 'insertText', data: text
                }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                input.dispatchEvent(new Event('blur', { bubbles: true }));
            }

            // Poll for login form fields
            var waited = 0;
            while (waited < 20000) {
                var emailInput = document.querySelector("input[type='email']");
                var passInput  = document.querySelector("input[type='password']");
                var submitBtn  = document.querySelector("button[type='submit']");

                if (emailInput && passInput && submitBtn && emailInput.offsetParent !== null) {
                    Android.onProgressUpdate('Filling credentials...');
                    
                    simulateTyping(emailInput, '$safeEmail');
                    await sleep(300);
                    simulateTyping(passInput, '$safePassword');
                    await sleep(500);
                    
                    Android.onProgressUpdate('Clicking login button...');
                    submitBtn.click();

                    // Wait for URL to change (login redirect)
                    var urlWait = 0;
                    while (urlWait < 20000) {
                        if (!window.location.href.includes('/users/login')) {
                            Android.onLoginSuccess();
                            return;
                        }
                        // Also check for error messages on page
                        var errorEl = document.querySelector('.error, .alert-danger, [role="alert"]');
                        if (errorEl && errorEl.innerText && errorEl.innerText.trim().length > 0) {
                            Android.onError('Login failed: ' + errorEl.innerText.trim());
                            return;
                        }
                        await sleep(500);
                        urlWait += 500;
                    }
                    Android.onError('Login timed out after 20s — check your credentials.');
                    return;
                }
                await sleep(500);
                waited += 500;
            }
            Android.onError('Login form not found within 20 seconds.');
        } catch (e) {
            Android.onError('Login error: ' + (e.message || String(e)));
        }
    })();
    """.trimIndent()

    fun buildScrapingScript(): String = """
    (async function() {
        try {
            Android.onProgressUpdate('Setting up search parameters...');
            function sleep(ms) { return new Promise(function(r) { setTimeout(r, ms); }); }

            async function findLabel(text, maxWait) {
                maxWait = maxWait || 15000;
                var t = 0;
                while (t < maxWait) {
                    var labels = document.querySelectorAll('label');
                    for (var i = 0; i < labels.length; i++) {
                        if (labels[i].innerText && labels[i].innerText.toLowerCase().includes(text.toLowerCase())) {
                            return labels[i];
                        }
                    }
                    await sleep(500);
                    t += 500;
                }
                return null;
            }

            async function selectDropdown(labelText, optionText) {
                Android.onProgressUpdate('Setting ' + labelText + ' → ' + optionText);
                var label = await findLabel(labelText);
                if (!label) throw new Error('Label not found: ' + labelText);

                label.scrollIntoView({ block: 'center' });
                await sleep(500);

                var box = label.nextElementSibling.querySelector('.dropdown-selected-option')
                          || label.nextElementSibling;

                var target = null;
                var retry = 0;
                
                while(retry < 5) {
                    box.click();
                    await sleep(1500); 

                    var lowerOption = optionText.toLowerCase().trim();
                    var allEls = document.querySelectorAll('*');
                    var visibleMatches = [];

                    for (var k = 0; k < allEls.length; k++) {
                        var el = allEls[k];
                        if (el.offsetHeight > 0 && el.innerText) {
                            var elText = el.innerText.trim().toLowerCase();
                            if (elText === lowerOption && el.children.length === 0) {
                                visibleMatches.push(el);
                            }
                        }
                    }

                    if (visibleMatches.length > 0) {
                        target = visibleMatches[visibleMatches.length - 1];
                        break;
                    }

                    Android.onProgressUpdate('Retrying exact match for ' + optionText + ' (' + (retry+1) + '/5)');
                    box.click(); 
                    await sleep(1000);
                    retry++;
                }

                if (!target) throw new Error('Option not found or not visible: ' + optionText);
                Android.onProgressUpdate('Selected: ' + target.innerText.trim());
                target.click();
                await sleep(1000);
            }

            // --- 1. Setup Parameters ---
            await findLabel('Select Course', 15000);
            await selectDropdown('Select Course',   'Msc Cs');
            await selectDropdown('Select Batch',    'MSC CS BATCH 2022-2027');
            await selectDropdown('Select Division', 'MSC CS BATCH 2022-2027 Div-2');
            await selectDropdown('Select Semester', 'Sem8');

            // --- 2. Extract Available Subjects ---
            Android.onProgressUpdate('Getting subjects...');
            var subjectLabel = await findLabel('Select Subjects');
            if (!subjectLabel) throw new Error('Subject dropdown label not found');

            var subjectBox = subjectLabel.nextElementSibling.querySelector('.dropdown-selected-option') || subjectLabel.nextElementSibling;
            subjectBox.click();
            await sleep(1000);

            var rawText = subjectLabel.nextElementSibling.innerText;
            var allSubjects = rawText.split('\n')
                .map(function(s) { return s.trim(); })
                .filter(function(s) { return s && s.toLowerCase() !== 'none' && !s.toLowerCase().includes('select'); });

            subjectBox.click(); 
            await sleep(1000);

            Android.onProgressUpdate('Found ' + allSubjects.length + ' subjects to scrape');
            var masterData = [];

            // Reusable Safe Go Back (Now explicitly pauses the script)
            async function goBackSafely() {
                var btns = document.querySelectorAll('button');
                for (var g = 0; g < btns.length; g++) {
                    if (btns[g].innerText && btns[g].innerText.includes('Go Back')) {
                        btns[g].click(); break;
                    }
                }
                
                var backWait = 0;
                while(backWait < 15000) {
                    await sleep(1000);
                    var isFormVisible = Array.from(document.querySelectorAll('button')).some(b => b.innerText && b.innerText.includes('View Attendance'));
                    if (isFormVisible) break;
                    backWait += 1000;
                }
                await sleep(1000); // Extra buffer for React rendering
            }

            // --- 3. Scrape Each Subject ---
            for (var si = 0; si < allSubjects.length; si++) {
                var subject = allSubjects[si];
                if (subject.toLowerCase().includes('web')) {
                    Android.onProgressUpdate('Skipping: ' + subject);
                    continue;
                }

                Android.onProgressUpdate('Processing: ' + subject + ' (' + (si + 1) + '/' + allSubjects.length + ')');
                await selectDropdown('Select Subjects', subject);

                var viewBtn = null;
                var allBtns = document.querySelectorAll('button');
                for (var b = 0; b < allBtns.length; b++) {
                    if (allBtns[b].innerText && allBtns[b].innerText.includes('View Attendance')) {
                        viewBtn = allBtns[b]; break;
                    }
                }
                if (!viewBtn) continue;
                viewBtn.click();

                var loadWait = 0;
                while (loadWait < 15000) {
                    var loadingEl = Array.from(document.querySelectorAll('*')).find(e => e.innerText && e.innerText.trim() === 'Loading...');
                    if (!loadingEl) break;
                    await sleep(500);
                    loadWait += 500;
                }
                await sleep(1000);

                // Check for empty attendance and securely await transition
                if (document.body.innerText.includes('There is no attendances found for you')) {
                    Android.onProgressUpdate(subject + ': No attendance data, skipping');
                    await goBackSafely();
                    continue;
                }

                var totalEl = Array.from(document.querySelectorAll('*')).find(e => e.innerText && e.innerText.includes('Total Attendances:'));
                var matchArr = totalEl ? totalEl.innerText.match(/\d+/) : null;
                var expectedTotal = matchArr ? parseInt(matchArr[0]) : 0;

                if (expectedTotal === 0) {
                    await goBackSafely();
                    continue;
                }

                Android.onProgressUpdate(subject + ': Targeting ' + expectedTotal + ' records');
                var recordsScraped = 0;
                var pageNumber = 1;

                while (recordsScraped < expectedTotal) {
                    Android.onProgressUpdate(subject + ' — page ' + pageNumber + ' (' + recordsScraped + '/' + expectedTotal + ')');

                    var rows = document.querySelectorAll('[class*="bg-green"], [class*="bg-red"]');
                    if (rows.length === 0) break;

                    var topRowText = rows[0].innerText;

                    for (var ri = 0; ri < rows.length; ri++) {
                        var row = rows[ri];
                        try {
                            var rowHtml = row.outerHTML.toLowerCase();
                            var rowText = row.innerText;

                            if (rowText.includes('/') && rowText.includes(':')) {
                                var lines = rowText.replace(/\r/g, '').split('\n').map(l => l.trim()).filter(l => l.length > 0);

                                if (lines.length >= 4) {
                                    var isPresent = rowHtml.includes('bg-green') || rowHtml.includes('rgb(34, 197, 94');
                                    var record = {
                                        subject:  subject,
                                        date:     lines[0], 
                                        fromTime: lines[1], 
                                        toTime:   lines[2], 
                                        topic:    lines.slice(3).join(' '), 
                                        status:   isPresent ? 'Present' : 'Absent'
                                    };
                                    masterData.push(record);
                                    recordsScraped++;
                                }
                            }
                        } catch(rowErr) { } 
                    }

                    if (recordsScraped >= expectedTotal) break;

                    var pageBtns = document.querySelectorAll('button');
                    var navBtns = [];
                    for (var nb = 0; nb < pageBtns.length; nb++) {
                        var bText = pageBtns[nb].innerText.toLowerCase().trim();
                        if (bText !== 'log in' && bText !== 'go back' && !bText.includes('view attendance')) {
                            navBtns.push(pageBtns[nb]);
                        }
                    }

                    var nextBtn = navBtns.length > 0 ? navBtns[navBtns.length - 1] : null;

                    if (!nextBtn || nextBtn.disabled || (nextBtn.className && nextBtn.className.includes('opacity-'))) break;

                    nextBtn.click();

                    var pw = 0;
                    var pageLoaded = false;
                    while (pw < 15000) {
                        await sleep(500);
                        var newRows = document.querySelectorAll('[class*="bg-green"], [class*="bg-red"]');
                        if (newRows.length > 0 && newRows[0].innerText !== topRowText) {
                            pageLoaded = true;
                            break;
                        }
                        pw += 500;
                    }
                    if (!pageLoaded) await sleep(2000); 
                    pageNumber++;
                }

                Android.onProgressUpdate(subject + ': Scraped ' + recordsScraped + ' records');
                await goBackSafely();
            }

            Android.onProgressUpdate('Scraping complete! Extracted ' + masterData.length + ' total records.');
            Android.onDataExtracted(JSON.stringify(masterData));

        } catch (err) {
            Android.onError(err.message || String(err));
        }
    })();
    """.trimIndent()
}