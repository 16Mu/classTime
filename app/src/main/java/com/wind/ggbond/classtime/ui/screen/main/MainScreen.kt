package com.wind.ggbond.classtime.ui.screen.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.screen.main.components.GridWeekView
import com.wind.ggbond.classtime.ui.screen.main.components.WeekView
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import android.app.Activity
import com.wind.ggbond.classtime.R
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
/**
 * 主界面
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
    settingsViewModel: com.wind.ggbond.classtime.ui.screen.settings.SettingsViewModel = hiltViewModel(),
    forceRefresh: Boolean = false
) {
    val currentSchedule by viewModel.currentSchedule.collectAsState()
    val currentWeekNumber by viewModel.currentWeekNumber.collectAsState()
    val actualWeekNumber by viewModel.actualWeekNumber.collectAsState()  // 真实本周（不随滑动变化）
    val viewMode by viewModel.viewMode.collectAsState()
    val classTimes by viewModel.classTimes.collectAsState()
    val compactModeEnabled by settingsViewModel.compactModeEnabled.collectAsState()
    val showWeekendEnabled by settingsViewModel.showWeekendEnabled.collectAsState()
    val upcomingExams by viewModel.upcomingExams.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val showClipboardImport by viewModel.showClipboardImport.collectAsState()
    val clipboardImportResult by viewModel.clipboardImportResult.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // Snackbar 状态（用于删除撤销等操作反馈）
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    
    // 课程详情BottomSheet状态
    var selectedCourseId by remember { mutableStateOf<Long?>(null) }
    
    // 周次选择器对话框状态
    var showWeekPicker by remember { mutableStateOf(false) }
    
    // 课表快速编辑对话框状态
    var showScheduleQuickEdit by remember { mutableStateOf(false) }
    
    // 临时调课对话框状态
    var adjustmentCourseId by remember { mutableStateOf<Long?>(null) }
    
    // 调试日志
    androidx.compose.runtime.LaunchedEffect(upcomingExams) {
        android.util.Log.d("MainScreen", "upcomingExams updated: ${upcomingExams.size} exams")
    }
    
    // 总周数
    val totalWeeks = currentSchedule?.totalWeeks ?: 20
    
    // 课程数据
    val allCourses by viewModel.courses.collectAsState()
    // 调课记录（用于触发重组）
    val adjustments by viewModel.adjustments.collectAsState()
    
    // ✅ 优化：等待学期数据和课程数据加载完成后再初始化 Pager
    // 这样可以确保 initialPage 是正确的当前周次，避免先显示第1周再跳转的问题
    // ✅ 修复：添加课程数据加载状态检查，避免从小组件进入时显示空课表
    val isScheduleReady = currentSchedule != null && currentWeekNumber > 0
    val isCoursesLoading = isScheduleReady && allCourses.isEmpty()
    val isDataReady = isScheduleReady
    
    // 创建 Pager 状态，初始页面为当前周次
    // ✅ 优化：预加载相邻页面，提升滑动流畅度
    val pagerState = rememberPagerState(
        initialPage = if (isDataReady) currentWeekNumber - 1 else 0,
        pageCount = { totalWeeks }
    )
    
    val coroutineScope = rememberCoroutineScope()
    
    // 监听页面变化，同步更新 ViewModel 中的当前周次，并提供触觉反馈
    LaunchedEffect(pagerState.currentPage) {
        viewModel.changeWeek(pagerState.currentPage + 1)
        // 滑动切换周次时提供轻量级震动反馈
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    
    // ✅ 修复：当数据准备好后，立即滚动到当前周（只在首次加载时执行）
    LaunchedEffect(isDataReady) {
        if (isDataReady && pagerState.currentPage != currentWeekNumber - 1) {
            coroutineScope.launch {
                // 使用 scrollToPage 而不是 animateScrollToPage，避免动画闪烁
                pagerState.scrollToPage(currentWeekNumber - 1)
            }
        }
    }
    
    // ✅ 修复：导入成功后强制刷新数据
    LaunchedEffect(forceRefresh) {
        if (forceRefresh) {
            android.util.Log.d("MainScreen", "强制刷新数据，清除缓存")
            viewModel.clearCache()
        }
    }
    
    // ✅ 使用 Box 包装，引导页面在最外层完全覆盖
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            // Snackbar 宿主（用于删除撤销等操作反馈）
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
            // FAB：快速添加课程
            floatingActionButton = {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(
                            Screen.CourseEdit.createRoute(
                                weekNumber = pagerState.currentPage + 1
                            )
                        )
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = "添加课程") },
                    text = { Text("添加") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            },
            topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Material3 TopAppBar 已内置 statusBar insets 处理，无需重复添加
            ) {
                TopAppBar(
                    // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Column(
                            modifier = Modifier.clickable {
                                // 点击标题区域弹出课表快速编辑对话框
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showScheduleQuickEdit = true
                            }
                        ) {
                            // 课表名称 - 可点击编辑
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentSchedule?.name ?: "未设置课表",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "编辑课表",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // 周次指示器 - 可点击弹出周次选择器
                            androidx.compose.material3.AssistChip(
                                onClick = { showWeekPicker = true },
                                label = {
                                    Text(
                                        text = "第 ${pagerState.currentPage + 1} 周",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.UnfoldMore,
                                        contentDescription = "选择周次",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    },
                    actions = {
                        // 带 Q 弹动画的视图切换按钮
                        val scale by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "button_scale"
                        )
                        
                        // 紧凑模式快捷切换按钮
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                settingsViewModel.updateCompactModeEnabled(!compactModeEnabled)
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (compactModeEnabled) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        ) {
                            // 紧凑模式图标：使用 UnfoldLess 表示紧凑，UnfoldMore 表示展开
                            AnimatedContent(
                                targetState = compactModeEnabled,
                                transitionSpec = {
                                    scaleIn(
                                        initialScale = 0.6f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessHigh
                                        )
                                    ) + fadeIn() togetherWith scaleOut(
                                        targetScale = 0.6f,
                                        animationSpec = tween(150)
                                    ) + fadeOut()
                                },
                                label = "compact_icon_transition"
                            ) { isCompact ->
                                Icon(
                                    imageVector = if (isCompact) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                    contentDescription = if (isCompact) "关闭紧凑模式" else "开启紧凑模式"
                                )
                            }
                        }
                        
                        // 视图模式切换按钮（列表/网格）
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.switchViewMode(if (viewMode == 0) 1 else 0)
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        ) {
                            // 图标切换也带动画
                            AnimatedContent(
                                targetState = viewMode,
                                transitionSpec = {
                                    scaleIn(
                                        initialScale = 0.6f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessHigh
                                        )
                                    ) + fadeIn() togetherWith scaleOut(
                                        targetScale = 0.6f,
                                        animationSpec = tween(150)
                                    ) + fadeOut()
                                },
                                label = "icon_transition"
                            ) { mode ->
                                Icon(
                                    imageVector = if (mode == 0) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = if (mode == 0) "列表" else "网格"
                                )
                            }
                        }
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 主内容区域 - 横向滑动切换周次
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { it }  // 使用页码作为key，优化重组性能
            ) { page ->
                val weekNumber = page + 1
                val examsForWeek by viewModel.examsForCurrentWeek.collectAsState()
                
                // ✅ 修复：使用 allCourses.size 和 adjustments.size 作为 key
                // 确保课程数据加载后或调课记录变化时能触发重组
                val coursesForWeek = remember(allCourses.size, adjustments.size, weekNumber) {
                    viewModel.getCoursesForWeek(weekNumber)
                }
                
                // 使用 AnimatedContent 实现视图切换的 Q 弹动画
                AnimatedContent(
                    targetState = viewMode,
                    transitionSpec = {
                        // 缩放 + 淡入淡出的 Q 弹效果
                        scaleIn(
                            initialScale = 0.92f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,  // Q 弹效果
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeIn(
                            animationSpec = tween(300)
                        ) togetherWith scaleOut(
                            targetScale = 0.92f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeOut(
                            animationSpec = tween(300)
                        )
                    },
                    label = "ViewModeTransition"
                ) { mode ->
                    when (mode) {
                        0 -> {
                            // 网格式周视图
                            GridWeekView(
                                weekNumber = weekNumber,
                                coursesMap = coursesForWeek,
                                classTimes = classTimes,
                                compactModeEnabled = compactModeEnabled,
                                showWeekend = showWeekendEnabled,
                                onCourseClick = { course ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCourseId = course.id
                                },
                                onCourseLongClick = { course ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // ✅ 长按时不再触发调课窗口，改由操作面板处理
                                    // adjustmentCourseId = course.id
                                },
                                onEmptyCellClick = { dayOfWeek, section ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    navController.navigate(
                                        Screen.CourseEdit.createRoute(
                                            courseId = null,
                                            dayOfWeek = dayOfWeek,
                                            startSection = section,
                                            sectionCount = 1,
                                            weekNumber = weekNumber
                                        )
                                    )
                                },
                                semesterStartDate = currentSchedule?.startDate,
                                exams = if (weekNumber == currentWeekNumber) examsForWeek else emptyList(),
                                onExamClick = { exam ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCourseId = exam.courseId
                                },
                                // ✅ 操作面板回调
                                onSlotActionAdd = { dayOfWeek, section, weekNum ->
                                    navController.navigate(
                                        Screen.CourseEdit.createRoute(
                                            courseId = null,
                                            dayOfWeek = dayOfWeek,
                                            startSection = section,
                                            sectionCount = 1,
                                            weekNumber = weekNum
                                        )
                                    )
                                },
                                onSlotActionAdjust = { course ->
                                    val viewingWeekNumber = pagerState.currentPage + 1
                                    adjustmentCourseId = course.id
                                },
                                onSlotActionDelete = { course, weekNum ->
                                    viewModel.deleteCourseForWeek(course, weekNum)
                                    // 删除后显示Snackbar带撤销按钮
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "已删除「${course.courseName}」",
                                            actionLabel = "撤销",
                                            duration = androidx.compose.material3.SnackbarDuration.Long
                                        )
                                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                            viewModel.undoLastDelete()
                                        }
                                    }
                                },
                                onSlotActionCopy = { course, weekNum ->
                                    viewModel.copyCourseToClipboard(course, weekNum)
                                },
                                onSlotActionPaste = { dayOfWeek, section ->
                                    viewModel.pasteCourseFromClipboard(weekNumber, dayOfWeek, section)
                                },
                                hasClipboard = clipboard != null,
                                currentWeekNumber = currentWeekNumber
                            )
                        }
                        1 -> {
                            // 列表式周视图
                            WeekView(
                                weekNumber = weekNumber,
                                coursesMap = coursesForWeek,
                                showWeekend = showWeekendEnabled,
                                onCourseClick = { course ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCourseId = course.id
                                },
                                onCourseLongClick = { course ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    adjustmentCourseId = course.id
                                },
                                getAdjustmentInfo = { courseId, week, day, section ->
                                    viewModel.isAdjustedCourse(courseId, week, day, section)
                                }
                            )
                        }
                    }
                }
            }
            
            // "回到本周"悬浮按钮 - 当用户滑到非当前周时显示
            val isNotCurrentWeek = isDataReady && (pagerState.currentPage + 1) != actualWeekNumber
            androidx.compose.animation.AnimatedVisibility(
                visible = isNotCurrentWeek,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(actualWeekNumber - 1)
                        }
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("回到第${actualWeekNumber}周")
                }
            }
        }
    }
    
    // 周次快速选择对话框
    if (showWeekPicker) {
        com.wind.ggbond.classtime.ui.screen.main.components.WeekPickerDialog(
            currentWeek = pagerState.currentPage + 1,
            totalWeeks = totalWeeks,
            actualCurrentWeek = actualWeekNumber,
            onWeekSelected = { week ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(week - 1)
                }
                showWeekPicker = false
            },
            onDismiss = { showWeekPicker = false }
        )
    }
    
    // 课程详情BottomSheet
    selectedCourseId?.let { courseId ->
        com.wind.ggbond.classtime.ui.components.CourseDetailBottomSheet(
            courseId = courseId,
            onDismiss = { selectedCourseId = null },
            onDelete = { _ ->
                selectedCourseId = null
            },
            // ✅ 步骤6.2: Dialog提升到MainScreen层级，避免BottomSheet内嵌套Dialog
            onRequestAdjustment = { id ->
                selectedCourseId = null  // 先关闭BottomSheet
                adjustmentCourseId = id  // 再触发调课Dialog
            },
            onRequestAddExam = { id ->
                selectedCourseId = null  // 先关闭BottomSheet
                // CourseDetail路由已删除，跳转到课程编辑页
                navController.navigate(
                    Screen.CourseEdit.createRoute(courseId = id)
                )
            }
        )
    }
    
    // 临时调课对话框
    adjustmentCourseId?.let { courseId ->
        val adjustmentViewModel: com.wind.ggbond.classtime.ui.screen.course.CourseAdjustmentViewModel = hiltViewModel()
        
        // ✅ 修复：使用用户当前查看的周次，而不是系统当前周
        val viewingWeekNumber = pagerState.currentPage + 1
        
        LaunchedEffect(courseId) {
            adjustmentViewModel.loadCourse(courseId, viewingWeekNumber)
        }
        
        val course by adjustmentViewModel.course.collectAsState()
        val adjustmentSaveState by adjustmentViewModel.saveState.collectAsState()
        
        LaunchedEffect(adjustmentSaveState) {
            when (val state = adjustmentSaveState) {
                is com.wind.ggbond.classtime.ui.screen.course.CourseAdjustmentViewModel.SaveState.Success -> {
                    snackbarHostState.showSnackbar(state.message)
                    viewModel.clearCache()
                    adjustmentCourseId = null
                    adjustmentViewModel.resetSaveState()
                }
                is com.wind.ggbond.classtime.ui.screen.course.CourseAdjustmentViewModel.SaveState.Error -> {
                    snackbarHostState.showSnackbar(state.message)
                    adjustmentViewModel.resetSaveState()
                }
                else -> {}
            }
        }
        
        course?.let { c ->
            com.wind.ggbond.classtime.ui.screen.course.components.CourseAdjustmentDialog(
                course = c,
                currentWeekNumber = adjustmentViewModel.originalWeekNumber.collectAsState().value,
                totalWeeks = currentSchedule?.totalWeeks ?: 20,
                newWeekNumber = adjustmentViewModel.newWeekNumber.collectAsState().value,
                newDayOfWeek = adjustmentViewModel.newDayOfWeek.collectAsState().value,
                newStartSection = adjustmentViewModel.newStartSection.collectAsState().value,
                newSectionCount = adjustmentViewModel.newSectionCount.collectAsState().value,
                newClassroom = adjustmentViewModel.newClassroom.collectAsState().value,
                reason = adjustmentViewModel.reason.collectAsState().value,
                hasConflict = adjustmentViewModel.hasConflict.collectAsState().value,
                conflictMessage = adjustmentViewModel.conflictMessage.collectAsState().value,
                isSaving = adjustmentSaveState is com.wind.ggbond.classtime.ui.screen.course.CourseAdjustmentViewModel.SaveState.Saving,
                onNewWeekNumberChange = { adjustmentViewModel.setNewWeekNumber(it) },
                onNewDayOfWeekChange = { adjustmentViewModel.setNewDayOfWeek(it) },
                onNewStartSectionChange = { adjustmentViewModel.setNewStartSection(it) },
                onNewSectionCountChange = { adjustmentViewModel.setNewSectionCount(it) },
                onNewClassroomChange = { adjustmentViewModel.setNewClassroom(it) },
                onReasonChange = { adjustmentViewModel.setReason(it) },
                onConfirm = { adjustmentViewModel.saveAdjustment() },
                onDismiss = { adjustmentCourseId = null }
            )
        }
    }
    
    // P0-2: 剪贴板智能导入功能尚未实现，暂时移除入口，待功能完成后恢复
    
    // 课表快速编辑对话框
    if (showScheduleQuickEdit && currentSchedule != null) {
        com.wind.ggbond.classtime.ui.components.ScheduleQuickEditDialog(
            schedule = currentSchedule!!,
            onConfirm = { name, startDate, totalWeeks ->
                viewModel.updateCurrentSchedule(name, startDate, totalWeeks)
                showScheduleQuickEdit = false
            },
            onDismiss = { showScheduleQuickEdit = false }
        )
    }
}
}
