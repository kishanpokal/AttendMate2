package com.kishan.attendmate.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class DeviceType { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberDeviceType(): DeviceType {
    val configuration = LocalConfiguration.current
    return when {
        configuration.screenWidthDp < 600 -> DeviceType.COMPACT
        configuration.screenWidthDp < 840 -> DeviceType.MEDIUM
        else -> DeviceType.EXPANDED
    }
}

// Spacing helper for padding around main content layouts
val DeviceType.contentPadding: Dp
    get() = when (this) {
        DeviceType.COMPACT -> 16.dp
        DeviceType.MEDIUM -> 24.dp
        DeviceType.EXPANDED -> 32.dp
    }

// Responsive corner radius for cards
val DeviceType.cardRadius: Dp
    get() = when (this) {
        DeviceType.COMPACT -> 12.dp
        DeviceType.MEDIUM -> 16.dp
        DeviceType.EXPANDED -> 20.dp
    }

// Responsive circular progress indicator size for home screen
val DeviceType.circularProgressIndicatorSize: Dp
    get() = when (this) {
        DeviceType.COMPACT -> 160.dp
        else -> 200.dp
    }

// Responsive calendar cell size for Analytics screen
val DeviceType.calendarCellSize: Dp
    get() = when (this) {
        DeviceType.COMPACT -> 38.dp
        DeviceType.MEDIUM -> 46.dp
        DeviceType.EXPANDED -> 52.dp
    }
