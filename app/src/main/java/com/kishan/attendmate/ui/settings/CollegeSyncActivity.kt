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
    FETCH_SUBJECTS,
    SCRAPING,
    EXTRACTING
}

// ─── JS bridge (proper class so @JavascriptInterface is visible) ─────────────

class ScraperBridge(
    private val progressCb: (String) -> Unit,
    private val errorCb: (String) -> Unit,
    private val dataCb: (String) -> Unit,
    private val loginSuccessCb: () -> Unit,
    private val subjectsCb: ((String) -> Unit)? = null
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
    fun onSubjectsFetched(json: String) {
        Log.d("CollegeSync", "JS Subjects extracted, length=${json.length}")
        subjectsCb?.invoke(json)
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

// ── NEW HELPER: Check if CollegeSyncService is still running (used on app reopen) ──
fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) return true
    }
    return false
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

fun buildSubjectList(data: List<CollegeAttendanceRecord>): List<String> =
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
fun readFirestoreTime(value: Any?, timeFormat: SimpleDateFormat): String {
    return when (value) {
        is com.google.firebase.Timestamp -> timeFormat.format(value.toDate())
        is java.util.Date -> timeFormat.format(value)
        is String -> value
        is Long -> timeFormat.format(java.util.Date(value))
        else -> ""
    }
}

// ─── UI Components ───────────────────────────────────────────────────────────







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

// ─── Loader clean ───────────────────────────────────────────────────────────

// ─── SHARED SCRAPER SCRIPTS ──────────────────────────────────────────────────

object ScraperScripts {

    fun buildLoginScript(safeEmail: String, safePassword: String): String =
        """
    (async function() {
        if (window.__loginScriptInjected) return;
        window.__loginScriptInjected = true;
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
                if (document.body && (document.body.innerText.includes("Your Attendances") || document.body.innerText.includes("Your Today's Attendance"))) {
                    Android.onProgressUpdate('Already logged in, skipping login form...');
                    Android.loginSuccess();
                    return;
                }
                
                var passInput  = document.querySelector("input[type='password']");
                var emailInput = document.querySelector("input[type='email']") || document.querySelector("input[placeholder*='@']") || document.querySelector("input[type='text']") || document.querySelector("input");
                var submitBtn  = Array.from(document.querySelectorAll('button')).find(b => b.innerText.toLowerCase().includes('log')) || document.querySelector("button[type='submit']") || document.querySelector("button");

                if (waited % 2000 === 0) {
                    Android.onProgressUpdate('DEBUG: email=' + !!emailInput + ', pass=' + !!passInput + ', btn=' + !!submitBtn);
                }
                if (waited === 2000) {
                    var text = document.body ? document.body.innerText.trim().substring(0, 150) : 'NO BODY';
                    Android.onProgressUpdate('PAGE TEXT: ' + (text || 'EMPTY BODY'));
                }
                if (emailInput && passInput && submitBtn) {
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

    fun buildSubjectFetchScript(semester: String): String = """
    (async function() {
        if (window.__subjectScriptInjected) return;
        window.__subjectScriptInjected = true;
        try {
            Android.onProgressUpdate('Fetching subjects for ' + '$semester' + '...');
            function sleep(ms) { return new Promise(function(r) { setTimeout(r, ms); }); }

            // Step 1: Click "Your Attendances" button if visible
            var waited = 0;
            while (waited < 15000) {
                var buttons = document.querySelectorAll('button, a');
                var attendBtn = null;
                for (var i = 0; i < buttons.length; i++) {
                    var txt = (buttons[i].innerText || '').toLowerCase().trim();
                    if (txt === 'your attendances') {
                        attendBtn = buttons[i];
                        break;
                    }
                }
                if (attendBtn) {
                    Android.onProgressUpdate('Clicking "Your Attendances" button...');
                    attendBtn.click();
                    await sleep(3000);
                    break;
                }
                
                // Maybe we're already on the filter page (check for labels)
                var labels = document.querySelectorAll('label');
                var hasFilter = false;
                for (var j = 0; j < labels.length; j++) {
                    if (labels[j].innerText && labels[j].innerText.toLowerCase().includes('select')) {
                        hasFilter = true;
                        break;
                    }
                }
                if (hasFilter) {
                    Android.onProgressUpdate('Already on filter page...');
                    break;
                }
                
                await sleep(1000);
                waited += 1000;
            }

            // Step 2: Debug - log what we see on the page
            Android.onProgressUpdate('PAGE: ' + (document.body ? document.body.innerText.trim().substring(0, 100).replace(/\n/g, ' ') : 'EMPTY'));

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

            await findLabel('Select Course', 15000);
            await selectDropdown('Select Course',   'Msc Cs');
            await selectDropdown('Select Batch',    'MSC CS BATCH 2022-2027');
            await selectDropdown('Select Division', 'MSC CS BATCH 2022-2027 Div-2');
            await selectDropdown('Select Semester', '$semester');

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

            Android.onSubjectsFetched(JSON.stringify(allSubjects));
        } catch (e) {
            Android.onError('Fetch subjects error: ' + (e.message || String(e)));
        }
    })();
    """.trimIndent()

    fun buildScrapingScript(semester: String, targetSubjects: List<String>): String {
        val subjectsArrayJs = targetSubjects.joinToString(prefix = "['", postfix = "']", separator = "','") { it.replace("'", "\'") }
        return """
    (async function() {
        if (window.__scrapingScriptInjected) return;
        window.__scrapingScriptInjected = true;
        try {
            Android.onProgressUpdate('Setting up search parameters...');
            function sleep(ms) { return new Promise(function(r) { setTimeout(r, ms); }); }

            // Step 1: Click "Your Attendances" button if visible
            var waited = 0;
            while (waited < 15000) {
                var buttons = document.querySelectorAll('button, a');
                var attendBtn = null;
                for (var i = 0; i < buttons.length; i++) {
                    var txt = (buttons[i].innerText || '').toLowerCase().trim();
                    if (txt === 'your attendances') {
                        attendBtn = buttons[i];
                        break;
                    }
                }
                if (attendBtn) {
                    Android.onProgressUpdate('Clicking "Your Attendances" button...');
                    attendBtn.click();
                    await sleep(3000);
                    break;
                }
                
                // Maybe we're already on the filter page (check for labels)
                var labels = document.querySelectorAll('label');
                var hasFilter = false;
                for (var j = 0; j < labels.length; j++) {
                    if (labels[j].innerText && labels[j].innerText.toLowerCase().includes('select')) {
                        hasFilter = true;
                        break;
                    }
                }
                if (hasFilter) {
                    Android.onProgressUpdate('Already on filter page...');
                    break;
                }
                
                await sleep(1000);
                waited += 1000;
            }

            // Step 2: Debug - log what we see on the page
            Android.onProgressUpdate('PAGE: ' + (document.body ? document.body.innerText.trim().substring(0, 100).replace(/\n/g, ' ') : 'EMPTY'));

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
            await selectDropdown('Select Semester', '${semester}');

            // --- 2. Extract Available Subjects ---
            var allSubjects = ${subjectsArrayJs};
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
}