package com.wind.ggbond.classtime.ui.screen.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.screen.settings.SettingsViewModel
import com.wind.ggbond.classtime.widget.WidgetPinHelper

/**
 * 工具页面 - 底部导航Tab2
 * 承载：导入导出、课程管理、自动更新等操作型功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    
    // 小组件选择对话框状态
    var showWidgetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("工具") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // === 导入导出 ===
            item {
                ToolsSectionTitle("导入导出")
            }

            // 推荐方式：大CTA卡片
            item {
                Card(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.SchoolSelection.route)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧图标
                        Icon(
                            Icons.AutoMirrored.Filled.Login,
                            contentDescription = "从教务系统导入",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        // 中间文字
                        Column(modifier = Modifier.weight(1f)) {
                            // "推荐"标签
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(
                                    "推荐",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            // 标题
                            Text(
                                "从教务系统导入课表",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            // 描述
                            Text(
                                "支持27+所高校，登录后自动获取",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        // 右侧箭头
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // 次要方式：两个小卡片并排
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 手动添加
                    Card(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigate(Screen.BatchCourseCreate.route)
                        },
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = "手动添加",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "手动添加",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "逐条填写课程",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 从文件导入
                    Card(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            settingsViewModel.showImportDialog()
                        },
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = "从文件导入",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "从文件导入",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "JSON/ICS/CSV",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 导出课表
            item {
                ToolsRowItem(
                    icon = Icons.Default.FileDownload,
                    title = "导出课表",
                    subtitle = "多种格式导出",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        settingsViewModel.showExportDialog()
                    }
                )
            }

            // === 课程管理 ===
            item {
                ToolsSectionTitle("课程管理")
            }

            // 所有课程
            item {
                ToolsRowItem(
                    icon = Icons.Default.List,
                    title = "所有课程",
                    subtitle = "查看和编辑课程表中的全部课程",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.CourseInfoList.route)
                    }
                )
            }

            // 调课记录
            item {
                ToolsRowItem(
                    icon = Icons.Default.SwapHoriz,
                    title = "调课记录",
                    subtitle = "查看和管理临时调课",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.AdjustmentManagement.route)
                    }
                )
            }

            // === 自动更新 ===
            item {
                ToolsSectionTitle("自动更新")
            }

            // 自动更新设置
            item {
                ToolsRowItem(
                    icon = Icons.Default.Autorenew,
                    title = "自动更新设置",
                    subtitle = "配置自动检查课表变化",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.AutoUpdateSettings.route)
                    }
                )
            }

            // 临时隐藏整个桌面小组件分类
            // === 桌面小组件 ===
            // item {
            //     ToolsSectionTitle("桌面小组件")
            // }

            // // 添加桌面小组件
            // item {
            //     ToolsRowItem(
            //         icon = Icons.Default.Widgets,
            //         title = "添加桌面小组件",
            //         subtitle = "快速将小组件添加到桌面",
            //         onClick = {
            //             haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            //             showWidgetDialog = true
            //         }
            //     )
            // }

            // 底部间距
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 小组件选择对话框
    if (showWidgetDialog) {
        WidgetSelectionDialog(
            onDismiss = { showWidgetDialog = false },
            onSelectWidget = { widgetType ->
                showWidgetDialog = false
                WidgetPinHelper.requestPinWidget(context, widgetType)
            }
        )
    }

    // 导出对话框（复用SettingsViewModel的逻辑）
    val showExportDialog by settingsViewModel.showExportDialog.collectAsState()
    if (showExportDialog) {
        com.wind.ggbond.classtime.ui.screen.settings.ExportDialog(
            onDismiss = { settingsViewModel.hideExportDialog() },
            onExport = { format -> settingsViewModel.exportSchedule(format) },
            onShare = { format -> settingsViewModel.shareExportedFile(format) }
        )
    }

    // 导出结果对话框
    val exportResult by settingsViewModel.exportResult.collectAsState()
    exportResult?.let { result ->
        com.wind.ggbond.classtime.ui.screen.settings.ExportResultDialog(
            result = result,
            onDismiss = { settingsViewModel.clearExportResult() },
            onShare = result.filePath?.let { path ->
                { settingsViewModel.shareFile(path) }
            }
        )
    }

    // 导入对话框
    val showImportDialog by settingsViewModel.showImportDialog.collectAsState()
    if (showImportDialog) {
        com.wind.ggbond.classtime.ui.screen.settings.ImportDialog(
            onDismiss = { settingsViewModel.hideImportDialog() },
            onImport = { uri -> settingsViewModel.importSchedule(uri) }
        )
    }
}

/**
 * 工具页面分类标题
 */
@Composable
private fun ToolsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/**
 * 工具页面行项目（带图标和右箭头）
 */
@Composable
private fun ToolsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标容器
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 文字
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 右箭头
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * 小组件选择对话框
 * 
 * @param onDismiss 关闭对话框回调
 * @param onSelectWidget 选择小组件回调
 */
@Composable
private fun WidgetSelectionDialog(
    onDismiss: () -> Unit,
    onSelectWidget: (WidgetPinHelper.WidgetType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择小组件",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 遍历所有小组件类型
                WidgetPinHelper.WidgetType.entries.forEach { widgetType ->
                    WidgetOptionItem(
                        widgetType = widgetType,
                        onClick = { onSelectWidget(widgetType) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 小组件选项项
 * 
 * @param widgetType 小组件类型
 * @param onClick 点击回调
 */
@Composable
private fun WidgetOptionItem(
    widgetType: WidgetPinHelper.WidgetType,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 小组件图标
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 小组件信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = WidgetPinHelper.getWidgetDisplayName(widgetType),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = WidgetPinHelper.getWidgetDescription(widgetType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 添加图标
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
