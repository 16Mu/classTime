// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.ui.theme.Spacing
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wind.ggbond.classtime.util.PermissionGuideManager
import kotlinx.coroutines.delay

/**
 * 权限步骤引导对话框
 * 展示清晰的步骤化权限配置流程，适合不懂技术的用户
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionStepGuideDialog(
    onDismiss: () -> Unit,
    onAllCriticalCompleted: () -> Unit
) {
    val context = LocalContext.current
    var guideResult by remember { mutableStateOf(PermissionGuideManager.getAllSteps(context)) }
    var expandedStepId by remember { mutableStateOf<Int?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    // 用于触发界面刷新的计数器（每次手动确认后自增以触发重新加载）
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // 自动刷新检查权限状态（持久化状态已存储在 SharedPreferences 中）
    LaunchedEffect(refreshTrigger) {
        while (true) {
            delay(2000)  // 每2秒检查一次
            val newResult = PermissionGuideManager.getAllSteps(context)
            guideResult = newResult
            
            // 检查所有关键步骤是否完成（isCompleted 已包含 SharedPreferences 中的手动确认状态）
            if (newResult.allCriticalStepsCompleted && !isRefreshing) {
                delay(800)  // 给用户一点时间看到完成状态
                onAllCriticalCompleted()
                break
            }
        }
    }
    
    Dialog(
        onDismissRequest = { /* 不允许点击外部关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(Spacing.xl),
            elevation = CardDefaults.cardElevation(defaultElevation = Spacing.sm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // 顶部标题栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .padding(Spacing.xl)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = stringResource(R.string.desc_permission_icon),
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "开启课程提醒前的准备",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "请按照以下步骤完成权限配置",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                    }
                }
                
                // 进度条和统计
                ProgressSection(
                    guideResult = guideResult,
                    onRefresh = {
                        isRefreshing = true
                        guideResult = PermissionGuideManager.getAllSteps(context)
                        isRefreshing = false
                    }
                )
                
                // 步骤列表
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    itemsIndexed(guideResult.steps) { index, step ->
                        PermissionStepCard(
                            step = step,
                            stepNumber = index + 1,
                            isExpanded = expandedStepId == step.id,
                            isManuallyConfirmed = step.stepKey != null && step.isCompleted,
                            onExpandToggle = {
                                expandedStepId = if (expandedStepId == step.id) null else step.id
                            },
                            onOpenSettings = {
                                step.openSettings?.invoke(context)
                            },
                            onManualConfirm = {
                                // 将手动确认状态持久化到 SharedPreferences
                                step.stepKey?.let { key ->
                                    PermissionGuideManager.saveManualStepConfirmation(context, key, true)
                                }
                                // 立即刷新界面状态
                                guideResult = PermissionGuideManager.getAllSteps(context)
                                refreshTrigger++
                            }
                        )
                    }
                    
                    // 底部说明
                    item {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(Spacing.md)
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.lg),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = stringResource(R.string.desc_info),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "温馨提示",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        text = "• 所有步骤都必须完成才能开启提醒\n" +
                                                "• 部分步骤需要您手动确认已完成\n" +
                                                "• 完成所有步骤后，对话框会自动关闭",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 底部按钮
                BottomButtons(
                    allCriticalCompleted = guideResult.allCriticalStepsCompleted,
                    onComplete = onAllCriticalCompleted,
                    onCancel = onDismiss
                )
            }
        }
    }
}

@Composable
private fun ProgressSection(
    guideResult: PermissionGuideManager.GuideResult,
    onRefresh: () -> Unit
) {
    // 计算实际完成的步骤数（isCompleted 已包含 SharedPreferences 中的手动确认状态）
    val actualCompletedSteps = guideResult.completedSteps
    val actualProgress = guideResult.progress
    
    // 检查所有关键步骤是否完成
    val allCriticalCompleted = guideResult.allCriticalStepsCompleted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(Spacing.xl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "完成进度",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "$actualCompletedSteps / ${guideResult.totalSteps} 步骤已完成",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 刷新按钮
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 进度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(actualProgress)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        RoundedCornerShape(Spacing.md)
                    )
            )
        }
        
        // 状态标签
        Spacer(modifier = Modifier.height(Spacing.md))
        if (allCriticalCompleted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.desc_check),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                                Text(
                                    text = "所有必需步骤已完成，即将为您开启提醒",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = stringResource(R.string.desc_warning),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "请完成所有必需步骤",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 紧凑的已完成步骤卡片
 * 只显示必要信息，节省空间
 */
@Composable
private fun CompactCompletedStepCard(
    step: PermissionGuideManager.PermissionStep,
    isManuallyConfirmed: Boolean,
    onExpandToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandToggle() },
        shape = RoundedCornerShape(Spacing.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 完成图标（更小）
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.desc_check),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // 标题
            Text(
                text = step.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            
            // 已完成标记
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.desc_check),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "已完成",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionStepCard(
    step: PermissionGuideManager.PermissionStep,
    stepNumber: Int,
    isExpanded: Boolean,
    isManuallyConfirmed: Boolean,
    onExpandToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onManualConfirm: () -> Unit
) {
    val isActuallyCompleted = step.isCompleted || isManuallyConfirmed
    val scale by animateFloatAsState(
        targetValue = if (isExpanded) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    // 已完成的步骤使用紧凑样式
    if (isActuallyCompleted && !isExpanded) {
        CompactCompletedStepCard(
            step = step,
            isManuallyConfirmed = isManuallyConfirmed,
            onExpandToggle = onExpandToggle
        )
        return
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActuallyCompleted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isExpanded) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandToggle() }
                .padding(Spacing.lg)
        ) {
            // 头部：状态图标 + 标题 + 标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 步骤编号或完成图标
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isActuallyCompleted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isActuallyCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.desc_check),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Text(
                            text = stepNumber.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                
                // 标题和描述
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        
                        // 必需标签
                        if (step.isCritical && !isActuallyCompleted) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.error
                            ) {
                                Text(
                                    text = "必需",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
                
                // 展开/收起图标
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 展开内容
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = Spacing.lg)
                    )
                    
                    // 详细说明
                    Text(
                        text = "详细说明",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = step.detailedExplanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                    
                    // 操作提示
                    if (step.tips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "操作步骤",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        step.tips.forEachIndexed { index, tip ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = tip,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                    
                    // 操作按钮
                    if (!isActuallyCompleted) {
                        Spacer(modifier = Modifier.height(Spacing.xl))
                        
                        if (step.openSettings != null) {
                            Button(
                                onClick = onOpenSettings,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(Spacing.md),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = "前往设置",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        // 仅对需要手动确认的步骤（有 stepKey 标识）显示确认按钮
                        if (step.stepKey != null && step.isCritical) {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            OutlinedButton(
                                onClick = onManualConfirm,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(Spacing.md)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = stringResource(R.string.desc_check)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = "我已完成此步骤",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    // 完成提示
                    if (isActuallyCompleted) {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(Spacing.md)
                                )
                                .padding(Spacing.md)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.desc_check),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isManuallyConfirmed) "已手动确认完成" else "已完成此步骤",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomButtons(
    allCriticalCompleted: Boolean,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = Spacing.sm,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 取消按钮
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Spacing.md)
            ) {
                Text("暂不开启提醒")
            }
            
            // 完成按钮
            Button(
                onClick = onComplete,
                enabled = allCriticalCompleted,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (allCriticalCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                if (allCriticalCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.desc_check)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "完成并开启",
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "请先完成必需步骤",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

