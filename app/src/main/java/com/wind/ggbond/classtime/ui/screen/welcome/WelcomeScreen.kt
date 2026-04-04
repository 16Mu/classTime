// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.screen.welcome

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wind.ggbond.classtime.R
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 优化后的欢迎/免责声明界面
 * 特点：
 * 1. 保留完整内容（不缩短）
 * 2. 分步骤展示，降低认知负担
 * 3. 智能倒计时代替强制滚动
 * 4. 视觉友好，动画流畅
 */
@Composable
fun WelcomeDisclaimerDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 3
    
    Dialog(
        onDismissRequest = { /* 不允许点击外部关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp
        ) {
            Column(
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
                // 顶部进度指示
                StepProgressIndicator(
                    currentStep = currentStep,
                    totalSteps = totalSteps,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 内容区域
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(300, easing = EaseInOut)
                            ) + fadeIn() togetherWith 
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(300, easing = EaseInOut)
                            ) + fadeOut()
                        },
                        label = "step_transition"
                    ) { step ->
                        when (step) {
                            0 -> WelcomeStep()
                            1 -> KeyPointsStep()
                            2 -> FullDisclaimerStep(
                                onAccept = onAccept,
                                onDecline = onDecline
                            )
                        }
                    }
                }
                
                // 底部导航按钮
                if (currentStep < 2) {
                    StepNavigationButtons(
                        currentStep = currentStep,
                        onNext = { currentStep++ },
                        onSkipToAgreement = { currentStep = 2 }
                    )
                }
            }
        }
    }
}

/**
 * 步骤1: 欢迎页面
 */
@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 应用图标动画
        val infiniteTransition = rememberInfiniteTransition(label = "icon_pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp * scale)
                .padding(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "欢迎使用时课",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Wind Class Schedule",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        InfoCard(
            icon = Icons.Default.Person,
            title = "学生独立开发",
            description = "个人学习项目，非商业应用"
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        InfoCard(
            icon = Icons.Default.Lock,
            title = "本地数据存储",
            description = "所有数据仅保存在您的设备本地"
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        InfoCard(
            icon = Icons.Default.Warning,
            title = "使用前必读",
            description = "请花1分钟了解重要条款"
        )
    }
}

/**
 * 步骤2: 关键要点
 */
@Composable
private fun KeyPointsStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "重要提示",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "在使用本应用前，请了解以下关键信息：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        KeyPointCard(
            icon = Icons.Default.Person,
            title = "开发者身份",
            content = "本应用为个人技术学习项目，由在校学生独立开发，基于Kotlin与Jetpack Compose构建，仅供学习交流使用，不具备商业运营资质",
            color = MaterialTheme.colorScheme.primaryContainer
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        KeyPointCard(
            icon = Icons.Default.Block,
            title = "禁止行为",
            content = "严禁用于商业用途、违法违规行为、攻击教务系统、批量抓取数据或逆向工程。禁止转售、分发或用于任何盈利活动。",
            color = MaterialTheme.colorScheme.errorContainer
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        KeyPointCard(
            icon = Icons.Default.School,
            title = "非官方声明",
            content = "本应用与任何学校、教育机构、教务系统供应商无任何隶属、合作、授权或背书关系。不支持多账号，仅管理个人课表。",
            color = MaterialTheme.colorScheme.tertiaryContainer
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        KeyPointCard(
            icon = Icons.Default.Lock,
            title = "隐私保护",
            content = "所有数据仅本地存储，不上传任何服务器。账号密码和Cookie使用AES-256-GCM军用级加密。Cookie默认30天有效期。",
            color = MaterialTheme.colorScheme.secondaryContainer
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        KeyPointCard(
            icon = Icons.Default.Warning,
            title = "责任限制",
            content = "应用按\"现状\"提供，不提供任何保证。开发者不对数据丢失、提醒失效、教务系统问题等承担责任。提醒功能不保证100%准时送达。",
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "下一步将展示完整免责声明，请仔细阅读后同意方可使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 步骤3: 完整免责声明（带智能倒计时）
 */
@Composable
private fun FullDisclaimerStep(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var hasScrolledToBottom by remember { mutableStateOf(false) }
    var readingTimeElapsed by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(5) }
    
    // 检测是否滚动到底部
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        hasScrolledToBottom = scrollState.value >= scrollState.maxValue - 50 || scrollState.maxValue <= 10
    }
    
    // 阅读时间倒计时（10秒）
    LaunchedEffect(Unit) {
        while (countdownSeconds > 0) {
            delay(1000)
            countdownSeconds--
        }
        readingTimeElapsed = true
    }
    
    val canAgree = hasScrolledToBottom && readingTimeElapsed
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部提示栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (canAgree) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (canAgree) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (canAgree) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (canAgree) "可以同意了" else "请仔细阅读",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    AnimatedContent(
                        targetState = Triple(hasScrolledToBottom, readingTimeElapsed, countdownSeconds),
                        label = "hint_text"
                    ) { (scrolled, timeElapsed, countdown) ->
                        Text(
                            text = when {
                                !scrolled -> "向下滚动查看全部内容"
                                !timeElapsed -> "请再阅读 $countdown 秒"
                                else -> "您已完整阅读，可以做出选择"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 免责声明内容 - 从assets文件读取以避免STRING_TOO_LARGE错误
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            // 先获取字符串资源的回退值（在@Composable上下文中）
            val fallbackText = stringResource(id = R.string.disclaimer_content)
            
            val disclaimerText = remember {
                try {
                    context.assets.open("disclaimer.txt").use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                            reader.readText()
                        }
                    }
                } catch (e: Exception) {
                    // 如果读取失败，回退到字符串资源
                    android.util.Log.e("WelcomeScreen", "Failed to read disclaimer.txt", e)
                    fallbackText
                }
            }
            Text(
                text = disclaimerText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.6f
            )
        }
        
        // 底部按钮区
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 进度指示
                if (!canAgree) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (!hasScrolledToBottom) "1/2 滚动阅读" else "2/2 等待计时",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (hasScrolledToBottom) "$countdownSeconds 秒" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { 
                                when {
                                    !hasScrolledToBottom -> 0.5f
                                    else -> 0.5f + (10 - countdownSeconds) / 20f
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // 退出应用
                            onDecline()
                            (context as? Activity)?.finishAffinity()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("不同意并退出")
                    }
                    
                    Button(
                        onClick = onAccept,
                        enabled = canAgree,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("我已阅读并同意")
                    }
                }
            }
        }
    }
}

/**
 * 进度指示器
 */
@Composable
private fun StepProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(totalSteps) { index ->
                StepDot(
                    isActive = index <= currentStep,
                    isCurrent = index == currentStep,
                    modifier = Modifier.weight(1f)
                )
                if (index < totalSteps - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (currentStep) {
                0 -> "欢迎"
                1 -> "关键要点"
                2 -> "完整声明"
                else -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StepDot(
    isActive: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(300),
        label = "progress"
    )
    
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = if (isCurrent) 
            MaterialTheme.colorScheme.primary 
        else if (isActive)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surfaceVariant,
    )
}

/**
 * 信息卡片
 */
@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    description: String
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 关键要点卡片
 */
@Composable
private fun KeyPointCard(
    icon: ImageVector,
    title: String,
    content: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = color
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5f
                )
            }
        }
    }
}

/**
 * 导航按钮
 */
@Composable
private fun StepNavigationButtons(
    currentStep: Int,
    onNext: () -> Unit,
    onSkipToAgreement: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (currentStep == 1) {
                TextButton(
                    onClick = onSkipToAgreement,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("快速同意")
                }
            }
            
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (currentStep == 0) "继续" else "下一步")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

