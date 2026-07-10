package com.kishan.attendmate.ui.attendance

import com.kishan.attendmate.ui.theme.statusColors

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AddAttendanceScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAttendanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
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
    var note by remember { mutableStateOf("") }

    // Animation states
    var currentStep by remember { mutableStateOf(0) }

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
                            "Mark Attendance",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Track your class participation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.shadow(
                    elevation = 2.dp,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                // Progress Indicator
                AnimatedProgressIndicator(
                    currentStep = currentStep,
                    totalSteps = 4,
                    stepLabels = listOf("Subject", "Date & Time", "Status", "Save")
                )

                // Hero Card with gradient
                HeroCard()

                // Subject Section
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            icon = Icons.Outlined.Book,
                            title = "Select Subject",
                            subtitle = "Choose the subject for this lecture"
                        )

                        if (isLoadingSubjects) {
                            LoadingCard()
                        } else {
                            ModernSubjectSelector(
                                subjects = subjects,
                                selectedSubjectName = selectedSubjectName,
                                expanded = expanded,
                                onExpandChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    expanded = it
                                },
                                onSubjectSelected = { id, name ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedSubjectId = id
                                    selectedSubjectName = name
                                    currentStep = 1
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Lecture Details Section
                AnimatedVisibility(
                    visible = selectedSubjectId.isNotBlank(),
                    enter = fadeIn() + slideInVertically() + expandVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            icon = Icons.Outlined.CalendarMonth,
                            title = "Date & Time",
                            subtitle = "When did this lecture take place?"
                        )

                        ModernDateTimeSelector(
                            lectureDate = lectureDate,
                            startTime = startTime,
                            endTime = endTime,
                            dateFormatter = dateFormatter,
                            timeFormatter = timeFormatter,
                            onDateClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        lectureDate = Calendar.getInstance().apply {
                                            set(year, month, day)
                                        }
                                        currentStep = maxOf(currentStep, 2)
                                    },
                                    lectureDate.get(Calendar.YEAR),
                                    lectureDate.get(Calendar.MONTH),
                                    lectureDate.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            onStartTimeClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        startTime = Calendar.getInstance().apply {
                                            set(Calendar.HOUR_OF_DAY, hour)
                                            set(Calendar.MINUTE, minute)
                                        }
                                        currentStep = maxOf(currentStep, 2)
                                    },
                                    currentTime.get(Calendar.HOUR_OF_DAY),
                                    currentTime.get(Calendar.MINUTE),
                                    false
                                ).show()
                            },
                            onEndTimeClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        endTime = Calendar.getInstance().apply {
                                            set(Calendar.HOUR_OF_DAY, hour)
                                            set(Calendar.MINUTE, minute)
                                        }
                                        currentStep = maxOf(currentStep, 2)
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
                AnimatedVisibility(
                    visible = startTime != null && endTime != null,
                    enter = fadeIn() + slideInVertically() + expandVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            icon = Icons.Outlined.CheckCircle,
                            title = "Attendance Status",
                            subtitle = "Mark your presence for this lecture"
                        )

                        ModernStatusSelector(
                            status = status,
                            onStatusChange = { newStatus ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                status = newStatus
                                currentStep = 3
                            }
                        )

                        ModernNoteField(
                            note = note,
                            onNoteChange = { if (it.length <= 200) note = it },
                            status = status
                        )
                    }
                }

                // Save Button
                AnimatedVisibility(
                    visible = selectedSubjectId.isNotBlank() && startTime != null && endTime != null,
                    enter = fadeIn() + slideInVertically() + expandVertically()
                ) {
                    ModernSaveButton(
                        enabled = !isSaving,
                        isSaving = isSaving,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isSaving) return@ModernSaveButton
                            isSaving = true

                            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(lectureDate.time)
                            val startKey = SimpleDateFormat("HHmm", Locale.getDefault())
                                .format(startTime!!.time)
                            val endKey = SimpleDateFormat("HHmm", Locale.getDefault())
                                .format(endTime!!.time)
                            val lectureId = "${dateKey}_${startKey}_${endKey}"

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

                            val lectureKey = if (dayName != null && slotIndex >= 0 && durationHours > 0) {
                                "${dayName}_${slotIndex}_${durationHours}"
                            } else null

                            val subjectRef = db.collection("users")
                                .document(userId)
                                .collection("subjects")
                                .document(selectedSubjectId)

                            val attendanceRef = subjectRef.collection("attendance").document(lectureId)

                            db.runTransaction { transaction ->
                                val attendanceSnap = transaction.get(attendanceRef)
                                if (attendanceSnap.exists()) {
                                    throw Exception("Attendance already marked for this lecture")
                                }

                                val subjectSnap = transaction.get(subjectRef)
                                val totalClasses = (subjectSnap.getLong("totalClasses") ?: 0) + 1
                                val attendedClasses = if (status == "Present")
                                    (subjectSnap.getLong("attendedClasses") ?: 0) + 1
                                else subjectSnap.getLong("attendedClasses") ?: 0

                                val attendanceData = mutableMapOf(
                                    "status" to status,
                                    "date" to lectureDate.time,
                                    "startTime" to startTime!!.time,
                                    "endTime" to endTime!!.time,
                                    "createdAt" to Date()
                                )

                                if (note.isNotBlank()) {
                                    attendanceData["note"] = note.trim()
                                }

                                lectureKey?.let { attendanceData["lectureKey"] = it }

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
                                        "✓ Attendance saved successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    startTime = null
                                    endTime = null
                                    status = "Present"
                                    note = ""
                                    currentStep = 0
                                }
                                .addOnFailureListener { e ->
                                    isSaving = false
                                    Toast.makeText(
                                        context,
                                        e.message ?: "Failed to save attendance",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// Modern Progress Indicator
@Composable
fun AnimatedProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    stepLabels: List<String>
) {
    val progress by animateFloatAsState(
        targetValue = (currentStep + 1) / totalSteps.toFloat(),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Progress",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${currentStep + 1}/$totalSteps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            if (currentStep < stepLabels.size) {
                Text(
                    "Current: ${stepLabels[currentStep]}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Hero Card with Gradient
@Composable
fun HeroCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
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
                        Icons.Filled.EditCalendar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Mark Your Attendance",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Keep track of every class you attend",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

// Section Header with Icon
@Composable
fun SectionHeader(
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Loading Card
@Composable
fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Loading subjects...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Modern Subject Selector
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernSubjectSelector(
    subjects: List<Pair<String, String>>,
    selectedSubjectName: String,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onSubjectSelected: (String, String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandChange
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .shadow(
                    elevation = if (expanded) 8.dp else 2.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedSubjectName.isNotEmpty())
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            border = if (expanded) BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            ) else null
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
                            .background(
                                if (selectedSubjectName.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Book,
                            contentDescription = null,
                            tint = if (selectedSubjectName.isNotEmpty())
                                Color.White
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Subject",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            selectedSubjectName.ifEmpty { "Select a subject" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selectedSubjectName.isNotEmpty())
                                FontWeight.SemiBold
                            else
                                FontWeight.Normal,
                            color = if (selectedSubjectName.isEmpty())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (expanded)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else
                                Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = if (expanded)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandChange(false) },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .exposedDropdownSize()
        ) {
            subjects.forEach { (id, name) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (name == selectedSubjectName)
                                FontWeight.SemiBold
                            else
                                FontWeight.Normal
                        )
                    },
                    onClick = { onSubjectSelected(id, name) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (name == selectedSubjectName)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Book,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (name == selectedSubjectName)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingIcon = if (name == selectedSubjectName) {
                        {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else null,
                    colors = MenuDefaults.itemColors(
                        textColor = if (name == selectedSubjectName)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// Modern Date Time Selector
@Composable
fun ModernDateTimeSelector(
    lectureDate: Calendar,
    startTime: Calendar?,
    endTime: Calendar?,
    dateFormatter: SimpleDateFormat,
    timeFormatter: SimpleDateFormat,
    onDateClick: () -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Date Card
        ModernSelectionCard(
            icon = Icons.Filled.CalendarMonth,
            label = "Lecture Date",
            value = dateFormatter.format(lectureDate.time),
            isSelected = true,
            onClick = onDateClick
        )

        // Time Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ModernSelectionCard(
                    icon = Icons.Filled.AccessTime,
                    label = "Start Time",
                    value = startTime?.let { timeFormatter.format(it.time) } ?: "Select",
                    isSelected = startTime != null,
                    onClick = onStartTimeClick
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                ModernSelectionCard(
                    icon = Icons.Filled.AccessTime,
                    label = "End Time",
                    value = endTime?.let { timeFormatter.format(it.time) } ?: "Select",
                    isSelected = endTime != null,
                    onClick = onEndTimeClick
                )
            }
        }

        // Duration Display
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
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Timelapse,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Duration: ${if (hours > 0) "${hours}h " else ""}${minutes}min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// Modern Selection Card
@Composable
fun ModernSelectionCard(
    icon: ImageVector,
    label: String,
    value: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.98f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card_scale"
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
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = if (isSelected) BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ) else null
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
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceContainerHighest
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

                Column {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Modern Status Selector
@Composable
fun ModernStatusSelector(
    status: String,
    onStatusChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            EnhancedStatusCard(
                text = "Present",
                icon = Icons.Filled.CheckCircle,
                selected = status == "Present",
                selectedColor = statusColors().success,
                onClick = { onStatusChange("Present") }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            EnhancedStatusCard(
                text = "Absent",
                icon = Icons.Filled.Cancel,
                selected = status == "Absent",
                selectedColor = statusColors().error,
                onClick = { onStatusChange("Absent") }
            )
        }
    }
}

// Enhanced Status Card
@Composable
fun EnhancedStatusCard(
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
        label = "status_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.7f,
        label = "status_alpha"
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
                color = if (selected) selectedColor else MaterialTheme.colorScheme.onSurface
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

// Modern Note Field
@Composable
fun ModernNoteField(
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

// Modern Save Button
@Composable
fun ModernSaveButton(
    enabled: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit
) {
    com.kishan.attendmate.ui.components.PrimaryButton(
        text = "Save Attendance",
        onClick = onClick,
        enabled = enabled,
        isLoading = isSaving,
        icon = {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    )
}