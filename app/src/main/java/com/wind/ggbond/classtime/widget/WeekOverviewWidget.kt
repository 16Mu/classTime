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
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.widget.data.WeekOverviewData

class WeekOverviewWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val weekData = try {
            WidgetDataProvider.getWeekOverview(context)
        } catch (e: Exception) {
            AppLogger.e("WeekOverviewWidget", "加载失败", e)
            WeekOverviewData.empty("数据加载失败")
        }
        provideContent {
            GlanceTheme {
                WeekOverviewContent(data = weekData)
            }
        }
    }
}



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
        HeaderRow(data = data)
        Spacer(modifier = GlanceModifier.height(5.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dayNightColorProvider(day = WidgetColors.dividerDay, night = WidgetColors.dividerNight))
        ) {}
        Spacer(modifier = GlanceModifier.height(5.dp))
        if (data.days.isNotEmpty()) {
            WeekBody(days = data.days, todayDow = data.todayDayOfWeek)
        } else {
            EmptyState(msg = data.emptyMessage ?: "本周无课程")
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
        val total = data.days.sumOf { it.courseCount }
        if (total > 0) {
            Text(
                text = "${total}节",
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
private fun WeekBody(days: List<DayCourseInfo>, todayDow: Int) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        days.forEachIndexed { i, d ->
            DayBlock(
                label = "周${WidgetColors.WEEKDAY_LABELS_FULL[i.coerceIn(0, 6)]}",
                courses = d.courses,
                isToday = i + 1 == todayDow
            )
            if (i < days.size - 1) {
                Spacer(modifier = GlanceModifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun DayBlock(label: String, courses: List<CourseBrief>, isToday: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(
                if (isToday) dayNightColorProvider(day = WidgetColors.todayHighlightBgDay, night = WidgetColors.todayHighlightBgNight)
                else dayNightColorProvider(day = Color.Transparent, night = Color.Transparent)
            )
            .cornerRadius(8.dp)
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = TextStyle(
                    color = if (isToday) dayNightColorProvider(day = WidgetColors.todayHighlightTextDay, night = WidgetColors.todayHighlightTextNight)
                    else dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                )
            )
            Spacer(modifier = GlanceModifier.width(5.dp))
            if (courses.isNotEmpty()) {
                Text(
                    text = courses.joinToString("·") { it.sectionLabel },
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                        fontSize = 9.sp
                    ),
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        if (courses.isNotEmpty()) {
            Text(
                text = courses.joinToString("  ") { it.name },
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
private fun EmptyState(msg: String) = WidgetEmptyState(
    message = msg,
    primaryFontSize = 20f,
    secondaryFontSize = 12f,
    spacerHeight = 3f,
    verticalPadding = 16f,
    clickable = false
)
