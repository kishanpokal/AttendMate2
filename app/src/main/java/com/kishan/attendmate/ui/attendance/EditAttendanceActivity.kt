package com.kishan.attendmate.ui.attendance

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
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/* ---------------- SAFE TIME PARSER ---------------- */
private fun parseTimeToCalendar(
    raw: Any?,
    baseDate: Calendar
): Calendar {
    val cal = baseDate.clone() as Calendar
    when (raw) {
        is Timestamp -> {
            cal.timeInMillis = raw.toDate().time
        }
        is String -> {
            val parts = raw.split(":")
            if (parts.size == 2) {
                cal.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 0)
                cal.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
            }
        }
    }
    // CRITICAL: reset noise
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
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    val userId = auth.currentUser?.uid ?: return

    val STATUS_PRESENT = "PRESENT"
    val STATUS_ABSENT = "ABSENT"

    /* ---------- STATE ---------- */
    var subjectName by remember { mutableStateOf("") }
    var lectureDate by remember { mutableStateOf(Calendar.getInstance()) }
    var startTime by remember { mutableStateOf<Calendar?>(null) }
    var endTime by remember { mutableStateOf<Calendar?>(null) }
    var status by remember { mutableStateOf("Present") }
    var oldStatus by remember { mutableStateOf("Present") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var hasChanges by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val subjectRef = db.collection("users")
        .document(userId)
        .collection("subjects")
        .document(subjectId)

    val attendanceRef = subjectRef
        .collection("attendance")
        .document(attendanceId)

    /* ---------- LOAD DATA FROM FIRESTORE ---------- */
    LaunchedEffect(Unit) {
        try {
            val subjectSnap = subjectRef.get().await()
            subjectName = subjectSnap.getString("name") ?: ""

            val attendanceSnap = attendanceRef.get().await()
            val date = (attendanceSnap.getTimestamp("date") ?: Timestamp.now()).toDate()
            lectureDate.timeInMillis = date.time

            val rawStart = attendanceSnap.get("startTime")
            startTime = if (rawStart != null) parseTimeToCalendar(rawStart, lectureDate) else null

            val rawEnd = attendanceSnap.get("endTime")
            endTime = if (rawEnd != null) parseTimeToCalendar(rawEnd, lectureDate) else null

            val rawStatus = attendanceSnap.getString("status") ?: STATUS_ABSENT
            status = rawStatus.uppercase(Locale.getDefault())
            oldStatus = status

            note = attendanceSnap.getString("note") ?: ""

            isLoading = false
        } catch (e: Exception) {
            isLoading = false
            Toast.makeText(context, "Failed to load attendance", Toast.LENGTH_LONG).show()
            onBack()
        }
    }

    /* ---------- TRACK CHANGES ---------- */
    LaunchedEffect(status, startTime, endTime, note) {
        hasChanges = true
    }

    /* ---------------- UI ---------------- */
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBack()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            "Edit Attendance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Update lecture details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                },
                actions = {
                    if (hasChanges && !isLoading) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    "Modified",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            border = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.shadow(
                    elevation = 2.dp,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Loading attendance data...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                /* ---------- HERO CARD ---------- */
                EditHeroCard()

                /* ---------- SUBJECT (LOCKED) ---------- */
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditSectionHeader(
                            icon = Icons.Outlined.Book,
                            title = "Subject Information",
                            subtitle = "This cannot be changed"
                        )
                        LockedInfoCard(
                            icon = Icons.Filled.Book,
                            label = "Subject",
                            value = subjectName
                        )
                    }
                }

                /* ---------- LECTURE DETAILS ---------- */
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically() + expandVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditSectionHeader(
                            icon = Icons.Outlined.CalendarMonth,
                            title = "Lecture Details",
                            subtitle = "Date is locked, time is editable"
                        )

                        /* ---------- DATE (LOCKED) ---------- */
                        LockedInfoCard(
                            icon = Icons.Filled.CalendarMonth,
                            label = "Lecture Date",
                            value = dateFormatter.format(lectureDate.time)
                        )

                        /* ---------- TIME ROW ---------- */
                        EditTimeSelector(
                            lectureDate = lectureDate,
                            startTime = startTime,
                            endTime = endTime,
                            timeFormatter = timeFormatter,
                            onStartTimeClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val current = startTime ?: lectureDate
                                TimePickerDialog(
                                    context,
                                    { _, h, min ->
                                        startTime = (startTime ?: lectureDate.clone() as Calendar).apply {
                                            set(Calendar.HOUR_OF_DAY, h)
                                            set(Calendar.MINUTE, min)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                    },
                                    current.get(Calendar.HOUR_OF_DAY),
                                    current.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            onEndTimeClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val current = endTime ?: lectureDate
                                TimePickerDialog(
                                    context,
                                    { _, h, min ->
                                        endTime = (endTime ?: lectureDate.clone() as Calendar).apply {
                                            set(Calendar.HOUR_OF_DAY, h)
                                            set(Calendar.MINUTE, min)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                    },
                                    current.get(Calendar.HOUR_OF_DAY),
                                    current.get(Calendar.MINUTE),
                                    true
                                ).show()
                            }
                        )
                    }
                }

                /* ---------- STATUS SECTION ---------- */
                AnimatedVisibility(
                    visible = startTime != null && endTime != null,
                    enter = fadeIn() + slideInVertically() + expandVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditSectionHeader(
                            icon = Icons.Outlined.CheckCircle,
                            title = "Attendance Status",
                            subtitle = "Update your presence status"
                        )

                        EditStatusSelector(
                            status = status,
                            onStatusChange = { newStatus ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                status = newStatus
                            }
                        )

                        /* ---------- NOTE FIELD ---------- */
                        EditNoteField(
                            note = note,
                            onNoteChange = { if (it.length <= 200) note = it },
                            status = status
                        )
                    }
                }

                /* ---------- UPDATE BUTTON ---------- */
                AnimatedVisibility(
                    visible = startTime != null && endTime != null,
                    enter = fadeIn() + slideInVertically() + expandVertically()
                ) {
                    EditUpdateButton(
                        enabled = !isSaving,
                        isSaving = isSaving,
                        hasChanges = hasChanges,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (startTime == null || endTime == null) {
                                Toast.makeText(context, "Please complete all fields", Toast.LENGTH_SHORT).show()
                                return@EditUpdateButton
                            }
                            if (isSaving) return@EditUpdateButton
                            isSaving = true

                            db.runTransaction { transaction ->
                                val subjectSnap = transaction.get(subjectRef)
                                var attendedClasses = subjectSnap.getLong("attendedClasses") ?: 0L
                                if (oldStatus != status) {
                                    attendedClasses += if (status == STATUS_PRESENT) 1 else -1
                                }

                                val updateData = mutableMapOf<String, Any>(
                                    "status" to status,
                                    "startTime" to Timestamp(startTime!!.time),
                                    "endTime" to Timestamp(endTime!!.time),
                                    "updatedAt" to Timestamp.now()
                                )

                                if (note.isNotBlank()) {
                                    updateData["note"] = note.trim()
                                } else {
                                    updateData["note"] = com.google.firebase.firestore.FieldValue.delete()
                                }

                                transaction.update(attendanceRef, updateData)
                                transaction.update(subjectRef, mapOf(
                                    "attendedClasses" to maxOf(0, attendedClasses)
                                ))
                            }
                                .addOnSuccessListener {
                                    isSaving = false
                                    Toast.makeText(
                                        context,
                                        "✓ Attendance updated successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onBack()
                                }
                                .addOnFailureListener {
                                    isSaving = false
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Failed to update attendance",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/* ---------------- HERO CARD ---------------- */
@Composable
private fun EditHeroCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.primary
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Edit Attendance",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Make changes to this lecture record",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/* ---------------- SECTION HEADER ---------------- */
@Composable
private fun EditSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

/* ---------------- LOCKED INFO CARD ---------------- */
@Composable
private fun LockedInfoCard(
    icon: ImageVector,
    label: String,
    value: String
) {
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Locked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/* ---------------- EDIT TIME SELECTOR ---------------- */
@Composable
private fun EditTimeSelector(
    lectureDate: Calendar,
    startTime: Calendar?,
    endTime: Calendar?,
    timeFormatter: SimpleDateFormat,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            /* ---------- START TIME ---------- */
            Box(modifier = Modifier.weight(1f)) {
                EditableTimeCard(
                    icon = Icons.Filled.AccessTime,
                    label = "Start Time",
                    value = startTime?.let { timeFormatter.format(it.time) } ?: "Select",
                    isSelected = startTime != null,
                    onClick = onStartTimeClick
                )
            }

            /* ---------- END TIME ---------- */
            Box(modifier = Modifier.weight(1f)) {
                EditableTimeCard(
                    icon = Icons.Filled.AccessTime,
                    label = "End Time",
                    value = endTime?.let { timeFormatter.format(it.time) } ?: "Select",
                    isSelected = endTime != null,
                    onClick = onEndTimeClick
                )
            }
        }

        /* ---------- DURATION DISPLAY ---------- */
        AnimatedVisibility(
            visible = startTime != null && endTime != null,
            enter = fadeIn() + expandVertically()
        ) {
            if (startTime != null && endTime != null) {
                val durationMinutes = (endTime.timeInMillis - startTime.timeInMillis) / (1000 * 60)
                val hours = durationMinutes / 60
                val minutes = durationMinutes % 60

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Timelapse,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                "Lecture Duration",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 11.sp
                            )
                            Text(
                                "${if (hours > 0) "${hours}h " else ""}${minutes}min",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- EDITABLE TIME CARD ---------------- */
@Composable
private fun EditableTimeCard(
    icon: ImageVector,
    label: String,
    value: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.98f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "time_card_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = if (isSelected) 4.dp else 2.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = if (isSelected) BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/* ---------------- EDIT STATUS SELECTOR ---------------- */
@Composable
private fun EditStatusSelector(
    status: String,
    onStatusChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            EnhancedEditStatusCard(
                text = "Present",
                icon = Icons.Filled.CheckCircle,
                selected = status == "PRESENT",
                selectedColor = Color(0xFF34C759),
                onClick = { onStatusChange("PRESENT") }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            EnhancedEditStatusCard(
                text = "Absent",
                icon = Icons.Filled.Cancel,
                selected = status == "ABSENT",
                selectedColor = Color(0xFFFF3B30),
                onClick = { onStatusChange("ABSENT") }
            )
        }
    }
}

/* ---------------- ENHANCED EDIT STATUS CARD ---------------- */
@Composable
private fun EnhancedEditStatusCard(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "edit_status_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.7f,
        label = "edit_status_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .shadow(
                elevation = if (selected) 12.dp else 2.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (selected) selectedColor.copy(alpha = 0.4f) else Color.Transparent
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                selectedColor.copy(alpha = 0.12f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = if (selected) BorderStroke(2.dp, selectedColor) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected)
                            selectedColor
                        else
                            MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) selectedColor else MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )

            if (selected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                )
            }
        }
    }
}

/* ---------------- EDIT NOTE FIELD ---------------- */
@Composable
private fun EditNoteField(
    note: String,
    onNoteChange: (String) -> Unit,
    status: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Reason for Absence (Optional)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "e.g., Sick, Festival, Personal work...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                maxLines = 3,
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
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.error,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

/* ---------------- EDIT UPDATE BUTTON ---------------- */
@Composable
private fun EditUpdateButton(
    enabled: Boolean,
    isSaving: Boolean,
    hasChanges: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled && !isSaving) 1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "update_button_scale"
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Change indicator
        if (hasChanges && !isSaving) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "You have unsaved changes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Button(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .scale(scale)
                .shadow(
                    elevation = if (enabled && !isSaving) 12.dp else 4.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (enabled && !isSaving) {
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.tertiary,
                                    MaterialTheme.colorScheme.primary
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Updating...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Update Attendance",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/* ---------------- LEGACY COMPONENTS (for backwards compatibility) ---------------- */
@Composable
private fun SelectableCard(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    val isSelected = value != "Select"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EditStatusCard(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = ""
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable { onClick() }
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = selectedColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) selectedColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) selectedColor else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) selectedColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}