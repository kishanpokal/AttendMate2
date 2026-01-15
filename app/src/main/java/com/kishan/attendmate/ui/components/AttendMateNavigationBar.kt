package com.kishan.attendmate.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
import com.kishan.attendmate.MainActivity
import com.kishan.attendmate.ui.attendance.AddAttendanceActivity
import com.kishan.attendmate.ui.attendance.AttendanceListActivity
import com.kishan.attendmate.ui.analytics.AnalyticsActivity
import com.kishan.attendmate.ui.settings.SettingsActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState



/* ---------- NAV ITEM ---------- */
data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/* ---------- iOS-STYLE GLASSMORPHIC NAV BAR ---------- */
/* ---------- UPDATED: TRUE GLASSMORPHIC NAV BAR ---------- */
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
        NavItem("attendance", "Attendance", Icons.Filled.ListAlt, Icons.Outlined.ListAlt),
        NavItem("analytics", "Analytics", Icons.Filled.BarChart, Icons.Outlined.BarChart),
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
    ) {
        // Glassmorphic Navigation Container
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(70.dp)
        ) {
            // Glass background with blur effect (transparent!)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(35.dp))
                    .background(
                        // KEY CHANGE: Much lower alpha for true transparency
                        if (isDark)
                            Color(0xFF1C1C1E).copy(alpha = 0.25f) // Was 0.72f
                        else
                            Color(0xFFF2F2F7).copy(alpha = 0.35f) // Was 0.72f
                    )
                    // Add blur effect for glass look
                    .blur(20.dp)
            )

            // Brighter border for glass effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp, // Increased from 0.5dp
                        color = if (isDark)
                            Color.White.copy(alpha = 0.25f) // Increased from 0.15f
                        else
                            Color.White.copy(alpha = 0.8f), // Increased from 0.6f
                        shape = RoundedCornerShape(35.dp)
                    )
            )

            // Inner content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(35.dp))
            ) {
                // Subtle gradient for depth
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.08f else 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.take(2).forEach { item ->
                        GlassNavIcon(
                            item = item,
                            selected = selectedRoute == item.route,
                            isDark = isDark,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                navigate(item.route)
                            }
                        )
                    }

                    // Spacer for FAB
                    Spacer(modifier = Modifier.width(72.dp))

                    navItems.takeLast(2).forEach { item ->
                        GlassNavIcon(
                            item = item,
                            selected = selectedRoute == item.route,
                            isDark = isDark,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                navigate(item.route)
                            }
                        )
                    }
                }
            }
        }

        // Premium FAB with glass effect
        GlassFloatingActionButton(
            isDark = isDark,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                context.startActivity(Intent(context, AddAttendanceActivity::class.java))
            }
        )
    }
}

/* ---------- UPDATED: GLASS FAB WITH MORE TRANSPARENCY ---------- */
@Composable
private fun BoxScope.GlassFloatingActionButton(
    isDark: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-5).dp)
            .zIndex(3f)
    ) {
        // Outer glow
        Box(
            modifier = Modifier
                .size(90.dp)
                .align(Alignment.Center)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.5f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Glass FAB container - MORE TRANSPARENT
        Box(
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(
                    // KEY CHANGE: Lower alpha for transparency
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) // Was 0.85f
                )
                .border(
                    width = 2.dp, // Increased from 1.5dp
                    color = Color.White.copy(alpha = 0.5f), // Increased from 0.4f
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Attendance",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/* ---------- ALTERNATIVE: EXTRA TRANSPARENT SLEEK VERSION ---------- */
@Composable
fun AttendMateNavigationBarSleek(
    selectedRoute: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    val navItems = listOf(
        NavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("attendance", "Attendance", Icons.Filled.ListAlt, Icons.Outlined.ListAlt),
        NavItem("analytics", "Analytics", Icons.Filled.BarChart, Icons.Outlined.BarChart),
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
    ) {
        // ULTRA transparent glass background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(
                    // KEY CHANGE: Very low alpha for see-through effect
                    if (isDark)
                        Color(0xFF1C1C1E).copy(alpha = 0.2f) // Was 0.72f
                    else
                        Color(0xFFF2F2F7).copy(alpha = 0.3f) // Was 0.72f
                )
                .blur(15.dp) // Add blur for glass effect
        )

        // Subtle top border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp) // Increased from 0.5dp
                .background(
                    if (isDark)
                        Color.White.copy(alpha = 0.2f) // Increased from 0.15f
                    else
                        Color.Black.copy(alpha = 0.15f) // Increased from 0.1f
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.take(2).forEach { item ->
                SleekGlassNavIcon(
                    item = item,
                    selected = selectedRoute == item.route,
                    isDark = isDark,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        navigate(item.route)
                    }
                )
            }

            Box(modifier = Modifier.size(56.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), // More transparent
                            CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                )
                FloatingActionButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        context.startActivity(Intent(context, AddAttendanceActivity::class.java))
                    },
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(24.dp))
                }
            }

            navItems.takeLast(2).forEach { item ->
                SleekGlassNavIcon(
                    item = item,
                    selected = selectedRoute == item.route,
                    isDark = isDark,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        navigate(item.route)
                    }
                )
            }
        }
    }
}

/* ---------- GLASS NAV ICON ---------- */
@Composable
private fun GlassNavIcon(
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

    val iconTint by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primary
        else
            if (isDark) Color(0xFFEBEBF5).copy(alpha = 0.6f)
            else Color(0xFF3C3C43).copy(alpha = 0.6f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "tint"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
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
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp)
            ) {
                // Selected background with glass effect
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                    )
                }

                // Icon
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Label with smooth fade
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(animationSpec = tween(200)) + expandVertically(),
                exit = fadeOut(animationSpec = tween(150)) + shrinkVertically()
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = iconTint,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/* ---------- ALTERNATIVE: MODERN FLOATING GLASS DESIGN ---------- */
@Composable
fun AttendMateNavigationBarModern(
    selectedRoute: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    val navItems = listOf(
        NavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("attendance", "Attendance", Icons.Filled.ListAlt, Icons.Outlined.ListAlt),
        NavItem("analytics", "Analytics", Icons.Filled.BarChart, Icons.Outlined.BarChart),
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
        }?.let {
            context.startActivity(it)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Left group
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                navItems.take(2).forEach { item ->
                    ModernGlassNavButton(
                        item = item,
                        selected = selectedRoute == item.route,
                        isDark = isDark,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            navigate(item.route)
                        }
                    )
                }
            }

            // Center FAB with glass
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .offset(y = (-8).dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )

                FloatingActionButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        context.startActivity(Intent(context, AddAttendanceActivity::class.java))
                    },
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxSize(),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Right group
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                navItems.takeLast(2).forEach { item ->
                    ModernGlassNavButton(
                        item = item,
                        selected = selectedRoute == item.route,
                        isDark = isDark,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            navigate(item.route)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernGlassNavButton(
    item: NavItem,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(if (selected) 70.dp else 56.dp)
    ) {
        // Glass background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    if (selected)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    else
                        if (isDark) Color(0xFF2C2C2E).copy(alpha = 0.7f)
                        else Color(0xFFF2F2F7).copy(alpha = 0.7f)
                )
                .border(
                    width = 0.5.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.15f)
                    else Color.White.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(28.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                    tint = if (selected)
                        MaterialTheme.colorScheme.primary
                    else
                        if (isDark) Color(0xFFEBEBF5).copy(alpha = 0.6f)
                        else Color(0xFF3C3C43).copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )

                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/* ---------- ALTERNATIVE: SLEEK MINIMAL GLASS ---------- */
@Composable
private fun SleekGlassNavIcon(
    item: NavItem,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            tint = if (selected)
                MaterialTheme.colorScheme.primary
            else
                if (isDark) Color(0xFFEBEBF5).copy(alpha = 0.6f)
                else Color(0xFF3C3C43).copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Active indicator
        Box(
            modifier = Modifier
                .width(if (selected) 20.dp else 6.dp)
                .height(3.dp)
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