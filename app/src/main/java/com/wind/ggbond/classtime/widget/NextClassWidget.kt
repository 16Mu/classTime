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

/**
 * 下节课倒计时桌面小组件
 * 
 * 使用 Jetpack Glance 实现，以紧凑卡片形式展示：
 * - 下节课课程名称、教室、教师
 * - 距离上课/下课的倒计时
 * - 正在上课时显示下课倒计时
 * - 无课时显示友好提示
 * - 点击跳转到主应用
 */
class NextClassWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 获取下节课数据
        val data = WidgetDataProvider.getNextClass(context)

        provideContent {
            GlanceTheme {
                NextClassContent(data = data)
            }
        }
    }
}

/**
 * 下节课 Widget 整体布局
 */
@Composable
private fun NextClassContent(data: NextClassDisplayData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(dayNightColorProvider(day = WidgetColors.cardBgDay, night = WidgetColors.cardBgNight))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp)
    ) {
        if (data.hasNextClass) {
            // 有课程：显示课程信息和倒计时
            NextClassInfo(data = data)
        } else {
            // 无课程：显示空状态
            NextClassEmpty(message = data.message ?: "暂无课程")
        }
    }
}

/**
 * 有课程时的信息展示
 */
@Composable
private fun NextClassInfo(data: NextClassDisplayData) {
    // 解析课程颜色
    val courseColor = try {
        Color(android.graphics.Color.parseColor(data.color))
    } catch (e: Exception) {
        Color(0xFF5C6BC0)
    }

    // 顶部状态行：状态标签 + 周次信息
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态标签（正在上课 / 即将上课）
        Box(
            modifier = GlanceModifier
                .background(
                    if (data.isOngoing) {
                        dayNightColorProvider(day = courseColor, night = courseColor)
                    } else {
                        dayNightColorProvider(day = WidgetColors.warningBgDay, night = WidgetColors.warningBgNight)
                    }
                )
                .cornerRadius(8.dp)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (data.isOngoing) "上课中" else "即将上课",
                style = TextStyle(
                    color = if (data.isOngoing) {
                        dayNightColorProvider(day = Color.White, night = Color.White)
                    } else {
                        dayNightColorProvider(day = WidgetColors.warningTextDay, night = WidgetColors.warningTextNight)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        // 周次信息
        if (data.weekNumberText.isNotEmpty()) {
            Text(
                text = "${data.dayOfWeekText} · ${data.weekNumberText}",
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                    fontSize = 11.sp
                )
            )
        }
    }

    Spacer(modifier = GlanceModifier.height(10.dp))

    // 倒计时数字（大字显示）
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        // 剩余时间
        val hours = data.minutesRemaining / 60
        val minutes = data.minutesRemaining % 60
        val countdownText = if (hours > 0) {
            "${hours}小时${minutes}分钟"
        } else {
            "${minutes}分钟"
        }

        Text(
            text = countdownText,
            style = TextStyle(
                color = dayNightColorProvider(day = courseColor, night = courseColor),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = GlanceModifier.width(6.dp))

        // 倒计时后缀
        Text(
            text = if (data.isOngoing) "后下课" else "后上课",
            style = TextStyle(
                color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                fontSize = 13.sp
            )
        )
    }

    Spacer(modifier = GlanceModifier.height(10.dp))

    // 分隔线
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(dayNightColorProvider(day = WidgetColors.dividerDay, night = WidgetColors.dividerNight))
    ) {}

    Spacer(modifier = GlanceModifier.height(10.dp))

    // 课程详细信息
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧色条
        Box(
            modifier = GlanceModifier
                .width(5.dp)
                .height(40.dp)
                .background(dayNightColorProvider(day = courseColor, night = courseColor))
                .cornerRadius(3.dp)
        ) {}

        Spacer(modifier = GlanceModifier.width(10.dp))

        // 课程信息
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = data.courseName,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.textPrimaryDay, night = WidgetColors.textPrimaryNight),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            // 教室 + 节次
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (data.classroom.isNotEmpty()) {
                    Text(
                        text = data.classroom,
                        style = TextStyle(
                            color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
                if (data.classroom.isNotEmpty() && data.sectionText.isNotEmpty()) {
                    Text(
                        text = " · ",
                        style = TextStyle(
                            color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                            fontSize = 12.sp
                        )
                    )
                }
                if (data.sectionText.isNotEmpty()) {
                    Text(
                        text = data.sectionText,
                        style = TextStyle(
                            color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                            fontSize = 12.sp
                        )
                    )
                }
            }
            // 教师
            if (data.teacher.isNotEmpty()) {
                Text(
                    text = data.teacher,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textTertiaryDay, night = WidgetColors.textTertiaryNight),
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
        }

        // 时间信息
        if (data.timeText.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = data.timeText,
                    style = TextStyle(
                        color = dayNightColorProvider(day = WidgetColors.textSecondaryDay, night = WidgetColors.textSecondaryNight),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

/**
 * 无课程空状态展示
 */
@Composable
private fun NextClassEmpty(message: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "- -",
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.emptyPrimaryDay, night = WidgetColors.emptyPrimaryNight),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = message,
                style = TextStyle(
                    color = dayNightColorProvider(day = WidgetColors.emptySecondaryDay, night = WidgetColors.emptySecondaryNight),
                    fontSize = 14.sp
                )
            )
        }
    }
}
