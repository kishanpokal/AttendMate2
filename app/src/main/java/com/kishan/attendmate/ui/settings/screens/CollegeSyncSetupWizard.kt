package com.kishan.attendmate.ui.settings

import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kishan.attendmate.ui.components.PrimaryButton
import com.kishan.attendmate.ui.components.SecondaryButton
import com.kishan.attendmate.ui.settings.ScraperScripts
import com.kishan.attendmate.ui.settings.CollegeSyncPreferences
import com.kishan.attendmate.ui.settings.ScrapePhase
import com.kishan.attendmate.ui.settings.ScraperBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

enum class SetupStep {
    SEMESTER,
    FETCHING,
    SUBJECTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollegeSyncSetupWizard(onSetupComplete: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val syncPrefs = remember { CollegeSyncPreferences(context) }
    val portalPrefs = context.getSharedPreferences("CollegeSyncPrefs", Context.MODE_PRIVATE)
    
    val email = portalPrefs.getString("email", "") ?: ""
    val password = portalPrefs.getString("password", "") ?: ""

    var emailInput by remember { mutableStateOf(email) }
    var passwordInput by remember { mutableStateOf(password) }

    var currentStep by remember { mutableStateOf(SetupStep.SEMESTER) }
    var selectedSemester by remember { mutableStateOf(syncPrefs.selectedSemester ?: "Sem9") }
    var fetchedSubjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSubjects by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fetchStatus by remember { mutableStateOf("Logging in to fetch subjects...") }

    val coroutineScope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var phaseState by remember { mutableStateOf(ScrapePhase.IDLE) }
    
    val semesters = (1..10).map { "Sem${it}" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup Sync") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = currentStep,
                label = "setup_step_anim"
            ) { step ->
                when (step) {
                    SetupStep.SEMESTER -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Step 1: Enter Credentials",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Please enter your portal credentials. We need this to fetch the list of available subjects.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Portal Email") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Portal Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = "Step 2: Select Your Semester",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                text = "To speed up syncing and avoid tracking unrelated electives, we need to know your current semester.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(semesters) { sem ->
                                    val isSelected = sem == selectedSemester
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable { selectedSemester = sem }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = sem,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            PrimaryButton(
                                text = "Next",
                                onClick = {
                                    if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                        portalPrefs.edit()
                                            .putString("email", emailInput)
                                            .putString("password", passwordInput)
                                            .apply()
                                        currentStep = SetupStep.FETCHING
                                        phaseState = ScrapePhase.LOGIN
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = emailInput.isNotBlank() && passwordInput.isNotBlank()
                            )
                        }
                    }

                    SetupStep.FETCHING -> {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // WebView Container (75% height)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.75f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (phaseState != ScrapePhase.IDLE) {
                                    AndroidView(
                                        factory = { ctx ->
                                            WebView(ctx).apply {
                                                settings.javaScriptEnabled = true
                                                settings.domStorageEnabled = true
                                                settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                                                addJavascriptInterface(
                                                    ScraperBridge(
                                                        progressCb = { msg ->
                                                            coroutineScope.launch(Dispatchers.Main) { fetchStatus = msg }
                                                        },
                                                        errorCb = { err ->
                                                            coroutineScope.launch(Dispatchers.Main) {
                                                                fetchStatus = "Error: $err"
                                                                phaseState = ScrapePhase.IDLE
                                                                currentStep = SetupStep.SEMESTER
                                                            }
                                                        },
                                                        dataCb = { },
                                                        loginSuccessCb = {
                                                            coroutineScope.launch(Dispatchers.Main) {
                                                                fetchStatus = "Logged in! Loading attendance page..."
                                                                phaseState = ScrapePhase.FETCH_SUBJECTS
                                                                webViewRef?.loadUrl("https://attendence-system-1910.vercel.app/students/current/attendances")
                                                            }
                                                        },
                                                        subjectsCb = { json ->
                                                            coroutineScope.launch(Dispatchers.Main) {
                                                                try {
                                                                    val array = JSONArray(json)
                                                                    val parsed = mutableListOf<String>()
                                                                    for (i in 0 until array.length()) {
                                                                        parsed.add(array.getString(i))
                                                                    }
                                                                    fetchedSubjects = parsed
                                                                    selectedSubjects = parsed.toSet() // select all by default
                                                                    phaseState = ScrapePhase.IDLE
                                                                    currentStep = SetupStep.SUBJECTS
                                                                 } catch (e: Exception) {
                                                                    fetchStatus = "Failed to parse subjects: $e"
                                                                }
                                                            }
                                                        }
                                                    ),
                                                    "Android"
                                                )
                                                webChromeClient = object : WebChromeClient() {
                                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                                        android.util.Log.d("CollegeSyncJS", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                                                        return true
                                                    }
                                                }

                                                webViewClient = object : WebViewClient() {
                                                    private var lastHandledUrl = ""
                                                    
                                                    private fun handleUrlChange(view: WebView, url: String) {
                                                        if (url == lastHandledUrl) return
                                                        lastHandledUrl = url
                                                        
                                                        val phase = phaseState
                                                        android.util.Log.d("CollegeSync", "handleUrlChange: phase=$phase, url=$url")
                                                        
                                                        // Evaluate script based on actual page content check to avoid multiple injections or dead scripts
                                                        view.evaluateJavascript(
                                                            "(function() { return document.body ? document.body.innerText.substring(0, 200) : ''; })()"
                                                        ) { pageText ->
                                                            val text = pageText?.replace("\"", "") ?: ""
                                                            android.util.Log.d("CollegeSync", "handleUrlChange page text: ${text.take(80)}")
                                                            
                                                            if (text.contains("Your Attendances") || text.contains("Select Course") || text.contains("Select Subject") || text.contains("Attendance System") || text.contains("Select Subject For Attendance")) {
                                                                if (phaseState == ScrapePhase.LOGIN || phaseState == ScrapePhase.LOGIN_INJECTED || phaseState == ScrapePhase.FETCH_SUBJECTS) {
                                                                    coroutineScope.launch(Dispatchers.Main) {
                                                                        fetchStatus = "Logged in! Extracting subjects..."
                                                                        phaseState = ScrapePhase.EXTRACTING
                                                                        view.evaluateJavascript(ScraperScripts.buildSubjectFetchScript(selectedSemester), null)
                                                                    }
                                                                }
                                                            } else if (text.contains("Log In") && text.contains("Email") && phaseState == ScrapePhase.LOGIN) {
                                                                coroutineScope.launch(Dispatchers.Main) {
                                                                    fetchStatus = "Filling login form..."
                                                                    phaseState = ScrapePhase.LOGIN_INJECTED
                                                                    val safeEmail = emailInput.replace("\\", "\\\\").replace("'", "\\'")
                                                                    val safePassword = passwordInput.replace("\\", "\\\\").replace("'", "\\'")
                                                                    view.evaluateJavascript(ScraperScripts.buildLoginScript(safeEmail, safePassword), null)
                                                                }
                                                            }
                                                        }
                                                    }

                                                    override fun onPageFinished(view: WebView, url: String) {
                                                        super.onPageFinished(view, url)
                                                        handleUrlChange(view, url)
                                                    }

                                                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                                        super.doUpdateVisitedHistory(view, url, isReload)
                                                        handleUrlChange(view, url)
                                                    }
                                                    
                                                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                                                        val url = request.url.toString()
                                                        android.util.Log.d("CollegeSync", "shouldOverrideUrlLoading: url=$url")
                                                        coroutineScope.launch(Dispatchers.Main) {
                                                            kotlinx.coroutines.delay(3000)
                                                            handleUrlChange(view, url)
                                                        }
                                                        return false
                                                    }
                                                }
                                            }.also { webViewRef = it }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            // Status and controls (25% height)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.25f)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = fetchStatus,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                SecondaryButton(
                                    text = "Cancel",
                                    onClick = {
                                        phaseState = ScrapePhase.IDLE
                                        currentStep = SetupStep.SEMESTER
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Trigger the initial load — go to attendance page directly!
                        LaunchedEffect(webViewRef) {
                            webViewRef?.loadUrl("https://attendence-system-1910.vercel.app/students/current/attendances")
                            
                            kotlinx.coroutines.delay(8000)
                            
                            while (phaseState != ScrapePhase.IDLE && phaseState != ScrapePhase.EXTRACTING) {
                                webViewRef?.let { wv ->
                                    val currentUrl = wv.url ?: ""
                                    android.util.Log.d("CollegeSync", "URL Poller: phase=$phaseState, url=$currentUrl")
                                    
                                    wv.evaluateJavascript(
                                        "(function() { return document.body ? document.body.innerText.substring(0, 200) : ''; })()"
                                    ) { pageText ->
                                        val text = pageText?.replace("\"", "") ?: ""
                                        android.util.Log.d("CollegeSync", "URL Poller page text: ${text.take(80)}")
                                        
                                        if (text.contains("Your Attendances") || text.contains("Select Course") || text.contains("Select Subject") || text.contains("Attendance System") || text.contains("Select Subject For Attendance")) {
                                            if (phaseState == ScrapePhase.LOGIN || phaseState == ScrapePhase.LOGIN_INJECTED || phaseState == ScrapePhase.FETCH_SUBJECTS) {
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    fetchStatus = "Session detected! Extracting subjects..."
                                                    phaseState = ScrapePhase.EXTRACTING
                                                    kotlinx.coroutines.delay(1000)
                                                    wv.evaluateJavascript(ScraperScripts.buildSubjectFetchScript(selectedSemester), null)
                                                }
                                            }
                                        } else if (text.contains("Log In") && text.contains("Email") && phaseState == ScrapePhase.LOGIN) {
                                            coroutineScope.launch(Dispatchers.Main) {
                                                fetchStatus = "Filling login form..."
                                                phaseState = ScrapePhase.LOGIN_INJECTED
                                                val safeEmail = emailInput.replace("\\", "\\\\").replace("'", "\\'")
                                                val safePassword = passwordInput.replace("\\", "\\\\").replace("'", "\\'")
                                                wv.evaluateJavascript(ScraperScripts.buildLoginScript(safeEmail, safePassword), null)
                                            }
                                        }
                                    }
                                }
                                kotlinx.coroutines.delay(5000)
                            }
                        }
                    }

                    SetupStep.SUBJECTS -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Step 2: Select Your Subjects",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Uncheck any electives or subjects you are not enrolled in. This will dramatically speed up the syncing process.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(fetchedSubjects) { subj ->
                                    val isSelected = selectedSubjects.contains(subj)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable { 
                                                selectedSubjects = if (isSelected) selectedSubjects - subj else selectedSubjects + subj 
                                            }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = subj,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }

                            PrimaryButton(
                                text = "Save & Continue",
                                onClick = {
                                    syncPrefs.selectedSemester = selectedSemester
                                    syncPrefs.targetSubjects = selectedSubjects
                                    syncPrefs.isConfigured = true
                                    onSetupComplete()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedSubjects.isNotEmpty()
                            )
                        }
                    }
                }
            }
        }
    }
}
