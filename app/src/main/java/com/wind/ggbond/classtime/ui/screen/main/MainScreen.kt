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
import com.wind.ggbond.classtime.ui.components.CourseDetailBottomSheet
import com.wind.ggbond.classtime.ui.components.ScheduleQuickEditDialog
import com.wind.ggbond.classtime.util.AnnouncementInfo
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    backgroundThemeManager: BackgroundThemeManager,
    courseColors: List<String> = emptyList(),
    forceRefresh: Boolean = false
) {
    val currentSchedule by viewModel.currentSchedule.collectAsState()
    val currentWeekNumber by viewModel.currentWeekNumber.collectAsState()
    val actualWeekNumber by viewModel.actualWeekNumber.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val classTimes by viewModel.classTimes.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val compactModeEnabled by settingsViewModel.compactModeEnabled.collectAsState()
    val showWeekendEnabled by settingsViewModel.showWeekendEnabled.collectAsState()
    val glassEffectEnabled by settingsViewModel.glassEffectEnabled.collectAsState()
    val upcomingExams by viewModel.upcomingExams.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()

    val isDynamicThemeEnabled by backgroundThemeManager.isDynamicThemeEnabled().collectAsState(initial = false)
    val activeScheme by backgroundThemeManager.getActiveBackgroundScheme().collectAsState(initial = null)
    val isWallpaperEnabled = isDynamicThemeEnabled && activeScheme != null

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        settingsViewModel.messageEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    var selectedCourseId by remember { mutableLongStateOf(0L).also { it.value = 0; } }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showScheduleQuickEdit by remember { mutableStateOf(false) }
    var adjustmentCourseId by remember { mutableLongStateOf(0L).also { it.value = 0; } }
    var currentAnnouncementIndex by remember { mutableIntStateOf(0) }

    val totalWeeks = currentSchedule?.totalWeeks ?: 20
    val allCourses by viewModel.courses.collectAsState()
    val adjustments by viewModel.adjustments.collectAsState()
    val isDataReady = currentSchedule != null && currentWeekNumber > 0

    val pagerState = rememberPagerState(
        initialPage = if (isDataReady) currentWeekNumber - 1 else 0,
        pageCount = { totalWeeks }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) { viewModel.changeWeek(pagerState.currentPage + 1) }
    LaunchedEffect(isDataReady) {
        if (isDataReady && pagerState.currentPage != currentWeekNumber - 1)
            coroutineScope.launch { pagerState.scrollToPage(currentWeekNumber - 1) }
    }
    LaunchedEffect(forceRefresh) { if (forceRefresh) viewModel.clearCache() }
    LaunchedEffect(Unit) {
        updateViewModel.autoCheckIfNeeded(); updateViewModel.fetchAnnouncements()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ActionPanelProvider(currentSchedule = currentSchedule, currentWeekNumber = currentWeekNumber,
            pagerState = pagerState, viewMode = viewMode, compactModeEnabled = compactModeEnabled,
            settingsViewModel = settingsViewModel, navController = navController,
            onScheduleQuickEditRequest = { showScheduleQuickEdit = true },
            onWeekPickerRequest = { showWeekPicker = true },
            onViewModeToggle = { viewModel.switchViewMode(if (viewMode == 0) 1 else 0) },
            isWallpaperEnabled = isWallpaperEnabled, modifier = Modifier.matchParentSize()) { paddingValues ->
            if (currentSchedule == null) EmptyScheduleGuide(navController = navController, modifier = Modifier.padding(paddingValues))
            else WeekViewContainer(pagerState = pagerState, currentSchedule = currentSchedule,
                currentWeekNumber = currentWeekNumber, actualWeekNumber = actualWeekNumber,
                viewMode = viewMode, classTimes = classTimes, compactModeEnabled = compactModeEnabled,
                showWeekendEnabled = showWeekendEnabled, glassEffectEnabled = glassEffectEnabled,
                allCourses = allCourses, adjustments = adjustments, clipboard = clipboard,
                viewModel = viewModel, navController = navController, snackbarHostState = snackbarHostState,
                coroutineScope = coroutineScope, isDataReady = isDataReady,
                onCourseSelected = { selectedCourseId = it }, onAdjustmentRequest = { adjustmentCourseId = it },
                isWallpaperEnabled = isWallpaperEnabled, courseColors = courseColors,
                displayMode = displayMode,
                modifier = Modifier.padding(paddingValues))
        }
        SnackbarHost(hostState = snackbarHostState)
    }

    if (showWeekPicker) WeekPickerDialog(currentWeek = pagerState.currentPage + 1, totalWeeks = totalWeeks,
        actualCurrentWeek = actualWeekNumber, onWeekSelected = { week ->
            coroutineScope.launch { pagerState.animateScrollToPage(week - 1) }; showWeekPicker = false
        }, onDismiss = { showWeekPicker = false })

    selectedCourseId.takeIf { it > 0 }?.let { courseId ->
        CourseDetailBottomSheet(courseId = courseId, onDismiss = { selectedCourseId = 0L },
            onDelete = { _ -> selectedCourseId = 0L },
            onRequestAdjustment = { id -> selectedCourseId = 0L; adjustmentCourseId = id },
            onRequestAddExam = { id -> selectedCourseId = 0L; navController.navigate(Screen.CourseEdit.createRoute(id)) })
    }

    adjustmentCourseId.takeIf { it > 0 }?.let { courseId ->
        val adjustmentViewModel: com.wind.ggbond.classtime.ui.screen.course.CourseAdjustmentViewModel = hiltViewModel()
        val viewingWeekNumber = pagerState.currentPage + 1
        LaunchedEffect(courseId) { adjustmentViewModel.loadCourse(courseId, viewingWeekNumber) }
        val course by adjustmentViewModel.course.collectAsState()
        val adjustmentSaveState by adjustmentViewModel.saveState.collectAsState()
        LaunchedEffect(adjustmentSaveState) {
            when (val state = adjustmentSaveState) {
                is com.wind.ggbond.classtime.ui.screen.course.CourseAdjustmentViewModel.SaveState.Success -> {
                    adjustmentCourseId = 0L; adjustmentViewModel.resetSaveState(); viewModel.clearCache(); snackbarHostState.showSnackbar(state.message)
                }
                is com.wind.ggbond.classtime.ui.screen.course.CourseAdjustmentViewModel.SaveState.Error -> {
                    snackbarHostState.showSnackbar(state.message); adjustmentViewModel.resetSaveState()
                }
                else -> {}
            }
        }
        course?.let { c ->
            com.wind.ggbond.classtime.ui.screen.course.components.CourseAdjustmentDialog(course = c,
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
                onDismiss = { adjustmentCourseId = 0L })
        }
    }

    currentSchedule?.takeIf { showScheduleQuickEdit }?.let { scheduleVal ->
        ScheduleQuickEditDialog(schedule = scheduleVal,
            onConfirm = { name, startDate, totalWeeks ->
                viewModel.updateCurrentSchedule(name, startDate, totalWeeks); showScheduleQuickEdit = false
            }, onDismiss = { showScheduleQuickEdit = false })
    }

    when (val state = updateViewModel.updateState.collectAsState().value) {
        is UpdateViewModel.UpdateState.UpdateAvailable -> UpdateDialog(versionInfo = state.versionInfo,
            currentVersion = updateViewModel.currentVersion.collectAsState().value,
            onDismiss = { updateViewModel.dismissUpdate() },
            onDownload = { url -> updateViewModel.dismissUpdate(); safeOpenUrl(context, url) })
        else -> {}
    }

    updateViewModel.announcements.collectAsState().value?.let { list ->
        list.getOrNull(currentAnnouncementIndex)?.let { announcement ->
            AnnouncementDialog(announcement = announcement,
                currentVersion = updateViewModel.currentVersion.collectAsState().value,
                onDismiss = {
                    currentAnnouncementIndex++
                    if (currentAnnouncementIndex >= list.size) {
                        updateViewModel.markAnnouncementAsRead(); updateViewModel.dismissAnnouncements(); currentAnnouncementIndex = 0
                    }
                }, onUrlClick = { safeOpenUrl(context, it) })
        }
    }
}

private fun safeOpenUrl(context: android.content.Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { AppLogger.e("Safety", "操作异常", e) }
}
