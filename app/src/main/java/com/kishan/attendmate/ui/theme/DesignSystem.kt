package com.kishan.attendmate.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

// SEMANTIC COLORS
val SuccessColor = Color(0xFF16A34A)
val WarningColor = Color(0xFFD97706)
val DangerColor  = Color(0xFFDC2626)

// CARD CONFIGURATION
object CardStyle {
    val shape = RoundedCornerShape(RadiusLG)
    val elevation = ElevationLow
    
    @Composable
    fun containerColor() = MaterialTheme.colorScheme.surface
    
    @Composable
    fun border() = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
}
