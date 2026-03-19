package com.kishan.attendmate.ui.attendance

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
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
        
        // 1. Extract the filterDate from Intent if it exists
        val filterDate = intent.getStringExtra("filterDate") ?: ""
        
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
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    )
                                )
                            )
                    ) {
                        AttendanceListScreen(
                            initialSearchQuery = filterDate,
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
    val status: String,
    val startTime: String = "",
    val endTime: String = "",
    val note: String? = null
)

data class FilterOption(
    val name: String,
    val icon: ImageVector
)

/* -------------------- SCREEN -------------------- */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AttendanceListScreen(
    initialSearchQuery: String = "",
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
    
    // 2. Initialize search query with the passed value
    var searchQuery by remember { mutableStateOf(initialSearchQuery) }

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

    /* -------- OFFLINE-FIRST DATA FETCH -------- */
    LaunchedEffect(refreshKey) {
        if (!loading && allAttendance.isNotEmpty()) loading = true

        suspend fun fetchWithSource(source: Source) {
            val tempList = mutableListOf<AttendanceItem>()
            val subjectMap = mutableMapOf<String, String>()

            val subjectsSnap = db.collection("users")
                .document(userId)
                .collection("subjects")
                .get(source) // Passed Source (CACHE or SERVER)
                .await()

            if (subjectsSnap.isEmpty) {
                if (source == Source.SERVER) loading = false
                return
            }

            for (subjectDoc in subjectsSnap.documents) {
                val subjectId = subjectDoc.id
                val subjectName = subjectDoc.getString("name") ?: "Unknown"
                subjectMap[subjectId] = subjectName

                val attendanceSnap = subjectDoc.reference
                    .collection("attendance")
                    .get(source) // Passed Source
                    .await()

                for (doc in attendanceSnap.documents) {
                    val date = doc.getTimestamp("date")?.toDate() ?: continue
                    val status = doc.getString("status")?.uppercase() ?: "ABSENT"
                    val startTime = readTimeAsString(doc, "startTime")
                    val endTime = readTimeAsString(doc, "endTime")
                    val note = doc.getString("note")

                    tempList.add(
                        AttendanceItem(
                            subjectId = subjectId,
                            attendanceId = doc.id,
                            subjectName = subjectName,
                            date = date,
                            status = status,
                            startTime = startTime,
                            endTime = endTime,
                            note = note
                        )
                    )
                }
            }
            allAttendance = tempList.sortedByDescending { it.date }
            subjects = subjectMap
        }

        try {
            // 1. Instant Cache Load
            fetchWithSource(Source.CACHE)
            loading = false
        } catch (e: Exception) {
            // Cache empty, wait for server
        }

        try {
            // 2. Silent Server Update
            fetchWithSource(Source.SERVER)
        } catch (e: Exception) {
            // Network failed, rely on cache data currently on screen
        } finally {
            loading = false
        }
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
        /* -------- SLEEK HEADER -------- */
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 26.sp
                        )
                        Text(
                            text = "Your complete attendance record",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        /* -------- CONTENT -------- */
        if (loading && allAttendance.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically()) {
                        AttendanceSummaryCard(total = total, present = attended, absent = missed, percentage = percentage)
                    }
                }

                /* -------- SEARCH BAR -------- */
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by date or subject...", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
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

                /* -------- LIST WITH STICKY HEADERS -------- */
                when {
                    filteredList.isEmpty() -> {
                        item { EmptyState(filter = filter) }
                    }
                    else -> {
                        val groupedAttendance = filteredList.groupBy {
                            SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(it.date)
                        }

                        groupedAttendance.forEach { (date, items) ->
                            // STICKY HEADER
                            stickyHeader {
                                DateHeader(date = date)
                            }
                            items(items, key = { it.attendanceId }) { item ->
                                Box(modifier = Modifier.animateItemPlacement()) {
                                    ModernAttendanceCard(
                                        item = item,
                                        onClick = { selectedAttendance = item }
                                    )
                                }
                            }
                        }
                    }
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
                        val subjectRef = db.collection("users").document(userId).collection("subjects").document(item.subjectId)
                        val attendanceRef = subjectRef.collection("attendance").document(item.attendanceId)

                        db.runTransaction { tx ->
                            val attendanceSnap = tx.get(attendanceRef)
                            if (attendanceSnap.exists()) {
                                val status = attendanceSnap.getString("status")?.uppercase() ?: "ABSENT"
                                val subjectSnap = tx.get(subjectRef)
                                val totalClasses = subjectSnap.getLong("totalClasses") ?: 0L
                                val attendedClasses = subjectSnap.getLong("attendedClasses") ?: 0L

                                tx.update(subjectRef, mapOf(
                                    "totalClasses" to maxOf(0, totalClasses - 1),
                                    "attendedClasses" to maxOf(0, if (status == "PRESENT") attendedClasses - 1 else attendedClasses)
                                ))
                                tx.delete(attendanceRef)
                            }
                        }.await()
                        selectedAttendance = null
                        refreshKey++
                    } catch (e: Exception) { }
                }
            }
        )
    }
}

/* -------------------- PRO UI COMPONENTS -------------------- */
@Composable
fun AttendanceSummaryCard(total: Int, present: Int, absent: Int, percentage: Int) {
    val animatedPercentage = remember { Animatable(0f) }
    LaunchedEffect(percentage) {
        animatedPercentage.animateTo(
            targetValue = percentage.toFloat(),
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    val statusColor = if (percentage >= 75) Color(0xFF10B981) else if (percentage >= 60) Color(0xFFF59E0B) else Color(0xFFEF4444)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
        ) {
            // 1. Read the color OUTSIDE the Canvas block
            val primaryColor = MaterialTheme.colorScheme.primary

// 2. Use the variable INSIDE the Canvas block
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.05f),
                    radius = 150f,
                    center = Offset(size.width, 0f)
                )
            }

            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Analytics Overview",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular Progress
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 12.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round
                        )
                        CircularProgressIndicator(
                            progress = { animatedPercentage.value / 100f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 12.dp,
                            color = statusColor,
                            strokeCap = StrokeCap.Round
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${animatedPercentage.value.toInt()}%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = statusColor
                            )
                        }
                    }

                    // Stats Column
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProStatItem(label = "Present", value = present.toString(), color = Color(0xFF10B981), icon = Icons.Default.CheckCircle)
                        ProStatItem(label = "Absent", value = absent.toString(), color = Color(0xFFEF4444), icon = Icons.Default.Cancel)
                        ProStatItem(label = "Total", value = total.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, icon = Icons.Default.List)
                    }
                }
            }
        }
    }
}

@Composable
fun ProStatItem(label: String, value: String, color: Color, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalFilterChips(
    selectedFilter: String, onFilterChange: (String) -> Unit,
    selectedSubject: String?, onSubjectChange: (String?) -> Unit,
    subjects: Map<String, String>
) {
    val filters = listOf(FilterOption("All", Icons.Default.List), FilterOption("Present", Icons.Default.CheckCircle), FilterOption("Absent", Icons.Outlined.Cancel), FilterOption("Subject", Icons.Default.School))
    var showSubjectMenu by remember { mutableStateOf(false) }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(filters) { filterOption ->
            val isSelected = if (filterOption.name == "Subject") selectedSubject != null else selectedFilter == filterOption.name
            val displayLabel = if (filterOption.name == "Subject" && selectedSubject != null) subjects[selectedSubject!!] ?: "Subject" else filterOption.name

            Box {
                FilterChip(
                    selected = isSelected,
                    onClick = { if (filterOption.name == "Subject") showSubjectMenu = true else onFilterChange(filterOption.name) },
                    label = { Text(displayLabel, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp) },
                    leadingIcon = { Icon(filterOption.icon, null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = null
                )
                if (filterOption.name == "Subject") {
                    DropdownMenu(expanded = showSubjectMenu, onDismissRequest = { showSubjectMenu = false }) {
                        DropdownMenuItem(text = { Text("All Subjects") }, onClick = { onSubjectChange(null); showSubjectMenu = false })
                        subjects.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSubjectChange(id); showSubjectMenu = false }) }
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(date: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 0.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Box(modifier = Modifier.height(1.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
            Text(
                text = date,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Box(modifier = Modifier.height(1.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

@Composable
fun ModernAttendanceCard(item: AttendanceItem, onClick: () -> Unit) {
    val isPresent = item.status == "PRESENT"
    val statusColor = if (isPresent) Color(0xFF10B981) else Color(0xFFEF4444)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.97f else 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (isPresent) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null, tint = statusColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = item.subjectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!item.note.isNullOrBlank()) Icon(Icons.Default.EditNote, "Has note", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                if (item.startTime.isNotEmpty() && item.endTime.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Schedule, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${item.startTime} - ${item.endTime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                Text(
                    text = if (isPresent) "Present" else "Absent",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun EmptyState(filter: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(if (filter == "Present") Icons.Default.CheckCircle else if (filter == "Absent") Icons.Default.Cancel else Icons.Outlined.EventNote, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "No Records Found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = "Try adjusting your filters or search query.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernAttendanceDialog(attendance: AttendanceItem, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val isPresent = attendance.status == "PRESENT"
    val statusColor = if (isPresent) Color(0xFF10B981) else Color(0xFFEF4444)
    val formattedDate = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(attendance.date)
    var isDeleting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // Header
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(statusColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(if (isPresent) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null, tint = statusColor, modifier = Modifier.size(24.dp))
                            }
                            Text("Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    // Subject info
                    Text(text = attendance.subjectName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Rows
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProDetailRow(Icons.Default.CalendarToday, "Date", formattedDate)
                        if (attendance.startTime.isNotEmpty()) ProDetailRow(Icons.Default.Schedule, "Time", "${attendance.startTime} - ${attendance.endTime}")
                        ProDetailRow(if (isPresent) Icons.Default.CheckCircle else Icons.Default.Cancel, "Status", if (isPresent) "Present" else "Absent", statusColor)
                        if (!attendance.note.isNullOrBlank()) ProDetailRow(Icons.Default.EditNote, "Note", attendance.note!!)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (!isDeleting) {
                                    isDeleting = true
                                    onDelete()
                                }
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            AnimatedContent(targetState = isDeleting, label = "delete_anim") { deleting ->
                                if (deleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.error,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Delete", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Button(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Edit Record", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
fun ProDetailRow(icon: ImageVector, label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = valueColor)
        }
    }
}