package com.kishan.attendmate.ui.entry

import android.content.Intent
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.ui.auth.LoginActivity
import com.kishan.attendmate.ui.theme.AttendMateTheme
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

class EntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendMateTheme {
                QuantumEntryScreen { nextActivity ->
                    startActivity(Intent(this, nextActivity))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3D MATH & DATA STRUCTURES
// ---------------------------------------------------------------------------
data class Point3D(var x: Float, var y: Float, var z: Float)

fun rotateX(p: Point3D, angle: Float) {
    val rad = angle * (PI / 180f).toFloat()
    val cosA = cos(rad)
    val sinA = sin(rad)
    val ny = p.y * cosA - p.z * sinA
    val nz = p.y * sinA + p.z * cosA
    p.y = ny
    p.z = nz
}

fun rotateY(p: Point3D, angle: Float) {
    val rad = angle * (PI / 180f).toFloat()
    val cosA = cos(rad)
    val sinA = sin(rad)
    val nx = p.x * cosA + p.z * sinA
    val nz = -p.x * sinA + p.z * cosA
    p.x = nx
    p.z = nz
}

// ---------------------------------------------------------------------------
// MAIN QUANTUM ENTRY SCREEN
// ---------------------------------------------------------------------------
@Composable
fun QuantumEntryScreen(onNavigate: (Class<*>) -> Unit) {
    // Determine target activity after delay
    LaunchedEffect(Unit) {
        delay(3500) // generous time to show off animation
        val user = FirebaseAuth.getInstance().currentUser
        onNavigate(if (user == null) LoginActivity::class.java else MainActivity::class.java)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        // 1. The Interactive 3D Starfield Constellation
        InteractiveConstellation()

        // 2. The Fluid Metaball Core
        FluidMetaballCore()

        // 3. Shimmer Text Reveal overlaid on top
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = 130.dp)) {
            ShimmerTextReveal("AttendMate")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "SMART ATTENDANCE TRACKING",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // 4. Footer
        Text(
            text = "SPIN TO INTERACT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            letterSpacing = 3.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// 1. INTERACTIVE 3D CONSTELLATION
// ---------------------------------------------------------------------------
@Composable
fun InteractiveConstellation() {
    val primaryColor = MaterialTheme.colorScheme.primary

    // Generate random 3D points on a sphere
    val numStars = 80
    val radius = 500f
    val stars = remember {
        List(numStars) {
            val theta = Random.nextDouble(0.0, 2 * PI).toFloat()
            val phi = acos(Random.nextDouble(-1.0, 1.0)).toFloat()
            Point3D(
                x = radius * sin(phi) * cos(theta),
                y = radius * sin(phi) * sin(theta),
                z = radius * cos(phi)
            )
        }
    }

    // Touch interaction states
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    // Auto-rotation states
    val infiniteTransition = rememberInfiniteTransition(label = "constellation")
    val autoRotX by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(35000, easing = LinearEasing)), label = "autoRotX"
    )
    val autoRotY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "autoRotY"
    )

    Canvas(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                dragX += dragAmount.x * 0.4f
                dragY += dragAmount.y * 0.4f
            }
        }
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // Copy points for transformation
        val transformed = stars.map { Point3D(it.x, it.y, it.z) }

        transformed.forEach { p ->
            // Apply auto rotation + user drag rotation
            rotateX(p, autoRotX + dragY)
            rotateY(p, autoRotY + dragX)
        }

        // Draw points with depth fading
        transformed.forEach { p ->
            val perspective = 1200f / (1200f - p.z) // projection
            val projX = centerX + p.x * perspective
            val projY = centerY + p.y * perspective

            // Depth calculation for alpha and size
            val depthRatio = ((p.z + radius) / (2 * radius)).coerceIn(0f, 1f)
            val alpha = (0.1f + 0.9f * depthRatio)
            val pointSize = (1.5f + 4.5f * depthRatio)

            drawCircle(
                color = primaryColor.copy(alpha = alpha),
                radius = pointSize,
                center = Offset(projX, projY)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 2. FLUID METABALL CORE
// ---------------------------------------------------------------------------
@Composable
fun FluidMetaballCore() {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val infiniteTransition = rememberInfiniteTransition(label = "metaballs")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "time"
    )

    // A ColorMatrix that increases contrast heavily to crush soft blurred edges into sharp fluid edges
    val colorMatrixFilter = remember {
        ColorMatrixColorFilter(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 40f, -4000f // alpha multiplier and offset
            )
        )
    }

    Box(
        modifier = Modifier
            .size(250.dp)
            .graphicsLayer {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blur = RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP)
                    val contrast = RenderEffect.createColorFilterEffect(colorMatrixFilter)
                    renderEffect = RenderEffect.createChainEffect(contrast, blur).asComposeRenderEffect()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            
            // Orb 1 (Center pulsing)
            val pulse = 1f + 0.15f * sin(time * 2f)
            drawCircle(primaryColor, radius = 70f * pulse, center = center)

            // Orb 2 (Orbiting)
            val orb2X = center.x + 60f * cos(time)
            val orb2Y = center.y + 60f * sin(time)
            drawCircle(secondaryColor, radius = 55f, center = Offset(orb2X, orb2Y))

            // Orb 3 (Orbiting opposite and faster)
            val orb3X = center.x + 80f * cos(-time * 1.3f)
            val orb3Y = center.y + 45f * sin(-time * 1.3f)
            drawCircle(primaryColor, radius = 40f, center = Offset(orb3X, orb3Y))
            
            // Orb 4 (Small accent orbiting)
            val orb4X = center.x + 70f * cos(time * 2f + PI.toFloat())
            val orb4Y = center.y + 70f * sin(time * 2f + PI.toFloat())
            drawCircle(surfaceVariant, radius = 30f, center = Offset(orb4X, orb4Y))
        }
    }
}

// ---------------------------------------------------------------------------
// 3. SHIMMER TEXT REVEAL
// ---------------------------------------------------------------------------
@Composable
fun ShimmerTextReveal(text: String) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -800f, targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shimmerOffset"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            onSurfaceColor.copy(alpha = 0.2f),
            onSurfaceColor.copy(alpha = 0.4f),
            primaryColor,
            onSurfaceColor.copy(alpha = 0.4f),
            onSurfaceColor.copy(alpha = 0.2f)
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 400f, 0f)
    )

    Text(
        text = text,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 46.sp,
        style = MaterialTheme.typography.displayMedium.copy(
            brush = shimmerBrush
        )
    )
}