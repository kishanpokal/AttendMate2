package com.kishan.attendmate.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kishan.attendmate.ui.theme.statusColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AttendanceRing(
    percentage: Float,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    strokeWidth: Dp = 8.dp,
    threshold: Float = 0.75f,
    showText: Boolean = true
) {
    var animatedPercentage by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = animatedPercentage,
        animationSpec = tween(durationMillis = 1000),
        label = "progress_animation"
    )

    LaunchedEffect(percentage) {
        animatedPercentage = percentage
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val safeColor = statusColors().success
    val warningColor = statusColors().warning
    val dangerColor = statusColors().warning // Use danger if needed, fallback to warning. Actually Theme doesn't have Danger right now, only Success/Warning/Error.
    
    val errorColor = MaterialTheme.colorScheme.error

    val progressColor = when {
        progress >= 0.75f -> safeColor
        progress >= 0.60f -> warningColor
        else -> errorColor
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            val sweepAngle = 360f * progress
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            
            // Draw track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            
            // Draw progress
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = stroke
            )
            
            // Draw threshold tick mark
            val thresholdAngle = -90f + (360f * threshold)
            val thresholdAngleRad = Math.toRadians(thresholdAngle.toDouble())
            
            val radius = (size.toPx() - strokeWidth.toPx()) / 2f
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            
            // Tick mark lines extending slightly beyond the stroke
            val tickStartRadius = radius - (strokeWidth.toPx() / 2f) - 4.dp.toPx()
            val tickEndRadius = radius + (strokeWidth.toPx() / 2f) + 4.dp.toPx()
            
            val startX = center.x + tickStartRadius * cos(thresholdAngleRad).toFloat()
            val startY = center.y + tickStartRadius * sin(thresholdAngleRad).toFloat()
            
            val endX = center.x + tickEndRadius * cos(thresholdAngleRad).toFloat()
            val endY = center.y + tickEndRadius * sin(thresholdAngleRad).toFloat()
            
            drawLine(
                color = onSurfaceColor,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        
        if (showText) {
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
