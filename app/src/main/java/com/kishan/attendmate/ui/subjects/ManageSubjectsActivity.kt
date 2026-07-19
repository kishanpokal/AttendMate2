package com.kishan.attendmate.ui.subjects

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.theme.AttendMateTheme

data class Subject(val id: String, val name: String)

class ManageSubjectsActivity : ComponentActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AttendMateTheme {
                val user = auth.currentUser ?: return@AttendMateTheme

                var subjectName by remember { mutableStateOf("") }
                var subjects by remember { mutableStateOf<List<Subject>>(emptyList()) }

                // 🔹 Loaders
                var pageLoading by remember { mutableStateOf(true) }
                var addingSubject by remember { mutableStateOf(false) }

                // 🔹 Load existing subjects
                LaunchedEffect(Unit) {
                    pageLoading = true
                    db.collection("users")
                            .document(user.uid)
                            .collection("subjects")
                            .get()
                            .addOnSuccessListener { snapshot ->
                                subjects =
                                        snapshot.documents.map {
                                            Subject(id = it.id, name = it.getString("name") ?: "")
                                        }
                                pageLoading = false
                            }
                            .addOnFailureListener {
                                pageLoading = false
                                Toast.makeText(
                                                this@ManageSubjectsActivity,
                                                "Failed to load subjects",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            }
                }

                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(
                            modifier =
                                    Modifier.fillMaxSize()
                                            .padding(
                                                    start = 24.dp,
                                                    end = 24.dp,
                                                    top = 40.dp,
                                                    bottom = 100.dp
                                            ),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Surface(
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)),
                                color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                        Icons.Default.Book,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                                text = "Manage Subjects",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                                text = "Add or remove subjects for attendance tracking",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(Modifier.height(32.dp))

                        // Card
                        Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 8.dp
                        ) {
                            Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                OutlinedTextField(
                                        value = subjectName,
                                        onValueChange = { subjectName = it },
                                        label = { Text("Subject Name", color = Color(0xFF6B7280)) },
                                        placeholder = {
                                            Text("e.g., Deep Learning", color = Color(0xFF9CA3AF))
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Book, null, tint = Color(0xFF8B5CF6))
                                        },
                                        singleLine = true,
                                        keyboardOptions =
                                                KeyboardOptions(imeAction = ImeAction.Done),
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = Color(0xFF8B5CF6),
                                                        unfocusedBorderColor = Color(0xFFE5E7EB),
                                                        focusedLabelColor = Color(0xFF000000),
                                                        unfocusedTextColor = Color.Black,
                                                        focusedTextColor = Color.Black,
                                                        cursorColor = Color(0xFF8B5CF6)
                                                ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(Modifier.height(16.dp))

                                Button(
                                        onClick = {
                                            if (subjectName.isBlank()) return@Button

                                            addingSubject = true
                                            db.collection("users")
                                                    .document(user.uid)
                                                    .collection("subjects")
                                                    .add(
                                                            mapOf(
                                                                    "name" to subjectName.trim(),
                                                                    "totalClasses" to 0,
                                                                    "attendedClasses" to 0,
                                                                    "createdAt" to
                                                                            System.currentTimeMillis()
                                                            )
                                                    )
                                                    .addOnSuccessListener {
                                                        subjects =
                                                                subjects +
                                                                        Subject(
                                                                                id = it.id,
                                                                                name =
                                                                                        subjectName
                                                                                                .trim()
                                                                        )
                                                        subjectName = ""
                                                        addingSubject = false
                                                    }
                                                    .addOnFailureListener {
                                                        addingSubject = false
                                                        Toast.makeText(
                                                                        this@ManageSubjectsActivity,
                                                                        "Failed to add subject",
                                                                        Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                    }
                                        },
                                        enabled = !addingSubject,
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary
                                                ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) {
                                    if (addingSubject) {
                                        CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(Icons.Default.Add, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                                "ADD SUBJECT",
                                                fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (subjects.isNotEmpty()) {
                                    Spacer(Modifier.height(24.dp))

                                    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                                    Spacer(Modifier.height(16.dp))

                                    Text(
                                            text = "Your Subjects (${subjects.size})",
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(Modifier.height(12.dp))
                                }

                                LazyColumn(
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)
                                ) {
                                    items(subjects.size) { index ->
                                        val subject = subjects[index]

                                        Surface(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Row(
                                                    modifier =
                                                            Modifier.fillMaxWidth().padding(12.dp),
                                                    horizontalArrangement =
                                                            Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                            Icons.Default.Book,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(Modifier.width(12.dp))
                                                    Text(
                                                            text = subject.name,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                IconButton(
                                                        onClick = {
                                                            db.collection("users")
                                                                    .document(user.uid)
                                                                    .collection("subjects")
                                                                    .document(subject.id)
                                                                    .delete()
                                                                    .addOnSuccessListener {
                                                                        subjects =
                                                                                subjects.filter {
                                                                                    it.id !=
                                                                                            subject.id
                                                                                }
                                                                    }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Delete",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                                onClick = { finish() },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                disabledContainerColor =
                                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text(
                                    "DONE",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // 🔹 FULL PAGE LOADER
                    if (pageLoading) {
                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                        ) {
                            Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                            text = "Loading subjects...",
                                            fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }


                }
            }
        }
    }
}
