package com.wind.ggbond.classtime.ui.screen.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wind.ggbond.classtime.service.ExportService
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * 导出对话框组件
 */
@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (ExportService.ExportFormat) -> Unit,
    onShare: (ExportService.ExportFormat) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportService.ExportFormat.ICS) }
    var showShareOptions by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "导出",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("导出课程表")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "请选择导出格式:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(getExportOptions()) { index, option ->
                        // 列表项依次渐入动画
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(index * 80L)
                            visible = true
                        }
                        
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(400)) + 
                                   slideInHorizontally(
                                       initialOffsetX = { it / 2 },
                                       animationSpec = tween(400, easing = FastOutSlowInEasing)
                                   )
                        ) {
                            ExportFormatOption(
                                format = option.format,
                                icon = option.icon,
                                title = option.title,
                                description = option.description,
                                selected = selectedFormat == option.format,
                                onClick = { selectedFormat = option.format }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showShareOptions) {
                    FilledTonalButton(
                        onClick = {
                            onShare(selectedFormat)
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("分享")
                    }
                }
                
                Button(
                    onClick = {
                        onExport(selectedFormat)
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导出")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 导出格式选项卡
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
    // 选中状态的动画过渡
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (selected) 4.dp else 0.dp,
        animationSpec = tween(300),
        label = "elevation"
    )
    
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            )
        },
        shadowElevation = elevation,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标缩放动画
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.15f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "icon_scale"
            )
            
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .size(32.dp)
                    .scale(iconScale)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
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
            
            // 勾选图标带弹性动画
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
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
 */
@Composable
fun ExportResultDialog(
    result: ExportService.ExportResult,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null
) {
    // 成功图标旋转+缩放动画
    val iconScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "icon_scale"
    )
    
    val iconRotation by animateFloatAsState(
        targetValue = if (result.success) 360f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_rotation"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
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
                modifier = Modifier
                    .size(48.dp)
                    .scale(iconScale)
                    .rotate(iconRotation)
            )
        },
        title = {
            Text(if (result.success) "导出成功" else "导出失败")
        },
        text = {
            Column {
                if (result.success) {
                    // 成功消息渐入动画
                    var textVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(200)
                        textVisible = true
                    }
                    
                    AnimatedVisibility(
                        visible = textVisible,
                        enter = fadeIn(tween(400)) + slideInVertically(
                            initialOffsetY = { -it / 2 },
                            animationSpec = tween(400)
                        )
                    ) {
                        Column {
                            Text("课程表已成功导出！")
                            result.filePath?.let { path ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "保存位置: $path",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // 错误消息震动效果
                    var errorVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(100)
                        errorVisible = true
                    }
                    
                    AnimatedVisibility(
                        visible = errorVisible,
                        enter = fadeIn(tween(300)) + slideInHorizontally(
                            initialOffsetX = { -it / 4 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    ) {
                        Text(
                            result.errorMessage ?: "导出过程中发生错误",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (result.success) {
                // 按钮依次弹出
                var buttonsVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(400)
                    buttonsVisible = true
                }
                
                AnimatedVisibility(
                    visible = buttonsVisible,
                    enter = fadeIn(tween(300)) + scaleIn(
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onShare?.let {
                            FilledTonalButton(onClick = {
                                it()
                                onDismiss()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("分享")
                            }
                        }
                        
                        onOpen?.let {
                            Button(onClick = {
                                it()
                                onDismiss()
                            }) {
                                Text("打开")
                            }
                        }
                        
                        if (onShare == null && onOpen == null) {
                            Button(onClick = onDismiss) {
                                Text("确定")
                            }
                        }
                    }
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("确定")
                }
            }
        },
        dismissButton = if (result.success && (onShare != null || onOpen != null)) {
            {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        } else null
    )
}

