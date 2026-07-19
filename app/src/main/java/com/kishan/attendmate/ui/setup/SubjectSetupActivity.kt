package com.kishan.attendmate.ui.setup

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.kishan.attendmate.ui.components.PrimaryButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.ui.theme.AttendMateTheme

class SubjectSetupActivity : ComponentActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AttendMateTheme {

                var subjectName by remember { mutableStateOf("") }
                val subjects = remember { mutableStateListOf<String>() }
                var loading by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        // Logo or Icon Area
                        Surface(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = "Add Your Subjects",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "Add all the subjects you want to track attendance for",
                            style = MaterialTheme.typography.bodyLarge,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
                        )

                        Spacer(Modifier.height(32.dp))

                        // Subject Setup Form Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                            shadowElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {

                                // Subject Name Input
                                OutlinedTextField(
                                    value = subjectName,
                                    onValueChange = { subjectName = it },
                                    label = { Text("Subject Name", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) },
                                    placeholder = { Text("e.g., Deep Learning", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Book,
                                            null,
                                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                                        focusedLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                                        unfocusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                        focusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                        cursorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(Modifier.height(16.dp))

                                PrimaryButton(
                                    onClick = {
                                        if (subjectName.isBlank()) {
                                            Toast.makeText(
                                                this@SubjectSetupActivity,
                                                "Please enter subject name",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@PrimaryButton
                                        }
                                        subjects.add(subjectName.trim())
                                        subjectName = ""
                                    },
                                    text = "ADD SUBJECT",
                                    icon = {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )

                                if (subjects.isNotEmpty()) {
                                    Spacer(Modifier.height(24.dp))

                                    Divider(
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                                        thickness = 1.dp
                                    )

                                    Spacer(Modifier.height(16.dp))

                                    Text(
                                        text = "Added Subjects (${subjects.size})",
                                        fontWeight = FontWeight.SemiBold,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    // Subject List
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                    ) {
                                        items(subjects.size) { index ->
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Book,
                                                            contentDescription = null,
                                                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(Modifier.width(12.dp))
                                                        Text(
                                                            text = subjects[index],
                                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            subjects.removeAt(index)
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Remove",
                                                            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        PrimaryButton(
                            enabled = subjects.isNotEmpty(),
                            isLoading = loading,
                            onClick = {
                                saveSubjects(subjects, onDone = {
                                    startActivity(
                                        Intent(
                                            this@SubjectSetupActivity,
                                            MainActivity::class.java
                                        )
                                    )
                                    finish()
                                }, onLoading = { loading = it })
                            },
                            text = "CONTINUE"
                        )

                        if (subjects.isEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Add at least one subject to continue",
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    private fun saveSubjects(
        subjects: List<String>,
        onDone: () -> Unit,
        onLoading: (Boolean) -> Unit
    ) {
        val user = auth.currentUser ?: return
        onLoading(true)

        val batch = db.batch()
        val userRef = db.collection("users").document(user.uid)

        subjects.forEach { name ->
            val ref = userRef.collection("subjects").document()
            batch.set(ref, mapOf(
                "name" to name,
                "totalClasses" to 0,
                "attendedClasses" to 0,
                "createdAt" to System.currentTimeMillis()
            ))
        }

        batch.update(userRef, "setupDone", true)

        batch.commit()
            .addOnSuccessListener {
                onLoading(false)
                onDone()
            }
            .addOnFailureListener {
                onLoading(false)
                Toast.makeText(
                    this,
                    "Failed to save subjects",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}