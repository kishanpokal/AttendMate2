package com.kishan.attendmate.ui.attendance

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                EditAttendanceScreen(
                    subjectId = subjectId,
                    attendanceId = attendanceId,
                    onBack = { finish() }
                )
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

            isLoading = false
        } catch (e: Exception) {
            isLoading = false
            Toast.makeText(context, "Failed to load attendance", Toast.LENGTH_LONG).show()
            onBack()
        }
    }

    /* ---------------- UI ---------------- */
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Text(
                        "Edit Attendance",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            /* ---------- HEADER CARD ---------- */
            HeaderCard()

            /* ---------- SUBJECT (LOCKED) ---------- */
            Text(
                text = "Subject Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Subject",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = subjectName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            /* ---------- LECTURE DETAILS ---------- */
            Text(
                text = "Lecture Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            /* ---------- DATE (LOCKED) ---------- */
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Lecture Date",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dateFormatter.format(lectureDate.time),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            /* ---------- TIME ROW ---------- */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                /* ---------- START TIME ---------- */
                Box(modifier = Modifier.weight(1f)) {
                    SelectableCard(
                        icon = Icons.Default.Schedule,
                        label = "Start Time",
                        value = startTime?.let { timeFormatter.format(it.time) } ?: "Select",
                        onClick = {
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
                                true // 24-hour format
                            ).show()
                        }
                    )
                }

                /* ---------- END TIME ---------- */
                Box(modifier = Modifier.weight(1f)) {
                    SelectableCard(
                        icon = Icons.Default.Schedule,
                        label = "End Time",
                        value = endTime?.let { timeFormatter.format(it.time) } ?: "Select",
                        onClick = {
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
                                true // 24-hour format
                            ).show()
                        }
                    )
                }
            }

            /* ---------- STATUS ---------- */
            Text(
                text = "Attendance Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EditStatusCard(
                    text = "Present",
                    icon = Icons.Default.CheckCircle,
                    selected = status == STATUS_PRESENT,
                    selectedColor = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                ) {
                    status = STATUS_PRESENT
                }
                EditStatusCard(
                    text = "Absent",
                    icon = Icons.Default.Cancel,
                    selected = status == STATUS_ABSENT,
                    selectedColor = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                ) {
                    status = STATUS_ABSENT
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            /* ---------- UPDATE ---------- */
            Button(
                enabled = !isSaving && startTime != null && endTime != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                onClick = {
                    if (startTime == null || endTime == null) {
                        Toast.makeText(context, "Please complete all fields", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isSaving) return@Button
                    isSaving = true
                    db.runTransaction { transaction ->
                        val subjectSnap = transaction.get(subjectRef)
                        var attendedClasses = subjectSnap.getLong("attendedClasses") ?: 0L
                        if (oldStatus != status) {
                            attendedClasses += if (status == STATUS_PRESENT) 1 else -1
                        }
                        transaction.update(attendanceRef, mapOf(
                            "status" to status,
                            "startTime" to Timestamp(startTime!!.time),
                            "endTime" to Timestamp(endTime!!.time),
                            "updatedAt" to Timestamp.now()
                        ))
                        transaction.update(subjectRef, mapOf(
                            "attendedClasses" to maxOf(0, attendedClasses)
                        ))
                    }
                        .addOnSuccessListener {
                            isSaving = false
                            Toast.makeText(context, "Attendance updated successfully!", Toast.LENGTH_SHORT).show()
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
            ) {
                if (isSaving) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Updating...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Update Attendance",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/* ---------------- COMPONENTS ---------------- */
@Composable
private fun HeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column {
                Text(
                    text = "Edit Attendance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Update the details below",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

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