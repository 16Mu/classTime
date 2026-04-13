// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.screen.profile

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.wind.ggbond.classtime.service.ApkDownloadManager
import com.wind.ggbond.classtime.util.AppLogger
import android.net.Uri
import java.io.File
import kotlinx.coroutines.launch

private fun formatVersionName(raw: String): String {
    val cleaned = raw.removePrefix("v")
    return "v$cleaned"
}

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
    val glassEffectEnabled by settingsViewModel.glassEffectEnabled.collectAsState()
    val desktopModeEnabled by settingsViewModel.desktopModeEnabled.collectAsState()
    val isWallpaperSet by settingsViewModel.isWallpaperSet.collectAsState()
    val reminderEnabled by settingsViewModel.reminderEnabled.collectAsState()
    val defaultReminderMinutes by settingsViewModel.defaultReminderMinutes.collectAsState()
    val updateState by updateViewModel.updateState.collectAsState()
    val currentVersion by updateViewModel.currentVersion.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingInstallFile = remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    var showGlassEffectDisableDialog by remember { mutableStateOf(false) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        pendingInstallFile.value?.let { file ->
            if (ApkDownloadManager.canRequestInstall(context)) {
                ApkDownloadManager.installApk(context, file)
            } else {
                AppLogger.w("ProfileScreen", "用户未授予安装权限")
            }
            pendingInstallFile.value = null
        }
    }

    LaunchedEffect(Unit) {
        updateViewModel.observeDownloadState()
    }

    LaunchedEffect(Unit) {
        settingsViewModel.messageEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun hapticClick(onClick: () -> Unit): () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }

    fun hapticToggle(onToggle: (Boolean) -> Unit): (Boolean) -> Unit = { value ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggle(value)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text("我的") }) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // === 课表配置 ===
            item { ProfileSectionTitle("课表配置", isFirst = true) }
            item { ProfileNavigateItem(Icons.Default.CalendarToday, "课表管理", "管理课表信息和学期日期", hapticClick { navController.navigate(Screen.SemesterManagement.createRoute()) }) }
            item { ProfileNavigateItem(Icons.Default.Numbers, "节次设置", "设置上午和下午的节次数", hapticClick { navController.navigate(Screen.SectionCountConfig.route) }) }
            item { ProfileNavigateItem(Icons.Default.Schedule, "上下课时间", "配置每节课的起止时间", hapticClick { navController.navigate(Screen.ClassTimeConfig.createRoute()) }) }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            // === 显示偏好 ===
            item { ProfileSectionTitle("显示偏好") }
            item { ProfileSwitchItem(Icons.Default.ViewCompact, "紧凑模式", "收缩空白节次，放大有课内容", compactModeEnabled, hapticToggle { settingsViewModel.updateCompactModeEnabled(it) }) }
            item { ProfileSwitchItem(Icons.Default.Weekend, "显示周末", if (showWeekendEnabled) "周一到周日" else "周一到周五", showWeekendEnabled, hapticToggle { settingsViewModel.updateShowWeekendEnabled(it) }) }
            item { ProfileSwitchItem(Icons.Default.BlurOn, "壁纸透视",
                when {
                    desktopModeEnabled -> "桌面模式下自动开启，无法手动关闭"
                    glassEffectEnabled -> "已开启，组件背景半透明，壁纸可见"
                    else -> "已关闭，所有组件使用不透明背景"
                },
                glassEffectEnabled,
                onCheckedChange = { newValue ->
                    if (desktopModeEnabled) {
                        scope.launch { snackbarHostState.showSnackbar("桌面模式开启时无法关闭壁纸透视，请先关闭桌面模式") }
                    } else if (!newValue && isWallpaperSet) {
                        showGlassEffectDisableDialog = true
                    } else {
                        settingsViewModel.updateGlassEffectEnabled(newValue)
                    }
                },
                enabled = !desktopModeEnabled
            ) }
            item { ProfileNavigateItem(Icons.Default.Wallpaper, "背景与主题", "自定义背景图片、视频和动态配色", hapticClick { navController.navigate(Screen.BackgroundSettings.route) }) }
            item { ProfileNavigateItem(Icons.Default.Palette, "课程颜色设置", "主题色调、随机配色、手动选色", hapticClick { navController.navigate(Screen.CourseColorSettings.route) }) }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            // === 提醒设置 ===
            item { ProfileSectionTitle("提醒设置") }
            item { ProfileNavigateItem(Icons.Default.Notifications, "课程提醒", if (reminderEnabled) "已开启，提前 $defaultReminderMinutes 分钟通知" else "点击配置提醒选项", hapticClick { navController.navigate(Screen.ReminderSettings.route) }) }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            // === 关于 ===
            item { ProfileSectionTitle("关于") }
            item { ProfileNavigateItem(Icons.Default.Info, "应用版本", formatVersionName(com.wind.ggbond.classtime.BuildConfig.VERSION_NAME), { }) }
            item {
                val hasUpdate by updateViewModel.hasUpdate.collectAsState()
                ProfileNavigateItem(
                    Icons.Default.SystemUpdate,
                    "检查更新",
                    if (hasUpdate) "发现新版本，点击查看" else "当前已是最新版本",
                    hapticClick { updateViewModel.checkUpdate(force = true); Unit },
                    showBadge = hasUpdate
                )
            }
            item { ProfileNavigateItem(Icons.Default.WarningAmber, stringResource(id = R.string.disclaimer_title), stringResource(id = R.string.disclaimer_subtitle), { settingsViewModel.showDisclaimerDialog() }) }
            item { ProfileNavigateItem(Icons.Default.Person, "联系开发者", "反馈问题或建议", { settingsViewModel.copyToClipboard("微信: Z2X00404, QQ: 2326000841", "联系方式") }) }

            // === 危险区域 ===
            item { Spacer(Modifier.height(24.dp)) }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)) }
            item { ProfileDangerItem(Icons.Default.DeleteForever, "清除所有数据", "删除所有课程、学期和设置，不可恢复", { settingsViewModel.showClearDataDialog() }) }
        }
    }

    if (settingsViewModel.showClearDataDialog.collectAsState().value) {
        AlertDialog(
            onDismissRequest = { settingsViewModel.hideClearDataDialog() },
            title = { Text("清除所有数据") },
            text = { Text("此操作将删除所有课程、学期和设置信息，且无法恢复。确定要继续吗？") },
            confirmButton = {
                TextButton(hapticClick {
                    settingsViewModel.clearAllData()
                    settingsViewModel.hideClearDataDialog()
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(hapticClick { settingsViewModel.hideClearDataDialog() }) { Text("取消") }
            }
        )
    }

    if (showGlassEffectDisableDialog) {
        AlertDialog(
            onDismissRequest = { showGlassEffectDisableDialog = false },
            title = { Text("关闭壁纸透视") },
            text = { Text("关闭壁纸透视后，壁纸将不可见。确定要关闭吗？") },
            confirmButton = {
                TextButton(hapticClick {
                    settingsViewModel.updateGlassEffectEnabled(false)
                    showGlassEffectDisableDialog = false
                }) { Text("关闭", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(hapticClick { showGlassEffectDisableDialog = false }) { Text("取消") }
            }
        )
    }

    if (settingsViewModel.showDisclaimerDialog.collectAsState().value) {
        val disclaimerText = remember {
            try {
                context.assets.open("disclaimer.txt").use { inputStream ->
                    java.io.BufferedReader(java.io.InputStreamReader(inputStream, "UTF-8")).use { it.readText() }
                }
            } catch (_: Exception) { context.getString(R.string.disclaimer_content) }
        }
        AlertDialog(
            onDismissRequest = { settingsViewModel.hideDisclaimerDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "警告", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("免责声明")
                }
            },
            text = {
                LazyColumn(Modifier.fillMaxWidth()) {
                    item { Text(disclaimerText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
            confirmButton = {
                TextButton(hapticClick { settingsViewModel.hideDisclaimerDialog() }) { Text(stringResource(id = R.string.disclaimer_acknowledge)) }
            }
        )
    }

    LaunchedEffect(updateState) {
        if (updateState is UpdateViewModel.UpdateState.NoUpdate) {
            snackbarHostState.showSnackbar("当前已是最新版本")
            updateViewModel.resetState()
        }
    }

    val downloadState by updateViewModel.downloadState.collectAsState()

    when (val state = updateState) {
        is UpdateViewModel.UpdateState.Checking -> UpdateCheckLoadingDialog("正在检查更新...")
        is UpdateViewModel.UpdateState.UpdateAvailable -> UpdateDialog(
            versionInfo = state.versionInfo, currentVersion = currentVersion,
            onDismiss = { updateViewModel.dismissUpdate() },
            onDownload = { url ->
                updateViewModel.dismissUpdate()
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                catch (e: Exception) { AppLogger.e("ProfileScreen", "打开下载链接失败", e) }
            },
            downloadState = downloadState,
            onDownloadApk = { url -> updateViewModel.downloadApk(url) },
            onInstall = { file ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ApkDownloadManager.canRequestInstall(context)) {
                    pendingInstallFile.value = file
                    installPermissionLauncher.launch(ApkDownloadManager.getInstallPermissionIntent(context))
                } else {
                    ApkDownloadManager.installApk(context, file)
                }
            },
            onResetDownload = { updateViewModel.resetDownloadState() }
        )
        is UpdateViewModel.UpdateState.Error -> AlertDialog(
            onDismissRequest = { updateViewModel.resetState() },
            title = { Text("检查失败") },
            text = { Text(state.message) },
            confirmButton = { TextButton({ updateViewModel.resetState() }) { Text("确定") } }
        )
        else -> {}
    }
}

// ==================== 共享子组件 ====================

@Composable
private fun ProfileIconBox(icon: ImageVector, containerColor: Color, tint: Color) {
    Surface(Modifier.size(36.dp), shape = RoundedCornerShape(8.dp), color = containerColor) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, Modifier.size(18.dp), tint = tint)
        }
    }
}

@Composable
private fun RowScope.ProfileTextContent(title: String, subtitle: String, titleColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = titleColor)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileRowContent(
    icon: ImageVector, iconContainerColor: Color, iconTint: Color,
    title: String, subtitle: String, titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable () -> Unit = {}
) {
    Surface(Modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileIconBox(icon, iconContainerColor, iconTint)
            Spacer(Modifier.width(12.dp))
            ProfileTextContent(title, subtitle, titleColor)
            trailing()
        }
    }
}

// ==================== 行项目组件 ====================

@Composable
private fun ProfileSectionTitle(text: String, isFirst: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = if (isFirst) 8.dp else 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun ProfileNavigateItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, showBadge: Boolean = false) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgedBox(
                badge = {
                    if (showBadge) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(8.dp)
                        )
                    }
                }
            ) {
                ProfileIconBox(icon, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            ProfileTextContent(title, subtitle, if (showBadge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun ProfileSwitchItem(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    ProfileRowContent(icon, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary, title, subtitle,
        titleColor = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ProfileDangerItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileIconBox(icon, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            ProfileTextContent(title, subtitle, MaterialTheme.colorScheme.error)
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        }
    }
}
