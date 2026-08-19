package com.kishan.attendmate.ui.entry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.ui.auth.LoginActivity
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

class EntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendMateTheme {
                MobiusFluxEntryScreen { nextActivity ->
                    startActivity(Intent(this, nextActivity))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3D VECTOR MATH
// ---------------------------------------------------------------------------
private data class Vec3D(var x: Float, var y: Float, var z: Float) {
    fun rotateX(deg: Float): Vec3D {
        val rad = deg * (PI / 180f).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        return Vec3D(x, y * cosA - z * sinA, y * sinA + z * cosA)
    }

    fun rotateY(deg: Float): Vec3D {
        val rad = deg * (PI / 180f).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        return Vec3D(x * cosA + z * sinA, y, -x * sinA + z * cosA)
    }

    fun rotateZ(deg: Float): Vec3D {
        val rad = deg * (PI / 180f).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        return Vec3D(x * cosA - y * sinA, x * sinA + y * cosA, z)
    }
}

private data class CosmicParticle(
    var pos: Vec3D,
    val speed: Float,
    val size: Float,
    val alphaBase: Float,
    val phase: Float,
    val color: Color
)

// ---------------------------------------------------------------------------
// MAIN MOBIUS FLUX ENTRY SCREEN (NO CUBES, NO GREEN - LUXURY SAPPHIRE & AMETHYST)
// ---------------------------------------------------------------------------
@Composable
fun MobiusFluxEntryScreen(onNavigate: (Class<*>) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // State
    var isHoldingScanner by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var isAccessGranted by remember { mutableStateOf(false) }
    var isTransitioning by remember { mutableStateOf(false) }

    // 3D Gyro Tilt Physics
    var rotX by remember { mutableFloatStateOf(-12f) }
    var rotY by remember { mutableFloatStateOf(0f) }
    var velX by remember { mutableFloatStateOf(0f) }
    var velY by remember { mutableFloatStateOf(0f) }
    var touchActive by remember { mutableStateOf(false) }

    // Color Palette: Electric Cyan, Royal Sapphire, Radiant Amethyst, Pure Platinum (Zero Green)
    val colorCyan = Color(0xFF00E5FF)
    val colorSapphire = Color(0xFF3D5AFE)
    val colorAmethyst = Color(0xFFD500F9)
    val colorPlatinum = Color(0xFFF1F5F9)

    // Trigger Entrance
    fun triggerSuccess() {
        if (isAccessGranted || isTransitioning) return
        isAccessGranted = true
        isTransitioning = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        coroutineScope.launch {
            delay(580)
            val user = FirebaseAuth.getInstance().currentUser
            onNavigate(if (user == null) LoginActivity::class.java else MainActivity::class.java)
        }
    }

    // Biometric Scanner Touch-Hold Loop (Strict Requirement)
    LaunchedEffect(isHoldingScanner, isAccessGranted) {
        if (isAccessGranted) return@LaunchedEffect

        if (isHoldingScanner) {
            while (isHoldingScanner && scanProgress < 1f) {
                scanProgress = (scanProgress + 0.036f).coerceAtMost(1f)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(20)
            }
            if (scanProgress >= 1f) {
                triggerSuccess()
            }
        } else {
            // Smoothly drain back to 0 if released early
            while (!isHoldingScanner && scanProgress > 0f) {
                scanProgress = (scanProgress - 0.065f).coerceAtLeast(0f)
                delay(16)
            }
        }
    }

    // 3D Inertia Momentum Loop
    LaunchedEffect(Unit) {
        while (true) {
            if (!touchActive && (abs(velX) > 0.01f || abs(velY) > 0.01f)) {
                rotY += velX
                rotX += velY
                velX *= 0.94f
                velY *= 0.94f
            }
            delay(16)
        }
    }

    // Continuous Animation Loops
    val infiniteTransition = rememberInfiniteTransition(label = "fluxLoop")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "time"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val fluxArcAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "fluxArcAngle"
    )

    // Smooth Screen Fade Out
    val screenAlpha by animateFloatAsState(
        targetValue = if (isTransitioning) 0f else 1f,
        animationSpec = tween(500, easing = LinearEasing),
        label = "screenAlpha"
    )
    val screenOffsetY by animateFloatAsState(
        targetValue = if (isTransitioning) -25f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "screenOffsetY"
    )
    val bloomRadius by animateFloatAsState(
        targetValue = if (isTransitioning) 1800f else 0f,
        animationSpec = tween(580, easing = FastOutSlowInEasing),
        label = "bloomRadius"
    )

    // Floating Ambient Cosmic Flux Particles
    val cosmicParticles = remember {
        List(45) {
            val r = 270f * (0.35f + 0.65f * Random.nextFloat())
            val theta = Random.nextFloat() * 2 * PI.toFloat()
            val phi = acos(Random.nextFloat() * 2f - 1f)
            val pColor = if (Random.nextBoolean()) colorCyan else if (Random.nextBoolean()) colorAmethyst else colorSapphire
            CosmicParticle(
                pos = Vec3D(
                    r * sin(phi) * cos(theta),
                    r * sin(phi) * sin(theta),
                    r * cos(phi)
                ),
                speed = 0.5f + Random.nextFloat() * 0.9f,
                size = 1.8f + Random.nextFloat() * 2.5f,
                alphaBase = 0.25f + Random.nextFloat() * 0.55f,
                phase = Random.nextFloat() * 2 * PI.toFloat(),
                color = pColor
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060913),
                        Color(0xFF03050A),
                        Color(0xFF010205)
                    )
                )
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        touchActive = true
                        velX = 0f
                        velY = 0f
                    },
                    onDragEnd = { touchActive = false },
                    onDragCancel = { touchActive = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x * 0.35f
                        val dy = dragAmount.y * 0.35f
                        rotY += dx
                        rotX -= dy
                        velX = dx * 0.72f
                        velY = -dy * 0.72f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // -------------------------------------------------------------------
        // 1. VOLUMETRIC BACKGROUND BLOOM & 3D LIQUID MOBIUS FLUX RIBBON
        // -------------------------------------------------------------------
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = screenAlpha
                    translationY = screenOffsetY
                }
        ) {
            val center = Offset(size.width / 2f, size.height * 0.40f)

            // Deep Ambient Soft Luminescence (Cyan & Sapphire Core)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colorCyan.copy(alpha = if (isHoldingScanner) 0.35f else 0.18f),
                        colorSapphire.copy(alpha = 0.12f),
                        colorAmethyst.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = (290f + 60f * scanProgress) * pulse
                ),
                radius = (290f + 60f * scanProgress) * pulse,
                center = center
            )

            // Draw Floating Ambient Cosmic Micro-Particles
            cosmicParticles.forEach { p ->
                var pos = Vec3D(p.pos.x, p.pos.y, p.pos.z)
                pos = pos.rotateX(rotX + (time * 180f / PI.toFloat()) * 0.1f * p.speed)
                pos = pos.rotateY(rotY + (time * 180f / PI.toFloat()) * 0.15f * p.speed)

                val fov = 750f
                val scale = fov / (650f - pos.z).coerceAtLeast(100f)
                val projX = center.x + pos.x * scale
                val projY = center.y + pos.y * scale

                val depth = ((pos.z + 270f) / 540f).coerceIn(0.1f, 1f)
                val pAlpha = p.alphaBase * depth * (0.6f + 0.4f * sin(time * 3f + p.phase))

                drawCircle(
                    color = p.color.copy(alpha = pAlpha.coerceIn(0f, 1f)),
                    radius = p.size * scale,
                    center = Offset(projX, projY)
                )
            }

            // Draw 3D Continuous Parametric Möbius Ribbon of Light
            drawLiquidMobiusRibbon(
                center = center,
                time = time,
                rotX = rotX,
                rotY = rotY,
                pulse = pulse,
                charge = scanProgress,
                cyan = colorCyan,
                sapphire = colorSapphire,
                amethyst = colorAmethyst,
                platinum = colorPlatinum
            )
        }

        // -------------------------------------------------------------------
        // 2. RADIAL SAPPHIRE BLOOM TRANSITION
        // -------------------------------------------------------------------
        if (bloomRadius > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scannerCenter = Offset(size.width / 2f, size.height * 0.82f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            colorCyan.copy(alpha = 0.75f),
                            colorSapphire.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = scannerCenter,
                        radius = bloomRadius
                    ),
                    center = scannerCenter,
                    radius = bloomRadius
                )
            }
        }

        // -------------------------------------------------------------------
        // 3. MINIMAL BRAND HERO & MAGNETIC FLUX FINGERPRINT SCANNER
        // -------------------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 46.dp)
                .graphicsLayer {
                    alpha = screenAlpha
                    translationY = screenOffsetY
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Subtle Category Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, colorCyan.copy(alpha = 0.15f), RoundedCornerShape(30.dp))
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "ATTENDMATE // SPATIAL OS",
                    color = colorCyan.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.weight(1f))

            // BRAND HERO & MAGNETIC SCANNER
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Minimal Brand Title with Sapphire & Cyan Liquid Shimmer
                MobiusBrandTitle()

                Text(
                    text = "Seamless Academic Intelligence",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                )

                Spacer(Modifier.height(18.dp))

                // MAGNETIC FLUX BIOMETRIC SCANNER (Zero Green, Dual Arc Fusion)
                MagneticFluxBiometricPad(
                    isHolding = isHoldingScanner,
                    scanProgress = scanProgress,
                    isAccessGranted = isAccessGranted,
                    arcAngle = fluxArcAngle,
                    cyan = colorCyan,
                    sapphire = colorSapphire,
                    amethyst = colorAmethyst,
                    onPressStart = {
                        if (!isAccessGranted) {
                            isHoldingScanner = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onPressRelease = {
                        isHoldingScanner = false
                    }
                )

                // Minimal Dynamic Prompt Text
                Text(
                    text = when {
                        isAccessGranted -> "AUTHENTICATED"
                        isHoldingScanner -> "RESONATING · ${(scanProgress * 100).toInt()}%"
                        else -> "TOUCH & HOLD TO ENTER"
                    },
                    color = when {
                        isAccessGranted -> colorCyan
                        isHoldingScanner -> colorCyan
                        else -> Color.White.copy(alpha = 0.45f)
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.6.sp
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// 1. 3D CONTINUOUS PARAMETRIC MOBIUS LIQUID RIBBON (NO CUBES!)
// ---------------------------------------------------------------------------
private fun DrawScope.drawLiquidMobiusRibbon(
    center: Offset,
    time: Float,
    rotX: Float,
    rotY: Float,
    pulse: Float,
    charge: Float,
    cyan: Color,
    sapphire: Color,
    amethyst: Color,
    platinum: Color
) {
    val rMajor = 105f * pulse * (1f + 0.15f * charge)
    val rMinor = 42f * pulse
    val numRibbonSteps = 72
    val spinSpeed = 1f + 1.6f * charge

    val ribbonPointsA = mutableListOf<Offset>()
    val ribbonPointsB = mutableListOf<Offset>()
    val depths = mutableListOf<Float>()

    for (i in 0..numRibbonSteps) {
        val u = (i.toFloat() / numRibbonSteps) * 2 * PI.toFloat()
        val wave = sin(u * 3f + time * 3f * spinSpeed) * 12f

        // Parametric 3D Möbius Curve Math
        val edgeWidth = (rMinor + wave)
        val cosHalfU = cos(u / 2f)
        val sinHalfU = sin(u / 2f)

        // Strand A (+edgeWidth)
        var pA = Vec3D(
            (rMajor + edgeWidth * cosHalfU) * cos(u),
            (rMajor + edgeWidth * cosHalfU) * sin(u),
            edgeWidth * sinHalfU
        )

        // Strand B (-edgeWidth)
        var pB = Vec3D(
            (rMajor - edgeWidth * cosHalfU) * cos(u),
            (rMajor - edgeWidth * cosHalfU) * sin(u),
            -edgeWidth * sinHalfU
        )

        // Apply 3D Euler rotations & touch parallax
        pA = pA.rotateX(rotX + time * 35f * spinSpeed)
        pA = pA.rotateY(rotY + time * 45f * spinSpeed)
        pA = pA.rotateZ(time * 25f * spinSpeed)

        pB = pB.rotateX(rotX + time * 35f * spinSpeed)
        pB = pB.rotateY(rotY + time * 45f * spinSpeed)
        pB = pB.rotateZ(time * 25f * spinSpeed)

        val fov = 750f
        val scaleA = fov / (650f - pA.z).coerceAtLeast(100f)
        val scaleB = fov / (650f - pB.z).coerceAtLeast(100f)

        ribbonPointsA.add(Offset(center.x + pA.x * scaleA, center.y + pA.y * scaleA))
        ribbonPointsB.add(Offset(center.x + pB.x * scaleB, center.y + pB.y * scaleB))
        depths.add(((pA.z + pB.z) / 2f + rMajor) / (2f * rMajor))
    }

    // Draw Continuous Flowing Ribbon Strands with Dynamic Gradients
    for (i in 0 until numRibbonSteps) {
        val t = i.toFloat() / numRibbonSteps
        val d = depths[i].coerceIn(0.15f, 1f)

        val strandColor = when {
            t < 0.33f -> lerp(cyan, sapphire, t * 3f)
            t < 0.66f -> lerp(sapphire, amethyst, (t - 0.33f) * 3f)
            else -> lerp(amethyst, cyan, (t - 0.66f) * 3f)
        }

        // Draw Strand A line
        drawLine(
            color = strandColor.copy(alpha = 0.75f * d),
            start = ribbonPointsA[i],
            end = ribbonPointsA[i + 1],
            strokeWidth = 2.2f * d + charge * 1.5f
        )

        // Draw Strand B line
        drawLine(
            color = strandColor.copy(alpha = 0.55f * d),
            start = ribbonPointsB[i],
            end = ribbonPointsB[i + 1],
            strokeWidth = 1.8f * d + charge * 1.2f
        )

        // Draw cross-lattice connecting filaments every few steps
        if (i % 3 == 0) {
            drawLine(
                color = platinum.copy(alpha = 0.30f * d),
                start = ribbonPointsA[i],
                end = ribbonPointsB[i],
                strokeWidth = 1.2f
            )
        }
    }

    // Moving Quantum Energy Nodes Traveling Along Ribbon
    val numEnergyNodes = 4
    for (k in 0 until numEnergyNodes) {
        val nodeT = ((time * 0.4f * spinSpeed + k.toFloat() / numEnergyNodes) % 1f)
        val stepIdx = (nodeT * (numRibbonSteps - 1)).toInt().coerceIn(0, numRibbonSteps - 1)
        val pt = ribbonPointsA[stepIdx]

        drawCircle(
            color = Color.White,
            radius = 3.5f + 2f * charge,
            center = pt
        )
        drawCircle(
            color = cyan.copy(alpha = 0.6f),
            radius = 8f + 4f * charge,
            center = pt
        )
    }

    // Central Radiant Singularity Node
    drawCircle(
        color = Color.White.copy(alpha = 0.9f),
        radius = (9f + 4f * charge) * pulse,
        center = center
    )
    drawCircle(
        color = cyan.copy(alpha = 0.5f),
        radius = (22f + 8f * charge) * pulse,
        center = center
    )
}

// ---------------------------------------------------------------------------
// 2. MAGNETIC FLUX BIOMETRIC SCANNER (ZERO GREEN - DUAL ARC RESONANCE)
// ---------------------------------------------------------------------------
@Composable
private fun MagneticFluxBiometricPad(
    isHolding: Boolean,
    scanProgress: Float,
    isAccessGranted: Boolean,
    arcAngle: Float,
    cyan: Color,
    sapphire: Color,
    amethyst: Color,
    onPressStart: () -> Unit,
    onPressRelease: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(115.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressStart()
                        val released = tryAwaitRelease()
                        onPressRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = (size.minDimension / 2f) - 6f

            // 1. Dual Counter-Rotating Magnetic Flux Arcs (Idle / Active)
            if (scanProgress == 0f) {
                // Arc 1: Cyan
                drawArc(
                    brush = Brush.sweepGradient(listOf(cyan.copy(alpha = 0f), cyan, cyan.copy(alpha = 0f))),
                    startAngle = arcAngle,
                    sweepAngle = 100f,
                    useCenter = false,
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )
                // Arc 2: Amethyst (Counter-rotating)
                drawArc(
                    brush = Brush.sweepGradient(listOf(amethyst.copy(alpha = 0f), amethyst, amethyst.copy(alpha = 0f))),
                    startAngle = -arcAngle + 180f,
                    sweepAngle = 100f,
                    useCenter = false,
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )
            }

            // 2. Subtle Outer Ambient Track
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.5f)
            )

            // 3. Magnetic Flux Charging Arc (When Holding)
            if (scanProgress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(sapphire, cyan, Color.White, amethyst, sapphire)
                    ),
                    startAngle = -90f,
                    sweepAngle = scanProgress * 360f,
                    useCenter = false,
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                )

                // Glowing Leading Particle Node
                val leadAngle = (-90f + scanProgress * 360f) * (PI / 180f).toFloat()
                val leadX = center.x + outerRadius * cos(leadAngle)
                val leadY = center.y + outerRadius * sin(leadAngle)
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(leadX, leadY)
                )
                drawCircle(
                    color = cyan.copy(alpha = 0.6f),
                    radius = 8f,
                    center = Offset(leadX, leadY)
                )

                // Magnetic Flux Particle Filaments Streaming Inward
                val numFilaments = 6
                for (f in 0 until numFilaments) {
                    val fAngle = (f.toFloat() / numFilaments) * 2 * PI.toFloat() + arcAngle * 0.05f
                    val filamentDist = outerRadius * (1f - scanProgress * 0.4f)
                    val fx = center.x + filamentDist * cos(fAngle)
                    val fy = center.y + filamentDist * sin(fAngle)
                    drawCircle(
                        color = Color.White.copy(alpha = scanProgress * 0.8f),
                        radius = 2f,
                        center = Offset(fx, fy)
                    )
                }
            }
        }

        // Inner Titanium-Glass Touch Pod
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isAccessGranted) cyan.copy(alpha = 0.4f)
                            else if (isHolding) sapphire.copy(alpha = 0.28f)
                            else Color.White.copy(alpha = 0.04f),
                            Color(0xFF090D18).copy(alpha = 0.9f)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            if (isAccessGranted) cyan
                            else if (isHolding) cyan
                            else Color.White.copy(alpha = 0.20f),
                            if (isHolding) amethyst else sapphire.copy(alpha = 0.4f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isAccessGranted) Icons.Default.Check else Icons.Default.Fingerprint,
                contentDescription = "Fingerprint",
                tint = if (isAccessGranted) Color.White
                else if (isHolding) cyan
                else Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// MINIMAL BRAND TITLE WITH SAPPHIRE-CYAN LIQUID SHIMMER
// ---------------------------------------------------------------------------
@Composable
fun MobiusBrandTitle() {
    val transition = rememberInfiniteTransition(label = "titleShimmer")
    val sweep by transition.animateFloat(
        initialValue = -300f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing)),
        label = "sweep"
    )

    val sapphireShimmer = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.75f),
            Color.White,
            Color(0xFF00E5FF),
            Color(0xFF8C9EFF),
            Color.White,
            Color.White.copy(alpha = 0.75f)
        ),
        start = Offset(sweep, 0f),
        end = Offset(sweep + 260f, 0f)
    )

    Text(
        text = "AttendMate",
        fontSize = 42.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        style = MaterialTheme.typography.headlineLarge.copy(
            brush = sapphireShimmer
        )
    )
}