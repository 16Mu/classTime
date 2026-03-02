package com.wind.ggbond.classtime.ui.screen.course

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.ui.theme.CourseColors
import com.wind.ggbond.classtime.ui.navigation.Screen
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
                        onTimeSlotClick = { courseId -> selectedCourseId = courseId },
                        onAddTimeSlot = {
                            // 跳转到课程编辑页面，预填充课程名称以添加新时间段
                            navController.navigate(
                                Screen.CourseEdit.createRoute(
                                    courseName = aggregatedCourse.courseName
                                )
                            )
                        }
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
 * 采用与整体UI一致的卡片样式
 */
@Composable
private fun CourseTableInfoCard(
    schedule: com.wind.ggbond.classtime.data.local.entity.Schedule?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 图标容器
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "当前课程表",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            schedule?.let {
                // 课表信息网格
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 课表名称
                    InfoChip(
                        icon = Icons.Default.Label,
                        label = "名称",
                        value = it.name,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 总周数
                    InfoChip(
                        icon = Icons.Default.DateRange,
                        label = "周数",
                        value = "${it.totalWeeks}周",
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 开始日期
                    InfoChip(
                        icon = Icons.Default.Event,
                        label = "开始",
                        value = it.startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 创建时间
                    InfoChip(
                        icon = Icons.Default.AccessTime,
                        label = "创建",
                        value = formatDateTime(it.createdAt),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 聚合课程卡片
 * 采用与BatchCourseCreateScreen一致的展开式设计，支持查看、编辑和新增时间段
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AggregatedCourseCard(
    aggregatedCourse: AggregatedCourse,
    onClick: () -> Unit,
    onToggleExpanded: () -> Unit,
    haptic: HapticFeedback,
    onTimeSlotClick: (Long) -> Unit,
    onAddTimeSlot: () -> Unit
) {
    // 星期名称映射
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    
    // 按压状态，用于触摸缩放交互
    var isPressed by remember { mutableStateOf(false) }
    
    // 按压缩放动画（弹簧物理）
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardPressScale"
    )
    
    // 展开/折叠箭头旋转动画（低刚度弹簧，缓慢优雅旋转）
    val arrowRotation by animateFloatAsState(
        targetValue = if (aggregatedCourse.isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 120f
        ),
        label = "arrowRotation"
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
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 课程标题栏（可点击展开/折叠，带按压缩放反馈）
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            },
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleExpanded()
                            }
                        )
                    },
                color = courseColor.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 课程颜色标识圆形
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = courseColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Book,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
                        
                        // 教师和学分信息
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            if (aggregatedCourse.teacher.isNotBlank()) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(2.dp))
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
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${aggregatedCourse.credit}学分",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // 时间段数量
                        Text(
                            text = "${aggregatedCourse.timeSlots.size} 个时间段",
                            style = MaterialTheme.typography.bodySmall,
                            color = courseColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // 展开/折叠图标（弹簧旋转动画）
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
            
            // 可折叠的详细内容（低刚度弹簧，PPT级平滑展开/收缩）
            AnimatedVisibility(
                visible = aggregatedCourse.isExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = 80f
                    )
                ) + fadeIn(
                    animationSpec = spring(
                        dampingRatio = 1f,
                        stiffness = 100f
                    )
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = 1f,
                        stiffness = 100f
                    )
                ) + fadeOut(
                    animationSpec = spring(
                        dampingRatio = 1f,
                        stiffness = 120f
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 时间安排标题
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "时间安排",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // 时间段列表
                    aggregatedCourse.timeSlots.forEachIndexed { index, timeSlot ->
                        TimeSlotCard(
                            timeSlot = timeSlot,
                            dayNames = dayNames,
                            courseColor = courseColor,
                            haptic = haptic,
                            onTimeSlotClick = onTimeSlotClick
                        )
                    }
                    
                    // 添加时间段按钮
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAddTimeSlot()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = courseColor
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(courseColor.copy(alpha = 0.5f))
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("添加时间段")
                    }
                }
            }
        }
    }
}

/**
 * 单个时间段卡片
 * 采用与BatchCourseCreateScreen一致的卡片样式
 */
@Composable
private fun TimeSlotCard(
    timeSlot: CourseTimeSlot,
    dayNames: List<String>,
    courseColor: Color,
    haptic: HapticFeedback,
    onTimeSlotClick: (Long) -> Unit
) {
    // 格式化周次显示
    val weeksText = if (timeSlot.weeks.isEmpty()) {
        "未设置周次"
    } else {
        formatWeeksDisplay(timeSlot.weeks)
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTimeSlotClick(timeSlot.id)
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧颜色指示条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(courseColor)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 时间信息
            Column(modifier = Modifier.weight(1f)) {
                // 星期和节次
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 星期标签
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = courseColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = dayNames.getOrNull(timeSlot.dayOfWeek - 1) ?: "周${timeSlot.dayOfWeek}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = courseColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    // 节次
                    Text(
                        text = "第${timeSlot.startSection}-${timeSlot.startSection + timeSlot.sectionCount - 1}节",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 教室和周次
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 教室
                    if (timeSlot.classroom.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = timeSlot.classroom,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 周次
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = weeksText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // 编辑图标
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "编辑时间段",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 格式化周次显示
 * 将周次列表转换为易读的字符串，如 "1-8周" 或 "1,3,5周"
 */
private fun formatWeeksDisplay(weeks: List<Int>): String {
    if (weeks.isEmpty()) return "未设置"
    
    val sortedWeeks = weeks.sorted()
    
    // 检查是否连续
    val isConsecutive = sortedWeeks.zipWithNext().all { (a, b) -> b - a == 1 }
    
    return if (isConsecutive && sortedWeeks.size > 2) {
        "${sortedWeeks.first()}-${sortedWeeks.last()}周"
    } else if (sortedWeeks.size <= 5) {
        sortedWeeks.joinToString(",") + "周"
    } else {
        "${sortedWeeks.size}周"
    }
}

/**
 * 信息标签组件
 * 用于显示带图标的键值对信息
 */
@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
