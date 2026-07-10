package com.kishan.attendmate.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import com.kishan.attendmate.ui.theme.NavContentBottomPadding

/**
 * GlassNavScaffold
 * A reusable wrapper that places the content layer and AttendMateNavigationBar as siblings in a Box.
 * It provides a HazeState so that the navigation bar blurs the content scrolling behind it.
 */
@Composable
fun GlassNavScaffold(
    selectedRoute: String,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    val hazeState = remember { HazeState() }
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues()

    Box(modifier = modifier.fillMaxSize()) {
        // Content Layer - takes the HazeState to act as the blur source
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState)
        ) {
            // Hand the content the top inset (status bars) and bottom inset (clear nav bar + FAB)
            content(
                PaddingValues(
                    top = statusBarsPadding.calculateTopPadding(),
                    bottom = NavContentBottomPadding
                )
            )
        }

        // Frosted Navigation Bar - overlaid at the bottom
        AttendMateNavigationBar(
            selectedRoute = selectedRoute,
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
