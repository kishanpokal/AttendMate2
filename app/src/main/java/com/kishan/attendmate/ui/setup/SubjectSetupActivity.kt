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
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF6366F1),
                                    Color(0xFF8B5CF6),
                                    Color(0xFFA855F7)
                                )
                            )
                        )
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
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = "Add Your Subjects",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 32.sp
                        )

                        Text(
                            text = "Add all the subjects you want to track attendance for",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
                        )

                        Spacer(Modifier.height(32.dp))

                        // Subject Setup Form Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White,
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
                                    label = { Text("Subject Name", color = Color(0xFF6B7280)) },
                                    placeholder = { Text("e.g., Deep Learning", color = Color(0xFF9CA3AF)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Book,
                                            null,
                                            tint = Color(0xFF8B5CF6)
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    colors = OutlinedTextFieldDefaults.colors(
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
                                        if (subjectName.isBlank()) {
                                            Toast.makeText(
                                                this@SubjectSetupActivity,
                                                "Please enter subject name",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@Button
                                        }
                                        subjects.add(subjectName.trim())
                                        subjectName = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF8B5CF6)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "ADD SUBJECT",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (subjects.isNotEmpty()) {
                                    Spacer(Modifier.height(24.dp))

                                    Divider(
                                        color = Color(0xFF000000),
                                        thickness = 1.dp
                                    )

                                    Spacer(Modifier.height(16.dp))

                                    Text(
                                        text = "Added Subjects (${subjects.size})",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF374151),
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
                                                color = Color(0xFFF3F4F6)
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
                                                            tint = Color(0xFF8B5CF6),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(Modifier.width(12.dp))
                                                        Text(
                                                            text = subjects[index],
                                                            fontSize = 15.sp,
                                                            color = Color(0xFF374151),
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
                                                            tint = Color(0xFF6B7280),
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

                        // Continue Button
                        Button(
                            enabled = subjects.isNotEmpty() && !loading,
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = Color(0xFF8B5CF6)
                                )
                            } else {
                                Text(
                                    "CONTINUE",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8B5CF6)
                                )
                            }
                        }

                        if (subjects.isEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Add at least one subject to continue",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
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