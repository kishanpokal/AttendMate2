package com.kishan.attendmate.ui.timetable.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
    // ✅ Directly open timetable setup screen
    TimetableSetupScreen()
}
