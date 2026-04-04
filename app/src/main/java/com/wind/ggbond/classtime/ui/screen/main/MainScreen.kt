package com.wind.ggbond.classtime.ui.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.screen.main.components.*
import com.wind.ggbond.classtime.ui.screen.settings.SettingsViewModel
import com.wind.ggbond.classtime.ui.viewmodel.UpdateViewModel
import com.wind.ggbond.classtime.ui.components.UpdateDialog
import com.wind.ggbond.classtime.ui.components.AnnouncementDialog
import com.wind.ggbond.classtime.util.AnnouncementInfo
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri

/**
 * 主界面 - 编排器模式
 * 
 * 职责：
 * - 收集和管理 ViewModel 状态
 * - 协调子组件间的数据传递和事件处理
 * - 管理全局 UI 状态（对话框、BottomSheet 等）
 * - 不直接渲染复杂的 UI 结构，仅做组合编排
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    forceRefresh: Boolean = false
) {
    val currentSchedule by viewModel.currentSchedule.collectAsState()
    val currentWeekNumber by viewModel.currentWeekNumber.collectAsState()
    val actualWeekNumber by viewModel.actualWeekNumber.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val classTimes by viewModel.classTimes.collectAsState()
    val compactModeEnabled by settingsViewModel.compactModeEnabled.collectAsState()
    val showWeekendEnabled by settingsViewModel.showWeekendEnabled.collectAsState()
    val upcomingExams by viewModel.upcomingExams.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()

    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

    var selectedCourseId by remember { mutableStateOf<Long?>(null) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showScheduleQuickEdit by remember { mutableStateOf(false) }
    var adjustmentCourseId by remember { mutableStateOf<Long?>(null) }
    var currentAnnouncementIndex by remember { mutableStateOf(0) }
    
    LaunchedEffect(upcomingExams) {
        android.util.Log.d("MainScreen", "upcomingExams updated: ${upcomingExams.size} exams")
    }
    
    val totalWeeks = currentSchedule?.totalWeeks ?: 20
    val allCourses by viewModel.courses.collectAsState()
    val adjustments by viewModel.adjustments.collectAsState()
    
    val isScheduleReady = currentSchedule != null && currentWeekNumber > 0
    val isDataReady = isScheduleReady
    
    val pagerState = rememberPagerState(
        initialPage = if (isDataReady) currentWeekNumber - 1 else 0,
        pageCount = { totalWeeks }
    )
    
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(pagerState.currentPage) {
        viewModel.changeWeek(pagerState.currentPage + 1)
    }
    
    LaunchedEffect(isDataReady) {
        if (isDataReady && pagerState.currentPage != currentWeekNumber - 1) {
            coroutineScope.launch {
                pagerState.scrollToPage(currentWeekNumber - 1)
            }
        }
    }
    
    LaunchedEffect(forceRefresh) {
        if (forceRefresh) {
            android.util.Log.d("MainScreen", "强制刷新数据，清除缓存")
            viewModel.clearCache()
        }
    }

    LaunchedEffect(Unit) {
        updateViewModel.autoCheckIfNeeded()
        updateViewModel.fetchAnnouncements()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        ActionPanelProvider(
            currentSchedule = currentSchedule,
            currentWeekNumber = currentWeekNumber,
            pagerState = pagerState,
            viewMode = viewMode,
            compactModeEnabled = compactModeEnabled,
            settingsViewModel = settingsViewModel,
            navController = navController,
            onScheduleQuickEditRequest = { showScheduleQuickEdit = true },
            onWeekPickerRequest = { showWeekPicker = true },
            modifier = Modifier.matchParentSize()
        ) { paddingValues ->
            if (currentSchedule == null) {
                EmptyScheduleGuide(
                    navController = navController,
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                WeekViewContainer(
                    pagerState = pagerState,
                    currentSchedule = currentSchedule,
                    currentWeekNumber = currentWeekNumber,
                    actualWeekNumber = actualWeekNumber,
                    viewMode = viewMode,
                    classTimes = classTimes,
                    compactModeEnabled = compactModeEnabled,
                    showWeekendEnabled = showWeekendEnabled,
                    allCourses = allCourses,
                    adjustments = adjustments,
                    clipboard = clipboard,
                    viewModel = viewModel,
                    navController = navController,
                    snackbarHostState = snackbarHostState,
                    coroutineScope = coroutineScope,
                    isDataReady = isDataReady,
                    onCourseSelected = { courseId -> selectedCourseId = courseId },
                    onAdjustmentRequest = { courseId -> adjustmentCourseId = courseId },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
        
        SnackbarHost(hostState = snackbarHostState)
    }
    
    if (showWeekPicker) {
        WeekPickerDialog(
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
    
    selectedCourseId?.let { courseId ->
        com.wind.ggbond.classtime.ui.components.CourseDetailBottomSheet(
            courseId = courseId,
            onDismiss = { selectedCourseId = null },
            onDelete = { _ -> selectedCourseId = null },
            onRequestAdjustment = { id ->
                selectedCourseId = null
                adjustmentCourseId = id
            },
            onRequestAddExam = { id ->
                selectedCourseId = null
                navController.navigate(
                    Screen.CourseEdit.createRoute(courseId = id)
                )
            }
        )
    }
    
    adjustmentCourseId?.let { courseId ->
        val adjustmentViewModel: com.wind.ggbond.classtime.ui.screen.course.CourseAdjustmentViewModel = hiltViewModel()
        val viewingWeekNumber = pagerState.currentPage + 1
        
        LaunchedEffect(courseId) {
            adjustmentViewModel.loadCourse(courseId, viewingWeekNumber)
        }
        
        val course by adjustmentViewModel.course.collectAsState()
        val adjustmentSaveState by adjustmentViewModel.saveState.collectAsState()
        
        LaunchedEffect(adjustmentSaveState) {
            when (val state = adjustmentSaveState) {
                is com.wind.ggbond.classtime.ui.screen.course.CourseAdjustmentViewModel.SaveState.Success -> {
                    adjustmentCourseId = null
                    adjustmentViewModel.resetSaveState()
                    viewModel.clearCache()
                    snackbarHostState.showSnackbar(state.message)
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

    val scheduleVal = currentSchedule
    if (showScheduleQuickEdit && scheduleVal != null) {
        com.wind.ggbond.classtime.ui.components.ScheduleQuickEditDialog(
            schedule = scheduleVal,
            onConfirm = { name, startDate, totalWeeks ->
                viewModel.updateCurrentSchedule(name, startDate, totalWeeks)
                showScheduleQuickEdit = false
            },
            onDismiss = { showScheduleQuickEdit = false }
        )
    }

    val updateState by updateViewModel.updateState.collectAsState()
    val currentVersion by updateViewModel.currentVersion.collectAsState()
    val announcements by updateViewModel.announcements.collectAsState()

    when (val state = updateState) {
        is UpdateViewModel.UpdateState.UpdateAvailable -> {
            UpdateDialog(
                versionInfo = state.versionInfo,
                currentVersion = currentVersion,
                onDismiss = { updateViewModel.dismissUpdate() },
                onDownload = { url ->
                    updateViewModel.dismissUpdate()
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        android.util.Log.e("MainScreen", "打开下载链接失败", e)
                    }
                }
            )
        }
        else -> {}
    }

    announcements?.let { list ->
        if (currentAnnouncementIndex < list.size) {
            AnnouncementDialog(
                announcement = list[currentAnnouncementIndex],
                currentVersion = currentVersion,
                onDismiss = {
                    currentAnnouncementIndex++
                    if (currentAnnouncementIndex >= list.size) {
                        updateViewModel.dismissAnnouncements()
                        currentAnnouncementIndex = 0
                    }
                },
                onUrlClick = { url ->
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        android.util.Log.e("MainScreen", "打开公告链接失败", e)
                    }
                }
            )
        }
    }
}
