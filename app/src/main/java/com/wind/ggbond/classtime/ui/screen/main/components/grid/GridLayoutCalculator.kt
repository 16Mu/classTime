package com.wind.ggbond.classtime.ui.screen.main.components.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course

@Stable
class GridLayoutCalculator(
    private val coursesMap: Map<Int, List<Course>>,
    private val classTimes: List<ClassTime>,
    private val showWeekend: Boolean,
    private val compactModeEnabled: Boolean,
    private val weekNumber: Int
) {
    val dayRange: IntRange
        get() = if (showWeekend) 1..7 else 1..5

    val maxSection: Int
        get() {
            val maxCourseSection = coursesMap.values.flatten()
                .maxOfOrNull { it.startSection + it.sectionCount - 1 } ?: 0
            val configuredSections = classTimes.size.coerceAtLeast(0)
            return maxOf(configuredSections, maxCourseSection)
        }

    val sectionsWithCourses: Set<Int>
        get() {
            if (!compactModeEnabled) {
                return (1..maxSection).toSet()
            }
            val sections = mutableSetOf<Int>()
            coursesMap.values.flatten()
                .filter { it.weeks.contains(weekNumber) }
                .forEach { course ->
                    for (section in course.startSection until course.startSection + course.sectionCount) {
                        sections.add(section)
                    }
                }
            return sections
        }

    val daysWithCourses: Set<Int>
        get() {
            return dayRange.filter { dayOfWeek ->
                coursesMap[dayOfWeek]?.any { it.weeks.contains(weekNumber) } ?: false
            }.toSet()
        }

    val compactSectionHeight: Dp = 18.dp
    val compactDayWidth: Dp = 18.dp

    fun calculateDayCount(): Int = dayRange.count()

    fun calculateFullWidthPerDay(dayAreaWidth: Dp): Dp {
        val dayCount = calculateDayCount()
        return if (dayCount > 0) dayAreaWidth / dayCount else 0.dp
    }

    fun calculateCompactNonEmptyDayWidth(dayAreaWidth: Dp): Dp {
        val dayCount = calculateDayCount()
        val hasAnyCourseInWeek = daysWithCourses.isNotEmpty()
        val emptyDaysCount = if (hasAnyCourseInWeek) {
            dayRange.count { it !in daysWithCourses }
        } else {
            0
        }
        val nonEmptyCount = dayCount - emptyDaysCount

        return if (hasAnyCourseInWeek && nonEmptyCount > 0) {
            val remainingWidth = (dayAreaWidth - compactDayWidth * emptyDaysCount).coerceAtLeast(0.dp)
            remainingWidth / nonEmptyCount
        } else {
            calculateFullWidthPerDay(dayAreaWidth)
        }
    }

    fun calculateColumnWidth(
        dayAreaWidth: Dp,
        dayOfWeek: Int,
        hasClassOnThisDay: Boolean,
        compactProgress: Float,
        staggerIndex: Int
    ): Dp {
        val fullWidthPerDay = calculateFullWidthPerDay(dayAreaWidth)
        val dayDelay = (staggerIndex.coerceAtMost(MAX_DAY_STAGGER_INDEX)) * DAY_STAGGER_DELAY_MS
        val dayCompactProgress = staggerProgress(compactProgress, dayDelay)
        val compactWidth = if (hasClassOnThisDay) calculateCompactNonEmptyDayWidth(dayAreaWidth) else compactDayWidth
        return lerpDp(fullWidthPerDay, compactWidth, dayCompactProgress)
    }

    fun calculateFullHeightPerSection(totalHeight: Dp): Dp {
        val sectionCount = maxSection
        return if (sectionCount > 0) totalHeight / sectionCount else 0.dp
    }

    fun calculateCompactNonEmptySectionHeight(totalHeight: Dp): Dp {
        val sectionCount = maxSection
        val sectionsWithCoursesSet = sectionsWithCourses
        val hasAnySectionWithCourse = sectionsWithCoursesSet.isNotEmpty()
        val emptySectionCount = if (hasAnySectionWithCourse) {
            (1..maxSection).count { it !in sectionsWithCoursesSet }
        } else {
            0
        }
        val nonEmptySectionCount = sectionCount - emptySectionCount

        return if (hasAnySectionWithCourse && nonEmptySectionCount > 0) {
            val remainingHeight = (totalHeight - compactSectionHeight * emptySectionCount).coerceAtLeast(0.dp)
            remainingHeight / nonEmptySectionCount
        } else {
            calculateFullHeightPerSection(totalHeight)
        }
    }

    fun calculateRowHeight(
        totalHeight: Dp,
        section: Int,
        hasClassThisSection: Boolean,
        compactProgress: Float
    ): Dp {
        val fullHeightPerSection = calculateFullHeightPerSection(totalHeight)
        val sectionDelay = ((section - 1).coerceAtMost(MAX_SECTION_STAGGER_INDEX)) * SECTION_STAGGER_DELAY_MS
        val sectionCompactProgress = staggerProgress(compactProgress, sectionDelay)
        val compactHeight = if (hasClassThisSection) calculateCompactNonEmptySectionHeight(totalHeight) else compactSectionHeight
        return lerpDp(fullHeightPerSection, compactHeight, sectionCompactProgress)
    }

    fun calculateAllRowHeights(
        totalHeight: Dp,
        compactProgress: Float
    ): List<Dp> {
        val fullHeightPerSection = calculateFullHeightPerSection(totalHeight)
        val sectionsWithCoursesSet = sectionsWithCourses
        return (1..maxSection).map { section ->
            val hasClassThisSection = section in sectionsWithCoursesSet
            val sectionDelay = ((section - 1).coerceAtMost(MAX_SECTION_STAGGER_INDEX)) * SECTION_STAGGER_DELAY_MS
            val sectionCompactProgress = staggerProgress(compactProgress, sectionDelay)
            val compactHeight = if (hasClassThisSection) calculateCompactNonEmptySectionHeight(totalHeight) else compactSectionHeight
            lerpDp(fullHeightPerSection, compactHeight, sectionCompactProgress)
        }
    }

    private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp {
        return start + (stop - start) * fraction
    }
}

@Composable
fun rememberGridLayoutCalculator(
    coursesMap: Map<Int, List<Course>>,
    classTimes: List<ClassTime>,
    showWeekend: Boolean,
    compactModeEnabled: Boolean,
    weekNumber: Int
): GridLayoutCalculator {
    return remember(coursesMap, classTimes, showWeekend, compactModeEnabled, weekNumber) {
        GridLayoutCalculator(
            coursesMap = coursesMap,
            classTimes = classTimes,
            showWeekend = showWeekend,
            compactModeEnabled = compactModeEnabled,
            weekNumber = weekNumber
        )
    }
}
