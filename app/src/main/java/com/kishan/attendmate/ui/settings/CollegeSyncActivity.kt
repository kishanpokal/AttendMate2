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

    val lectureDate = java.util.Calendar.getInstance().apply {
        time = dateObj
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val startTime = (lectureDate.clone() as java.util.Calendar).apply {
        val temp = java.util.Calendar.getInstance().apply { time = fromTimeObj }
        set(java.util.Calendar.HOUR_OF_DAY, temp.get(java.util.Calendar.HOUR_OF_DAY))
        set(java.util.Calendar.MINUTE, temp.get(java.util.Calendar.MINUTE))
    }
    val endTime = (lectureDate.clone() as java.util.Calendar).apply {
        val temp = java.util.Calendar.getInstance().apply { time = toTimeObj }
        set(java.util.Calendar.HOUR_OF_DAY, temp.get(java.util.Calendar.HOUR_OF_DAY))
        set(java.util.Calendar.MINUTE, temp.get(java.util.Calendar.MINUTE))
    }

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
    val finalStatus = if (isPresent) "PRESENT" else "ABSENT"

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
                mutableMapOf<String, Any>(
                    "status" to finalStatus,
                    "date" to com.google.firebase.Timestamp(lectureDate.time),
                    "startTime" to com.google.firebase.Timestamp(startTime.time),
                    "endTime" to com.google.firebase.Timestamp(endTime.time),
                    "createdAt" to com.google.firebase.Timestamp.now()
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
        try {
            Android.onProgressUpdate('Looking for login fields...');

            function sleep(ms) { return new Promise(function(r) { setTimeout(r, ms); }); }

            function simulateTyping(input, text) {
                input.focus();
                input.value = '';
                input.dispatchEvent(new Event('focus', { bubbles: true }));
                
                var nativeSetter = Object.getOwnPropertyDescriptor(
                    window.HTMLInputElement.prototype, 'value'
                ).set;
                nativeSetter.call(input, text);
                
                input.dispatchEvent(new InputEvent('input', {
                    bubbles: true, cancelable: true, inputType: 'insertText', data: text
                }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                input.dispatchEvent(new Event('blur', { bubbles: true }));
            }

            var waited = 0;
            while (waited < 20000) {
                if (document.body && (document.body.innerText.includes("Your Attendances") || document.body.innerText.includes("Your Today's Attendance") || document.body.innerText.includes("Select Subject For Attendance"))) {
                    Android.onProgressUpdate('Already logged in, proceeding...');
                    Android.onLoginSuccess();
                    return;
                }
                
                var passInput  = document.getElementById('userPassword') || document.querySelector("input[type='password']");
                var emailInput = document.getElementById('userEmail') || document.querySelector("input[type='email']") || document.querySelector("input[placeholder*='@']") || document.querySelector("input[type='text']");
                var submitBtn  = document.querySelector("button[type='submit']") || Array.from(document.querySelectorAll('button')).find(b => (b.innerText || '').toLowerCase().includes('log'));

                if (emailInput && passInput && submitBtn) {
                    Android.onProgressUpdate('Filling credentials...');
                    
                    simulateTyping(emailInput, '$safeEmail');
                    await sleep(300);
                    simulateTyping(passInput, '$safePassword');
                    await sleep(400);
                    
                    Android.onProgressUpdate('Submitting login...');
                    submitBtn.click();

                    var urlWait = 0;
                    while (urlWait < 20000) {
                        if (!window.location.href.includes('/users/login')) {
                            Android.onLoginSuccess();
                            return;
                        }
                        var errorEl = document.querySelector('.input__error-message, .glass--red, [role="alert"]');
                        if (errorEl && errorEl.innerText && errorEl.innerText.trim().length > 0) {
                            Android.onError('Login failed: ' + errorEl.innerText.trim());
                            return;
                        }
                        await sleep(500);
                        urlWait += 500;
                    }
                    Android.onError('Login timed out — please check credentials.');
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
        try {
            Android.onProgressUpdate('Preparing to fetch subjects for ' + '$semester' + '...');
            function sleep(ms) { return new Promise(function(r) { setTimeout(r, ms); }); }

            async function getDropdownContainer(idOrLabel, maxWait) {
                maxWait = maxWait || 20000;
                var t = 0;
                while (t < maxWait) {
                    var el = document.getElementById(idOrLabel);
                    if (el) {
                        var container = el.closest('.input__container') || el.closest('.dropdown') || el.parentElement;
                        if (container) return container;
                    }
                    var labels = document.querySelectorAll('label, .input__label');
                    for (var i = 0; i < labels.length; i++) {
                        var lText = (labels[i].innerText || '').toLowerCase();
                        if (lText.includes(idOrLabel.toLowerCase())) {
                            var parent = labels[i].closest('.input__container') || labels[i].parentElement;
                            if (parent) return parent;
                        }
                    }
                    await sleep(300);
                    t += 300;
                }
                return null;
            }

            async function selectDropdown(idOrLabel, preferredValue) {
                var displayName = preferredValue ? idOrLabel + ' (' + preferredValue + ')' : idOrLabel;
                Android.onProgressUpdate('Selecting ' + displayName + '...');
                
                var container = await getDropdownContainer(idOrLabel, 15000);
                if (!container) throw new Error('Dropdown not found: ' + idOrLabel);

                var waitEnabled = 0;
                while (waitEnabled < 15000) {
                    var dropdownEl = container.querySelector('.dropdown') || container;
                    var isDisabled = dropdownEl.classList.contains('dropdown--disabled') || 
                                     (dropdownEl.innerText && dropdownEl.innerText.includes('Loading...'));
                    if (!isDisabled) break;
                    await sleep(300);
                    waitEnabled += 300;
                }

                var selectedOptionEl = container.querySelector('.dropdown-selected-option');
                if (!selectedOptionEl) throw new Error('Dropdown selected option box not found for ' + idOrLabel);

                var currentSelectedText = (selectedOptionEl.innerText || '').trim();
                
                if (preferredValue && currentSelectedText.toLowerCase() === preferredValue.toLowerCase().trim()) {
                    Android.onProgressUpdate(idOrLabel + ' already set to: ' + currentSelectedText);
                    return currentSelectedText;
                }

                var listOpen = false;
                for (var attempt = 0; attempt < 5; attempt++) {
                    selectedOptionEl.scrollIntoView({ block: 'center' });
                    await sleep(200);
                    selectedOptionEl.click();
                    await sleep(600);

                    var items = container.querySelectorAll('.dropdown-list__item, li');
                    if (items.length > 0) {
                        listOpen = true;
                        break;
                    }
                }

                if (!listOpen) {
                    if (currentSelectedText && currentSelectedText.toLowerCase() !== 'none' && !currentSelectedText.toLowerCase().includes('select')) {
                        return currentSelectedText;
                    }
                    throw new Error('Could not open dropdown list for: ' + idOrLabel);
                }

                var items = Array.from(container.querySelectorAll('.dropdown-list__item, li'))
                    .filter(function(li) {
                        var txt = (li.innerText || '').trim().toLowerCase();
                        return txt && txt !== 'none' && !txt.includes('select');
                    });

                if (items.length === 0) {
                    selectedOptionEl.click();
                    await sleep(300);
                    if (currentSelectedText && currentSelectedText.toLowerCase() !== 'none') {
                        return currentSelectedText;
                    }
                    throw new Error('No available options found for: ' + idOrLabel);
                }

                var chosenItem = null;
                if (preferredValue && preferredValue.trim().length > 0) {
                    var targetLower = preferredValue.toLowerCase().trim();
                    chosenItem = items.find(function(li) {
                        return (li.innerText || '').trim().toLowerCase() === targetLower;
                    });
                    if (!chosenItem) {
                        var cleanTarget = targetLower.replace(/[^a-z0-9]/g, '');
                        chosenItem = items.find(function(li) {
                            var cleanLi = (li.innerText || '').trim().toLowerCase().replace(/[^a-z0-9]/g, '');
                            return cleanLi === cleanTarget;
                        });
                    }
                    if (!chosenItem) {
                        chosenItem = items.find(function(li) {
                            var liTxt = (li.innerText || '').trim().toLowerCase();
                            return liTxt.includes(targetLower) || targetLower.includes(liTxt);
                        });
                    }
                }

                if (!chosenItem) {
                    chosenItem = items[0];
                }

                var chosenText = (chosenItem.innerText || '').trim();
                Android.onProgressUpdate('Selected ' + idOrLabel + ' → ' + chosenText);
                chosenItem.click();
                await sleep(800);
                return chosenText;
            }

            // Step 1: Wait for the filter form to be ready
            var formReadyWait = 0;
            while (formReadyWait < 15000) {
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
                    attendBtn.click();
                    await sleep(2000);
                    break;
                }
                var courseEl = document.getElementById('course') || document.querySelector("label");
                if (courseEl) break;
                await sleep(500);
                formReadyWait += 500;
            }

            // Step 2: Configure Course, Batch, Division, and Semester
            await selectDropdown('course', 'Msc Cs');
            await selectDropdown('batch', '');
            await selectDropdown('division', '');
            await selectDropdown('semester', '$semester');

            // Step 3: Extract all subjects
            Android.onProgressUpdate('Reading subject options for ' + '$semester' + '...');
            var subContainer = await getDropdownContainer('subjects', 15000);
            if (!subContainer) throw new Error('Subjects dropdown container not found');

            var waitSub = 0;
            while (waitSub < 15000) {
                var dropdownEl = subContainer.querySelector('.dropdown') || subContainer;
                var isSubDisabled = dropdownEl.classList.contains('dropdown--disabled') || 
                                    (dropdownEl.innerText && dropdownEl.innerText.includes('Loading...'));
                if (!isSubDisabled) break;
                await sleep(300);
                waitSub += 300;
            }

            var subBox = subContainer.querySelector('.dropdown-selected-option');
            if (!subBox) throw new Error('Subjects dropdown box not found');

            subBox.click();
            await sleep(800);

            var subItems = Array.from(subContainer.querySelectorAll('.dropdown-list__item, li'))
                .map(function(li) { return (li.innerText || '').trim(); })
                .filter(function(txt) {
                    return txt && txt.toLowerCase() !== 'none' && !txt.toLowerCase().includes('select');
                });

            subBox.click();
            await sleep(300);

            if (subItems.length === 0) {
                throw new Error('No subjects found for semester ' + '$semester');
            }

            Android.onProgressUpdate('Found ' + subItems.length + ' subjects: ' + subItems.join(', '));
            Android.onSubjectsFetched(JSON.stringify(subItems));
        } catch (e) {
            Android.onError('Fetch subjects error: ' + (e.message || String(e)));
        }
    })();
    """.trimIndent()

    fun buildScrapingScript(semester: String, targetSubjects: List<String>): String {
        val subjectsArrayJs = targetSubjects.joinToString(prefix = "['", postfix = "']", separator = "','") { it.replace("'", "\\'") }
        return """
    (async function() {
        try {
            Android.onProgressUpdate('Initializing attendance sync...');
            function sleep(ms) { return new Promise(function(r) { setTimeout(r, ms); }); }

            async function getDropdownContainer(idOrLabel, maxWait) {
                maxWait = maxWait || 20000;
                var t = 0;
                while (t < maxWait) {
                    var el = document.getElementById(idOrLabel);
                    if (el) {
                        var container = el.closest('.input__container') || el.closest('.dropdown') || el.parentElement;
                        if (container) return container;
                    }
                    var labels = document.querySelectorAll('label, .input__label');
                    for (var i = 0; i < labels.length; i++) {
                        var lText = (labels[i].innerText || '').toLowerCase();
                        if (lText.includes(idOrLabel.toLowerCase())) {
                            var parent = labels[i].closest('.input__container') || labels[i].parentElement;
                            if (parent) return parent;
                        }
                    }
                    await sleep(300);
                    t += 300;
                }
                return null;
            }

            async function selectDropdown(idOrLabel, preferredValue) {
                var displayName = preferredValue ? idOrLabel + ' (' + preferredValue + ')' : idOrLabel;
                Android.onProgressUpdate('Selecting ' + displayName + '...');
                
                var container = await getDropdownContainer(idOrLabel, 15000);
                if (!container) throw new Error('Dropdown not found: ' + idOrLabel);

                var waitEnabled = 0;
                while (waitEnabled < 15000) {
                    var dropdownEl = container.querySelector('.dropdown') || container;
                    var isDisabled = dropdownEl.classList.contains('dropdown--disabled') || 
                                     (dropdownEl.innerText && dropdownEl.innerText.includes('Loading...'));
                    if (!isDisabled) break;
                    await sleep(300);
                    waitEnabled += 300;
                }

                var selectedOptionEl = container.querySelector('.dropdown-selected-option');
                if (!selectedOptionEl) throw new Error('Dropdown selected option box not found for ' + idOrLabel);

                var currentSelectedText = (selectedOptionEl.innerText || '').trim();
                
                if (preferredValue && currentSelectedText.toLowerCase() === preferredValue.toLowerCase().trim()) {
                    Android.onProgressUpdate(idOrLabel + ' already set to: ' + currentSelectedText);
                    return currentSelectedText;
                }

                var listOpen = false;
                for (var attempt = 0; attempt < 5; attempt++) {
                    selectedOptionEl.scrollIntoView({ block: 'center' });
                    await sleep(200);
                    selectedOptionEl.click();
                    await sleep(600);

                    var items = container.querySelectorAll('.dropdown-list__item, li');
                    if (items.length > 0) {
                        listOpen = true;
                        break;
                    }
                }

                if (!listOpen) {
                    if (currentSelectedText && currentSelectedText.toLowerCase() !== 'none' && !currentSelectedText.toLowerCase().includes('select')) {
                        return currentSelectedText;
                    }
                    throw new Error('Could not open dropdown list for: ' + idOrLabel);
                }

                var items = Array.from(container.querySelectorAll('.dropdown-list__item, li'))
                    .filter(function(li) {
                        var txt = (li.innerText || '').trim().toLowerCase();
                        return txt && txt !== 'none' && !txt.includes('select');
                    });

                if (items.length === 0) {
                    selectedOptionEl.click();
                    await sleep(300);
                    if (currentSelectedText && currentSelectedText.toLowerCase() !== 'none') {
                        return currentSelectedText;
                    }
                    throw new Error('No available options found for: ' + idOrLabel);
                }

                var chosenItem = null;
                if (preferredValue && preferredValue.trim().length > 0) {
                    var targetLower = preferredValue.toLowerCase().trim();
                    chosenItem = items.find(function(li) {
                        return (li.innerText || '').trim().toLowerCase() === targetLower;
                    });
                    if (!chosenItem) {
                        var cleanTarget = targetLower.replace(/[^a-z0-9]/g, '');
                        chosenItem = items.find(function(li) {
                            var cleanLi = (li.innerText || '').trim().toLowerCase().replace(/[^a-z0-9]/g, '');
                            return cleanLi === cleanTarget;
                        });
                    }
                    if (!chosenItem) {
                        chosenItem = items.find(function(li) {
                            var liTxt = (li.innerText || '').trim().toLowerCase();
                            return liTxt.includes(targetLower) || targetLower.includes(liTxt);
                        });
                    }
                }

                if (!chosenItem) {
                    chosenItem = items[0];
                }

                var chosenText = (chosenItem.innerText || '').trim();
                Android.onProgressUpdate('Selected ' + idOrLabel + ' → ' + chosenText);
                chosenItem.click();
                await sleep(800);
                return chosenText;
            }

            // Step 1: Navigate to filter form if needed
            var formReadyWait = 0;
            while (formReadyWait < 15000) {
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
                    attendBtn.click();
                    await sleep(2000);
                    break;
                }
                var courseEl = document.getElementById('course') || document.querySelector("label");
                if (courseEl) break;
                await sleep(500);
                formReadyWait += 500;
            }

            // Step 2: Setup Course, Batch, Division, Semester
            await selectDropdown('course', 'Msc Cs');
            await selectDropdown('batch', '');
            await selectDropdown('division', '');
            await selectDropdown('semester', '${semester}');

            // Step 3: Determine subjects to scrape
            var allSubjects = ${subjectsArrayJs};
            if (!allSubjects || allSubjects.length === 0) {
                var subContainer = await getDropdownContainer('subjects', 15000);
                var subBox = subContainer.querySelector('.dropdown-selected-option');
                subBox.click();
                await sleep(800);
                allSubjects = Array.from(subContainer.querySelectorAll('.dropdown-list__item, li'))
                    .map(function(li) { return (li.innerText || '').trim(); })
                    .filter(function(txt) { return txt && txt.toLowerCase() !== 'none' && !txt.toLowerCase().includes('select'); });
                subBox.click();
                await sleep(300);
            }

            Android.onProgressUpdate('Found ' + allSubjects.length + ' subjects to scrape');
            var masterData = [];

            async function goBackSafely() {
                var btns = document.querySelectorAll('button');
                var backBtn = null;
                for (var g = 0; g < btns.length; g++) {
                    var t = (btns[g].innerText || '').trim().toLowerCase();
                    if (t === 'go back') {
                        backBtn = btns[g];
                        break;
                    }
                }
                if (backBtn) {
                    backBtn.click();
                }
                
                var backWait = 0;
                while (backWait < 15000) {
                    await sleep(400);
                    var isFormVisible = Array.from(document.querySelectorAll('button'))
                        .some(function(b) { return (b.innerText || '').includes('View Attendance'); });
                    if (isFormVisible) break;
                    backWait += 400;
                }
                await sleep(600);
            }

            // Step 4: Scrape each subject
            for (var si = 0; si < allSubjects.length; si++) {
                var subject = allSubjects[si];
                Android.onProgressUpdate('Processing: ' + subject + ' (' + (si + 1) + '/' + allSubjects.length + ')');
                
                await selectDropdown('subjects', subject);
                await sleep(400);

                var viewBtn = null;
                var allBtns = document.querySelectorAll('button');
                for (var b = 0; b < allBtns.length; b++) {
                    if ((allBtns[b].innerText || '').includes('View Attendance')) {
                        viewBtn = allBtns[b];
                        break;
                    }
                }
                if (!viewBtn) {
                    Android.onProgressUpdate('View Attendance button not found for ' + subject);
                    continue;
                }
                viewBtn.click();

                var loadWait = 0;
                while (loadWait < 20000) {
                    var hasLoading = Array.from(document.querySelectorAll('*'))
                        .some(function(e) { return (e.innerText || '').trim() === 'Loading...'; });
                    var hasTotal = document.body && (document.body.innerText.includes('Total Attendances:') || document.body.innerText.includes('There is no attendances found for you'));
                    if (!hasLoading && hasTotal) break;
                    await sleep(400);
                    loadWait += 400;
                }
                await sleep(600);

                if (document.body.innerText.includes('There is no attendances found for you')) {
                    Android.onProgressUpdate(subject + ': No attendance data, skipping');
                    await goBackSafely();
                    continue;
                }

                var totalEl = Array.from(document.querySelectorAll('h3, *'))
                    .find(function(e) { return (e.innerText || '').includes('Total Attendances:'); });
                var matchArr = totalEl ? totalEl.innerText.match(/\d+/) : null;
                var expectedTotal = matchArr ? parseInt(matchArr[0]) : 0;

                if (expectedTotal === 0) {
                    var initialRows = Array.from(document.querySelectorAll('li')).filter(function(li) {
                        var cls = (li.className || '').toLowerCase();
                        var txt = li.innerText || '';
                        return (cls.includes('bg-green') || cls.includes('bg-red') || cls.includes('min-w-max')) && 
                               txt.includes('/') && txt.includes(':');
                    });
                    if (initialRows.length === 0) {
                        Android.onProgressUpdate(subject + ': 0 total attendances');
                        await goBackSafely();
                        continue;
                    } else {
                        expectedTotal = 9999;
                    }
                }

                Android.onProgressUpdate(subject + ': Targeting ' + (expectedTotal === 9999 ? 'all' : expectedTotal) + ' records');
                var recordsScraped = 0;
                var pageNumber = 1;
                var seenKeys = {};

                while (recordsScraped < expectedTotal) {
                    Android.onProgressUpdate(subject + ' — page ' + pageNumber + ' (' + recordsScraped + '/' + (expectedTotal === 9999 ? '?' : expectedTotal) + ')');

                    var rows = Array.from(document.querySelectorAll('li')).filter(function(li) {
                        var cls = (li.className || '').toLowerCase();
                        var txt = li.innerText || '';
                        return (cls.includes('bg-green') || cls.includes('bg-red') || cls.includes('min-w-max')) && 
                               txt.includes('/') && txt.includes(':');
                    });

                    if (rows.length === 0) {
                        Android.onProgressUpdate(subject + ': No rows found on page ' + pageNumber);
                        break;
                    }

                    var topRowKey = rows[0].innerText.trim();

                    for (var ri = 0; ri < rows.length; ri++) {
                        var row = rows[ri];
                        try {
                            var rowHtml = (row.outerHTML || '').toLowerCase();
                            var rowClass = (row.className || '').toLowerCase();
                            var isPresent = rowClass.includes('bg-green') || rowHtml.includes('bg-green') || rowHtml.includes('rgb(34, 197, 94');

                            var spans = Array.from(row.querySelectorAll('span'))
                                .map(function(s) { return s.innerText.trim(); })
                                .filter(function(s) { return s.length > 0; });

                            var recDate = '';
                            var recFrom = '';
                            var recTo = '';
                            var recTopic = '';

                            if (spans.length >= 3) {
                                recDate = spans[0];
                                recFrom = spans[1];
                                recTo = spans[2];
                                recTopic = spans.slice(3).join(' ');
                            } else {
                                var tokens = (row.innerText || '').replace(/\r/g, '').split(/[\n\t]+/).map(function(t){ return t.trim(); }).filter(Boolean);
                                if (tokens.length >= 3) {
                                    recDate = tokens[0];
                                    recFrom = tokens[1];
                                    recTo = tokens[2];
                                    recTopic = tokens.slice(3).join(' ');
                                }
                            }

                            if (recDate.includes('/') && recFrom.includes(':')) {
                                var recordKey = subject + '_' + recDate + '_' + recFrom + '_' + recTo + '_' + recTopic;
                                if (!seenKeys[recordKey]) {
                                    seenKeys[recordKey] = true;
                                    var record = {
                                        subject: subject,
                                        date: recDate,
                                        fromTime: recFrom,
                                        toTime: recTo,
                                        topic: recTopic,
                                        status: isPresent ? 'Present' : 'Absent'
                                    };
                                    masterData.push(record);
                                    recordsScraped++;
                                }
                            }
                        } catch(rowErr) { }
                    }

                    if (recordsScraped >= expectedTotal) break;

                    // Navigate to next page with async batch loading support
                    var targetPage = pageNumber + 1;
                    var pageChanged = false;

                    for (var waitAttempts = 0; waitAttempts < 25; waitAttempts++) {
                        // Check if numbered button for targetPage exists and is clickable
                        var allButtons = Array.from(document.querySelectorAll('button'));
                        var targetPageBtn = allButtons.find(function(b) {
                            return (b.innerText || '').trim() === String(targetPage) && !b.disabled;
                        });

                        if (targetPageBtn) {
                            targetPageBtn.click();
                            pageChanged = true;
                            break;
                        }

                        // Check if Next Page button is enabled
                        var nextBtn = document.querySelector('button[aria-label="btnNextPage"]');
                        var isNextDisabled = !nextBtn || nextBtn.disabled || 
                                             (nextBtn.className && (nextBtn.className.includes('disabled') || nextBtn.className.includes('cursor-not-allowed') || nextBtn.className.includes('opacity-')));

                        if (nextBtn && !isNextDisabled) {
                            nextBtn.click();
                            pageChanged = true;
                            break;
                        }

                        // Next button may be temporarily disabled while React fetches next batch
                        await sleep(400);
                    }

                    if (!pageChanged) {
                        Android.onProgressUpdate(subject + ': Completed all pages (' + recordsScraped + ' records)');
                        break;
                    }

                    // Wait for new page rows to render
                    var rowWait = 0;
                    while (rowWait < 12000) {
                        await sleep(300);
                        var currentRows = Array.from(document.querySelectorAll('li')).filter(function(li) {
                            var cls = (li.className || '').toLowerCase();
                            var txt = li.innerText || '';
                            return (cls.includes('bg-green') || cls.includes('bg-red') || cls.includes('min-w-max')) && 
                                   txt.includes('/') && txt.includes(':');
                        });
                        if (currentRows.length > 0 && currentRows[0].innerText.trim() !== topRowKey) {
                            break;
                        }
                        rowWait += 300;
                    }

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