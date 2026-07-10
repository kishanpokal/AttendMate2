package com.kishan.attendmate.ui.components

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.kishan.attendmate.ui.theme.GlassBlurRadius
import com.kishan.attendmate.ui.theme.GlassTintAlpha
import com.kishan.attendmate.ui.theme.GlassBorder
import com.kishan.attendmate.ui.theme.NavBarHeight
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.ui.attendance.AddAttendanceActivity
import com.kishan.attendmate.ui.attendance.AttendanceListActivity
import com.kishan.attendmate.ui.analytics.AnalyticsActivity
import com.kishan.attendmate.ui.settings.SettingsActivity
import com.kishan.attendmate.ui.theme.ElevationLow
import kotlinx.coroutines.delay

data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * FLOATING TRANSLUCENT NAVIGATION BAR
 * Features: True transparent glass, drop shadow, lifted pill design
 */
@Composable
fun AttendMateNavigationBar(
    selectedRoute: String,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    val navItems = listOf(
        NavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("attendance", "Attendance", Icons.AutoMirrored.Filled.EventNote, Icons.AutoMirrored.Outlined.EventNote),
        NavItem("analytics", "Analytics", Icons.Filled.Insights, Icons.Outlined.Insights),
        NavItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    fun navigate(route: String) {
        if (route == selectedRoute) return
        val intent = when (route) {
            "home" -> Intent(context, MainActivity::class.java)
            "attendance" -> Intent(context, AttendanceListActivity::class.java)
            "analytics" -> Intent(context, AnalyticsActivity::class.java)
            "settings" -> Intent(context, SettingsActivity::class.java)
            else -> null
        }
        intent?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }?.let { context.startActivity(it) }
    }

    val containerShape = RoundedCornerShape(34.dp)
    val glassModifier = if (hazeState != null) {
        val surfaceColor = MaterialTheme.colorScheme.surface
        Modifier.hazeChild(
            state = hazeState,
            shape = containerShape,
            style = HazeStyle(
                backgroundColor = surfaceColor,
                blurRadius = GlassBlurRadius,
                tint = HazeTint(color = surfaceColor.copy(alpha = GlassTintAlpha))
            )
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // Increased bottom padding to lift it higher off the screen edge
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        // Main navigation container
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(NavBarHeight)
                .then(glassModifier)
                .border(
                    width = GlassBorder,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    shape = containerShape
                ),
            shape = containerShape,
            color = if (hazeState != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
            shadowElevation = ElevationLow
        ) {
            // Navigation content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side icons
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.take(2).forEach { item ->
                        NavIcon(
                            item = item,
                            selected = selectedRoute == item.route,
                            isDark = isDark,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navigate(item.route)
                            }
                        )
                    }
                }

                // Center space for FAB
                Spacer(modifier = Modifier.width(64.dp))

                // Right side icons
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.takeLast(2).forEach { item ->
                        NavIcon(
                            item = item,
                            selected = selectedRoute == item.route,
                            isDark = isDark,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navigate(item.route)
                            }
                        )
                    }
                }
            }
        }

        // Clean standard FAB - repositioned for the floating pill
        androidx.compose.material3.FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                context.startActivity(Intent(context, AddAttendanceActivity::class.java))
            },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = ElevationLow,
                pressedElevation = ElevationLow
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(64.dp)
                .offset(y = (-30).dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Attendance",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun NavIcon(
    item: NavItem,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val iconSize by animateDpAsState(
        targetValue = if (selected) 26.dp else 24.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "icon_size"
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Icon container
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background glow for selected state
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }

                // Icon
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                    tint = if (selected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(iconSize)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Indicator pill with spring transition
            val indicatorWidth by animateDpAsState(
                targetValue = if (selected) 16.dp else 0.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "indicator_width"
            )

            Box(
                modifier = Modifier
                    .size(width = indicatorWidth, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected)
                            MaterialTheme.colorScheme.primary
                        else
                            Color.Transparent
                    )
            )
        }
    }
}