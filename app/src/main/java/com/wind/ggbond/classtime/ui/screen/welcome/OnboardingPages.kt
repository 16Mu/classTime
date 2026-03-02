package com.wind.ggbond.classtime.ui.screen.welcome

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ==================== 第1页：欢迎 ====================

/**
 * 欢迎页 - 大标题 + 简洁品牌介绍
 * 视觉焦点：App名称和一句话介绍，底部三个核心标签
 */
@Composable
fun WelcomePage(
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color
) {
    // 入场动画控制
    var animStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150)
        animStarted = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo圆形容器（弹性缩放动画）
        val logoScale by animateFloatAsState(
            targetValue = if (animStarted) 1f else 0.5f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "logoScale"
        )
        val logoAlpha by animateFloatAsState(
            targetValue = if (animStarted) 1f else 0f,
            animationSpec = tween(600),
            label = "logoAlpha"
        )

        Surface(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    alpha = logoAlpha
                },
            shape = CircleShape,
            color = primaryColor.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = primaryColor
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // 主标题（从下方滑入）
        AnimatedVisibility(
            visible = animStarted,
            enter = fadeIn(tween(700)) + slideInVertically(
                tween(700, easing = FastOutSlowInEasing),
                initialOffsetY = { 60 }
            )
        ) {
            Text(
                text = "时课",
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                letterSpacing = 2.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        // 副标题（延迟滑入）
        AnimatedVisibility(
            visible = animStarted,
            enter = fadeIn(tween(700, delayMillis = 200)) + slideInVertically(
                tween(700, delayMillis = 200, easing = FastOutSlowInEasing),
                initialOffsetY = { 40 }
            )
        ) {
            Text(
                text = "你的智能课表助手",
                fontSize = 17.sp,
                color = textColor.copy(alpha = 0.55f),
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(48.dp))

        // 三个核心标签（横排淡入）
        AnimatedVisibility(
            visible = animStarted,
            enter = fadeIn(tween(600, delayMillis = 500))
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingTag(Icons.Outlined.Lock, "本地存储", surfaceColor, textColor)
                OnboardingTag(Icons.Outlined.School, "120+高校", surfaceColor, textColor)
                OnboardingTag(Icons.Outlined.Notifications, "课前提醒", surfaceColor, textColor)
            }
        }
    }
}

/**
 * 小标签组件（圆角药丸形状）
 */
@Composable
private fun OnboardingTag(
    icon: ImageVector,
    text: String,
    surfaceColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = surfaceColor.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(icon, null, Modifier.size(13.dp), tint = textColor.copy(alpha = 0.6f))
            Text(
                text = text,
                fontSize = 11.sp,
                color = textColor.copy(alpha = 0.6f),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

// ==================== 第2页：核心亮点 ====================

/**
 * 核心亮点页 - 3个大特性卡片，纵向排列
 * 每个特性用图标+标题+描述，依次从下方滑入
 */
@Composable
fun HighlightsPage(
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color
) {
    var animStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150)
        animStarted = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 页面标题
        AnimatedVisibility(
            visible = animStarted,
            enter = fadeIn(tween(600)) + slideInVertically(
                tween(600, easing = FastOutSlowInEasing),
                initialOffsetY = { -30 }
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "为你而设计",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "简单高效的课表管理",
                    fontSize = 14.sp,
                    color = textColor.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        // 三个特性卡片（依次滑入）
        val features = listOf(
            Triple(Icons.Outlined.CloudDownload, "一键导入", "从教务系统自动获取课表，支持120+所高校"),
            Triple(Icons.Outlined.Sync, "自动更新", "每次打开自动检查课表变化，告别旷课"),
            Triple(Icons.Outlined.EditCalendar, "灵活调课", "临时调课一键记录，自动同步到课表")
        )

        features.forEachIndexed { index, (icon, title, desc) ->
            AnimatedVisibility(
                visible = animStarted,
                enter = fadeIn(
                    tween(500, delayMillis = 300 + index * 180)
                ) + slideInVertically(
                    tween(500, delayMillis = 300 + index * 180, easing = FastOutSlowInEasing),
                    initialOffsetY = { 60 }
                )
            ) {
                FeatureCard(
                    icon = icon,
                    title = title,
                    description = desc,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    surfaceColor = surfaceColor
                )
            }
            if (index < features.size - 1) {
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

/**
 * 特性卡片组件（图标在左，文字在右）
 */
@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor.copy(alpha = 0.35f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标容器
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = primaryColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = primaryColor
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // 右侧文字
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = textColor.copy(alpha = 0.6f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ==================== 第3页：开始使用 + 免责声明 ====================

/**
 * 开始使用页 - 简洁的操作引导 + 底部免责声明CheckBox
 * 免责声明不再独占整页，改为内联在底部
 */
@Composable
fun GetStartedPage(
    primaryColor: Color,
    textColor: Color,
    surfaceColor: Color,
    disclaimerChecked: Boolean,
    onDisclaimerCheckedChange: (Boolean) -> Unit,
    context: Context
) {
    var animStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150)
        animStarted = true
    }

    // 读取免责声明摘要（截取前200字）
    val disclaimerSummary = remember {
        try {
            val full = context.assets.open("disclaimer.txt").bufferedReader().use { it.readText() }
            if (full.length > 120) full.take(120) + "..." else full
        } catch (_: Exception) {
            "本应用为个人学习项目，仅供参考，不保证课表数据准确性。"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 上半部分：标题 + 步骤列表，垂直居中分布
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 页面标题
            AnimatedVisibility(
                visible = animStarted,
                enter = fadeIn(tween(600)) + slideInVertically(
                    tween(600, easing = FastOutSlowInEasing),
                    initialOffsetY = { -30 }
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "准备好了吗",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "几步即可开始使用",
                        fontSize = 14.sp,
                        color = textColor.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 步骤列表
            val steps = listOf(
                Triple(Icons.Outlined.School, "选择你的学校", "支持120+所高校的教务系统"),
                Triple(Icons.Outlined.CloudDownload, "导入课表", "登录教务系统后自动获取课表数据"),
                Triple(Icons.Outlined.Notifications, "开启提醒", "课前自动提醒，不再错过课程")
            )

            steps.forEachIndexed { index, (icon, title, desc) ->
                AnimatedVisibility(
                    visible = animStarted,
                    enter = fadeIn(
                        tween(500, delayMillis = 200 + index * 150)
                    ) + slideInVertically(
                        tween(500, delayMillis = 200 + index * 150, easing = FastOutSlowInEasing),
                        initialOffsetY = { 50 }
                    )
                ) {
                    StepItem(
                        stepNumber = index + 1,
                        icon = icon,
                        title = title,
                        description = desc,
                        primaryColor = primaryColor,
                        textColor = textColor
                    )
                }
                if (index < steps.size - 1) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // 下半部分：免责声明（紧贴底部，不参与居中）
        AnimatedVisibility(
            visible = animStarted,
            enter = fadeIn(tween(600, delayMillis = 700))
        ) {
            Column {
                // 免责声明摘要
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = surfaceColor.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = disclaimerSummary,
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.4f),
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // CheckBox + 文案（紧凑行）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Checkbox(
                        checked = disclaimerChecked,
                        onCheckedChange = onDisclaimerCheckedChange
                    )
                    Text(
                        text = "我已阅读并同意使用条款",
                        fontSize = 13.sp,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * 步骤项组件（序号圆圈 + 图标 + 文字）
 */
@Composable
private fun StepItem(
    stepNumber: Int,
    icon: ImageVector,
    title: String,
    description: String,
    primaryColor: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 序号圆圈
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = primaryColor.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$stepNumber",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // 文字区域
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = textColor.copy(alpha = 0.5f),
                lineHeight = 17.sp
            )
        }

        // 右侧图标
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = primaryColor.copy(alpha = 0.5f)
        )
    }
}
