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
import androidx.glance.text.TextAlign
import com.wind.ggbond.classtime.MainActivity
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.widget.data.WeekGridData
import com.wind.ggbond.classtime.widget.data.GridCourseInfo
import com.wind.ggbond.classtime.widget.data.TimeSlot

class WeekGridViewWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val gridData = try {
            WidgetDataProvider.getWeekGridView(context)
        } catch (e: Exception) {
            AppLogger.e("WeekGridViewWidget", "加载失败", e)
            WeekGridData.empty("数据加载失败")
        }
        provideContent {
            GlanceTheme {
                WeekGridContent(data = gridData)
            }
        }
    }
}

private val WEEKDAY_LABELS = arrayOf("一", "二", "三", "四", "五")
private val TIME_SLOT_LABELS = arrayOf("第1节", "第2节", "第3节", "第4节", "第5节")

@Composable
private fun WeekGridContent(data: WeekGridData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(dayNightColorProvider(day = WidgetColors.cardBgDay, night = WidgetColors.cardBgNight))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(8.dp)
    ) {
        if (data.grid.isNotEmpty()) {
            WeekHeader(weekNumber = data.weekNumber, totalCourses = data.totalCourses)
            Spacer(modifier = GlanceModifier.height(6.dp))
            WeekGrid(
                grid = data.grid,
                timeSlots = data.timeSlots,
                todayDow = data.todayDayOfWeek
            )
        } else {
            EmptyState(msg = data.emptyMessage ?: "本周无课程")
        }
    }
}

@Composable
private fun WeekHeader(weekNumber: String, totalCourses: Int) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = weekNumber,
            style = TextStyle(
                color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = "周课表",
            style = TextStyle(
                color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                fontSize = 11.sp
            )
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (totalCourses > 0) {
            Text(
                text = "${totalCourses}节",
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.accentTextDay, night = WidgetColors.accentTextNight),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun WeekGrid(
    grid: List<List<GridCourseInfo?>>,
    timeSlots: List<TimeSlot>,
    todayDow: Int
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = GlanceModifier.width(28.dp).height(20.dp),
                contentAlignment = Alignment.Center
            ) {
            }
            WEEKDAY_LABELS.forEachIndexed { index, label ->
                val isToday = index + 1 == todayDow
                Box(
                    modifier = GlanceModifier
                        .width(52.dp)
                        .height(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            color = if (isToday) 
                                dayNightColorProvider(day = Color(0xFF0058BC), night = Color(0xFF3A86FF))
                            else 
                                dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                            fontSize = 9.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
        
        Spacer(modifier = GlanceModifier.height(2.dp))
        
        grid.forEachIndexed { rowIndex, rowCourses ->
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(40.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(28.dp)
                        .height(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = TIME_SLOT_LABELS.getOrElse(rowIndex) { "${rowIndex + 1}" },
                        style = TextStyle(
                            color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
                
                rowCourses.forEachIndexed { colIndex, courseInfo ->
                    if (courseInfo != null) {
                        CourseCard(
                            course = courseInfo,
                            isToday = colIndex + 1 == todayDow
                        )
                    } else {
                        EmptyCell(isToday = colIndex + 1 == todayDow)
                    }
                }
            }
            
            if (rowIndex < grid.size - 1) {
                Spacer(modifier = GlanceModifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun CourseCard(course: GridCourseInfo, isToday: Boolean) {
    val backgroundColor = try {
        Color(android.graphics.Color.parseColor(course.color)).copy(alpha = 0.1f)
    } catch (e: Exception) {
        Color(0xFFE8F0FE).copy(alpha = 0.1f)
    }
    
    val borderColor = try {
        Color(android.graphics.Color.parseColor(course.color))
    } catch (e: Exception) {
        Color(0xFF0058BC)
    }
    
    Box(
        modifier = GlanceModifier
            .width(52.dp)
            .height(40.dp)
            .background(
                if (isToday) 
                    dayNightColorProvider(day = backgroundColor, night = backgroundColor.copy(alpha = 0.15f))
                else 
                    dayNightColorProvider(day = backgroundColor, night = backgroundColor.copy(alpha = 0.15f))
            )
            .cornerRadius(4.dp)
            .padding(start = 4.dp, top = 2.dp, end = 2.dp, bottom = 2.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = course.name,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2
            )
            if (course.location.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(1.dp))
                Text(
                    text = course.location,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                        fontSize = 7.sp
                    ),
                    maxLines = 1
                )
            }
        }
        
        Box(
            modifier = GlanceModifier
                .width(2.dp)
                .height(40.dp)
                .background(dayNightColorProvider(day = borderColor, night = borderColor)),
            content = {}
        )
    }
}

@Composable
private fun EmptyCell(isToday: Boolean) {
    Box(
        modifier = GlanceModifier
            .width(52.dp)
            .height(40.dp)
            .background(
                if (isToday)
                    dayNightColorProvider(day = Color(0xFFE8F0FE).copy(alpha = 0.3f), night = Color(0xFF1A237E).copy(alpha = 0.3f))
                else
                    dayNightColorProvider(day = Color.Transparent, night = Color.Transparent)
            )
            .cornerRadius(4.dp),
        content = {}
    )
}

@Composable
private fun EmptyState(msg: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "- -",
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.emptyPrimaryDay, night = WidgetColors.emptyPrimaryNight),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = msg,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.emptySecondaryDay, night = WidgetColors.emptySecondaryNight),
                    fontSize = 12.sp
                )
            )
        }
    }
}
