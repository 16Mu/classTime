package com.wind.ggbond.classtime.ui.screen.course

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.ui.theme.CourseColors
import com.wind.ggbond.classtime.util.DateUtils
import com.wind.ggbond.classtime.ui.screen.course.CourseInfoListViewModel
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import java.text.SimpleDateFormat
import java.util.*

/**
 * 时间格式化辅助函数
 */
fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * 课程信息列表页面
 * 显示当前课程表中的所有课程，支持查看详情和编辑
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseInfoListScreen(
    navController: NavController,
    viewModel: CourseInfoListViewModel = hiltViewModel()
) {
    // 获取聚合课程数据
    val aggregatedCourses by viewModel.aggregatedCourses.collectAsState()
    val currentSchedule by viewModel.currentSchedule.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val expandedCourseNames by viewModel.expandedCourseNames.collectAsState()
    
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    
    // BottomSheet 课程详情/编辑状态
    var selectedCourseId by remember { mutableStateOf<Long?>(null) }
    
    // 下拉刷新状态
    var isRefreshing by remember { mutableStateOf(false) }
    
    // 错误状态检测
    val hasError = remember(currentSchedule) {
        !isLoading && currentSchedule == null
    }
    
    // 空状态检测
    val isEmpty = remember(aggregatedCourses) {
        !isLoading && aggregatedCourses.isEmpty()
    }
    
    // 刷新函数
    suspend fun performRefresh() {
        isRefreshing = true
        try {
            viewModel.refresh()
        } finally {
            isRefreshing = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { 
                    Text(
                        text = "课程信息",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 统计信息
                    if (!isLoading && aggregatedCourses.isNotEmpty()) {
                        val totalSlots = aggregatedCourses.sumOf { it.timeSlots.size }
                        Text(
                            text = "${aggregatedCourses.size}门课程 • ${totalSlots}个时段",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            // 加载状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在加载课程信息...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (hasError) {
            // 错误状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "加载失败",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "无法加载课程表信息，请检查网络连接或重试",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.refresh()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("重试")
                    }
                }
            }
        } else if (isEmpty) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无课程",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "您还没有导入任何课程，请先导入课表或手动添加课程",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigate(com.wind.ggbond.classtime.ui.navigation.Screen.ImportSchedule.route)
                        }
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("导入课表")
                    }
                }
            }
        } else {
            // 课程列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 显示当前课程表信息
                if (currentSchedule != null) {
                    item {
                        CourseTableInfoCard(
                            schedule = currentSchedule
                        )
                    }
                }
                
                // 课程列表
                items(
                    items = aggregatedCourses,
                    key = { it.courseName }
                ) { aggregatedCourse ->
                    AggregatedCourseCard(
                        aggregatedCourse = aggregatedCourse,
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            // 点击打开 BottomSheet 查看/编辑课程详情
                            val firstSlotId = aggregatedCourse.timeSlots.firstOrNull()?.id
                            if (firstSlotId != null) {
                                selectedCourseId = firstSlotId
                            }
                        },
                        onToggleExpanded = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleCourseExpanded(aggregatedCourse.courseName)
                        },
                        haptic = haptic,
                        onTimeSlotClick = { courseId -> selectedCourseId = courseId }
                    )
                }
            }
        }
    }
    
    // 课程详情 BottomSheet
    selectedCourseId?.let { courseId ->
        com.wind.ggbond.classtime.ui.components.CourseDetailBottomSheet(
            courseId = courseId,
            onDismiss = {
                selectedCourseId = null
                // 刷新课程列表数据
                viewModel.refresh()
            },
            onDelete = { _ ->
                selectedCourseId = null
                viewModel.refresh()
            }
        )
    }
}

/**
 * 课程表信息卡片
 */
@Composable
private fun CourseTableInfoCard(
    schedule: com.wind.ggbond.classtime.data.local.entity.Schedule?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "当前课程表",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            schedule?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "课表名称",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = it.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "创建时间",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = formatDateTime(it.createdAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            schedule?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "开始日期",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = it.startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "周数",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${it.totalWeeks}周",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * 聚合课程卡片
 * 采用批量导入的展开式设计，显示课程基本信息和所有时间段
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AggregatedCourseCard(
    aggregatedCourse: AggregatedCourse,
    onClick: () -> Unit,
    onToggleExpanded: () -> Unit,
    haptic: HapticFeedback,
    onTimeSlotClick: (Long) -> Unit
) {
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    
    // 展开/折叠箭头旋转动画
    val arrowRotation by animateFloatAsState(
        targetValue = if (aggregatedCourse.isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "arrow_rotation"
    )
    
    // 获取课程颜色
    val courseColor = try {
        Color(android.graphics.Color.parseColor(aggregatedCourse.color))
    } catch (_: Exception) {
        CourseColors[kotlin.math.abs(aggregatedCourse.courseName.hashCode()) % CourseColors.size]
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        colors = CardDefaults.cardColors(
            containerColor = courseColor.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 课程基本信息行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 课程颜色标识
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = courseColor
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 课程信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = aggregatedCourse.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (aggregatedCourse.teacher.isNotBlank()) {
                            Text(
                                text = aggregatedCourse.teacher,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        
                        if (aggregatedCourse.credit > 0) {
                            Text(
                                text = " • ${aggregatedCourse.credit}学分",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Text(
                        text = "${aggregatedCourse.timeSlots.size} 个时间段",
                        style = MaterialTheme.typography.bodySmall,
                        color = courseColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // 展开/折叠图标
                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (aggregatedCourse.isExpanded) "折叠" else "展开",
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                rotationZ = arrowRotation
                            },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 可展开的时间段详情
            AnimatedVisibility(
                visible = aggregatedCourse.isExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(
                    animationSpec = tween(300, easing = LinearOutSlowInEasing)
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut(
                    animationSpec = tween(200, easing = LinearOutSlowInEasing)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    // 时间段列表
                    aggregatedCourse.timeSlots.forEachIndexed { index, timeSlot ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                        
                        TimeSlotItem(
                            timeSlot = timeSlot,
                            dayNames = dayNames,
                            courseColor = courseColor,
                            haptic = haptic,
                            onTimeSlotClick = onTimeSlotClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个时间段项
 */
@Composable
private fun TimeSlotItem(
    timeSlot: CourseTimeSlot,
    dayNames: List<String>,
    courseColor: Color,
    haptic: HapticFeedback,
    onTimeSlotClick: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                // 点击打开 BottomSheet 查看/编辑该时间段
                onTimeSlotClick(timeSlot.id)
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 时间信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${dayNames.getOrNull(timeSlot.dayOfWeek - 1) ?: "周${timeSlot.dayOfWeek}"} " +
                        "第${timeSlot.startSection}-${timeSlot.startSection + timeSlot.sectionCount - 1}节",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (timeSlot.classroom.isNotBlank()) {
                Text(
                    text = "教室: ${timeSlot.classroom}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "周次: ${timeSlot.weeks.size}周",
                style = MaterialTheme.typography.bodySmall,
                color = courseColor
            )
        }
        
        // 编辑图标
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "编辑时间段",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
