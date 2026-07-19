package com.kishan.attendmate.ui.theme

import com.kishan.attendmate.ui.theme.statusColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp

// SPACING TOKENS
val SpaceXXS = 4.dp
val SpaceXS  = 8.dp
val SpaceSM  = 12.dp
val SpaceMD  = 16.dp
val SpaceLG  = 24.dp
val SpaceXL  = 32.dp

// RADIUS TOKENS
val RadiusSM = 8.dp
val RadiusMD = 12.dp
val RadiusLG = 16.dp
val RadiusXL = 20.dp

// ELEVATION TOKENS
val ElevationNone = 0.dp
val ElevationLow  = 2.dp
val ElevationHigh = 6.dp


@Composable
fun authBackgroundBrush(): Brush {
    val isDark = isSystemInDarkTheme()
    val colors = authGradientColors()
    return if (isDark) {
        Brush.linearGradient(colors = colors)
    } else {
        Brush.radialGradient(colors = colors)
    }
}

// CARD CONFIGURATION
object CardStyle {
    val shape = RoundedCornerShape(RadiusLG)
    val elevation = ElevationLow
    
    @Composable
    fun containerColor() = MaterialTheme.colorScheme.surface
    
    @Composable
    fun border() = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
}

// GLASS TOKENS
val NavBarHeight = 68.dp
val NavContentBottomPadding = 120.dp
val GlassTintAlpha = 0.65f
val GlassBlurRadius = 24.dp
val GlassBorder = 0.5.dp

