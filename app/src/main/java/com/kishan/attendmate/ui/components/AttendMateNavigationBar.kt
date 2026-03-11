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
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.ui.attendance.AddAttendanceActivity
import com.kishan.attendmate.ui.attendance.AttendanceListActivity
import com.kishan.attendmate.ui.analytics.AnalyticsActivity
import com.kishan.attendmate.ui.settings.SettingsActivity
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
    modifier: Modifier = Modifier
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // Increased bottom padding to lift it higher off the screen edge
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        // Main navigation container
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(68.dp) // Slightly slimmer
                // Increased drop shadow
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(34.dp),
                    ambientColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                    spotColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(34.dp))
        ) {
            // Glass background with lower alpha for transparency
            TransparentGlassBackground(isDark = isDark)

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

        // Floating Action Button - repositioned for the floating pill
        FloatingActionButton(
            isDark = isDark,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                context.startActivity(Intent(context, AddAttendanceActivity::class.java))
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Adjusted offset to straddle the top of the pill perfectly
                .offset(y = (-30).dp)
        )
    }
}

@Composable
private fun TransparentGlassBackground(isDark: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        val color1 = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.35f else 0.5f)
        val color2 = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.5f else 0.7f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(color1, color2)
                    )
                )
        )

        // Top highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (isDark) 0.2f else 0.4f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.1f else 0.15f),
                    shape = RoundedCornerShape(34.dp)
                )
        )
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

            // Indicator dot
            Box(
                modifier = Modifier
                    .size(
                        width = if (selected) 5.dp else 0.dp,
                        height = if (selected) 5.dp else 0.dp
                    )
                    .clip(CircleShape)
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

@Composable
private fun FloatingActionButton(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_glow")
    val primaryColor = MaterialTheme.colorScheme.primary

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "press"
    )

    Box(
        modifier = modifier.zIndex(10f),
        contentAlignment = Alignment.Center
    ) {
        // Animated glow
        Canvas(
            modifier = Modifier.size(88.dp)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.3f * glowAlpha),
                        primaryColor.copy(alpha = 0.1f * glowAlpha),
                        Color.Transparent
                    ),
                    center = center
                ),
                radius = size.width / 2,
                center = center
            )
        }

        // FAB button
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(pressScale)
        ) {
            // Gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .shadow(8.dp, CircleShape) // Added shadow to FAB itself
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                primaryColor,
                                primaryColor.copy(alpha = 0.8f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
            )

            // Glass overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.3f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f)
                            )
                        )
                    )
            )

            // Outer ring border
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.5.dp,
                        color = Color.White.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )

            // Clickable area with icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            isPressed = true
                            onClick()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Attendance",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Reset press state
        LaunchedEffect(isPressed) {
            if (isPressed) {
                delay(100)
                isPressed = false
            }
        }
    }
}