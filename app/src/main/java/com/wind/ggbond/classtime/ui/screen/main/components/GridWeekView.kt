package com.wind.ggbond.classtime.ui.screen.main.components

import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.local.entity.Exam
import com.wind.ggbond.classtime.ui.theme.DesktopTransparencyLevel
import com.wind.ggbond.classtime.ui.theme.LocalScheduleColors
import com.wind.ggbond.classtime.ui.theme.WallpaperAwareSurface
import com.wind.ggbond.classtime.ui.theme.wallpaperAwareBackground
import com.wind.ggbond.classtime.ui.screen.main.components.grid.CourseCardCell
import com.wind.ggbond.classtime.ui.screen.main.components.grid.EmptyCell
import com.wind.ggbond.classtime.ui.screen.main.components.grid.GridLayoutCalculator
import com.wind.ggbond.classtime.ui.screen.main.components.grid.SectionCellAdaptive
import com.wind.ggbond.classtime.ui.screen.main.components.grid.WeekDayHeaderAdaptive
import com.wind.ggbond.classtime.ui.screen.main.components.grid.rememberGridLayoutCalculator
import com.wind.ggbond.classtime.ui.screen.main.components.grid.COMPACT_ANIM_DURATION_MS
import com.wind.ggbond.classtime.ui.screen.main.components.grid.TOTAL_COMPACT_ANIM_MS
import com.wind.ggbond.classtime.ui.screen.main.components.grid.staggerProgress
import com.wind.ggbond.classtime.ui.screen.main.components.grid.lerpFloat
import com.wind.ggbond.classtime.ui.screen.main.components.grid.MAX_DAY_STAGGER_INDEX
import com.wind.ggbond.classtime.ui.screen.main.components.grid.DAY_STAGGER_DELAY_MS
import com.wind.ggbond.classtime.ui.screen.main.components.grid.MAX_SECTION_STAGGER_INDEX
import com.wind.ggbond.classtime.ui.screen.main.components.grid.SECTION_STAGGER_DELAY_MS

private data class GridSlot(
    val section: Int,
    val dayOfWeek: Int,
    val dayIndex: Int,
    val course: Course? = null,
    val exam: Exam? = null,
    val isCovered: Boolean = false
)

@Composable
fun GridWeekView(
    weekNumber: Int,
    coursesMap: Map<Int, List<Course>>,
    classTimes: List<ClassTime>,
    compactModeEnabled: Boolean = false,
    showWeekend: Boolean = true,
    onCourseClick: (Course) -> Unit,
    onCourseLongClick: ((Course) -> Unit)? = null,
    onEmptyCellClick: ((dayOfWeek: Int, section: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    semesterStartDate: java.time.LocalDate? = null,
    exams: List<Exam> = emptyList(),
    onExamClick: (Exam) -> Unit = {},
    onSlotActionAdd: ((dayOfWeek: Int, section: Int, weekNumber: Int) -> Unit)? = null,
    onSlotActionAdjust: ((Course) -> Unit)? = null,
    onSlotActionDelete: ((Course, Int) -> Unit)? = null,
    onSlotActionCopy: ((Course, Int) -> Unit)? = null,
    onSlotActionPaste: ((Int, Int) -> Unit)? = null,
    hasClipboard: Boolean = false,
    currentWeekNumber: Int = weekNumber,
    getAdjustmentInfo: ((Long, Int, Int, Int) -> CourseAdjustment?)? = null,
    courseColorMap: Map<String, String> = emptyMap(),
    isWallpaperEnabled: Boolean = false,
    displayMode: Boolean = true
) {
    var selectedSlotForAction by remember { mutableStateOf<Triple<Int, Int, List<Course>>?>(null) }
    var courseToDelete by remember { mutableStateOf<Pair<Course, Int>?>(null) }
    var slotPositionY by remember { mutableStateOf(0f) }
    var slotPositionX by remember { mutableStateOf(0f) }
    var panelHeightPx by remember { mutableStateOf(0f) }
    var panelWidthPx by remember { mutableStateOf(0f) }

    val layoutCalculator = rememberGridLayoutCalculator(
        coursesMap = coursesMap,
        classTimes = classTimes,
        showWeekend = showWeekend,
        compactModeEnabled = compactModeEnabled,
        weekNumber = weekNumber
    )

    val maxSection = layoutCalculator.maxSection
    val sectionsWithCourses by remember(compactModeEnabled, weekNumber, coursesMap) {
        derivedStateOf { layoutCalculator.sectionsWithCourses }
    }
    val sectionsWithCoursesRaw = sectionsWithCourses
    val dayRange = layoutCalculator.dayRange
    val daysWithCourses by remember(weekNumber, showWeekend, coursesMap) {
        derivedStateOf { layoutCalculator.daysWithCourses }
    }

    val compactSectionHeight = layoutCalculator.compactSectionHeight
    val compactDayWidth = layoutCalculator.compactDayWidth

    val compactAnimatable = remember { Animatable(if (compactModeEnabled) 1f else 0f) }
    LaunchedEffect(compactModeEnabled) {
        val target = if (compactModeEnabled) 1f else 0f
        val distance = kotlin.math.abs(target - compactAnimatable.value)
        val duration = (TOTAL_COMPACT_ANIM_MS * distance).toInt().coerceAtLeast(50)
        compactAnimatable.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = duration, easing = LinearEasing)
        )
    }
    val compactLinearProgress by compactAnimatable.asState()

    val sortedCoursesByDay by remember(coursesMap) {
        derivedStateOf {
            coursesMap.mapValues { entry ->
                entry.value.sortedBy { it.startSection }
            }
        }
    }

    val examsByKey by remember(exams) {
        derivedStateOf {
            exams.associateBy { it.dayOfWeek to it.startSection }
        }
    }

    val today = java.time.LocalDate.now()

    val scheduleColors = LocalScheduleColors.current
    val sectionBackground = scheduleColors.sectionBackground
    val textPrimary = scheduleColors.textPrimary
    val textSecondary = scheduleColors.textSecondary
    val gridLine = scheduleColors.gridLine

    val weekDates = remember(semesterStartDate, weekNumber) {
        if (semesterStartDate != null) {
            val weekStartDate = semesterStartDate.plusWeeks((weekNumber - 1).toLong())
            (0..6).map { dayOffset ->
                weekStartDate.plusDays(dayOffset.toLong())
            }
        } else {
            emptyList()
        }
    }

    val hasNoCourseThisWeek = daysWithCourses.isEmpty() && exams.isEmpty()
    val columnDividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val rowDividerColor = gridLine.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isWallpaperEnabled) {
                    Modifier
                } else {
                    Modifier.wallpaperAwareBackground(MaterialTheme.colorScheme.background)
                }
            )
    ) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (exams.isNotEmpty()) {
            ExamBanner(
                exams = exams,
                currentWeek = weekNumber,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                onExamClick = onExamClick
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .wallpaperAwareBackground(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    desktopLevel = DesktopTransparencyLevel.SEMI_TRANSPARENT
                )
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                )
        ) {
            val dayCount = dayRange.count()
            val dayAreaWidth = (maxWidth - 40.dp).coerceAtLeast(0.dp)
            val fullWidthPerDay = if (dayCount > 0) dayAreaWidth / dayCount else 0.dp

            val hasAnyCourseInWeek = daysWithCourses.isNotEmpty()
            val emptyDaysCount = if (hasAnyCourseInWeek) {
                dayRange.count { it !in daysWithCourses }
            } else {
                0
            }
            val nonEmptyCount = dayCount - emptyDaysCount

            val compactNonEmptyWidth = if (hasAnyCourseInWeek && nonEmptyCount > 0) {
                val remainingWidth = (dayAreaWidth - compactDayWidth * emptyDaysCount).coerceAtLeast(0.dp)
                remainingWidth / nonEmptyCount
            } else {
                fullWidthPerDay
            }

            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(48.dp)
                        .wallpaperAwareBackground(
                            sectionBackground,
                            desktopLevel = DesktopTransparencyLevel.SEMI_TRANSPARENT
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "节次",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary,
                        fontSize = 10.sp
                    )
                }

                dayRange.forEachIndexed { index, dayOfWeek ->
                    key(dayOfWeek) {
                    val hasClassOnThisDay = dayOfWeek in daysWithCourses
                    val dateForDay = if (weekDates.isNotEmpty() && dayOfWeek <= weekDates.size) {
                        weekDates[dayOfWeek - 1]
                    } else null

                    val dayDelay = (index.coerceAtMost(MAX_DAY_STAGGER_INDEX)) * DAY_STAGGER_DELAY_MS
                    val dayCompactProgress = staggerProgress(compactLinearProgress, dayDelay)
                    val compactWidth = if (hasClassOnThisDay) compactNonEmptyWidth else compactDayWidth
                    val dayWidth = lerp(fullWidthPerDay, compactWidth, dayCompactProgress)

                    WeekDayHeaderAdaptive(
                        dayOfWeek = dayOfWeek,
                        date = dateForDay,
                        isCompact = !hasClassOnThisDay || compactModeEnabled,
                        compactProgress = if (!hasClassOnThisDay) dayCompactProgress else 0f,
                        modifier = Modifier
                            .width(dayWidth)
                            .then(
                                if (index < dayCount - 1) {
                                    Modifier.drawBehind {
                                        drawLine(
                                            brush = SolidColor(columnDividerColor),
                                            start = Offset(size.width, 0f),
                                            end = Offset(size.width, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    )
                    }
                }
            }
        }

        val fixedHeightPerSection = 80.dp
        val gridScrollState = rememberScrollState()
        val density = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val availableHeight = maxHeight
            val dayCount = dayRange.count()
            val dayAreaWidth = (maxWidth - 40.dp).coerceAtLeast(0.dp)
            val fullWidthPerDay = if (dayCount > 0) dayAreaWidth / dayCount else 0.dp

            val hasAnyCourseInWeek = daysWithCourses.isNotEmpty()
            val emptyDaysCount = if (hasAnyCourseInWeek) {
                dayRange.count { it !in daysWithCourses }
            } else {
                0
            }
            val nonEmptyDayCount = dayCount - emptyDaysCount

            val compactNonEmptyWidth = if (hasAnyCourseInWeek && nonEmptyDayCount > 0) {
                val remainingWidth = (dayAreaWidth - compactDayWidth * emptyDaysCount).coerceAtLeast(0.dp)
                remainingWidth / nonEmptyDayCount
            } else {
                fullWidthPerDay
            }

            val totalHeight = if (displayMode) availableHeight else fixedHeightPerSection * maxSection
            val sectionCount = maxSection
            val fullHeightPerSection = if (displayMode) {
                if (sectionCount > 0) totalHeight / sectionCount else 0.dp
            } else {
                fixedHeightPerSection
            }

            val hasAnySectionWithCourse = sectionsWithCoursesRaw.isNotEmpty()
            val emptySectionCount = if (hasAnySectionWithCourse) {
                (1..maxSection).count { it !in sectionsWithCoursesRaw }
            } else {
                0
            }
            val nonEmptySectionCount = sectionCount - emptySectionCount

            val compactNonEmptyHeight = if (hasAnySectionWithCourse && nonEmptySectionCount > 0) {
                val remainingHeight = (totalHeight - compactSectionHeight * emptySectionCount).coerceAtLeast(0.dp)
                remainingHeight / nonEmptySectionCount
            } else {
                fullHeightPerSection
            }

            val rowHeights = (1..maxSection).map { section ->
                val hasClassThisSection = section in sectionsWithCoursesRaw
                val sectionDelay = ((section - 1).coerceAtMost(MAX_SECTION_STAGGER_INDEX)) * SECTION_STAGGER_DELAY_MS
                val sectionCompactProgress = staggerProgress(compactLinearProgress, sectionDelay)
                val compactHeight = if (hasClassThisSection) compactNonEmptyHeight else compactSectionHeight
                lerp(fullHeightPerSection, compactHeight, sectionCompactProgress)
            }

            val dayWidths = dayRange.mapIndexed { index, dayOfWeek ->
                val hasClassOnThisDay = dayOfWeek in daysWithCourses
                val dayDelay = (index.coerceAtMost(MAX_DAY_STAGGER_INDEX)) * DAY_STAGGER_DELAY_MS
                val dayCompactProgress = staggerProgress(compactLinearProgress, dayDelay)
                val compactWidth = if (hasClassOnThisDay) compactNonEmptyWidth else compactDayWidth
                lerp(fullWidthPerDay, compactWidth, dayCompactProgress)
            }

            val sectionColumnWidth = 40.dp
            val sectionColumnWidthPx = with(density) { sectionColumnWidth.toPx() }
            val dayWidthsPx = dayWidths.map { with(density) { it.toPx() } }
            val rowHeightsPx = rowHeights.map { with(density) { it.toPx() } }
            val rowYOffsetsPx = rowHeightsPx.runningFold(0f) { acc, h -> acc + h }
            val isAdaptiveMode = displayMode

            val slots = remember(maxSection, dayRange, sortedCoursesByDay, examsByKey) {
                val result = mutableListOf<GridSlot>()
                for (section in 1..maxSection) {
                    for ((dayIndex, dayOfWeek) in dayRange.withIndex()) {
                        val examAtSection = examsByKey[dayOfWeek to section]
                        val dayCourses = sortedCoursesByDay[dayOfWeek] ?: emptyList()
                        val courseStartingHere = dayCourses.find { it.startSection == section }
                        val isCoveredByCourse = dayCourses.any {
                            it.startSection < section && it.startSection + it.sectionCount > section
                        }
                        val isCoveredByExam = (1 until section).any { s ->
                            val ex = examsByKey[dayOfWeek to s]
                            ex != null && s + ex.sectionCount > section
                        }

                        result.add(GridSlot(
                            section = section,
                            dayOfWeek = dayOfWeek,
                            dayIndex = dayIndex,
                            course = courseStartingHere,
                            exam = examAtSection,
                            isCovered = isCoveredByCourse || isCoveredByExam
                        ))
                    }
                }
                result
            }

            val contentSlots = slots.filter { !it.isCovered && (it.course != null || it.exam != null) }
            val emptySlots = slots.filter { !it.isCovered && it.course == null && it.exam == null }

            val dividerThicknessPx = with(density) { 0.5.dp.toPx() }
            val rowDivColor = rowDividerColor
            val colDivColor = columnDividerColor

            val measuredRowHeights = remember { mutableStateOf<List<Float>>(emptyList()) }

            val scrollModifier = if (!displayMode) Modifier.verticalScroll(gridScrollState) else Modifier

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(scrollModifier)
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (displayMode) Modifier.height(availableHeight) else Modifier)
                    .drawBehind {
                        val rowH = measuredRowHeights.value
                        if (rowH.isEmpty()) return@drawBehind

                        val sectionW = sectionColumnWidthPx
                        val dayXOffsets = mutableListOf<Float>()
                        var accX = sectionW
                        for (w in dayWidthsPx) {
                            dayXOffsets.add(accX)
                            accX += w
                        }

                        for (i in 1 until rowH.size) {
                            val y = (0 until i).fold(0f) { acc, j -> acc + rowH[j] }
                            drawLine(
                                brush = SolidColor(rowDivColor),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = dividerThicknessPx
                            )
                        }

                        drawLine(
                            brush = SolidColor(colDivColor),
                            start = Offset(sectionW, 0f),
                            end = Offset(sectionW, size.height),
                            strokeWidth = dividerThicknessPx
                        )

                        for (xOffset in dayXOffsets.drop(1)) {
                            drawLine(
                                brush = SolidColor(colDivColor),
                                start = Offset(xOffset, 0f),
                                end = Offset(xOffset, size.height),
                                strokeWidth = dividerThicknessPx
                            )
                        }
                    }
            ) {
            Layout(
                content = {
                    (1..maxSection).forEach { section ->
                        key("section_$section") {
                            val hasClassThisSection = section in sectionsWithCoursesRaw
                            val sectionDelay = ((section - 1).coerceAtMost(MAX_SECTION_STAGGER_INDEX)) * SECTION_STAGGER_DELAY_MS
                            val sectionCompactProgress = staggerProgress(compactLinearProgress, sectionDelay)
                            SectionCellAdaptive(
                                section = section,
                                classTime = classTimes.find { it.sectionNumber == section },
                                isCompact = compactModeEnabled && !hasClassThisSection,
                                compactProgress = if (!hasClassThisSection) sectionCompactProgress else 0f,
                                modifier = Modifier.width(sectionColumnWidth)
                            )
                        }
                    }

                    emptySlots.forEach { slot ->
                        key("empty_${slot.section}_${slot.dayOfWeek}") {
                            val isEmptySection = compactModeEnabled && slot.section !in sectionsWithCourses
                            EmptyCell(
                                dayOfWeek = slot.dayOfWeek,
                                section = slot.section,
                                isSelected = false,
                                isCompact = isEmptySection,
                                onEmptyCellClick = { day, sec ->
                                    onEmptyCellClick?.invoke(day, sec)
                                },
                                onEmptyCellLongClick = { day, sec, positionY, positionX ->
                                    selectedSlotForAction = Triple(day, sec, emptyList())
                                    slotPositionY = positionY
                                    slotPositionX = positionX
                                },
                                date = if (weekDates.isNotEmpty() && slot.dayIndex < weekDates.size) {
                                    weekDates[slot.dayIndex]
                                } else {
                                    null
                                }
                            )
                        }
                    }

                    contentSlots.forEach { slot ->
                        slot.exam?.let { exam ->
                            key("exam_${exam.id}_${slot.section}") {
                                ExamCardCell(
                                    exam = exam,
                                    onExamClick = onExamClick
                                )
                            }
                        }
                        slot.course?.let { course ->
                            key("course_${course.id}_${slot.section}") {
                                val adjustmentInfoForCourse = getAdjustmentInfo?.invoke(
                                    course.id,
                                    weekNumber,
                                    slot.dayOfWeek,
                                    course.startSection
                                )
                                CourseCardCell(
                                    weekNumber = weekNumber,
                                    course = course,
                                    classTimes = classTimes,
                                    compactModeEnabled = compactModeEnabled,
                                    showWeekend = showWeekend,
                                    onCourseClick = onCourseClick,
                                    onCourseLongClick = { c, positionY, positionX ->
                                        onCourseLongClick?.invoke(c)
                                        selectedSlotForAction = Triple(slot.dayOfWeek, course.startSection, listOf(c))
                                        slotPositionY = positionY
                                        slotPositionX = positionX
                                    },
                                    staggerIndex = (course.startSection - 1).coerceAtLeast(0),
                                    compactProgress = compactLinearProgress,
                                    semesterStartDate = semesterStartDate,
                                    adjustmentInfo = adjustmentInfoForCourse,
                                    courseColorMap = courseColorMap
                                )
                            }
                        }
                    }
                },
                measurePolicy = { measurables, constraints ->
                    val sectionCellMeasurables = measurables.take(maxSection)
                    val emptyCellMeasurables = measurables.drop(maxSection).take(emptySlots.size)
                    val contentCellMeasurables = measurables.drop(maxSection + emptySlots.size)

                    val sectionWidthPx = sectionColumnWidthPx.toInt()
                    val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

                    val sectionCellPlaceables = sectionCellMeasurables.mapIndexed { index, measurable ->
                        val rowHeightPx = rowHeightsPx.getOrElse(index) { 0f }.toInt()
                        measurable.measure(looseConstraints.copy(
                            maxWidth = sectionWidthPx,
                            maxHeight = rowHeightPx
                        ))
                    }

                    val emptyCellPlaceables = emptyCellMeasurables.mapIndexed { index, measurable ->
                        val slot = emptySlots[index]
                        val dayWidthPx = dayWidthsPx.getOrElse(slot.dayIndex) { 0f }.toInt()
                        val rowHeightPx = rowHeightsPx.getOrElse(slot.section - 1) { 0f }.toInt()
                        measurable.measure(looseConstraints.copy(
                            maxWidth = dayWidthPx,
                            maxHeight = rowHeightPx
                        ))
                    }

                    val contentCellPlaceables = contentCellMeasurables.mapIndexed { index, measurable ->
                        val slot = contentSlots[index]
                        val spanCount = slot.course?.sectionCount ?: slot.exam?.sectionCount ?: 1
                        val dayWidthPx = dayWidthsPx.getOrElse(slot.dayIndex) { 0f }.toInt()
                        val spanHeightPx = (slot.section until slot.section + spanCount).fold(0f) { acc, s ->
                            acc + rowHeightsPx.getOrElse(s - 1) { 0f }
                        }.toInt()
                        measurable.measure(looseConstraints.copy(
                            maxWidth = dayWidthPx,
                            maxHeight = spanHeightPx
                        ))
                    }

                    val measuredContentHeights = contentCellPlaceables.mapIndexed { index, placeable ->
                        val slot = contentSlots[index]
                        val spanCount = slot.course?.sectionCount ?: slot.exam?.sectionCount ?: 1
                        slot.section to (placeable.height.toFloat() / spanCount)
                    }

                    val requiredHeightPerSection = FloatArray(maxSection) { rowHeightsPx.getOrElse(it) { 0f } }

                    if (!isAdaptiveMode) {
                        for ((startSection, heightPerSection) in measuredContentHeights) {
                            val spanCount = contentSlots.find { it.section == startSection }?.course?.sectionCount
                                ?: contentSlots.find { it.section == startSection }?.exam?.sectionCount ?: 1
                            for (s in startSection until startSection + spanCount) {
                                if (s in 1..maxSection) {
                                    requiredHeightPerSection[s - 1] = maxOf(requiredHeightPerSection[s - 1], heightPerSection)
                                }
                            }
                        }

                        for ((index, placeable) in emptyCellPlaceables.withIndex()) {
                            val slot = emptySlots[index]
                            if (slot.section in 1..maxSection) {
                                requiredHeightPerSection[slot.section - 1] = maxOf(
                                    requiredHeightPerSection[slot.section - 1],
                                    placeable.height.toFloat()
                                )
                            }
                        }

                        for ((index, placeable) in sectionCellPlaceables.withIndex()) {
                            requiredHeightPerSection[index] = maxOf(
                                requiredHeightPerSection[index],
                                placeable.height.toFloat()
                            )
                        }
                    }

                    val finalRowHeightsPx = requiredHeightPerSection.toList()
                    val finalRowYOffsetsPx = finalRowHeightsPx.runningFold(0f) { acc, h -> acc + h }
                    val totalGridHeightPx = finalRowYOffsetsPx.last()

                    val totalGridWidthPx = sectionColumnWidthPx + dayWidthsPx.sum()

                    measuredRowHeights.value = finalRowHeightsPx

                    layout(totalGridWidthPx.toInt(), totalGridHeightPx.toInt()) {
                        sectionCellPlaceables.forEachIndexed { index, placeable ->
                            placeable.place(
                                x = 0,
                                y = finalRowYOffsetsPx.getOrElse(index) { 0f }.toInt()
                            )
                        }

                        emptyCellPlaceables.forEachIndexed { index, placeable ->
                            val slot = emptySlots[index]
                            val xOffset = sectionColumnWidthPx + dayWidthsPx.take(slot.dayIndex).sum()
                            placeable.place(
                                x = xOffset.toInt(),
                                y = finalRowYOffsetsPx.getOrElse(slot.section - 1) { 0f }.toInt()
                            )
                        }

                        contentCellPlaceables.forEachIndexed { index, placeable ->
                            val slot = contentSlots[index]
                            val spanCount = slot.course?.sectionCount ?: slot.exam?.sectionCount ?: 1
                            val xOffset = sectionColumnWidthPx + dayWidthsPx.take(slot.dayIndex).sum()
                            val yOffset = finalRowYOffsetsPx.getOrElse(slot.section - 1) { 0f }
                            val spanHeight = (slot.section until slot.section + spanCount).fold(0f) { acc, s ->
                                acc + finalRowHeightsPx.getOrElse(s - 1) { 0f }
                            }
                            placeable.place(
                                x = xOffset.toInt(),
                                y = yOffset.toInt()
                            )
                        }
                    }
                }
            )
            }
            }
        }
    }

    if (hasNoCourseThisWeek) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            WallpaperAwareSurface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                desktopLevel = DesktopTransparencyLevel.SEMI_TRANSPARENT,
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .padding(top = 80.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = if (weekNumber == currentWeekNumber) "本周暂无课程" else "第${weekNumber}周暂无课程",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "左右滑动切换周次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }

    val selectedSlot = selectedSlotForAction
    if (selectedSlot != null) {
        val (dayOfWeek, section, courses) = selectedSlot
        val d = LocalDensity.current

        val gapDp = 8.dp
        val gapPx = with(d) { gapDp.toPx() }
        val offsetY = (slotPositionY - gapPx - panelHeightPx).toInt()
        val offsetX = (slotPositionX - panelWidthPx / 2f).toInt()

        androidx.compose.ui.window.Popup(
            onDismissRequest = {
                selectedSlotForAction = null
            },
            alignment = Alignment.TopStart,
            offset = androidx.compose.ui.unit.IntOffset(
                x = offsetX,
                y = offsetY
            )
        ) {
            CourseSlotActionPanel(
                isVisible = true,
                courses = courses,
                dayOfWeek = dayOfWeek,
                section = section,
                weekNumber = weekNumber,
                hasClipboard = hasClipboard,
                compactModeEnabled = compactModeEnabled,
                onAddClick = {
                    onSlotActionAdd?.invoke(dayOfWeek, section, weekNumber)
                },
                onAdjustClick = { course ->
                    onSlotActionAdjust?.invoke(course)
                },
                onDeleteClick = { course ->
                    courseToDelete = Pair(course, weekNumber)
                    selectedSlotForAction = null
                },
                onCopyClick = { course ->
                    onSlotActionCopy?.invoke(course, weekNumber)
                },
                onPasteClick = { day, sec ->
                    onSlotActionPaste?.invoke(day, sec)
                },
                onDismiss = {
                    selectedSlotForAction = null
                },
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    panelHeightPx = coordinates.size.height.toFloat()
                    panelWidthPx = coordinates.size.width.toFloat()
                }
            )
        }
    }

    courseToDelete?.let { (course, week) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { courseToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("删除课程") },
            text = {
                Text("确定要删除「${course.courseName}」在第${week}周的课程吗？此操作不可撤销。")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onSlotActionDelete?.invoke(course, week)
                        courseToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { courseToDelete = null }
                ) {
                    Text("取消")
                }
            }
        )
    }

    }
}
