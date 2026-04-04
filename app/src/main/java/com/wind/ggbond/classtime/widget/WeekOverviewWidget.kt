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
import com.wind.ggbond.classtime.MainActivity
import com.wind.ggbond.classtime.widget.data.CourseBrief
import com.wind.ggbond.classtime.widget.data.DayCourseInfo
import com.wind.ggbond.classtime.widget.data.WeekOverviewData
import com.wind.ggbond.classtime.widget.WidgetDataProvider

class WeekOverviewWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val weekData = try {
            WidgetDataProvider.getWeekOverview(context)
        } catch (e: Exception) {
            android.util.Log.e("WeekOverviewWidget", "加载数据失败", e)
            WeekOverviewData.empty("数据加载失败")
        }

        provideContent {
            GlanceTheme {
                WeekOverviewContent(data = weekData)
            }
        }
    }
}

private val WEEKDAY_LABELS = arrayOf("一", "二", "三", "四", "五", "六", "日")

@Composable
private fun WeekOverviewContent(data: WeekOverviewData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(dayNightColorProvider(day = WidgetColors.cardBgDay, night = WidgetColors.cardBgNight))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        HeaderRow(data)

        Spacer(modifier = GlanceModifier.height(5.dp))

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dayNightColorProvider(day = WidgetColors.dividerDay, night = WidgetColors.dividerNight))
        ) {}

        Spacer(modifier = GlanceModifier.height(5.dp))

        if (data.days.isNotEmpty()) {
            WeekBody(days = data.days, todayDayOfWeek = data.todayDayOfWeek)
        } else {
            EmptyState(message = data.emptyMessage ?: "本周无课程")
        }
    }
}

@Composable
private fun HeaderRow(data: WeekOverviewData) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = data.weekNumberText,
            style = TextStyle(
                color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = data.dateRangeText,
            style = TextStyle(
                color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                fontSize = 10.sp
            )
        )

        Spacer(modifier = GlanceModifier.defaultWeight())

        val totalCourses = data.days.sumOf { it.courseCount }
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
private fun WeekBody(days: List<DayCourseInfo>, todayDayOfWeek: Int) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        days.forEachIndexed { index, day ->
            val isToday = (index + 1) == todayDayOfWeek

            DayBlock(
                label = "周${WEEKDAY_LABELS[index.coerceIn(0, 6)]}",
                courses = day.courses,
                isToday = isToday
            )

            if (index < days.size - 1) {
                Spacer(modifier = GlanceModifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun DayBlock(label: String, courses: List<CourseBrief>, isToday: Boolean) {
    val blockBg = if (isToday) {
        dayNightColorProvider(day = Color(0xFFE8F0FE), night = Color(0xFF1A237E))
    } else {
        dayNightColorProvider(day = Color.Transparent, night = Color.Transparent)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(blockBg)
            .cornerRadius(8.dp)
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val labelColor = if (isToday) {
                dayNightColorProvider(day = Color(0xFF1565C0), night = Color(0xFF64B5F6))
            } else {
                dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight)
            }
            Text(
                text = label,
                style = TextStyle(
                    color = labelColor,
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                )
            )

            Spacer(modifier = GlanceModifier.width(5.dp))

            if (courses.isNotEmpty()) {
                val sectionsStr = courses.joinToString("·") { it.sectionLabel }
                Text(
                    text = sectionsStr,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                        fontSize = 9.sp
                    ),
                    maxLines = 1
                )
            }
        }

        if (courses.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(2.dp))

            val namesStr = courses.joinToString("  ") { it.name }
            Text(
                text = namesStr,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 10.sp
                ),
                maxLines = 2
            )
        } else {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "-",
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                    fontSize = 10.sp
                )
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
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
                text = message,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.emptySecondaryDay, night = WidgetColors.emptySecondaryNight),
                    fontSize = 12.sp
                )
            )
        }
    }
}
