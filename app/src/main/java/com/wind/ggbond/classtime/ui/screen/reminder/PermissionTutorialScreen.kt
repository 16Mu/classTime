package com.wind.ggbond.classtime.ui.screen.reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.wind.ggbond.classtime.util.BackgroundPermissionHelper
import com.wind.ggbond.classtime.util.PermissionGuideManager

/**
 * 手机品牌枚举
 * 用于品牌切换和品牌专属教程匹配
 */
enum class PhoneBrand(val displayName: String) {
    AUTO("自动检测"),
    XIAOMI("小米 / Redmi"),
    HUAWEI("华为 / 荣耀"),
    OPPO("OPPO"),
    VIVO("vivo / iQOO"),
    ONEPLUS("一加"),
    REALME("真我 realme"),
    BLACKSHARK("黑鲨"),
    SAMSUNG("三星"),
    MEIZU("魅族"),
    OTHER("其他 Android")
}

/**
 * 权限教程页面（三级页面）
 * 针对特定权限展示品牌专属的设置教程和一键跳转
 *
 * @param navController 导航控制器
 * @param permissionTypeName 权限类型名称（PermissionType 枚举的 name）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionTutorialScreen(
    navController: NavController,
    permissionTypeName: String
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // 解析权限类型
    val permissionType = remember {
        try {
            PermissionType.valueOf(permissionTypeName)
        } catch (e: IllegalArgumentException) {
            PermissionType.NOTIFICATION
        }
    }

    // 自动检测当前品牌
    val detectedBrand = remember { detectCurrentBrand() }

    // 当前选中的品牌（默认自动检测）
    var selectedBrand by remember { mutableStateOf(PhoneBrand.AUTO) }

    // 品牌选择菜单展开状态
    var showBrandMenu by remember { mutableStateOf(false) }

    // 实际使用的品牌（AUTO 时使用检测到的品牌）
    val activeBrand = if (selectedBrand == PhoneBrand.AUTO) detectedBrand else selectedBrand

    // 获取该权限在该品牌下的教程数据
    val tutorialData = remember(permissionType, activeBrand) {
        getTutorialData(permissionType, activeBrand)
    }

    // 页面标题
    val pageTitle = remember(permissionType) {
        getPermissionTitle(permissionType)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(pageTitle) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 右上角品牌切换按钮
                    Box {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showBrandMenu = true
                        }) {
                            Icon(Icons.Default.PhoneAndroid, "切换品牌")
                        }
                        // 品牌选择下拉菜单
                        DropdownMenu(
                            expanded = showBrandMenu,
                            onDismissRequest = { showBrandMenu = false }
                        ) {
                            PhoneBrand.entries.forEach { brand ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // 当前选中标记
                                            if (brand == selectedBrand) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            } else {
                                                Spacer(modifier = Modifier.width(24.dp))
                                            }
                                            Text(
                                                text = if (brand == PhoneBrand.AUTO) {
                                                    "${brand.displayName}（${detectedBrand.displayName}）"
                                                } else {
                                                    brand.displayName
                                                }
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedBrand = brand
                                        showBrandMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 当前系统信息
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "当前系统: ${activeBrand.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedBrand == PhoneBrand.AUTO) {
                            Text(
                                text = "（自动检测）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // 权限说明
            item {
                Text(
                    text = tutorialData.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            }

            // 操作步骤标题
            item {
                Text(
                    text = "操作步骤",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 步骤列表
            itemsIndexed(tutorialData.steps) { index, step ->
                TutorialStepItem(
                    stepNumber = index + 1,
                    content = step
                )
            }

            // 一键跳转按钮
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        openPermissionSettings(context, permissionType)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("跳转到系统设置", fontWeight = FontWeight.Bold)
                }
            }

            // 手动确认按钮（仅对需要手动确认的权限显示）
            if (isManualConfirmPermission(permissionType)) {
                item {
                    val isConfirmed = remember {
                        mutableStateOf(
                            PermissionGuideManager.isManualStepConfirmed(
                                context,
                                getManualStepKey(permissionType)
                            )
                        )
                    }

                    if (!isConfirmed.value) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                PermissionGuideManager.saveManualStepConfirmation(
                                    context,
                                    getManualStepKey(permissionType),
                                    true
                                )
                                isConfirmed.value = true
                                android.widget.Toast.makeText(context, "已标记为完成", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("我已完成此设置", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "已确认完成",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // 底部温馨提示
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "设置完成后返回此页面，权限状态会自动刷新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 教程步骤项
 * 显示序号和步骤内容
 */
@Composable
private fun TutorialStepItem(
    stepNumber: Int,
    content: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 步骤序号圆圈
        Surface(
            modifier = Modifier.size(28.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stepNumber.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        // 步骤描述
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp,
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp)
        )
    }
}

// ==================== 数据与工具函数 ====================

/**
 * 教程数据类
 */
data class TutorialData(
    val description: String,    // 权限说明
    val steps: List<String>     // 操作步骤列表
)

/**
 * 获取权限页面标题
 */
private fun getPermissionTitle(type: PermissionType): String {
    return when (type) {
        PermissionType.NOTIFICATION -> "通知权限设置"
        PermissionType.EXACT_ALARM -> "准时提醒设置"
        PermissionType.BATTERY_OPTIMIZATION -> "电池优化设置"
        PermissionType.FULL_SCREEN_INTENT -> "全屏通知设置"
        PermissionType.AUTO_START -> "自启动设置"
        PermissionType.BACKGROUND_POPUP -> "后台弹出设置"
        PermissionType.OVERLAY -> "悬浮窗设置"
    }
}

/**
 * 自动检测当前手机品牌
 */
private fun detectCurrentBrand(): PhoneBrand {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> PhoneBrand.XIAOMI
        manufacturer.contains("huawei") || manufacturer.contains("honor") -> PhoneBrand.HUAWEI
        manufacturer.contains("oppo") -> PhoneBrand.OPPO
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> PhoneBrand.VIVO
        manufacturer.contains("oneplus") -> PhoneBrand.ONEPLUS
        manufacturer.contains("realme") -> PhoneBrand.REALME
        manufacturer.contains("blackshark") || manufacturer.contains("black shark") -> PhoneBrand.BLACKSHARK
        manufacturer.contains("samsung") -> PhoneBrand.SAMSUNG
        manufacturer.contains("meizu") -> PhoneBrand.MEIZU
        else -> PhoneBrand.OTHER
    }
}

/**
 * 判断该权限是否需要手动确认
 */
private fun isManualConfirmPermission(type: PermissionType): Boolean {
    return type == PermissionType.AUTO_START || type == PermissionType.BACKGROUND_POPUP
}

/**
 * 获取手动确认步骤的 SharedPreferences key
 */
private fun getManualStepKey(type: PermissionType): String {
    return when (type) {
        PermissionType.AUTO_START -> "auto_start"
        PermissionType.BACKGROUND_POPUP -> "background_popup"
        else -> ""
    }
}

/**
 * 跳转到系统设置页面
 * 根据权限类型跳转到对应的系统设置入口
 */
private fun openPermissionSettings(context: Context, type: PermissionType) {
    try {
        when (type) {
            PermissionType.NOTIFICATION -> {
                // 跳转到应用通知设置
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }

            PermissionType.EXACT_ALARM -> {
                // 跳转到精确闹钟设置
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }

            PermissionType.BATTERY_OPTIMIZATION -> {
                // 跳转到电池优化设置
                BackgroundPermissionHelper.openBatteryOptimizationSettings(context)
            }

            PermissionType.FULL_SCREEN_INTENT -> {
                // 跳转到全屏通知权限设置
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }

            PermissionType.AUTO_START -> {
                // 跳转到自启动管理
                BackgroundPermissionHelper.openAutoStartSettings(context)
            }

            PermissionType.BACKGROUND_POPUP -> {
                // 跳转到后台弹出界面设置
                val manufacturer = Build.MANUFACTURER.lowercase()
                if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) {
                    try {
                        val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                            putExtra("extra_pkgname", context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        openAppDetailsSettings(context)
                    }
                } else {
                    openAppDetailsSettings(context)
                }
            }

            PermissionType.OVERLAY -> {
                // 跳转到悬浮窗权限设置
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        }
    } catch (e: Exception) {
        // 降级方案：打开应用详情页
        openAppDetailsSettings(context)
    }
}

/**
 * 打开应用详情页（降级方案）
 */
private fun openAppDetailsSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("PermissionTutorial", "打开应用详情页失败", e)
    }
}

/**
 * 获取权限教程数据
 * 根据权限类型和手机品牌返回对应的教程说明和步骤
 */
private fun getTutorialData(type: PermissionType, brand: PhoneBrand): TutorialData {
    return when (type) {
        PermissionType.NOTIFICATION -> getNotificationTutorial(brand)
        PermissionType.EXACT_ALARM -> getExactAlarmTutorial(brand)
        PermissionType.BATTERY_OPTIMIZATION -> getBatteryOptimizationTutorial(brand)
        PermissionType.FULL_SCREEN_INTENT -> getFullScreenIntentTutorial(brand)
        PermissionType.AUTO_START -> getAutoStartTutorial(brand)
        PermissionType.BACKGROUND_POPUP -> getBackgroundPopupTutorial(brand)
        PermissionType.OVERLAY -> getOverlayTutorial(brand)
    }
}

// ==================== 各权限的品牌教程数据 ====================

/**
 * 通知权限教程
 */
private fun getNotificationTutorial(brand: PhoneBrand): TutorialData {
    val description = "通知权限是课程提醒的基础，没有此权限将无法收到任何提醒通知。"
    val steps = when (brand) {
        PhoneBrand.XIAOMI -> listOf(
            "打开手机「设置」",
            "进入「应用设置」>「应用管理」",
            "找到「时课」应用",
            "点击「通知管理」",
            "开启「允许通知」总开关",
            "建议同时开启「锁屏通知」和「横幅通知」"
        )
        PhoneBrand.HUAWEI -> listOf(
            "打开手机「设置」",
            "进入「通知」>「应用通知管理」",
            "找到「时课」应用",
            "开启「允许通知」",
            "建议选择「横幅」显示方式"
        )
        PhoneBrand.OPPO -> listOf(
            "打开手机「设置」",
            "进入「通知与状态栏」>「通知管理」",
            "找到「时课」应用",
            "开启「允许通知」",
            "建议开启「允许横幅通知」"
        )
        PhoneBrand.VIVO -> listOf(
            "打开手机「设置」",
            "进入「通知与状态栏」>「应用通知管理」",
            "找到「时课」应用",
            "开启「允许通知」",
            "建议开启「悬浮通知」"
        )
        PhoneBrand.ONEPLUS -> listOf(
            "打开手机「设置」",
            "进入「通知与状态栏」>「通知管理」",
            "找到「时课」应用",
            "开启「允许通知」和「横幅通知」"
        )
        PhoneBrand.REALME -> listOf(
            "打开手机「设置」",
            "进入「通知与状态栏」>「通知管理」",
            "找到「时课」应用",
            "开启「允许通知」",
            "建议开启「横幅通知」"
        )
        PhoneBrand.SAMSUNG -> listOf(
            "打开手机「设置」",
            "进入「通知」",
            "找到「时课」应用",
            "开启通知开关",
            "建议设置为「简要弹出」或「详细弹出」"
        )
        PhoneBrand.MEIZU -> listOf(
            "打开手机「设置」",
            "进入「通知和状态栏」>「通知管理」",
            "找到「时课」应用",
            "开启「允许通知」和「悬浮通知」"
        )
        PhoneBrand.BLACKSHARK -> listOf(
            "打开手机「设置」",
            "进入「应用设置」>「应用管理」",
            "找到「时课」应用",
            "点击「通知管理」，开启通知"
        )
        else -> listOf(
            "打开手机「设置」",
            "进入「应用」或「应用管理」",
            "找到「时课」应用",
            "开启「通知」权限",
            "建议同时开启横幅/悬浮通知"
        )
    }
    return TutorialData(description, steps)
}

/**
 * 准时提醒权限教程
 */
private fun getExactAlarmTutorial(brand: PhoneBrand): TutorialData {
    val description = "准时提醒权限（精确闹钟权限）可以确保通知在设定的精确时间送达。" +
            "例如课程 9:00 开始，设置提前 10 分钟提醒，通知会在 8:50 准时弹出。" +
            "如果没有此权限，通知可能延迟数分钟。"
    val steps = when (brand) {
        PhoneBrand.XIAOMI -> listOf(
            "打开手机「设置」",
            "进入「应用设置」>「应用管理」",
            "找到「时课」应用",
            "点击「权限管理」>「其他权限」",
            "找到「闹钟和提醒」并开启"
        )
        PhoneBrand.HUAWEI -> listOf(
            "打开手机「设置」",
            "进入「应用」>「应用管理」",
            "找到「时课」应用",
            "点击「权限」",
            "找到「闹钟和提醒」并开启"
        )
        PhoneBrand.OPPO -> listOf(
            "打开手机「设置」",
            "进入「应用管理」>「应用列表」",
            "找到「时课」应用",
            "点击「应用权限」",
            "找到「闹钟和提醒」并开启"
        )
        PhoneBrand.VIVO -> listOf(
            "打开手机「设置」",
            "进入「应用与权限」>「应用管理」",
            "找到「时课」应用",
            "点击「权限」",
            "找到「闹钟和提醒」并开启"
        )
        else -> listOf(
            "打开手机「设置」",
            "进入「应用」或「应用管理」",
            "找到「时课」应用",
            "进入「权限」设置",
            "找到「闹钟和提醒」或「精确闹钟」并开启"
        )
    }
    return TutorialData(description, steps)
}

/**
 * 电池优化白名单教程
 */
private fun getBatteryOptimizationTutorial(brand: PhoneBrand): TutorialData {
    val description = "将应用加入电池优化白名单可以防止系统在后台自动关闭应用，" +
            "确保提醒服务持续运行。如果不加入白名单，系统可能会在省电模式下杀掉后台的提醒进程。"
    val steps = when (brand) {
        PhoneBrand.XIAOMI -> listOf(
            "打开手机「设置」",
            "进入「电池与性能」",
            "点击右上角齿轮图标",
            "找到「应用智能省电」",
            "找到「时课」，选择「无限制」"
        )
        PhoneBrand.HUAWEI -> listOf(
            "打开手机「设置」",
            "进入「电池」>「更多电池设置」",
            "进入「应用启动管理」",
            "找到「时课」，关闭「自动管理」",
            "手动开启「允许后台活动」"
        )
        PhoneBrand.OPPO -> listOf(
            "打开手机「设置」",
            "进入「电池」>「更多电池设置」",
            "进入「耗电保护」",
            "找到「时课」，选择「不过度限制」"
        )
        PhoneBrand.VIVO -> listOf(
            "打开手机「设置」",
            "进入「电池」>「后台耗电管理」",
            "找到「时课」",
            "选择「允许后台高耗电」"
        )
        PhoneBrand.ONEPLUS -> listOf(
            "打开手机「设置」",
            "进入「电池」>「电池优化」",
            "找到「时课」",
            "选择「不优化」"
        )
        PhoneBrand.REALME -> listOf(
            "打开手机「设置」",
            "进入「电池」>「更多电池设置」",
            "找到「时课」",
            "选择「不过度限制」"
        )
        PhoneBrand.SAMSUNG -> listOf(
            "打开手机「设置」",
            "进入「电池和设备维护」>「电池」",
            "点击「后台使用限制」",
            "确保「时课」不在「深度休眠应用」列表中",
            "或将其加入「不受限的应用」列表"
        )
        PhoneBrand.MEIZU -> listOf(
            "打开手机「设置」",
            "进入「电量管理」",
            "找到「时课」",
            "设置为「不限制后台」"
        )
        PhoneBrand.BLACKSHARK -> listOf(
            "打开手机「设置」",
            "进入「电池与性能」",
            "找到「时课」",
            "选择「无限制」省电策略"
        )
        else -> listOf(
            "打开手机「设置」",
            "进入「电池」或「电源管理」",
            "找到「电池优化」或「省电设置」",
            "找到「时课」应用",
            "选择「不优化」或「不限制」"
        )
    }
    return TutorialData(description, steps)
}

/**
 * 全屏通知权限教程
 */
private fun getFullScreenIntentTutorial(brand: PhoneBrand): TutorialData {
    val description = "全屏通知权限（Android 14+ 新增）允许应用在锁屏或使用其他应用时以全屏形式弹出提醒，" +
            "类似闹钟效果，确保你不会错过重要课程。"
    val steps = when (brand) {
        PhoneBrand.XIAOMI -> listOf(
            "打开手机「设置」",
            "进入「应用设置」>「应用管理」",
            "找到「时课」应用",
            "点击「权限管理」>「其他权限」",
            "找到「全屏显示」或「显示在其他应用的上层」并开启"
        )
        PhoneBrand.HUAWEI -> listOf(
            "打开手机「设置」",
            "进入「应用」>「应用管理」",
            "找到「时课」应用",
            "进入「权限」",
            "找到「全屏通知」并开启"
        )
        else -> listOf(
            "打开手机「设置」",
            "进入「应用」或「应用管理」",
            "找到「时课」应用",
            "进入权限设置",
            "找到「全屏通知」或「全屏显示」并开启"
        )
    }
    return TutorialData(description, steps)
}

/**
 * 自启动权限教程
 */
private fun getAutoStartTutorial(brand: PhoneBrand): TutorialData {
    val description = "自启动权限允许应用在手机重启后自动启动，确保提醒服务不会中断。" +
            "如果不开启此权限，每次重启手机后需要手动打开应用才能恢复提醒。"
    val steps = when (brand) {
        PhoneBrand.XIAOMI -> listOf(
            "打开手机「设置」",
            "进入「应用设置」>「应用管理」",
            "找到「时课」应用",
            "开启「自启动」开关",
            "设置完成后点击下方「我已完成此设置」按钮"
        )
        PhoneBrand.HUAWEI -> listOf(
            "打开手机「设置」",
            "进入「应用」>「应用启动管理」",
            "找到「时课」应用",
            "关闭「自动管理」，改为手动管理",
            "开启「自动启动」和「后台活动」",
            "设置完成后点击下方「我已完成此设置」按钮"
        )
        PhoneBrand.OPPO -> listOf(
            "打开手机「设置」",
            "进入「应用管理」>「自启动管理」",
            "找到「时课」应用",
            "开启自启动开关",
            "设置完成后点击下方「我已完成此设置」按钮"
        )
        PhoneBrand.VIVO -> listOf(
            "打开手机「设置」",
            "进入「应用与权限」>「权限管理」",
            "切换到「自启动」标签",
            "找到「时课」并开启",
            "设置完成后点击下方「我已完成此设置」按钮"
        )
        PhoneBrand.ONEPLUS -> listOf(
            "打开手机「设置」",
            "进入「应用管理」>「自启动管理」",
            "找到「时课」并开启",
            "设置完成后点击下方「我已完成此设置」按钮"
        )
        PhoneBrand.REALME -> listOf(
            "打开手机「设置」",
            "进入「应用管理」>「自启动管理」",
            "找到「时课」应用",
            "开启自启动开关",
            "设置完成后点击下方「我已完成此设置」按钮"
        )
        PhoneBrand.MEIZU -> listOf(
            "打开手机「设置」",
            "进入「应用管理」>「权限管理」",
            "找到「时课」应用",
            "开启「自启动」权限",
            "设置完成后点击下方「我已完成此设置」按钮"
        )
        PhoneBrand.BLACKSHARK -> listOf(
            "打开手机「设置」",
            "进入「应用设置」>「自启动管理」",
            "找到「时课」并开启",
            "设置完成后点击下方「我已完成此设置」按钮"
        )
        else -> listOf(
            "打开手机「设置」",
            "进入「应用管理」或「权限管理」",
            "找到「自启动」相关选项",
            "找到「时课」应用并开启",
            "设置完成后点击下方「我已完成此设置」按钮"
        )
    }
    return TutorialData(description, steps)
}

/**
 * 后台弹出界面教程
 */
private fun getBackgroundPopupTutorial(brand: PhoneBrand): TutorialData {
    val description = "后台弹出界面权限允许应用在你使用其他应用时弹出提醒窗口。" +
            "如果不开启此权限，提醒可能只静默显示在通知栏中，容易被忽略。"
    val steps = when (brand) {
        PhoneBrand.XIAOMI -> listOf(
            "打开手机「设置」",
            "进入「应用设置」>「应用管理」",
            "找到「时课」应用",
            "点击「权限管理」>「其他权限」",
            "找到「后台弹出界面」并选择「允许」",
            "设置完成后点击下方「我已完成此设置」按钮"
        )
        PhoneBrand.OPPO -> listOf(
            "打开手机「设置」",
            "进入「应用管理」>「应用列表」",
            "找到「时课」应用",
            "点击「应用权限」",
            "找到「后台弹出」并允许",
            "设置完成后点击下方「我已完成此设置」按钮"
        )
        else -> listOf(
            "打开手机「设置」",
            "进入「应用管理」或「权限管理」",
            "找到「时课」应用",
            "找到「后台弹出界面」或类似选项并开启",
            "设置完成后点击下方「我已完成此设置」按钮"
        )
    }
    return TutorialData(description, steps)
}

/**
 * 悬浮窗权限教程
 */
private fun getOverlayTutorial(brand: PhoneBrand): TutorialData {
    val description = "悬浮窗权限允许应用在其他应用上方显示内容，" +
            "部分手机需要此权限才能正常弹出提醒通知。"
    val steps = when (brand) {
        PhoneBrand.XIAOMI -> listOf(
            "打开手机「设置」",
            "进入「应用设置」>「应用管理」",
            "找到「时课」应用",
            "点击「权限管理」>「其他权限」",
            "找到「显示悬浮窗」并开启"
        )
        PhoneBrand.OPPO -> listOf(
            "打开手机「设置」",
            "进入「应用管理」>「应用列表」",
            "找到「时课」应用",
            "点击「应用权限」",
            "找到「悬浮窗」并允许"
        )
        PhoneBrand.VIVO -> listOf(
            "打开手机「设置」",
            "进入「应用与权限」>「权限管理」",
            "切换到「权限」标签",
            "找到「悬浮窗」",
            "找到「时课」并开启"
        )
        else -> listOf(
            "打开手机「设置」",
            "进入「应用」或「应用管理」",
            "找到「时课」应用",
            "找到「悬浮窗」或「显示在其他应用上层」",
            "开启此权限"
        )
    }
    return TutorialData(description, steps)
}
