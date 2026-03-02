package com.wind.ggbond.classtime.ui.screen.main.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 周次快速选择对话框
 * 圆形指示器网格布局，支持一键跳转到指定周
 *
 * @param currentWeek 当前选中/查看的周次
 * @param totalWeeks 学期总周数
 * @param actualCurrentWeek 系统实际当前周次（用于高亮标记）
 * @param onWeekSelected 选中某一周的回调
 * @param onDismiss 关闭对话框的回调
 */
@Composable
fun WeekPickerDialog(
    currentWeek: Int,
    totalWeeks: Int,
    actualCurrentWeek: Int,
    onWeekSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // 标题区域：图标 + 标题 + 当前周信息
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 日历图标
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // 标题与副标题
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "选择周次",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "当前是第${actualCurrentWeek}周",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // "回到本周"快捷按钮（仅当用户不在当前周时显示）
                    if (currentWeek != actualCurrentWeek) {
                        FilledTonalButton(
                            onClick = { onWeekSelected(actualCurrentWeek) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Today,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "本周",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 分隔线
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 周次网格：每行5个圆形指示器
                val columns = 5
                val rows = (totalWeeks + columns - 1) / columns

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(rows) { row ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            repeat(columns) { col ->
                                val week = row * columns + col + 1
                                if (week <= totalWeeks) {
                                    // 渲染圆形周次指示器
                                    WeekCircleItem(
                                        week = week,
                                        isSelected = week == currentWeek,
                                        isActualCurrent = week == actualCurrentWeek,
                                        onClick = { onWeekSelected(week) }
                                    )
                                } else {
                                    // 占位：保持网格对齐
                                    Spacer(modifier = Modifier.size(44.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 底部图例说明 + 取消按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 图例
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // 当前周图例：浅色背景 + 描边 + 底部小圆点
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            )
                            // 底部小圆点
                            Spacer(modifier = Modifier.height(1.dp))
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "本周",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // 查看中图例：实心填充
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "查看中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 取消按钮
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

/**
 * 单个圆形周次指示器
 * - 当前查看周：primary 填充色
 * - 当前真实周：primaryContainer 浅色背景 + 描边 + 底部小圆点
 * - 同时满足两者：primary 填充 + 底部小圆点
 * - 普通周：透明背景
 *
 * @param week 周次编号
 * @param isSelected 是否为当前查看的周次
 * @param isActualCurrent 是否为系统当前周次
 * @param onClick 点击回调
 */
@Composable
private fun WeekCircleItem(
    week: Int,
    isSelected: Boolean,
    isActualCurrent: Boolean,
    onClick: () -> Unit
) {
    // 背景色动画：当前真实周使用 primaryContainer 浅色底，选中周使用 primary 填充
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isActualCurrent -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 200),
        label = "weekBgColor"
    )

    // 文字颜色动画
    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isActualCurrent -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 200),
        label = "weekTextColor"
    )

    // 外层 Column：圆形 + 底部小圆点（当前真实周标识）
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color = backgroundColor, shape = CircleShape)
                .then(
                    // 当前真实周添加描边圆环（未选中时）
                    if (isActualCurrent && !isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(bounded = true, radius = 22.dp),
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$week",
                color = textColor,
                fontWeight = if (isSelected || isActualCurrent) FontWeight.Bold else FontWeight.Normal,
                fontSize = 15.sp,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 底部小圆点：标识当前真实周
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(
                    color = if (isActualCurrent) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    shape = CircleShape
                )
        )
    }
}
