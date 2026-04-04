package com.wind.ggbond.classtime.ui.screen.main.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wind.ggbond.classtime.data.local.entity.Course

/**
 * 课程格子操作面板 - 长按格子时弹出简约浮动菜单
 * 参考小爱课程表的设计风格
 * 支持：新增、调课、删除、复制、粘贴
 */
@Composable
fun CourseSlotActionPanel(
    isVisible: Boolean,
    courses: List<Course>,
    dayOfWeek: Int,
    section: Int,
    weekNumber: Int,
    hasClipboard: Boolean,
    compactModeEnabled: Boolean = false,
    onAddClick: () -> Unit,
    onAdjustClick: (Course) -> Unit,
    onDeleteClick: (Course) -> Unit,
    onCopyClick: (Course) -> Unit,
    onPasteClick: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelHorizontalPadding = if (compactModeEnabled) 8.dp else 12.dp
    val panelVerticalPadding = if (compactModeEnabled) 4.dp else 8.dp
    val panelShape = RoundedCornerShape(if (compactModeEnabled) 12.dp else 16.dp)
    val panelTonalElevation = if (compactModeEnabled) 8.dp else 12.dp
    val panelShadowElevation = if (compactModeEnabled) 8.dp else 12.dp
    val contentHorizontalPadding = if (compactModeEnabled) 2.dp else 4.dp
    val contentVerticalPadding = if (compactModeEnabled) 2.dp else 4.dp

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(initialScale = 0.8f) + fadeIn(),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(),
        modifier = modifier
    ) {
        // ✅ 简约浮动菜单 - 仅显示必要的操作
        Surface(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = panelHorizontalPadding, vertical = panelVerticalPadding)
                // MD3 规范细微边框：在纯色阴影基础上增加视觉边界，避免与下层卡片混淆
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = panelShape
                ),
            shape = panelShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = panelTonalElevation,
            shadowElevation = panelShadowElevation
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = contentHorizontalPadding, vertical = contentVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 新增
                ActionMenuItem(
                    icon = Icons.Default.Add,
                    label = "新增",
                    compactModeEnabled = compactModeEnabled,
                    onClick = {
                        onAddClick()
                        onDismiss()
                    }
                )

                // 调课（仅当有课程时显示）
                if (courses.isNotEmpty()) {
                    ActionMenuDivider()
                    ActionMenuItem(
                        icon = Icons.Default.Edit,
                        label = "调课",
                        compactModeEnabled = compactModeEnabled,
                        onClick = {
                            onAdjustClick(courses.first())
                            onDismiss()
                        }
                    )

                    // 移除本周（仅从当前周移除，非彻底删除）
                    ActionMenuDivider()
                    ActionMenuItem(
                        icon = Icons.Default.RemoveCircleOutline,
                        label = "移除",
                        isDestructive = true,
                        compactModeEnabled = compactModeEnabled,
                        onClick = {
                            onDeleteClick(courses.first())
                            onDismiss()
                        }
                    )

                    // 复制
                    ActionMenuDivider()
                    ActionMenuItem(
                        icon = Icons.Default.ContentCopy,
                        label = "复制",
                        compactModeEnabled = compactModeEnabled,
                        onClick = {
                            onCopyClick(courses.first())
                            onDismiss()
                        }
                    )
                }

                // 粘贴（仅当有剪贴板内容时显示）
                if (hasClipboard) {
                    ActionMenuDivider()
                    ActionMenuItem(
                        icon = Icons.Default.ContentPaste,
                        label = "粘贴",
                        compactModeEnabled = compactModeEnabled,
                        onClick = {
                            onPasteClick(dayOfWeek, section)
                            onDismiss()
                        }
                    )
                }

            }
        }
    }
}

/**
 * 菜单项
 */
@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    label: String,
    isDestructive: Boolean = false,
    compactModeEnabled: Boolean = false,
    onClick: () -> Unit
) {
    val iconColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val itemHorizontalPadding = if (compactModeEnabled) 8.dp else 12.dp
    val itemVerticalPadding = if (compactModeEnabled) 6.dp else 8.dp
    val iconSize = if (compactModeEnabled) 18.dp else 20.dp

    Column(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = iconColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = iconColor,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * 菜单分隔线
 */
@Composable
private fun ActionMenuDivider() {
    HorizontalDivider(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
