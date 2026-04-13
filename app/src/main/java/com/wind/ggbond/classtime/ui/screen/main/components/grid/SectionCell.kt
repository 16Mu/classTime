package com.wind.ggbond.classtime.ui.screen.main.components.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.ui.theme.DesktopTransparencyLevel
import com.wind.ggbond.classtime.ui.theme.LocalScheduleColors
import com.wind.ggbond.classtime.ui.theme.wallpaperAwareBackground

@Composable
fun SectionCellAdaptive(
    section: Int,
    classTime: ClassTime? = null,
    isCompact: Boolean = false,
    compactProgress: Float = 0f,
    modifier: Modifier = Modifier
) {
    val scheduleColors = LocalScheduleColors.current
    val sectionBackground = scheduleColors.sectionBackground
    val textSecondary = scheduleColors.textSecondary
    val gridLine = scheduleColors.gridLine

    val alpha = lerpFloat(1f, 0.4f, compactProgress)

    val targetBackground = if (isCompact) gridLine else sectionBackground
    val backgroundColor = lerpColor(sectionBackground, targetBackground, compactProgress)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wallpaperAwareBackground(
                backgroundColor.copy(alpha = 0.65f),
                desktopLevel = DesktopTransparencyLevel.SEMI_TRANSPARENT
            )
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "$section",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = textSecondary.copy(alpha = alpha),
                fontSize = if (isCompact) 7.sp else 11.sp,
                textAlign = TextAlign.Center
            )

            if (!isCompact && classTime != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = classTime.startTime.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary.copy(alpha = alpha * 0.6f),
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 8.sp
                )
                Text(
                    text = classTime.endTime.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary.copy(alpha = alpha * 0.6f),
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 8.sp
                )
            }
        }
    }
}

@Composable
fun SectionCellFixed(section: Int) {
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 90.dp)
            .wallpaperAwareBackground(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$section",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
