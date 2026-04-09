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
import com.wind.ggbond.classtime.widget.data.WidgetCourseItem
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.widget.data.WidgetDisplayData

class CompactListViewWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val displayData = try {
            WidgetDataProvider.getTodayCourses(context)
        } catch (e: Exception) {
            AppLogger.e("CompactListViewWidget", "加载数据失败", e)
            WidgetDisplayData.empty("数据加载失败")
        }

        provideContent {
            GlanceTheme {
                CompactListContent(data = displayData)
            }
        }
    }
}

@Composable
private fun CompactListContent(data: WidgetDisplayData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(dayNightColorProvider(day = WidgetColors.cardBgDay, night = WidgetColors.cardBgNight))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(10.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${data.dateText} ${data.dayOfWeekText}",
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "${data.courseItems.size}门",
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                    fontSize = 11.sp
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dayNightColorProvider(day = WidgetColors.dividerDay, night = WidgetColors.dividerNight))
        ) {}

        Spacer(modifier = GlanceModifier.height(6.dp))

        if (data.courseItems.isEmpty()) {
            EmptyState(msg = data.emptyMessage ?: "暂无课程")
        } else {
            CompactCourseList(courses = data.courseItems)
        }
    }
}

@Composable
private fun EmptyState(msg: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
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
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = msg,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.emptySecondaryDay, night = WidgetColors.emptySecondaryNight),
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
private fun CompactCourseList(courses: List<WidgetCourseItem>) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(courses) { course ->
            CompactCourseRow(course = course)
            Spacer(modifier = GlanceModifier.height(4.dp))
        }
    }
}

@Composable
private fun CompactCourseRow(course: WidgetCourseItem) {
    val color = parseCourseColor(course.color)
    val isOngoing = course.isOngoing

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(
                if (isOngoing) dayNightColorProvider(day = color.copy(alpha = 0.1f), night = color.copy(alpha = 0.15f))
                else courseItemBg()
            )
            .cornerRadius(8.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(if (isOngoing) 4.dp else 3.dp)
                .height(32.dp)
                .background(dayNightColorProvider(day = color, night = color))
                .cornerRadius(2.dp)
        ) {}

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.width(48.dp)) {
            Text(
                text = course.startTimeText.ifEmpty { "--:--" },
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 12.sp,
                    fontWeight = if (isOngoing) FontWeight.Bold else FontWeight.Normal
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

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = course.courseName,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 13.sp,
                    fontWeight = if (isOngoing) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1
            )
            if (course.classroom.isNotEmpty()) {
                Text(
                    text = course.classroom,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }

        if (isOngoing) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .background(dayNightColorProvider(day = color, night = color))
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
