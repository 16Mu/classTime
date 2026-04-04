package com.wind.ggbond.classtime.ui.screen.main.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.util.DateUtils
import com.wind.ggbond.classtime.ui.theme.contentColorForBackground
import com.wind.ggbond.classtime.ui.theme.secondaryContentColorForBackground

/**
 * 周视图组件
 */
@Composable
fun WeekView(
    weekNumber: Int,
    coursesMap: Map<Int, List<Course>>,
    showWeekend: Boolean = true,
    onCourseClick: (Course) -> Unit,
    onCourseLongClick: ((Course) -> Unit)? = null,
    modifier: Modifier = Modifier,
    getAdjustmentInfo: ((Long, Int, Int, Int) -> com.wind.ggbond.classtime.data.local.entity.CourseAdjustment?)? = null,
    semesterStartDate: java.time.LocalDate? = null,
    courseColorMap: Map<String, String> = emptyMap()
) {
    // 根据是否显示周末动态确定天数范围
    val dayCount = if (showWeekend) 7 else 5
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // 优化：添加key参数提升LazyColumn性能，移除入场动画减少卡顿
        items(
            count = dayCount,
            key = { index -> "day_${index + 1}_week_$weekNumber" }  // 使用更精确的key
        ) { index ->
            val dayOfWeek = index + 1
            val allCourses = coursesMap[dayOfWeek] ?: emptyList()
            
            DaySchedule(
                weekNumber = weekNumber,
                dayOfWeek = dayOfWeek,
                courses = allCourses,
                onCourseClick = onCourseClick,
                onCourseLongClick = onCourseLongClick,
                getAdjustmentInfo = getAdjustmentInfo,
                semesterStartDate = semesterStartDate,
                courseColorMap = courseColorMap
            )
        }
        
        // 底部间距
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * 单日课程显示
 */
@Composable
fun DaySchedule(
    weekNumber: Int,
    dayOfWeek: Int,
    courses: List<Course>,
    onCourseClick: (Course) -> Unit,
    onCourseLongClick: ((Course) -> Unit)? = null,
    getAdjustmentInfo: ((Long, Int, Int, Int) -> com.wind.ggbond.classtime.data.local.entity.CourseAdjustment?)? = null,
    semesterStartDate: java.time.LocalDate? = null,
    courseColorMap: Map<String, String> = emptyMap()
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 星期标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = DateUtils.getDayOfWeekShortName(dayOfWeek),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = DateUtils.getDayOfWeekName(dayOfWeek),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "${courses.size} 节课",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 课程列表
        if (courses.isEmpty()) {
            // 简化的"无课"状态显示 - 减少占用高度
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.EventAvailable,
                    contentDescription = "无课程",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "这天没有课",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            // 优化：为每个课程卡片指定key，减少不必要的重组
            courses.forEach { course ->
                androidx.compose.runtime.key(course.id) {
                    val adjustmentInfo = getAdjustmentInfo?.invoke(
                        course.id, 
                        weekNumber, 
                        course.dayOfWeek, 
                        course.startSection
                    )
                    CourseCard(
                        weekNumber = weekNumber,
                        course = course,
                        onClick = { onCourseClick(course) },
                        onLongClick = onCourseLongClick?.let { { it(course) } },
                        adjustmentInfo = adjustmentInfo,
                        semesterStartDate = semesterStartDate,
                        courseColorMap = courseColorMap
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * 课程卡片 -「小爱课程表」风格 - 性能优化版本
 * 特点：极简高效、文本完全换行、紧凑布局、高对比度
 * 优化：使用 remember 缓存颜色计算，减少重组开销
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CourseCard(
    weekNumber: Int,
    course: Course,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    adjustmentInfo: com.wind.ggbond.classtime.data.local.entity.CourseAdjustment? = null,
    semesterStartDate: java.time.LocalDate? = null,
    courseColorMap: Map<String, String> = emptyMap()
) {
    val now = java.time.LocalDateTime.now()
    val today = java.time.LocalDate.now()
    
    val currentWeek = remember(semesterStartDate, today) {
        if (semesterStartDate != null) {
            DateUtils.calculateWeekNumber(semesterStartDate, today)
        } else {
            val currentWeekOfYear = java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()
            now.get(currentWeekOfYear)
        }
    }
    
    val courseState = remember(weekNumber, currentWeek, course.dayOfWeek) {
        val isPast = weekNumber < currentWeek
        val isOngoing = weekNumber == currentWeek && now.dayOfWeek.value == course.dayOfWeek
        Pair(isPast, isOngoing)
    }
    val (isPast, isOngoing) = courseState
    
    // 优化：缓存颜色解析和计算
    val baseColor = remember(course.courseName, courseColorMap) {
        val dynamicColor = courseColorMap[course.courseName]
        if (dynamicColor != null) {
            try { Color(android.graphics.Color.parseColor(dynamicColor)) }
            catch (e: Exception) { Color(android.graphics.Color.parseColor(course.color)) }
        } else {
            Color(android.graphics.Color.parseColor(course.color))
        }
    }.let { if (it == Color.Unspecified) MaterialTheme.colorScheme.primaryContainer else it }
    
    // 根据状态调整颜色 - 与表格模式保持一致
    val displayColor = remember(baseColor, isPast, isOngoing) {
        when {
            isPast -> baseColor.copy(alpha = 0.6f)  // 过去课程适中透明度
            isOngoing -> baseColor  // 正在上课的课程保持原色
            else -> baseColor.copy(alpha = 0.9f)  // 未来课程接近原色
        }
    }
    
    // 「小爱」风格：极简扁平设计
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(displayColor)
            .then(
                if (isOngoing) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    )
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
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
                    if (adjustmentInfo != null) {
                        append("，已调课")
                    }
                }
            }
            .padding(12.dp)  // 紧凑内边距 10dp
    ) {
        // 「小爱课程表」风格：统一使用黑/白文字 - 与表格模式保持一致
        val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
        val baseTextColor = if (isDarkTheme) Color.White else Color.Black
        
        val titleColor = if (isPast) baseTextColor.copy(alpha = 0.7f) else baseTextColor  // 标题文字与表格模式一致
        val subColor = baseTextColor.copy(alpha = 0.8f)  // 次要信息80%透明度，与表格模式一致
        val tertiaryColor = baseTextColor.copy(alpha = 0.7f)  // 第三级信息70%透明度，与表格模式一致
        
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 课程名称 + 调课标记 + 节次（同一行）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 课程名称 - 主要信息，大字号18sp，粗体
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = course.courseName,
                        fontSize = 18.sp,  // 增大到18sp
                        fontWeight = FontWeight.Bold,
                        lineHeight = 24.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = titleColor
                    )
                    
                    // 调课标记 - 低调小图标
                    if (adjustmentInfo != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "⟳",
                            fontSize = 12.sp,
                            color = titleColor.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 节次信息 - 紧凑显示
                Text(
                    text = "第${course.startSection}${if (course.sectionCount > 1) "-${course.startSection + course.sectionCount - 1}" else ""}节",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor.copy(alpha = 0.85f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(titleColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 教室信息 - 次要信息，小字号12sp（比课程名小6sp）
            if (course.classroom.isNotEmpty()) {
                Text(
                    text = course.classroom,
                    fontSize = 12.sp,  // 减小到12sp
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = subColor  // 70%透明度
                )
            }
            
            // 教师信息 - 次要信息，小字号12sp（与地点同级）
            if (course.teacher.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = course.teacher,
                    fontSize = 12.sp,  // 减小到12sp
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = subColor  // 70%透明度，与地点同级
                )
            }
            
            // 正在上课的指示标签
            if (isOngoing) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "正在上课",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = titleColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
