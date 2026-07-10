package com.kishan.attendmate.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.R
import com.kishan.attendmate.ui.auth.LoginActivity
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()

        setContent {
            AttendMateTheme {

                val nameAlpha = remember { Animatable(0f) }
                val sloganAlpha = remember { Animatable(0f) }

                LaunchedEffect(Unit) {
                    // --- ANIMATION SEQUENCE (UNCHANGED) ---
                    delay(300)
                    nameAlpha.animateTo(1f, tween(1000))

                    delay(300)
                    sloganAlpha.animateTo(1f, tween(1000))

                    delay(400)

                    // --- AUTH DECISION ---
                    val user = auth.currentUser

                    val nextActivity = if (user != null && user.isEmailVerified) {
                        MainActivity::class.java
                    } else {
                        LoginActivity::class.java
                    }

                    startActivity(
                        Intent(this@SplashActivity, nextActivity)
                    )
                    finish()
                }

                SplashUI(
                    nameAlpha = nameAlpha.value,
                    sloganAlpha = sloganAlpha.value
                )
            }
        }
    }
}

@Composable
private fun SplashUI(
    nameAlpha: Float,
    sloganAlpha: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "AttendMate",
                color = Color.White,
                modifier = Modifier.graphicsLayer(alpha = nameAlpha)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Track smart. Attend better.",
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.graphicsLayer(alpha = sloganAlpha)
            )
        }
    }
}
