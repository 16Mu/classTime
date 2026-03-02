package com.wind.ggbond.classtime.ui.screen.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wind.ggbond.classtime.service.ExportService

/**
 * 导出对话框组件
 * 采用Card+Dialog风格，与应用整体设计保持一致
 */
@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (ExportService.ExportFormat) -> Unit,
    onShare: (ExportService.ExportFormat) -> Unit
) {
    // 当前选中的导出格式
    var selectedFormat by remember { mutableStateOf(ExportService.ExportFormat.ICS) }
    // 触觉反馈
    val haptic = LocalHapticFeedback.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
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
                // 标题区域：图标 + 标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 导出图标
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // 标题与副标题
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "导出课程表",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "选择导出格式",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 分隔线
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 导出格式选项列表
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    getExportOptions().forEach { option ->
                        ExportFormatOption(
                            format = option.format,
                            icon = option.icon,
                            title = option.title,
                            description = option.description,
                            selected = selectedFormat == option.format,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFormat = option.format
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 底部按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 取消按钮
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        }
                    ) {
                        Text("取消")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 分享按钮
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onShare(selectedFormat)
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("分享")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 导出按钮
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExport(selectedFormat)
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("导出")
                    }
                }
            }
        }
    }
}

/**
 * 导出格式选项卡
 * 简洁的选项卡设计，选中状态通过颜色变化和勾选图标表示
 */
@Composable
private fun ExportFormatOption(
    format: ExportService.ExportFormat,
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // 背景色动画过渡
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        animationSpec = tween(200),
        label = "backgroundColor"
    )
    
    // 边框色动画过渡
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(200),
        label = "borderColor"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                role = Role.RadioButton,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 格式图标
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 标题和描述
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            // 选中指示器：使用RadioButton风格
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

/**
 * 导出选项数据类
 */
private data class ExportOption(
    val format: ExportService.ExportFormat,
    val icon: ImageVector,
    val title: String,
    val description: String
)

/**
 * 获取所有导出选项
 */
private fun getExportOptions(): List<ExportOption> {
    return listOf(
        ExportOption(
            format = ExportService.ExportFormat.ICS,
            icon = Icons.Default.CalendarMonth,
            title = "ICS 日历格式",
            description = "可导入到系统日历、Outlook、Google日历等"
        ),
        ExportOption(
            format = ExportService.ExportFormat.JSON,
            icon = Icons.Default.Code,
            title = "JSON 格式",
            description = "结构化数据，适合备份和程序处理"
        ),
        ExportOption(
            format = ExportService.ExportFormat.CSV,
            icon = Icons.Default.TableChart,
            title = "CSV 表格",
            description = "可在Excel、WPS等表格软件中打开"
        ),
        ExportOption(
            format = ExportService.ExportFormat.TXT,
            icon = Icons.Default.Description,
            title = "纯文本",
            description = "简单易读，适合打印和查看"
        ),
        ExportOption(
            format = ExportService.ExportFormat.HTML,
            icon = Icons.Default.Language,
            title = "HTML 网页",
            description = "美观的网页格式，可在浏览器中查看"
        )
    )
}

/**
 * 导出结果对话框
 * 采用Card+Dialog风格，简洁的结果展示
 */
@Composable
fun ExportResultDialog(
    result: ExportService.ExportResult,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null
) {
    // 触觉反馈
    val haptic = LocalHapticFeedback.current
    
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 结果图标
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (result.success) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (result.success) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = if (result.success) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 结果标题
                Text(
                    text = if (result.success) "导出成功" else "导出失败",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 结果描述
                if (result.success) {
                    Text(
                        text = "课程表已成功导出！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // 文件路径信息
                    result.filePath?.let { path ->
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = result.errorMessage ?: "导出过程中发生错误",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 底部按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (result.success) {
                        // 关闭按钮
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDismiss()
                            }
                        ) {
                            Text("关闭")
                        }
                        
                        // 分享按钮
                        onShare?.let { shareAction ->
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    shareAction()
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("分享")
                            }
                        }
                        
                        // 打开按钮
                        onOpen?.let { openAction ->
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    openAction()
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("打开")
                            }
                        }
                    } else {
                        // 失败时只显示确定按钮
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDismiss()
                            }
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

