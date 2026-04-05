package com.kishan.attendmate.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private val BaseBackground = Color(0xFF030712)

// ─── Data Classes ────────────────────────────────────────────────────────────
data class TerminalLine(val text: String, val color: Color, val id: Long)
data class PhaseStep(val phase: ScrapePhase, val label: String)

// ─── Main Overlay Composable ─────────────────────────────────────────────────
@Composable
fun ScrapingAnimationOverlay(
    phase: ScrapePhase,
    statusText: String,
    onCancel: () -> Unit
) {
    if (phase == ScrapePhase.IDLE) return

    var isFullscreen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(BaseBackground)) {

        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 24.dp, top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // [ZONE 1] The Interactive 3D Solar System
            Box(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Ensure you have Interactive3DGlobe.kt created in this package!
                // In both places (normal + fullscreen):
                Interactive3DGlobe()   // ← no statusText anymore

                // Fullscreen Toggle Button
                IconButton(
                    onClick = { isFullscreen = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
                }
            }

            // [ZONE 2] Live Data Stream Terminal
            Box(
                modifier = Modifier
                    .weight(0.30f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                TerminalStream(phase, statusText)
            }

            // [ZONE 3] Phase Stepper
            Box(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                PhaseStepperAndStatus(phase, statusText, onCancel)
            }
        }
    }

    // --- FULLSCREEN MODE OVERLAY ---
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(BaseBackground)) {
                // Render the 3D globe full screen
                // In both places (normal + fullscreen):
                Interactive3DGlobe()   // ← no statusText anymore

                // Floating Exit Button
                IconButton(
                    onClick = { isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                }

                // Floating Status overlay at the bottom so user still knows what is happening
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp)
                        .fillMaxWidth(0.8f)
                ) {
                    Text(
                        text = statusText,
                        color = Color(0xFF00F5A0),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

// ─── Zone 2: Terminal Stream ─────────────────────────────────────────────────
@Composable
private fun TerminalStream(phase: ScrapePhase, statusText: String) {
    val lines = remember { mutableStateListOf<TerminalLine>() }
    val listState = rememberLazyListState()

    LaunchedEffect(statusText) {
        if (statusText.isBlank()) return@LaunchedEffect
        val color = when {
            statusText.contains("Error", true) || statusText.contains("failed", true) -> Color(0xFFFF4444)
            statusText.contains("Processing:", true) -> Color(0xFF6C63FF)
            statusText.contains("Found", true) || statusText.contains("Extracting", true) -> Color(0xFF00D9FF)
            statusText.contains("Skipping", true) -> Color(0xFFA0AEC0)
            else -> Color(0xFF00F5A0)
        }
        lines.add(TerminalLine("> $statusText".replace(">> ", "> "), color, System.currentTimeMillis()))
        if (lines.size > 15) lines.removeFirst()
        listState.animateScrollToItem(lines.size - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF00D9FF).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0E1A).copy(alpha = 0.85f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF4444)))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFC107)))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF00F5A0)))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("LIVE_STREAM.exe", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF00F5A0))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(lines, key = { it.id }) { line ->
                    AnimatedTypewriterText(line)
                }
            }
        }
    }
}

@Composable
private fun AnimatedTypewriterText(line: TerminalLine) {
    var charCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(line.text) {
        for (i in 1..line.text.length) {
            delay(10)
            charCount = i
        }
    }
    Text(line.text.take(charCount), color = line.color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
}

// ─── Zone 3: Phase Stepper ───────────────────────────────────────────────────
@Composable
private fun PhaseStepperAndStatus(currentPhase: ScrapePhase, statusText: String, onCancel: () -> Unit) {
    val steps = listOf(
        PhaseStep(ScrapePhase.LOGIN, "AUTH"),
        PhaseStep(ScrapePhase.LOGIN_INJECTED, "INJECT"),
        PhaseStep(ScrapePhase.SCRAPING, "SCRAPE"),
        PhaseStep(ScrapePhase.EXTRACTING, "EXTRACT")
    )

    val currentIndex = steps.indexOfFirst { it.phase == currentPhase }.coerceAtLeast(0)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, step ->
                val isCompleted = index < currentIndex
                val isActive = index == currentIndex

                if (index > 0) {
                    val prevCompleted = index <= currentIndex
                    Box(modifier = Modifier.weight(1f).height(if (prevCompleted) 2.dp else 1.dp).background(if (prevCompleted) Color(0xFF00D9FF) else Color(0xFFA0AEC0).copy(alpha = 0.3f)))
                }

                Box(modifier = Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    val nodeSize by animateDpAsState(targetValue = if (isActive) 42.dp else if (isCompleted) 36.dp else 32.dp, label = "node_size")
                    if (isCompleted) {
                        Box(modifier = Modifier.size(nodeSize).clip(CircleShape).background(Color(0xFF00D9FF)), contentAlignment = Alignment.Center) {
                            Text("✓", color = Color(0xFF080C14), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    } else if (isActive) {
                        val inf = rememberInfiniteTransition(label = "")
                        val bw by inf.animateFloat(initialValue = 2f, targetValue = 5f, animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "")
                        Box(modifier = Modifier.size(nodeSize).border(bw.dp, Color(0xFF00D9FF), CircleShape).background(Color(0xFF080C14), CircleShape), contentAlignment = Alignment.Center) {
                            Text(step.label.take(1), color = Color(0xFF00D9FF), fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Box(modifier = Modifier.size(nodeSize).border(1.dp, Color(0xFFA0AEC0), CircleShape).background(Color(0xFF080C14), CircleShape), contentAlignment = Alignment.Center) {
                            Text(step.label.take(1), color = Color(0xFFA0AEC0))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        AnimatedContent(targetState = steps.getOrNull(currentIndex)?.label ?: "ACTIVE", label = "phaseAnim") { label ->
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Spacer(modifier = Modifier.height(4.dp))
        AnimatedContent(targetState = statusText, label = "statusAnim") { text ->
            Text(text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFA0AEC0))
        }

        Spacer(modifier = Modifier.height(16.dp))
        val inf = rememberInfiniteTransition(label = "prog")
        val pOff by inf.animateFloat(initialValue = -1000f, targetValue = 1000f, animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart), label = "")
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFA0AEC0).copy(alpha=0.1f))) {
            Box(modifier = Modifier.offset(x = pOff.dp).width(200.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF00D9FF), Color(0xFF8B5CF6), Color(0xFF00D9FF), Color.Transparent))))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(containerColor = Color(0xFFFF4444).copy(alpha = 0.4f), contentColor = Color.White)) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cancel")
            }
        }
    }
}