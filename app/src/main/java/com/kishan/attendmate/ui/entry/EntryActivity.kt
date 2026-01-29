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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

data class Particle(
    var x: Float,
    var y: Float,
    val speedX: Float,
    val speedY: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float
)

@Composable
fun EntryScreen(onNavigate: (Class<*>) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var logoVisible by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }

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

    var quoteIndex by remember { mutableStateOf(Random.nextInt(quotes.size)) }

    // Quote rotation
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            var nextIndex: Int
            do {
                nextIndex = Random.nextInt(quotes.size)
            } while (nextIndex == quoteIndex)
            quoteIndex = nextIndex
        }
    }

    // Staggered entrance animations
    LaunchedEffect(Unit) {
        delay(200)
        visible = true
        delay(400)
        logoVisible = true
        delay(600)
        contentVisible = true
        delay(2500)

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
                    colors = listOf(
                        Color(0xFF1a1a2e),
                        Color(0xFF0f0f1e),
                        Color(0xFF000000)
                    ),
                    center = Offset(0.5f, 0.3f)
                )
            )
    ) {
        // Particle system background
        ParticleSystem()

        // Animated gradient overlay
        AnimatedGradientMesh()

        // Floating geometric shapes
        FloatingShapes()

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Logo
            AnimatedVisibility(
                visible = logoVisible,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(tween(800))
            ) {
                AdvancedAnimatedLogo()
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Title with stagger effect
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(1000)) + slideInVertically(
                    initialOffsetY = { 40 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Animated title with shimmer effect
                    ShimmerText(
                        text = "AttendMate",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Subtitle with typing effect
                    AnimatedSubtitle()

                    Spacer(modifier = Modifier.height(56.dp))

                    // Glassmorphic quote card
                    AnimatedContent(
                        targetState = quoteIndex,
                        transitionSpec = {
                            (fadeIn(tween(800, easing = EaseInOutCubic)) +
                                    slideInVertically(
                                        initialOffsetY = { it / 2 },
                                        animationSpec = tween(800, easing = EaseInOutCubic)
                                    ) + scaleIn(
                                initialScale = 0.8f,
                                animationSpec = tween(800, easing = EaseInOutCubic)
                            )).togetherWith(
                                fadeOut(tween(400)) +
                                        slideOutVertically(
                                            targetOffsetY = { -it / 2 },
                                            animationSpec = tween(400)
                                        ) + scaleOut(targetScale = 0.8f, animationSpec = tween(400))
                            )
                        },
                        label = "quote_animation"
                    ) { index ->
                        GlassmorphicCard {
                            Text(
                                text = "\"${quotes[index]}\"",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(28.dp),
                                color = Color.White.copy(alpha = 0.95f),
                                fontWeight = FontWeight.Medium,
                                lineHeight = 26.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(56.dp))

                    // Advanced loading indicator
                    AdvancedLoadingIndicator()
                }
            }
        }
    }
}

@Composable
fun ParticleSystem() {
    val particles = remember {
        List(60) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speedX = (Random.nextFloat() - 0.5f) * 0.0005f,
                speedY = (Random.nextFloat() - 0.5f) * 0.0005f,
                radius = Random.nextFloat() * 3f + 1f,
                color = listOf(
                    Color(0xFF6366F1),
                    Color(0xFF8B5CF6),
                    Color(0xFFEC4899),
                    Color(0xFF3B82F6)
                ).random(),
                alpha = Random.nextFloat() * 0.6f + 0.2f
            )
        }
    }

    var frame by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            frame++
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            particle.x += particle.speedX
            particle.y += particle.speedY

            if (particle.x < 0f) particle.x = 1f
            if (particle.x > 1f) particle.x = 0f
            if (particle.y < 0f) particle.y = 1f
            if (particle.y > 1f) particle.y = 0f

            drawCircle(
                color = particle.color.copy(alpha = particle.alpha),
                radius = particle.radius,
                center = Offset(particle.x * size.width, particle.y * size.height),
                blendMode = BlendMode.Plus
            )
        }

        // Draw connections between nearby particles
        for (i in particles.indices) {
            for (j in i + 1 until particles.size) {
                val p1 = particles[i]
                val p2 = particles[j]
                val dx = (p1.x - p2.x) * size.width
                val dy = (p1.y - p2.y) * size.height
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                if (distance < 150f) {
                    val alpha = (1f - distance / 150f) * 0.15f
                    drawLine(
                        color = Color.White.copy(alpha = alpha),
                        start = Offset(p1.x * size.width, p1.y * size.height),
                        end = Offset(p2.x * size.width, p2.y * size.height),
                        strokeWidth = 1f,
                        blendMode = BlendMode.Plus
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedGradientMesh() {
    val infiniteTransition = rememberInfiniteTransition(label = "gradient_mesh")

    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset1"
    )

    val offset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        for (i in 0..3) {
            val angle1 = Math.toRadians((offset1 + i * 90).toDouble())
            val angle2 = Math.toRadians((offset2 + i * 90).toDouble())

            val x1 = (width / 2 + cos(angle1) * width / 2.5).toFloat()
            val y1 = (height / 2 + sin(angle1) * height / 2.5).toFloat()

            val x2 = (width / 2 + cos(angle2) * width / 3).toFloat()
            val y2 = (height / 2 + sin(angle2) * height / 3).toFloat()

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(alpha = 0.15f),
                        Color(0xFF8B5CF6).copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = Offset(x1, y1),
                    radius = 300f
                ),
                radius = 300f,
                center = Offset(x1, y1),
                blendMode = BlendMode.Screen
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFEC4899).copy(alpha = 0.12f),
                        Color(0xFF3B82F6).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(x2, y2),
                    radius = 250f
                ),
                radius = 250f,
                center = Offset(x2, y2),
                blendMode = BlendMode.Screen
            )
        }
    }
}

@Composable
fun FloatingShapes() {
    val infiniteTransition = rememberInfiniteTransition(label = "shapes")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        rotate(rotation, pivot = Offset(centerX, centerY)) {
            // Hexagons
            for (i in 0..2) {
                val radius = 100f + i * 80f
                drawHexagon(
                    center = Offset(centerX, centerY),
                    radius = radius,
                    color = Color.White.copy(alpha = 0.03f),
                    strokeWidth = 2f
                )
            }
        }
    }
}

fun DrawScope.drawHexagon(center: Offset, radius: Float, color: Color, strokeWidth: Float) {
    val path = Path()
    for (i in 0..6) {
        val angle = Math.toRadians(60.0 * i - 30.0)
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = strokeWidth))
}

@Composable
fun AdvancedAnimatedLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val baseRadius = size.minDimension / 2

            // Outer rotating rings
            rotate(rotation, pivot = Offset(centerX, centerY)) {
                for (i in 0..2) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF6366F1).copy(alpha = 0.8f),
                                Color(0xFF8B5CF6).copy(alpha = 0.6f),
                                Color(0xFFEC4899).copy(alpha = 0.7f),
                                Color(0xFF3B82F6).copy(alpha = 0.5f),
                                Color(0xFF6366F1).copy(alpha = 0.8f)
                            )
                        ),
                        radius = baseRadius * (0.85f - i * 0.15f),
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 4f),
                        blendMode = BlendMode.Plus
                    )
                }
            }

            // Counter-rotating inner ring
            rotate(-rotation * 0.7f, pivot = Offset(centerX, centerY)) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFEC4899),
                            Color(0xFF8B5CF6),
                            Color(0xFF6366F1),
                            Color(0xFFEC4899)
                        )
                    ),
                    radius = baseRadius * 0.4f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 6f)
                )
            }

            // Pulsing center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        Color(0xFF6366F1).copy(alpha = 0.8f),
                        Color(0xFF8B5CF6).copy(alpha = 0.6f)
                    )
                ),
                radius = baseRadius * 0.25f * pulseScale,
                center = Offset(centerX, centerY),
                blendMode = BlendMode.Plus
            )

            // Glowing core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        Color(0xFF6366F1).copy(alpha = 0.4f),
                        Color.Transparent
                    )
                ),
                radius = baseRadius * 0.35f,
                center = Offset(centerX, centerY),
                blendMode = BlendMode.Plus
            )
        }
    }
}

@Composable
fun ShimmerText(text: String, style: androidx.compose.ui.text.TextStyle, fontWeight: FontWeight) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Text(
        text = text,
        style = style.copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF6366F1),
                    Color(0xFF8B5CF6),
                    Color(0xFFEC4899),
                    Color(0xFF6366F1)
                ),
                start = Offset(shimmer, shimmer),
                end = Offset(shimmer + 500f, shimmer + 500f)
            )
        ),
        fontWeight = fontWeight,
        letterSpacing = 2.sp
    )
}

@Composable
fun AnimatedSubtitle() {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Track smart",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f)
        )

        // Animated dot
        val infiniteTransition = rememberInfiniteTransition(label = "dot")
        val dotAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_alpha"
        )

        Text(
            text = " • ",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF6366F1).copy(alpha = dotAlpha)
        )

        Text(
            text = "Attend better",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
fun GlassmorphicCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = MaterialTheme.shapes.large
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = 0.15f),
                            Color(0xFF8B5CF6).copy(alpha = 0.1f)
                        )
                    ),
                    shape = MaterialTheme.shapes.large
                )
                .padding(1.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF1a1a2e).copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.large
                    )
            ) {
                content()
            }
        }
    }
}

@Composable
fun AdvancedLoadingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = size.minDimension / 2

            rotate(rotation, pivot = Offset(centerX, centerY)) {
                for (i in 0..3) {
                    val startAngle = i * 90f
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF6366F1),
                                Color(0xFF8B5CF6),
                                Color(0xFFEC4899)
                            )
                        ),
                        startAngle = startAngle,
                        sweepAngle = 60f,
                        useCenter = false,
                        style = Stroke(width = 4f, cap = StrokeCap.Round),
                        topLeft = Offset(centerX - radius, centerY - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                    )
                }
            }
        }
    }
}