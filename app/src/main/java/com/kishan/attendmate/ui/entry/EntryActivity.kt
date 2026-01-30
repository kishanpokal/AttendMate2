
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.ui.auth.LoginActivity
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
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
    var alpha: Float,
    val pulseSpeed: Float
)

data class OrbitingParticle(
    val angle: Float,
    val distance: Float,
    val speed: Float,
    val size: Float,
    val color: Color
)

@Composable
fun EntryScreen(onNavigate: (Class<*>) -> Unit) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val minDimension = minOf(screenWidth, screenHeight)

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
            delay(5000)
            var nextIndex: Int
            do {
                nextIndex = Random.nextInt(quotes.size)
            } while (nextIndex == quoteIndex)
            quoteIndex = nextIndex
        }
    }

    // Staggered entrance animations
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
        delay(300)
        logoVisible = true
        delay(500)
        contentVisible = true
        delay(4000)

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
                        Color(0xFF0a0a1e),
                        Color(0xFF1a1a3e),
                        Color(0xFF0f0f2e),
                        Color(0xFF000000)
                    )
                )
            )
    ) {
        // Animated cosmic background
        CosmicBackground()

        // Enhanced particle system
        EnhancedParticleSystem()

        // Animated mesh gradient
        AnimatedMeshGradient()

        // Geometric grid overlay
        GeometricGrid()

        // Floating rings
        FloatingRings()

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ultra-advanced animated logo
            AnimatedVisibility(
                visible = logoVisible,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    initialScale = 0.3f
                ) + fadeIn(tween(1000, easing = EaseOutCubic))
            ) {
                UltraAdvancedLogo(minDimension = minDimension)
            }

            Spacer(modifier = Modifier.height(minDimension * 0.1f))

            // Content section
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(1200, easing = EaseOutCubic)) +
                        slideInVertically(
                            initialOffsetY = { 60 },
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
                    // Advanced shimmer title
                    AdvancedShimmerText(
                        text = "AttendMate",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = (minDimension * 0.12f).value.sp),
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Animated subtitle with particles
                    EnhancedSubtitle()

                    Spacer(modifier = Modifier.height(minDimension * 0.2f))

                    // Holographic quote card
                    AnimatedContent(
                        targetState = quoteIndex,
                        transitionSpec = {
                            (fadeIn(tween(1000, easing = EaseInOutCubic)) +
                                    slideInVertically(
                                        initialOffsetY = { it },
                                        animationSpec = tween(1000, easing = EaseOutBack)
                                    ) + scaleIn(
                                initialScale = 0.7f,
                                animationSpec = tween(1000, easing = EaseOutBack)
                            )).togetherWith(
                                fadeOut(tween(500, easing = EaseInCubic)) +
                                        slideOutVertically(
                                            targetOffsetY = { -it },
                                            animationSpec = tween(500, easing = EaseInCubic)
                                        ) + scaleOut(
                                    targetScale = 0.7f,
                                    animationSpec = tween(500, easing = EaseInCubic)
                                )
                            )
                        },
                        label = "quote_animation"
                    ) { index ->
                        HolographicCard(minDimension = minDimension) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(minDimension * 0.08f)
                            ) {
                                // Quote icon
                                Box(
                                    modifier = Modifier
                                        .size(minDimension * 0.12f)
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0xFF6366F1).copy(alpha = 0.3f),
                                                    Color.Transparent
                                                )
                                            ),
                                            shape = MaterialTheme.shapes.medium
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "💡",
                                        fontSize = (minDimension * 0.08f).value.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(minDimension * 0.05f))

                                Text(
                                    text = quotes[index],
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 28.sp,
                                    letterSpacing = 0.3.sp,
                                    fontSize = (minDimension * 0.04f).value.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(minDimension * 0.2f))

                    // Quantum loading indicator
                    QuantumLoadingIndicator(minDimension = minDimension)

                    Spacer(modifier = Modifier.height(minDimension * 0.06f))

                    // Loading text with pulse
                    PulsingText()
                }
            }
        }

        // Corner accent decorations
        CornerAccents(minDimension = minDimension)
    }
}

@Composable
fun CosmicBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic")

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Draw nebula effect
        for (i in 0..8) {
            val angle = time * 0.001f + i * (2f * PI.toFloat() / 8f)
            val radius = width * 0.4f
            val x = width / 2 + cos(angle) * radius
            val y = height / 2 + sin(angle) * radius

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4338CA).copy(alpha = 0.08f),
                        Color(0xFF6366F1).copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(x, y),
                    radius = minOf(width, height) * 0.3f
                ),
                center = Offset(x, y),
                radius = minOf(width, height) * 0.3f,
                blendMode = BlendMode.Plus
            )
        }
    }
}

@Composable
fun EnhancedParticleSystem() {
    val particles = remember {
        List(100) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speedX = (Random.nextFloat() - 0.5f) * 0.0008f,
                speedY = (Random.nextFloat() - 0.5f) * 0.0008f,
                radius = Random.nextFloat() * 4f + 1f,
                color = listOf(
                    Color(0xFF6366F1),
                    Color(0xFF8B5CF6),
                    Color(0xFFEC4899),
                    Color(0xFF3B82F6),
                    Color(0xFF14B8A6)
                ).random(),
                alpha = Random.nextFloat() * 0.7f + 0.3f,
                pulseSpeed = Random.nextFloat() * 0.02f + 0.01f
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

            // Pulse effect
            val pulse = (sin(frame * particle.pulseSpeed) + 1f) / 2f
            particle.alpha = 0.3f + pulse * 0.7f

            if (particle.x < 0f) particle.x = 1f
            if (particle.x > 1f) particle.x = 0f
            if (particle.y < 0f) particle.y = 1f
            if (particle.y > 1f) particle.y = 0f

            // Draw glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        particle.color.copy(alpha = particle.alpha * 0.8f),
                        particle.color.copy(alpha = particle.alpha * 0.3f),
                        Color.Transparent
                    )
                ),
                radius = particle.radius * 4f,
                center = Offset(particle.x * size.width, particle.y * size.height),
                blendMode = BlendMode.Plus
            )

            // Draw core
            drawCircle(
                color = particle.color.copy(alpha = particle.alpha),
                radius = particle.radius,
                center = Offset(particle.x * size.width, particle.y * size.height),
                blendMode = BlendMode.Plus
            )
        }

        // Enhanced connections with gradient
        for (i in particles.indices) {
            for (j in i + 1 until particles.size) {
                val p1 = particles[i]
                val p2 = particles[j]
                val dx = (p1.x - p2.x) * size.width
                val dy = (p1.y - p2.y) * size.height
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                val maxDistance = minOf(size.width, size.height) * 0.15f
                if (distance < maxDistance) {
                    val alpha = (1f - distance / maxDistance) * 0.25f
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                p1.color.copy(alpha = alpha),
                                p2.color.copy(alpha = alpha)
                            ),
                            start = Offset(p1.x * size.width, p1.y * size.height),
                            end = Offset(p2.x * size.width, p2.y * size.height)
                        ),
                        start = Offset(p1.x * size.width, p1.y * size.height),
                        end = Offset(p2.x * size.width, p2.y * size.height),
                        strokeWidth = 1.5f,
                        blendMode = BlendMode.Plus
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedMeshGradient() {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")

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
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        for (i in 0..5) {
            val angle1 = Math.toRadians((offset1 + i * 60).toDouble())
            val angle2 = Math.toRadians((offset2 + i * 60).toDouble())

            val x1 = (width / 2 + cos(angle1) * width / 2.2).toFloat()
            val y1 = (height / 2 + sin(angle1) * height / 2.2).toFloat()

            val x2 = (width / 2 + cos(angle2) * width / 2.8).toFloat()
            val y2 = (height / 2 + sin(angle2) * height / 2.8).toFloat()

            val baseRadius = minOf(width, height) * 0.3f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(alpha = 0.2f),
                        Color(0xFF8B5CF6).copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(x1, y1),
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = Offset(x1, y1),
                blendMode = BlendMode.Screen
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFEC4899).copy(alpha = 0.18f),
                        Color(0xFF14B8A6).copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = Offset(x2, y2),
                    radius = baseRadius * 0.85f
                ),
                radius = baseRadius * 0.85f,
                center = Offset(x2, y2),
                blendMode = BlendMode.Screen
            )
        }
    }
}

@Composable
fun GeometricGrid() {
    val infiniteTransition = rememberInfiniteTransition(label = "grid")

    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val spacing = minOf(size.width, size.height) * 0.05f
        val lineAlpha = 0.03f

        // Vertical lines
        var x = offset % spacing
        while (x < size.width) {
            drawLine(
                color = Color.White.copy(alpha = lineAlpha),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
            x += spacing
        }

        // Horizontal lines
        var y = offset % spacing
        while (y < size.height) {
            drawLine(
                color = Color.White.copy(alpha = lineAlpha),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += spacing
        }
    }
}

@Composable
fun FloatingRings() {
    val infiniteTransition = rememberInfiniteTransition(label = "rings")

    val rotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation1"
    )

    val rotation2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val baseRadius = minOf(size.width, size.height) * 0.3f

        // Outer ring
        rotate(rotation1, pivot = Offset(centerX, centerY)) {
            for (i in 0..3) {
                val radius = baseRadius * (0.9f - i * 0.18f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f - i * 0.008f),
                    radius = radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 2f)
                )
            }
        }

        // Inner ring
        rotate(rotation2, pivot = Offset(centerX, centerY)) {
            for (i in 0..2) {
                val radius = baseRadius * (0.5f + i * 0.15f)
                drawOval(
                    color = Color(0xFF6366F1).copy(alpha = 0.08f - i * 0.02f),
                    topLeft = Offset(centerX - radius, centerY - radius / 2),
                    size = Size(radius * 2, radius),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

@Composable
fun UltraAdvancedLogo(minDimension: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Orbiting particles
    val orbitingParticles = remember {
        List(12) { index ->
            OrbitingParticle(
                angle = (index * 30f),
                distance = minDimension.value * 0.4f,
                speed = 0.3f + (index % 3) * 0.1f,
                size = 4f + (index % 3) * 2f,
                color = listOf(
                    Color(0xFF6366F1),
                    Color(0xFF8B5CF6),
                    Color(0xFFEC4899),
                    Color(0xFF14B8A6)
                )[index % 4]
            )
        }
    }

    Box(
        modifier = Modifier.size(minDimension * 0.6f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val baseRadius = size.minDimension / 2

            // Outer glow
            for (i in 0..5) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = (0.15f - i * 0.02f) * glowIntensity),
                            Color.Transparent
                        ),
                        center = Offset(centerX, centerY),
                        radius = baseRadius * (1f + i * 0.15f)
                    ),
                    center = Offset(centerX, centerY),
                    radius = baseRadius * (1f + i * 0.15f),
                    blendMode = BlendMode.Plus
                )
            }

            // Orbiting particles
            orbitingParticles.forEach { particle ->
                val angle = Math.toRadians((rotation * particle.speed + particle.angle).toDouble())
                val x = centerX + (cos(angle) * particle.distance).toFloat()
                val y = centerY + (sin(angle) * particle.distance).toFloat()

                // Particle glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            particle.color.copy(alpha = 0.8f),
                            particle.color.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    center = Offset(x, y),
                    radius = particle.size * 3f,
                    blendMode = BlendMode.Plus
                )

                // Particle core
                drawCircle(
                    color = Color.White,
                    center = Offset(x, y),
                    radius = particle.size
                )
            }

            // Rotating rings
            rotate(rotation, pivot = Offset(centerX, centerY)) {
                for (i in 0..3) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF6366F1).copy(alpha = 0.9f),
                                Color(0xFF8B5CF6).copy(alpha = 0.7f),
                                Color(0xFFEC4899).copy(alpha = 0.8f),
                                Color(0xFF14B8A6).copy(alpha = 0.6f),
                                Color(0xFF6366F1).copy(alpha = 0.9f)
                            ),
                            center = Offset(centerX, centerY)
                        ),
                        radius = baseRadius * (0.9f - i * 0.18f),
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 5f),
                        blendMode = BlendMode.Plus
                    )
                }
            }

            // Counter-rotating inner ring
            rotate(-rotation * 0.8f, pivot = Offset(centerX, centerY)) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFEC4899),
                            Color(0xFF8B5CF6),
                            Color(0xFF6366F1),
                            Color(0xFF14B8A6),
                            Color(0xFFEC4899)
                        ),
                        center = Offset(centerX, centerY)
                    ),
                    radius = baseRadius * 0.45f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 8f)
                )
            }

            // Pulsing center
            scale(pulseScale, pivot = Offset(centerX, centerY)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFF6366F1).copy(alpha = 0.9f),
                            Color(0xFF8B5CF6).copy(alpha = 0.7f),
                            Color(0xFFEC4899).copy(alpha = 0.5f)
                        ),
                        center = Offset(centerX, centerY)
                    ),
                    radius = baseRadius * 0.3f,
                    center = Offset(centerX, centerY),
                    blendMode = BlendMode.Plus
                )
            }

            // Glowing core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = glowIntensity),
                        Color(0xFF6366F1).copy(alpha = 0.6f * glowIntensity),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY)
                ),
                radius = baseRadius * 0.45f,
                center = Offset(centerX, centerY),
                blendMode = BlendMode.Plus
            )

            // Center dot
            drawCircle(
                color = Color.White,
                radius = 8f,
                center = Offset(centerX, centerY)
            )
        }
    }
}

@Composable
fun AdvancedShimmerText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    val shimmer by infiniteTransition.animateFloat(
        initialValue = -400f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Text(
        text = text,
        style = style.copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF4338CA),
                    Color(0xFF6366F1),
                    Color(0xFF8B5CF6),
                    Color(0xFFEC4899),
                    Color(0xFF14B8A6),
                    Color(0xFF6366F1),
                    Color(0xFF4338CA)
                ),
                start = Offset(shimmer - 200f, shimmer - 200f),
                end = Offset(shimmer + 600f, shimmer + 600f)
            ),
            shadow = androidx.compose.ui.graphics.Shadow(
                color = Color(0xFF6366F1).copy(alpha = 0.5f),
                offset = Offset(0f, 0f),
                blurRadius = 30f
            )
        ),
        fontWeight = fontWeight,
        letterSpacing = 3.sp
    )
}

@Composable
fun EnhancedSubtitle() {
    val infiniteTransition = rememberInfiniteTransition(label = "subtitle")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Track smart",
            style = MaterialTheme.typography.titleLarge.copy(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color(0xFF6366F1).copy(alpha = 0.3f * glowAlpha),
                    offset = Offset(0f, 0f),
                    blurRadius = 20f
                )
            ),
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.95f),
            letterSpacing = 1.sp
        )

        // Animated dot
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .size(8.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = glowAlpha),
                            Color(0xFF8B5CF6).copy(alpha = glowAlpha * 0.5f),
                            Color.Transparent
                        )
                    ),
                    shape = MaterialTheme.shapes.small
                )
        )

        Text(
            text = "Attend better",
            style = MaterialTheme.typography.titleLarge.copy(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color(0xFFEC4899).copy(alpha = 0.3f * glowAlpha),
                    offset = Offset(0f, 0f),
                    blurRadius = 20f
                )
            ),
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.95f),
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun HolographicCard(
    minDimension: Dp,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "holographic")

    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = minDimension * 0.04f)
    ) {
        // Outer glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = 0.2f),
                            Color(0xFF8B5CF6).copy(alpha = 0.15f),
                            Color(0xFFEC4899).copy(alpha = 0.1f)
                        )
                    ),
                    shape = MaterialTheme.shapes.extraLarge
                )
                .padding(2.dp)
        ) {
            // Border gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF6366F1).copy(alpha = 0.4f + shimmer * 0.2f),
                                Color(0xFF8B5CF6).copy(alpha = 0.3f),
                                Color(0xFFEC4899).copy(alpha = 0.4f - shimmer * 0.2f)
                            ),
                            start = Offset(shimmer * minDimension.value, shimmer * minDimension.value),
                            end = Offset((1f - shimmer) * minDimension.value, (1f - shimmer) * minDimension.value)
                        ),
                        shape = MaterialTheme.shapes.extraLarge
                    )
                    .padding(2.dp)
            ) {
                // Inner content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0f0f2e).copy(alpha = 0.95f),
                                    Color(0xFF1a1a3e).copy(alpha = 0.9f)
                                )
                            ),
                            shape = MaterialTheme.shapes.extraLarge
                        )
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun QuantumLoadingIndicator(minDimension: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "quantum")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier.size(minDimension * 0.2f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = size.minDimension / 2

            // Outer rings
            rotate(rotation, pivot = Offset(centerX, centerY)) {
                for (i in 0..2) {
                    val currentRadius = radius * (0.7f + i * 0.15f)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF6366F1).copy(alpha = 0.8f),
                                Color(0xFF8B5CF6).copy(alpha = 0.6f),
                                Color(0xFFEC4899).copy(alpha = 0.7f),
                                Color(0xFF14B8A6).copy(alpha = 0.5f),
                                Color(0xFF6366F1).copy(alpha = 0.8f)
                            ),
                            center = Offset(centerX, centerY)
                        ),
                        startAngle = i * 120f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = 3f, cap = StrokeCap.Round),
                        topLeft = Offset(centerX - currentRadius, centerY - currentRadius),
                        size = Size(currentRadius * 2, currentRadius * 2)
                    )
                }
            }

            // Counter-rotating inner ring
            rotate(-rotation * 1.5f, pivot = Offset(centerX, centerY)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFEC4899),
                            Color(0xFF8B5CF6),
                            Color(0xFF6366F1)
                        ),
                        center = Offset(centerX, centerY)
                    ),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4f, cap = StrokeCap.Round),
                    topLeft = Offset(centerX - radius * 0.4f, centerY - radius * 0.4f),
                    size = Size(radius * 0.8f, radius * 0.8f)
                )
            }

            // Pulsing center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        Color(0xFF6366F1).copy(alpha = 0.5f),
                        Color.Transparent
                    )
                ),
                center = Offset(centerX, centerY),
                radius = radius * 0.25f * scale,
                blendMode = BlendMode.Plus
            )
        }
    }
}

@Composable
fun PulsingText() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_text")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )

    Text(
        text = "Initializing" + ".".repeat(dotCount.toInt()),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = alpha),
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp
    )
}

@Composable
fun CornerAccents(minDimension: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "corners")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cornerSize = minOf(size.width, size.height) * 0.1f
        val strokeWidth = 3f
        // Top-left
        drawLine(
            color = Color(0xFF6366F1).copy(alpha = glowAlpha),
            start = Offset(0f, cornerSize),
            end = Offset(0f, 0f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF6366F1).copy(alpha = glowAlpha),
            start = Offset(0f, 0f),
            end = Offset(cornerSize, 0f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // Top-right
        drawLine(
            color = Color(0xFF8B5CF6).copy(alpha = glowAlpha),
            start = Offset(size.width - cornerSize, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF8B5CF6).copy(alpha = glowAlpha),
            start = Offset(size.width, 0f),
            end = Offset(size.width, cornerSize),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // Bottom-left
        drawLine(
            color = Color(0xFFEC4899).copy(alpha = glowAlpha),
            start = Offset(0f, size.height - cornerSize),
            end = Offset(0f, size.height),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFEC4899).copy(alpha = glowAlpha),
            start = Offset(0f, size.height),
            end = Offset(cornerSize, size.height),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // Bottom-right
        drawLine(
            color = Color(0xFF14B8A6).copy(alpha = glowAlpha),
            start = Offset(size.width - cornerSize, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF14B8A6).copy(alpha = glowAlpha),
            start = Offset(size.width, size.height),
            end = Offset(size.width, size.height - cornerSize),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}