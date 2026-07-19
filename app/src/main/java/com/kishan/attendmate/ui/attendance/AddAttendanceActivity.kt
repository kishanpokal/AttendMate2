package com.kishan.attendmate.ui.attendance

import com.kishan.attendmate.ui.theme.statusColors

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
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
    var selectedSubjectId by remember { mutableStateOf("") }
    var selectedSubjectName by remember { mutableStateOf("") }

    // Attendance states
    var lectureDate by remember { mutableStateOf(Calendar.getInstance()) }
    var startTime by remember { mutableStateOf<Calendar?>(null) }
    var endTime by remember { mutableStateOf<Calendar?>(null) }
    var status by remember { mutableStateOf("Present") }
    var isSaving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var showSuccessOverlay by remember { mutableStateOf(false) }

    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

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

    /* Auto-fill from Timetable */
    LaunchedEffect(selectedSubjectId, lectureDate) {
        if (selectedSubjectId.isNotBlank()) {
            val dayName = when (lectureDate.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "MONDAY"
                Calendar.TUESDAY -> "TUESDAY"
                Calendar.WEDNESDAY -> "WEDNESDAY"
                Calendar.THURSDAY -> "THURSDAY"
                Calendar.FRIDAY -> "FRIDAY"
                Calendar.SATURDAY -> "SATURDAY"
                Calendar.SUNDAY -> "SUNDAY"
                else -> return@LaunchedEffect
            }
            
            try {
                val timetableSnapshot = db.collection("users")
                    .document(userId)
                    .collection("timetable")
                    .whereEqualTo("day", dayName)
                    .whereEqualTo("subjectId", selectedSubjectId)
                    .get()
                    .await()
                
                if (!timetableSnapshot.isEmpty) {
                    val doc = timetableSnapshot.documents.first()
                    val startRaw = doc.getString("startTime")
                    val endRaw = doc.getString("endTime")
                    
                    if (startRaw != null && endRaw != null) {
                        val sParts = startRaw.split(":")
                        val eParts = endRaw.split(":")
                        
                        if (sParts.size == 2 && eParts.size == 2) {
                            startTime = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, sParts[0].toInt())
                                set(Calendar.MINUTE, sParts[1].toInt())
                            }
                            endTime = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, eParts[0].toInt())
                                set(Calendar.MINUTE, eParts[1].toInt())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore failure, auto-fill is optional
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {

                    // Subject Section
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            icon = Icons.Outlined.Book,
                            title = "Subject",
                            subtitle = "Which lecture?"
                        )

                        if (isLoadingSubjects) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            ModernSubjectChips(
                                subjects = subjects,
                                selectedSubjectId = selectedSubjectId,
                                onSubjectSelected = { id, name ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedSubjectId = id
                                    selectedSubjectName = name
                                }
                            )
                        }
                    }

                    // Lecture Details Section
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            icon = Icons.Outlined.CalendarMonth,
                            title = "Date & Time",
                            subtitle = "Auto-filled from your timetable"
                        )

                        ModernDateTimeSelector(
                            lectureDate = lectureDate,
                            startTime = startTime,
                            endTime = endTime,
                            dateFormatter = dateFormatter,
                            timeFormatter = timeFormatter,
                            onDateChange = { newDate ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lectureDate = newDate
                            },
                            onStartTimeChange = { newTime ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                startTime = newTime
                                if (endTime == null) {
                                    endTime = Calendar.getInstance().apply {
                                        timeInMillis = newTime.timeInMillis + (60 * 60 * 1000)
                                    }
                                }
                            },
                            onEndTimeChange = { newTime ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                endTime = newTime
                                if (startTime == null) {
                                    startTime = Calendar.getInstance().apply {
                                        timeInMillis = newTime.timeInMillis - (60 * 60 * 1000)
                                    }
                                }
                            },
                            onDurationClick = { hours ->
                                startTime?.let { st ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    endTime = Calendar.getInstance().apply {
                                        timeInMillis = st.timeInMillis + (hours * 60 * 60 * 1000)
                                    }
                                }
                            }
                        )
                    }

                    // Status Selection
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            icon = Icons.Outlined.CheckCircle,
                            title = "Status",
                            subtitle = "Were you present?"
                        )

                        ModernStatusSelector(
                            status = status,
                            onStatusChange = { newStatus ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                status = newStatus
                            }
                        )

                        ContextAwareNoteField(
                            note = note,
                            onNoteChange = { if (it.length <= 200) note = it },
                            status = status
                        )
                    }

                    // Save Button
                    ModernSaveButton(
                        enabled = selectedSubjectId.isNotBlank() && startTime != null && endTime != null && !isSaving,
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
                                    "status" to status.uppercase(),
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
                                    showSuccessOverlay = true
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
                    
                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        // Lottie Success Overlay
        AnimatedVisibility(
            visible = showSuccessOverlay,
            enter = fadeIn(tween(300)) + scaleIn(tween(300, easing = OvershootInterpolator(1.2f))),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .clickable(enabled = false) {}, // intercept clicks
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val composition by rememberLottieComposition(LottieCompositionSpec.Url("https://assets9.lottiefiles.com/packages/lf20_lk80fpsm.json"))
                    LottieAnimation(
                        composition = composition,
                        iterations = 1,
                        modifier = Modifier.size(150.dp)
                    )
                    
                    Text(
                        "Attendance Saved!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "$selectedSubjectName • $status",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Auto-dismiss after animation
            LaunchedEffect(Unit) {
                delay(1800)
                onBack()
            }
        }
    }
}

// Custom easing for overshoot effect
class OvershootInterpolator(private val tension: Float = 2.0f) : Easing {
    override fun transform(fraction: Float): Float {
        val t = fraction - 1.0f
        return t * t * ((tension + 1) * t + tension) + 1.0f
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

// Modern Subject Chips
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernSubjectChips(
    subjects: List<Pair<String, String>>,
    selectedSubjectId: String,
    onSubjectSelected: (String, String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(subjects) { (id, name) ->
            val isSelected = id == selectedSubjectId
            FilterChip(
                selected = isSelected,
                onClick = { onSubjectSelected(id, name) },
                label = {
                    Text(
                        name,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}

// Modern Date Time Selector with M3 Pickers
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernDateTimeSelector(
    lectureDate: Calendar,
    startTime: Calendar?,
    endTime: Calendar?,
    dateFormatter: SimpleDateFormat,
    timeFormatter: SimpleDateFormat,
    onDateChange: (Calendar) -> Unit,
    onStartTimeChange: (Calendar) -> Unit,
    onEndTimeChange: (Calendar) -> Unit,
    onDurationClick: (Int) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = lectureDate.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance().apply { timeInMillis = millis }
                        onDateChange(cal)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = startTime?.get(Calendar.HOUR_OF_DAY) ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            initialMinute = startTime?.get(Calendar.MINUTE) ?: Calendar.getInstance().get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    onStartTimeChange(cal)
                    showStartTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = endTime?.get(Calendar.HOUR_OF_DAY) ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            initialMinute = endTime?.get(Calendar.MINUTE) ?: Calendar.getInstance().get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    onEndTimeChange(cal)
                    showEndTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Date Card
        ModernSelectionCard(
            icon = Icons.Filled.CalendarMonth,
            label = "Lecture Date",
            value = dateFormatter.format(lectureDate.time),
            isSelected = true,
            onClick = { showDatePicker = true }
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
                    onClick = { showStartTimePicker = true }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                ModernSelectionCard(
                    icon = Icons.Filled.AccessTime,
                    label = "End Time",
                    value = endTime?.let { timeFormatter.format(it.time) } ?: "Select",
                    isSelected = endTime != null,
                    onClick = { showEndTimePicker = true }
                )
            }
        }
        
        // Quick Duration Chips
        AnimatedVisibility(
            visible = startTime != null,
            enter = fadeIn() + expandVertically()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onDurationClick(1) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("1 Hour")
                }
                OutlinedButton(
                    onClick = { onDurationClick(2) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("2 Hours")
                }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
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

// Context-Aware Note Field
@Composable
fun ContextAwareNoteField(
    note: String,
    onNoteChange: (String) -> Unit,
    status: String
) {
    var isExpanded by remember { mutableStateOf(note.isNotEmpty()) }
    val isPresent = status == "Present"

    val containerColor by animateColorAsState(
        if (isPresent) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        label = "note_container_color"
    )
    val contentColor by animateColorAsState(
        if (isPresent) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onErrorContainer,
        label = "note_content_color"
    )
    val iconColor by animateColorAsState(
        if (isPresent) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.error,
        label = "note_icon_color"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.EditNote,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        if (isPresent) "Add a note (optional)" else "Reason for Absence (optional)",
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = contentColor
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
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
                                    color = if (note.length > 180) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = iconColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
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