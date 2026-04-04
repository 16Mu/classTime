// [Monet] 已排查：该文件使用 courseColor (String?) 直接解析颜色用于调课记录卡片图标容器显示。
// 说明：此为"读取已有课程颜色并渲染"场景，courseColor 来自 Course 实体的 color 字段（数据库存储的十六进制颜色值），
// 属于课程颜色的消费端，非生成端。如需支持 Monet 动态配色，可考虑在 AdjustmentListItem 组件中
// 接收动态颜色参数，或使用 CourseColorProvider.getColorForCourse() 根据课程名重新获取。
package com.wind.ggbond.classtime.ui.screen.adjustment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * 调课记录管理页面
 * 
 * 采用与软件整体风格一致的Material Design 3设计
 * - 清晰的时间线布局展示调课前后变化
 * - 统一的卡片和间距风格
 * - 支持展开/收起查看详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustmentManagementScreen(
    navController: NavController,
    viewModel: AdjustmentManagementViewModel = hiltViewModel()
) {
    // 收集调课记录数据
    val adjustmentsWithCourses by viewModel.adjustmentsWithCourses.collectAsState()
    // 收集操作状态
    val operationState by viewModel.operationState.collectAsState()
    // 触觉反馈
    val haptic = LocalHapticFeedback.current
    // Snackbar状态
    val snackbarHostState = remember { SnackbarHostState() }
    // 取消调课确认对话框状态
    var showCancelDialog by remember { mutableStateOf<CourseAdjustment?>(null) }
    
    // 处理操作状态（使用Snackbar替代Toast）
    LaunchedEffect(operationState) {
        when (val state = operationState) {
            is AdjustmentManagementViewModel.OperationState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetOperationState()
            }
            is AdjustmentManagementViewModel.OperationState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetOperationState()
            }
            else -> {}
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { 
                    Column {
                        Text("调课记录")
                        // 显示记录数量
                        if (adjustmentsWithCourses.isNotEmpty()) {
                            Text(
                                text = "共 ${adjustmentsWithCourses.size} 条记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigateUp() 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        if (adjustmentsWithCourses.isEmpty()) {
            // 空状态 - 采用与其他页面一致的空状态设计
            AdjustmentEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // 调课记录列表
                items(adjustmentsWithCourses, key = { it.adjustment.id }) { item ->
                    AdjustmentListItem(
                        adjustment = item.adjustment,
                        courseName = item.course?.courseName ?: "未知课程",
                        courseColor = item.course?.color,
                        onCancelClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showCancelDialog = item.adjustment 
                        }
                    )
                }
                
                // 底部提示
                item {
                    AdjustmentFooterTip()
                }
            }
        }
    }
    
    // 取消调课确认对话框
    showCancelDialog?.let { adjustment ->
        AlertDialog(
            onDismissRequest = { showCancelDialog = null },
            icon = {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            title = {
                Text("取消调课")
            },
            text = {
                Text("确定要取消这次调课吗？课程将恢复到原定时间。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.cancelAdjustment(adjustment)
                        showCancelDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("取消调课")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showCancelDialog = null 
                }) {
                    Text("保留")
                }
            }
        )
    }
}

/**
 * 空状态组件
 */
@Composable
private fun AdjustmentEmptyState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // 图标容器 - 与ProfileScreen风格一致
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            
            Text(
                text = "暂无调课记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "在课程详情页可设置临时调课",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 调课记录列表项 - 采用与ProfileScreen一致的列表项风格
 */
@Composable
private fun AdjustmentListItem(
    adjustment: CourseAdjustment,
    courseName: String,
    courseColor: String?,
    onCancelClick: () -> Unit
) {
    // 展开状态
    var isExpanded by remember { mutableStateOf(false) }
    // 展开图标旋转动画
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "expandRotation"
    )
    // 触觉反馈
    val haptic = LocalHapticFeedback.current
    // 课程颜色 - 从16进制字符串转换为Color
    val parsedColor = remember(courseColor) {
        runCatching {
            courseColor?.let { android.graphics.Color.parseColor(it) }
        }.getOrNull()
    }
    val cardColor = parsedColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                isExpanded = !isExpanded
            },
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 主要信息行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧图标容器 - 使用课程颜色
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = cardColor.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = cardColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 中间内容
                Column(modifier = Modifier.weight(1f)) {
                    // 课程名称
                    Text(
                        text = courseName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    // 简要时间变化信息
                    Text(
                        text = formatBriefChange(adjustment),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 展开/收起图标
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            
            // 展开的详细信息
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    // 详细时间变化卡片
                    AdjustmentDetailCard(
                        adjustment = adjustment,
                        cardColor = cardColor
                    )
                    
                    // 调课原因（如果有）
                    if (adjustment.reason.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 56.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Notes,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = adjustment.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 操作按钮
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 取消调课按钮
                        OutlinedButton(
                            onClick = onCancelClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                )
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "取消调课",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    
                    // 创建时间
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "创建于 ${formatTimestamp(adjustment.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 56.dp)
                    )
                }
            }
        }
    }
    
    // 分隔线
    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp, end = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/**
 * 详细时间变化卡片 - 使用时间线设计
 */
@Composable
private fun AdjustmentDetailCard(
    adjustment: CourseAdjustment,
    cardColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 原始时间
            TimelineItem(
                label = "原时间",
                weekNumber = adjustment.originalWeekNumber,
                dayOfWeek = adjustment.originalDayOfWeek,
                startSection = adjustment.originalStartSection,
                sectionCount = adjustment.originalSectionCount,
                isStart = true,
                lineColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            )
            
            // 新时间
            TimelineItem(
                label = "新时间",
                weekNumber = adjustment.newWeekNumber,
                dayOfWeek = adjustment.newDayOfWeek,
                startSection = adjustment.newStartSection,
                sectionCount = adjustment.newSectionCount,
                isStart = false,
                lineColor = cardColor
            )
        }
    }
}

/**
 * 时间线项目
 */
@Composable
private fun TimelineItem(
    label: String,
    weekNumber: Int,
    dayOfWeek: Int,
    startSection: Int,
    sectionCount: Int,
    isStart: Boolean,
    lineColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // 时间线指示器
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            // 连接线（上方）
            if (!isStart) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            
            // 圆点
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(lineColor)
            )
            
            // 连接线（下方）
            if (isStart) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 时间信息
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isStart) 8.dp else 0.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = lineColor,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "第${weekNumber}周 ${DateUtils.getDayOfWeekName(dayOfWeek)} 第${startSection}-${startSection + sectionCount - 1}节",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 底部提示
 */
@Composable
private fun AdjustmentFooterTip() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "点击记录可展开查看详情，取消调课后课程将恢复原时间",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 格式化简要变化信息
 */
private fun formatBriefChange(adjustment: CourseAdjustment): String {
    val originalDay = DateUtils.getDayOfWeekName(adjustment.originalDayOfWeek)
    val newDay = DateUtils.getDayOfWeekName(adjustment.newDayOfWeek)
    
    return if (adjustment.originalWeekNumber == adjustment.newWeekNumber) {
        // 同周调课
        "第${adjustment.originalWeekNumber}周 $originalDay → $newDay"
    } else {
        // 跨周调课
        "第${adjustment.originalWeekNumber}周 → 第${adjustment.newWeekNumber}周"
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * 格式化调课变更（用于通知和摘要）
 * 
 * 格式说明：
 * - 同周：显示节次变化 "xx课程 第3-4节 → 第7-8节"
 * - 跨周：显示周次变化 "xx课程 第5周 → 第8周"
 */
fun formatAdjustmentChange(
    courseName: String,
    originalWeek: Int,
    newWeek: Int,
    originalStart: Int,
    originalCount: Int,
    newStart: Int,
    newCount: Int
): String {
    return if (originalWeek == newWeek) {
        // 同周：显示节次变化
        val originalEnd = originalStart + originalCount - 1
        val newEnd = newStart + newCount - 1
        "$courseName: 第${originalStart}-${originalEnd}节 → 第${newStart}-${newEnd}节"
    } else {
        // 跨周：显示周次变化
        "$courseName: 第${originalWeek}周 → 第${newWeek}周"
    }
}


