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
import com.wind.ggbond.classtime.widget.data.WidgetDisplayData
import com.wind.ggbond.classtime.widget.data.WidgetCourseItem
import com.wind.ggbond.classtime.widget.WidgetDataProvider

/**
 * 紧凑列表视图桌面小组件
 * 
 * 使用 Jetpack Glance 实现，以极简紧凑的列表形式展示今日所有课程。
 * 
 * 适用场景：
 * - 手机副屏或小尺寸 Widget
 * - 需要快速纵览全天课程时
 * - 信息密度要求较高的场景
 * 
 * 设计特点：
 * - 去掉倒计时等动态信息
 * - 紧凑的列表形式：时间段 | 课程名称 | 教室
 * - 用竖线色块区分当前进行中的课程
 * - 极简设计，信息密度高
 */
class CompactListViewWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val displayData = try {
            WidgetDataProvider.getTodayCourses(context)
        } catch (e: Exception) {
            android.util.Log.e("CompactListViewWidget", "加载数据失败", e)
            WidgetDisplayData.empty("数据加载失败")
        }
        
        provideContent {
            GlanceTheme {
                CompactListContent(displayData = displayData)
            }
        }
    }
}

/**
 * 紧凑列表视图整体布局
 */
@Composable
private fun CompactListContent(displayData: WidgetDisplayData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(dayNightColorProvider(day = WidgetColors.cardBgDay, night = WidgetColors.cardBgNight))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        // 紧凑头部：日期 + 课程数 + 进度（单行显示）
        CompactHeader(displayData = displayData)
        
        Spacer(modifier = GlanceModifier.height(8.dp))
        
        // 分隔线
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dayNightColorProvider(day = WidgetColors.dividerDay, night = WidgetColors.dividerNight))
        ) {}
        
        Spacer(modifier = GlanceModifier.height(8.dp))
        
        // 紧凑课程列表或空状态
        if (displayData.courseItems.isEmpty()) {
            CompactEmptyState(message = displayData.emptyMessage ?: "今日无课程")
        } else {
            CompactCourseList(courses = displayData.courseItems)
        }
    }
}

/**
 * 紧凑头部：单行显示日期、星期、周次、课程数量和进度
 */
@Composable
private fun CompactHeader(displayData: WidgetDisplayData) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：日期和星期
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayData.dateText,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = displayData.dayOfWeekText,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                        fontSize = 12.sp
                    )
                )
                if (displayData.weekNumberText.isNotEmpty()) {
                    Text(
                        text = " · ${displayData.weekNumberText}",
                        style = TextStyle(
                            color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                            fontSize = 11.sp
                        )
                    )
                }
            }
            
            // 进度信息（如果有）
            if (displayData.progressText.isNotEmpty()) {
                Text(
                    text = displayData.progressText,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                        fontSize = 10.sp
                    )
                )
            }
        }
        
        // 右侧：课程数量标签
        if (displayData.courseItems.isNotEmpty()) {
            Box(
                modifier = GlanceModifier
                    .background(dayNightColorProvider(day = WidgetColors.accentBgDay, night = WidgetColors.accentBgNight))
                    .cornerRadius(10.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${displayData.courseItems.size}节",
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.accentTextDay, night = WidgetColors.accentTextNight),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * 空状态展示（紧凑版）
 */
@Composable
private fun CompactEmptyState(message: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = TextStyle(
                color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                fontSize = 13.sp
            )
        )
    }
}

/**
 * 紧凑课程列表
 * 
 * 每行显示：[色条] 时间段 | 课程名 | 教室
 * 正在进行的课程用高亮色条标识
 */
@Composable
private fun CompactCourseList(courses: List<WidgetCourseItem>) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(courses) { course ->
            CompactCourseRow(course = course)
            Spacer(modifier = GlanceModifier.height(4.dp))
        }
    }
}

/**
 * 单个紧凑课程行
 * 
 * 布局结构：
 * [竖线色条] [时间段] [课程名称 | 教室]
 * 
 * 信息密度优化：
 * - 去除教师信息（节省空间）
 * - 去除节次文字（时间段已包含）
 * - 使用更小的字号和间距
 * - 当前课程用醒目色条标识
 */
@Composable
private fun CompactCourseRow(course: WidgetCourseItem) {
    // 解析课程颜色
    val courseColor = try {
        Color(android.graphics.Color.parseColor(course.color))
    } catch (e: Exception) {
        Color(0xFF5C6BC0)
    }
    
    // 当前进行的课程使用更醒目的样式
    val isCurrentCourse = course.isOngoing
    
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(
                if (isCurrentCourse) {
                    dayNightColorProvider(
                        day = courseColor.copy(alpha = 0.1f),
                        night = courseColor.copy(alpha = 0.15f)
                    )
                } else {
                    dayNightColorProvider(
                        day = WidgetColors.courseItemBgDay,
                        night = WidgetColors.courseItemBgNight
                    )
                }
            )
            .cornerRadius(8.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧竖线色条（当前课程加宽并加亮）
        Box(
            modifier = GlanceModifier
                .width(if (isCurrentCourse) 4.dp else 3.dp)
                .height(32.dp)
                .background(dayNightColorProvider(day = courseColor, night = courseColor))
                .cornerRadius(2.dp)
        ) {}
        
        Spacer(modifier = GlanceModifier.width(8.dp))
        
        // 时间段（左侧固定宽度）
        Column(
            modifier = GlanceModifier.width(48.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = if (course.startTimeText.isNotEmpty()) course.startTimeText else "--:--",
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 12.sp,
                    fontWeight = if (isCurrentCourse) FontWeight.Bold else FontWeight.Normal
                )
            )
            if (course.endTimeText.isNotEmpty()) {
                Text(
                    text = course.endTimeText,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                        fontSize = 10.sp
                    )
                )
            }
        }
        
        Spacer(modifier = GlanceModifier.width(8.dp))
        
        // 课程信息（右侧弹性空间）
        Column(modifier = GlanceModifier.defaultWeight()) {
            // 课程名称
            Text(
                text = course.courseName,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 13.sp,
                    fontWeight = if (isCurrentCourse) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1
            )
            
            // 教室（一行显示）
            if (course.classroom.isNotEmpty()) {
                Text(
                    text = "🏫 ${course.classroom}",
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }
        
        // 进行中标记（仅当前课程显示）
        if (isCurrentCourse) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .background(dayNightColorProvider(day = courseColor, night = courseColor))
                    .cornerRadius(4.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "▶",
                    style = TextStyle(
                        color = dayNightColorProvider(day = Color.White, night = Color.White),
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}
