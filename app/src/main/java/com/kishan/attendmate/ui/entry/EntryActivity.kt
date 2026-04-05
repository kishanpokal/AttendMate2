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

        delay(2500) // Let the advanced animation play for a moment
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

    // ── Infinite continuous animations ──
    val infiniteTransition = rememberInfiniteTransition(label = "infinite")

    // Breathing background glow
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Sleek orbital sweep rotation
    val orbitalRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {

        // ── Breathing Ambient Glow ──
        Box(
            modifier = Modifier
                .size(250.dp)
                .scale(logoScale)
                .alpha(pulseAlpha * if (isDark) 0.15f else 0.08f)
                .blur(40.dp)
                .background(
                    brush = Brush.radialGradient(listOf(BrandCyan, Color.Transparent)),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {

            // ── Advanced Logo & Orbital Ring ──
            Box(contentAlignment = Alignment.Center) {
                // Orbital Ring
                if (loadingAnimation) {
                    Canvas(
                        modifier = Modifier
                            .size(150.dp) // Slightly larger than logo
                            .alpha(logoAlpha)
                    ) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                0.0f to Color.Transparent,
                                0.6f to BrandBlue.copy(alpha = 0.5f),
                                1.0f to BrandCyan
                            ),
                            startAngle = orbitalRotation,
                            sweepAngle = 280f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Core Logo
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "AttendMate Logo",
                    modifier = Modifier
                        .size(110.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── App Name & Tagline ──
            AnimatedVisibility(
                visible = textAnimation,
                enter = fadeIn(tween(800)) + slideInVertically(
                    initialOffsetY = { 30 },
                    animationSpec = tween(800, easing = EaseOutExpo)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Attend",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Mate",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandBlue,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "SMART ATTENDANCE TRACKING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = mutedTextColor,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(60.dp))

            // ── Status Pill (Advanced loading indicator) ──
            AnimatedVisibility(
                visible = loadingAnimation,
                enter = fadeIn(tween(600))
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isDark) Color(0xFF111827) else Color(0xFFE2E8F0))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pulsing dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .alpha(pulseAlpha)
                                .background(BrandCyan)
                        )
                        Text(
                            text = "SYSTEM INITIALIZING...",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) BrandCyan else BrandBlue,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
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
                fontSize = 10.sp,
                color = mutedTextColor.copy(alpha = 0.4f),
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CUSTOM EASING FUNCTIONS (Fixed Kotlin Math)
// ─────────────────────────────────────────────────────────────────────────────
private val EaseOutExpo: Easing = Easing { t ->
    if (t == 1f) 1f else 1f - 2f.pow(-10f * t)
}

private val EaseInOutSine: Easing = Easing { t ->
    -(cos(PI * t).toFloat() - 1f) / 2f
}