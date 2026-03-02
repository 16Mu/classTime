package com.wind.ggbond.classtime.ui.screen.scheduleimport

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.ui.navigation.BottomNavItem
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.screen.course.components.WeekSelectorDialog
import com.wind.ggbond.classtime.ui.components.ScheduleSelectionState
import com.wind.ggbond.classtime.ui.components.ScheduleExpiredDialog
import com.wind.ggbond.classtime.ui.components.CreateScheduleDialog
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.wind.ggbond.classtime.util.DateUtils
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.launch

/**
 * 批量创建课程页面
 * 支持一次创建多门课程，每门课程可设置多个时间段
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchCourseCreateScreen(
    navController: NavController,
    viewModel: BatchCourseCreateViewModel = hiltViewModel()
) {
    val courseItems by viewModel.courseItems.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val showWeekSelectorState by viewModel.showWeekSelector.collectAsState()
    val showClipboardImport by viewModel.showClipboardImport.collectAsState()
    val scheduleState by viewModel.scheduleState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val listContentPadding = 16.dp
    // 列表滚动状态，用于自动定位到新添加的课程
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // 记录当前可见的第一个项目索引，用于智能滚动定位
    val currentFirstVisibleItem = remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // 根据课表状态显示对应对话框
    when (val state = scheduleState) {
        // 加载中：显示加载指示器
        is ScheduleSelectionState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }
        // 需要创建课表：显示创建对话框
        is ScheduleSelectionState.NeedCreate -> {
            CreateScheduleDialog(
                onConfirm = { name, startDate, totalWeeks ->
                    viewModel.createSchedule(name, startDate, totalWeeks)
                },
                onDismiss = {
                    // 取消创建则返回上一页
                    navController.navigateUp()
                }
            )
            return
        }
        // 课表已过期：显示过期提醒对话框
        is ScheduleSelectionState.Expired -> {
            ScheduleExpiredDialog(
                schedule = state.schedule,
                onContinue = {
                    viewModel.confirmUseExpiredSchedule()
                },
                onCreateNew = {
                    viewModel.switchToCreateNewSchedule()
                },
                onDismiss = {
                    navController.navigateUp()
                }
            )
            return
        }
        // 就绪状态：继续显示正常界面
        is ScheduleSelectionState.Ready -> {
            // 继续执行下面的正常界面逻辑
        }
    }

    // 监听保存状态
    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is BatchSaveState.Success -> {
                snackbarHostState.showSnackbar(
                    message = "成功创建 ${state.courseCount} 门课程（${state.recordCount} 条记录）",
                    duration = SnackbarDuration.Short
                )
                kotlinx.coroutines.delay(500)
                // 返回主界面（使用底部Tab的课表路由，确保导航栈正确清理）
                navController.navigate(Screen.Main.createRoute(refresh = true)) {
                    popUpTo(BottomNavItem.Schedule.route) { inclusive = false }
                    launchSingleTop = true
                }
            }
            is BatchSaveState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.resetSaveState()
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
                title = { Text("手动添加课程") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 保存按钮
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.saveAll()
                        },
                        enabled = saveState !is BatchSaveState.Saving,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        if (saveState is BatchSaveState.Saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "保存",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (saveState is BatchSaveState.Saving) "保存中..."
                            else "保存(${viewModel.getEstimatedRecordCount()}条)"
                        )
                    }
                }
            )
        },
        // 底部添加课程按钮
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 添加课程按钮
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            // 优化动画逻辑：收缩卡片与列表下滑同时进行，新课程从顶部插入
                            coroutineScope.launch {
                                // 记录当前用户位置
                                val userCurrentPosition = currentFirstVisibleItem.value
                                
                                // 第1步：同时执行 - 收缩所有已有课程卡片 + 添加新课程（折叠态）
                                // 这样收缩动画和新课程插入导致的列表下滑会同时发生
                                viewModel.collapseAllCourses()
                                val newCourseId = viewModel.addCourseItemCollapsed()
                                
                                // 等待列表重组和收缩动画同步进行（stiffness=200f 约300ms）
                                kotlinx.coroutines.delay(150)
                                
                                // 第2步：滚动到顶部（新课程位置）
                                // 使用动画滚动，与收缩动画形成联动效果
                                listState.animateScrollToItem(0)
                                
                                // 等待滚动完成
                                kotlinx.coroutines.delay(200)
                                
                                // 第3步：展开新课程卡片
                                viewModel.expandCourse(newCourseId)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("继续添加课程")
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(listContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 课程列表
            // ✅ 显示顺序：最新的课程在最上面，序号从大到小显示
            val totalCourses = courseItems.size
            itemsIndexed(
                items = courseItems,
                key = { _, item -> item.id }
            ) { index, courseItem ->
                // 序号显示：最新的是最大的序号（如：第4门课程在最上面）
                val displayIndex = totalCourses - index
                // ✅ 课程卡片入场动画（与时间段动画一致）
                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    isVisible = true
                }
                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 150)
                    )
                ) {
                    CourseItemCard(
                        modifier = Modifier,
                        index = displayIndex - 1,
                        courseItem = courseItem,
                        canDelete = courseItems.size > 1,
                        onDelete = { viewModel.removeCourseItem(courseItem.id) },
                        onToggleExpanded = { viewModel.toggleCourseExpanded(courseItem.id) },
                        onToggleBasicInfoExpanded = { viewModel.toggleBasicInfoExpanded(courseItem.id) },
                        onCollapseBasicInfo = { viewModel.collapseBasicInfo(courseItem.id) },
                        onCourseNameChange = { viewModel.updateCourseName(courseItem.id, it) },
                        onTeacherChange = { viewModel.updateTeacher(courseItem.id, it) },
                        onDefaultClassroomChange = { viewModel.updateDefaultClassroom(courseItem.id, it) },
                        onColorChange = { viewModel.updateCourseColor(courseItem.id, it) },
                        onCreditChange = { viewModel.updateCredit(courseItem.id, it) },
                        onReminderEnabledChange = { viewModel.updateReminderEnabled(courseItem.id, it) },
                        onReminderMinutesChange = { viewModel.updateReminderMinutes(courseItem.id, it) },
                        onNoteChange = { viewModel.updateNote(courseItem.id, it) },
                        onShowClipboardImport = { viewModel.showClipboardImport(courseItem.id) },
                        onAddTimeSlot = { viewModel.addTimeSlot(courseItem.id) },
                        onRemoveTimeSlot = { slotId -> viewModel.removeTimeSlot(courseItem.id, slotId) },
                        onSlotDayChange = { slotId, day -> viewModel.updateSlotDayOfWeek(courseItem.id, slotId, day) },
                        onSlotStartChange = { slotId, start -> viewModel.updateSlotStartSection(courseItem.id, slotId, start) },
                        onSlotEndChange = { slotId, end -> viewModel.updateSlotEndSection(courseItem.id, slotId, end) },
                        onSlotClassroomChange = { slotId, room -> viewModel.updateSlotClassroom(courseItem.id, slotId, room) },
                        onSlotShowWeekSelector = { slotId -> viewModel.showWeekSelector(courseItem.id, slotId) }
                    )
                }
            }
        }
    }

    // 周次选择器对话框
    if (showWeekSelectorState != null) {
        WeekSelectorDialog(
            selectedWeeks = viewModel.getCurrentEditingWeeks(),
            onDismiss = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.hideWeekSelector()
            },
            onConfirm = { weeks ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.confirmWeekSelection(weeks)
            }
        )
    }

    // 剪贴板导入对话框
    if (showClipboardImport != null) {
        ClipboardImportDialog(
            onDismiss = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.hideClipboardImport()
            },
            onConfirm = { text ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val result = viewModel.applyFromClipboard(showClipboardImport!!, text)
                viewModel.hideClipboardImport()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(result, duration = SnackbarDuration.Long)
                }
            },
            clipboardManager = clipboardManager
        )
    }
}

/**
 * 单门课程卡片
 * 采用现代化卡片设计，与CourseEditScreen风格统一
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseItemCard(
    modifier: Modifier = Modifier,
    index: Int,
    courseItem: BatchCourseItem,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onToggleExpanded: () -> Unit,
    onToggleBasicInfoExpanded: () -> Unit,
    onCollapseBasicInfo: () -> Unit,
    onCourseNameChange: (String) -> Unit,
    onTeacherChange: (String) -> Unit,
    onDefaultClassroomChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onCreditChange: (Float) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderMinutesChange: (Int) -> Unit,
    onNoteChange: (String) -> Unit,
    onShowClipboardImport: () -> Unit,
    onAddTimeSlot: () -> Unit,
    onRemoveTimeSlot: (Long) -> Unit,
    onSlotDayChange: (Long, Int) -> Unit,
    onSlotStartChange: (Long, Int) -> Unit,
    onSlotEndChange: (Long, Int) -> Unit,
    onSlotClassroomChange: (Long, String) -> Unit,
    onSlotShowWeekSelector: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // 颜色选择器状态
    var showColorPicker by remember { mutableStateOf(false) }
    // 按压状态，用于触摸缩放交互
    var isPressed by remember { mutableStateOf(false) }
    // 按压缩放动画（弹簧物理）
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardPressScale"
    )
    // 展开/折叠箭头旋转动画（参考CourseInfoListScreen，速度更快）
    val arrowRotation by animateFloatAsState(
        targetValue = if (courseItem.isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 200f
        ),
        label = "arrowRotation"
    )
    
    // 解析课程颜色
    val courseColor = remember(courseItem.color) {
        if (courseItem.color.isNotEmpty()) {
            try {
                Color(android.graphics.Color.parseColor(courseItem.color))
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                color = Color.Transparent,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 序号圆角方形标识（与CourseEditScreen风格统一）
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = courseColor ?: MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${index + 1}",
                                color = if (courseColor != null) Color.White 
                                       else MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // 课程名称/提示
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = courseItem.courseName.ifEmpty { "第${index + 1}门课程" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 时间段标签
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "${courseItem.timeSlots.size} 个时间段",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            // 周次标签（如果有）
                            if (courseItem.weeks.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${courseItem.weeks.size}周",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 删除按钮
                    if (canDelete) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDelete()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                "删除课程",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 展开/折叠图标（弹簧旋转动画）
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = if (courseItem.isExpanded) "折叠" else "展开",
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        rotationZ = arrowRotation
                                    },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 可折叠的详细内容（参考CourseInfoListScreen动画，速度更快）
            AnimatedVisibility(
                visible = courseItem.isExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = 200f
                    )
                ) + fadeIn(
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = 250f
                    )
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = 250f
                    )
                ) + fadeOut(
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = 300f
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ===== 基础信息卡片（支持独立展开/收缩） =====
                    // 基础信息展开箭头旋转动画
                    val basicInfoArrowRotation by animateFloatAsState(
                        targetValue = if (courseItem.isBasicInfoExpanded) 180f else 0f,
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = 200f
                        ),
                        label = "basicInfoArrowRotation"
                    )
                    // 判断基础信息是否已填写完成（课程名称不为空）
                    val isBasicInfoFilled = courseItem.courseName.isNotBlank()
                    // 基础信息摘要文本
                    val basicInfoSummary = buildString {
                        append(courseItem.courseName.ifBlank { "未填写" })
                        if (courseItem.teacher.isNotBlank()) append(" · ${courseItem.teacher}")
                        if (courseItem.defaultClassroom.isNotBlank()) append(" · ${courseItem.defaultClassroom}")
                    }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 标题行（可点击展开/收缩）
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleBasicInfoExpanded()
                                    },
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "基础信息",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        // 收缩时显示摘要信息
                                        if (!courseItem.isBasicInfoExpanded && isBasicInfoFilled) {
                                            Text(
                                                text = basicInfoSummary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    // 展开/收缩图标
                                    Icon(
                                        Icons.Default.ExpandMore,
                                        contentDescription = if (courseItem.isBasicInfoExpanded) "收缩" else "展开",
                                        modifier = Modifier
                                            .size(24.dp)
                                            .graphicsLayer {
                                                rotationZ = basicInfoArrowRotation
                                            },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            // 可折叠的基础信息内容
                            AnimatedVisibility(
                                visible = courseItem.isBasicInfoExpanded,
                                enter = expandVertically(
                                    animationSpec = spring(
                                        dampingRatio = 0.8f,
                                        stiffness = 200f
                                    )
                                ) + fadeIn(
                                    animationSpec = spring(
                                        dampingRatio = 0.9f,
                                        stiffness = 250f
                                    )
                                ),
                                exit = shrinkVertically(
                                    animationSpec = spring(
                                        dampingRatio = 0.9f,
                                        stiffness = 250f
                                    )
                                ) + fadeOut(
                                    animationSpec = spring(
                                        dampingRatio = 0.9f,
                                        stiffness = 300f
                                    )
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 课程名称
                                    OutlinedTextField(
                                        value = courseItem.courseName,
                                        onValueChange = onCourseNameChange,
                                        label = { Text("课程名称 *") },
                                        placeholder = { Text("例如：Java开发") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.primary)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 1,
                                        maxLines = 3,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    )

                                    // 教师和默认教室
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = courseItem.teacher,
                                            onValueChange = onTeacherChange,
                                            label = { Text("教师") },
                                            placeholder = { Text("张老师") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.secondary)
                                            },
                                            modifier = Modifier.weight(1f),
                                            minLines = 1,
                                            maxLines = 2,
                                            shape = RoundedCornerShape(16.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            )
                                        )
                                        OutlinedTextField(
                                            value = courseItem.defaultClassroom,
                                            onValueChange = onDefaultClassroomChange,
                                            label = { Text("默认教室") },
                                            placeholder = { Text("A101") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.tertiary)
                                            },
                                            modifier = Modifier.weight(1f),
                                            minLines = 1,
                                            maxLines = 2,
                                            shape = RoundedCornerShape(16.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            )
                                        )
                                    }

                                    // 学分输入框
                                    var creditText by remember(courseItem.credit) {
                                        mutableStateOf(if (courseItem.credit > 0f) courseItem.credit.toString() else "")
                                    }
                                    OutlinedTextField(
                                        value = creditText,
                                        onValueChange = { newValue ->
                                            creditText = newValue
                                            newValue.toFloatOrNull()?.let { num -> onCreditChange(num) }
                                        },
                                        label = { Text("学分") },
                                        placeholder = { Text("例如: 3.0") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.secondary)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    )
                                    
                                    // 填写完成后自动收缩按钮
                                    if (isBasicInfoFilled) {
                                        TextButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onCollapseBasicInfo()
                                            },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("完成基础信息")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ===== 课程颜色卡片 =====
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Palette,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        "课程颜色",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "选择喜欢的颜色标识课程",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            // 颜色选择器网格
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CourseColorPalette.getAllColors().take(6).forEach { colorHex ->
                                    val color = try {
                                        Color(android.graphics.Color.parseColor(colorHex))
                                    } catch (_: Exception) {
                                        Color.Gray
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(color)
                                            .border(
                                                width = if (courseItem.color == colorHex) 3.dp else 0.dp,
                                                color = if (courseItem.color == colorHex)
                                                    MaterialTheme.colorScheme.primary
                                                else Color.Transparent,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onColorChange(colorHex)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (courseItem.color == colorHex) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "已选中",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // 更多颜色按钮
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showColorPicker = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.MoreHoriz, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("查看更多颜色")
                            }
                        }
                    }

                    // ===== 剪贴板智能导入 =====
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onShowClipboardImport()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("从剪贴板智能导入")
                    }

                    // ===== 时间安排卡片 =====
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    "时间安排",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                // 添加时间段按钮
                                FilledTonalButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onAddTimeSlot()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("添加", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            // 时间段列表（带进入动画）
                            val totalSlots = courseItem.timeSlots.size
                            courseItem.timeSlots.forEachIndexed { slotIndex, slot ->
                                val displayIndex = totalSlots - slotIndex
                                key(slot.id) {
                                    var isVisible by remember { mutableStateOf(false) }
                                    LaunchedEffect(Unit) {
                                        isVisible = true
                                    }
                                    AnimatedVisibility(
                                        visible = isVisible,
                                        enter = slideInVertically(
                                            initialOffsetY = { -it },
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        ) + fadeIn(
                                            animationSpec = tween(durationMillis = 150)
                                        )
                                    ) {
                                        TimeSlotCard(
                                            slotIndex = displayIndex - 1,
                                            slot = slot,
                                            canDelete = courseItem.timeSlots.size > 1,
                                            onDelete = { onRemoveTimeSlot(slot.id) },
                                            onDayChange = { day -> onSlotDayChange(slot.id, day) },
                                            onStartChange = { start -> onSlotStartChange(slot.id, start) },
                                            onEndChange = { end -> onSlotEndChange(slot.id, end) },
                                            onClassroomChange = { room -> onSlotClassroomChange(slot.id, room) },
                                            onShowWeekSelector = { onSlotShowWeekSelector(slot.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ===== 提醒设置卡片 =====
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (courseItem.reminderEnabled)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                if (courseItem.reminderEnabled) Icons.Default.NotificationsActive
                                                else Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = if (courseItem.reminderEnabled)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            "课程提醒",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            if (courseItem.reminderEnabled) "将在上课前提醒您" else "开启后可设置提醒时间",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = courseItem.reminderEnabled,
                                    onCheckedChange = { enabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onReminderEnabledChange(enabled)
                                    }
                                )
                            }

                            // 提醒分钟数（仅在开启时显示）
                            AnimatedVisibility(
                                visible = courseItem.reminderEnabled,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                var minutesText by remember(courseItem.reminderMinutes) {
                                    mutableStateOf(courseItem.reminderMinutes.toString())
                                }
                                OutlinedTextField(
                                    value = minutesText,
                                    onValueChange = { newValue ->
                                        minutesText = newValue
                                        newValue.toIntOrNull()?.let { num -> onReminderMinutesChange(num) }
                                    },
                                    label = { Text("提前提醒") },
                                    suffix = { Text("分钟") },
                                    placeholder = { Text("15") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }

                    // ===== 备注卡片 =====
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Notes,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    "备注",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            OutlinedTextField(
                                value = courseItem.note,
                                onValueChange = onNoteChange,
                                placeholder = { Text("添加课程备注（可选）") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // 颜色选择器对话框
    if (showColorPicker) {
        ColorPickerDialog(
            selectedColor = courseItem.color,
            onColorSelected = { color ->
                onColorChange(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

/**
 * 时间段卡片
 * 采用现代化设计，与整体风格统一
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSlotCard(
    slotIndex: Int,
    slot: TimeSlot,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onDayChange: (Int) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    onClassroomChange: (String) -> Unit,
    onShowWeekSelector: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 序号标识
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${slotIndex + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "时间段 ${slotIndex + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                if (canDelete) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDelete()
                        },
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Close,
                                "删除时间段",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 星期选择
            var expandedDay by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedDay,
                onExpandedChange = { expandedDay = it }
            ) {
                OutlinedTextField(
                    value = DateUtils.getDayOfWeekName(slot.dayOfWeek),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("星期 *") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CalendarToday,
                            null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDay) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
                ExposedDropdownMenu(
                    expanded = expandedDay,
                    onDismissRequest = { expandedDay = false }
                ) {
                    (1..7).forEach { day ->
                        DropdownMenuItem(
                            text = { Text(DateUtils.getDayOfWeekName(day)) },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDayChange(day)
                                expandedDay = false
                            }
                        )
                    }
                }
            }

            // 节次选择
            var startSectionText by remember(slot.startSection) { mutableStateOf(slot.startSection.toString()) }
            var sectionCountText by remember(slot.sectionCount) { mutableStateOf(slot.sectionCount.toString()) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = startSectionText,
                    onValueChange = { newValue ->
                        startSectionText = newValue
                        newValue.toIntOrNull()?.let { num -> onStartChange(num) }
                    },
                    label = { Text("起始节次 *") },
                    placeholder = { Text("1") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
                OutlinedTextField(
                    value = sectionCountText,
                    onValueChange = { newValue ->
                        sectionCountText = newValue
                        newValue.toIntOrNull()?.let { num -> onEndChange(num) }
                    },
                    label = { Text("持续节数 *") },
                    placeholder = { Text("2") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Timer,
                            null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }

            // 教室（可选）
            OutlinedTextField(
                value = slot.classroom,
                onValueChange = { onClassroomChange(it) },
                label = { Text("教室") },
                placeholder = { Text("留空使用默认教室") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Place,
                        null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            // 周次选择
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onShowWeekSelector()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "上课周次 *",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (slot.customWeeks.isEmpty()) "点击选择"
                            else "已选择 ${slot.customWeeks.size} 周 (${formatWeeksShort(slot.customWeeks)})",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 颜色选择器对话框
 */
@Composable
private fun ColorPickerDialog(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val allColors = CourseColorPalette.getAllColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 颜色网格：每行6个
                allColors.chunked(6).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { colorHex ->
                            val color = try {
                                Color(android.graphics.Color.parseColor(colorHex))
                            } catch (_: Exception) {
                                Color.Gray
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selectedColor == colorHex) 3.dp else 0.dp,
                                        color = if (selectedColor == colorHex)
                                            MaterialTheme.colorScheme.primary
                                        else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onColorSelected(colorHex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == colorHex) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "已选中",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismiss()
            }) {
                Text("关闭")
            }
        }
    )
}

/**
 * 格式化周次列表为简短显示文本
 * 例如：[1,2,3,4,5,6] -> "1-6周"
 * 例如：[1,3,5,7] -> "1,3,5,7周"
 */
private fun formatWeeksShort(weeks: List<Int>): String {
    if (weeks.isEmpty()) return ""
    val sorted = weeks.sorted()
    // 检查是否连续
    val isConsecutive = sorted.size > 1 && (sorted.last() - sorted.first() + 1 == sorted.size)
    return if (isConsecutive) {
        "${sorted.first()}-${sorted.last()}周"
    } else if (sorted.size <= 5) {
        sorted.joinToString(",") + "周"
    } else {
        "${sorted.first()}-${sorted.last()}周(共${sorted.size}周)"
    }
}

/**
 * 剪贴板智能导入对话框
 */
@Composable
private fun ClipboardImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    clipboardManager: ClipboardManager
) {
    var inputText by remember { mutableStateOf("") }
    var hasPasted by remember { mutableStateOf(false) }

    // 自动读取剪贴板内容
    LaunchedEffect(Unit) {
        val clipText = clipboardManager.getText()?.text ?: ""
        if (clipText.isNotBlank()) {
            inputText = clipText
            hasPasted = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("剪贴板智能导入") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "粘贴课程信息，系统将自动识别课程名称、教师、教室、周次等信息。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (hasPasted && inputText.isNotBlank()) {
                    Text(
                        "已自动读取剪贴板内容",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("课程信息") },
                    placeholder = { Text("粘贴课程信息...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    maxLines = 10,
                    shape = RoundedCornerShape(10.dp)
                )

                // 示例说明
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            "示例格式：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "体育4★\n(1-2节)1-5周,7-9周,11-13周\n(单),14-16周,19周/校区:潼南\n校区/场地:未排地点/教师:李兢",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(inputText) },
                enabled = inputText.isNotBlank()
            ) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
