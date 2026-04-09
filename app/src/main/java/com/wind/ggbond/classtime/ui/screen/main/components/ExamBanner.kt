package com.wind.ggbond.classtime.ui.screen.main.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wind.ggbond.classtime.data.local.entity.Exam
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.DateUtils

/**
 * 嵌入式考试通知条 - 简洁优雅的跑马灯设计
 * 
 * 特点：
 * - 非悬浮层，自然嵌入布局
 * - 紧凑设计，不占用过多空间
 * - 主题色适配
 * - 流畅的跑马灯效果
 */
@Composable
fun ExamBanner(
    exams: List<Exam>,
    currentWeek: Int,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onExamClick: (Exam) -> Unit = {}
) {
    // 筛选相关考试（当前周 + 下一周）
    val relevantExams = remember(exams, currentWeek) {
        exams.filter { exam ->
            exam.weekNumber == currentWeek || exam.weekNumber == currentWeek + 1
        }
    }

    // 是否被用户手动关闭
    var isDismissed by remember { mutableStateOf(false) }

    // 只在有考试且未被关闭时渲染
    if (relevantExams.isNotEmpty() && !isDismissed) {
        ExamBannerContent(
            exams = relevantExams,
            currentWeek = currentWeek,
            onDismiss = {
                isDismissed = true
                onDismiss()
            },
            onExamClick = onExamClick,
            modifier = modifier
        )
    }
}

@Composable
private fun ExamBannerContent(
    exams: List<Exam>,
    currentWeek: Int,
    onDismiss: () -> Unit,
    onExamClick: (Exam) -> Unit,
    modifier: Modifier = Modifier
) {
    // 拼接考试信息文本
    val examText = remember(exams, currentWeek) {
        try {
            if (exams.isEmpty()) {
                "暂无考试"
            } else {
                exams.joinToString(" • ") { exam ->
                    try {
                        val weekPrefix = when (exam.weekNumber) {
                            currentWeek -> "本周"
                            currentWeek + 1 -> "下周"
                            else -> "第${exam.weekNumber}周"
                        }
                        val dayInfo = exam.dayOfWeek?.let { dayOfWeek ->
                            if (dayOfWeek in 1..7) {
                                DateUtils.getDayOfWeekName(dayOfWeek)
                            } else ""
                        } ?: ""
                        val courseName = exam.courseName.takeIf { it.isNotBlank() } ?: "未命名"
                        val examType = exam.examType.takeIf { it.isNotBlank() } ?: "考试"
                        "$weekPrefix $courseName$examType${if (dayInfo.isNotEmpty()) " $dayInfo" else ""}"
                    } catch (e: Exception) {
                        "考试信息"
                    }
                } + " • "  // 结尾添加分隔符，让循环更自然
            }
        } catch (e: Exception) {
            AppLogger.e("ExamBanner", "Error creating exam text", e)
            "考试信息加载中..."
        }
    }

    // 跑马灯动画
    val infiniteTransition = rememberInfiniteTransition(label = "marquee")
    var textWidth by remember { mutableStateOf(0f) }

    val marqueeOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (textWidth > 0f) -textWidth else -1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (textWidth > 0f) (textWidth / 30f * 1000).toInt() else 10000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    // 简洁的通知条
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics {
                contentDescription = if (exams.size == 1) {
                    val exam = exams.first()
                    buildString {
                        append("考试提醒：${exam.courseName}${exam.examType}")
                        exam.dayOfWeek?.let { append("，${DateUtils.getDayOfWeekName(it)}") }
                        append("，${exam.getTimeDescription()}")
                        if (exam.location.isNotEmpty()) {
                            append("，在${exam.location}")
                        }
                    }
                } else {
                    "考试提醒：共${exams.size}场考试"
                }
            },
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 跑马灯文本
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(marqueeOffset.toInt(), 0) }
                    .padding(vertical = 2.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 显示两遍文本，实现无缝循环
                repeat(2) {
                    Text(
                        text = examText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            if (textWidth == 0f) {
                                textWidth = coordinates.size.width.toFloat()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(32.dp))
                }
            }

            // 右侧关闭按钮（增大触摸区域至 48dp，满足 WCAG 最小触摸目标要求）
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .size(36.dp)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}


