package com.kishan.attendmate.ui.timetable.setup

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Dialog for adding or editing a lecture.
 *
 * Responsibilities:
 * - Subject selection
 * - Start time picker
 * - Duration selection (1–4 hours)
 *
 * NO Firestore logic here.
 */
@Composable
fun AddLectureDialog(
    subjects: List<SubjectUiModel>,
    onDismiss: () -> Unit,
    onConfirm: (
        subjectId: String,
        subjectName: String,
        startTime: LocalTime,
        durationHours: Int
    ) -> Unit
) {
    val context = LocalContext.current

    var selectedSubject by remember { mutableStateOf<SubjectUiModel?>(null) }
    var startTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var duration by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Add New Lecture",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure your lecture details",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {

                /* -------- Subject Picker -------- */
                SectionLabel("Subject")
                SubjectDropdown(
                    subjects = subjects,
                    selected = selectedSubject,
                    onSelected = { selectedSubject = it }
                )

                /* -------- Time Picker -------- */
                SectionLabel("Start Time")
                TimePickerField(
                    time = startTime,
                    icon = Icons.Default.AccessTime,
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                startTime = LocalTime.of(hour, minute)
                            },
                            startTime.hour,
                            startTime.minute,
                            false
                        ).show()
                    }
                )

                /* -------- Duration Picker -------- */
                SectionLabel("Duration")
                DurationSelector(
                    selected = duration,
                    onSelected = { duration = it }
                )

                // Summary Card
                if (selectedSubject != null) {
                    LectureSummaryCard(
                        subject = selectedSubject!!,
                        startTime = startTime,
                        duration = duration
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedSubject != null,
                onClick = {
                    selectedSubject?.let {
                        onConfirm(
                            it.id,
                            it.name,
                            startTime,
                            duration
                        )
                    }
                },
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add Lecture", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

/* ------------------------------------------------ */
/* UI Helpers                                       */
/* ------------------------------------------------ */

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectDropdown(
    subjects: List<SubjectUiModel>,
    selected: SubjectUiModel?,
    onSelected: (SubjectUiModel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text("Select a subject") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.then(
                        if (expanded) Modifier else Modifier
                    )
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (subjects.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No subjects available", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { },
                    enabled = false
                )
            } else {
                subjects.forEach { subject ->
                    DropdownMenuItem(
                        text = { Text(subject.name, fontWeight = FontWeight.Medium) },
                        onClick = {
                            onSelected(subject)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimePickerField(
    time: LocalTime,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = time.format(timeFormatter),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = "Change",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DurationSelector(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        (1..4).forEach { hour ->
            val isSelected = selected == hour
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelected(hour) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$hour",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (hour == 1) "hour" else "hours",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LectureSummaryCard(
    subject: SubjectUiModel,
    startTime: LocalTime,
    duration: Int
) {
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
    val endTime = startTime.plusHours(duration.toLong())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Column {
                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${startTime.format(timeFormatter)} - ${endTime.format(timeFormatter)} ($duration ${if (duration == 1) "hour" else "hours"})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}