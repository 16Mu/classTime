package com.wind.ggbond.classtime.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
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
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.widget.data.WidgetDisplayData

class TodayCourseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val displayData = try {
            WidgetDataProvider.getTodayCourses(context)
        } catch (e: Exception) {
            AppLogger.e("TodayCourseWidget", "加载数据失败", e)
            WidgetDisplayData.empty("数据加载失败")
        }

        provideContent {
            GlanceTheme {
                TodayCourseContent(data = displayData)
            }
        }
    }
}

@Composable
private fun TodayCourseContent(data: WidgetDisplayData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(dayNightColorProvider(day = WidgetColors.cardBgDay, night = WidgetColors.cardBgNight))
            .cornerRadius(16.dp)
            .padding(12.dp)
    ) {
        HeaderRow(dateText = data.dateText, dayOfWeek = data.dayOfWeekText, count = data.courseItems.size)

        Spacer(modifier = GlanceModifier.height(6.dp))

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dayNightColorProvider(day = WidgetColors.dividerDay, night = WidgetColors.dividerNight))
        ) {}

        Spacer(modifier = GlanceModifier.height(6.dp))

        if (data.courseItems.isEmpty()) {
            EmptyState(message = data.emptyMessage ?: "今天没有课程")
        } else {
            CourseList(courses = data.courseItems)
        }
    }
}

@Composable
private fun HeaderRow(dateText: String, dayOfWeek: String, count: Int) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$dateText $dayOfWeek",
            style = TextStyle(
                color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = GlanceModifier.defaultWeight()
        )

        Box(
            modifier = GlanceModifier
                .background(dayNightColorProvider(day = WidgetColors.accentBgDay, night = WidgetColors.accentBgNight))
                .cornerRadius(8.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "$count 门",
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.accentTextDay, night = WidgetColors.accentTextNight),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) = WidgetEmptyState(
    message = message,
    primaryFontSize = 24f,
    secondaryFontSize = 13f,
    spacerHeight = 4f
)

@Composable
private fun CourseList(courses: List<com.wind.ggbond.classtime.widget.data.WidgetCourseItem>) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(courses) { course ->
            CourseItemRow(course = course)
            Spacer(modifier = GlanceModifier.height(6.dp))
        }
    }
}

@Composable
private fun CourseItemRow(course: com.wind.ggbond.classtime.widget.data.WidgetCourseItem) {
    val color = parseCourseColor(course.color)
    val endSec = course.startSection + course.sectionCount - 1
    val courseIdKey = ActionParameters.Key<Long>("courseId")
    val actionParams = actionParametersOf(courseIdKey to course.courseId)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(courseItemBg(isOngoing = course.isOngoing, courseColor = color))
            .cornerRadius(12.dp)
            .clickable(actionStartActivity<MainActivity>(actionParams))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(5.dp)
                .height(40.dp)
                .background(dayNightColorProvider(day = color, night = color))
                .cornerRadius(3.dp)
        ) {}

        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(
            modifier = GlanceModifier.width(50.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${course.startSection}-${endSec}节",
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

        Spacer(modifier = GlanceModifier.width(10.dp))

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

        if (course.isOngoing) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .background(dayNightColorProvider(day = color, night = color))
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
