package com.kishan.attendmate.ui.settings

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// ---------------------------------------------------------------------------
// 3D VECTOR MATH ENGINE
// ---------------------------------------------------------------------------
private data class Vec3D(var x: Float, var y: Float, var z: Float) {
    fun rotateX(deg: Float): Vec3D {
        val rad = deg * (PI / 180.0).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        return Vec3D(x, y * cosA - z * sinA, y * sinA + z * cosA)
    }

    fun rotateY(deg: Float): Vec3D {
        val rad = deg * (PI / 180.0).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        return Vec3D(x * cosA + z * sinA, y, -x * sinA + z * cosA)
    }

    fun rotateZ(deg: Float): Vec3D {
        val rad = deg * (PI / 180.0).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        return Vec3D(x * cosA - y * sinA, x * sinA + y * cosA, z)
    }

    fun project(centerX: Float, centerY: Float, fov: Float = 600f, cameraDist: Float = 550f): Pair<Offset, Float> {
        val zEff = (z + cameraDist).coerceAtLeast(50f)
        val scale = fov / zEff
        val sx = centerX + x * scale
        val sy = centerY + y * scale
        return Pair(Offset(sx, sy), scale)
    }
}

private enum class NodeSyncState {
    PENDING,
    ACTIVE,
    COMPLETED
}

private data class SubjectOrbitalNode(
    val name: String,
    val initialAngle: Float,
    val orbitRadius: Float,
    val tiltX: Float,
    val tiltZ: Float,
    var state: NodeSyncState = NodeSyncState.PENDING,
    var progress: Float = 0f,
    var statusMessage: String = "",
    var recordsCount: Int = 0
)

private data class DataStreamParticle(
    var t: Float, // 0f (at subject node) to 1f (at core)
    val speed: Float,
    val size: Float,
    val color: Color,
    val lateralWobble: Float
)

private data class SpaceDustParticle(
    var pos: Vec3D,
    val size: Float,
    val alpha: Float,
    val color: Color
)

// ---------------------------------------------------------------------------
// MAIN 3D QUANTUM COLLEGE SYNC OVERLAY
// ---------------------------------------------------------------------------
@OptIn(ExperimentalTextApi::class)
@Composable
fun ScrapingAnimationOverlay(
    phase: ScrapePhase,
    statusText: String,
    onCancel: () -> Unit
) {
    if (phase == ScrapePhase.IDLE) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    // 3D Gyro Tilt & Physics
    var rotX by remember { mutableFloatStateOf(-15f) }
    var rotY by remember { mutableFloatStateOf(10f) }
    var velX by remember { mutableFloatStateOf(0f) }
    var velY by remember { mutableFloatStateOf(0f) }
    var touchActive by remember { mutableStateOf(false) }

    // Color Palette
    val colorCyan = Color(0xFF00E5FF)
    val colorSapphire = Color(0xFF3D5AFE)
    val colorAmethyst = Color(0xFFD500F9)
    val colorEmerald = Color(0xFF00F5A0)
    val colorDarkBg = Color(0xFF050811)

    // Subject Nodes State
    val subjectNodes = remember { mutableStateListOf<SubjectOrbitalNode>() }
    var activeSubjectName by remember { mutableStateOf<String?>(null) }
    var totalRecordsExtracted by remember { mutableIntStateOf(0) }

    // Helper: Initialize nodes from list of strings
    fun setupNodes(subjects: List<String>) {
        if (subjects.isEmpty()) return
        val currentNames = subjectNodes.map { it.name }.toSet()
        val newSubjects = subjects.filter { it !in currentNames }
        if (newSubjects.isEmpty() && subjectNodes.isNotEmpty()) return

        val combined = (subjectNodes.map { it.name } + newSubjects).distinct()
        val count = combined.size
        val updated = combined.mapIndexed { index, name ->
            val angle = (2 * PI.toFloat() / count) * index
            val existing = subjectNodes.find { it.name == name }
            existing ?: SubjectOrbitalNode(
                name = name,
                initialAngle = angle,
                orbitRadius = 240f + (index % 3) * 25f,
                tiltX = ((index % 4) - 1.5f) * 18f,
                tiltZ = ((index % 3) - 1f) * 12f,
                state = NodeSyncState.PENDING
            )
        }
        subjectNodes.clear()
        subjectNodes.addAll(updated)
    }

    // Initial Load from Preferences
    LaunchedEffect(Unit) {
        val syncPrefs = CollegeSyncPreferences(context)
        val targetList = syncPrefs.targetSubjects?.toList() ?: emptyList()
        if (targetList.isNotEmpty()) {
            setupNodes(targetList)
        } else {
            // Fallback placeholder subjects if none configured yet
            setupNodes(listOf("Subject Matrix", "Attendance Stream", "Portal Records"))
        }
    }

    // Event Bus Listener
    LaunchedEffect(Unit) {
        ScrapingEventBus.events.collect { event ->
            when (event) {
                is ScrapingEvent.SubjectsFetched -> {
                    if (event.subjects.isNotEmpty()) {
                        setupNodes(event.subjects)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
                is ScrapingEvent.SpawnSubject -> {
                    if (subjectNodes.none { it.name.equals(event.name, ignoreCase = true) }) {
                        val currentList = subjectNodes.map { it.name } + event.name
                        setupNodes(currentList)
                    }
                }
                is ScrapingEvent.StartExtraction -> {
                    activeSubjectName = event.name
                    val activeIdx = subjectNodes.indexOfFirst { it.name.equals(event.name, ignoreCase = true) }
                    subjectNodes.forEachIndexed { i, node ->
                        if (i == activeIdx) {
                            subjectNodes[i] = node.copy(
                                state = NodeSyncState.ACTIVE,
                                statusMessage = "Target locked • Extracting..."
                            )
                        } else if (activeIdx >= 0 && i < activeIdx) {
                            subjectNodes[i] = node.copy(
                                state = NodeSyncState.COMPLETED,
                                progress = 100f,
                                statusMessage = "Sync Complete ✓"
                            )
                        } else if (node.state == NodeSyncState.ACTIVE) {
                            subjectNodes[i] = node.copy(
                                state = NodeSyncState.COMPLETED,
                                progress = 100f,
                                statusMessage = "Sync Complete ✓"
                            )
                        }
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                is ScrapingEvent.UpdateProgress -> {
                    val activeName = activeSubjectName
                    if (activeName != null) {
                        val idx = subjectNodes.indexOfFirst { it.name.equals(activeName, ignoreCase = true) }
                        if (idx >= 0) {
                            val node = subjectNodes[idx]
                            subjectNodes[idx] = node.copy(
                                progress = event.percent,
                                statusMessage = event.text
                            )
                        }
                    }
                }
                is ScrapingEvent.FinishSubject -> {
                    val idx = subjectNodes.indexOfFirst { it.name.equals(event.name, ignoreCase = true) }
                    if (idx >= 0) {
                        val node = subjectNodes[idx]
                        subjectNodes[idx] = node.copy(
                            state = NodeSyncState.COMPLETED,
                            progress = 100f,
                            statusMessage = "Sync Complete ✓"
                        )
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                is ScrapingEvent.RecordExtracted -> {
                    val activeName = activeSubjectName
                    if (activeName != null) {
                        val idx = subjectNodes.indexOfFirst { it.name.equals(activeName, ignoreCase = true) }
                        if (idx >= 0) {
                            val node = subjectNodes[idx]
                            subjectNodes[idx] = node.copy(recordsCount = max(node.recordsCount, event.count))
                        }
                    }
                }
                is ScrapingEvent.SetPhase -> {}
            }
        }
    }

    // 3D Inertia Momentum Loop
    LaunchedEffect(Unit) {
        while (true) {
            if (!touchActive && (abs(velX) > 0.01f || abs(velY) > 0.01f)) {
                rotY += velX
                rotX += velY
                velX *= 0.93f
                velY *= 0.93f
            }
            delay(16)
        }
    }

    // Continuous Animation Clock
    val infiniteTransition = rememberInfiniteTransition(label = "quantum_sync_anim")
    val orbitTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing)),
        label = "orbitTime"
    )
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corePulse"
    )
    val laserPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "laserPhase"
    )

    // Data Streaming Particles System
    val streamParticles = remember {
        List(24) { index ->
            DataStreamParticle(
                t = (index / 24f),
                speed = 0.018f + Random.nextFloat() * 0.015f,
                size = 3.5f + Random.nextFloat() * 3.5f,
                color = if (Random.nextBoolean()) colorCyan else colorAmethyst,
                lateralWobble = (Random.nextFloat() - 0.5f) * 20f
            )
        }
    }

    // Background Ambient Dust Particles
    val spaceDust = remember {
        List(35) {
            val r = 320f * (0.4f + 0.6f * Random.nextFloat())
            val theta = Random.nextFloat() * 2 * PI.toFloat()
            val phi = acos(Random.nextFloat() * 2f - 1f)
            SpaceDustParticle(
                pos = Vec3D(
                    r * sin(phi) * cos(theta),
                    r * sin(phi) * sin(theta),
                    r * cos(phi)
                ),
                size = 1.5f + Random.nextFloat() * 2f,
                alpha = 0.2f + Random.nextFloat() * 0.45f,
                color = if (Random.nextBoolean()) colorCyan else colorSapphire
            )
        }
    }

    // Frame update for stream particles
    LaunchedEffect(Unit) {
        while (true) {
            for (p in streamParticles) {
                p.t += p.speed
                if (p.t > 1f) p.t = 0f
            }
            delay(16)
        }
    }

    // Active Subject Details & Overall Progress Calculation
    val activeNode = subjectNodes.find { it.state == NodeSyncState.ACTIVE } 
        ?: subjectNodes.firstOrNull { it.state == NodeSyncState.PENDING }

    val totalSubjects = subjectNodes.size
    val activeSubjectIdx = if (activeSubjectName != null) {
        subjectNodes.indexOfFirst { it.name.equals(activeSubjectName, ignoreCase = true) }
    } else {
        subjectNodes.indexOfFirst { it.state == NodeSyncState.ACTIVE }
    }
    val completedCount = subjectNodes.count { it.state == NodeSyncState.COMPLETED }
    val totalExtractedRecords = subjectNodes.sumOf { it.recordsCount }

    val overallProgress = remember(phase, totalSubjects, completedCount, activeNode?.progress) {
        if (phase == ScrapePhase.IDLE) 0f
        else if (totalSubjects == 0) {
            when (phase) {
                ScrapePhase.LOGIN -> 6f
                ScrapePhase.LOGIN_INJECTED -> 12f
                ScrapePhase.FETCH_SUBJECTS, ScrapePhase.SCRAPING -> 18f
                ScrapePhase.EXTRACTING -> 25f
                else -> 0f
            }
        } else {
            val baseStageProgress = when (phase) {
                ScrapePhase.LOGIN -> 5f
                ScrapePhase.LOGIN_INJECTED -> 10f
                ScrapePhase.FETCH_SUBJECTS, ScrapePhase.SCRAPING -> 15f
                else -> 20f
            }

            if (phase == ScrapePhase.EXTRACTING || completedCount > 0 || activeNode?.state == NodeSyncState.ACTIVE) {
                val weightPerSubject = 80f / totalSubjects
                val completedWeight = completedCount * weightPerSubject
                val activeSubjectProgress = if (activeNode != null && activeNode.state == NodeSyncState.ACTIVE) {
                    (activeNode.progress / 100f) * weightPerSubject
                } else 0f

                (20f + completedWeight + activeSubjectProgress).coerceIn(baseStageProgress, 100f)
            } else {
                baseStageProgress
            }
        }
    }

    val animatedOverallProgress by animateFloatAsState(
        targetValue = (overallProgress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "overallProgressAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070B16),
                        Color(0xFF03050C),
                        Color(0xFF010206)
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
                        val dx = dragAmount.x * 0.42f
                        val dy = dragAmount.y * 0.42f
                        rotY = (rotY + dx) % 360f
                        rotX = (rotX - dy).coerceIn(-75f, 75f)
                        velX = dx * 0.5f
                        velY = -dy * 0.5f
                    }
                )
            }
    ) {

        // -------------------------------------------------------------------
        // 3D CANVAS VIEWPORT
        // -------------------------------------------------------------------
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f - 20.dp.toPx()

            // 1. Ambient Background Dust
            spaceDust.forEach { dust ->
                val rotated = dust.pos.rotateX(rotX).rotateY(rotY)
                val (proj, scale) = rotated.project(centerX, centerY)
                if (scale > 0) {
                    drawCircle(
                        color = dust.color.copy(alpha = (dust.alpha * scale).coerceIn(0.1f, 0.8f)),
                        radius = dust.size * scale,
                        center = proj
                    )
                }
            }

            // 2. Orbital Ring Projection (Celestial Orbit Grid)
            val orbitPointsCount = 48
            val orbitPath = Path()
            var firstPoint = true
            for (i in 0..orbitPointsCount) {
                val a = (2 * PI.toFloat() / orbitPointsCount) * i
                val p = Vec3D(250f * cos(a), 0f, 250f * sin(a))
                    .rotateX(rotX + 10f)
                    .rotateY(rotY + orbitTime * 10f)
                val (proj, scale) = p.project(centerX, centerY)
                if (scale > 0) {
                    if (firstPoint) {
                        orbitPath.moveTo(proj.x, proj.y)
                        firstPoint = false
                    } else {
                        orbitPath.lineTo(proj.x, proj.y)
                    }
                }
            }
            drawPath(
                path = orbitPath,
                color = colorCyan.copy(alpha = 0.15f),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 16f), 0f)
                )
            )

            // 3. Compute 3D Positions for All Subject Nodes
            val nodeProjectedData = subjectNodes.mapIndexed { index, node ->
                val currentAngle = node.initialAngle + orbitTime
                val baseVec = Vec3D(
                    node.orbitRadius * cos(currentAngle),
                    sin(currentAngle * 2f + index) * 35f,
                    node.orbitRadius * sin(currentAngle)
                )
                    .rotateX(node.tiltX)
                    .rotateZ(node.tiltZ)
                    .rotateX(rotX)
                    .rotateY(rotY)

                val (proj, scale) = baseVec.project(centerX, centerY)
                Triple(node, baseVec, Pair(proj, scale))
            }

            // Find projected position of Active Subject (for laser and particles)
            val activeData = nodeProjectedData.find { it.first.state == NodeSyncState.ACTIVE }

            // 4. Draw Active Targeting Laser Beam & Data Streaming Particles
            if (activeData != null) {
                val (activeNodeObj, activeVec, activeProjScale) = activeData
                val (activeProj, activeScale) = activeProjScale
                val coreProj = Offset(centerX, centerY)

                // Laser Outer Glow
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colorCyan.copy(alpha = 0.85f),
                            colorSapphire.copy(alpha = 0.6f),
                            colorAmethyst.copy(alpha = 0.9f)
                        ),
                        start = coreProj,
                        end = activeProj
                    ),
                    start = coreProj,
                    end = activeProj,
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Laser Inner Core Beam
                drawLine(
                    color = Color.White.copy(alpha = 0.95f),
                    start = coreProj,
                    end = activeProj,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Laser Energy Pulse Ripples
                val pulseT = (laserPhase % 360f) / 360f
                val pulseX = coreProj.x + (activeProj.x - coreProj.x) * pulseT
                val pulseY = coreProj.y + (activeProj.y - coreProj.y) * pulseT
                drawCircle(
                    color = colorCyan,
                    radius = 8.dp.toPx() * corePulse,
                    center = Offset(pulseX, pulseY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(pulseX, pulseY)
                )

                // 3D Data Particles Stream (Traveling from Subject -> Core)
                streamParticles.forEach { particle ->
                    val curX = activeProj.x + (coreProj.x - activeProj.x) * particle.t
                    val curY = activeProj.y + (coreProj.y - activeProj.y) * particle.t
                    val perpX = -(activeProj.y - coreProj.y)
                    val perpY = (activeProj.x - coreProj.x)
                    val perpLen = sqrt(perpX * perpX + perpY * perpY).coerceAtLeast(1f)
                    val wobble = sin(particle.t * PI.toFloat() * 3f) * particle.lateralWobble

                    val finalX = curX + (perpX / perpLen) * wobble
                    val finalY = curY + (perpY / perpLen) * wobble

                    val particleAlpha = sin(particle.t * PI.toFloat()).coerceIn(0f, 1f)
                    drawCircle(
                        color = particle.color.copy(alpha = particleAlpha),
                        radius = particle.size * activeScale,
                        center = Offset(finalX, finalY)
                    )
                }
            }

            // 5. Central Quantum Core (3D Interlocking Gyro Rings)
            val ringRadius = 75f * corePulse
            val ringSegments = 36

            // Core Ring 1 (Cyan - X/Y)
            val ring1Path = Path()
            for (i in 0..ringSegments) {
                val a = (2 * PI.toFloat() / ringSegments) * i
                val p = Vec3D(ringRadius * cos(a), ringRadius * sin(a), 0f)
                    .rotateX(rotX + laserPhase)
                    .rotateY(rotY + laserPhase * 0.7f)
                val (proj, scale) = p.project(centerX, centerY)
                if (i == 0) ring1Path.moveTo(proj.x, proj.y) else ring1Path.lineTo(proj.x, proj.y)
            }
            drawPath(
                path = ring1Path,
                color = colorCyan.copy(alpha = 0.8f),
                style = Stroke(width = 2.dp.toPx())
            )

            // Core Ring 2 (Sapphire - Y/Z)
            val ring2Path = Path()
            for (i in 0..ringSegments) {
                val a = (2 * PI.toFloat() / ringSegments) * i
                val p = Vec3D(0f, ringRadius * 1.15f * cos(a), ringRadius * 1.15f * sin(a))
                    .rotateX(rotX - laserPhase * 0.8f)
                    .rotateZ(rotY + laserPhase)
                val (proj, scale) = p.project(centerX, centerY)
                if (i == 0) ring2Path.moveTo(proj.x, proj.y) else ring2Path.lineTo(proj.x, proj.y)
            }
            drawPath(
                path = ring2Path,
                color = colorSapphire.copy(alpha = 0.85f),
                style = Stroke(width = 2.dp.toPx())
            )

            // Core Ring 3 (Amethyst - X/Z)
            val ring3Path = Path()
            for (i in 0..ringSegments) {
                val a = (2 * PI.toFloat() / ringSegments) * i
                val p = Vec3D(ringRadius * 0.9f * cos(a), 0f, ringRadius * 0.9f * sin(a))
                    .rotateY(rotY + laserPhase * 1.2f)
                    .rotateZ(rotX + 45f)
                val (proj, scale) = p.project(centerX, centerY)
                if (i == 0) ring3Path.moveTo(proj.x, proj.y) else ring3Path.lineTo(proj.x, proj.y)
            }
            drawPath(
                path = ring3Path,
                color = colorAmethyst.copy(alpha = 0.75f),
                style = Stroke(width = 1.8.dp.toPx())
            )

            // Core Center Orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        colorCyan.copy(alpha = 0.9f),
                        colorSapphire.copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = 45.dp.toPx() * corePulse
                ),
                radius = 45.dp.toPx() * corePulse,
                center = Offset(centerX, centerY)
            )

            // 6. Draw 3D Subject Nodes Sorted by Depth (Painter's Algorithm)
            val sortedNodes = nodeProjectedData.sortedBy { it.second.z }
            sortedNodes.forEach { (node, vec3d, projScale) ->
                val (proj, scale) = projScale
                if (scale <= 0) return@forEach

                val baseRadius = (when (node.state) {
                    NodeSyncState.ACTIVE -> 26.dp.toPx()
                    NodeSyncState.COMPLETED -> 20.dp.toPx()
                    NodeSyncState.PENDING -> 18.dp.toPx()
                }) * scale

                val nodeColor = when (node.state) {
                    NodeSyncState.ACTIVE -> colorCyan
                    NodeSyncState.COMPLETED -> colorEmerald
                    NodeSyncState.PENDING -> colorSapphire
                }

                // Outer Glow Halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            nodeColor.copy(alpha = if (node.state == NodeSyncState.ACTIVE) 0.6f else 0.25f),
                            Color.Transparent
                        ),
                        center = proj,
                        radius = baseRadius * 2.2f
                    ),
                    radius = baseRadius * 2.2f,
                    center = proj
                )

                // Base Node Sphere
                drawCircle(
                    color = colorDarkBg,
                    radius = baseRadius,
                    center = proj
                )
                drawCircle(
                    color = nodeColor.copy(alpha = 0.25f),
                    radius = baseRadius,
                    center = proj
                )
                drawCircle(
                    color = nodeColor,
                    radius = baseRadius,
                    center = proj,
                    style = Stroke(width = (if (node.state == NodeSyncState.ACTIVE) 2.8.dp.toPx() else 1.8.dp.toPx()) * scale)
                )

                // Active Node: Rotating Targeting Reticle & Progress Gauge
                if (node.state == NodeSyncState.ACTIVE) {
                    val progressSweep = (node.progress / 100f) * 360f
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = progressSweep,
                        useCenter = false,
                        topLeft = Offset(proj.x - baseRadius * 1.35f, proj.y - baseRadius * 1.35f),
                        size = Size(baseRadius * 2.7f, baseRadius * 2.7f),
                        style = Stroke(width = 3.dp.toPx() * scale, cap = StrokeCap.Round)
                    )

                    // Reticle brackets
                    val reticleRadius = baseRadius * 1.6f
                    val rAngle = laserPhase * 0.5f
                    for (k in 0..3) {
                        val deg = rAngle + k * 90f
                        val rad = deg * (PI / 180.0).toFloat()
                        val bx = proj.x + reticleRadius * cos(rad)
                        val by = proj.y + reticleRadius * sin(rad)
                        drawCircle(
                            color = colorCyan,
                            radius = 2.dp.toPx() * scale,
                            center = Offset(bx, by)
                        )
                    }
                }

                // Completed Node: Checkmark Badge
                if (node.state == NodeSyncState.COMPLETED) {
                    val checkSize = 10.dp.toPx() * scale
                    val checkPath = Path().apply {
                        moveTo(proj.x - checkSize * 0.45f, proj.y)
                        lineTo(proj.x - checkSize * 0.1f, proj.y + checkSize * 0.35f)
                        lineTo(proj.x + checkSize * 0.5f, proj.y - checkSize * 0.35f)
                    }
                    drawPath(
                        path = checkPath,
                        color = colorEmerald,
                        style = Stroke(width = 2.5.dp.toPx() * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // Node Center Glyph (Initials / Icon representation)
                if (node.state != NodeSyncState.COMPLETED) {
                    val shortInitial = node.name.take(2).uppercase()
                    val textLayout = textMeasurer.measure(
                        text = shortInitial,
                        style = TextStyle(
                            color = if (node.state == NodeSyncState.ACTIVE) Color.White else nodeColor,
                            fontSize = (11 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(
                            proj.x - textLayout.size.width / 2f,
                            proj.y - textLayout.size.height / 2f
                        )
                    )
                }

                // 3D Subject Name Tag Pill
                val labelText = if (node.name.length > 18) node.name.take(16) + "…" else node.name
                val labelLayout = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(
                        color = if (node.state == NodeSyncState.ACTIVE) Color.White else Color(0xFFCAD4E0),
                        fontSize = (10.5 * scale).coerceAtLeast(8.0).sp,
                        fontWeight = if (node.state == NodeSyncState.ACTIVE) FontWeight.Bold else FontWeight.Medium
                    )
                )

                val tagWidth = labelLayout.size.width + 16.dp.toPx() * scale
                val tagHeight = labelLayout.size.height + 8.dp.toPx() * scale
                val tagTop = proj.y + baseRadius * 1.45f
                val tagLeft = proj.x - tagWidth / 2f

                // Tag Background
                drawRoundRect(
                    color = colorDarkBg.copy(alpha = 0.88f),
                    topLeft = Offset(tagLeft, tagTop),
                    size = Size(tagWidth, tagHeight),
                    cornerRadius = CornerRadius(6.dp.toPx() * scale, 6.dp.toPx() * scale)
                )
                drawRoundRect(
                    color = nodeColor.copy(alpha = if (node.state == NodeSyncState.ACTIVE) 0.8f else 0.35f),
                    topLeft = Offset(tagLeft, tagTop),
                    size = Size(tagWidth, tagHeight),
                    cornerRadius = CornerRadius(6.dp.toPx() * scale, 6.dp.toPx() * scale),
                    style = Stroke(width = 1.dp.toPx() * scale)
                )

                // Tag Text
                drawText(
                    textLayoutResult = labelLayout,
                    topLeft = Offset(tagLeft + 8.dp.toPx() * scale, tagTop + 4.dp.toPx() * scale)
                )
            }
        }

        // -------------------------------------------------------------------
        // TOP HUD: PHASE MATRIX & LIVE SENSORS
        // -------------------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Phase Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colorCyan.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colorCyan)
                        )
                        Spacer(Modifier.width(8.dp))
                        val phaseTitle = when (phase) {
                            ScrapePhase.LOGIN -> "PHASE 01 // SECURE LOGIN"
                            ScrapePhase.LOGIN_INJECTED -> "PHASE 02 // PORTAL HANDSHAKE"
                            ScrapePhase.FETCH_SUBJECTS -> "PHASE 03 // SUBJECT MATRIX"
                            ScrapePhase.SCRAPING -> "PHASE 04 // DATA MAPPING"
                            ScrapePhase.EXTRACTING -> "PHASE 05 // QUANTUM EXTRACTION"
                            ScrapePhase.IDLE -> "STANDBY"
                        }
                        Text(
                            text = phaseTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = colorCyan
                        )
                    }
                }

                // Live Sync Status Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = colorEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "LIVE MATRIX",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Subtitle Status Ticker
            AnimatedContent(
                targetState = statusText,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "status_ticker"
            ) { txt ->
                Text(
                    text = txt,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // -------------------------------------------------------------------
        // BOTTOM HUD: ACTIVE TARGET SUBJECT CARD & CONTROLS
        // -------------------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Active Target Subject Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0B1120).copy(alpha = 0.92f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorCyan.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colorCyan)
                                )
                                Spacer(Modifier.width(6.dp))
                                val stepLabel = if (totalSubjects > 0 && activeNode != null) {
                                    val currentStepNum = if (activeSubjectIdx >= 0) activeSubjectIdx + 1 else min(completedCount + 1, totalSubjects)
                                    "STEP $currentStepNum OF $totalSubjects • TARGET SUBJECT"
                                } else {
                                    "PORTAL SYNC MATRIX"
                                }
                                Text(
                                    text = stepLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = activeNode?.name ?: "Synchronizing Portal Data...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Cumulative Overall Progress percentage badge (0% -> 100%)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colorCyan.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colorCyan.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${(animatedOverallProgress * 100f).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Animated Cumulative Progress Bar (0% -> 100%)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF1E293B))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedOverallProgress)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(colorSapphire, colorCyan, colorEmerald)
                                    )
                                )
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Step breakdown: completed count & total extracted records tally
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusSummary = if (totalSubjects > 0) {
                            "$completedCount of $totalSubjects subjects synced"
                        } else {
                            "Initializing Matrix..."
                        }
                        Text(
                            text = statusSummary,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF94A3B8)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colorCyan.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colorCyan.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "$totalExtractedRecords Records Extracted",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colorCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Action Controls: Cancel Button
            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.9f),
                    contentColor = Color(0xFFFF5252)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Cancel Sync",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}