package com.kishan.attendmate.ui.entry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.R
import com.kishan.attendmate.ui.auth.LoginActivity
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

// ─────────────────────────────────────────────────────────────────────────────
//  ADVANCED PROFESSIONAL PALETTE
// ─────────────────────────────────────────────────────────────────────────────
private val BrandBlue = Color(0xFF1A64F0)
private val BrandCyan = Color(0xFF00F5FF)
private val DarkBackground = Color(0xFF060913) // Deeper, sleeker dark mode
private val LightBackground = Color(0xFFF8FAFC)
private val DarkText = Color(0xFF0F172A)
private val LightText = Color(0xFFF8FAFC)
private val MutedTextDark = Color(0xFF64748B)
private val MutedTextLight = Color(0xFF94A3B8)

class EntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendMateTheme {
                AdvancedEntryScreen { nextActivity ->
                    startActivity(Intent(this, nextActivity))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            }
        }
    }
}

@Composable
fun AdvancedEntryScreen(onNavigate: (Class<*>) -> Unit) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) DarkBackground else LightBackground
    val textColor = if (isDark) LightText else DarkText
    val mutedTextColor = if (isDark) MutedTextLight else MutedTextDark

    // Animation visibility states
    var startAnimation by remember { mutableStateOf(false) }
    var textAnimation by remember { mutableStateOf(false) }
    var loadingAnimation by remember { mutableStateOf(false) }

    // Navigation orchestration
    LaunchedEffect(Unit) {
        delay(100)
        startAnimation = true
        delay(400)
        textAnimation = true
        delay(200)
        loadingAnimation = true

        delay(2500) // Let the splash screen display for a brief moment
        val user = FirebaseAuth.getInstance().currentUser
        onNavigate(if (user == null) LoginActivity::class.java else MainActivity::class.java)
    }

    // ── Spring physics for logo pop ──
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "logo_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── Core Logo ──
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "AttendMate Logo",
                modifier = Modifier
                    .size(110.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // ── App Name & Tagline ──
            AnimatedVisibility(
                visible = textAnimation,
                enter = fadeIn(tween(800)) + slideInVertically(
                    initialOffsetY = { 30 },
                    animationSpec = tween(800, easing = EaseOut)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Attend",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Mate",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandBlue,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "SMART ATTENDANCE TRACKING",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = mutedTextColor,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── Clean Progress Indicator ──
            if (loadingAnimation) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = BrandBlue
                )
            }
        }

        // ── Footer ──
        AnimatedVisibility(
            visible = textAnimation,
            enter = fadeIn(tween(1200, delayMillis = 400)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        ) {
            Text(
                text = "MADE BY KISHAN POKAL",
                style = MaterialTheme.typography.labelSmall,
                color = mutedTextColor.copy(alpha = 0.4f),
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}