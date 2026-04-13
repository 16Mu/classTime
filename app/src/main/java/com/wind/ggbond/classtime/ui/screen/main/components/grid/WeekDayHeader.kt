package com.wind.ggbond.classtime.ui.screen.main.components.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wind.ggbond.classtime.ui.theme.DesktopTransparencyLevel
import com.wind.ggbond.classtime.ui.theme.LocalScheduleColors
import com.wind.ggbond.classtime.ui.theme.wallpaperAwareBackground
import com.wind.ggbond.classtime.util.DateUtils
import java.time.LocalDate

@Composable
fun WeekDayHeaderAdaptive(
    dayOfWeek: Int,
    date: LocalDate? = null,
    isCompact: Boolean = false,
    compactProgress: Float = 0f,
    modifier: Modifier = Modifier
) {
    val isToday = date != null && date == LocalDate.now()

    val scheduleColors = LocalScheduleColors.current
    val sectionBackground = scheduleColors.sectionBackground
    val textPrimary = scheduleColors.textPrimary
    val todayHighlight = scheduleColors.todayHighlight

    val fullHeaderAlpha = 1f - compactProgress
    val compactHeaderAlpha = compactProgress

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .wallpaperAwareBackground(
                if (isToday) todayHighlight.copy(alpha = 0.5f)
                else sectionBackground,
                desktopLevel = DesktopTransparencyLevel.SEMI_TRANSPARENT
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(fullHeaderAlpha)
        ) {
            Text(
                text = DateUtils.getDayOfWeekShortName(dayOfWeek),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isToday) MaterialTheme.colorScheme.primary
                    else textPrimary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            if (date != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${date.monthValue}/${date.dayOfMonth.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                        else textPrimary.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Text(
            text = DateUtils.getDayOfWeekShortName(dayOfWeek).take(1),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Light,
            color = if (isToday) MaterialTheme.colorScheme.primary
                else textPrimary.copy(alpha = 0.5f),
            fontSize = 7.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(compactHeaderAlpha)
        )
    }
}

@Composable
fun WeekDayHeaderFixed(dayOfWeek: Int) {
    val isToday = LocalDate.now().dayOfWeek.value == dayOfWeek

    Box(
        modifier = Modifier
            .size(width = 110.dp, height = 50.dp)
            .wallpaperAwareBackground(
                if (isToday) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = DateUtils.getDayOfWeekShortName(dayOfWeek),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = DateUtils.getDayOfWeekName(dayOfWeek),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
