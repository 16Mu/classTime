// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.screen.reminder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.ui.navigation.Screen

/**
 * 课程提醒设置页面（二级页面）
 * 包含提醒总开关、偏好设置、权限状态和工具入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(
    navController: NavController,
    viewModel: ReminderSettingsViewModel = hiltViewModel()
) {
    // 收集 ViewModel 状态
    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val defaultReminderMinutes by viewModel.defaultReminderMinutes.collectAsState()
    val headsUpNotificationEnabled by viewModel.headsUpNotificationEnabled.collectAsState()
    val permissionStates by viewModel.permissionStates.collectAsState()
    val showMinutesSelector by viewModel.showMinutesSelector.collectAsState()
    val showReminderTestDialog by viewModel.showReminderTestDialog.collectAsState()
    val haptic = LocalHapticFeedback.current

    // 页面每次可见时刷新权限状态
    LaunchedEffect(Unit) {
        viewModel.refreshPermissionStates()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("课程提醒") },
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ==================== 总开关 ====================
            item {
                ReminderMasterSwitch(
                    enabled = reminderEnabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.updateReminderEnabled(it)
                    }
                )
            }

            // ==================== 提醒偏好（开启后显示）====================
            item {
                AnimatedVisibility(
                    visible = reminderEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 分类标题
                        SectionTitle("提醒偏好")

                        // 提前提醒时间
                        ReminderSettingsItem(
                            icon = Icons.Default.Timer,
                            title = "提前提醒时间",
                            subtitle = "${defaultReminderMinutes}分钟",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.showMinutesSelector()
                            }
                        )

                        // 悬浮通知
                        ReminderSettingsSwitchItem(
                            icon = Icons.Default.NotificationsActive,
                            title = "悬浮通知",
                            subtitle = "显示横幅式通知效果",
                            checked = headsUpNotificationEnabled,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.updateHeadsUpNotificationEnabled(it)
                            }
                        )
                    }
                }
            }

            // ==================== 权限状态（开启后显示）====================
            item {
                AnimatedVisibility(
                    visible = reminderEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 分类标题
                        SectionTitle("权限状态")

                        // 说明文字
                        Text(
                            text = "以下权限可帮助提醒准时送达，建议开启",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            // 权限列表项（开启后显示）
            if (reminderEnabled) {
                items(permissionStates, key = { it.type.name }) { permission ->
                    PermissionStatusItem(
                        permission = permission,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            // 点击跳转到权限教程页面
                            navController.navigate(
                                Screen.PermissionTutorial.createRoute(permission.type.name)
                            )
                        }
                    )
                }
            }

            // ==================== 工具（开启后显示）====================
            item {
                AnimatedVisibility(
                    visible = reminderEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 分类标题
                        SectionTitle("工具")

                        // 查看所有提醒
                        ReminderSettingsItem(
                            icon = Icons.Default.Checklist,
                            title = "查看所有提醒",
                            subtitle = "管理已创建的课程提醒",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navController.navigate(Screen.ReminderManagement.route)
                            }
                        )

                        // 提醒测试
                        ReminderSettingsItem(
                            icon = Icons.Default.BugReport,
                            title = "提醒测试",
                            subtitle = "测试提醒功能是否正常",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.showReminderTestDialog()
                            }
                        )
                    }
                }
            }
        }
    }

    // ==================== 对话框 ====================

    // 提前时间选择器对话框
    if (showMinutesSelector) {
        MinutesSelectorDialog(
            currentMinutes = defaultReminderMinutes,
            onSelect = { minutes ->
                viewModel.updateDefaultReminderMinutes(minutes)
                viewModel.hideMinutesSelector()
            },
            onDismiss = { viewModel.hideMinutesSelector() }
        )
    }

    // 提醒测试对话框（复用已有的对话框结构）
    if (showReminderTestDialog) {
        ReminderTestDialog(
            onDismiss = { viewModel.hideReminderTestDialog() },
            onTestImmediateReminder = { viewModel.testImmediateReminder() },
            onTestNextClassReminder = { viewModel.testNextClassReminder() },
            onTestCourseStartReminder = { viewModel.testCourseStartReminder() },
            onTestCourseEndReminder = { viewModel.testCourseEndReminder() },
            onTestAllReminders = { viewModel.testAllReminders() },
            onRunDiagnostic = { viewModel.runReminderDiagnostic() }
        )
    }
}

// ==================== 子组件 ====================

/**
 * 提醒总开关区域
 * 顶部大开关，带说明文字
 */
@Composable
private fun ReminderMasterSwitch(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标容器
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (enabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 文字区域
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "课程提醒",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (enabled) "已开启，将在上课前提醒你" else "开启后，将在上课前提醒你",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 开关
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/**
 * 分类标题
 */
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
    )
}

/**
 * 通用设置项（点击跳转类型）
 */
@Composable
private fun ReminderSettingsItem(
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
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
 * 通用设置项（Switch 类型）
 */
@Composable
private fun ReminderSettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
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

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/**
 * 权限状态列表项
 * 展示权限名称、描述和当前状态，点击跳转教程页
 */
@Composable
private fun PermissionStatusItem(
    permission: PermissionState,
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态指示圆点
            Surface(
                modifier = Modifier.size(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = if (permission.isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            ) {}

            Spacer(modifier = Modifier.width(14.dp))

            // 权限名称和描述
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = permission.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = permission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // 状态标签
            if (permission.isGranted) {
                Text(
                    text = "已开启",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "去开启",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

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
 * 提前提醒时间选择对话框
 */
@Composable
private fun MinutesSelectorDialog(
    currentMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 可选的分钟数列表
    val options = listOf(5, 10, 15, 20, 30)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提前提醒时间") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = minutes == currentMinutes,
                            onClick = { onSelect(minutes) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${minutes}分钟",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
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
 * 提醒测试对话框
 * 提供各类提醒测试和诊断功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTestDialog(
    onDismiss: () -> Unit,
    onTestImmediateReminder: () -> Unit,
    onTestNextClassReminder: () -> Unit,
    onTestCourseStartReminder: () -> Unit,
    onTestCourseEndReminder: () -> Unit,
    onTestAllReminders: () -> Unit,
    onRunDiagnostic: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("课程提醒测试")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "选择要测试的提醒功能：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 立即提醒测试
                TextButton(
                    onClick = { onTestImmediateReminder(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Notifications, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("立即提醒测试")
                }
                // 下节课提醒测试
                TextButton(
                    onClick = { onTestNextClassReminder(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.NextPlan, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("下节课提醒测试")
                }
                // 上课提醒测试
                TextButton(
                    onClick = { onTestCourseStartReminder(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("上课提醒测试")
                }
                // 下课提醒测试
                TextButton(
                    onClick = { onTestCourseEndReminder(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("下课提醒测试")
                }

                HorizontalDivider()

                // 诊断
                OutlinedButton(
                    onClick = { onRunDiagnostic(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SettingsSuggest, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("运行诊断")
                }
                // 全部测试
                Button(
                    onClick = { onTestAllReminders(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SelectAll, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("全部测试")
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
