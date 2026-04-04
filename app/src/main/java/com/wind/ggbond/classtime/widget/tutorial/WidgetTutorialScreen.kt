package com.wind.ggbond.classtime.widget.tutorial

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

data class TutorialStep(
    val title: String,
    val description: String,
    val gifResId: Int? = null,
    val gifUrl: String? = null,
    val icon: ImageVector? = null,
    val tips: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetTutorialScreen(
    onNavigateBack: () -> Unit,
    manufacturerName: String = "",
    widgetType: String = "general"
) {
    val tutorialSteps = getTutorialSteps(manufacturerName, widgetType)
    
    var currentStep by remember { mutableIntStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (manufacturerName.isNotEmpty()) {
                            "$manufacturerName 设备专用教程"
                        } else {
                            "小组件添加教程"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 进度指示器
            StepProgressIndicator(
                totalSteps = tutorialSteps.size,
                currentStep = currentStep
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 当前步骤内容
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val step = tutorialSteps[currentStep]
                    
                    // 步骤标题
                    Text(
                        text = "第 ${currentStep + 1} / ${tutorialSteps.size} 步",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // GIF 或图片展示区域
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            step.gifUrl != null -> {
                                AsyncImage(
                                    model = step.gifUrl,
                                    contentDescription = step.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                )
                            }
                            step.gifResId != null -> {
                                Image(
                                    painter = painterResource(id = step.gifResId),
                                    contentDescription = step.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                )
                            }
                            else -> {
                                // 无 GIF 时显示图标占位
                                if (step.icon != null) {
                                    Icon(
                                        imageVector = step.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(80.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 描述文字
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                    
                    // 提示列表
                    if (step.tips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        step.tips.forEach { tip ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                ) {}
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Text(
                                    text = tip,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 导航按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 上一步按钮
                TextButton(
                    onClick = {
                        if (currentStep > 0) currentStep--
                    },
                    enabled = currentStep > 0
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("上一步")
                }
                
                // 下一步/完成按钮
                Button(
                    onClick = {
                        if (currentStep < tutorialSteps.size - 1) {
                            currentStep++
                        } else {
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (currentStep < tutorialSteps.size - 1) "下一步" else "完成")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StepProgressIndicator(
    totalSteps: Int,
    currentStep: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .width(if (index == currentStep) 32.dp else 8.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (index == currentStep) {
                            MaterialTheme.colorScheme.primary
                        } else if (index < currentStep) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    )
            )
            
            if (index < totalSteps - 1) {
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

private fun getTutorialSteps(
    manufacturer: String,
    widgetType: String
): List<TutorialStep> {
    return when {
        manufacturer.contains("华为") || manufacturer.contains("荣耀") -> getHuaweiSteps(widgetType)
        manufacturer.contains("小米") || manufacturer.contains("红米") -> getXiaomiSteps(widgetType)
        manufacturer.contains("OPPO") || manufacturer.contains("OnePlus") || manufacturer.contains("Realme") -> getOppoSteps(widgetType)
        manufacturer.contains("vivo") || manufacturer.contains("iQOO") -> getVivoSteps(widgetType)
        else -> getGenericSteps(widgetType)
    }
}

// 华为/荣耀教程步骤
private fun getHuaweiSteps(widgetType: String): List<TutorialStep> {
    return listOf(
        TutorialStep(
            title = "长按桌面空白处",
            description = "在手机桌面的空白位置长按 1-2 秒，直到弹出菜单选项。",
            icon = androidx.compose.material.icons.Icons.Default.TouchApp,
            tips = listOf(
                "不要长按应用图标，那样会进入编辑模式",
                "确保手指按在没有任何图标的空白区域"
            )
        ),
        TutorialStep(
            title = "选择「小部件」或「Widgets」",
            description = "在弹出的菜单中找到并点击「小部件」或「Widgets」按钮。",
            icon = androidx.compose.material.icons.Icons.Default.Widgets,
            tips = listOf(
                "部分 EMUI 版本可能在「更多」菜单中",
                "如果找不到，尝试从屏幕顶部下滑打开通知栏"
            )
        ),
        TutorialStep(
            title = "找到「课表」小组件",
            description = "在小部件列表中向下滚动，找到「课表」应用的小组件。",
            icon = androidx.compose.material.icons.Icons.Default.Search,
            tips = listOf(
                "小组件通常按字母顺序排列",
                "可以在「课」字母分类下快速找到"
            )
        ),
        TutorialStep(
            title = "选择合适尺寸",
            description = "根据需要选择不同尺寸的课表小组件。",
            icon = androidx.compose.material.icons.Icons.Default.DashboardCustomize,
            tips = listOf(
                "建议先添加「下节课倒计时」试试效果",
                "大尺寸组件可以展示更多信息"
            )
        ),
        TutorialStep(
            title = "拖动到桌面完成添加",
            description = "长按选中的小组件不放，将其拖动到桌面的目标位置后松手即可。",
            icon = androidx.compose.material.icons.Icons.Default.AddCircle,
            tips = listOf(
                "拖动时可以调整位置到任意空位",
                "添加后可以再次长按调整大小和位置"
            )
        )
    )
}

// 小米教程步骤
private fun getXiaomiSteps(widgetType: String): List<TutorialStep> {
    return listOf(
        TutorialStep(
            title = "长按桌面空白处",
            description = "在 MIUI 桌面空白位置双指捏合或长按 1-2 秒。",
            icon = androidx.compose.material.icons.Icons.Default.TouchApp,
            tips = listOf(
                "MIUI 支持双指捏合快速进入编辑模式",
                "确保桌面未锁定"
            )
        ),
        TutorialStep(
            title = "点击底部「+」号或「小部件」",
            description = "在屏幕底部出现的工具栏中点击「+」或「小部件」图标。",
            icon = androidx.compose.material.icons.Icons.Default.AddCircle,
            tips = listOf(
                "MIUI 12+ 版本界面可能略有不同",
                "找不到时可尝试滑动底部工具栏"
            )
        ),
        TutorialStep(
            title = "搜索「课表」",
            description = "在小组件页面顶部的搜索框输入「课表」，快速定位到我们的组件。",
            icon = androidx.compose.material.icons.Icons.Default.Search,
            tips = listOf(
                "也可以手动浏览查找",
                "注意区分「课表」和其他教育类应用"
            )
        ),
        TutorialStep(
            title = "选择并放置小组件",
            description = "点击想要的小组件类型，然后在桌面选择放置位置。",
            icon = androidx.compose.material.icons.Icons.Default.DashboardCustomize,
            tips = listOf(
                "支持调整小组件大小（拖动边角）",
                "推荐使用「下节课倒计时」作为首选"
            )
        )
    )
}

// OPPO 教程步骤
private fun getOppoSteps(widgetType: String): List<TutorialStep> {
    return listOf(
        TutorialStep(title = "长按桌面空白区域", description = "在 ColorOS 桌面空白处长按 1-2 秒进入编辑模式。"),
        TutorialStep(title = "点击「小部件」入口", description = "在底部工具栏中找到并点击「小部件」按钮。"),
        TutorialStep(title = "浏览并选择课表组件", description = "在小部件列表中找到「课表」应用，查看可用的组件样式。"),
        TutorialStep(title = "拖放到桌面", description = "长按选中组件并拖动到桌面目标位置松开即可。")
    )
}

// vivo 教程步骤
private fun getVivoSteps(widgetType: String): List<TutorialStep> {
    return listOf(
        TutorialStep(title = "长按桌面空白处", description = "在 OriginOS 桌面空白位置长按进入编辑模式。"),
        TutorialStep(title = "选择「原子组件」或「小部件」", description = "OriginOS 使用「原子组件」概念，点击对应入口。"),
        TutorialStep(title = "搜索课表组件", description = "在组件库中搜索「课表」或浏览找到我们的组件。"),
        TutorialStep(title = "添加到桌面", description = "点击组件预览中的「添加」按钮或直接拖放。")
    )
}

// 通用 Android 教程步骤
private fun getGenericSteps(widgetType: String): List<TutorialStep> {
    return listOf(
        TutorialStep(
            title = "第一步：长按桌面空白处",
            description = "在手机桌面的空白位置长按 1-2 秒，等待出现菜单或进入编辑模式。\n\n这是所有 Android 手机通用的操作方式。",
            icon = androidx.compose.material.icons.Icons.Default.TouchApp,
            tips = listOf(
                "避免长按应用图标（会触发其他操作）",
                "如果无反应，检查是否启用了桌面锁定"
            )
        ),
        TutorialStep(
            title = "第二步：找到「小部件」入口",
            description = "在弹出的菜单或底部工具栏中寻找以下关键词之一：\n\n• 「小部件」\n• 「Widgets」\n• 「组件」\n• 「+」号按钮",
            icon = androidx.compose.material.icons.Icons.Default.Widgets,
            tips = listOf(
                "不同厂商使用的术语略有差异",
                "如果在桌面没找到，尝试从设置中添加"
            )
        ),
        TutorialStep(
            title = "第三步：找到课表小组件",
            description = "在小部件列表中滚动查找「课表」应用。通常会显示应用图标和名称。\n\n可用的小组件类型包括：\n• 📱 下节课倒计时（紧凑）\n• 📋 今日课程列表（中等）\n• 📊 紧凑列表视图（信息密集）\n• 📅 周视图概览（一周规划）",
            icon = androidx.compose.material.icons.Icons.Default.Apps,
            tips = listOf(
                "可以按字母顺序查找",
                "注意区分「课表」和其他类似应用"
            )
        ),
        TutorialStep(
            title = "第四步：选择并放置",
            description = "点击想要的小组件类型，系统会提示你选择放置位置。\n\n或者直接长按小组件不放，将其拖动到桌面目标位置。",
            icon = androidx.compose.material.icons.Icons.Default.AddCircle,
            tips = listOf(
                "首次添加建议选择「下节课倒计时」",
                "添加后可以随时调整位置和大小"
            )
        ),
        TutorialStep(
            title = "第五步：完成！享受便捷课表",
            description = "🎉 恭喜！你已经成功将课表小组件添加到桌面了。\n\n现在你可以：\n✨ 一眼看到下节课倒计时\n✨ 快速浏览今日所有课程\n✨ 规划一周的学习安排",
            icon = androidx.compose.material.icons.Icons.Default.CheckCircle,
            tips = listOf(
                "小组件会自动更新数据",
                "如需更换样式，删除后重新添加即可"
            )
        )
    )
}
