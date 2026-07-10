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
                                GlassNavScaffold(selectedRoute = "home") { paddingValues ->
                                        HomeScreen(paddingValues)
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

/* -------------------- COMPACT HEADER -------------------- */


/* -------------------- PRO ERROR STATE -------------------- */

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

/* -------------------- ENHANCED EMPTY STATE -------------------- */

/* -------------------- MODERN ATTENDANCE DIALOG -------------------- */


/* -------------------- MODERN ICON -------------------- */

/* -------------------- MODERN ACTION BUTTON -------------------- */

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


/* -------------------- MODERN ATTENDANCE SUMMARY CARD -------------------- */

/* -------------------- MODERN STAT ITEM -------------------- */


fun calculateLecturesNeededFor75Percent(attended: Int, total: Int): Int {
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