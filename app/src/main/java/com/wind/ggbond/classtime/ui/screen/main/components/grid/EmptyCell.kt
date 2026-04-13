package com.wind.ggbond.classtime.ui.screen.main.components.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wind.ggbond.classtime.ui.theme.DesktopTransparencyLevel
import com.wind.ggbond.classtime.ui.theme.WallpaperAwareSurface
import com.wind.ggbond.classtime.ui.theme.wallpaperAwareBackground
import com.wind.ggbond.classtime.util.DateUtils
import java.time.LocalDate

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmptyCell(
    dayOfWeek: Int,
    section: Int,
    isSelected: Boolean,
    isCompact: Boolean = false,
    onEmptyCellClick: (Int, Int) -> Unit,
    onEmptyCellLongClick: ((Int, Int, Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    date: LocalDate? = null
) {
    val isToday = date != null && date == LocalDate.now()
    val haptic = LocalHapticFeedback.current
    var cellPositionY by remember { mutableStateOf(0f) }
    var cellCenterX by remember { mutableStateOf(0f) }

    WallpaperAwareSurface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        desktopLevel = DesktopTransparencyLevel.FULLY_TRANSPARENT
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    cellPositionY = bounds.top
                    cellCenterX = bounds.center.x
                }
                .then(
                    if (isToday && !isCompact) {
                        Modifier.wallpaperAwareBackground(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.04f),
                            desktopLevel = DesktopTransparencyLevel.FULLY_TRANSPARENT
                        )
                    } else {
                        Modifier
                    }
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(),
                    onClick = { onEmptyCellClick(dayOfWeek, section) },
                    onLongClick = onEmptyCellLongClick?.let { {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it(dayOfWeek, section, cellPositionY, cellCenterX)
                    } }
                )
                .semantics {
                    contentDescription = "${DateUtils.getDayOfWeekName(dayOfWeek)}，第${section}节，空闲" +
                            if (isToday) "，今天" else ""
                },
            contentAlignment = Alignment.Center
        ) {
            if (!isCompact) {
                val todayDotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                val normalDotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dotColor = if (isToday) todayDotColor else normalDotColor

                    val spacing = 20f
                    val dotRadius = 1f
                    var x = spacing
                    while (x < size.width) {
                        var y = spacing
                        while (y < size.height) {
                            drawCircle(
                                color = dotColor,
                                radius = dotRadius,
                                center = Offset(x, y)
                            )
                            y += spacing
                        }
                        x += spacing
                    }
                }
            }
        }
    }
}
