package com.kishan.attendmate.ui.entry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.ui.auth.LoginActivity
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

class EntryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AttendMateTheme {
                EntryScreen { next ->
                    startActivity(Intent(this, next))
                    finish()
                }
            }
        }
    }
}

@Composable
fun EntryScreen(onNavigate: (Class<*>) -> Unit) {

    var visible by remember { mutableStateOf(false) }
//    var quoteIndex by remember { mutableStateOf(0) }

    val quotes = listOf(
        "Success is the sum of small efforts repeated day in and day out.",
        "Showing up is 80% of success.",
        "Consistency is the key to excellence.",
        "Your presence today shapes your future tomorrow.",
        "Every day you attend is a step toward your goals.",
        "Attendance is the foundation of achievement.",
        "Be present. Be counted. Be successful.",
        "Excellence is not an act, but a habit of showing up."
    )

    var quoteIndex by remember {
        mutableStateOf(Random.nextInt(quotes.size))
    }
    // 1️⃣ Quote rotation (infinite loop)


    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)

            var nextIndex: Int
            do {
                nextIndex = Random.nextInt(quotes.size)
            } while (nextIndex == quoteIndex)

            quoteIndex = nextIndex
        }
    }


// 2️⃣ Navigation decision (runs once)
    LaunchedEffect(Unit) {
        visible = true
        delay(2000)

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
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                    )
                )
            )
    ) {
        // Animated Background
        AnimatedBackground()

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(800)) +
                        slideInVertically(
                            initialOffsetY = { 60 },
                            animationSpec = tween(800, easing = FastOutSlowInEasing)
                        )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // App Logo/Icon Placeholder (animated circle)
                    AnimatedLogo()

                    Spacer(modifier = Modifier.height(32.dp))

                    // Main Title
                    Text(
                        text = "AttendMate",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Subtitle with animation
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Track smart",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Attend better",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Rotating Quotes
                    AnimatedContent(
                        targetState = quoteIndex,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(600)) +
                                    slideInVertically { height -> height / 2 })
                                .togetherWith(
                                    fadeOut(animationSpec = tween(600)) +
                                            slideOutVertically { height -> -height / 2 }
                                )
                        },
                        label = "quote_animation"
                    ) { index ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Text(
                                text = "\"${quotes[index]}\"",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 24.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Loading indicator
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_animation")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = size.minDimension / 2

            rotate(rotation, pivot = Offset(centerX, centerY)) {
                // Outer ring
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF6366F1),
                            Color(0xFF8B5CF6),
                            Color(0xFFEC4899),
                            Color(0xFF6366F1)
                        )
                    ),
                    radius = radius * scale,
                    center = Offset(centerX, centerY),
                    alpha = 0.8f
                )

                // Inner circle
                drawCircle(
                    color = Color.White,
                    radius = radius * 0.7f * scale,
                    center = Offset(centerX, centerY)
                )

                // Center accent
                drawCircle(
                    color = Color(0xFF6366F1),
                    radius = radius * 0.3f * scale,
                    center = Offset(centerX, centerY)
                )
            }
        }
    }
}

@Composable
fun AnimatedBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg_animation")

    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset1"
    )

    val offset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Floating circles
        for (i in 0..5) {
            val angle1 = Math.toRadians((offset1 + i * 60).toDouble())
            val angle2 = Math.toRadians((offset2 + i * 60).toDouble())

            val x1 = (width / 2 + cos(angle1) * width / 3).toFloat()
            val y1 = (height / 2 + sin(angle1) * height / 4).toFloat()

            val x2 = (width / 2 + cos(angle2) * width / 4).toFloat()
            val y2 = (height / 2 + sin(angle2) * height / 3).toFloat()

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = Offset(x1, y1),
                    radius = 150f
                ),
                radius = 150f,
                center = Offset(x1, y1)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8B5CF6).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(x2, y2),
                    radius = 120f
                ),
                radius = 120f,
                center = Offset(x2, y2)
            )
        }

        // Subtle wave patterns
        val path = Path()
        val waveLength = width / 3
        val amplitude = 30f

        path.moveTo(0f, height / 2)

        for (x in 0..width.toInt() step 10) {
            val y = (height / 2 + amplitude * sin((x + offset1 * 5) / waveLength * 2 * Math.PI)).toFloat()
            path.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF6366F1).copy(alpha = 0.05f),
                    Color(0xFF8B5CF6).copy(alpha = 0.05f)
                )
            )
        )
    }
}