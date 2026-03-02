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
                            // 智能滚动逻辑：根据用户当前位置决定滚动行为
                            coroutineScope.launch {
                                // 记录当前用户位置
                                val userCurrentPosition = currentFirstVisibleItem.value
                                
                                // 第1步：收缩所有已有课程卡片
                                viewModel.collapseAllCourses()
                                // 等待收缩动画基本完成（stiffness=100f 约600ms）
                                kotlinx.coroutines.delay(500)
                                // 第2步：添加新课程（初始为折叠态，作为卡片入场）
                                val newCourseId = viewModel.addCourseItemCollapsed()
                                // 等待列表重组完成
                                kotlinx.coroutines.delay(100)
                                // 第3步：智能滚动 - 如果用户在顶部则保持新课程可见，否则保持用户原视野
                                val targetScrollPosition = if (userCurrentPosition <= 1) {
                                    // 用户在顶部附近，滚动到新课程位置（第0个）
                                    0
                                } else {
                                    // 用户不在顶部，保持用户原来的视野位置（由于新课程插入到顶部，原位置需要+1）
                                    userCurrentPosition + 1
                                }
                                listState.animateScrollToItem(targetScrollPosition)
                                // 等待滚动完成后再展开
                                kotlinx.coroutines.delay(300)
                                // 第4步：展开新课程卡片
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
            itemsIndexed(
                items = courseItems,
                key = { _, item -> item.id }
            ) { index, courseItem ->
                CourseItemCard(
                    modifier = Modifier,
                    index = index,
                    courseItem = courseItem,
                    canDelete = courseItems.size > 1,
                    onDelete = { viewModel.removeCourseItem(courseItem.id) },
                    onToggleExpanded = { viewModel.toggleCourseExpanded(courseItem.id) },
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
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardPressScale"
    )
    // 展开/折叠箭头旋转动画（低刚度弹簧，缓慢优雅旋转）
    val arrowRotation by animateFloatAsState(
        targetValue = if (courseItem.isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 120f
        ),
        label = "arrowRotation"
    )

    Card(
        modifier = modifier
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
                color = if (courseItem.color.isNotEmpty()) {
                    try {
                        Color(android.graphics.Color.parseColor(courseItem.color)).copy(alpha = 0.15f)
                    } catch (_: Exception) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    }
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 序号圆形标识
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = if (courseItem.color.isNotEmpty()) {
                            try {
                                Color(android.graphics.Color.parseColor(courseItem.color))
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${index + 1}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 课程名称/提示
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = courseItem.courseName.ifEmpty { "第${index + 1}门课程" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${courseItem.timeSlots.size} 个时间段" +
                                    if (courseItem.weeks.isNotEmpty()) " | ${courseItem.weeks.size}周" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 删除按钮
                    if (canDelete) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDelete()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                "删除课程",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // 展开/折叠图标（弹簧旋转动画）
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (courseItem.isExpanded) "折叠" else "展开",
                        modifier = Modifier.graphicsLayer {
                            rotationZ = arrowRotation
                        }
                    )
                }
            }

            // 可折叠的详细内容（低刚度弹簧，PPT级平滑展开/收缩）
            AnimatedVisibility(
                visible = courseItem.isExpanded,
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ===== 基础信息区域 =====
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "基础信息",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 课程名称 - 支持动态扩大，内容过多时可内部滑动
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
                        shape = RoundedCornerShape(12.dp)
                    )

                    // 教师和默认教室
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = courseItem.defaultClassroom,
                            onValueChange = onDefaultClassroomChange,
                            label = { Text("默认教室") },
                            placeholder = { Text("4303") },
                            leadingIcon = {
                                Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.tertiary)
                            },
                            modifier = Modifier.weight(1f),
                            minLines = 1,
                            maxLines = 2,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // 学分输入框（使用字符串状态管理，避免删除键光标跳动）
                    var creditText by remember(courseItem.credit) {
                        mutableStateOf(if (courseItem.credit > 0f) courseItem.credit.toString() else "")
                    }
                    OutlinedTextField(
                        value = creditText,
                        onValueChange = { newValue ->
                            // 直接赋值，不做 filter，避免光标位置丢失导致删除键失效
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
                        shape = RoundedCornerShape(12.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "课程颜色",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        // 快速颜色选择（展示6个常用颜色）
                        CourseColorPalette.getAllColors().take(6).forEach { colorHex ->
                            val color = try {
                                Color(android.graphics.Color.parseColor(colorHex))
                            } catch (_: Exception) {
                                Color.Gray
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (courseItem.color == colorHex) 2.dp else 0.dp,
                                        color = if (courseItem.color == colorHex)
                                            MaterialTheme.colorScheme.primary
                                        else Color.Transparent,
                                        shape = CircleShape
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        // 更多颜色
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showColorPicker = true
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreHoriz,
                                "更多颜色",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // ===== 剪贴板智能导入 =====
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onShowClipboardImport()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // ===== 时间安排区域 =====
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
                        // 添加时间段按钮
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAddTimeSlot()
                            }
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加时间段")
                        }
                    }

                    // 时间段列表（带进入动画）
                    courseItem.timeSlots.forEachIndexed { slotIndex, slot ->
                        // ✅ 使用key + remember控制动画状态
                        key(slot.id) {
                            var isVisible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                isVisible = true
                            }
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = slideInHorizontally(
                                    initialOffsetX = { fullWidth -> fullWidth },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                ) + fadeIn(
                                    animationSpec = tween(durationMillis = 200)
                                )
                            ) {
                                TimeSlotCard(
                                    slotIndex = slotIndex,
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // ===== 提醒设置区域 =====
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (courseItem.reminderEnabled) Icons.Default.NotificationsActive
                                else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "上课提醒",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = courseItem.reminderEnabled,
                            onCheckedChange = onReminderEnabledChange
                        )
                    }

                    // 提醒分钟数（仅在开启时显示）
                    if (courseItem.reminderEnabled) {
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
                            placeholder = { Text("10") },
                            leadingIcon = {
                                Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // ===== 备注区域 =====
                    OutlinedTextField(
                        value = courseItem.note,
                        onValueChange = onNoteChange,
                        label = { Text("备注") },
                        placeholder = { Text("添加课程备注（可选）") },
                        leadingIcon = {
                            Icon(Icons.Default.Notes, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp)
                    )
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "时间段 ${slotIndex + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (canDelete) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDelete()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            "删除时间段",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
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
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDay) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(10.dp),
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

            // 节次选择（使用字符串状态管理，避免删除键光标跳动）
            var startSectionText by remember(slot.startSection) { mutableStateOf(slot.startSection.toString()) }
            var sectionCountText by remember(slot.sectionCount) { mutableStateOf(slot.sectionCount.toString()) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = startSectionText,
                    onValueChange = { newValue ->
                        // 直接赋值，不做 filter，避免光标位置丢失导致删除键失效
                        startSectionText = newValue
                        newValue.toIntOrNull()?.let { num -> onStartChange(num) }
                    },
                    label = { Text("起始节次 *") },
                    placeholder = { Text("1") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
                OutlinedTextField(
                    value = sectionCountText,
                    onValueChange = { newValue ->
                        // 直接赋值，不做 filter，避免光标位置丢失导致删除键失效
                        sectionCountText = newValue
                        newValue.toIntOrNull()?.let { num -> onEndChange(num) }
                    },
                    label = { Text("持续节次 *") },
                    placeholder = { Text("2") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }

            // 该时间段专属教室（可选）
            OutlinedTextField(
                value = slot.classroom,
                onValueChange = { onClassroomChange(it) },
                label = { Text("教室（留空使用全局教室）") },
                placeholder = { Text("留空则使用全局教室") },
                leadingIcon = {
                    Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.tertiary)
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 2,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            // 周次选择（每个时间段独立设置）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onShowWeekSelector()
                    }
            ) {
                OutlinedTextField(
                    value = if (slot.customWeeks.isEmpty()) "点击选择周次"
                    else "已选择 ${slot.customWeeks.size} 周 (${formatWeeksShort(slot.customWeeks)})",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("周次 *") },
                    trailingIcon = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
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
