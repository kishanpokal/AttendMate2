package com.kishan.attendmate.ui.attendance

import com.kishan.attendmate.ui.theme.statusColors

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/* ---------------- SAFE TIME PARSER ---------------- */
private fun parseTimeToCalendar(raw: Any?, baseDate: Calendar): Calendar {
    val cal = baseDate.clone() as Calendar
    when (raw) {
        is Timestamp -> cal.timeInMillis = raw.toDate().time
        is String -> {
            val parts = raw.split(":")
            if (parts.size == 2) {
                cal.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 0)
                cal.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
            }
        }
    }
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal
}

/* ---------------- ACTIVITY ---------------- */
class EditAttendanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val subjectId = intent.getStringExtra("subjectId") ?: return finish()
        val attendanceId = intent.getStringExtra("attendanceId") ?: return finish()
        setContent {
            AttendMateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EditAttendanceScreen(
                        subjectId = subjectId,
                        attendanceId = attendanceId,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

/* ---------------- SCREEN ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAttendanceScreen(
    subjectId: String,
    attendanceId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid ?: return

    /* ---------- STATE ---------- */
    var allSubjects by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // id to name
    var selectedSubjectId by remember { mutableStateOf(subjectId) }
    var selectedSubjectName by remember { mutableStateOf("") }
    var lectureDate by remember { mutableStateOf(Calendar.getInstance()) }
    var startTime by remember { mutableStateOf<Calendar?>(null) }
    var endTime by remember { mutableStateOf<Calendar?>(null) }
    var status by remember { mutableStateOf("PRESENT") }
    var oldStatus by remember { mutableStateOf("PRESENT") }
    var oldSubjectId by remember { mutableStateOf(subjectId) }
    var note by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showSubjectDropdown by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    val userRef = db.collection("users").document(userId)

    /* ---------- LOAD DATA ---------- */
    LaunchedEffect(Unit) {
        try {
            // Load all subjects for dropdown
            val subjectsSnap = userRef.collection("subjects").get().await()
            allSubjects = subjectsSnap.documents.map { it.id to (it.getString("name") ?: "") }

            // Load attendance record
            val attendanceSnap = userRef
                .collection("subjects").document(subjectId)
                .collection("attendance").document(attendanceId)
                .get().await()

            val date = (attendanceSnap.getTimestamp("date") ?: Timestamp.now()).toDate()
            lectureDate.timeInMillis = date.time

            startTime = attendanceSnap.get("startTime")
                ?.let { parseTimeToCalendar(it, lectureDate) }
            endTime = attendanceSnap.get("endTime")
                ?.let { parseTimeToCalendar(it, lectureDate) }

            status = (attendanceSnap.getString("status") ?: "ABSENT").uppercase()
            oldStatus = status
            oldSubjectId = subjectId

            note = attendanceSnap.getString("note") ?: ""

            selectedSubjectName = allSubjects.firstOrNull { it.first == subjectId }?.second ?: ""
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to load attendance", Toast.LENGTH_LONG).show()
            onBack()
        } finally {
            isLoading = false
        }
    }

    /* ---------- DERIVED ---------- */
    val durationMinutes = remember(startTime, endTime) {
        if (startTime != null && endTime != null)
            ((endTime!!.timeInMillis - startTime!!.timeInMillis) / 60000).toInt()
        else null
    }

    /* ---------------- SCAFFOLD ---------------- */
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            "Edit Attendance",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "All fields are editable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.shadow(2.dp)
            )
        }
    ) { padding ->

        /* ---------- LOADING ---------- */
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                    Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            /* ═══════ 1. SUBJECT SELECTOR ═══════ */
            SectionCard(
                icon = Icons.Outlined.Book,
                title = "Subject",
                accent = MaterialTheme.colorScheme.primary
            ) {
                ExposedDropdownMenuBox(
                    expanded = showSubjectDropdown,
                    onExpandedChange = { showSubjectDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedSubjectName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Subject") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSubjectDropdown)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showSubjectDropdown,
                        onDismissRequest = { showSubjectDropdown = false }
                    ) {
                        allSubjects.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (id == selectedSubjectId)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.outline
                                                )
                                        )
                                        Text(
                                            name,
                                            fontWeight = if (id == selectedSubjectId)
                                                FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                },
                                onClick = {
                                    selectedSubjectId = id
                                    selectedSubjectName = name
                                    showSubjectDropdown = false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                trailingIcon = if (id == selectedSubjectId) {
                                    {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            /* ═══════ 2. DATE PICKER ═══════ */
            SectionCard(
                icon = Icons.Outlined.CalendarMonth,
                title = "Lecture Date",
                accent = MaterialTheme.colorScheme.secondary
            ) {
                EditableClickCard(
                    icon = Icons.Filled.CalendarMonth,
                    label = "Tap to change date",
                    value = dateFormatter.format(lectureDate.time),
                    accentColor = MaterialTheme.colorScheme.secondary,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val newCal = lectureDate.clone() as Calendar
                                newCal.set(year, month, day)
                                lectureDate = newCal
                                // Re-align start/end times to the new date
                                startTime = startTime?.let {
                                    val c = newCal.clone() as Calendar
                                    c.set(Calendar.HOUR_OF_DAY, it.get(Calendar.HOUR_OF_DAY))
                                    c.set(Calendar.MINUTE, it.get(Calendar.MINUTE))
                                    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                                    c
                                }
                                endTime = endTime?.let {
                                    val c = newCal.clone() as Calendar
                                    c.set(Calendar.HOUR_OF_DAY, it.get(Calendar.HOUR_OF_DAY))
                                    c.set(Calendar.MINUTE, it.get(Calendar.MINUTE))
                                    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                                    c
                                }
                            },
                            lectureDate.get(Calendar.YEAR),
                            lectureDate.get(Calendar.MONTH),
                            lectureDate.get(Calendar.DAY_OF_MONTH)
                        ).also { dialog ->
                            // Prevent future dates
                            dialog.datePicker.maxDate = System.currentTimeMillis()
                        }.show()
                    }
                )
            }

            /* ═══════ 3. TIME ROW ═══════ */
            SectionCard(
                icon = Icons.Outlined.AccessTime,
                title = "Lecture Time",
                accent = MaterialTheme.colorScheme.tertiary
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        EditableClickCard(
                            icon = Icons.Filled.PlayArrow,
                            label = "Start Time",
                            value = startTime?.let { timeFormatter.format(it.time) } ?: "Set time",
                            accentColor = statusColors().success,
                            isSet = startTime != null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val cur = startTime ?: lectureDate
                                TimePickerDialog(
                                    context,
                                    { _, h, min ->
                                        startTime = (lectureDate.clone() as Calendar).apply {
                                            set(Calendar.HOUR_OF_DAY, h)
                                            set(Calendar.MINUTE, min)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                    },
                                    cur.get(Calendar.HOUR_OF_DAY),
                                    cur.get(Calendar.MINUTE),
                                    false
                                ).show()
                            }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        EditableClickCard(
                            icon = Icons.Filled.Stop,
                            label = "End Time",
                            value = endTime?.let { timeFormatter.format(it.time) } ?: "Set time",
                            accentColor = statusColors().warning,
                            isSet = endTime != null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val cur = endTime ?: lectureDate
                                TimePickerDialog(
                                    context,
                                    { _, h, min ->
                                        endTime = (lectureDate.clone() as Calendar).apply {
                                            set(Calendar.HOUR_OF_DAY, h)
                                            set(Calendar.MINUTE, min)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                    },
                                    cur.get(Calendar.HOUR_OF_DAY),
                                    cur.get(Calendar.MINUTE),
                                    false
                                ).show()
                            }
                        )
                    }
                }

                // Duration pill
                AnimatedVisibility(
                    visible = durationMinutes != null && durationMinutes > 0,
                    enter = fadeIn() + expandVertically()
                ) {
                    if (durationMinutes != null && durationMinutes > 0) {
                        val h = durationMinutes / 60
                        val m = durationMinutes % 60
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Timelapse,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        buildString {
                                            if (h > 0) append("${h}h ")
                                            append("${m}min lecture")
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            /* ═══════ 4. STATUS ═══════ */
            SectionCard(
                icon = Icons.Outlined.CheckCircle,
                title = "Attendance Status"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusToggleCard(
                        modifier = Modifier.weight(1f),
                        label = "Present",
                        icon = Icons.Filled.CheckCircle,
                        selected = status == "PRESENT",
                        selectedColor = statusColors().success,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            status = "PRESENT"
                        }
                    )
                    StatusToggleCard(
                        modifier = Modifier.weight(1f),
                        label = "Absent",
                        icon = Icons.Filled.Cancel,
                        selected = status == "ABSENT",
                        selectedColor = statusColors().error,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            status = "ABSENT"
                        }
                    )
                }
            }

            /* ═══════ 5. NOTE ═══════ */
            SectionCard(
                icon = Icons.Outlined.EditNote,
                title = "Note  (optional)"
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 200) note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "e.g., Sick, Festival, Medical leave…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    maxLines = 4,
                    supportingText = {
                        Text(
                            "${note.length}/200",
                            color = if (note.length > 180)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                )
            }

            /* ═══════ 6. UPDATE BUTTON ═══════ */
            SaveButton(
                isSaving = isSaving,
                onClick = {
                    if (startTime == null || endTime == null) {
                        Toast.makeText(context, "Please set start and end time", Toast.LENGTH_SHORT).show()
                        return@SaveButton
                    }
                    if (durationMinutes != null && durationMinutes <= 0) {
                        Toast.makeText(context, "End time must be after start time", Toast.LENGTH_SHORT).show()
                        return@SaveButton
                    }
                    if (isSaving) return@SaveButton
                    isSaving = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    scope.launch {
                        try {
                            val newSubjectRef = userRef.collection("subjects").document(selectedSubjectId)
                            val newAttendanceRef = newSubjectRef.collection("attendance").document(attendanceId)

                            if (selectedSubjectId != oldSubjectId) {
                                // Move attendance record: delete from old subject, add to new
                                val oldSubjectRef = userRef.collection("subjects").document(oldSubjectId)
                                val oldAttendanceRef = oldSubjectRef.collection("attendance").document(attendanceId)

                                val attendanceData = mutableMapOf<String, Any>(
                                    "status" to status,
                                    "date" to Timestamp(lectureDate.time),
                                    "startTime" to Timestamp(startTime!!.time),
                                    "endTime" to Timestamp(endTime!!.time),
                                    "updatedAt" to Timestamp.now()
                                )
                                if (note.isNotBlank()) attendanceData["note"] = note.trim()

                                db.runBatch { batch ->
                                    // Delete from old subject
                                    batch.delete(oldAttendanceRef)

                                    // Adjust old subject count
                                    if (oldStatus == "PRESENT") {
                                        batch.update(oldSubjectRef, "attendedClasses", FieldValue.increment(-1))
                                        batch.update(oldSubjectRef, "totalClasses", FieldValue.increment(-1))
                                    } else {
                                        batch.update(oldSubjectRef, "totalClasses", FieldValue.increment(-1))
                                    }

                                    // Add to new subject
                                    batch.set(newAttendanceRef, attendanceData)

                                    // Adjust new subject count
                                    batch.update(newSubjectRef, "totalClasses", FieldValue.increment(1))
                                    if (status == "PRESENT") {
                                        batch.update(newSubjectRef, "attendedClasses", FieldValue.increment(1))
                                    }
                                }.await()
                            } else {
                                // Same subject — just update the record + adjust counts
                                db.runTransaction { transaction ->
                                    val subjectSnap = transaction.get(newSubjectRef)
                                    var attended = subjectSnap.getLong("attendedClasses") ?: 0L

                                    if (oldStatus != status) {
                                        attended += if (status == "PRESENT") 1L else -1L
                                    }

                                    val updateData = mutableMapOf<String, Any>(
                                        "status" to status,
                                        "date" to Timestamp(lectureDate.time),
                                        "startTime" to Timestamp(startTime!!.time),
                                        "endTime" to Timestamp(endTime!!.time),
                                        "updatedAt" to Timestamp.now()
                                    )

                                    if (note.isNotBlank()) {
                                        updateData["note"] = note.trim()
                                    } else {
                                        updateData["note"] = FieldValue.delete()
                                    }

                                    transaction.update(newAttendanceRef, updateData)
                                    transaction.update(newSubjectRef, "attendedClasses", maxOf(0L, attended))
                                }.await()
                            }

                            Toast.makeText(context, "✓ Attendance updated!", Toast.LENGTH_SHORT).show()
                            onBack()
                        } catch (e: Exception) {
                            isSaving = false
                            Toast.makeText(
                                context,
                                e.message ?: "Update failed. Please try again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ══════════════════════════════════════════════════
   REUSABLE COMPONENTS
══════════════════════════════════════════════════ */

/* --- Section wrapper card --- */
@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.3.sp
                )
            }
            content()
        }
    }
}

/* --- Tappable field card (Date / Time slots) --- */
@Composable
private fun EditableClickCard(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    isSet: Boolean = true,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "card_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSet)
                accentColor.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(
            1.dp,
            if (isSet) accentColor.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSet) accentColor else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSet) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSet) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSet) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/* --- Present / Absent toggle card --- */
@Composable
private fun StatusToggleCard(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "status_scale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(onClick = onClick)
            .shadow(
                elevation = if (selected) 8.dp else 1.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = if (selected) selectedColor.copy(0.35f) else Color.Transparent
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                selectedColor.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = if (selected) BorderStroke(2.dp, selectedColor) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) selectedColor
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) selectedColor else MaterialTheme.colorScheme.onSurface
            )
            AnimatedVisibility(visible = selected) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                )
            }
        }
    }
}

/* --- Save / Update button --- */
@Composable
private fun SaveButton(
    isSaving: Boolean,
    onClick: () -> Unit
) {
    com.kishan.attendmate.ui.components.PrimaryButton(
        text = "Save Changes",
        onClick = onClick,
        isLoading = isSaving,
        icon = {
            Icon(
                Icons.Default.Save,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    )
}