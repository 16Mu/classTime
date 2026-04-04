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
import com.wind.ggbond.classtime.widget.data.TomorrowCourseDisplayData
import com.wind.ggbond.classtime.widget.data.WidgetCourseItem

class TomorrowCourseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            WidgetDataProvider.getSmartCourses(context)
        } catch (e: Exception) {
            android.util.Log.e("TomorrowCourseWidget", "加载数据失败", e)
            TomorrowCourseDisplayData.empty("数据加载失败")
        }

        provideContent {
            GlanceTheme {
                SmartCourseContent(data = data)
            }
        }
    }
}

@Composable
private fun SmartCourseContent(data: TomorrowCourseDisplayData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(dayNightColorProvider(day = WidgetColors.cardBgDay, night = WidgetColors.cardBgNight))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp)
    ) {
        SmartHeader(data = data)

        Spacer(modifier = GlanceModifier.height(8.dp))

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dayNightColorProvider(day = WidgetColors.dividerDay, night = WidgetColors.dividerNight))
        ) {}

        Spacer(modifier = GlanceModifier.height(10.dp))

        if (data.courseItems.isEmpty()) {
            EmptyState(message = data.emptyMessage ?: "暂无课程")
        } else {
            CourseList(courses = data.courseItems)
        }
    }
}

@Composable
private fun SmartHeader(data: TomorrowCourseDisplayData) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = data.dateText,
                        style = TextStyle(
                            color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = data.dayOfWeekText,
                        style = TextStyle(
                            color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                            fontSize = 12.sp
                        )
                    )
                    if (data.weekNumberText.isNotEmpty()) {
                        Text(
                            text = " · ${data.weekNumberText}",
                            style = TextStyle(
                                color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                if (data.statusLabel.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(3.dp))
                    val statusColor = if (data.isShowingToday) {
                        dayNightColorProvider(day = Color(0xFF4CAF50), night = Color(0xFF81C784))
                    } else {
                        dayNightColorProvider(day = WidgetColors.accentBgDay, night = WidgetColors.accentTextDay)
                    }
                    Text(
                        text = data.statusLabel,
                        style = TextStyle(
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                } else if (data.progressText.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(3.dp))
                    Text(
                        text = data.progressText,
                        style = TextStyle(
                            color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            if (data.courseItems.isNotEmpty()) {
                val badgeText = if (data.isShowingToday) "今日${data.courseItems.size}节" else "明日${data.courseItems.size}节"
                Box(
                    modifier = GlanceModifier
                        .background(if (!data.isShowingToday) dayNightColorProvider(day = Color(0xFFE3F2FD), night = Color(0xFF1A237E)) else dayNightColorProvider(day = WidgetColors.accentBgDay, night = WidgetColors.accentBgNight))
                        .cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeText,
                        style = TextStyle(
                            color = if (!data.isShowingToday) dayNightColorProvider(day = Color(0xFF1976D2), night = Color(0xFF64B5F6)) else dayNightColorProvider(day = WidgetColors.accentTextDay, night = WidgetColors.accentTextNight),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

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
                    fontSize = 22.sp,
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

@Composable
private fun CourseList(courses: List<WidgetCourseItem>) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(courses) { course ->
            SmartCourseItemRow(course = course)
            Spacer(modifier = GlanceModifier.height(6.dp))
        }
    }
}

@Composable
private fun SmartCourseItemRow(course: WidgetCourseItem) {
    val courseColor = try {
        Color(android.graphics.Color.parseColor(course.color))
    } catch (e: Exception) {
        Color(0xFF5C6BC0)
    }

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
        Box(
            modifier = GlanceModifier
                .width(5.dp)
                .height(36.dp)
                .background(dayNightColorProvider(day = courseColor, night = courseColor))
                .cornerRadius(3.dp)
        ) {}

        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(
            modifier = GlanceModifier.width(46.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val endSection = course.startSection + course.sectionCount - 1
            Text(
                text = "${course.startSection}-${endSection}",
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
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

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = course.courseName,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
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
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
