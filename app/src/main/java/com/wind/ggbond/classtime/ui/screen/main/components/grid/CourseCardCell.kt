package com.wind.ggbond.classtime.ui.screen.main.components.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.ui.theme.DesktopTransparencyLevel
import com.wind.ggbond.classtime.ui.theme.LocalDesktopModeEnabled
import com.wind.ggbond.classtime.ui.theme.LocalGlassEffectEnabled
import com.wind.ggbond.classtime.ui.theme.LocalWallpaperEnabled
import com.wind.ggbond.classtime.ui.theme.WallpaperTransparencyLevel
import com.wind.ggbond.classtime.ui.theme.contentColorForBackground
import com.wind.ggbond.classtime.ui.theme.wallpaperAwareBackground
import com.wind.ggbond.classtime.util.DateUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CourseCardCell(
    weekNumber: Int,
    course: Course,
    classTimes: List<ClassTime>,
    compactModeEnabled: Boolean = false,
    showWeekend: Boolean = true,
    onCourseClick: (Course) -> Unit,
    onCourseLongClick: ((Course, Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    staggerIndex: Int = 0,
    compactProgress: Float = 0f,
    semesterStartDate: java.time.LocalDate? = null,
    adjustmentInfo: CourseAdjustment? = null,
    courseColorMap: Map<String, String> = emptyMap()
) {
    val shouldShowThisWeek = remember(course.id, weekNumber, adjustmentInfo) {
        adjustmentInfo != null || course.weeks.contains(weekNumber)
    }

    val courseStartSection = course.startSection
    val courseEndSection = course.startSection + course.sectionCount - 1
    val courseStartTime = remember(classTimes, courseStartSection) {
        classTimes.find { it.sectionNumber == courseStartSection }?.startTime
    }
    val courseEndTime = remember(classTimes, courseEndSection) {
        classTimes.find { it.sectionNumber == courseEndSection }?.endTime
    }

    val now by remember {
        derivedStateOf { java.time.LocalDateTime.now() }
    }
    val today by remember {
        derivedStateOf { java.time.LocalDate.now() }
    }

    val currentWeek = remember(semesterStartDate, today) {
        if (semesterStartDate != null) {
            DateUtils.calculateWeekNumber(semesterStartDate, today)
        } else {
            val currentWeekOfYear = java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()
            now.get(currentWeekOfYear)
        }
    }

    val isPast = remember(weekNumber, currentWeek) {
        weekNumber < currentWeek
    }
    val isOngoing = remember(weekNumber, currentWeek, course.dayOfWeek, courseStartTime, courseEndTime) {
        weekNumber == currentWeek &&
            now.dayOfWeek.value == course.dayOfWeek &&
            courseStartTime != null && courseEndTime != null &&
            !now.toLocalTime().isBefore(courseStartTime) &&
            !now.toLocalTime().isAfter(courseEndTime)
    }

    val baseColor = remember(course.courseName, courseColorMap) {
        val dynamicColor = courseColorMap[course.courseName]
        if (dynamicColor != null) {
            try { Color(android.graphics.Color.parseColor(dynamicColor)) }
            catch (e: Exception) { Color(android.graphics.Color.parseColor(course.color)) }
        } else {
            Color(android.graphics.Color.parseColor(course.color))
        }
    }

    val baseTextColor = remember(baseColor) {
        contentColorForBackground(baseColor)
    }

    val textColor by remember {
        derivedStateOf {
            when {
                !shouldShowThisWeek -> baseTextColor.copy(alpha = 0.15f)
                isPast -> baseTextColor.copy(alpha = 0.5f)
                else -> baseTextColor
            }
        }
    }

    var showFullInfoDialog by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    var cellPositionY by remember { mutableStateOf(0f) }
    var cellCenterX by remember { mutableStateOf(0f) }

    val isWallpaperEnabledForCard = LocalWallpaperEnabled.current
    val glassEffectEnabledForCard = LocalGlassEffectEnabled.current
    val desktopModeEnabledForCard = LocalDesktopModeEnabled.current

    val wallpaperAlphaMultiplier = if (isWallpaperEnabledForCard) 1f
    else 1f

    val displayColor by remember {
        derivedStateOf {
            when {
                !shouldShowThisWeek -> baseColor.copy(alpha = 0.08f * wallpaperAlphaMultiplier)
                isPast -> baseColor.copy(alpha = 0.35f * wallpaperAlphaMultiplier)
                isOngoing -> baseColor.copy(alpha = wallpaperAlphaMultiplier)
                else -> baseColor.copy(alpha = 0.95f * wallpaperAlphaMultiplier)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .wallpaperAwareBackground(
                MaterialTheme.colorScheme.surface,
                level = WallpaperTransparencyLevel.FULL_TRANSPARENT,
                desktopLevel = DesktopTransparencyLevel.FULLY_TRANSPARENT
            )
            .padding(horizontal = 1.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(5.dp))
            .wallpaperAwareBackground(
                displayColor,
                desktopLevel = DesktopTransparencyLevel.OPAQUE
            )
            .then(
                if (isOngoing) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    )
                } else {
                    Modifier
                }
            )
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                cellPositionY = bounds.top
                cellCenterX = bounds.center.x
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(),
                onClick = { onCourseClick(course) },
                onLongClick = onCourseLongClick?.let { {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    it(course, cellPositionY, cellCenterX)
                } },
                onClickLabel = "查看详情"
            )
            .semantics {
                contentDescription = buildString {
                    append(course.courseName)
                    if (course.teacher.isNotEmpty()) {
                        append("，${course.teacher}老师")
                    }
                    if (course.classroom.isNotEmpty()) {
                        append("，在${course.classroom}")
                    }
                    append("，第${course.startSection}")
                    if (course.sectionCount > 1) {
                        append("-${course.startSection + course.sectionCount - 1}")
                    }
                    append("节")
                    if (isOngoing) {
                        append("，正在上课")
                    } else if (isPast) {
                        append("，已结束")
                    }
                    if (!shouldShowThisWeek) {
                        append("，本周不上课")
                    }
                    if (adjustmentInfo != null) {
                        append("，已调课")
                    }
                }
            }
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 2.dp, top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (adjustmentInfo != null) {
                Text(
                    text = "⟳",
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.4f)
                )
            }
            if (isOngoing) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        val useProportionalLayout = compactModeEnabled && !showWeekend

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalArrangement = if (useProportionalLayout) Arrangement.Top else Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start
        ) {
            val compactFontScale = when {
                !showWeekend -> 1.2f
                else -> 1.1f
            }
            val fontScale = lerpFloat(1.0f, compactFontScale, compactProgress)

            val (nameFontSize, nameLineHeight) = when {
                course.sectionCount >= 10 -> (16 * fontScale).sp to (18 * fontScale).sp
                course.sectionCount >= 8 -> (15 * fontScale).sp to (17 * fontScale).sp
                course.sectionCount >= 6 -> (14 * fontScale).sp to (16 * fontScale).sp
                course.sectionCount >= 4 -> (13 * fontScale).sp to (15 * fontScale).sp
                course.sectionCount >= 3 -> (12 * fontScale).sp to (14 * fontScale).sp
                course.sectionCount >= 2 -> (11.5 * fontScale).sp to (13.5 * fontScale).sp
                else -> (11 * fontScale).sp to (13 * fontScale).sp
            }

            if (useProportionalLayout) {
                CourseCardProportionalContent(
                    course = course,
                    shouldShowThisWeek = shouldShowThisWeek,
                    isPast = isPast,
                    textColor = textColor,
                    fontScale = fontScale,
                    nameFontSize = nameFontSize,
                    nameLineHeight = nameLineHeight
                )
            } else {
                CourseCardNormalContent(
                    course = course,
                    shouldShowThisWeek = shouldShowThisWeek,
                    isPast = isPast,
                    textColor = textColor,
                    fontScale = fontScale,
                    nameFontSize = nameFontSize,
                    nameLineHeight = nameLineHeight
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CourseCardProportionalContent(
    course: Course,
    shouldShowThisWeek: Boolean,
    isPast: Boolean,
    textColor: Color,
    fontScale: Float,
    nameFontSize: androidx.compose.ui.unit.TextUnit,
    nameLineHeight: androidx.compose.ui.unit.TextUnit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(3.5f),
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            text = course.courseName,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
            fontSize = nameFontSize,
            lineHeight = nameLineHeight,
            letterSpacing = 0.sp
        )
    }

    if (course.classroom.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(5f),
            contentAlignment = Alignment.CenterStart
        ) {
            var displayClassroom by remember(course.classroom) { mutableStateOf(course.classroom) }
            Text(
                text = displayClassroom,
                fontWeight = FontWeight.Medium,
                color = when {
                    !shouldShowThisWeek -> textColor.copy(alpha = 0.15f)
                    isPast -> textColor.copy(alpha = 0.5f)
                    else -> textColor.copy(alpha = 0.85f)
                },
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                fontSize = (10 * fontScale).sp,
                lineHeight = (12 * fontScale).sp,
                onTextLayout = { result ->
                    if (displayClassroom == course.classroom && result.lineCount > 1) {
                        val lastLineLen = result.getLineEnd(result.lineCount - 1) - result.getLineStart(result.lineCount - 1)
                        val firstLineLen = result.getLineEnd(0) - result.getLineStart(0)
                        if (firstLineLen > 0 && lastLineLen < firstLineLen * 0.4f) {
                            val mid = (course.classroom.length + 1) / 2
                            displayClassroom = course.classroom.substring(0, mid) + "\n" + course.classroom.substring(mid)
                        }
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .wallpaperAwareBackground(
                        when {
                            !shouldShowThisWeek -> textColor.copy(alpha = 0.03f)
                            isPast -> textColor.copy(alpha = 0.08f)
                            else -> textColor.copy(alpha = 0.12f)
                        },
                        desktopLevel = DesktopTransparencyLevel.OPAQUE
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    } else {
        Spacer(modifier = Modifier.weight(5f))
    }

    if (course.teacher.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2f),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = course.teacher,
                fontWeight = FontWeight.Normal,
                color = when {
                    !shouldShowThisWeek -> textColor.copy(alpha = 0.15f)
                    isPast -> textColor.copy(alpha = 0.5f)
                    else -> textColor.copy(alpha = 0.75f)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = (10 * fontScale).sp,
                lineHeight = (12 * fontScale).sp
            )
        }
    } else {
        Spacer(modifier = Modifier.weight(2f))
    }

    if (course.credit > 0f) {
        val creditText = if (course.credit % 1 == 0f) {
            "${course.credit.toInt()}学分"
        } else {
            "${course.credit}学分"
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.5f),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = creditText,
                fontWeight = FontWeight.Normal,
                color = textColor.copy(
                    alpha = if (!shouldShowThisWeek) 0.12f
                    else if (isPast) 0.4f
                    else 0.60f
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = (9 * fontScale).sp,
                lineHeight = (11 * fontScale).sp
            )
        }
    } else {
        Spacer(modifier = Modifier.weight(1.5f))
    }
}

@Composable
private fun CourseCardNormalContent(
    course: Course,
    shouldShowThisWeek: Boolean,
    isPast: Boolean,
    textColor: Color,
    fontScale: Float,
    nameFontSize: androidx.compose.ui.unit.TextUnit,
    nameLineHeight: androidx.compose.ui.unit.TextUnit
) {
    Text(
        text = course.courseName,
        fontWeight = FontWeight.Bold,
        color = textColor,
        maxLines = Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
        fontSize = nameFontSize,
        lineHeight = nameLineHeight,
        letterSpacing = (-0.5).sp
    )

    if (course.classroom.isNotEmpty() || course.teacher.isNotEmpty()) {
        val detailFontSize = when {
            course.sectionCount >= 6 -> (10 * fontScale).sp
            course.sectionCount >= 4 -> (9 * fontScale).sp
            course.sectionCount >= 2 -> (9 * fontScale).sp
            else -> (8 * fontScale).sp
        }

        if (course.classroom.isNotEmpty()) {
            var displayClassroom by remember(course.classroom) { mutableStateOf(course.classroom) }
            Text(
                text = displayClassroom,
                fontWeight = FontWeight.Medium,
                color = when {
                    !shouldShowThisWeek -> textColor.copy(alpha = 0.15f)
                    isPast -> textColor.copy(alpha = 0.5f)
                    else -> textColor.copy(alpha = 0.95f)
                },
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                fontSize = detailFontSize,
                lineHeight = (detailFontSize.value + 2).sp,
                onTextLayout = { result ->
                    if (displayClassroom == course.classroom && result.lineCount > 1) {
                        val lastLineLen = result.getLineEnd(result.lineCount - 1) - result.getLineStart(result.lineCount - 1)
                        val firstLineLen = result.getLineEnd(0) - result.getLineStart(0)
                        if (firstLineLen > 0 && lastLineLen < firstLineLen * 0.4f) {
                            val mid = (course.classroom.length + 1) / 2
                            displayClassroom = course.classroom.substring(0, mid) + "\n" + course.classroom.substring(mid)
                        }
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .wallpaperAwareBackground(
                        when {
                            !shouldShowThisWeek -> textColor.copy(alpha = 0.03f)
                            isPast -> textColor.copy(alpha = 0.08f)
                            else -> textColor.copy(alpha = 0.12f)
                        },
                        desktopLevel = DesktopTransparencyLevel.OPAQUE
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        if (course.teacher.isNotEmpty() && course.sectionCount >= 2) {
            Text(
                text = course.teacher,
                fontWeight = FontWeight.Light,
                color = textColor.copy(
                    alpha = if (!shouldShowThisWeek) 0.12f
                    else if (isPast) 0.35f
                    else 0.50f
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = detailFontSize,
                lineHeight = (detailFontSize.value + 2).sp
            )
        }

        if (course.credit > 0f && course.sectionCount >= 2) {
            val creditText = if (course.credit % 1 == 0f) {
                "${course.credit.toInt()}学分"
            } else {
                "${course.credit}学分"
            }
            Text(
                text = creditText,
                fontWeight = FontWeight.Medium,
                color = textColor.copy(
                    alpha = if (!shouldShowThisWeek) 0.12f
                    else if (isPast) 0.4f
                    else 0.60f
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = detailFontSize,
                lineHeight = (detailFontSize.value + 2).sp
            )
        }
    }
}
