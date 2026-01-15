package com.kishan.attendmate.ui.timetable.setup

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kishan.attendmate.ui.theme.AttendMateTheme

class TimetableSetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AttendMateTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TimetableSetupRoot()
                }
            }
        }
    }
}

@Composable
private fun TimetableSetupRoot() {

    val context = LocalContext.current

    /* ---------------- Notification Permission ---------------- */

    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasNotificationPermission = granted
        }

    /* ---------------- Exact Alarm Permission ---------------- */

    val hasExactAlarmPermission = remember {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                    .canScheduleExactAlarms()
    }

    /* ---------------- UI Routing ---------------- */

    when {
        !hasNotificationPermission -> {
            NotificationPermissionScreen(
                onAllowClick = {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            )
        }

        !hasExactAlarmPermission -> {
            ExactAlarmPermissionScreen(
                onAllowClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent =
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        context.startActivity(intent)
                    }
                }
            )
        }

        else -> {
            // ✅ All permissions granted
            TimetableSetupScreen()
        }
    }
}

/* ------------------------------------------------------------------ */
/* Notification Permission UI                                          */
/* ------------------------------------------------------------------ */

@Composable
private fun NotificationPermissionScreen(
    onAllowClick: () -> Unit
) {
    PermissionScaffold(
        title = "Enable Notifications",
        description =
            "Smart Timetable needs notification permission to remind you to mark attendance on time.",
        buttonText = "Allow Notifications",
        onAllowClick = onAllowClick
    )
}

/* ------------------------------------------------------------------ */
/* Exact Alarm Permission UI                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun ExactAlarmPermissionScreen(
    onAllowClick: () -> Unit
) {
    PermissionScaffold(
        title = "Allow Exact Alarms",
        description =
            "Exact alarms are required to send attendance reminders at the correct time.",
        buttonText = "Allow Exact Alarms",
        onAllowClick = onAllowClick
    )
}

/* ------------------------------------------------------------------ */
/* Shared Permission UI                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun PermissionScaffold(
    title: String,
    description: String,
    buttonText: String,
    onAllowClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAllowClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(buttonText)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "You can change this later from system settings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
