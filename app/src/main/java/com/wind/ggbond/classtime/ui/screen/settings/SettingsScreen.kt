package com.wind.ggbond.classtime.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.res.stringResource
import com.wind.ggbond.classtime.R
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val defaultReminderMinutes by viewModel.defaultReminderMinutes.collectAsState()
    val compactModeEnabled by viewModel.compactModeEnabled.collectAsState()
    val showWeekendEnabled by viewModel.showWeekendEnabled.collectAsState()
    val bottomBarBlurEnabled by viewModel.bottomBarBlurEnabled.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // 课程与数据管理
            item {
                SettingsCategory("课程与数据管理", isFirst = true)
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Add,
                    title = "添加课程",
                    subtitle = "手动添加新课程",
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(com.wind.ggbond.classtime.ui.navigation.Screen.CourseEdit.createRoute())
                    }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.List,
                    title = "查看课程信息",
                    subtitle = "查看和编辑当前课程表中的所有课程",
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(com.wind.ggbond.classtime.ui.navigation.Screen.CourseInfoList.route)
                    }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.CloudUpload,
                    title = "导入课表",
                    subtitle = "从教务系统或文件导入",
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(com.wind.ggbond.classtime.ui.navigation.Screen.ImportSchedule.route)
                    }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.FileUpload,
                    title = "文件导入",
                    subtitle = "从JSON、ICS、CSV文件导入课程",
                    onClick = { viewModel.showImportDialog() }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.FileDownload,
                    title = "导出课表",
                    subtitle = "导出为 ICS、JSON、CSV 等格式",
                    onClick = { viewModel.showExportDialog() }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Autorenew,
                    title = "自动更新课表",
                    subtitle = "开启后自动检查课表变化（实验性功能）",
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(com.wind.ggbond.classtime.ui.navigation.Screen.AutoUpdateSettings.route)
                    }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "清除所有数据",
                    subtitle = "删除所有课程和设置",
                    onClick = { viewModel.showClearDataDialog() },
                    isDestructive = true
                )
            }
            
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            
            // 课表配置
            item {
                SettingsCategory("课表配置")
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Dashboard,
                    title = "课表配置",
                    subtitle = "学期、节次、时间、显示等设置",
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(com.wind.ggbond.classtime.ui.navigation.Screen.TimetableSettings.route)
                    }
                )
            }
            
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            
            // 显示设置
            item {
                SettingsCategory("显示设置")
            }
            
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.ViewCompact,
                    title = "紧凑模式",
                    subtitle = "收缩空白节次，放大有课内容",
                    checked = compactModeEnabled,
                    onCheckedChange = { viewModel.updateCompactModeEnabled(it) }
                )
            }
            
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Weekend,
                    title = "显示周末",
                    subtitle = if (showWeekendEnabled) "显示周一到周日" else "只显示周一到周五",
                    checked = showWeekendEnabled,
                    onCheckedChange = { viewModel.updateShowWeekendEnabled(it) }
                )
            }
            
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.BlurOn,
                    title = "底部栏模糊效果",
                    subtitle = if (bottomBarBlurEnabled) "已启用高斯模糊背景" else "已关闭，使用纯色背景",
                    checked = bottomBarBlurEnabled,
                    onCheckedChange = { viewModel.updateBottomBarBlurEnabled(it) }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "更新课程颜色",
                    subtitle = "为所有课程应用新的配色方案",
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.updateAllCoursesColor()
                    }
                )
            }
            
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            
            // 提醒设置
            item {
                SettingsCategory("提醒设置")
            }
            
            // 课程提醒 - 跳转到二级页面
            item {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "课程提醒",
                    subtitle = if (reminderEnabled) "已开启，提前 $defaultReminderMinutes 分钟通知上课" else "点击配置提醒选项",
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(com.wind.ggbond.classtime.ui.navigation.Screen.ReminderSettings.route)
                    }
                )
            }
            
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            
            // 关于
            item {
                SettingsCategory("关于")
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "应用版本",
                    // P3-1: 动态获取版本号
                    subtitle = "v${com.wind.ggbond.classtime.BuildConfig.VERSION_NAME}",
                    onClick = {}
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.WarningAmber,
                    title = stringResource(id = R.string.disclaimer_title),
                    subtitle = stringResource(id = R.string.disclaimer_subtitle),
                    onClick = { viewModel.showDisclaimerDialog() }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "联系开发者",
                    subtitle = "微信: Z2X00404 | QQ: 2326000841",
                    onClick = { 
                        // 弹出对话框让用户选择复制微信还是QQ
                        viewModel.copyToClipboard("微信: Z2X00404, QQ: 2326000841", "联系方式")
                    }
                )
            }
        }
    }
    
    // 导出对话框
    val showExportDialog by viewModel.showExportDialog.collectAsState()
    if (showExportDialog) {
        ExportDialog(
            onDismiss = { viewModel.hideExportDialog() },
            onExport = { format ->
                viewModel.exportSchedule(format)
            },
            onShare = { format ->
                viewModel.shareExportedFile(format)
            }
        )
    }
    
    // 导入对话框
    val showImportDialog by viewModel.showImportDialog.collectAsState()
    if (showImportDialog) {
        ImportDialog(
            onDismiss = { viewModel.hideImportDialog() },
            onImport = { uri ->
                viewModel.importSchedule(uri)
            }
        )
    }
    
    // 导出结果对话框
    val exportResult by viewModel.exportResult.collectAsState()
    exportResult?.let { result ->
        ExportResultDialog(
            result = result,
            onDismiss = { viewModel.clearExportResult() },
            onShare = result.filePath?.let { path ->
                {
                    // 通过ViewModel调用分享功能
                    viewModel.shareFile(path)
                }
            }
        )
    }
    
    // 清除数据确认对话框
    val showClearDialog by viewModel.showClearDataDialog.collectAsState()
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearDataDialog() },
            title = { Text("清除所有数据") },
            text = { Text("此操作将删除所有课程、学期和设置信息，且无法恢复。确定要继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.clearAllData()
                        viewModel.hideClearDataDialog()
                    }
                ) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.hideClearDataDialog()
                }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 免责声明对话框
    val showDisclaimerDialog by viewModel.showDisclaimerDialog.collectAsState()
    if (showDisclaimerDialog) {
        // 从 assets 文件读取免责声明内容（避免 STRING_TOO_LARGE 错误）
        val disclaimerText = remember {
            try {
                context.assets.open("disclaimer.txt").use { inputStream ->
                    java.io.BufferedReader(java.io.InputStreamReader(inputStream, "UTF-8")).use { reader ->
                        reader.readText()
                    }
                }
            } catch (e: Exception) {
                // 如果读取失败，回退到字符串资源
                android.util.Log.e("SettingsScreen", "Failed to read disclaimer.txt", e)
                context.getString(R.string.disclaimer_content)
            }
        }
        
        AlertDialog(
            onDismissRequest = { viewModel.hideDisclaimerDialog() },
            title = { 
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "免责声明",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("免责声明")
                }
            },
            text = { 
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            text = disclaimerText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.hideDisclaimerDialog()
                }) {
                    Text(stringResource(id = R.string.disclaimer_acknowledge))
                }
            }
        )
    }
}

@Composable
fun SettingsCategory(title: String, isFirst: Boolean = false) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = 20.dp, 
            end = 20.dp, 
            top = if (isFirst) 8.dp else 20.dp, 
            bottom = 8.dp
        )
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    isSubItem: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (isSubItem) 56.dp else 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标容器
            Surface(
                modifier = Modifier.size(if (isSubItem) 32.dp else 36.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = if (isDestructive) 
                    MaterialTheme.colorScheme.errorContainer
                else if (isSubItem)
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(if (isSubItem) 16.dp else 18.dp),
                        tint = if (isDestructive) 
                            MaterialTheme.colorScheme.error
                        else if (isSubItem)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = if (isSubItem) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSubItem) FontWeight.Normal else FontWeight.Medium,
                    color = if (isDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isSubItem) 0.7f else 1f)
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "查看详情",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isSubItem: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (isSubItem) 56.dp else 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标容器
            Surface(
                modifier = Modifier.size(if (isSubItem) 32.dp else 36.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = if (isSubItem)
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(if (isSubItem) 16.dp else 18.dp),
                        tint = if (isSubItem)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = if (isSubItem) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSubItem) FontWeight.Normal else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isSubItem) 0.7f else 1f)
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(it)
                },
                modifier = Modifier.scale(if (isSubItem) 0.9f else 1f)
            )
        }
    }
}

@Composable
fun CompactWarningCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clickable(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                if (message.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "去设置",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}





