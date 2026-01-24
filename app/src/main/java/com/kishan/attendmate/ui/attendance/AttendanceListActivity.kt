package com.kishan.attendmate.ui.attendance

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.components.AttendMateNavigationBar
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/* -------------------- SAFE TIME READER -------------------- */
private fun readTimeAsString(
    doc: DocumentSnapshot,
    field: String
): String {
    val value = doc.get(field) ?: return ""
    return when (value) {
        is Timestamp -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(value.toDate())
        }
        is String -> value
        else -> ""
    }
}

/* -------------------- ACTIVITY -------------------- */
class AttendanceListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendMateTheme {
                Scaffold(
                    bottomBar = {
                        AttendMateNavigationBar(selectedRoute = "attendance")
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        AttendanceListScreen(
                            onEdit = { subjectId, attendanceId ->
                                startActivity(
                                    Intent(
                                        this@AttendanceListActivity,
                                        EditAttendanceActivity::class.java
                                    ).apply {
                                        putExtra("subjectId", subjectId)
                                        putExtra("attendanceId", attendanceId)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/* -------------------- MODELS -------------------- */
data class AttendanceItem(
    val subjectId: String,
    val attendanceId: String,
    val subjectName: String,
    val date: Date,
    val status: String,  // "PRESENT" / "ABSENT"
    val startTime: String = "",
    val endTime: String = ""
)

data class FilterOption(
    val name: String,
    val icon: ImageVector
)

/* -------------------- SCREEN -------------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceListScreen(
    onEdit: (String, String) -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid ?: return
    val scope = rememberCoroutineScope()
    var allAttendance by remember { mutableStateOf<List<AttendanceItem>>(emptyList()) }
    var filter by remember { mutableStateOf("All") }
    var selectedSubject by remember { mutableStateOf<String?>(null) }
    var subjects by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var selectedAttendance by remember { mutableStateOf<AttendanceItem?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    // Lifecycle observer to refresh when returning to this screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    /* -------- FETCH DATA (SAFE) -------- */
    LaunchedEffect(refreshKey) {
        loading = true
        val tempList = mutableListOf<AttendanceItem>()
        val subjectMap = mutableMapOf<String, String>()
        val subjectsSnap = db.collection("users")
            .document(userId)
            .collection("subjects")
            .get()
            .await()

        if (subjectsSnap.isEmpty) {
            loading = false
            return@LaunchedEffect
        }

        for (subjectDoc in subjectsSnap.documents) {
            val subjectId = subjectDoc.id
            val subjectName = subjectDoc.getString("name") ?: "Unknown"
            subjectMap[subjectId] = subjectName
            val attendanceSnap = subjectDoc.reference
                .collection("attendance")
                .get()
                .await()
            for (doc in attendanceSnap.documents) {
                val date = doc.getTimestamp("date")?.toDate() ?: continue
                val status = doc.getString("status")?.uppercase() ?: "ABSENT"
                val startTime = readTimeAsString(doc, "startTime")
                val endTime = readTimeAsString(doc, "endTime")
                tempList.add(
                    AttendanceItem(
                        subjectId = subjectId,
                        attendanceId = doc.id,
                        subjectName = subjectName,
                        date = date,
                        status = status,
                        startTime = startTime,
                        endTime = endTime
                    )
                )
            }
        }
        allAttendance = tempList.sortedByDescending { it.date }
        subjects = subjectMap
        loading = false
    }

    val filteredList = remember(allAttendance, filter, selectedSubject, searchQuery) {
        allAttendance.filter {
            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it.date)
            val matchesSearch = searchQuery.isEmpty() ||
                    it.subjectName.contains(searchQuery, ignoreCase = true) ||
                    dateStr.contains(searchQuery, ignoreCase = true)
            val matchesStatus = filter == "All" || it.status.uppercase() == filter.uppercase()
            val matchesSubject = selectedSubject == null || it.subjectId == selectedSubject
            matchesSearch && matchesStatus && matchesSubject
        }
    }
    val total = filteredList.size
    val attended = filteredList.count { it.status == "PRESENT" }
    val missed = filteredList.count { it.status == "ABSENT" }
    val percentage = if (total == 0) 0 else ((attended.toFloat() / total.toFloat()) * 100).toInt()

    Column(modifier = Modifier.fillMaxSize()) {
        /* -------- TOP HEADER -------- */
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Attendance",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Track your class attendance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        /* -------- LOADING STATE -------- */
        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading attendance...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        /* -------- CONTENT -------- */
        AnimatedVisibility(
            visible = !loading,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AttendanceSummaryCard(
                        total = total,
                        present = attended,
                        absent = missed,
                        percentage = percentage
                    )
                }
                /* -------- SEARCH BAR -------- */
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by date or subject name") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(50.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
                /* -------- FILTER CHIPS -------- */
                item {
                    ProfessionalFilterChips(
                        selectedFilter = filter,
                        onFilterChange = { filter = it },
                        selectedSubject = selectedSubject,
                        onSubjectChange = { selectedSubject = it },
                        subjects = subjects
                    )
                }
                /* -------- LIST HEADER -------- */
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Attendance Records",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                /* -------- LIST -------- */
                when {
                    filteredList.isEmpty() -> {
                        item {
                            EmptyState(filter = filter)
                        }
                    }
                    else -> {
                        val groupedAttendance = filteredList.groupBy {
                            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it.date)
                        }
                        groupedAttendance.forEach { (date, items) ->
                            item {
                                DateHeader(date = date)
                            }
                            items(items) { item ->
                                ModernAttendanceCard(
                                    item = item,
                                    onClick = { selectedAttendance = item }
                                )
                            }
                        }
                    }
                }
                // Bottom padding
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
    /* -------- DETAIL DIALOG -------- */
    selectedAttendance?.let { item ->
        ModernAttendanceDialog(
            attendance = item,
            onDismiss = { selectedAttendance = null },
            onEdit = {
                onEdit(item.subjectId, item.attendanceId)
                selectedAttendance = null
            },
            onDelete = {
                scope.launch {
                    try {
                        val subjectRef = db.collection("users")
                            .document(userId)
                            .collection("subjects")
                            .document(item.subjectId)
                        val attendanceRef = subjectRef
                            .collection("attendance")
                            .document(item.attendanceId)
                        db.runTransaction { tx ->
                            // Read attendance first
                            val attendanceSnap = tx.get(attendanceRef)
                            if (!attendanceSnap.exists()) {
                                throw Exception("Attendance not found")
                            }
                            val status = attendanceSnap.getString("status")?.uppercase() ?: "ABSENT"
                            // Read subject counters
                            val subjectSnap = tx.get(subjectRef)
                            var totalClasses = subjectSnap.getLong("totalClasses") ?: 0L
                            var attendedClasses = subjectSnap.getLong("attendedClasses") ?: 0L
                            // Update counters
                            totalClasses -= 1
                            if (status == "PRESENT") {
                                attendedClasses -= 1
                            }
                            // Safety clamp
                            tx.update(
                                subjectRef,
                                mapOf(
                                    "totalClasses" to maxOf(0, totalClasses),
                                    "attendedClasses" to maxOf(0, attendedClasses)
                                )
                            )
                            // Delete attendance
                            tx.delete(attendanceRef)
                        }.await()
                        selectedAttendance = null
                        refreshKey++ // refresh list
                    } catch (e: Exception) {
                        // Handle error (optional: show toast)
                    }
                }
            }
        )
    }
}

/* -------------------- UI COMPONENTS -------------------- */
@Composable
fun AttendanceSummaryCard(
    total: Int,
    present: Int,
    absent: Int,
    percentage: Int
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val animatedPercentage = remember { Animatable(0f) }
    LaunchedEffect(percentage) {
        animatedPercentage.animateTo(
            targetValue = percentage.toFloat(),
            animationSpec = tween(durationMillis = 1000, easing = EaseOutCubic)
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.12f),
                        primaryColor.copy(alpha = 0.04f)
                    )
                )
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Overview",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = primaryColor
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                // Circular progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(120.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedPercentage.value / 100f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 10.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${animatedPercentage.value.toInt()}%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            Text(
                                text = "Attended",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StatItem(
                            label = "Present",
                            value = present.toString(),
                            color = primaryColor,
                            icon = Icons.Default.CheckCircle
                        )
                        StatItem(
                            label = "Absent",
                            value = absent.toString(),
                            color = errorColor,
                            icon = Icons.Outlined.Cancel
                        )
                        StatItem(
                            label = "Total",
                            value = total.toString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            icon = Icons.Default.List
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalFilterChips(
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    selectedSubject: String?,
    onSubjectChange: (String?) -> Unit,
    subjects: Map<String, String>
) {
    val filters = listOf(
        FilterOption("All", Icons.Default.List),
        FilterOption("Present", Icons.Default.CheckCircle),
        FilterOption("Absent", Icons.Outlined.Cancel),
        FilterOption("Subject", Icons.Default.School)
    )
    var showSubjectMenu by remember { mutableStateOf(false) }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { filterOption ->
            val isSelected = when {
                filterOption.name == "Subject" -> selectedSubject != null
                else -> selectedFilter == filterOption.name
            }
            val displayLabel = when {
                filterOption.name == "Subject" && selectedSubject != null -> subjects[selectedSubject!!] ?: "Subject"
                else -> filterOption.name
            }
            Box {
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        when (filterOption.name) {
                            "Subject" -> {
                                showSubjectMenu = true
                            }
                            else -> {
                                onFilterChange(filterOption.name)
                                showSubjectMenu = false
                            }
                        }
                    },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = displayLabel,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = filterOption.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                        borderWidth = 0.dp,
                        selectedBorderWidth = 0.dp
                    )
                )
                // Subject dropdown
                if (filterOption.name == "Subject") {
                    DropdownMenu(
                        expanded = showSubjectMenu,
                        onDismissRequest = { showSubjectMenu = false }
                    ) {
                        if (subjects.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No subjects available") },
                                onClick = { showSubjectMenu = false }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("All Subjects") },
                                onClick = {
                                    onSubjectChange(null)
                                    showSubjectMenu = false
                                }
                            )
                            subjects.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        onSubjectChange(id)
                                        showSubjectMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
        Text(
            text = date,
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    }
}

@Composable
fun ModernAttendanceCard(
    item: AttendanceItem,
    onClick: () -> Unit
) {
    val isPresent = item.status == "PRESENT"
    val statusColor = if (isPresent)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPresent) Icons.Filled.CheckCircle else Icons.Outlined.Cancel,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.subjectName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.startTime.isNotEmpty() && item.endTime.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${item.startTime} - ${item.endTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = if (isPresent) "Present" else "Absent",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EmptyState(filter: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (filter) {
                        "Present" -> Icons.Default.CheckCircle
                        "Absent" -> Icons.Outlined.Cancel
                        else -> Icons.Outlined.EventNote
                    },
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
            Text(
                text = when (filter) {
                    "Present" -> "No Present Records"
                    "Absent" -> "No Absent Records"
                    else -> "No Attendance Records"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = when (filter) {
                    "Present" -> "You haven't marked any attendance as present yet"
                    "Absent" -> "Great! You haven't missed any classes"
                    else -> "Start tracking your attendance to see records here"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernAttendanceDialog(
    attendance: AttendanceItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isPresent = attendance.status == "PRESENT"
    val statusColor = if (isPresent)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.error
    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val formattedDate = dateFormatter.format(attendance.date)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clip(RoundedCornerShape(28.dp))
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPresent) Icons.Filled.CheckCircle else Icons.Outlined.Cancel,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = attendance.subjectName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = statusColor.copy(alpha = 0.1f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isPresent) "Present" else "Absent",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                            Text(
                                text = if (isPresent) "✓ Attended" else "✗ Missed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = statusColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Date",
                        value = formattedDate
                    )
                    if (attendance.startTime.isNotEmpty() && attendance.endTime.isNotEmpty()) {
                        DetailRow(
                            icon = Icons.Default.Schedule,
                            label = "Time",
                            value = "${attendance.startTime} - ${attendance.endTime}"
                        )
                    }
                    DetailRow(
                        icon = Icons.Default.School,
                        label = "Subject",
                        value = attendance.subjectName
                    )
                    DetailRow(
                        icon = if (isPresent) Icons.Default.CheckCircle else Icons.Outlined.Cancel,
                        label = "Status",
                        value = if (isPresent) "Present" else "Absent"
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}