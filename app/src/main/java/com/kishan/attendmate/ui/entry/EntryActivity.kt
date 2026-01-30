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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
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
import kotlin.math.sqrt
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

    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    // Calculate safe dimensions
    val isPortrait = screenHeightDp > screenWidthDp
    val minDimensionValue = minOf(screenWidthDp.value, screenHeightDp.value)

    // Responsive sizing based on screen size
    val logoSize = when {
        minDimensionValue < 360f -> (minDimensionValue * 0.35f).dp
        minDimensionValue < 400f -> (minDimensionValue * 0.4f).dp
        else -> (minDimensionValue * 0.45f).dp
    }

    val titleFontSize = when {
        minDimensionValue < 360f -> 32.sp
        minDimensionValue < 400f -> 40.sp
        else -> 48.sp
    }

    val subtitleFontSize = when {
        minDimensionValue < 360f -> 14.sp
        minDimensionValue < 400f -> 16.sp
        else -> 18.sp
    }

    val quoteFontSize = when {
        minDimensionValue < 360f -> 13.sp
        minDimensionValue < 400f -> 14.sp
        else -> 16.sp
    }

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

        // Scrollable main content for small screens
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isPortrait && screenHeightDp < 700.dp) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 20.dp,
                        vertical = if (isPortrait) 32.dp else 16.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top spacer for centering
                Spacer(modifier = Modifier.weight(0.5f))

                // Logo section
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
                    Box(
                        modifier = Modifier
                            .size(logoSize)
                            .padding(8.dp)
                    ) {
                        UltraAdvancedLogo(logoSize = logoSize)
                    }
                }

                Spacer(modifier = Modifier.height(if (isPortrait) 24.dp else 16.dp))

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
                            fontSize = titleFontSize,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Animated subtitle with particles
                        EnhancedSubtitle(fontSize = subtitleFontSize)

                        Spacer(modifier = Modifier.height(if (isPortrait) 32.dp else 20.dp))

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
                            HolographicCard(
                                horizontalPadding = 16.dp,
                                verticalPadding = 20.dp
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    // Quote icon
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
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
                                            fontSize = 28.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = quotes[index],
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center,
                                        color = Color.White.copy(alpha = 0.95f),
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 24.sp,
                                        letterSpacing = 0.3.sp,
                                        fontSize = quoteFontSize
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom spacer
                Spacer(modifier = Modifier.weight(0.8f))

                // Loading section at bottom
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(1500))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = if (isPortrait) 32.dp else 16.dp)
                    ) {
                        // Quantum loading indicator
                        QuantumLoadingIndicator(size = 60.dp)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Loading text with pulse
                        PulsingText()
                    }
                }
            }
        }

        // Corner accent decorations
        CornerAccents(cornerSize = (minDimensionValue * 0.08f).dp)
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
        List(80) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speedX = (Random.nextFloat() - 0.5f) * 0.0008f,
                speedY = (Random.nextFloat() - 0.5f) * 0.0008f,
                radius = Random.nextFloat() * 3f + 1f,
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
        val maxConnections = 40
        var connectionCount = 0
        for (i in particles.indices) {
            if (connectionCount >= maxConnections) break
            for (j in i + 1 until particles.size) {
                if (connectionCount >= maxConnections) break
                val p1 = particles[i]
                val p2 = particles[j]
                val dx = (p1.x - p2.x) * size.width
                val dy = (p1.y - p2.y) * size.height
                val distance = sqrt(dx * dx + dy * dy)

                val maxDistance = minOf(size.width, size.height) * 0.12f
                if (distance < maxDistance) {
                    val alpha = (1f - distance / maxDistance) * 0.2f
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
                        strokeWidth = 1f,
                        blendMode = BlendMode.Plus
                    )
                    connectionCount++
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
            val angle1 = (offset1 + i * 60).toDouble() * PI / 180.0
            val angle2 = (offset2 + i * 60).toDouble() * PI / 180.0

            val x1 = (width / 2 + cos(angle1) * width / 2.2).toFloat()
            val y1 = (height / 2 + sin(angle1) * height / 2.2).toFloat()

            val x2 = (width / 2 + cos(angle2) * width / 2.8).toFloat()
            val y2 = (height / 2 + sin(angle2) * height / 2.8).toFloat()

            val baseRadius = minOf(width, height) * 0.25f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(alpha = 0.15f),
                        Color(0xFF8B5CF6).copy(alpha = 0.1f),
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
                        Color(0xFFEC4899).copy(alpha = 0.15f),
                        Color(0xFF14B8A6).copy(alpha = 0.08f),
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
        val lineAlpha = 0.02f

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
        val baseRadius = minOf(size.width, size.height) * 0.25f

        // Outer ring
        rotate(rotation1, pivot = Offset(centerX, centerY)) {
            for (i in 0..3) {
                val radius = baseRadius * (0.9f - i * 0.15f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.03f - i * 0.005f),
                    radius = radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1.5f)
                )
            }
        }

        // Inner ring
        rotate(rotation2, pivot = Offset(centerX, centerY)) {
            for (i in 0..2) {
                val radius = baseRadius * (0.5f + i * 0.12f)
                drawOval(
                    color = Color(0xFF6366F1).copy(alpha = 0.06f - i * 0.015f),
                    topLeft = Offset(centerX - radius, centerY - radius / 2),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius),
                    style = Stroke(width = 1.5f)
                )
            }
        }
    }
}

@Composable
fun UltraAdvancedLogo(logoSize: Dp) {
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
        targetValue = 1.05f,
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
        List(10) { index ->
            OrbitingParticle(
                angle = (index * 36f),
                distance = logoSize.value * 0.45f,
                speed = 0.3f + (index % 3) * 0.1f,
                size = 3f + (index % 3) * 1.5f,
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
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val baseRadius = size.minDimension / 2

            // Outer glow layers
            for (i in 0..4) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = (0.12f - i * 0.02f) * glowIntensity),
                            Color.Transparent
                        ),
                        center = Offset(centerX, centerY),
                        radius = baseRadius * (1f + i * 0.12f)
                    ),
                    center = Offset(centerX, centerY),
                    radius = baseRadius * (1f + i * 0.12f),
                    blendMode = BlendMode.Plus
                )
            }

            // Orbiting particles
            orbitingParticles.forEach { particle ->
                val angle = (rotation * particle.speed + particle.angle).toDouble() * PI / 180.0
                val x = centerX + (cos(angle) * particle.distance).toFloat()
                val y = centerY + (sin(angle) * particle.distance).toFloat()

                // Particle glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            particle.color.copy(alpha = 0.7f),
                            particle.color.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    center = Offset(x, y),
                    radius = particle.size * 2.5f,
                    blendMode = BlendMode.Plus
                )

                // Particle core
                drawCircle(
                    color = Color.White,
                    center = Offset(x, y),
                    radius = particle.size
                )
            }

            // Rotating outer rings
            rotate(rotation, pivot = Offset(centerX, centerY)) {
                for (i in 0..2) {
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
                        radius = baseRadius * (0.85f - i * 0.15f),
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 4f),
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
                    radius = baseRadius * 0.4f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 6f)
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
                    radius = baseRadius * 0.28f,
                    center = Offset(centerX, centerY),
                    blendMode = BlendMode.Plus
                )
            }

            // Glowing core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = glowIntensity),
                        Color(0xFF6366F1).copy(alpha = 0.5f * glowIntensity),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY)
                ),
                radius = baseRadius * 0.38f,
                center = Offset(centerX, centerY),
                blendMode = BlendMode.Plus
            )

            // Center dot
            drawCircle(
                color = Color.White,
                radius = 6f,
                center = Offset(centerX, centerY)
            )
        }
    }
}

@Composable
fun AdvancedShimmerText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
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
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = fontSize,
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
                color = Color(0xFF6366F1).copy(alpha = 0.4f),
                offset = Offset(0f, 0f),
                blurRadius = 25f
            )
        ),
        fontWeight = fontWeight,
        letterSpacing = 2.sp
    )
}

@Composable
fun EnhancedSubtitle(fontSize: androidx.compose.ui.unit.TextUnit) {
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
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Track smart",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = fontSize,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color(0xFF6366F1).copy(alpha = 0.3f * glowAlpha),
                    offset = Offset(0f, 0f),
                    blurRadius = 15f
                )
            ),
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.95f),
            letterSpacing = 0.8.sp
        )

        // Animated dot
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(6.dp)
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
                fontSize = fontSize,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color(0xFFEC4899).copy(alpha = 0.3f * glowAlpha),
                    offset = Offset(0f, 0f),
                    blurRadius = 15f
                )
            ),
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.95f),
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
fun HolographicCard(
    horizontalPadding: Dp,
    verticalPadding: Dp,
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
            .padding(horizontal = horizontalPadding)
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
                                Color(0xFF6366F1).copy(alpha = 0.35f + shimmer * 0.15f),
                                Color(0xFF8B5CF6).copy(alpha = 0.25f),
                                Color(0xFFEC4899).copy(alpha = 0.35f - shimmer * 0.15f)
                            )
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
fun QuantumLoadingIndicator(size: Dp) {
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
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = this.size.width / 2
            val centerY = this.size.height / 2
            val radius = this.size.minDimension / 2

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
                        size = androidx.compose.ui.geometry.Size(currentRadius * 2, currentRadius * 2)
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
                    size = androidx.compose.ui.geometry.Size(radius * 0.8f, radius * 0.8f)
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
                radius = radius * 0.22f * scale,
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
        letterSpacing = 1.5.sp,
        fontSize = 14.sp
    )
}

@Composable
fun CornerAccents(cornerSize: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "corners")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cornerLength = cornerSize.toPx()
        val strokeWidth = 2.5f

        // Top-left
        drawLine(
            color = Color(0xFF6366F1).copy(alpha = glowAlpha),
            start = Offset(0f, cornerLength),
            end = Offset(0f, 0f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF6366F1).copy(alpha = glowAlpha),
            start = Offset(0f, 0f),
            end = Offset(cornerLength, 0f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Top-right
        drawLine(
            color = Color(0xFF8B5CF6).copy(alpha = glowAlpha),
            start = Offset(size.width - cornerLength, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF8B5CF6).copy(alpha = glowAlpha),
            start = Offset(size.width, 0f),
            end = Offset(size.width, cornerLength),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Bottom-left
        drawLine(
            color = Color(0xFFEC4899).copy(alpha = glowAlpha),
            start = Offset(0f, size.height - cornerLength),
            end = Offset(0f, size.height),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFEC4899).copy(alpha = glowAlpha),
            start = Offset(0f, size.height),
            end = Offset(cornerLength, size.height),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Bottom-right
        drawLine(
            color = Color(0xFF14B8A6).copy(alpha = glowAlpha),
            start = Offset(size.width - cornerLength, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF14B8A6).copy(alpha = glowAlpha),
            start = Offset(size.width, size.height),
            end = Offset(size.width, size.height - cornerLength),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}