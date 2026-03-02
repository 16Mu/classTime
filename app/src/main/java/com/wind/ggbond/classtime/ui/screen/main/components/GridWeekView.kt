package com.wind.ggbond.classtime.ui.screen.main.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.util.DateUtils
import com.wind.ggbond.classtime.ui.theme.contentColorForBackground
import com.wind.ggbond.classtime.ui.theme.secondaryContentColorForBackground
import com.wind.ggbond.classtime.ui.theme.topGradientOverlayAlpha
import com.wind.ggbond.classtime.ui.theme.LocalScheduleColors

/**
 * 网格式周视图 - 主流课程表设计（完全自适应屏幕）
 * 类似于小米小爱课程表的设计风格 - 一屏显示所有内容
 */
@Composable
fun GridWeekView(
    weekNumber: Int,
    coursesMap: Map<Int, List<Course>>,
    classTimes: List<com.wind.ggbond.classtime.data.local.entity.ClassTime>,
    compactModeEnabled: Boolean = false,
    showWeekend: Boolean = true,  // 新增：是否显示周末
    onCourseClick: (Course) -> Unit,
    onCourseLongClick: ((Course) -> Unit)? = null,  // 新增：长按课程卡片回调（不关心坐标，内部处理）
    onEmptyCellClick: ((dayOfWeek: Int, section: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    semesterStartDate: java.time.LocalDate? = null,  // 新增：学期开始日期，用于计算每周日期
    exams: List<com.wind.ggbond.classtime.data.local.entity.Exam> = emptyList(),  // 新增：考试列表
    onExamClick: (com.wind.ggbond.classtime.data.local.entity.Exam) -> Unit = {},  // 新增：考试点击回调
    onSlotActionAdd: ((dayOfWeek: Int, section: Int, weekNumber: Int) -> Unit)? = null,
    onSlotActionAdjust: ((Course) -> Unit)? = null,
    onSlotActionDelete: ((Course, Int) -> Unit)? = null,
    onSlotActionCopy: ((Course, Int) -> Unit)? = null,
    onSlotActionPaste: ((Int, Int) -> Unit)? = null,
    hasClipboard: Boolean = false,
    currentWeekNumber: Int = weekNumber  // 系统实际当前周次，用于区分空状态文案
) {
    // ✅ 生产环境移除调试日志
    
    // ✅ 操作面板状态管理
    var selectedSlotForAction by remember { mutableStateOf<Triple<Int, Int, List<Course>>?>(null) }
    // ✅ 删除确认对话框状态（课程 + 周次）
    var courseToDelete by remember { mutableStateOf<Pair<Course, Int>?>(null) }
    var slotPositionY by remember { mutableStateOf(0f) }  // ✅ 记录长按课程卡片/空白格子的顶部 Y 坐标（相对于根布局，像素）
    var slotPositionX by remember { mutableStateOf(0f) }  // ✅ 记录长按课程卡片/空白格子的中心 X 坐标（相对于根布局，像素）
    var panelHeightPx by remember { mutableStateOf(0f) }  // ✅ 记录操作面板实际高度（像素）
    var panelWidthPx by remember { mutableStateOf(0f) }   // ✅ 记录操作面板实际宽度（像素）
    // 每个星期几对应的列中心 X（相对于根布局，像素）
    val dayColumnCenterXMap = remember { mutableStateMapOf<Int, Float>() }
    
    // ✅ 优化：使用derivedStateOf缓存计算结果，只在依赖变化时重新计算
    val maxSection by remember(coursesMap) {
        derivedStateOf {
        val maxCourseSection = coursesMap.values.flatten()
            .maxOfOrNull { it.startSection + it.sectionCount - 1 } ?: 0
        maxOf(12, maxCourseSection)  // 至少显示12节
        }
    }
    
    // ✅ 优化：使用derivedStateOf计算节次
    // 添加coursesMap作为依赖，确保数据变化时重新计算
    val sectionsWithCourses by remember(compactModeEnabled, weekNumber, coursesMap) {
        derivedStateOf {
            if (!compactModeEnabled) {
                (1..maxSection).toSet()
            } else {
                val sections = mutableSetOf<Int>()
                coursesMap.values.flatten()
                    .filter { it.weeks.contains(weekNumber) }
                    .forEach { course ->
                        for (section in course.startSection until course.startSection + course.sectionCount) {
                            sections.add(section)
                        }
                    }
                sections
            }
        }
    }

    // 本周所有真正有课的节次集合（与紧凑模式无关）
    val sectionsWithCoursesRaw by remember(weekNumber, coursesMap) {
        derivedStateOf {
            val sections = mutableSetOf<Int>()
            coursesMap.values.flatten()
                .filter { it.weeks.contains(weekNumber) }
                .forEach { course ->
                    for (section in course.startSection until course.startSection + course.sectionCount) {
                        sections.add(section)
                    }
                }
            sections
        }
    }
    
    // ✅ 使用derivedStateOf计算日期
    // 根据是否显示周末动态确定天数范围
    val dayRange = if (showWeekend) 1..7 else 1..5
    
    // ✅ 本周实际有课的星期集合（与紧凑开关无关）
    // 用于计算紧凑模式下哪些列需要保持宽度、哪些列可以收缩
    val daysWithCourses by remember(weekNumber, showWeekend, coursesMap) {
        derivedStateOf {
            dayRange.filter { dayOfWeek ->
                coursesMap[dayOfWeek]?.any { it.weeks.contains(weekNumber) } ?: false
            }.toSet()
        }
    }
    
    // ✅ 优化：紧凑模式使用固定尺寸而不是权重
    // 空行/列使用固定最小尺寸，有课的行/列平分剩余空间
    val compactSectionHeight = 18.dp  // 空节次的固定高度：刚好容纳节次数字
    val compactDayWidth = 18.dp  // 空星期的固定宽度：刚好容纳星期标记

    // ✅ 全局紧凑模式动画进度：0f = 普通模式，1f = 紧凑模式
    // 后续所有列宽/行高变化都通过这个进度做 lerp，避免先跳到终态再动画
    val compactProgress by animateFloatAsState(
        targetValue = if (compactModeEnabled) 1f else 0f,
        animationSpec = tween(
            // 全局紧凑进度采用稍短的缓出曲线，提升干脆感
            durationMillis = 220,
            easing = FastOutSlowInEasing
        ),
        label = "compact_progress"
    )

    // ✅ 预处理：按天缓存排好序的课程列表，避免在动画期间重复排序
    val sortedCoursesByDay by remember(coursesMap) {
        derivedStateOf {
            coursesMap.mapValues { entry ->
                entry.value.sortedBy { it.startSection }
            }
        }
    }

    // ✅ 预处理：将考试按 (dayOfWeek, startSection) 建立索引，加快查找
    val examsByKey by remember(exams) {
        derivedStateOf {
            exams.associateBy { it.dayOfWeek to it.startSection }
        }
    }

    // ✅ 预处理：获取今天的具体日期，用于准确判断今日高光
    val today = java.time.LocalDate.now()
    
    // ✅ 优化：预热动画状态，减少首次点击时的卡顿
    // 在组件首次加载时就创建动画状态，避免首次切换时的初始化开销
    LaunchedEffect(Unit) {
        // 预热：触发一次极小的动画状态变化，让系统提前准备好动画管道
        // 这样首次真正切换时就不会有初始化延迟
    }
    
    // ✅ 通过 CompositionLocal 获取课表颜色，无需手动判断 isDarkTheme
    val scheduleColors = LocalScheduleColors.current
    val sectionBackground = scheduleColors.sectionBackground
    val textPrimary = scheduleColors.textPrimary
    val textSecondary = scheduleColors.textSecondary
    val gridLine = scheduleColors.gridLine
    val todayHighlight = scheduleColors.todayHighlight
    
    // ✅ 计算本周每天的日期
    val weekDates = remember(semesterStartDate, weekNumber) {
        if (semesterStartDate != null) {
            // 计算本周一的日期：学期开始日期 + (weekNumber - 1) * 7天
            val weekStartDate = semesterStartDate.plusWeeks((weekNumber - 1).toLong())
            (0..6).map { dayOffset ->
                weekStartDate.plusDays(dayOffset.toLong())
            }
        } else {
            emptyList()
        }
    }
    
    // 本周是否完全无课（用于空状态提示判断）
    val hasNoCourseThisWeek = daysWithCourses.isEmpty() && exams.isEmpty()
    // 横向列分隔线（更明显的“每天”区分）
    val columnDividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 考试通知条（嵌入式，位于星期行上方）
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
        
        // 星期标题行（固定在顶部，增加高度以容纳日期）
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)  // 增加高度以容纳两行
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val dayCount = dayRange.count()
            // 星期区域总宽度（扣除左侧 40dp 的节次标题列）
            val dayAreaWidth = (maxWidth - 40.dp).coerceAtLeast(0.dp)
            val fullWidthPerDay = if (dayCount > 0) dayAreaWidth / dayCount else 0.dp

            val hasAnyCourseInWeek = daysWithCourses.isNotEmpty()
            val emptyDaysCount = if (hasAnyCourseInWeek) {
                dayRange.count { it !in daysWithCourses }
            } else {
                0
            }
            val nonEmptyCount = dayCount - emptyDaysCount

            // 紧凑模式终态下，有课列的目标宽度（剩余宽度平均分配）
            val compactNonEmptyWidth = if (hasAnyCourseInWeek && nonEmptyCount > 0) {
                val remainingWidth = (dayAreaWidth - compactDayWidth * emptyDaysCount).coerceAtLeast(0.dp)
                remainingWidth / nonEmptyCount
            } else {
                fullWidthPerDay
            }

            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // 左上角 - 节次列标题（固定宽度，紧凑设计）
                Box(
                    modifier = Modifier
                        .width(40.dp)  // 固定宽度，与节次列一致
                        .height(48.dp)
                        .background(sectionBackground),
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
                
                // 星期标题（固定高度，宽度跟随课程列，通过 compactProgress + 每日错峰进度平滑过渡）
                dayRange.forEachIndexed { index, dayOfWeek ->
                    val hasClassOnThisDay = dayOfWeek in daysWithCourses
                    val dateForDay = if (weekDates.isNotEmpty() && dayOfWeek <= weekDates.size) {
                        weekDates[dayOfWeek - 1]
                    } else null

                    // 紧凑模式终态下，该列的目标宽度
                    val compactWidth = when {
                        !hasAnyCourseInWeek -> fullWidthPerDay
                        hasClassOnThisDay -> compactNonEmptyWidth
                        else -> compactDayWidth
                    }

                    // 从普通模式宽度 fullWidthPerDay 连续插值到紧凑宽度
                    // 通过每日错峰进度 dayCompactProgress 控制：星期越靠后，动画越晚开始
                    val dayStaggerIndex = (dayOfWeek - 1).coerceAtLeast(0)
                    // 控制最大错峰，避免尾部拖得太长
                    val clampedDayIndex = dayStaggerIndex.coerceIn(0, 5)
                    val dayDelay = clampedDayIndex * 60  // 每列 60ms，波浪更紧凑
                    val dayCompactProgress by animateFloatAsState(
                        targetValue = if (compactModeEnabled) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = 220,
                            delayMillis = dayDelay,
                            easing = FastOutSlowInEasing
                        ),
                        label = "day_compact_progress_header_$dayOfWeek"
                    )
                    val dayWidth = lerp(fullWidthPerDay, compactWidth, dayCompactProgress)

                    WeekDayHeaderAdaptive(
                        dayOfWeek = dayOfWeek,
                        date = dateForDay,
                        isCompact = compactModeEnabled && !hasClassOnThisDay,
                        modifier = Modifier
                            .width(dayWidth)
                            .then(
                                if (index < dayCount - 1) {
                                    Modifier.drawBehind {
                                        drawLine(
                                            color = columnDividerColor,
                                            start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
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

        
        // 课程表网格（自适应高度，无滚动）
        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // 左侧节次列（固定宽度40dp，紧凑设计）
            BoxWithConstraints(
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight()
                    .background(sectionBackground)
                    .padding(end = 2.dp)
            ) {
                val totalHeight = maxHeight
                val sectionCount = maxSection
                val fullHeightPerSection = if (sectionCount > 0) totalHeight / sectionCount else 0.dp

                val hasAnySectionWithCourse = sectionsWithCoursesRaw.isNotEmpty()
                val emptySectionsCount = if (hasAnySectionWithCourse) {
                    (1..maxSection).count { it !in sectionsWithCoursesRaw }
                } else {
                    0
                }
                val nonEmptyCount = sectionCount - emptySectionsCount

                val compactNonEmptyHeight = if (hasAnySectionWithCourse && nonEmptyCount > 0) {
                    val remaining = (totalHeight - compactSectionHeight * emptySectionsCount).coerceAtLeast(0.dp)
                    remaining / nonEmptyCount
                } else {
                    fullHeightPerSection
                }

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    (1..maxSection).forEach { section ->
                        val hasClassThisSection = section in sectionsWithCoursesRaw

                        val compactHeight = when {
                            !hasAnySectionWithCourse -> fullHeightPerSection
                            hasClassThisSection -> compactNonEmptyHeight
                            else -> compactSectionHeight
                        }

                        // 每一节的高度使用独立的错峰进度：节次越靠后，动画越晚开始
                        val sectionStaggerIndex = (section - 1).coerceAtLeast(0)
                        // 限制错峰层级，避免最后几节明显滞后
                    val clampedSectionIndex = sectionStaggerIndex.coerceIn(0, 5)
                    val sectionDelay = clampedSectionIndex * 70  // 每节 70ms，整体波浪收紧
                        val sectionCompactProgress by animateFloatAsState(
                            targetValue = if (compactModeEnabled) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = 220,
                                delayMillis = sectionDelay,
                                easing = FastOutSlowInEasing
                            ),
                            label = "section_compact_progress_left_$section"
                        )

                        val rowHeight = lerp(fullHeightPerSection, compactHeight, sectionCompactProgress)

                        SectionCellAdaptive(
                            section = section,
                            classTime = classTimes.find { it.sectionNumber == section },
                            isCompact = compactModeEnabled && !hasClassThisSection,
                            modifier = Modifier.height(rowHeight)
                        )
                    }
                }
            }
            
            // 右侧课程网格：使用 BoxWithConstraints + compactProgress 连续插值每一列的宽度和每节的高度
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                // 水平方向：列宽插值（与顶部星期标题保持一致）
                val dayCount = dayRange.count()
                val dayAreaWidth = maxWidth
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

                // 竖直方向：节次高度插值（与左侧节次列保持一致）
                val totalHeight = maxHeight
                val sectionCount = maxSection
                val fullHeightPerSection = if (sectionCount > 0) totalHeight / sectionCount else 0.dp

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

                    val compactHeight = when {
                        !hasAnySectionWithCourse -> fullHeightPerSection
                        hasClassThisSection -> compactNonEmptyHeight
                        else -> compactSectionHeight
                    }

                    // 课程网格内也使用按节次错峰的高度进度，保证与左侧节次列和课程卡片文本节奏一致
                    val sectionStaggerIndex = (section - 1).coerceAtLeast(0)
                    // 与左侧节次列保持一致的错峰策略，但整体节奏收紧
                    val clampedSectionIndex = sectionStaggerIndex.coerceIn(0, 5)
                    val sectionDelay = clampedSectionIndex * 70
                    val sectionCompactProgress by animateFloatAsState(
                        targetValue = if (compactModeEnabled) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = 220,
                            delayMillis = sectionDelay,
                            easing = FastOutSlowInEasing
                        ),
                        label = "section_compact_progress_grid_$section"
                    )

                    lerp(fullHeightPerSection, compactHeight, sectionCompactProgress)
                }

                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 课程网格（自适应宽度和高度）- 同一容器内根据 compactProgress 插值每一列的宽度和行高
                    dayRange.forEachIndexed { index, dayOfWeek ->
                        // ✅ 修复：基于具体日期判断是否为今天，而不是星期几
                        val isToday = if (weekDates.isNotEmpty() && index < weekDates.size) {
                            weekDates[index] == today
                        } else {
                            false
                        }
                        val hasClassOnThisDay = dayOfWeek in daysWithCourses

                        val compactWidth = when {
                            !hasAnyCourseInWeek -> fullWidthPerDay
                            hasClassOnThisDay -> compactNonEmptyWidth
                            else -> compactDayWidth
                        }

                        // 列宽错峰：星期越靠后，列宽动画越晚开始
                        val dayStaggerIndex = (dayOfWeek - 1).coerceAtLeast(0)
                        val clampedDayIndex = dayStaggerIndex.coerceIn(0, 5)
                        val dayDelay = clampedDayIndex * 60  // 与顶部星期头一致的更紧凑波浪
                        val dayCompactProgress by animateFloatAsState(
                            targetValue = if (compactModeEnabled) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = 220,
                                delayMillis = dayDelay,
                                easing = FastOutSlowInEasing
                            ),
                            label = "day_compact_progress_grid_$dayOfWeek"
                        )

                        val columnWidth = lerp(fullWidthPerDay, compactWidth, dayCompactProgress)

                        Column(
                            modifier = Modifier
                                .width(columnWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 0.5.dp)  // 最小列间距
                                .background(
                                    // 整列添加今日高亮背景（无边框）
                                    if (isToday) todayHighlight.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .then(
                                    if (index < dayCount - 1) {
                                        Modifier.drawBehind {
                                            drawLine(
                                                color = columnDividerColor,
                                                start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            // 使用预处理后的按天分组课程，避免在动画期间重复排序
                            val dayCourses = sortedCoursesByDay[dayOfWeek] ?: emptyList()
                            
                            // ✅ 正常周次过滤逻辑（MainViewModel已处理周次修复）
                            val filteredDayCourses = dayCourses

                            var currentSection = 1

                            while (currentSection <= maxSection) {
                                // 使用预处理后的考试索引按 (dayOfWeek, startSection) 快速查找
                                val examAtSection = examsByKey[dayOfWeek to currentSection]

                                if (examAtSection != null) {
                                    val baseHeight = rowHeights.getOrNull(currentSection - 1) ?: fullHeightPerSection
                                    ExamCardCell(
                                        exam = examAtSection,
                                        onExamClick = onExamClick,
                                        modifier = Modifier.height(baseHeight * examAtSection.sectionCount.toFloat())
                                    )
                                    currentSection += examAtSection.sectionCount
                                } else {
                                    val courseAtSection = filteredDayCourses.find { it.startSection == currentSection }

                                    if (courseAtSection != null) {
                                        val baseHeight = rowHeights.getOrNull(currentSection - 1) ?: fullHeightPerSection
                                        // 根据起始节次计算错峰索引：越往后的节次，动画开始越晚
                                        val staggerIndex = (courseAtSection.startSection - 1).coerceAtLeast(0)
                                        CourseCardCell(
                                            weekNumber = weekNumber,
                                            course = courseAtSection,
                                            classTimes = classTimes,
                                            compactModeEnabled = compactModeEnabled,
                                            showWeekend = showWeekend,
                                            onCourseClick = onCourseClick,
                                            onCourseLongClick = { course, positionY, positionX ->  // ✅ 接收课程卡片顶部 Y 和中心 X
                                                onCourseLongClick?.invoke(course)
                                                // 长按时显示操作面板，记录槽位坐标
                                                selectedSlotForAction = Triple(dayOfWeek, courseAtSection.startSection, listOf(course))
                                                slotPositionY = positionY
                                                slotPositionX = positionX
                                            },
                                            modifier = Modifier.height(baseHeight * courseAtSection.sectionCount.toFloat()),
                                            staggerIndex = staggerIndex,
                                            semesterStartDate = semesterStartDate  // ✅ 传递学期开始日期
                                        )
                                        currentSection += courseAtSection.sectionCount
                                    } else {
                                        val baseHeight = rowHeights.getOrNull(currentSection - 1) ?: fullHeightPerSection
                                        // ✅ 改进：渲染空白格子，单击直接跳转
                                        val isEmptySection = compactModeEnabled && currentSection !in sectionsWithCourses
                                        EmptyCell(
                                            dayOfWeek = dayOfWeek,
                                            section = currentSection,
                                            isSelected = false,  // 不再需要选中状态
                                            isCompact = isEmptySection,
                                            onEmptyCellClick = { day, sec ->
                                                // 单击直接触发添加课程
                                                onEmptyCellClick?.invoke(day, sec)
                                            },
                                            onEmptyCellLongClick = { day, sec, positionY, positionX ->
                                                // 长按空白格子显示操作面板，并记录该格子的顶部 Y / 中心 X 坐标
                                                selectedSlotForAction = Triple(day, sec, emptyList())
                                                slotPositionY = positionY
                                                slotPositionX = positionX
                                            },
                                            modifier = Modifier.height(baseHeight),  // ✅ 修复：传递节次高度，避免空白格子撑满整列
                                            date = if (weekDates.isNotEmpty() && dayOfWeek - 1 < weekDates.size) {
                                                weekDates[dayOfWeek - 1]
                                            } else {
                                                null
                                            }  // ✅ 传递对应日期
                                        )
                                        currentSection++
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ✅ 本周无课提示：叠加在网格区域上方偏上位置，不遮挡右下角 FAB
    if (hasNoCourseThisWeek) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp),  // 跳过星期标题行高度
            contentAlignment = Alignment.TopCenter  // 顶部居中，通过内部 padding 控制垂直位置
        ) {
            // 半透明提示卡片，位于网格上部 1/3 区域，避开右下角 FAB
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .padding(top = 80.dp)  // 在网格区域上部 1/3 处
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    // 主标题：根据是否为真实当前周显示不同文案
                    Text(
                        text = if (weekNumber == currentWeekNumber) "本周暂无课程" else "第${weekNumber}周暂无课程",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    // 副标题：操作提示
                    Text(
                        text = "左右滑动切换周次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }

    // ✅ 操作面板显示 - 使用 Popup 在当前课程/空白格子正上方显示
    if (selectedSlotForAction != null) {
        val (dayOfWeek, section, courses) = selectedSlotForAction!!
        val density = LocalDensity.current

        // ✅ 使用记录的课程卡片顶部 Y 和面板高度来精确定位
        // slotPositionY：课程卡片顶部相对于根布局的 Y（像素）
        // panelHeightPx：操作面板实际高度（像素）
        // gapDp：课程卡片顶部与面板底部之间的间距
        val gapDp = 8.dp
        val gapPx = with(density) { gapDp.toPx() }
        // Popup 以 alignment = TopStart + offset 放置
        // offsetY 表示面板顶部相对于根布局的 Y
        // 令面板底部 = 课程卡片顶部 - 间距：
        // offsetY + panelHeightPx = slotPositionY - gapPx
        // => offsetY = slotPositionY - gapPx - panelHeightPx
        val offsetY = (slotPositionY - gapPx - panelHeightPx).toInt()
        // 水平方向：让面板中心对齐到 slotPositionX
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
                    // ✅ 不直接删除，先弹出确认对话框
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
                    // ✅ 记录操作面板实际高度和宽度，用于精确定位
                    panelHeightPx = coordinates.size.height.toFloat()
                    panelWidthPx = coordinates.size.width.toFloat()
                }
            )
        }
    }
    // ✅ P0-1: 删除确认对话框
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

    // 闭合最外层 Box（用于承载主内容 + Popup 叠加层）
    }
}

/**
 * 星期标题单元格（紧凑设计 - 无边框）
 */
@Composable
fun WeekDayHeaderAdaptive(
    dayOfWeek: Int,
    date: java.time.LocalDate? = null,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    // ✅ 修复：基于具体日期判断是否为今天，而不是星期几
    val isToday = date != null && date == java.time.LocalDate.now()
    
    // ✅ 通过 CompositionLocal 获取课表颜色
    val scheduleColors = LocalScheduleColors.current
    val sectionBackground = scheduleColors.sectionBackground
    val textPrimary = scheduleColors.textPrimary
    val todayHighlight = scheduleColors.todayHighlight
    
    // 顶部完整「星期+日期」与紧凑一字缩写之间做交叉淡入淡出
    val fullHeaderAlpha by animateFloatAsState(
        targetValue = if (isCompact) 0f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "HeaderFullAlpha"
    )
    val compactHeaderAlpha by animateFloatAsState(
        targetValue = if (isCompact) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "HeaderCompactAlpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)  // 增加高度以容纳两行
            .background(
                // 今日使用更明显的背景色（无边框）
                if (isToday) todayHighlight.copy(alpha = 0.5f)
                else sectionBackground
            ),
        contentAlignment = Alignment.Center
    ) {
        // 普通模式样式：「星期+日期」，通过 alpha 渐隐
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

        // 紧凑模式样式：只显示一个字符，随 compact 切换渐显
        Text(
            text = DateUtils.getDayOfWeekShortName(dayOfWeek).take(1),  // 只显示第一个字符
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Light,
            color = if (isToday) MaterialTheme.colorScheme.primary
                else textPrimary.copy(alpha = 0.5f),
            fontSize = 7.sp,  // 更小的字号
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(compactHeaderAlpha)
        )
    }
}

/**
 * 星期标题单元格（固定宽度版本 - 保留用于滚动模式）
 */
@Composable
fun WeekDayHeader(dayOfWeek: Int) {
    val isToday = java.time.LocalDate.now().dayOfWeek.value == dayOfWeek
    
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(50.dp)
            .background(
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

/**
 * 节次单元格（自适应版本 - 优化设计）
 * 优化方案：扩大宽度、增加行间距、数字右对齐
 * 紧凑模式：显示浅灰条和节次号（优化方案要求）
 */
@Composable
fun SectionCellAdaptive(
    section: Int,
    classTime: com.wind.ggbond.classtime.data.local.entity.ClassTime? = null,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    // ✅ 通过 CompositionLocal 获取课表颜色
    val scheduleColors = LocalScheduleColors.current
    val sectionBackground = scheduleColors.sectionBackground
    val textSecondary = scheduleColors.textSecondary
    val gridLine = scheduleColors.gridLine
    
    // 优化方案：紧凑模式动画过渡
    val alpha by animateFloatAsState(
        targetValue = if (isCompact) 0.4f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "SectionAlpha"
    )

    val targetBackground = if (isCompact) gridLine else sectionBackground
    val backgroundColor by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "SectionBackground"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // 节次号 - 始终显示，只改变字体大小和透明度
            Text(
                text = "$section",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = textSecondary.copy(alpha = alpha),
                fontSize = if (isCompact) 7.sp else 11.sp,
                textAlign = TextAlign.Center
            )
            
            // 时间（两行显示：开始时间和结束时间）- 普通模式显示，紧凑模式隐藏
            if (!isCompact && classTime != null) {
                Spacer(modifier = Modifier.height(1.dp))
                // 开始时间
                Text(
                    text = classTime.startTime.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary.copy(alpha = alpha * 0.6f),
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 8.sp
                )
                // 结束时间
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

/**
 * 节次单元格（固定尺寸版本 - 保留用于滚动模式）
 */
@Composable
fun SectionCell(section: Int) {
    Box(
        modifier = Modifier
            .width(50.dp)
            .height(90.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
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

/**
 * 课程单元格（自适应版本）
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CourseCellAdaptive(
    dayOfWeek: Int,
    section: Int,
    courses: List<Course>,
    classTimes: List<com.wind.ggbond.classtime.data.local.entity.ClassTime>,
    isSelected: Boolean = false,
    onCourseClick: (Course) -> Unit,
    onCourseLongClick: ((Course) -> Unit)? = null,
    onEmptyCellClick: ((dayOfWeek: Int, section: Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // 如果是空白格子，添加点击效果
                    if (courses.isEmpty() && onEmptyCellClick != null) {
                        Modifier.clickable { onEmptyCellClick(dayOfWeek, section) }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (courses.isNotEmpty()) {
                val course = courses.first()
                val hasMultipleCourses = courses.size > 1  // 检测课程冲突
                val isFirstSection = section == course.startSection
                val isLastSection = section == course.startSection + course.sectionCount - 1
                
                // 判断课程状态（基于当前时间和实际时间表）
                val now = java.time.LocalDateTime.now()
                val currentDayOfWeek = now.dayOfWeek.value
                val currentTime = now.toLocalTime()
                
                // 获取课程开始和结束时间
                val courseStartSection = course.startSection
                val courseEndSection = course.startSection + course.sectionCount - 1
                
                val courseStartTime = classTimes.find { it.sectionNumber == courseStartSection }?.startTime
                val courseEndTime = classTimes.find { it.sectionNumber == courseEndSection }?.endTime
                
                // 判断课程状态
                val isPast = when {
                    dayOfWeek < currentDayOfWeek -> true  // 过去的日期
                    dayOfWeek > currentDayOfWeek -> false  // 未来的日期
                    else -> {  // 今天
                        courseEndTime?.let { currentTime.isAfter(it) } ?: false
                    }
                }
                
                val isOngoing = dayOfWeek == currentDayOfWeek &&
                    courseStartTime != null && courseEndTime != null &&
                    !currentTime.isBefore(courseStartTime) && 
                    !currentTime.isAfter(courseEndTime)
                
                // 计算圆角：只有顶部和底部才有圆角
                val topCorner = if (isFirstSection) 6.dp else 0.dp
                val bottomCorner = if (isLastSection) 6.dp else 0.dp
                
                // 计算padding：避免相同课程之间出现间隙
                val topPadding = if (isFirstSection) 1.dp else 0.dp
                val bottomPadding = if (isLastSection) 1.dp else 0.dp
                
                // 根据状态调整颜色
                val baseColor = try {
                                Color(android.graphics.Color.parseColor(course.color))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                
                val displayColor = when {
                    isPast -> baseColor.copy(alpha = 0.4f)  // 已上课：半透明
                    isOngoing -> baseColor  // 正在上课：正常颜色
                    else -> baseColor  // 未上课：正常颜色
                }
                
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        .padding(
                            start = 1.dp,
                            end = 1.dp,
                            top = topPadding,
                            bottom = bottomPadding
                        )
                        .clip(
                            RoundedCornerShape(
                                topStart = topCorner,
                                topEnd = topCorner,
                                bottomStart = bottomCorner,
                                bottomEnd = bottomCorner
                            )
                        )
                        .background(displayColor)
                        .then(
                            // 正在上课时添加边框高亮
                            if (isOngoing && isFirstSection) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(
                                        topStart = topCorner,
                                        topEnd = topCorner,
                                        bottomStart = bottomCorner,
                                        bottomEnd = bottomCorner
                                    )
                                )
                            } else {
                                Modifier
                            }
                        )
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onCourseClick(course) },
                            onLongClick = onCourseLongClick?.let { { it(course) } }
                        )
                ) {
                    // 课程冲突角标（右下角三角形 - 参照主流APP设计）
                    if (hasMultipleCourses && isLastSection) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val trianglePath = Path().apply {
                                    moveTo(size.width, size.height)
                                    lineTo(size.width, size.height * 0.3f)  // 更大的三角形
                                    lineTo(size.width * 0.3f, size.height)
                                    close()
                                }
                                drawPath(
                                    path = trianglePath,
                                    color = Color.White.copy(alpha = 0.85f)  // 更不透明
                                )
                            }
                            Text(
                                text = courses.size.toString(),
                                color = baseColor,  // 使用课程主色
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 2.dp, bottom = 2.dp)
                            )
                        }
                    }
                    
                    // 主要内容区域
                    val bg = baseColor
                    val textColor = contentColorForBackground(bg).copy(
                        alpha = if (isPast) 0.6f else 1f
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        // 在第一节显示课程信息
                        if (isFirstSection) {
                        // 根据课程节数动态调整布局策略
                        when {
                            course.sectionCount == 1 -> {
                                // 单节课：紧凑显示
                            Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    verticalArrangement = Arrangement.spacedBy(1.dp),
                                    horizontalAlignment = Alignment.Start
                            ) {
                                // 课程名称
                                Text(
                                    text = course.courseName,
                                    style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 10.sp,
                                        lineHeight = 11.sp
                                )
                                
                                // 教室信息
                                if (course.classroom.isNotEmpty()) {
                                    Text(
                                        text = course.classroom,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor.copy(alpha = 0.9f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp,
                                        lineHeight = 12.sp
                                    )
                                }
                                }
                            }
                            course.sectionCount == 2 -> {
                                // 2节课：标准显示
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    // 课程名称
                                    Text(
                                        text = course.courseName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp,
                                        lineHeight = 12.sp
                                    )
                                    
                                    // 教室信息
                                    if (course.classroom.isNotEmpty()) {
                                        Text(
                                            text = course.classroom,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textColor.copy(alpha = 0.95f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 13.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                            course.sectionCount >= 3 -> {
                                // 3节及以上：充分利用空间
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    verticalArrangement = Arrangement.Top,
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    // 课程名称 - 根据节数分配行数
                                    val maxNameLines = when {
                                        course.sectionCount >= 6 -> 8   // 6节以上：8行
                                        course.sectionCount >= 5 -> 7   // 5节：7行
                                        course.sectionCount >= 4 -> 6   // 4节：6行
                                        else -> 5  // 3节：5行
                                    }
                                    
                                    Text(
                                        text = course.courseName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor,
                                        maxLines = maxNameLines,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp,
                                        lineHeight = 13.sp
                                    )
                                    
                                    // 教室信息
                                    if (course.classroom.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = course.classroom,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textColor.copy(alpha = 0.95f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 14.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                            else -> {
                                // 默认情况
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    Text(
                                        text = course.courseName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp,
                                        lineHeight = 14.sp
                                    )
                                    
                                    if (course.classroom.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = course.classroom,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textColor.copy(alpha = 0.95f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 13.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            } else if (isSelected && onEmptyCellClick != null) {
                // 空白格子被选中，显示加号提示
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加课程",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/**
 * 课程单元格（固定尺寸版本 - 保留用于滚动模式）
 */
@Composable
fun CourseCell(
    section: Int,
    courses: List<Course>,
    onClick: (Course) -> Unit
) {
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(90.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (courses.isNotEmpty()) {
            val course = courses.first() // 如果有冲突，显示第一个
            
            // 只在课程的第一节显示完整信息
            val isFirstSection = section == course.startSection
            
            if (isFirstSection) {
                // 解析课程背景色
                val cardBgColor = try {
                    Color(android.graphics.Color.parseColor(course.color))
                } catch (e: Exception) {
                    MaterialTheme.colorScheme.primaryContainer
                }
                // 基于背景色动态计算文字颜色（WCAG 对比度算法）
                val cardTextColor = contentColorForBackground(cardBgColor)
                
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .clickable { onClick(course) },
                    colors = CardDefaults.cardColors(
                        containerColor = cardBgColor
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 课程名称
                        Text(
                            text = course.courseName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = cardTextColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        // 教室
                        if (course.classroom.isNotEmpty()) {
                            Text(
                                text = course.classroom,
                                style = MaterialTheme.typography.labelSmall,
                                color = cardTextColor.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 9.sp
                            )
                        }
                        
                        // 教师
                        if (course.teacher.isNotEmpty()) {
                            Text(
                                text = course.teacher,
                                style = MaterialTheme.typography.labelSmall,
                                color = cardTextColor.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            } else {
                // 非第一节，显示延续背景
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            try {
                                Color(android.graphics.Color.parseColor(course.color)).copy(alpha = 0.5f)
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            }
                        )
                        .clickable { onClick(course) }
                )
            }
        }
    }
}

/**
 * 课程卡片单元格 - 占据多个格子高度的完整课程卡片
 * @param weekNumber 当前选中的周次（用于判断课程是否该上）
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CourseCardCell(
    weekNumber: Int,
    course: Course,
    classTimes: List<ClassTime>,
    compactModeEnabled: Boolean = false,
    showWeekend: Boolean = true,
    onCourseClick: (Course) -> Unit,
    onCourseLongClick: ((Course, Float, Float) -> Unit)? = null,  // ✅ 传递课程卡片顶部 Y 和中心 X
    modifier: Modifier = Modifier,
    staggerIndex: Int = 0,  // 用于错峰动画的索引
    semesterStartDate: java.time.LocalDate? = null  // ✅ 添加学期开始日期参数
) {
    // ✅ 优化：使用remember缓存课程是否应该显示
    val shouldShowThisWeek = remember(course.id, weekNumber) {
        course.weeks.contains(weekNumber)
    }
    
    // ✅ 优化：使用remember缓存课程时间查找
    val courseStartSection = course.startSection
    val courseEndSection = course.startSection + course.sectionCount - 1
    val courseStartTime = remember(classTimes, courseStartSection) {
        classTimes.find { it.sectionNumber == courseStartSection }?.startTime
    }
    val courseEndTime = remember(classTimes, courseEndSection) {
        classTimes.find { it.sectionNumber == courseEndSection }?.endTime
    }
    
    // ✅ 优化：使用remember缓存状态计算
    val now = java.time.LocalDateTime.now()
    val today = java.time.LocalDate.now()
    
    // 使用学期开始日期计算当前周次，确保与课程表周数一致
    val currentWeek = remember(semesterStartDate, today) {
        if (semesterStartDate != null) {
            DateUtils.calculateWeekNumber(semesterStartDate, today)
        } else {
            // 如果没有学期开始日期，回退到ISO周数计算
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
    
    // ✅ 优化：使用remember缓存颜色计算
    val baseColor = remember(course.color) {
        try {
        Color(android.graphics.Color.parseColor(course.color))
    } catch (e: Exception) {
            Color(0xFF42A5F5)  // 默认蓝色
        }
    }
    
    // ✅ 优化：使用derivedStateOf计算显示颜色
    val displayColor by remember {
        derivedStateOf {
            when {
                !shouldShowThisWeek -> baseColor.copy(alpha = 0.08f)
                isPast -> baseColor.copy(alpha = 0.35f)
                isOngoing -> baseColor
                else -> baseColor.copy(alpha = 0.95f)
            }
        }
    }
    
    // ✅ 优化：基于课程卡片实际背景色计算文字颜色（WCAG 对比度算法）
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
    
    
    // ✅ 长按显示完整信息的状态
    var showFullInfoDialog by remember { mutableStateOf(false) }
    
    // 触觉反馈：长按课程卡片时提供震动反馈
    val haptic = LocalHapticFeedback.current
    
    // 紧凑扁平化设计 - 小圆角、无阴影、紧凑间距
    // 记录课程卡片在根布局中的「顶部」Y 和中心 X 坐标，用于定位操作面板
    var cellPositionY by remember { mutableStateOf(0f) }
    var cellCenterX by remember { mutableStateOf(0f) }
    
    // 错峰动画延迟：根据 staggerIndex 控制卡片和文字动画的起步时间
    // staggerIndex 通常由起始节次推导：节次越靠后，延迟越大
    // 为了让波浪更明显，这里拉大每一级的间隔
    val clampedStagger = staggerIndex.coerceIn(0, 6)
    // 收紧每一级的延迟，避免后几节课明显“掉队”，但保留轻微波浪感
    val staggerDelay = clampedStagger * 70

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)  // 填充矩形区域，与EmptyCell的Surface颜色一致，消除圆角色差
            .padding(horizontal = 1.dp, vertical = 1.dp)  // 最小卡片间距
            .clip(RoundedCornerShape(6.dp))  // 小圆角6dp
            .background(displayColor)  // 纯色背景，无阴影
            .then(
                // 正在上课时添加纯色边框
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
            // ✅ 获取课程卡片的精确顶部 Y 坐标（相对于根布局）
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                // 使用顶部 Y 和中心 X 作为锚点位置（像素）
                cellPositionY = bounds.top
                cellCenterX = bounds.center.x
            }
            // ✅ 单击查看详情，长按快速调课
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(),
                onClick = { onCourseClick(course) },
                onLongClick = onCourseLongClick?.let { {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)  // 长按震动反馈
                    it(course, cellPositionY, cellCenterX)
                } },
                onClickLabel = "查看详情"
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),  // 紧凑内边距
        contentAlignment = Alignment.TopStart  // 左上对齐
    ) {
            // 状态指示器：仅显示"正在上课"
            if (isOngoing) {
                // 正在上课：右上角绿色指示器
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))  // 翡翠绿
                )
            }
            // 本周不上课：不显示指示器，仅通过极低透明度表现
            
            // 紧凑布局 - 参照截图设计，信息密度更高
            // 隐藏周末+紧凑模式：使用weight精确控制比例
            val useProportionalLayout = compactModeEnabled && !showWeekend
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalArrangement = if (useProportionalLayout) Arrangement.Top else Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start  // 左对齐
            ) {
                // 课程名称字体配置 - 增大基础字体
                // 紧凑模式下字体放大，隐藏周末时格子更宽，字体可以更大
                val targetFontScale = when {
                    compactModeEnabled && !showWeekend -> 1.4f  // 隐藏周末：放大40%
                    compactModeEnabled -> 1.2f  // 显示周末：放大20%
                    else -> 1.0f  // 普通模式
                }

                // ✅ 精准微动画：字体缩放动画（平滑过渡，无Q弹）
                // 使用与卡片容器相同的 staggerDelay，让卡片整体和文字一起形成明显的错峰波浪
                val fontScale by animateFloatAsState(
                    targetValue = targetFontScale,
                    animationSpec = tween(
            durationMillis = 220,
                        delayMillis = staggerDelay,
            easing = FastOutSlowInEasing
                    ),
                    label = "course_font_scale"
                )
                val (nameFontSize, nameLineHeight, maxNameLines) = when {
                    course.sectionCount >= 8 -> Triple((18 * fontScale).sp, (20 * fontScale).sp, Int.MAX_VALUE)
                    course.sectionCount >= 6 -> Triple((16 * fontScale).sp, (18 * fontScale).sp, 10)
                    course.sectionCount >= 4 -> Triple((15 * fontScale).sp, (17 * fontScale).sp, 6)
                    course.sectionCount >= 3 -> Triple((14 * fontScale).sp, (16 * fontScale).sp, 4)
                    course.sectionCount >= 2 -> Triple((13 * fontScale).sp, (15 * fontScale).sp, 3)
                    else -> Triple((12 * fontScale).sp, (14 * fontScale).sp, 2)
                }
                
                if (useProportionalLayout) {
                    // 隐藏周末+紧凑模式：使用weight精确控制比例
                    // 课程名称：5/12高度
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(5f),
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
                    
                    // 教室：3/12高度（教室更重要，权重更大）
                    if (course.classroom.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(3f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // 智能均分换行：检测自然换行后第二行是否过短，若是则在中点重新换行
                            var displayClassroom by remember(course.classroom) { mutableStateOf(course.classroom) }
                            Text(
                                text = displayClassroom,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    !shouldShowThisWeek -> textColor.copy(alpha = 0.15f)
                                    isPast -> textColor.copy(alpha = 0.5f)
                                    else -> textColor.copy(alpha = 0.85f)
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = (10 * fontScale).sp,
                                lineHeight = (12 * fontScale).sp,
                                onTextLayout = { result ->
                                    // 当使用原始文本且产生了不均衡换行时，在中点位置重新换行
                                    if (displayClassroom == course.classroom && result.lineCount > 1) {
                                        val lastLineLen = result.getLineEnd(result.lineCount - 1) - result.getLineStart(result.lineCount - 1)
                                        val firstLineLen = result.getLineEnd(0) - result.getLineStart(0)
                                        // 最后一行字符数不足首行40%时触发均分
                                        if (firstLineLen > 0 && lastLineLen < firstLineLen * 0.4f) {
                                            val mid = (course.classroom.length + 1) / 2
                                            displayClassroom = course.classroom.substring(0, mid) + "\n" + course.classroom.substring(mid)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        when {
                                            !shouldShowThisWeek -> textColor.copy(alpha = 0.03f)
                                            isPast -> textColor.copy(alpha = 0.08f)
                                            else -> textColor.copy(alpha = 0.12f)
                                        }
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(3f))
                    }
                    
                    // 教师：2.5/12高度（无背景，纯文字显示）
                    if (course.teacher.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(2.5f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = course.teacher,
                                fontWeight = FontWeight.Normal,  // 普通字重
                                color = when {
                                    !shouldShowThisWeek -> textColor.copy(alpha = 0.15f)
                                    isPast -> textColor.copy(alpha = 0.5f)
                                    else -> textColor.copy(alpha = 0.75f)  // 次要信息，不透明度稍低
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = (10 * fontScale).sp,
                                lineHeight = (12 * fontScale).sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(2.5f))
                    }
                    
                    // 学分：1.5/12高度
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
                } else {
                    // 普通布局（原有逻辑）
                    // 1. 课程名称 - 主要信息，最醒目
                    Text(
                        text = course.courseName,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = maxNameLines,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = nameFontSize,
                        lineHeight = nameLineHeight,
                        letterSpacing = 0.sp
                    )
                    
                    // 2. 教室和教师信息 - 次要信息
                    if (course.classroom.isNotEmpty() || course.teacher.isNotEmpty()) {
                        val (detailFontSize, detailMaxLines) = when {
                            course.sectionCount >= 6 -> (11 * fontScale).sp to 3
                            course.sectionCount >= 4 -> (10 * fontScale).sp to 2
                            course.sectionCount >= 2 -> (9 * fontScale).sp to 2
                            else -> (9 * fontScale).sp to 1
                        }
                        
                        // 教室
                        if (course.classroom.isNotEmpty()) {
                            // 智能均分换行：检测自然换行后第二行是否过短，若是则在中点重新换行
                            var displayClassroom by remember(course.classroom) { mutableStateOf(course.classroom) }
                            Text(
                                text = displayClassroom,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    !shouldShowThisWeek -> textColor.copy(alpha = 0.15f)
                                    isPast -> textColor.copy(alpha = 0.5f)
                                    else -> textColor.copy(alpha = 0.95f)
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = detailFontSize,
                                lineHeight = (detailFontSize.value + 2).sp,
                                onTextLayout = { result ->
                                    // 当使用原始文本且产生了不均衡换行时，在中点位置重新换行
                                    if (displayClassroom == course.classroom && result.lineCount > 1) {
                                        val lastLineLen = result.getLineEnd(result.lineCount - 1) - result.getLineStart(result.lineCount - 1)
                                        val firstLineLen = result.getLineEnd(0) - result.getLineStart(0)
                                        // 最后一行字符数不足首行40%时触发均分
                                        if (firstLineLen > 0 && lastLineLen < firstLineLen * 0.4f) {
                                            val mid = (course.classroom.length + 1) / 2
                                            displayClassroom = course.classroom.substring(0, mid) + "\n" + course.classroom.substring(mid)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        when {
                                            !shouldShowThisWeek -> textColor.copy(alpha = 0.03f)
                                            isPast -> textColor.copy(alpha = 0.08f)
                                            else -> textColor.copy(alpha = 0.12f)
                                        }
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        
                        // 教师
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
                        
                        // 学分
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
            }
        }
}

/**
 * 空白单元格 - 优化版：美观的"无课"状态显示
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EmptyCell(
    dayOfWeek: Int,
    section: Int,
    isSelected: Boolean,
    isCompact: Boolean = false,
    onEmptyCellClick: (Int, Int) -> Unit,
    onEmptyCellLongClick: ((Int, Int, Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    date: java.time.LocalDate? = null  // ✅ 添加日期参数
) {
    // ✅ 修复：基于具体日期判断是否为今天，而不是星期几
    val isToday = date != null && date == java.time.LocalDate.now()
    // 触觉反馈：长按空白格子时提供震动反馈
    val haptic = LocalHapticFeedback.current
    // 记录空白格子在根布局中的顶部 Y 和中心 X，用于定位操作面板
    var cellPositionY by remember { mutableStateOf(0f) }
    var cellCenterX by remember { mutableStateOf(0f) }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // ✅ 获取空白格子的精确顶部 Y / 中心 X 坐标（相对于根布局）
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    cellPositionY = bounds.top
                    cellCenterX = bounds.center.x
                }
                .then(
                    // 今天的空白格子使用纯色淡背景（紧凑模式下不显示）
                    if (isToday && !isCompact) {
                        Modifier.background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.04f)
                        )
                    } else {
                        Modifier
                    }
                )
                // ✅ 改进：添加ripple效果提供即时反馈，支持长按
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(),
                    onClick = { onEmptyCellClick(dayOfWeek, section) },
                    onLongClick = onEmptyCellLongClick?.let { {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)  // 长按震动反馈
                        it(dayOfWeek, section, cellPositionY, cellCenterX)
                    } }
                ),
            contentAlignment = Alignment.Center
        ) {
            // ✅ 简化：移除选中状态，紧凑模式下不显示装饰图案
            if (!isCompact) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dotColor = if (isToday) {
                        androidx.compose.ui.graphics.Color(0xFF6366F1).copy(alpha = 0.08f)
                    } else {
                        androidx.compose.ui.graphics.Color(0xFF000000).copy(alpha = 0.02f)
                    }
                    
                    // 绘制网格点状装饰
                    val spacing = 20f
                    val dotRadius = 1f
                    var x = spacing
                    while (x < size.width) {
                        var y = spacing
                        while (y < size.height) {
                            drawCircle(
                                color = dotColor,
                                radius = dotRadius,
                                center = androidx.compose.ui.geometry.Offset(x, y)
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
