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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.theme.AttendMateTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

// ─── Activity ────────────────────────────────────────────────────────────────

class CollegeSyncActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AttendMateTheme { CollegeSyncScreen(onBack = { finish() }) } }
    }
}

// ─── State machine ───────────────────────────────────────────────────────────

private enum class ScrapePhase { IDLE, LOGIN, SCRAPING }

// ─── JS bridge (proper class so @JavascriptInterface is visible) ─────────────

private class ScraperBridge(
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

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CollegeSyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("CollegeSyncPrefs", Context.MODE_PRIVATE)

    var email by remember { mutableStateOf(prefs.getString("email", "") ?: "") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }

    val phaseState = remember { mutableStateOf(ScrapePhase.IDLE) }

    var statusText by remember { mutableStateOf("Ready to sync") }
    var scrapedData by remember { mutableStateOf<List<CollegeAttendanceRecord>>(emptyList()) }
    var appData by remember { mutableStateOf<List<CollegeAttendanceRecord>>(emptyList()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showLoginSheet by remember { mutableStateOf(false) }

    // Tab state:  0 = Subject Data,  1 = Compare Data
    var selectedTab by remember { mutableIntStateOf(0) }

    // Subject filter
    var subjects by remember { mutableStateOf<List<String>>(listOf("All")) }
    var selectedSubject by remember { mutableStateOf("All") }

    val coroutineScope = rememberCoroutineScope()
    val isScraping: Boolean = phaseState.value != ScrapePhase.IDLE

    // Load persisted scraped data + app data from Firestore on start
    LaunchedEffect(Unit) {
        val saved = loadScrapedData(context)
        scrapedData = saved
        subjects = buildSubjectList(saved)
        // Load app data from Firestore
        appData = loadAppAttendanceFromFirestore()
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

    val filteredData: List<CollegeAttendanceRecord> = remember(scrapedData, selectedSubject) {
        if (selectedSubject == "All") scrapedData
        else scrapedData.filter { it.subject == selectedSubject }
    }

    val displayItems = remember(filteredData, appData, selectedSubject) {
        val list = mutableListOf<CompareDisplayItem>()
        val matchedAppRecords = mutableSetOf<CollegeAttendanceRecord>()
        
        // Build a strict 1-to-1 mapping from App Subject to the BEST Scraped Subject
        val allScrapedSubjects = scrapedData.map { it.subject }.distinct()
        val appToScrapedSubjectMap = appData.map { it.subject }.distinct().associateWith { appSubj ->
            val s1 = appSubj.lowercase().replace(Regex("[^a-z0-9]"), "")
            
            // Exact alphanumeric match first
            var bestMatch = allScrapedSubjects.find { scrapedSubj ->
                val s2 = scrapedSubj.lowercase().replace(Regex("[^a-z0-9]"), "")
                s1 == s2
            }
            
            // If no exact match, find the longest containing match
            if (bestMatch == null) {
                bestMatch = allScrapedSubjects.filter { scrapedSubj ->
                    val s2 = scrapedSubj.lowercase().replace(Regex("[^a-z0-9]"), "")
                    s1.contains(s2) || s2.contains(s1)
                }.maxByOrNull { it.length } 
            }
            
            bestMatch ?: appSubj // Fallback
        }
        
        for (scraped in filteredData) {
            // Pass 1: Match Subject, Date, AND Status
            var matchedApp = appData.find { app ->
                app !in matchedAppRecords &&
                appToScrapedSubjectMap[app.subject] == scraped.subject &&
                app.date == scraped.date &&
                app.status.equals(scraped.status, ignoreCase = true)
            }
            
            // Pass 2: Subject and Date match (status differs)
            if (matchedApp == null) {
                matchedApp = appData.find { app ->
                    app !in matchedAppRecords &&
                    appToScrapedSubjectMap[app.subject] == scraped.subject &&
                    app.date == scraped.date 
                }
            }
            
            if (matchedApp != null) {
                matchedAppRecords.add(matchedApp)
            }
            list.add(CompareDisplayItem(scraped, matchedApp))
        }
        
        val unmatchedAppRecords = appData.filter { it !in matchedAppRecords }
        val relevantUnmatched = unmatchedAppRecords.filter { app ->
            if (selectedSubject == "All") true else {
                appToScrapedSubjectMap[app.subject] == selectedSubject
            }
        }
        
        for (app in relevantUnmatched) {
            list.add(CompareDisplayItem(null, app))
        }
        
        list.sortedByDescending { 
            val dateStr = it.scrapedRecord?.date ?: it.appRecord?.date ?: ""
            try { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).parse(dateStr)?.time ?: 0L } catch(e: Exception) { 0L }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("College Sync", fontWeight = FontWeight.Bold)
                        Text(
                            "${scrapedData.size} records synced",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isScraping) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        OutlinedButton(
                            onClick = { showLoginSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sync", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Header ───────────────────────────────────────────────
                item {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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

                // ── Status ───────────────────────────────────────────────
                if (statusText != "Ready to sync" || isScraping) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isScraping)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isScraping) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // ── Data Section (only when there is data) ───────────────
                if (scrapedData.isNotEmpty()) {

                    // Tab Row
                    item {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("📚 Subject Data") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("🔍 Compare Data") }
                            )
                        }
                    }

                    // Subject filter chips
                    item {
                        Text(
                            "Filter by Subject",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(subjects) { subj ->
                                FilterChip(
                                    selected = selectedSubject == subj,
                                    onClick = { selectedSubject = subj },
                                    label = { Text(subj) }
                                )
                            }
                        }
                    }

                    // Record count
                    item {
                        Text(
                            "${filteredData.size} records",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    when (selectedTab) {
                        // ── Tab 0: Subject Data ──────────────────────────
                        0 -> {
                            if (filteredData.isEmpty()) {
                                item {
                                    EmptyStateCard("No records for this subject.")
                                }
                            } else {
                                items(filteredData) { record ->
                                    SubjectDataCard(record)
                                }
                            }
                        }
                        // ── Tab 1: Compare Data ──────────────────────────
                        1 -> {
                            item {
                                val matched = displayItems.count { it.scrapedRecord != null && it.appRecord != null && it.scrapedRecord.status.equals(it.appRecord.status, ignoreCase = true) }
                                val mismatched = displayItems.count { it.scrapedRecord != null && it.appRecord != null && !it.scrapedRecord.status.equals(it.appRecord.status, ignoreCase = true) }
                                val missingInApp = displayItems.count { it.appRecord == null }
                                val missingInCollege = displayItems.count { it.scrapedRecord == null }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        SummaryChip("✅ Match", matched, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                        SummaryChip("⚠️ Mismatch", mismatched, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        SummaryChip("Missing App", missingInApp, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                                        SummaryChip("Missing College", missingInCollege, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                                    }
                                }
                            }

                            if (displayItems.isEmpty()) {
                                item { EmptyStateCard("No records to compare.") }
                            } else {
                                items(displayItems.size) { index ->
                                    CompareDataCard(displayItems[index])
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
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
                                                val list = mutableListOf<CollegeAttendanceRecord>()
                                                val arr = JSONArray(json)
                                                if (arr.length() == 0) {
                                                    Log.d("CollegeSync", "Ignoring empty extraction from premature SPA load")
                                                    return@launch
                                                }
                                                for (i in 0 until arr.length()) {
                                                    val o = arr.getJSONObject(i)
                                                    list.add(
                                                        CollegeAttendanceRecord(
                                                            subject = o.optString("subject", ""),
                                                            date = o.optString("date", ""),
                                                            fromTime = o.optString("fromTime", ""),
                                                            toTime = o.optString("toTime", ""),
                                                            topic = o.optString("topic", ""),
                                                            status = o.optString("status", "")
                                                        )
                                                    )
                                                }
                                                scrapedData = list
                                                saveScrapedData(context, list)
                                                statusText = "Sync complete — ${list.size} records."
                                                Log.d("CollegeSync", "Saved ${list.size} records")
                                                phaseState.value = ScrapePhase.IDLE
                                                Toast.makeText(
                                                    context, "Sync finished!", Toast.LENGTH_SHORT
                                                ).show()
                                            } catch (ex: Exception) {
                                                Log.e("CollegeSync", "Parse error", ex)
                                                statusText = "Parse error: ${ex.message}"
                                                phaseState.value = ScrapePhase.IDLE
                                            }
                                        }
                                    },
                                    loginSuccessCb = {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            statusText = "Logged in! Loading attendance page..."
                                            Log.d("CollegeSync", "Login success — navigating to attendance")
                                            phaseState.value = ScrapePhase.SCRAPING
                                            webViewRef?.loadUrl(
                                                "https://attendence-system-1910.vercel.app/students/current/attendances"
                                            )
                                        }
                                    }
                                ),
                                "Android"
                            )

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    super.onPageFinished(view, url)
                                    val phase = phaseState.value
                                    Log.d("CollegeSync", "onPageFinished url=$url phase=$phase")

                                    when {
                                        // ── LOGIN phase + login page → fill form ──
                                        phase == ScrapePhase.LOGIN &&
                                            url.contains("/users/login") -> {
                                            statusText = "Filling login form..."
                                            val safeEmail = email
                                                .replace("\\", "\\\\")
                                                .replace("'", "\\'")
                                            val safePassword = password
                                                .replace("\\", "\\\\")
                                                .replace("'", "\\'")
                                            val js = buildLoginScript(safeEmail, safePassword)
                                            Log.d("CollegeSync", "Injecting login script")
                                            view.evaluateJavascript(js, null)
                                        }

                                        // ── LOGIN phase + redirected away → login success
                                        phase == ScrapePhase.LOGIN &&
                                            !url.contains("/users/login") -> {
                                            Log.d("CollegeSync", "Login redirect detected: $url")
                                            coroutineScope.launch(Dispatchers.Main) {
                                                statusText = "Logged in! Loading attendance page..."
                                                phaseState.value = ScrapePhase.SCRAPING
                                                view.loadUrl(
                                                    "https://attendence-system-1910.vercel.app/students/current/attendances"
                                                )
                                            }
                                        }

                                        // ── SCRAPING phase + attendance page → scrape ──
                                        phase == ScrapePhase.SCRAPING &&
                                            url.contains("/students/current/attendances") -> {
                                            statusText = "Extracting data..."
                                            Log.d("CollegeSync", "Injecting scraping script")
                                            view.evaluateJavascript(buildScrapingScript(), null)
                                        }

                                        // ── SCRAPING phase + bounced to login → expired ──
                                        phase == ScrapePhase.SCRAPING &&
                                            url.contains("/users/login") -> {
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
                    }
                )
            }
        }
    }

    // ── Login Dialog ──────────────────────────────────────────────
    if (showLoginSheet) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showLoginSheet = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Sync Credentials",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enter your college portal login.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("College Email") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = "Toggle password visibility")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showLoginSheet = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                showLoginSheet = false
                                prefs.edit()
                                    .putString("email", email)
                                    .putString("password", password)
                                    .apply()
                                phaseState.value = ScrapePhase.LOGIN
                                statusText = "Starting login..."
                                Log.d("CollegeSync", "Sync started from dialog")
                                webViewRef?.loadUrl(
                                    "https://attendence-system-1910.vercel.app/users/login"
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = email.isNotEmpty() && password.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Start Sync", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

// ─── Login JS ────────────────────────────────────────────────────────────────
// Simulates keystrokes like Python's send_keys() by dispatching InputEvent
// with inputType='insertText' which React's onChange handler listens to.
// This is the KEY difference vs just setting .value + firing Event('input').

private fun buildLoginScript(safeEmail: String, safePassword: String): String = """
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

// ─── Scraping JS ─────────────────────────────────────────────────────────────

private fun buildScrapingScript(): String = """
(async function() {
    try {
        Android.onProgressUpdate('Setting up search parameters...');
        function sleep(ms) { return new Promise(function(r) { setTimeout(r, ms); }); }

        function findLabel(text, maxWait) {
            maxWait = maxWait || 15000;
            return new Promise(async function(resolve) {
                var t = 0;
                while (t < maxWait) {
                    var labels = document.querySelectorAll('label');
                    for (var i = 0; i < labels.length; i++) {
                        if (labels[i].innerText && labels[i].innerText.toLowerCase().includes(text.toLowerCase())) {
                            resolve(labels[i]);
                            return;
                        }
                    }
                    await sleep(500);
                    t += 500;
                }
                resolve(null);
            });
        }

        async function selectDropdown(labelText, optionText) {
            Android.onProgressUpdate('Setting ' + labelText + ' → ' + optionText);
            var label = await findLabel(labelText);
            if (!label) throw new Error('Label not found: ' + labelText);

            label.scrollIntoView({ block: 'center' });
            await sleep(500);

            var box = label.nextElementSibling.querySelector('.dropdown-selected-option')
                      || label.nextElementSibling;
            box.click();
            await sleep(1500);

            var lower = optionText.toLowerCase().trim();
            var allEls = document.querySelectorAll('*');
            var exactMatch = null;
            var partialMatches = [];
            for (var k = 0; k < allEls.length; k++) {
                var el = allEls[k];
                if (el.offsetHeight > 0 && el.innerText) {
                    var elText = el.innerText.trim().toLowerCase();
                    // Prefer exact match (prevents "Deep Learning" matching "Deep Learning Practical")
                    if (elText === lower && el.children.length === 0) {
                        exactMatch = el;
                    } else if (elText.includes(lower)) {
                        partialMatches.push(el);
                    }
                }
            }
            var target = exactMatch || (partialMatches.length > 0 ? partialMatches[partialMatches.length - 1] : null);
            if (!target) throw new Error('Option not visible: ' + optionText);
            Android.onProgressUpdate('Selected: ' + target.innerText.trim());
            target.click();
            await sleep(1000);
        }

        // Wait for page to fully render
        await findLabel('Select Course', 15000);

        await selectDropdown('Select Course',   'Msc Cs');
        await selectDropdown('Select Batch',    'MSC CS BATCH 2022-2027');
        await selectDropdown('Select Division', 'MSC CS BATCH 2022-2027 Div-2');
        await selectDropdown('Select Semester', 'Sem8');

        // Get all subjects
        Android.onProgressUpdate('Getting subjects...');
        var subjectLabel = await findLabel('Select Subjects');
        if (!subjectLabel) throw new Error('Subject dropdown label not found');

        var subjectBox = subjectLabel.nextElementSibling.querySelector('.dropdown-selected-option')
                         || subjectLabel.nextElementSibling;
        subjectBox.click();
        await sleep(1000);

        var rawText = subjectLabel.nextElementSibling.innerText;
        var allSubjects = rawText.split('\n')
            .map(function(s) { return s.trim(); })
            .filter(function(s) { return s && s.toLowerCase() !== 'none' && !s.toLowerCase().includes('select'); });

        subjectBox.click();
        await sleep(1000);

        Android.onProgressUpdate('Found ' + allSubjects.length + ' subjects');
        var masterData = [];

        for (var si = 0; si < allSubjects.length; si++) {
            var subject = allSubjects[si];
            if (subject.toLowerCase().includes('web')) {
                Android.onProgressUpdate('Skipping: ' + subject);
                continue;
            }

            Android.onProgressUpdate('Processing: ' + subject + ' (' + (si + 1) + '/' + allSubjects.length + ')');
            await selectDropdown('Select Subjects', subject);

            // Click "View Attendance"
            var viewBtn = null;
            var allBtns = document.querySelectorAll('button');
            for (var b = 0; b < allBtns.length; b++) {
                if (allBtns[b].innerText && allBtns[b].innerText.includes('View Attendance')) {
                    viewBtn = allBtns[b];
                    break;
                }
            }
            if (!viewBtn) continue;
            viewBtn.click();

            // Wait for loading to finish
            var loadWait = 0;
            while (loadWait < 10000) {
                var loadingEl = null;
                var checkEls = document.querySelectorAll('*');
                for (var c = 0; c < checkEls.length; c++) {
                    if (checkEls[c].innerText && checkEls[c].innerText.trim() === 'Loading...') {
                        loadingEl = checkEls[c]; break;
                    }
                }
                if (!loadingEl) break;
                await sleep(500);
                loadWait += 500;
            }
            await sleep(1000);

            // Check if empty
            var pageText = document.body.innerText;
            if (pageText.includes('There is no attendances found for you')) {
                Android.onProgressUpdate(subject + ': No attendance data, skipping');
                var goBackBtn1 = null;
                var btns1 = document.querySelectorAll('button');
                for (var g1 = 0; g1 < btns1.length; g1++) {
                    if (btns1[g1].innerText && btns1[g1].innerText.includes('Go Back')) {
                        goBackBtn1 = btns1[g1]; break;
                    }
                }
                if (goBackBtn1) goBackBtn1.click();
                await sleep(1500);
                continue;
            }

            // Get expected total
            var totalEl = null;
            var searchEls = document.querySelectorAll('*');
            for (var t = 0; t < searchEls.length; t++) {
                if (searchEls[t].innerText && searchEls[t].innerText.includes('Total Attendances:')) {
                    totalEl = searchEls[t]; break;
                }
            }
            var matchArr = totalEl ? totalEl.innerText.match(/\d+/) : null;
            var expectedTotal = matchArr ? parseInt(matchArr[0]) : 0;

            if (expectedTotal === 0) {
                var goBackBtn2 = null;
                var btns2 = document.querySelectorAll('button');
                for (var g2 = 0; g2 < btns2.length; g2++) {
                    if (btns2[g2].innerText && btns2[g2].innerText.includes('Go Back')) {
                        goBackBtn2 = btns2[g2]; break;
                    }
                }
                if (goBackBtn2) goBackBtn2.click();
                await sleep(1500);
                continue;
            }

            Android.onProgressUpdate(subject + ': Expecting ' + expectedTotal + ' records');

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
                        if (rowText.indexOf('/') >= 0 && rowText.indexOf(':') >= 0) {
                            // Use regex to extract date and times reliably
                            var dateMatch = rowText.match(/(\d{1,2}\/\d{1,2}\/\d{2,4})/);
                            var timeMatches = rowText.match(/(\d{1,2}:\d{2})/g);
                            
                            if (dateMatch && timeMatches && timeMatches.length >= 2) {
                                var extractedDate = dateMatch[1];
                                var extractedFromTime = timeMatches[0];
                                var extractedToTime = timeMatches[1];
                                
                                // Everything after the times is the topic
                                var lines = rowText.replace(/\r/g, '').split('\n')
                                    .map(function(l) { return l.trim(); })
                                    .filter(function(l) { return l.length > 0; });
                                var topicParts = [];
                                for (var lp = 0; lp < lines.length; lp++) {
                                    var line = lines[lp];
                                    // Skip date and time lines
                                    if (line === extractedDate) continue;
                                    if (line === extractedFromTime) continue;
                                    if (line === extractedToTime) continue;
                                    topicParts.push(line);
                                }
                                
                                var isPresent = rowHtml.indexOf('bg-green') >= 0 ||
                                                rowHtml.indexOf('rgb(34, 197, 94') >= 0 ||
                                                rowText.toLowerCase().indexOf('present') >= 0;
                                var record = {
                                    subject:  subject,
                                    date:     extractedDate,
                                    fromTime: extractedFromTime,
                                    toTime:   extractedToTime,
                                    topic:    topicParts.join(' '),
                                    status:   isPresent ? 'Present' : 'Absent'
                                };
                                Android.onProgressUpdate(subject + ': ' + extractedDate + ' ' + extractedFromTime + '-' + extractedToTime + ' = ' + record.status);
                                masterData.push(record);
                                recordsScraped++;
                            }
                        }
                    } catch(rowErr) {
                        // Skip stale elements
                    }
                }

                if (recordsScraped >= expectedTotal) break;

                // Click next page
                var targetNextPage = (pageNumber + 1).toString();
                var nextBtn = null;
                var pageBtns = document.querySelectorAll('button');
                for (var nb = 0; nb < pageBtns.length; nb++) {
                    if (pageBtns[nb].innerText && pageBtns[nb].innerText.trim() === targetNextPage) {
                        nextBtn = pageBtns[nb];
                        break;
                    }
                }
                if (!nextBtn || nextBtn.disabled || (nextBtn.className && nextBtn.className.indexOf('opacity-') >= 0)) break;
                nextBtn.click();

                // Wait for page content to change
                var pw = 0;
                var pageLoaded = false;
                while (pw < 15000) {
                    await sleep(1000);
                    var newRows = document.querySelectorAll('[class*="bg-green"], [class*="bg-red"]');
                    if (newRows.length > 0 && newRows[0].innerText !== topRowText) {
                        pageLoaded = true;
                        break;
                    }
                    pw += 1000;
                }
                if (!pageLoaded) {
                    await sleep(4000); // 4s fallback wait if dom text check failed
                }
                pageNumber++;
            }

            Android.onProgressUpdate(subject + ': Scraped ' + recordsScraped + ' records');

            // Go back to subject selection
            var goBackBtn3 = null;
            var btns3 = document.querySelectorAll('button');
            for (var g3 = 0; g3 < btns3.length; g3++) {
                if (btns3[g3].innerText && btns3[g3].innerText.includes('Go Back')) {
                    goBackBtn3 = btns3[g3]; break;
                }
            }
            if (goBackBtn3) goBackBtn3.click();
            await sleep(2000);
        }

        Android.onProgressUpdate('Scraping complete! Extracted ' + masterData.length + ' total records.');
        Android.onDataExtracted(JSON.stringify(masterData));

    } catch (err) {
        Android.onError(err.message || String(err));
    }
})();
""".trimIndent()

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun buildSubjectList(data: List<CollegeAttendanceRecord>): List<String> =
    listOf("All") + data.map { it.subject }.distinct().sorted()

/**
 * Loads all attendance records from Firestore for the current user.
 * Converts them to CollegeAttendanceRecord format for comparison.
 */
suspend fun loadAppAttendanceFromFirestore(): List<CollegeAttendanceRecord> {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
        Log.w("CollegeSync", "No user logged in — can't load app records")
        return emptyList()
    }
    val db = FirebaseFirestore.getInstance()
    val result = mutableListOf<CollegeAttendanceRecord>()
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    try {
        val subjectsSnap = db.collection("users")
            .document(uid)
            .collection("subjects")
            .get()
            .await()

        Log.d("CollegeSync", "Found ${subjectsSnap.documents.size} subjects in Firestore")

        for (subjectDoc in subjectsSnap.documents) {
            val subjectName = subjectDoc.getString("name")
            if (subjectName == null) {
                Log.w("CollegeSync", "Subject ${subjectDoc.id} has no name, skipping")
                continue
            }

            val attendanceSnap = subjectDoc.reference
                .collection("attendance")
                .get()
                .await()

            Log.d("CollegeSync", "Subject '$subjectName': ${attendanceSnap.documents.size} attendance docs")

            for (doc in attendanceSnap.documents) {
                // Handle date — could be Timestamp, Date, or Long
                val dateValue = doc.get("date")
                val formattedDate = when (dateValue) {
                    is com.google.firebase.Timestamp -> dateFormat.format(dateValue.toDate())
                    is java.util.Date -> dateFormat.format(dateValue)
                    is Long -> dateFormat.format(java.util.Date(dateValue))
                    else -> null
                }
                
                if (formattedDate == null) {
                    Log.w("CollegeSync", "  Doc ${doc.id}: unknown date type: ${dateValue?.javaClass?.name}")
                    continue
                }

                // Handle status — normalize to "Present" or "Absent"
                val rawStatus = doc.getString("status") ?: "Absent"
                val status = if (rawStatus.equals("Present", ignoreCase = true)) "Present" else "Absent"

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
private fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Text(
            message,
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.subject,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${record.date}  •  ${record.fromTime} – ${record.toTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.topic.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        record.topic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPresent) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = statusColor
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        record.status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }
        }
    }
}

data class CompareDisplayItem(
    val scrapedRecord: CollegeAttendanceRecord?,
    val appRecord: CollegeAttendanceRecord?
)

@Composable
private fun CompareDataCard(item: CompareDisplayItem) {
    val scrapedRecord = item.scrapedRecord
    val appRecord = item.appRecord
    
    val displaySubject = scrapedRecord?.subject ?: appRecord?.subject ?: ""
    val displayDate = scrapedRecord?.date ?: appRecord?.date ?: ""
    val displayFromTime = scrapedRecord?.fromTime ?: appRecord?.fromTime ?: ""
    val displayToTime = scrapedRecord?.toTime ?: appRecord?.toTime ?: ""

    val hasMismatch = scrapedRecord != null && appRecord != null && 
        !appRecord.status.equals(scrapedRecord.status, ignoreCase = true)

    val cardColor = when {
        scrapedRecord == null -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        appRecord == null -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        hasMismatch -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    displaySubject,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (hasMismatch) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "MISMATCH",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                if (scrapedRecord == null) {
                    Text("APP ONLY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                } else if (appRecord == null) {
                    Text("COLLEGE ONLY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(Modifier.height(4.dp))
            val timeString = if (displayFromTime.isNotBlank() && displayToTime.isNotBlank()) "  •  $displayFromTime – $displayToTime" else ""
            Text(
                "$displayDate$timeString",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (scrapedRecord != null) {
                    StatusBadge("College: ${scrapedRecord.status}", scrapedRecord.status.equals("Present", true))
                } else {
                    StatusBadge("College: No data", false)
                }
                
                if (appRecord != null) {
                    StatusBadge("App: ${appRecord.status}", appRecord.status.equals("Present", true))
                } else {
                    StatusBadge("App: No data", false)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, present: Boolean) {
    val bg = if (present) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer
    val fg = if (present) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onErrorContainer
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
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
