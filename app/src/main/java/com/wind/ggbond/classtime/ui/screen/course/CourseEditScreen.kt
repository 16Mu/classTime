package com.wind.ggbond.classtime.ui.screen.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.wind.ggbond.classtime.ui.screen.course.components.toHexString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.ui.screen.course.components.AdvancedSettingsSection
import com.wind.ggbond.classtime.ui.screen.course.components.BasicInfoSection
import com.wind.ggbond.classtime.ui.screen.course.components.WeekSelectorDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditScreen(
    navController: NavController,
    courseId: Long?,
    defaultDayOfWeek: Int? = null,
    defaultStartSection: Int? = null,
    defaultSectionCount: Int? = null,
    defaultWeekNumber: Int? = null,
    defaultCourseName: String? = null,
    viewModel: CourseEditViewModel = hiltViewModel()
) {
    LaunchedEffect(courseId) {
        if (courseId != null && courseId > 0) {
            viewModel.loadCourse(courseId)
        }
    }
    LaunchedEffect(courseId, defaultDayOfWeek, defaultStartSection, defaultSectionCount, defaultWeekNumber, defaultCourseName) {
        if (courseId == null) {
            viewModel.applyDefaultsIfNeeded(
                dayOfWeek = defaultDayOfWeek,
                startSection = defaultStartSection,
                sectionCount = defaultSectionCount,
                weekNumber = defaultWeekNumber,
                courseName = defaultCourseName
            )
        }
    }

    val isEdit = remember(courseId) { courseId != null && courseId > 0 }

    val courseName by viewModel.courseName.collectAsState()
    val teacher by viewModel.teacher.collectAsState()
    val classroom by viewModel.classroom.collectAsState()
    val dayOfWeek by viewModel.dayOfWeek.collectAsState()
    val startSection by viewModel.startSection.collectAsState()
    val sectionCount by viewModel.sectionCount.collectAsState()
    val selectedWeeks by viewModel.selectedWeeks.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val reminderMinutes by viewModel.reminderMinutes.collectAsState()
    val note by viewModel.note.collectAsState()
    val credit by viewModel.credit.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()

    val showWeekSelector by viewModel.showWeekSelector.collectAsState()
    val showColorPicker by viewModel.showColorPicker.collectAsState()

    val saveState by viewModel.saveState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is SaveState.Success -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Short
                )
                navController.navigate(com.wind.ggbond.classtime.ui.navigation.Screen.Main.createRoute(refresh = true)) {
                    popUpTo(com.wind.ggbond.classtime.ui.navigation.BottomNavItem.Schedule.route) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
            is SaveState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.resetSaveState()
            }
            SaveState.Idle, SaveState.Saving -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(if (isEdit) "编辑课程" else "新增课程") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.saveCourse()
                        },
                        enabled = saveState !is SaveState.Saving && courseName.isNotBlank(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        if (saveState is SaveState.Saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "确认",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (saveState is SaveState.Saving) "保存中..." else "保存")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            BasicInfoSection(
                courseName = courseName,
                onCourseNameChange = { viewModel.updateCourseName(it) },
                teacher = teacher,
                onTeacherChange = { viewModel.updateTeacher(it) },
                classroom = classroom,
                onClassroomChange = { viewModel.updateClassroom(it) },
                dayOfWeek = dayOfWeek,
                onDayOfWeekChange = { viewModel.updateDayOfWeek(it) },
                startSection = startSection,
                onStartSectionChange = { viewModel.updateStartSection(it) },
                sectionCount = sectionCount,
                onSectionCountChange = { viewModel.updateSectionCount(it) },
                selectedWeeks = selectedWeeks.toSet(),
                onShowWeekSelector = { viewModel.showWeekSelector() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            AdvancedSettingsSection(
                selectedColor = selectedColor,
                onColorChange = { viewModel.updateColor(it) },
                onShowColorPicker = { viewModel.showColorPicker() },
                reminderEnabled = reminderEnabled,
                onReminderEnabledChange = { viewModel.updateReminderEnabled(it) },
                reminderMinutes = reminderMinutes,
                onReminderMinutesChange = { viewModel.updateReminderMinutes(it) },
                credit = credit,
                onCreditChange = { viewModel.updateCredit(it) },
                note = note,
                onNoteChange = { viewModel.updateNote(it) }
            )
        }
    }

    if (showWeekSelector) {
        WeekSelectorDialog(
            selectedWeeks = selectedWeeks,
            maxWeeks = totalWeeks,
            onDismiss = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.hideWeekSelector()
            },
            onConfirm = { weeks ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.updateSelectedWeeks(weeks)
                viewModel.hideWeekSelector()
            }
        )
    }

    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { viewModel.hideColorPicker() },
            title = { Text("选择颜色") },
            text = {
                Column {
                    com.wind.ggbond.classtime.ui.theme.CourseColors.chunked(5).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            row.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(color)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.updateColor(toHexString(color))
                                            viewModel.hideColorPicker()
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.hideColorPicker()
                }) {
                    Text("关闭")
                }
            }
        )
    }
}
