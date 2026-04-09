package com.wind.ggbond.classtime.ui.screen.main.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.screen.main.MainViewModel
import kotlinx.coroutines.launch

/**
 * 周视图容器组件
 * 
 * 职责：
 * - 管理横向滑动周次切换（HorizontalPager）
 * - 处理视图模式切换动画（网格/列表）
 * - 显示"回到本周"悬浮按钮
 * - 协调课程点击、长按、空单元格点击等事件
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekViewContainer(
    pagerState: androidx.compose.foundation.pager.PagerState,
    currentSchedule: Schedule?,
    currentWeekNumber: Int,
    actualWeekNumber: Int,
    viewMode: Int,
    classTimes: List<ClassTime>,
    compactModeEnabled: Boolean,
    showWeekendEnabled: Boolean,
    glassEffectEnabled: Boolean,
    allCourses: List<Course>,
    adjustments: List<CourseAdjustment>,
    clipboard: Pair<Course, Int>?,
    viewModel: MainViewModel,
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    isDataReady: Boolean,
    onCourseSelected: (Long) -> Unit,
    onAdjustmentRequest: (Long) -> Unit,
    isWallpaperEnabled: Boolean = false,
    courseColors: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val totalWeeks = currentSchedule?.totalWeeks ?: 20

    // [Monet] 将课程颜色列表转换为Map - 使用课程名称作为key
    val courseColorMap = remember(courseColors, allCourses) {
        if (courseColors.isEmpty()) {
            emptyMap()
        } else {
            val uniqueCourseNames = allCourses.map { it.courseName }.distinct()
            uniqueCourseNames.mapIndexed { index, courseName ->
                val color = courseColors.getOrNull(index % courseColors.size) ?: courseColors.first()
                courseName to color
            }.toMap()
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { it }
        ) { page ->
            val weekNumber = page + 1
            val examsForWeek by viewModel.examsForCurrentWeek.collectAsState()
            
            val coursesForWeek = remember(allCourses.size, adjustments.size, weekNumber) {
                viewModel.getCoursesForWeek(weekNumber)
            }
            
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = {
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(
                        animationSpec = tween(300)
                    ) togetherWith scaleOut(
                        targetScale = 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeOut(
                        animationSpec = tween(300)
                    )
                },
                label = "ViewModeTransition"
            ) { mode ->
                when (mode) {
                    0 -> {
                        GridWeekView(
                            weekNumber = weekNumber,
                            coursesMap = coursesForWeek,
                            classTimes = classTimes,
                            compactModeEnabled = compactModeEnabled,
                            showWeekend = showWeekendEnabled,
                            onCourseClick = { course ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCourseSelected(course.id)
                            },
                            onCourseLongClick = { course ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onEmptyCellClick = { dayOfWeek, section ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navController.navigate(
                                    Screen.CourseEdit.createRoute(
                                        courseId = null,
                                        dayOfWeek = dayOfWeek,
                                        startSection = section,
                                        sectionCount = 1,
                                        weekNumber = weekNumber
                                    )
                                )
                            },
                            semesterStartDate = currentSchedule?.startDate,
                            exams = if (weekNumber == currentWeekNumber) examsForWeek else emptyList(),
                            onExamClick = { exam ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCourseSelected(exam.courseId)
                            },
                            onSlotActionAdd = { dayOfWeek, section, weekNum ->
                                navController.navigate(
                                    Screen.CourseEdit.createRoute(
                                        courseId = null,
                                        dayOfWeek = dayOfWeek,
                                        startSection = section,
                                        sectionCount = 1,
                                        weekNumber = weekNum
                                    )
                                )
                            },
                            onSlotActionAdjust = { course ->
                                onAdjustmentRequest(course.id)
                            },
                            onSlotActionDelete = { course, weekNum ->
                                viewModel.deleteCourseForWeek(course, weekNum)
                                coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "已删除「${course.courseName}」",
                                        actionLabel = "撤销",
                                        duration = SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoLastDelete()
                                    }
                                }
                            },
                            onSlotActionCopy = { course, weekNum ->
                                viewModel.copyCourseToClipboard(course, weekNum)
                            },
                            onSlotActionPaste = { dayOfWeek, section ->
                                viewModel.pasteCourseFromClipboard(weekNumber, dayOfWeek, section)
                            },
                            hasClipboard = clipboard != null,
                            currentWeekNumber = currentWeekNumber,
                            getAdjustmentInfo = { courseId, week, day, section ->
                                viewModel.isAdjustedCourse(courseId, week, day, section)
                            },
                            courseColorMap = courseColorMap,
                            isWallpaperEnabled = isWallpaperEnabled
                        )
                    }
                    1 -> {
                        WeekView(
                            weekNumber = weekNumber,
                            coursesMap = coursesForWeek,
                            showWeekend = showWeekendEnabled,
                            onCourseClick = { course ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCourseSelected(course.id)
                            },
                            onCourseLongClick = { course ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAdjustmentRequest(course.id)
                            },
                            getAdjustmentInfo = { courseId, week, day, section ->
                                viewModel.isAdjustedCourse(courseId, week, day, section)
                            },
                            semesterStartDate = currentSchedule?.startDate,
                            courseColorMap = courseColorMap,
                            isWallpaperEnabled = isWallpaperEnabled
                        )
                    }
                }
            }
        }
        
        val isNotCurrentWeek = isDataReady && (pagerState.currentPage + 1) != actualWeekNumber
        AnimatedVisibility(
            visible = isNotCurrentWeek,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(actualWeekNumber - 1)
                    }
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .background(
                        color = if (glassEffectEnabled) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .then(
                        if (glassEffectEnabled) {
                            Modifier.border(
                                width = 0.8.dp,
                                color = Color.White.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(24.dp)
                            )
                        } else {
                            Modifier
                        }
                    ),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Today, contentDescription = "回到本周", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("回到第${actualWeekNumber}周", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
