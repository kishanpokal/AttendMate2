package com.kishan.attendmate.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kishan.attendmate.ui.theme.ElevationHigh
import com.kishan.attendmate.ui.theme.RadiusLG
import com.kishan.attendmate.ui.theme.SpaceLG
import com.kishan.attendmate.ui.theme.SpaceMD

@Composable
fun ScrapingAnimationOverlay(
    phase: ScrapePhase,
    statusText: String,
    onCancel: () -> Unit
) {
    if (phase == ScrapePhase.IDLE) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(RadiusLG),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = ElevationHigh)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpaceLG),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Syncing College Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(SpaceMD))
                
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
                
                Spacer(modifier = Modifier.height(SpaceMD))
                
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(SpaceLG))
                
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}