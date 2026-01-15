package com.kishan.attendmate.ui.attendance

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.theme.AttendMateTheme
import java.text.SimpleDateFormat
import java.util.*

class AddAttendanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AttendMateTheme {
                AddAttendanceScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAttendanceScreen(onBack: () -> Unit) {

    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid ?: run {
        Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
        onBack()
        return
    }

    // Subject states
    var isLoadingSubjects by remember { mutableStateOf(true) }
    var subjects by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var selectedSubjectId by remember { mutableStateOf("") }
    var selectedSubjectName by remember { mutableStateOf("") }

    // Attendance states
    var lectureDate by remember { mutableStateOf(Calendar.getInstance()) }
    var startTime by remember { mutableStateOf<Calendar?>(null) }
    var endTime by remember { mutableStateOf<Calendar?>(null) }
    var status by remember { mutableStateOf("Present") }
    var isSaving by remember { mutableStateOf(false) }

    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val currentTime = Calendar.getInstance()



    /* Load Subjects */
    LaunchedEffect(Unit) {
        db.collection("users")
            .document(userId)
            .collection("subjects")
            .get()
            .addOnSuccessListener { result ->
                subjects = result.documents.mapNotNull {
                    val name = it.getString("name")
                    if (name != null) it.id to name else null
                }
                isLoadingSubjects = false
            }
            .addOnFailureListener {
                isLoadingSubjects = false
                Toast.makeText(context, "Failed to load subjects", Toast.LENGTH_SHORT).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("Add Attendance", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Header Card
            HeaderCard()

            // Subject Section
            Text(
                text = "Subject Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (isLoadingSubjects) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            } else {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
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
                                        Icons.Default.Book,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        "Subject",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        selectedSubjectName.ifEmpty { "Select a subject" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selectedSubjectName.isEmpty())
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        subjects.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedSubjectId = id
                                    selectedSubjectName = name
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Book, null, modifier = Modifier.size(20.dp))
                                }
                            )
                        }
                    }
                }
            }

            // Lecture Details
            Text(
                text = "Lecture Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            SelectableCard(
                icon = Icons.Default.CalendarMonth,
                label = "Lecture Date",
                value = dateFormatter.format(lectureDate.time),
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            lectureDate = Calendar.getInstance().apply {
                                set(year, month, day)
                            }
                        },
                        lectureDate.get(Calendar.YEAR),
                        lectureDate.get(Calendar.MONTH),
                        lectureDate.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    SelectableCard(
                        icon = Icons.Default.Schedule,
                        label = "Start Time",
                        value = startTime?.let { timeFormatter.format(it.time) } ?: "Select",
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    startTime = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, hour)
                                        set(Calendar.MINUTE, minute)
                                    }
                                },
                                currentTime.get(Calendar.HOUR_OF_DAY),
                                currentTime.get(Calendar.MINUTE),
                                false
                            ).show()
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    SelectableCard(
                        icon = Icons.Default.Schedule,
                        label = "End Time",
                        value = endTime?.let { timeFormatter.format(it.time) } ?: "Select",
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    endTime = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, hour)
                                        set(Calendar.MINUTE, minute)
                                    }
                                },
                                currentTime.get(Calendar.HOUR_OF_DAY),
                                currentTime.get(Calendar.MINUTE),
                                false
                            ).show()
                        }
                    )
                }
            }

            // Status Selection
            Text(
                text = "Attendance Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusCard(
                    text = "Present",
                    icon = Icons.Default.CheckCircle,
                    selected = status == "Present",
                    selectedColor = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                ) { status = "Present" }

                StatusCard(
                    text = "Absent",
                    icon = Icons.Default.Cancel,
                    selected = status == "Absent",
                    selectedColor = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                ) { status = "Absent" }
            }

            Spacer(Modifier.height(8.dp))

            // Save Button
            Button(
                enabled = !isSaving && selectedSubjectId.isNotBlank() && startTime != null && endTime != null,
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true

                    /* ---------------- Build lectureId (existing logic) ---------------- */

                    val dateKey =
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(lectureDate.time)
                    val startKey =
                        SimpleDateFormat("HHmm", Locale.getDefault()).format(startTime!!.time)
                    val endKey =
                        SimpleDateFormat("HHmm", Locale.getDefault()).format(endTime!!.time)

                    val lectureId = "${dateKey}_${startKey}_${endKey}"

                    /* ---------------- NEW: Compute lectureKey (if possible) ---------------- */

                    val calendar = lectureDate

                    val dayName = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> "MONDAY"
                        Calendar.TUESDAY -> "TUESDAY"
                        Calendar.WEDNESDAY -> "WEDNESDAY"
                        Calendar.THURSDAY -> "THURSDAY"
                        Calendar.FRIDAY -> "FRIDAY"
                        Calendar.SATURDAY -> "SATURDAY"
                        Calendar.SUNDAY -> "SUNDAY"
                        else -> null
                    }

                    val startHour = startTime!!.get(Calendar.HOUR_OF_DAY)
                    val endHour = endTime!!.get(Calendar.HOUR_OF_DAY)

                    val slotIndex = startHour - 9
                    val durationHours = endHour - startHour

                    val lectureKey =
                        if (
                            dayName != null &&
                            slotIndex >= 0 &&
                            durationHours > 0
                        ) {
                            "${dayName}_${slotIndex}_${durationHours}"
                        } else {
                            null
                        }

                    /* ---------------- Firestore refs ---------------- */

                    val subjectRef = db.collection("users")
                        .document(userId)
                        .collection("subjects")
                        .document(selectedSubjectId)

                    val attendanceRef =
                        subjectRef.collection("attendance").document(lectureId)

                    /* ---------------- Transaction ---------------- */

                    db.runTransaction { transaction ->

                        val attendanceSnap = transaction.get(attendanceRef)
                        if (attendanceSnap.exists()) {
                            throw Exception("Attendance already marked for this lecture")
                        }

                        val subjectSnap = transaction.get(subjectRef)

                        val totalClasses =
                            (subjectSnap.getLong("totalClasses") ?: 0) + 1

                        val attendedClasses =
                            if (status == "Present")
                                (subjectSnap.getLong("attendedClasses") ?: 0) + 1
                            else
                                subjectSnap.getLong("attendedClasses") ?: 0

                        val attendanceData = mutableMapOf<String, Any>(
                            "status" to status,
                            "date" to lectureDate.time,
                            "startTime" to startTime!!.time,
                            "endTime" to endTime!!.time,
                            "createdAt" to Date()
                        )

                        // ✅ Add lectureKey ONLY if it was computed
                        lectureKey?.let {
                            attendanceData["lectureKey"] = it
                        }

                        transaction.set(attendanceRef, attendanceData)

                        transaction.update(
                            subjectRef,
                            mapOf(
                                "totalClasses" to totalClasses,
                                "attendedClasses" to attendedClasses
                            )
                        )
                    }
                        .addOnSuccessListener {
                            isSaving = false
                            Toast.makeText(
                                context,
                                "Attendance saved successfully!",
                                Toast.LENGTH_SHORT
                            ).show()

                            startTime = null
                            endTime = null
                            status = "Present"
                        }
                        .addOnFailureListener { e ->
                            isSaving = false
                            Toast.makeText(
                                context,
                                e.message ?: "Failed to save attendance",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isSaving) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Saving...", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Save Attendance",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ---------------- REUSABLE COMPONENTS ---------------- */

@Composable
private fun HeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
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
                    Icons.Default.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column {
                Text(
                    "Mark Attendance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Fill in the details below",
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
    val isSelected = value != "Select" && value.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusCard(
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
        label = "status_scale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, selectedColor, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) selectedColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) selectedColor else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) selectedColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}