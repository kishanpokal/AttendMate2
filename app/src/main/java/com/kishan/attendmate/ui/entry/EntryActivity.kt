package com.kishan.attendmate.ui.entry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.ui.auth.LoginActivity
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.delay

class EntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendMateTheme {
                EntryScreen { nextActivity ->
                    startActivity(Intent(this, nextActivity))
                    finish()
                }
            }
        }
    }
}

@Composable
fun EntryScreen(onNavigate: (Class<*>) -> Unit) {
    // Modern, atmospheric dark theme colors
    val bgCenter = Color(0xFF1E2235)
    val bgEdge = Color(0xFF0B0D14)
    val primaryAccent = Color(0xFF6366F1) // Vibrant Indigo
    val successAccent = Color(0xFF0CFDCD) // Neon Cyan/Teal

    var startAnimation by remember { mutableStateOf(false) }

    // ─── STAGGERED ANIMATIONS ───

    // 1. Ring draws itself representing a completing clock/timeline
    val ringSweep by animateFloatAsState(
        targetValue = if (startAnimation) 360f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "ring_sweep"
    )

    // 2. Logo pops in with a spring effect
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )

    // 3. Text slides up and fades in slightly after the logo
    val textOffset by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 24.dp,
        animationSpec = tween(durationMillis = 800, delayMillis = 400, easing = EaseOutCubic),
        label = "text_offset"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 400, easing = LinearEasing),
        label = "text_alpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        // Allow animations to finish before checking auth and navigating
        delay(2200)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onNavigate(LoginActivity::class.java)
        } else {
            onNavigate(MainActivity::class.java)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(bgCenter, bgEdge),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ─── DYNAMIC LOGO ───
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale)
            ) {
                // Subtle background glow
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(primaryAccent.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                )

                AnimatedAttendLogo(
                    modifier = Modifier.fillMaxSize(),
                    sweepAngle = ringSweep,
                    primaryColor = primaryAccent,
                    accentColor = successAccent
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ─── BRANDING ───
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(y = textOffset)
                    .alpha(textAlpha)
            ) {
                Text(
                    text = "AttendMate",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Smart Attendance Tracking",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                )
            }
        }

        // ─── LOADING INDICATOR ───
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 64.dp)
                .alpha(textAlpha),
            contentAlignment = Alignment.BottomCenter
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = successAccent,
                strokeWidth = 3.dp,
                trackColor = primaryAccent.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun AnimatedAttendLogo(
    modifier: Modifier = Modifier,
    sweepAngle: Float,
    primaryColor: Color,
    accentColor: Color
) {
    Canvas(modifier = modifier) {
        val strokeThick = 6.dp.toPx()
        val strokeThin = 2.dp.toPx()
        val center = Offset(size.width / 2, size.height / 2)
        val outerRadius = size.minDimension / 2.2f

        // 1. Background Track Ring
        drawCircle(
            color = primaryColor.copy(alpha = 0.15f),
            radius = outerRadius,
            style = Stroke(width = strokeThick)
        )

        // 2. Animated Progress Ring (Represents Time/Attendance completion)
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(primaryColor, accentColor, primaryColor),
                center = center
            ),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2, outerRadius * 2),
            style = Stroke(width = strokeThick, cap = StrokeCap.Round)
        )

        // 3. Inner Decorative Ring
        drawCircle(
            color = primaryColor.copy(alpha = 0.3f),
            radius = outerRadius - 12.dp.toPx(),
            style = Stroke(width = strokeThin)
        )

        // 4. Modern Checkmark (Only drawn fully when sweep finishes)
        if (sweepAngle > 180f) {
            val checkmarkPath = Path().apply {
                // Dynamic drawing of checkmark based on remaining sweep
                val checkProgress = ((sweepAngle - 180f) / 180f).coerceIn(0f, 1f)

                val startX = center.x - outerRadius * 0.4f
                val startY = center.y + outerRadius * 0.1f

                val midX = center.x - outerRadius * 0.1f
                val midY = center.y + outerRadius * 0.4f

                val endX = center.x + outerRadius * 0.4f
                val endY = center.y - outerRadius * 0.3f

                moveTo(startX, startY)

                if (checkProgress < 0.4f) {
                    // Draw first leg
                    val segmentProgress = checkProgress / 0.4f
                    lineTo(
                        startX + (midX - startX) * segmentProgress,
                        startY + (midY - startY) * segmentProgress
                    )
                } else {
                    // Draw first leg fully, then animate second leg
                    lineTo(midX, midY)
                    val segmentProgress = (checkProgress - 0.4f) / 0.6f
                    lineTo(
                        midX + (endX - midX) * segmentProgress,
                        midY + (endY - midY) * segmentProgress
                    )
                }
            }

            drawPath(
                path = checkmarkPath,
                color = accentColor,
                style = Stroke(
                    width = strokeThick,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}