package com.kishan.attendmate.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kishan.attendmate.ui.auth.LoginActivity
import com.kishan.attendmate.ui.subjects.ManageSubjectsActivity
import com.kishan.attendmate.ui.theme.AttendMateTheme


class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AttendMateTheme {
                SettingsPage()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage() {

    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser

    if (user == null) {
        LaunchedEffect(Unit) {
            context.startActivity(Intent(context, LoginActivity::class.java))
            (context as Activity).finish()
        }
        return
    }

    var username by remember { mutableStateOf<String?>(null) }
    var isLoadingUsername by remember { mutableStateOf(true) }

    LaunchedEffect(user.uid) {
        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                username = doc.getString("username")
                isLoadingUsername = false
            }
            .addOnFailureListener {
                isLoadingUsername = false
                // Optionally show a toast or log error
            }
    }

    var showUsernameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { (context as Activity).finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            if (isLoadingUsername) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                UserProfileCard(
                    username = username ?: "User",
                    email = user.email ?: ""
                )
            }

            Spacer(Modifier.height(24.dp))

            SettingsSection("Account", Icons.Default.ManageAccounts)

            SettingsItem(
                icon = Icons.Default.Person,
                title = "Change Username",
                subtitle = "Update your display name",
                onClick = { showUsernameDialog = true }
            )

            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Change Password",
                subtitle = "Keep your account secure",
                onClick = { showPasswordDialog = true }
            )

            Spacer(Modifier.height(24.dp))

            SettingsSection("Subjects", Icons.Default.School)

            SettingsItem(
                icon = Icons.Default.Book,
                title = "Manage Subjects",
                subtitle = "Add, edit or remove subjects",
                onClick = {
                    context.startActivity(
                        Intent(context, ManageSubjectsActivity::class.java)
                    )
                }
            )

            Spacer(Modifier.height(24.dp))

            SettingsSection("Attendance Automation", Icons.Default.Schedule)

            SettingsItem(
                icon = Icons.Default.NotificationsActive,
                title = "Smart Timetable",
                subtitle = "Lecture reminders & auto attendance",
                onClick = {
                    context.startActivity(
                        Intent(
                            context,
                            com.kishan.attendmate.ui.timetable.setup.TimetableSetupActivity::class.java
                        )
                    )
                }
            )

            Spacer(Modifier.height(24.dp))

            SettingsSection("Session", Icons.Default.ExitToApp)

            SettingsItem(
                icon = Icons.Default.Logout,
                title = "Logout",
                subtitle = "Sign out from this device",
                isDestructive = true,
                onClick = { showLogoutDialog = true }
            )
        }
    }

    if (showUsernameDialog) {
        ChangeUsernameDialog(
            db = db,
            uid = user.uid,
            currentUsername = username ?: "",
            onUpdated = { newName -> username = newName },
            onDismiss = { showUsernameDialog = false }
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            auth = auth,
            db = db,
            onDismiss = { showPasswordDialog = false }
        )
    }

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                // 🔐 1. Cancel day confirmation alarm


                // 🔐 2. Sign out user
                auth.signOut()

                // 🔐 3. Redirect to login
                context.startActivity(
                    Intent(context, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

}

/* ───────────── UI COMPONENTS ───────────── */

@Composable
private fun UserProfileCard(username: String, email: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = username,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDestructive)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                null,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold)
    }
}

/* ───────────── DIALOGS ───────────── */

@Composable
private fun ChangeUsernameDialog(
    db: FirebaseFirestore,
    uid: String,
    currentUsername: String,
    onUpdated: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf(currentUsername) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Username") },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        error = null
                    },
                    label = { Text("Username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !loading,
                onClick = {
                    if (username.trim().length < 3) {
                        error = "Minimum 3 characters required"
                        return@Button
                    }
                    loading = true
                    db.collection("users")
                        .document(uid)
                        .update(
                            mapOf(
                                "username" to username.trim(),
                                "username_lower" to username.trim().lowercase(),
                                "updatedAt" to System.currentTimeMillis()
                            )
                        )
                        .addOnSuccessListener {
                            loading = false
                            onUpdated(username.trim())
                            onDismiss()
                        }
                        .addOnFailureListener {
                            loading = false
                            error = "Update failed"
                        }
                }
            ) {
                Text(if (loading) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ChangePasswordDialog(
    auth: FirebaseAuth,
    db: FirebaseFirestore,
    onDismiss: () -> Unit
) {
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    var showOldPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPass,
                    onValueChange = { oldPass = it },
                    label = { Text("Current Password") },
                    singleLine = true,
                    visualTransformation = if (showOldPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showOldPassword = !showOldPassword }) {
                            Icon(
                                imageVector = if (showOldPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showOldPassword) "Hide password" else "Show password"
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("New Password") },
                    singleLine = true,
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showNewPassword = !showNewPassword }) {
                            Icon(
                                imageVector = if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showNewPassword) "Hide password" else "Show password"
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                            Icon(
                                imageVector = if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showConfirmPassword) "Hide password" else "Show password"
                            )
                        }
                    }
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                enabled = !loading && oldPass.isNotBlank() && newPass.isNotBlank() && confirm.isNotBlank() && newPass.length >= 6,
                onClick = {
                    if (newPass != confirm) {
                        error = "Passwords do not match"
                        return@Button
                    }
                    if (newPass.length < 6) {
                        error = "Password must be at least 6 characters"
                        return@Button
                    }
                    val user = auth.currentUser ?: return@Button
                    val credential = EmailAuthProvider.getCredential(user.email!!, oldPass)
                    loading = true
                    error = null
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(newPass)
                                .addOnSuccessListener {
                                    db.collection("users")
                                        .document(user.uid)
                                        .update("passwordUpdatedAt", System.currentTimeMillis())
                                    loading = false
                                    onDismiss()
                                }
                                .addOnFailureListener {
                                    loading = false
                                    error = "Failed to update password"
                                }
                        }
                        .addOnFailureListener {
                            loading = false
                            error = "Incorrect current password"
                        }
                }
            ) {
                Text(if (loading) "Updating..." else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logout") },
        text = { Text("Are you sure you want to logout?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Logout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}