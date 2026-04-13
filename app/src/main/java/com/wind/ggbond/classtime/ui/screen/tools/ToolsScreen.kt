// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.screen.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.wind.ggbond.classtime.widget.WidgetPinHelper.WidgetType
import com.wind.ggbond.classtime.widget.WidgetPinHelper.PinResult
import com.wind.ggbond.classtime.util.AppLogger
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var fallbackGuide by remember { mutableStateOf<PinResult.FallbackNeeded?>(null) }
    var pendingWidgetType by remember { mutableStateOf<WidgetType?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        AppLogger.i("ToolsScreen", "REQUEST_BIND_WIDGETS result: granted=$isGranted")
        if (isGranted) {
            pendingWidgetType?.let { widgetType ->
                val result = WidgetPinHelper.requestPinWidget(context, widgetType)
                if (result is PinResult.FallbackNeeded) fallbackGuide = result
            }
        } else {
            Toast.makeText(context, "需要小组件权限才能添加，请手动添加", Toast.LENGTH_LONG).show()
            fallbackGuide = PinResult.FallbackNeeded(
                WidgetType.TODAY_COURSE,
                "请手动添加小组件",
                WidgetPinHelper.getManualGuideSteps()
            )
        }
        pendingWidgetType = null
    }

    fun handleWidgetPin(widgetType: WidgetType) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val result = WidgetPinHelper.requestPinWidget(context, widgetType)
        when (result) {
            is PinResult.FallbackNeeded -> fallbackGuide = result
            is PinResult.Failed -> {
                if (result.reason == "NEED_PERMISSION" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    pendingWidgetType = widgetType
                    permissionLauncher.launch("android.permission.REQUEST_BIND_WIDGETS")
                }
            }
            is PinResult.Success -> {}
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.messageEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("工具") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { ToolsSectionTitle("导入导出") }

            item {
                ToolsRecommendCard(
                    title = "从教务系统导入课表",
                    subtitle = "支持27+所高校，登录后自动获取",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.SchoolSelection.route)
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolsCompactCard(
                        icon = Icons.Outlined.EditNote,
                        title = "手动添加",
                        subtitle = "逐条填写课程",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigate(Screen.BatchCourseCreate.route)
                        }
                    )
                    ToolsCompactCard(
                        icon = Icons.Outlined.Upload,
                        title = "从文件导入",
                        subtitle = "JSON/CSV/Excel",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            settingsViewModel.showImportDialog()
                        }
                    )
                }
            }

            item {
                ToolsUnifiedCard(
                    icon = Icons.Outlined.FileDownload,
                    title = "导出课表",
                    subtitle = "多种格式导出",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        settingsViewModel.showExportDialog()
                    }
                )
            }

            item { ToolsSectionTitle("课程管理") }

            item {
                ToolsUnifiedCard(
                    icon = Icons.AutoMirrored.Outlined.List,
                    title = "所有课程",
                    subtitle = "查看和编辑课程表中的全部课程",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.CourseInfoList.route)
                    }
                )
            }

            item {
                ToolsUnifiedCard(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "调课记录",
                    subtitle = "查看和管理临时调课",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.AdjustmentManagement.route)
                    }
                )
            }

            item { ToolsSectionTitle("自动更新") }

            item {
                ToolsUnifiedCard(
                    icon = Icons.Outlined.Autorenew,
                    title = "自动更新设置",
                    subtitle = "配置自动检查课表变化",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.AutoUpdateSettings.route)
                    }
                )
            }

            item { ToolsSectionTitle("桌面小组件") }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolsCompactCard(
                        icon = Icons.Outlined.CalendarMonth,
                        title = "今日课程",
                        subtitle = "4×2 课程列表",
                        modifier = Modifier.weight(1f),
                        onClick = { handleWidgetPin(WidgetType.TODAY_COURSE) }
                    )
                    ToolsCompactCard(
                        icon = Icons.Outlined.Schedule,
                        title = "下节课倒计时",
                        subtitle = "3×2 倒计时",
                        modifier = Modifier.weight(1f),
                        onClick = { handleWidgetPin(WidgetType.NEXT_CLASS) }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolsCompactCard(
                        icon = Icons.Outlined.ViewList,
                        title = "紧凑列表",
                        subtitle = "省空间样式",
                        modifier = Modifier.weight(1f),
                        onClick = { handleWidgetPin(WidgetType.COMPACT_LIST) }
                    )
                    ToolsCompactCard(
                        icon = Icons.Outlined.DateRange,
                        title = "周概览",
                        subtitle = "一周总览",
                        modifier = Modifier.weight(1f),
                        onClick = { handleWidgetPin(WidgetType.WEEK_OVERVIEW) }
                    )
                }
            }

            item {
                ToolsUnifiedCard(
                    icon = Icons.Outlined.Dashboard,
                    title = "大尺寸课程表",
                    subtitle = "4×4 大屏展示更多课程细节，支持滚动浏览",
                    onClick = { handleWidgetPin(WidgetType.LARGE_TODAY_COURSE) }
                )
            }

            item {
                ToolsUnifiedCard(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "智能课表",
                    subtitle = "3×2 智能切换，今日课程结束后自动展示明日课程",
                    onClick = { handleWidgetPin(WidgetType.TOMORROW_COURSE) }
                )
            }
        }
    }

    val showExportDialog by settingsViewModel.showExportDialog.collectAsState()
    if (showExportDialog) {
        com.wind.ggbond.classtime.ui.screen.settings.ExportDialog(
            onDismiss = { settingsViewModel.hideExportDialog() },
            onExport = { format -> settingsViewModel.exportSchedule(format) },
            onShare = { format -> settingsViewModel.shareExportedFile(format) }
        )
    }

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

    val showImportDialog by settingsViewModel.showImportDialog.collectAsState()
    if (showImportDialog) {
        com.wind.ggbond.classtime.ui.screen.settings.ImportDialog(
            onDismiss = { settingsViewModel.hideImportDialog() },
            onImport = { uri -> settingsViewModel.importSchedule(uri) }
        )
    }

    fallbackGuide?.let { guide ->
        AlertDialog(
            onDismissRequest = { fallbackGuide = null },
            title = {
                Text(
                    text = guide.guideTitle,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        text = "「${guide.widgetType.displayName}」无法自动添加，请按以下步骤手动添加：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    guide.guideSteps.forEach { step ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { fallbackGuide = null }) {
                    Text("我知道了")
                }
            }
        )
    }
}

@Composable
private fun ToolsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ToolsRecommendCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Login,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "推荐",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun ToolsCompactCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ToolsUnifiedCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            if (trailingText != null && onTrailingClick != null) {
                TextButton(
                    onClick = onTrailingClick,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            } else {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
        }
    }
}
