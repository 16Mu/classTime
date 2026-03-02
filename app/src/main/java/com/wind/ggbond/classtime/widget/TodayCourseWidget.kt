package com.wind.ggbond.classtime.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.wind.ggbond.classtime.MainActivity

/**
 * 今日课程桌面小组件
 * 
 * 使用 Jetpack Glance 实现，展示当天的课程列表。
 * 支持：
 * - 显示日期、星期、周次
 * - 按节次顺序列出今日课程（含教师信息）
 * - 显示课程名称、教室、上课时间、节次
 * - 高亮正在进行的课程
 * - 点击跳转到主应用
 * - 深色模式适配
 * - 自动刷新数据
 */
class TodayCourseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 从数据库获取今日课程数据
        val displayData = WidgetDataProvider.getTodayCourses(context)
        
        provideContent {
            GlanceTheme {
                WidgetContent(displayData = displayData)
            }
        }
    }
}

/**
 * Widget 整体内容布局
 */
@Composable
private fun WidgetContent(displayData: WidgetDisplayData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(dayNightColorProvider(day = WidgetColors.cardBgDay, night = WidgetColors.cardBgNight))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp)
    ) {
        // 头部：日期信息
        WidgetHeader(displayData = displayData)
        
        // 分隔线
        Spacer(modifier = GlanceModifier.height(10.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dayNightColorProvider(day = WidgetColors.dividerDay, night = WidgetColors.dividerNight))
        ) {}
        Spacer(modifier = GlanceModifier.height(10.dp))
        
        // 课程列表或空状态
        if (displayData.courseItems.isEmpty()) {
            EmptyState(message = displayData.emptyMessage ?: "今日无课程")
        } else {
            CourseList(courses = displayData.courseItems)
        }
    }
}

/**
 * Widget 头部：显示日期、星期、周次、课程数量
 */
@Composable
private fun WidgetHeader(displayData: WidgetDisplayData) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 日期和星期
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = displayData.dateText,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayData.dayOfWeekText,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                        fontSize = 13.sp
                    )
                )
                if (displayData.weekNumberText.isNotEmpty()) {
                    // 分隔点
                    Text(
                        text = " · ",
                        style = TextStyle(
                            color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                            fontSize = 13.sp
                        )
                    )
                    Text(
                        text = displayData.weekNumberText,
                        style = TextStyle(
                            color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                            fontSize = 13.sp
                        )
                    )
                }
                // 课程进度显示
                if (displayData.progressText.isNotEmpty()) {
                    Text(
                        text = " · ",
                        style = TextStyle(
                            color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                            fontSize = 13.sp
                        )
                    )
                    Text(
                        text = displayData.progressText,
                        style = TextStyle(
                            color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
        
        // 课程数量标签
        if (displayData.courseItems.isNotEmpty()) {
            Box(
                modifier = GlanceModifier
                    .background(dayNightColorProvider(day = WidgetColors.accentBgDay, night = WidgetColors.accentBgNight))
                    .cornerRadius(12.dp)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${displayData.courseItems.size}节课",
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.accentTextDay, night = WidgetColors.accentTextNight),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * 空状态展示：居中显示提示文字
 */
@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "- -",
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.emptyPrimaryDay, night = WidgetColors.emptyPrimaryNight),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = message,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.emptySecondaryDay, night = WidgetColors.emptySecondaryNight),
                    fontSize = 13.sp
                )
            )
        }
    }
}

/**
 * 课程列表：使用 LazyColumn 支持滚动
 */
@Composable
private fun CourseList(courses: List<WidgetCourseItem>) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(courses) { course ->
            CourseItemRow(course = course)
            Spacer(modifier = GlanceModifier.height(6.dp))
        }
    }
}

/**
 * 单个课程行
 * 
 * 布局结构：
 * [色条] [节次+时间] [课程名称 + 教室 + 教师]  [进行中标记]
 */
@Composable
private fun CourseItemRow(course: WidgetCourseItem) {
    // 解析课程颜色
    val courseColor = try {
        Color(android.graphics.Color.parseColor(course.color))
    } catch (e: Exception) {
        Color(0xFF5C6BC0)
    }
    
    // 正在进行的课程使用课程色调背景，其他课程使用米白卡片色
    val bgColor = if (course.isOngoing) {
        dayNightColorProvider(
            day = courseColor.copy(alpha = 0.12f),
            night = courseColor.copy(alpha = 0.18f)
        )
    } else {
        dayNightColorProvider(
            day = WidgetColors.courseItemBgDay,
            night = WidgetColors.courseItemBgNight
        )
    }
    
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(bgColor)
            .cornerRadius(12.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧色条（加宽至 5dp，提升辨识度）
        Box(
            modifier = GlanceModifier
                .width(5.dp)
                .height(40.dp)
                .background(dayNightColorProvider(day = courseColor, night = courseColor))
                .cornerRadius(3.dp)
        ) {}
        
        Spacer(modifier = GlanceModifier.width(10.dp))
        
        // 节次 + 时间信息
        Column(
            modifier = GlanceModifier.width(50.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 节次信息（始终显示）
            val endSection = course.startSection + course.sectionCount - 1
            Text(
                text = "${course.startSection}-${endSection}节",
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            // 时间信息
            if (course.startTimeText.isNotEmpty()) {
                Text(
                    text = course.startTimeText,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                        fontSize = 10.sp
                    )
                )
            }
        }
        
        Spacer(modifier = GlanceModifier.width(10.dp))
        
        // 课程信息：名称 + 教室 + 教师
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = course.courseName,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            // 教室信息
            if (course.classroom.isNotEmpty()) {
                Text(
                    text = course.classroom,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
            // 教师信息
            if (course.teacher.isNotEmpty()) {
                Text(
                    text = course.teacher,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }
        
        // 正在进行标记
        if (course.isOngoing) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .background(dayNightColorProvider(day = courseColor, night = courseColor))
                    .cornerRadius(6.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "进行中",
                    style = TextStyle(
                        color = dayNightColorProvider(day = Color.White, night = Color.White),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
