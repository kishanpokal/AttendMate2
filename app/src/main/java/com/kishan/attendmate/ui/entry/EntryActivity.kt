package com.kishan.attendmate.ui.entry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.ui.auth.LoginActivity
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
//  PREMIUM PALETTE  –  Deep obsidian + electric violet + aurora cyan
// ─────────────────────────────────────────────────────────────────────────────
private val Obsidian       = Color(0xFF030712)
private val DeepSlate      = Color(0xFF0F172A)
private val VoidPurple     = Color(0xFF0D0B1E)
private val ElectricViolet = Color(0xFF7C3AED)
private val NeonViolet     = Color(0xFFA78BFA)
private val AuroraCyan     = Color(0xFF06B6D4)
private val AuroraGreen    = Color(0xFF10B981)
private val PureWhite      = Color(0xFFFFFFFF)
private val SoftWhite      = Color(0xCCFFFFFF)
private val DimWhite       = Color(0x66FFFFFF)
private val SubtleWhite    = Color(0x22FFFFFF)

// ─────────────────────────────────────────────────────────────────────────────
//  ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────
class EntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendMateTheme {
                PremiumEntryScreen { nextActivity ->
                    startActivity(Intent(this, nextActivity))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ROOT COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PremiumEntryScreen(onNavigate: (Class<*>) -> Unit) {
    var phase by remember { mutableStateOf(0) }
    // Phase 0 = idle, 1 = orbs, 2 = logo, 3 = text, 4 = tagline, 5 = pill

    val infinite = rememberInfiniteTransition(label = "global_infinite")

    // ── Continuous time for aurora shader ──────────────────────────────────
    val time by infinite.animateFloat(
        initialValue = 0f, targetValue = TWO_PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "aurora_time"
    )

    // ── Slow mesh rotation ─────────────────────────────────────────────────
    val meshRot by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)),
        label = "mesh_rot"
    )

    // ── Orb breathing ──────────────────────────────────────────────────────
    val orbPulse by infinite.animateFloat(
        initialValue = 0.88f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            tween(4000, easing = EaseInOutSine), RepeatMode.Reverse
        ), label = "orb_pulse"
    )

    // ── Logo levitation ────────────────────────────────────────────────────
    val levitate by infinite.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(
            tween(3800, easing = EaseInOutSine), RepeatMode.Reverse
        ), label = "levitate"
    )

    // ── Ring sweep ─────────────────────────────────────────────────────────
    val ringAngle by animateFloatAsState(
        targetValue = if (phase >= 2) 360f else 0f,
        animationSpec = tween(1400, delayMillis = 100, easing = EaseInOutExpo),
        label = "ring_sweep"
    )

    // ── Logo scale spring ─────────────────────────────────────────────────
    val logoScale by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = spring(0.42f, Spring.StiffnessMediumLow),
        label = "logo_scale"
    )

    // ── Underline width ───────────────────────────────────────────────────
    val underlineW by animateDpAsState(
        targetValue = if (phase >= 3) 72.dp else 0.dp,
        animationSpec = tween(900, delayMillis = 200, easing = EaseOutExpo),
        label = "underline"
    )

    // ── Progress bar ──────────────────────────────────────────────────────
    val progressBarW by animateDpAsState(
        targetValue = if (phase >= 5) 180.dp else 0.dp,
        animationSpec = tween(1800, easing = EaseInOutCubic),
        label = "progress"
    )

    // ── Orchestrated phase sequencing ─────────────────────────────────────
    LaunchedEffect(Unit) {
        delay(100);  phase = 1   // orbs fade in
        delay(300);  phase = 2   // logo springs up
        delay(700);  phase = 3   // app name
        delay(500);  phase = 4   // tagline
        delay(400);  phase = 5   // pill + progress

        // Wait for animations to land then navigate
        delay(2200)
        val user = FirebaseAuth.getInstance().currentUser
        onNavigate(if (user == null) LoginActivity::class.java else MainActivity::class.java)
    }

    // ── Root container ────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian),
        contentAlignment = Alignment.Center
    ) {

        // Layer 0 – Aurora mesh background
        AuroraMeshBackground(time = time, meshRot = meshRot)

        // Layer 1 – Ambient glow orbs
        AmbientOrbs(visible = phase >= 1, pulse = orbPulse)

        // Layer 2 – Noise grain overlay
        GrainOverlay(time = time)

        // Layer 3 – Central content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp)
        ) {

            // — Logo cluster ————————————————————————————————————————————
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer { translationY = if (phase >= 2) levitate else 0f }
            ) {
                // Diffuse halo
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .blur(60.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    ElectricViolet.copy(alpha = 0.35f * orbPulse),
                                    Color.Transparent
                                )
                            ), CircleShape
                        )
                )

                // Inner sharper halo
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .blur(24.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    AuroraCyan.copy(alpha = 0.20f),
                                    Color.Transparent
                                )
                            ), CircleShape
                        )
                )

                // Prismatic logo mark
                PrismaticLogoMark(
                    modifier = Modifier
                        .size(108.dp)
                        .scale(logoScale),
                    sweepAngle = ringAngle,
                    time = time
                )
            }

            Spacer(Modifier.height(52.dp))

            // — App name ————————————————————————————————————————————————
            AnimatedVisibility(
                visible = phase >= 3,
                enter = fadeIn(tween(700)) + slideInVertically(tween(700, easing = EaseOutExpo)) { 40 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Title word pair with kerning
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Attend",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PureWhite,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Mate",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonViolet,
                            letterSpacing = 1.sp
                        )
                    }

                    // Gradient underline
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .width(underlineW)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(listOf(ElectricViolet, AuroraCyan))
                            )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // — Tagline ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = phase >= 4,
                enter = fadeIn(tween(800, 100))
            ) {
                Text(
                    text = "Smart Attendance · Effortless Tracking",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = DimWhite,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(56.dp))

            // — Status pill + loading bar ───────────────────────────────
            AnimatedVisibility(
                visible = phase >= 5,
                enter = fadeIn(tween(600))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Frosted pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SubtleWhite)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Pulsing status dot
                            PulsingDot(color = AuroraCyan)
                            Text(
                                text = "Initializing",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SoftWhite,
                                letterSpacing = 3.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Slim progress track
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .width(200.dp)
                            .clip(CircleShape)
                            .background(SubtleWhite)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(progressBarW)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(listOf(ElectricViolet, AuroraCyan))
                                )
                        )
                    }
                }
            }
        }

        // Layer 4 – Floating star particles
        StarParticles(active = phase >= 1, time = time)

        // Layer 5 – Version stamp
        AnimatedVisibility(
            visible = phase >= 5,
            enter = fadeIn(tween(1200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        ) {
            Text(
                text = "Made By Kishan Pokal",
                fontSize = 11.sp,
                color = DimWhite.copy(alpha = 0.4f),
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AURORA MESH BACKGROUND
//  Draws several large, softly-shifting radial gradients to emulate an
//  animated shader aurora effect.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroraMeshBackground(time: Float, meshRot: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Orb 1 – violet upper-left drift
        val o1x = w * (0.15f + 0.12f * sin(time.toDouble()).toFloat())
        val o1y = h * (0.20f + 0.08f * cos(time * 0.7).toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ElectricViolet.copy(0.28f), Color.Transparent),
                center = Offset(o1x, o1y), radius = w * 0.65f
            ), radius = w * 0.65f, center = Offset(o1x, o1y)
        )

        // Orb 2 – cyan lower-right drift
        val o2x = w * (0.80f + 0.10f * cos(time * 0.85).toFloat())
        val o2y = h * (0.72f + 0.10f * sin(time * 1.1).toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AuroraCyan.copy(0.18f), Color.Transparent),
                center = Offset(o2x, o2y), radius = w * 0.55f
            ), radius = w * 0.55f, center = Offset(o2x, o2y)
        )

        // Orb 3 – small emerald mid pulse
        val o3x = w * (0.50f + 0.06f * sin(time * 1.3).toFloat())
        val o3y = h * (0.48f + 0.05f * cos(time * 0.9).toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AuroraGreen.copy(0.10f), Color.Transparent),
                center = Offset(o3x, o3y), radius = w * 0.35f
            ), radius = w * 0.35f, center = Offset(o3x, o3y)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AMBIENT ORB ACCENTS  (blurred floating circles)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AmbientOrbs(visible: Boolean, pulse: Float) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(1200))) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top-right violet orb
            Box(
                modifier = Modifier
                    .size((280 * pulse).dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 60.dp, y = (-40).dp)
                    .blur(80.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(ElectricViolet.copy(0.30f), Color.Transparent)
                        ), CircleShape
                    )
            )
            // Bottom-left cyan orb
            Box(
                modifier = Modifier
                    .size((220 * pulse).dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-50).dp, y = 50.dp)
                    .blur(70.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(AuroraCyan.copy(0.22f), Color.Transparent)
                        ), CircleShape
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GRAIN OVERLAY
//  Subtle animated noise to give a cinematic film-grain feel.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GrainOverlay(time: Float) {
    val grainParticles = remember {
        List(300) {
            Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.06f + 0.01f)
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Shift grain position each frame for animated grain
        val offset = (time * 47f).toInt()
        grainParticles.forEachIndexed { i, (fx, fy, alpha) ->
            val nx = ((fx * size.width + offset * ((i % 7) - 3)) % size.width + size.width) % size.width
            val ny = ((fy * size.height + offset * ((i % 5) - 2)) % size.height + size.height) % size.height
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = Random.nextFloat() * 1.2f + 0.3f,
                center = Offset(nx, ny)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PRISMATIC LOGO MARK
//  Multi-ring arc with inner geometric hexagonal grid and checkmark reveal.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PrismaticLogoMark(
    modifier: Modifier,
    sweepAngle: Float,
    time: Float
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.minDimension / 2f
        val strokeW = 5.dp.toPx()
        val strokeW2 = 3.dp.toPx()
        val strokeW3 = 1.5.dp.toPx()

        // ── Outermost faint track ring ──────────────────────────────────
        drawCircle(
            color = Color.White.copy(0.06f),
            radius = outerR,
            style = Stroke(strokeW)
        )

        // ── Outer sweeping gradient arc ─────────────────────────────────
        drawArc(
            brush = Brush.sweepGradient(
                0f to ElectricViolet,
                0.4f to AuroraCyan,
                0.7f to AuroraGreen,
                1f to ElectricViolet
            ),
            startAngle = -90f + time * 8f,   // slowly rotates
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(strokeW, cap = StrokeCap.Round),
            size = Size(outerR * 2, outerR * 2),
            topLeft = Offset(cx - outerR, cy - outerR)
        )

        // ── Second inner ring (offset phase) ───────────────────────────
        val innerR = outerR * 0.72f
        drawCircle(
            color = Color.White.copy(0.04f),
            radius = innerR,
            style = Stroke(strokeW2)
        )
        if (sweepAngle > 60f) {
            val innerSweep = (sweepAngle - 60f).coerceAtMost(300f)
            drawArc(
                brush = Brush.sweepGradient(
                    0f to NeonViolet.copy(0.7f),
                    0.5f to AuroraCyan.copy(0.7f),
                    1f to NeonViolet.copy(0.7f)
                ),
                startAngle = -90f - time * 12f,
                sweepAngle = innerSweep,
                useCenter = false,
                style = Stroke(strokeW2, cap = StrokeCap.Round),
                size = Size(innerR * 2, innerR * 2),
                topLeft = Offset(cx - innerR, cy - innerR)
            )
        }

        // ── Tiny third ring ─────────────────────────────────────────────
        val tinyR = outerR * 0.44f
        if (sweepAngle > 160f) {
            val tinySweep = ((sweepAngle - 160f) / 200f * 360f).coerceAtMost(360f)
            drawArc(
                brush = Brush.sweepGradient(
                    0f to AuroraGreen.copy(0.5f),
                    1f to AuroraCyan.copy(0.5f)
                ),
                startAngle = -90f + time * 20f,
                sweepAngle = tinySweep,
                useCenter = false,
                style = Stroke(strokeW3, cap = StrokeCap.Round),
                size = Size(tinyR * 2, tinyR * 2),
                topLeft = Offset(cx - tinyR, cy - tinyR)
            )
        }

        // ── Dot nodes at ring intersections ─────────────────────────────
        if (sweepAngle > 120f) {
            val dotAlpha = ((sweepAngle - 120f) / 120f).coerceIn(0f, 1f)
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60.0) - 90.0 + time * 5.0)
                val dotX = cx + outerR * cos(angle).toFloat()
                val dotY = cy + outerR * sin(angle).toFloat()
                drawCircle(
                    color = AuroraCyan.copy(dotAlpha * 0.9f),
                    radius = 4.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
                drawCircle(
                    color = Color.White.copy(dotAlpha * 0.6f),
                    radius = 2.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
            }
        }

        // ── Central glow disc ───────────────────────────────────────────
        if (sweepAngle > 80f) {
            val discAlpha = ((sweepAngle - 80f) / 100f).coerceIn(0f, 0.18f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(AuroraCyan.copy(discAlpha * 2f), Color.Transparent),
                    center = Offset(cx, cy), radius = outerR * 0.5f
                ), radius = outerR * 0.5f, center = Offset(cx, cy)
            )
        }

        // ── Checkmark reveal (path-traced) ──────────────────────────────
        if (sweepAngle > 210f) {
            val prog = ((sweepAngle - 210f) / 150f).coerceIn(0f, 1f)
            val p1 = Offset(cx - outerR * 0.30f, cy + outerR * 0.05f)
            val p2 = Offset(cx - outerR * 0.05f, cy + outerR * 0.30f)
            val p3 = Offset(cx + outerR * 0.38f, cy - outerR * 0.22f)

            val checkPath = Path().apply {
                moveTo(p1.x, p1.y)
                if (prog < 0.45f) {
                    val t = prog / 0.45f
                    lineTo(p1.x + (p2.x - p1.x) * t, p1.y + (p2.y - p1.y) * t)
                } else {
                    lineTo(p2.x, p2.y)
                    val t = (prog - 0.45f) / 0.55f
                    lineTo(p2.x + (p3.x - p2.x) * t, p2.y + (p3.y - p2.y) * t)
                }
            }
            drawPath(
                checkPath,
                brush = Brush.linearGradient(
                    listOf(AuroraCyan, AuroraGreen),
                    start = p1, end = p3
                ),
                style = Stroke(strokeW + 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  STAR PARTICLES  –  multi-layer depth with parallax
// ─────────────────────────────────────────────────────────────────────────────
private data class Star(
    val x: Float, val y: Float,
    val speed: Float, val size: Float,
    val alpha: Float, val layer: Int
)

@Composable
fun StarParticles(active: Boolean, time: Float) {
    val stars = remember {
        List(80) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speed = Random.nextFloat() * 0.3f + 0.05f,
                size = Random.nextFloat() * 2.8f + 0.6f,
                alpha = Random.nextFloat() * 0.35f + 0.08f,
                layer = Random.nextInt(3)
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (!active) return@Canvas
        stars.forEach { s ->
            val parallax = (s.layer + 1) * 0.35f
            val cy = ((s.y * size.height - time * s.speed * 35f * parallax) % size.height + size.height) % size.height
            val twinkle = (0.5f + 0.5f * sin((time * 3f + s.x * 10f).toDouble()).toFloat())
            drawCircle(
                color = Color.White.copy(s.alpha * twinkle),
                radius = s.size * (0.7f + 0.3f * twinkle),
                center = Offset(s.x * size.width, cy)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PULSING STATUS DOT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PulsingDot(color: Color, size: Dp = 7.dp) {
    val infinite = rememberInfiniteTransition(label = "dot_pulse")
    val scale by infinite.animateFloat(
        1f, 1.6f,
        infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "dot_scale"
    )
    val alpha by infinite.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "dot_alpha"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size * scale)
                .clip(CircleShape)
                .background(color.copy(alpha * 0.25f))
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color.copy(alpha))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HELPERS
// ─────────────────────────────────────────────────────────────────────────────
private val TWO_PI = 2.0 * Math.PI

private val EaseInOutExpo: Easing = Easing { t ->
    if (t == 0f || t == 1f) t
    else if (t < 0.5f) (2f.pow(20f * t - 10f)) / 2f
    else (2f - 2f.pow(-20f * t + 10f)) / 2f
}