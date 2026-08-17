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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
                BiometricNeuralNexusEntryScreen { nextActivity ->
                    startActivity(Intent(this, nextActivity))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3D VECTOR MATH ENGINE
// ---------------------------------------------------------------------------
private data class Vec3(var x: Float, var y: Float, var z: Float) {
    fun rotateX(deg: Float): Vec3 {
        val rad = deg * (PI / 180f).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        return Vec3(x, y * cosA - z * sinA, y * sinA + z * cosA)
    }

    fun rotateY(deg: Float): Vec3 {
        val rad = deg * (PI / 180f).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        return Vec3(x * cosA + z * sinA, y, -x * sinA + z * cosA)
    }

    fun rotateZ(deg: Float): Vec3 {
        val rad = deg * (PI / 180f).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        return Vec3(x * cosA - y * sinA, x * sinA + y * cosA, z)
    }
}

private data class OrbitingDataPill(
    val title: String,
    val value: String,
    val orbitRadius: Float,
    val initialAngle: Float,
    val tiltAngle: Float,
    val speed: Float,
    val color: Color
)

// ---------------------------------------------------------------------------
// MAIN BIOMETRIC NEURAL NEXUS SCREEN
// ---------------------------------------------------------------------------
@Composable
fun BiometricNeuralNexusEntryScreen(onNavigate: (Class<*>) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    // Authentication states
    var isHoldingScanner by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var isAccessGranted by remember { mutableStateOf(false) }
    var isTransitioningOut by remember { mutableStateOf(false) }

    // 3D Gyroscope & drag physics
    var rotX by remember { mutableFloatStateOf(-10f) }
    var rotY by remember { mutableFloatStateOf(0f) }
    var velX by remember { mutableFloatStateOf(0f) }
    var velY by remember { mutableFloatStateOf(0f) }
    var touchPoint by remember { mutableStateOf<Offset?>(null) }

    // Color Palette
    val primaryCyan = Color(0xFF00F0FF)
    val neonGreen = Color(0xFF00FF9D)
    val deepViolet = Color(0xFF9D4EDD)
    val electricBlue = Color(0xFF3B82F6)

    // Execute Smooth Cinematic Fade & Portal Launch
    fun triggerAuthenticationSuccess() {
        if (isAccessGranted || isTransitioningOut) return
        isAccessGranted = true
        isTransitioningOut = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        coroutineScope.launch {
            delay(550) // Smooth, elegant aperture transition duration
            val user = FirebaseAuth.getInstance().currentUser
            onNavigate(if (user == null) LoginActivity::class.java else MainActivity::class.java)
        }
    }

    // Biometric Scanner Touch-Hold Loop (Strict Finger Requirement)
    LaunchedEffect(isHoldingScanner, isAccessGranted) {
        if (isAccessGranted) return@LaunchedEffect

        if (isHoldingScanner) {
            while (isHoldingScanner && scanProgress < 1f) {
                scanProgress = (scanProgress + 0.035f).coerceAtMost(1f)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(20)
            }
            if (scanProgress >= 1f) {
                triggerAuthenticationSuccess()
            }
        } else {
            // Smoothly discharge if finger is lifted prematurely
            while (!isHoldingScanner && scanProgress > 0f) {
                scanProgress = (scanProgress - 0.06f).coerceAtLeast(0f)
                delay(16)
            }
        }
    }

    // Gyro & Drag Inertia Physics Loop
    LaunchedEffect(Unit) {
        while (true) {
            if (touchPoint == null && (abs(velX) > 0.015f || abs(velY) > 0.015f)) {
                rotY += velX
                rotX += velY
                velX *= 0.94f
                velY *= 0.94f
            }
            delay(16)
        }
    }

    // Continuous Animations
    val infiniteTransition = rememberInfiniteTransition(label = "nexusLoop")
    val globalTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "globalTime"
    )
    val gridFlow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "gridFlow"
    )
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corePulse"
    )
    val scannerLaserY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "scannerLaserY"
    )
    val radarRingAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing)),
        label = "radarRingAngle"
    )

    // Smooth UI Dissolve & Aperture Wave (No jarring zoom)
    val screenAlpha by animateFloatAsState(
        targetValue = if (isTransitioningOut) 0f else 1f,
        animationSpec = tween(450, easing = LinearEasing),
        label = "screenAlpha"
    )
    val screenOffsetY by animateFloatAsState(
        targetValue = if (isTransitioningOut) -30f else 0f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "screenOffsetY"
    )
    val apertureRadius by animateFloatAsState(
        targetValue = if (isTransitioningOut) 1600f else 0f,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "apertureRadius"
    )

    val dataPills = remember {
        listOf(
            OrbitingDataPill("ATTENDANCE", "94.8% OPTIMAL", 195f, 0f, 25f, 1.0f, primaryCyan),
            OrbitingDataPill("PORTAL SYNC", "ACTIVE // LIVE", 220f, 120f, -30f, 0.85f, neonGreen),
            OrbitingDataPill("STREAK", "14 DAYS ON TRACK", 180f, 240f, 40f, 1.15f, deepViolet),
            OrbitingDataPill("CREDENTIALS", "BIOMETRIC ENCRYPTED", 205f, 60f, -15f, 0.75f, electricBlue)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030509))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        touchPoint = offset
                        velX = 0f
                        velY = 0f
                    },
                    onDragEnd = { touchPoint = null },
                    onDragCancel = { touchPoint = null },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        touchPoint = change.position
                        val dx = dragAmount.x * 0.36f
                        val dy = dragAmount.y * 0.36f
                        rotY += dx
                        rotX -= dy
                        velX = dx * 0.75f
                        velY = -dy * 0.75f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // -------------------------------------------------------------------
        // LAYER 1: 3D CYBER GRID FLOOR
        // -------------------------------------------------------------------
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCyberGridFloor(
                flow = gridFlow,
                gridColor = if (scanProgress > 0f) {
                    lerp(primaryCyan, neonGreen, scanProgress).copy(alpha = 0.22f + 0.22f * scanProgress)
                } else primaryCyan.copy(alpha = 0.20f),
                horizonY = size.height * 0.46f
            )
        }

        // -------------------------------------------------------------------
        // LAYER 2: 3D SPATIAL HOLOGRAM CORE & ORBITING DATA MODULES
        // -------------------------------------------------------------------
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = screenAlpha
                    translationY = screenOffsetY
                }
        ) {
            val center = Offset(size.width / 2f, size.height * 0.38f)

            // Radiant Ambient Glow
            val glowColor = if (isAccessGranted) neonGreen else if (scanProgress > 0f) lerp(primaryCyan, neonGreen, scanProgress) else primaryCyan
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = if (isHoldingScanner) 0.50f else 0.26f),
                        deepViolet.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = (250f + 50f * scanProgress) * corePulse
                ),
                radius = (250f + 50f * scanProgress) * corePulse,
                center = center
            )

            // 3D Concentric Gyroscopic Rings & Floating Crystal
            drawSpatialHologramCore(
                center = center,
                time = globalTime,
                rotX = rotX,
                rotY = rotY,
                pulse = corePulse,
                charge = scanProgress,
                primaryColor = glowColor,
                secondaryColor = deepViolet,
                accentColor = neonGreen
            )

            // Orbiting 3D Data Capsules
            dataPills.forEach { pill ->
                drawOrbitingDataPill(
                    pill = pill,
                    center = center,
                    time = globalTime,
                    rotX = rotX,
                    rotY = rotY,
                    textMeasurer = textMeasurer,
                    charge = scanProgress
                )
            }

            // High-Energy Laser Stream Connecting Scanner to Core
            if (scanProgress > 0f) {
                val scannerCenterY = size.height * 0.82f
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            neonGreen.copy(alpha = scanProgress),
                            glowColor.copy(alpha = scanProgress * 0.85f),
                            Color.White.copy(alpha = scanProgress)
                        ),
                        startY = scannerCenterY,
                        endY = center.y
                    ),
                    start = Offset(center.x, scannerCenterY),
                    end = center,
                    strokeWidth = 3f + 6f * scanProgress
                )

                // Particle stream shooting upward
                val numSparks = 8
                for (s in 0 until numSparks) {
                    val progressRatio = ((globalTime * 5f + s.toFloat() / numSparks) % 1f)
                    val sparkY = scannerCenterY - progressRatio * (scannerCenterY - center.y)
                    val sparkX = center.x + sin(progressRatio * 14f + s) * 10f * (1f - progressRatio)
                    drawCircle(
                        color = Color.White.copy(alpha = scanProgress * 0.9f),
                        radius = 2.2f + 2f * scanProgress,
                        center = Offset(sparkX, sparkY)
                    )
                }
            }
        }

        // -------------------------------------------------------------------
        // LAYER 3: CINEMATIC APERTURE WAVE (Smooth Transition Effect)
        // -------------------------------------------------------------------
        if (apertureRadius > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scannerCenter = Offset(size.width / 2f, size.height * 0.82f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            neonGreen.copy(alpha = 0.8f),
                            primaryCyan.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        center = scannerCenter,
                        radius = apertureRadius
                    ),
                    center = scannerCenter,
                    radius = apertureRadius
                )
            }
        }

        // -------------------------------------------------------------------
        // LAYER 4: FUTURISTIC HUD, BRAND & HIGH-TECH SCANNER PAD
        // -------------------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 38.dp)
                .graphicsLayer {
                    alpha = screenAlpha
                    translationY = screenOffsetY
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP STATUS TELEMETRY BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left telemetry badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, if (scanProgress > 0f) neonGreen.copy(alpha = 0.5f) else primaryCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (scanProgress > 0f) neonGreen else primaryCyan)
                    )
                    Text(
                        text = if (isAccessGranted) "SYSTEM // ACCESS GRANTED" else if (scanProgress > 0f) "AUTHENTICATING MATRIX..." else "ACADEMIC MATRIX // IDLE",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    )
                }

                // Right Encryption badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, deepViolet.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = primaryCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "AES-256 SECURED",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // BRAND HERO & ADVANCED BIOMETRIC SCANNER
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Holographic Brand Title with Dynamic Light Sweep
                CyberpunkBrandTitle()

                Text(
                    text = "NEXT-GENERATION ATTENDANCE INTELLIGENCE",
                    color = if (scanProgress > 0f) neonGreen.copy(alpha = 0.9f) else primaryCyan.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.2.sp
                )

                Spacer(Modifier.height(14.dp))

                // HIGH-TECH BIOMETRIC SCANNER UNIT
                AdvancedBiometricScannerPad(
                    isHolding = isHoldingScanner,
                    scanProgress = scanProgress,
                    isAccessGranted = isAccessGranted,
                    laserY = scannerLaserY,
                    radarAngle = radarRingAngle,
                    primaryCyan = primaryCyan,
                    neonGreen = neonGreen,
                    deepViolet = deepViolet,
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

                // Dynamic Authentication Telemetry Text
                Text(
                    text = when {
                        isAccessGranted -> "AUTHENTICATION COMPLETE // LAUNCHING"
                        isHoldingScanner -> "MATCHING BIOMETRIC RIDGES [${(scanProgress * 100).toInt()}%]"
                        else -> "PRESS & HOLD FINGERPRINT TO ENTER"
                    },
                    color = when {
                        isAccessGranted -> neonGreen
                        isHoldingScanner -> neonGreen
                        else -> Color.White.copy(alpha = 0.55f)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.8.sp
                )
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// ADVANCED HIGH-TECH BIOMETRIC SCANNER PAD COMPONENT
// ---------------------------------------------------------------------------
@Composable
private fun AdvancedBiometricScannerPad(
    isHolding: Boolean,
    scanProgress: Float,
    isAccessGranted: Boolean,
    laserY: Float,
    radarAngle: Float,
    primaryCyan: Color,
    neonGreen: Color,
    deepViolet: Color,
    onPressStart: () -> Unit,
    onPressRelease: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(120.dp)
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
        // High-Tech Scanner Canvas (Concentric Reticles, Ticks, Laser Line & Particles)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = (size.minDimension / 2f) - 4f

            // 1. Outer Calibration Ticks Ring
            val numTicks = 32
            for (i in 0 until numTicks) {
                val tickAngle = (i.toFloat() / numTicks) * 2 * PI.toFloat() + (radarAngle * (PI / 180f).toFloat() * 0.2f)
                val tickLen = if (i % 4 == 0) 7f else 4f
                val startR = outerRadius - tickLen
                val endR = outerRadius
                val tickColor = if (isHolding) neonGreen.copy(alpha = 0.6f) else primaryCyan.copy(alpha = 0.3f)

                drawLine(
                    color = tickColor,
                    start = Offset(center.x + startR * cos(tickAngle), center.y + startR * sin(tickAngle)),
                    end = Offset(center.x + endR * cos(tickAngle), center.y + endR * sin(tickAngle)),
                    strokeWidth = 1.5f
                )
            }

            // 2. Dual Segmented Tracking Rings
            val ringRadius = outerRadius - 10f
            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                radius = ringRadius,
                style = Stroke(width = 1.5f)
            )

            // Segmented Rotating Arc
            drawArc(
                color = if (isHolding) neonGreen.copy(alpha = 0.8f) else primaryCyan.copy(alpha = 0.5f),
                startAngle = radarAngle,
                sweepAngle = 70f,
                useCenter = false,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )
            drawArc(
                color = deepViolet.copy(alpha = 0.6f),
                startAngle = radarAngle + 180f,
                sweepAngle = 50f,
                useCenter = false,
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )

            // 3. Dynamic Charge Fill Track
            if (scanProgress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(primaryCyan, neonGreen, Color.White, primaryCyan)
                    ),
                    startAngle = -90f,
                    sweepAngle = scanProgress * 360f,
                    useCenter = false,
                    style = Stroke(width = 5.5f, cap = StrokeCap.Round)
                )

                // Glowing Leading Particle on charge tip
                val leadAngle = (-90f + scanProgress * 360f) * (PI / 180f).toFloat()
                val leadX = center.x + ringRadius * cos(leadAngle)
                val leadY = center.y + ringRadius * sin(leadAngle)
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(leadX, leadY)
                )
            }
        }

        // Inner Touch Fingerprint Pod
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isAccessGranted) neonGreen.copy(alpha = 0.55f)
                            else if (isHolding) neonGreen.copy(alpha = 0.35f)
                            else primaryCyan.copy(alpha = 0.18f),
                            Color(0xFF090E17)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            if (isAccessGranted) neonGreen
                            else if (isHolding) neonGreen
                            else primaryCyan,
                            deepViolet
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Fingerprint Icon
            Icon(
                imageVector = if (isAccessGranted) Icons.Default.CheckCircle else Icons.Default.Fingerprint,
                contentDescription = "Hold Fingerprint to Authenticate",
                tint = if (isAccessGranted) Color.White else if (isHolding) neonGreen else primaryCyan,
                modifier = Modifier.size(42.dp)
            )

            // Real Laser Scan Line Moving Across Fingerprint when Holding
            if (isHolding && !isAccessGranted) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val h = size.height
                    val w = size.width
                    val lineY = 12f + laserY * (h - 24f)

                    // Laser beam gradient
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                neonGreen.copy(alpha = 0.8f),
                                Color.White,
                                neonGreen.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(10f, lineY),
                        end = Offset(w - 10f, lineY),
                        strokeWidth = 2.5f
                    )

                    // Trailing soft glow
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                neonGreen.copy(alpha = 0.35f),
                                neonGreen.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(14f, lineY - 4f),
                        end = Offset(w - 14f, lineY - 4f),
                        strokeWidth = 5f
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 1. 3D CYBER GRID FLOOR DRAWING
// ---------------------------------------------------------------------------
private fun DrawScope.drawCyberGridFloor(
    flow: Float,
    gridColor: Color,
    horizonY: Float
) {
    val w = size.width
    val h = size.height
    val numLines = 15

    // Longitudinal lines converging into horizon center
    val vanishingX = w / 2f
    for (i in -numLines..numLines) {
        val bottomX = vanishingX + (i * w * 0.15f)
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, gridColor.copy(alpha = 0.05f), gridColor),
                startY = horizonY,
                endY = h
            ),
            start = Offset(vanishingX, horizonY),
            end = Offset(bottomX, h),
            strokeWidth = 1.2f
        )
    }

    // Transverse horizontal grid lines moving forward
    val numHoriz = 16
    for (k in 0..numHoriz) {
        val t = ((k.toFloat() + flow) / numHoriz).let { it * it }
        val lineY = horizonY + t * (h - horizonY)
        val alpha = t.coerceIn(0f, 1f) * 0.55f
        val halfSpread = (w * 0.6f) + t * (w * 0.85f)

        drawLine(
            color = gridColor.copy(alpha = alpha),
            start = Offset(vanishingX - halfSpread, lineY),
            end = Offset(vanishingX + halfSpread, lineY),
            strokeWidth = 1f + 1.5f * t
        )
    }
}

// ---------------------------------------------------------------------------
// 2. 3D SPATIAL HOLOGRAM CORE DRAWING
// ---------------------------------------------------------------------------
private fun DrawScope.drawSpatialHologramCore(
    center: Offset,
    time: Float,
    rotX: Float,
    rotY: Float,
    pulse: Float,
    charge: Float,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color
) {
    val chargeMultiplier = 1f + 0.20f * charge
    val spinSpeed = 1f + 2f * charge

    // 1. Draw 3D Multi-Axis Gyroscopic Rings
    val rings = listOf(
        Triple(125f * pulse * chargeMultiplier, rotX + time * 65f * spinSpeed, rotY + time * 50f * spinSpeed),
        Triple(105f * pulse * chargeMultiplier, -rotX - time * 55f * spinSpeed, rotY - time * 70f * spinSpeed),
        Triple(88f * pulse * chargeMultiplier, rotY + 45f + time * 75f * spinSpeed, rotX - time * 45f * spinSpeed)
    )

    rings.forEachIndexed { idx, (r, degX, degY) ->
        val ringPts = mutableListOf<Offset>()
        val segments = 36
        for (i in 0..segments) {
            val th = (i.toFloat() / segments) * 2 * PI.toFloat()
            var v = Vec3(r * cos(th), r * sin(th), 0f)
            v = v.rotateX(degX)
            v = v.rotateY(degY)

            val fov = 700f
            val scale = fov / (600f - v.z).coerceAtLeast(100f)
            ringPts.add(Offset(center.x + v.x * scale, center.y + v.y * scale))
        }

        val col = when (idx) {
            0 -> primaryColor
            1 -> secondaryColor
            else -> accentColor
        }

        for (i in 0 until ringPts.size - 1) {
            drawLine(
                color = col.copy(alpha = 0.7f),
                start = ringPts[i],
                end = ringPts[i + 1],
                strokeWidth = 2.4f + charge * 1.5f
            )
        }
    }

    // 2. Dynamic 3D Floating Polyhedral Crystal
    val prismR = 52f * pulse * chargeMultiplier
    val prismVerts = listOf(
        Vec3(0f, -prismR, 0f),
        Vec3(prismR, 0f, 0f),
        Vec3(0f, 0f, prismR),
        Vec3(-prismR, 0f, 0f),
        Vec3(0f, 0f, -prismR),
        Vec3(0f, prismR, 0f)
    )

    val prismEdges = listOf(
        0 to 1, 0 to 2, 0 to 3, 0 to 4,
        5 to 1, 5 to 2, 5 to 3, 5 to 4,
        1 to 2, 2 to 3, 3 to 4, 4 to 1
    )

    val transformedPrism = prismVerts.map { p ->
        var v = Vec3(p.x, p.y, p.z)
        v = v.rotateX(rotX + time * 45f * spinSpeed)
        v = v.rotateY(rotY + time * 40f * spinSpeed)
        v = v.rotateZ(time * 30f * spinSpeed)

        val fov = 750f
        val scale = fov / (650f - v.z).coerceAtLeast(100f)
        Offset(center.x + v.x * scale, center.y + v.y * scale)
    }

    prismEdges.forEach { (a, b) ->
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(primaryColor, Color.White, secondaryColor),
                start = transformedPrism[a],
                end = transformedPrism[b]
            ),
            start = transformedPrism[a],
            end = transformedPrism[b],
            strokeWidth = 2.2f + charge * 1.2f
        )
    }

    // Singularity Nodes
    drawCircle(Color.White, radius = (10f + 4f * charge) * pulse, center = center)
    drawCircle(accentColor.copy(alpha = 0.75f), radius = (20f + 8f * charge) * pulse, center = center)
}

// ---------------------------------------------------------------------------
// 3. 3D ORBITING DATA CAPSULES DRAWING
// ---------------------------------------------------------------------------
private fun DrawScope.drawOrbitingDataPill(
    pill: OrbitingDataPill,
    center: Offset,
    time: Float,
    rotX: Float,
    rotY: Float,
    textMeasurer: TextMeasurer,
    charge: Float
) {
    val angle = pill.initialAngle + (time * 180f / PI.toFloat()) * pill.speed * (1f + 1.2f * charge)
    val rad = angle * (PI / 180f).toFloat()

    // 3D position in orbit plane
    var pos = Vec3(
        pill.orbitRadius * cos(rad),
        (pill.orbitRadius * 0.35f) * sin(rad),
        pill.orbitRadius * sin(rad)
    )

    // Tilt orbital plane & rotate with user drag
    pos = pos.rotateZ(pill.tiltAngle)
    pos = pos.rotateX(rotX)
    pos = pos.rotateY(rotY)

    // 3D Perspective Projection
    val fov = 800f
    val cameraDist = 700f
    val denom = (cameraDist - pos.z).coerceAtLeast(100f)
    val scale = fov / denom

    val projX = center.x + pos.x * scale
    val projY = center.y + pos.y * scale

    val depthRatio = ((pos.z + pill.orbitRadius) / (2f * pill.orbitRadius)).coerceIn(0.1f, 1f)
    val alpha = (0.25f + 0.75f * depthRatio).coerceIn(0f, 1f)

    val pillW = 126f * scale
    val pillH = 34f * scale

    val topLeft = Offset(projX - pillW / 2f, projY - pillH / 2f)

    // Capsule Background
    drawRoundRect(
        color = Color(0xFF090D16).copy(alpha = alpha * 0.90f),
        topLeft = topLeft,
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2f, pillH / 2f)
    )

    // Neon Border
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(pill.color.copy(alpha = alpha * 0.85f), Color.White.copy(alpha = alpha * 0.4f))
        ),
        topLeft = topLeft,
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2f, pillH / 2f),
        style = Stroke(width = 1.3f * scale)
    )

    // Text Content
    val titleLayout = textMeasurer.measure(
        text = pill.title,
        style = TextStyle(
            color = pill.color.copy(alpha = alpha),
            fontSize = (8.5f * scale).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    )
    drawText(
        textLayoutResult = titleLayout,
        topLeft = Offset(projX - titleLayout.size.width / 2f, topLeft.y + 4f * scale)
    )

    val valLayout = textMeasurer.measure(
        text = pill.value,
        style = TextStyle(
            color = Color.White.copy(alpha = alpha),
            fontSize = (7.5f * scale).sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    )
    drawText(
        textLayoutResult = valLayout,
        topLeft = Offset(projX - valLayout.size.width / 2f, topLeft.y + 17f * scale)
    )

    // Connector Filament from Core to Capsule
    if (pos.z > 0f) {
        drawLine(
            color = pill.color.copy(alpha = alpha * 0.28f),
            start = center,
            end = Offset(projX, projY),
            strokeWidth = 1f
        )
    }
}

// ---------------------------------------------------------------------------
// 4. CYBERPUNK BRAND TITLE WITH LIGHT SWEEP
// ---------------------------------------------------------------------------
@Composable
fun CyberpunkBrandTitle() {
    val transition = rememberInfiniteTransition(label = "titleShimmer")
    val sweep by transition.animateFloat(
        initialValue = -400f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing)),
        label = "sweep"
    )

    val holographicGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.5f),
            Color(0xFF00F0FF),
            Color.White,
            Color(0xFF00FF9D),
            Color(0xFF9D4EDD),
            Color.White.copy(alpha = 0.5f)
        ),
        start = Offset(sweep, 0f),
        end = Offset(sweep + 320f, 0f)
    )

    Text(
        text = "AttendMate",
        fontSize = 46.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.2.sp,
        style = MaterialTheme.typography.displayMedium.copy(
            brush = holographicGradient
        )
    )
}