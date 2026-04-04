// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.screen.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.screen.settings.SettingsViewModel
import com.wind.ggbond.classtime.ui.viewmodel.UpdateViewModel
import com.wind.ggbond.classtime.ui.components.UpdateDialog
import com.wind.ggbond.classtime.ui.components.UpdateCheckLoadingDialog
import android.content.Intent
import android.net.Uri

/**
 * 我的页面 - 底部导航Tab3
 * 承载：课表配置、显示偏好、提醒设置、关于、危险操作
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    mainViewModel: com.wind.ggbond.classtime.ui.viewmodel.MainViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    val compactModeEnabled by settingsViewModel.compactModeEnabled.collectAsState()
    val showWeekendEnabled by settingsViewModel.showWeekendEnabled.collectAsState()
    val bottomBarBlurEnabled by settingsViewModel.bottomBarBlurEnabled.collectAsState()
    val reminderEnabled by settingsViewModel.reminderEnabled.collectAsState()
    val defaultReminderMinutes by settingsViewModel.defaultReminderMinutes.collectAsState()
    val updateState by updateViewModel.updateState.collectAsState()
    val currentVersion by updateViewModel.currentVersion.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("我的") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // === 课表配置 ===
            item {
                ProfileSectionTitle("课表配置", isFirst = true)
            }

            // 课表管理
            item {
                ProfileNavigateItem(
                    icon = Icons.Default.CalendarToday,
                    title = "课表管理",
                    subtitle = "管理课表信息和学期日期",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.SemesterManagement.createRoute())
                    }
                )
            }

            // 节次设置
            item {
                ProfileNavigateItem(
                    icon = Icons.Default.Numbers,
                    title = "节次设置",
                    subtitle = "设置上午和下午的节次数",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.SectionCountConfig.route)
                    }
                )
            }

            // 上下课时间
            item {
                ProfileNavigateItem(
                    icon = Icons.Default.Schedule,
                    title = "上下课时间",
                    subtitle = "配置每节课的起止时间",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.ClassTimeConfig.createRoute())
                    }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // === 显示偏好 ===
            item {
                ProfileSectionTitle("显示偏好")
            }

            // 紧凑模式
            item {
                ProfileSwitchItem(
                    icon = Icons.Default.ViewCompact,
                    title = "紧凑模式",
                    subtitle = "收缩空白节次，放大有课内容",
                    checked = compactModeEnabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        settingsViewModel.updateCompactModeEnabled(it)
                    }
                )
            }

            // 显示周末
            item {
                ProfileSwitchItem(
                    icon = Icons.Default.Weekend,
                    title = "显示周末",
                    subtitle = if (showWeekendEnabled) "周一到周日" else "周一到周五",
                    checked = showWeekendEnabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        settingsViewModel.updateShowWeekendEnabled(it)
                    }
                )
            }

            // 底部栏模糊
            item {
                ProfileSwitchItem(
                    icon = Icons.Default.BlurOn,
                    title = "底部栏半透明",
                    subtitle = if (bottomBarBlurEnabled) "已开启，使用半透明背景" else "已关闭，使用不透明背景",
                    checked = bottomBarBlurEnabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        settingsViewModel.updateBottomBarBlurEnabled(it)
                    }
                )
            }

            // 背景与主题
            item {
                ProfileNavigateItem(
                    icon = Icons.Default.Wallpaper,
                    title = "背景与主题",
                    subtitle = "自定义背景图片、视频和动态配色",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.BackgroundSettings.route)
                    }
                )
            }

            // 课程配色
            item {
                ProfileNavigateItem(
                    icon = Icons.Default.Palette,
                    title = "更新课程颜色",
                    subtitle = "为所有课程应用新的配色方案",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        settingsViewModel.updateAllCoursesColor()
                    }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // === 提醒设置 ===
            item {
                ProfileSectionTitle("提醒设置")
            }

            // 课程提醒 - 跳转到二级页面
            item {
                ProfileNavigateItem(
                    icon = Icons.Default.Notifications,
                    title = "课程提醒",
                    subtitle = if (reminderEnabled)
                        "已开启，提前 $defaultReminderMinutes 分钟通知"
                    else
                        "点击配置提醒选项",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(Screen.ReminderSettings.route)
                    }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // === 关于 ===
            item {
                ProfileSectionTitle("关于")
            }

            // 应用版本（动态获取）
            item {
                ProfileNavigateItem(
                    icon = Icons.Default.Info,
                    title = "应用版本",
                    subtitle = "v${com.wind.ggbond.classtime.BuildConfig.VERSION_NAME}",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        updateViewModel.checkUpdate(force = true)
                    }
                )
            }

            // 免责声明
            item {
                ProfileNavigateItem(
                    icon = Icons.Default.WarningAmber,
                    title = stringResource(id = R.string.disclaimer_title),
                    subtitle = stringResource(id = R.string.disclaimer_subtitle),
                    onClick = { settingsViewModel.showDisclaimerDialog() }
                )
            }

            // 联系开发者
            item {
                ProfileNavigateItem(
                    icon = Icons.Default.Person,
                    title = "联系开发者",
                    subtitle = "反馈问题或建议",
                    onClick = {
                        settingsViewModel.copyToClipboard(
                            "微信: Z2X00404, QQ: 2326000841",
                            "联系方式"
                        )
                    }
                )
            }

            // === 危险区域 - 视觉隔离 ===
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                )
            }

            // 清除所有数据
            item {
                ProfileDangerItem(
                    icon = Icons.Default.DeleteForever,
                    title = "清除所有数据",
                    subtitle = "删除所有课程、学期和设置，不可恢复",
                    onClick = { settingsViewModel.showClearDataDialog() }
                )
            }
        }
    }

    // 清除数据确认对话框
    val showClearDialog by settingsViewModel.showClearDataDialog.collectAsState()
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { settingsViewModel.hideClearDataDialog() },
            title = { Text("清除所有数据") },
            text = { Text("此操作将删除所有课程、学期和设置信息，且无法恢复。确定要继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        settingsViewModel.clearAllData()
                        settingsViewModel.hideClearDataDialog()
                    }
                ) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    settingsViewModel.hideClearDataDialog()
                }) {
                    Text("取消")
                }
            }
        )
    }

    // 免责声明对话框
    val showDisclaimerDialog by settingsViewModel.showDisclaimerDialog.collectAsState()
    if (showDisclaimerDialog) {
        val disclaimerText = remember {
            try {
                context.assets.open("disclaimer.txt").use { inputStream ->
                    java.io.BufferedReader(
                        java.io.InputStreamReader(inputStream, "UTF-8")
                    ).use { reader -> reader.readText() }
                }
            } catch (e: Exception) {
                context.getString(R.string.disclaimer_content)
            }
        }

        AlertDialog(
            onDismissRequest = { settingsViewModel.hideDisclaimerDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
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
                    settingsViewModel.hideDisclaimerDialog()
                }) {
                    Text(stringResource(id = R.string.disclaimer_acknowledge))
                }
            }
        )
    }

    when (val state = updateState) {
        is UpdateViewModel.UpdateState.Checking -> {
            UpdateCheckLoadingDialog("正在检查更新...")
        }
        is UpdateViewModel.UpdateState.UpdateAvailable -> {
            UpdateDialog(
                versionInfo = state.versionInfo,
                currentVersion = currentVersion,
                onDismiss = { updateViewModel.dismissUpdate() },
                onDownload = { url ->
                    updateViewModel.dismissUpdate()
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileScreen", "打开下载链接失败", e)
                    }
                }
            )
        }
        is UpdateViewModel.UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = { updateViewModel.resetState() },
                title = { Text("检查失败") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { updateViewModel.resetState() }) {
                        Text("确定")
                    }
                }
            )
        }
        else -> {}
    }
}

// ==================== 组件 ====================

/**
 * 分类标题
 */
@Composable
private fun ProfileSectionTitle(text: String, isFirst: Boolean = false) {
    Text(
        text = text,
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

/**
 * 导航型行项目（带图标和右箭头）
 */
@Composable
private fun ProfileNavigateItem(
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
                Icons.Default.KeyboardArrowRight,
                contentDescription = "更多",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    }
}

/**
 * Switch型行项目
 */
@Composable
private fun ProfileSwitchItem(
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
 * 危险操作型行项目（红色强调）
 */
@Composable
private fun ProfileDangerItem(
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
            // 图标容器 - 红色
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "更多",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    }
}
