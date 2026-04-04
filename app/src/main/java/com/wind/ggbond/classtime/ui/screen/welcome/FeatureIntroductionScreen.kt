// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.screen.welcome

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 功能亮点介绍页面
 * 
 * 采用可滑动的卡片式设计，展示应用的4个核心功能：
 * 1. 自动更新课表 - 避免因调课旷课（重点功能）
 * 2. 临时调课管理 - 灵活调整课表（重点功能）
 * 3. 一键导入课表 - 支持110+所高校
 * 4. 本地数据隐私 - 军用级加密保护
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FeatureIntroductionScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    
    val isLastPage = pagerState.currentPage == 3
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "功能亮点",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // 功能介绍页面内容
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> FeaturePage(
                        icon = Icons.Default.Refresh,
                        iconColor = Color(0xFF4CAF50),
                        title = "智能自动更新",
                        subtitle = "告别旷课烦恼",
                        description = "每次打开应用自动检测课表变化\n实时发现调课、换教室等变动\n再也不用担心错过课程",
                        highlights = listOf(
                            "🎯 自动检测调课通知" to "后台智能监控，无需手动刷新",
                            "⚡ 快速更新" to "使用保存的登录凭证，秒级完成",
                            "📊 详细日志" to "每次更新都有完整记录可查询"
                        )
                    )
                    1 -> FeaturePage(
                        icon = Icons.Default.Edit,
                        iconColor = Color(0xFF2196F3),
                        title = "临时调课管理",
                        subtitle = "灵活掌控课表",
                        description = "轻松应对临时调课情况\n手动设置课程时间调整\n自动标记并同步到日历",
                        highlights = listOf(
                            "✏️ 一键调课" to "长按课程卡片即可设置临时调课",
                            "🔄 自动识别" to "系统检测到调课自动生成记录",
                            "📅 日历同步" to "调课信息自动更新到导出的日历"
                        )
                    )
                    2 -> FeaturePage(
                        icon = Icons.Default.School,
                        iconColor = Color(0xFFFF9800),
                        title = "一键导入课表",
                        subtitle = "全国高校通用",
                        description = "支持110+所高校教务系统\n自动识别并导入课程安排\n告别手动输入的烦恼",
                        highlights = listOf(
                            "🏫 覆盖广泛" to "正方、强智、青果等主流教务系统",
                            "🔐 安全登录" to "WebView内登录，不保存明文密码",
                            "⚙️ 智能解析" to "自动识别课表格式并完美导入"
                        )
                    )
                    3 -> FeaturePage(
                        icon = Icons.Default.Lock,
                        iconColor = Color(0xFF9C27B0),
                        title = "隐私安全保护",
                        subtitle = "数据只属于你",
                        description = "所有数据存储在本地设备\n账号密码军用级AES-256加密\n卸载即完全删除，无云端备份",
                        highlights = listOf(
                            "🛡️ 本地存储" to "不上传任何数据到外部服务器",
                            "🔒 加密保护" to "登录凭证使用AES-256-GCM加密",
                            "🗑️ 一键清除" to "随时导出备份或完全清空数据"
                        )
                    )
                }
            }
            
            // 底部指示器和按钮
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 页面指示器
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(
                                    width = if (index == pagerState.currentPage) 24.dp else 8.dp,
                                    height = 8.dp
                                )
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .animateContentSize()
                        )
                    }
                }
                
                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 跳过按钮（移至底部，方便单手操作）
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("跳过")
                    }
                    
                    // 上一步按钮（非首页显示）
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("上一步")
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    
                    // 下一步/完成按钮
                    Button(
                        onClick = {
                            if (isLastPage) {
                                onComplete()
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isLastPage) "开始使用" else "下一步")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (isLastPage) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个功能介绍页面
 */
@Composable
private fun FeaturePage(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    description: String,
    highlights: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标动画
        val infiniteTransition = rememberInfiniteTransition(label = "icon_animation")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        
        val rotation by infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rotation"
        )
        
        // 图标容器
        Surface(
            modifier = Modifier
                .size(120.dp)
                .scale(scale),
            shape = CircleShape,
            color = iconColor.copy(alpha = 0.15f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = iconColor
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // 标题
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))
        
        // 副标题
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = iconColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(16.dp))
        
        // 描述文字
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5f
        )
        
        Spacer(Modifier.height(32.dp))
        
        // 功能亮点列表
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            highlights.forEach { (emoji, detail) ->
                HighlightItem(emoji = emoji, detail = detail)
            }
        }
    }
}

/**
 * 功能亮点条目
 */
@Composable
private fun HighlightItem(
    emoji: String,
    detail: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 12.dp)
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}















