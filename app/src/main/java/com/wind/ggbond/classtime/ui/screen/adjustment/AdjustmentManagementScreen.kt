package com.wind.ggbond.classtime.ui.screen.adjustment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * 调课记录管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustmentManagementScreen(
    navController: NavController,
    viewModel: AdjustmentManagementViewModel = hiltViewModel()
) {
    val adjustmentsWithCourses by viewModel.adjustmentsWithCourses.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    
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
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("调课记录") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
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
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.EventAvailable,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "暂无调课记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "在课程详情页可设置临时调课",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // 统计信息
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Column {
                                Text(
                                    text = "共 ${adjustmentsWithCourses.size} 条调课记录",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "点击卡片可取消调课",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                
                items(adjustmentsWithCourses, key = { it.adjustment.id }) { item ->
                    AdjustmentCard(
                        adjustment = item.adjustment,
                        courseName = item.course?.courseName ?: "未知课程",
                        onCancel = { showCancelDialog = item.adjustment }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
    
    // 取消调课确认对话框
    showCancelDialog?.let { adjustment ->
        AlertDialog(
            onDismissRequest = { showCancelDialog = null },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null)
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
                TextButton(onClick = { showCancelDialog = null }) {
                    Text("保留")
                }
            }
        )
    }
}

/**
 * 调课记录卡片
 */
@Composable
fun AdjustmentCard(
    adjustment: CourseAdjustment,
    courseName: String,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 课程名称和调课图标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "🔄",
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "取消调课",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // 原始时间
            TimeInfoRow(
                label = "原定时间",
                weekNumber = adjustment.originalWeekNumber,
                dayOfWeek = adjustment.originalDayOfWeek,
                startSection = adjustment.originalStartSection,
                sectionCount = adjustment.originalSectionCount,
                isOriginal = true
            )
            
            // 箭头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // 调整后时间
            TimeInfoRow(
                label = "调整后",
                weekNumber = adjustment.newWeekNumber,
                dayOfWeek = adjustment.newDayOfWeek,
                startSection = adjustment.newStartSection,
                sectionCount = adjustment.newSectionCount,
                isOriginal = false
            )
            
            // 调课原因
            if (adjustment.reason.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "原因：${adjustment.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 创建时间
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "创建时间：${formatTimestamp(adjustment.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 时间信息行
 */
@Composable
fun TimeInfoRow(
    label: String,
    weekNumber: Int,
    dayOfWeek: Int,
    startSection: Int,
    sectionCount: Int,
    isOriginal: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isOriginal)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (isOriginal)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary
        )
        
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "第 $weekNumber 周 ${DateUtils.getDayOfWeekName(dayOfWeek)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "第${startSection}-${startSection + sectionCount - 1}节",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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


