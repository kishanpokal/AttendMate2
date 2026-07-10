package com.kishan.attendmate.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kishan.attendmate.ui.theme.CardStyle
import com.kishan.attendmate.ui.theme.SpaceMD
import com.kishan.attendmate.ui.theme.SpaceSM
import com.kishan.attendmate.ui.theme.statusColors

@Composable
fun SubjectListItem(
    title: String,
    attendancePercentage: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = CardStyle.shape,
        colors = CardDefaults.cardColors(containerColor = CardStyle.containerColor()),
        border = CardStyle.border(),
        elevation = CardDefaults.cardElevation(defaultElevation = CardStyle.elevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpaceMD),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceSM)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            AttendanceRing(
                percentage = attendancePercentage,
                size = 48.dp, // Compact size for list item
                strokeWidth = 4.dp,
                showText = true // We can show text or hide depending on design, but let's show it by default
            )
        }
    }
}
