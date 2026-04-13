package com.wind.ggbond.classtime.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import com.wind.ggbond.classtime.ui.theme.Spacing
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.ui.screen.course.CourseDetailViewModel
import com.wind.ggbond.classtime.ui.theme.CourseColors
import com.wind.ggbond.classtime.ui.theme.contentColorForBackground
import com.wind.ggbond.classtime.ui.theme.topGradientOverlayAlpha
import com.wind.ggbond.classtime.ui.theme.wallpaperAwareBackground
import com.wind.ggbond.classtime.ui.theme.DesktopTransparencyLevel
import com.wind.ggbond.classtime.util.DateUtils
import com.wind.ggbond.classtime.util.WeekParser
import kotlinx.coroutines.launch

private val EditTransitionSpec: ContentTransform = fadeIn(animationSpec = tween(300)) +
    expandVertically(animationSpec = tween(300)) togetherWith
    fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))

@Composable
private fun textFieldColors(primary: Color = MaterialTheme.colorScheme.primary) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
)

/**
 * 课程详情底部弹出卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailBottomSheet(
    courseId: Long,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit,
    onRequestAdjustment: ((Long) -> Unit)? = null,
    onRequestAddExam: ((Long) -> Unit)? = null,
    startInEditMode: Boolean = false,
    viewModel: CourseDetailViewModel = hiltViewModel(),
    dynamicColor: String? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(courseId) {
        viewModel.loadCourse(courseId)
    }
    
    val course by viewModel.course.collectAsState()
    val exams by viewModel.exams.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val showAdjustmentDialog by viewModel.showAdjustmentDialog.collectAsState()
    val showAddExamDialog by viewModel.showAddExamDialog.collectAsState()
    val currentSchedule by viewModel.currentSchedule.collectAsState()
    val currentWeekNumber by viewModel.currentWeekNumber.collectAsState()
    
    // 编辑模式状态 - 根据startInEditMode参数决定初始状态
    var isEditMode by remember { mutableStateOf(startInEditMode) }
    var validationError by remember { mutableStateOf<String?>(null) }
    
    // 编辑状态的临时数据 - 基本信息
    var editedCourseName by remember { mutableStateOf("") }
    var editedTeacher by remember { mutableStateOf("") }
    var editedClassroom by remember { mutableStateOf("") }
    var editedCourseCode by remember { mutableStateOf("") }
    
    // 编辑状态的临时数据 - 时间安排
    var editedDayOfWeek by remember { mutableStateOf(1) }
    var editedStartSection by remember { mutableStateOf(1) }
    var editedSectionCount by remember { mutableStateOf(2) }
    var editedWeeks by remember { mutableStateOf(listOf<Int>()) }
    
    // 编辑状态的临时数据 - 样式与附加
    var editedColor by remember { mutableStateOf("#42A5F5") }
    var editedCredit by remember { mutableStateOf(0f) }
    var editedNote by remember { mutableStateOf("") }
    
    // 编辑状态的临时数据 - 提醒
    var editedReminderEnabled by remember { mutableStateOf(true) }
    var editedReminderMinutes by remember { mutableStateOf(10) }
    
    var hasLoadedEditData by remember { mutableStateOf(false) }

    LaunchedEffect(isEditMode) {
        if (isEditMode && !hasLoadedEditData) {
            course?.let { c ->
                editedCourseName = c.courseName
                editedTeacher = c.teacher
                editedClassroom = c.classroom
                editedCourseCode = c.courseCode
                editedDayOfWeek = c.dayOfWeek
                editedStartSection = c.startSection
                editedSectionCount = c.sectionCount
                editedWeeks = c.weeks
                editedColor = c.color
                editedCredit = c.credit
                editedNote = c.note
                editedReminderEnabled = c.reminderEnabled
                editedReminderMinutes = c.reminderMinutes
            }
            hasLoadedEditData = true
        }
        if (!isEditMode) {
            hasLoadedEditData = false
        }
    }
    
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false  // 允许部分展开状态，默认显示一半高度
    )
    
    // 滚动状态 - 每次打开都重置到顶部
    val scrollState = rememberScrollState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(Spacing.md))
                Surface(
                    modifier = Modifier
                        .width(40.dp)
                        .height(Spacing.xs),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
        }
    ) {
        course?.let { c ->
            // 重置滚动位置到顶部
            LaunchedEffect(courseId) {
                scrollState.scrollTo(0)
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight() // 高度自适应内容，不强制填满
            ) {
                // 顶部课程卡片 - 固定在顶部，不滚动
                CourseHeaderCard(
                    course = c,
                    isEditMode = isEditMode,
                    dynamicColor = dynamicColor,
                    coroutineScope = coroutineScope,
                    onEdit = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // 切换到编辑模式而不是跳转页面
                        isEditMode = true
                    },
                    onDelete = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.showDeleteDialog()
                    },
                    onSave = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        
                        // 1. 全字段验证
                        validationError = when {
                            editedCourseName.isBlank() -> "课程名称不能为空"
                            editedDayOfWeek !in 1..7 -> "星期必须在1-7之间"
                            editedStartSection < 1 -> "开始节次必须大于等于1"
                            editedSectionCount < 1 -> "持续节数必须大于等于1"
                            editedStartSection > 14 -> "开始节次不能超过14"
                            (editedStartSection + editedSectionCount - 1) > 14 -> "结束节次不能超过14"
                            editedWeeks.isEmpty() -> "周次不能为空"
                            else -> null
                        }
                        
                        if (validationError != null) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(validationError!!)
                            }
                            return@CourseHeaderCard
                        }
                        
                        // 2. 全字段保存
                        val success = viewModel.updateCourse(
                            courseName = editedCourseName,
                            teacher = editedTeacher,
                            classroom = editedClassroom,
                            note = editedNote,
                            dayOfWeek = editedDayOfWeek,
                            startSection = editedStartSection,
                            sectionCount = editedSectionCount,
                            weeks = editedWeeks,
                            color = editedColor,
                            credit = editedCredit,
                            courseCode = editedCourseCode,
                            reminderEnabled = editedReminderEnabled,
                            reminderMinutes = editedReminderMinutes
                        )
                        
                        if (success) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("保存成功")
                            }
                            isEditMode = false
                            onDismiss()
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("保存失败，请检查输入")
                            }
                        }
                    },
                    onCancel = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isEditMode = false
                    }
                )
                
                Spacer(modifier = Modifier.height(Spacing.lg))

                // 可滚动的内容区域
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = Spacing.xl)
                        .padding(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    // 基本信息 - 支持编辑模式切换
                    AnimatedContent(
                        targetState = isEditMode,
                        label = "info_edit_animation",
                        transitionSpec = { EditTransitionSpec }
                    ) { editMode ->
                        if (editMode) {
                            // 编辑模式 - 全字段编辑
                            EditableInfoSection(
                                courseName = editedCourseName,
                                teacher = editedTeacher,
                                classroom = editedClassroom,
                                courseCode = editedCourseCode,
                                credit = editedCredit,
                                onCourseNameChange = { editedCourseName = it },
                                onTeacherChange = { editedTeacher = it },
                                onClassroomChange = { editedClassroom = it },
                                onCourseCodeChange = { editedCourseCode = it },
                                onCreditChange = { editedCredit = it }
                            )
                        } else {
                            // 查看模式 - 显示只读信息
                            InfoSection(title = "基本信息", icon = Icons.Default.Info) {
                                CourseInfoRow(
                                    icon = Icons.Default.Person,
                                    label = "教师",
                                    value = c.teacher.ifEmpty { "未设置" }
                                )
                                CourseInfoRow(
                                    icon = Icons.Default.Place,
                                    label = "教室",
                                    value = c.classroom.ifEmpty { "未设置" }
                                )
                                CourseInfoRow(
                                    icon = Icons.Default.Star,
                                    label = "学分",
                                    value = if (c.credit > 0) "${c.credit}" else "未设置"
                                )
                                // 课程代码（有值时显示）
                                if (c.courseCode.isNotEmpty()) {
                                    CourseInfoRow(
                                        icon = Icons.Default.Tag,
                                        label = "课程代码",
                                        value = c.courseCode
                                    )
                                }
                            }
                        }
                    }
                    
                    // 时间安排 - 支持编辑模式切换
                    AnimatedContent(
                        targetState = isEditMode,
                        label = "time_edit_animation",
                        transitionSpec = { EditTransitionSpec }
                    ) { editMode ->
                        if (editMode) {
                            // 编辑模式 - 时间安排编辑
                            EditableTimeSection(
                                dayOfWeek = editedDayOfWeek,
                                startSection = editedStartSection,
                                sectionCount = editedSectionCount,
                                weeks = editedWeeks,
                                onDayOfWeekChange = { editedDayOfWeek = it },
                                onStartSectionChange = { editedStartSection = it },
                                onSectionCountChange = { editedSectionCount = it },
                                onWeeksChange = { editedWeeks = it }
                            )
                        } else {
                            // 查看模式 - 显示只读信息
                            InfoSection(title = "时间安排", icon = Icons.Default.Schedule) {
                                CourseInfoRow(
                                    icon = Icons.Default.CalendarToday,
                                    label = "星期",
                                    value = DateUtils.getDayOfWeekName(c.dayOfWeek)
                                )
                                CourseInfoRow(
                                    icon = Icons.Default.Timer,
                                    label = "节次",
                                    value = "第${c.startSection}节 (共${c.sectionCount}节)"
                                )
                                CourseInfoRow(
                                    icon = Icons.Default.DateRange,
                                    label = "周次",
                                    value = if (c.weekExpression.isNotEmpty()) {
                                        c.weekExpression
                                    } else {
                                        WeekParser.formatWeekList(c.weeks)
                                    }
                                )
                            }
                        }
                    }
                    
                    // 颜色选择 - 仅编辑模式显示
                    if (isEditMode) {
                        EditableColorSection(
                            selectedColor = editedColor,
                            onColorChange = { editedColor = it }
                        )
                    }
                    
                    // 提醒设置 - 支持编辑模式切换
                    AnimatedContent(
                        targetState = isEditMode,
                        label = "reminder_edit_animation",
                        transitionSpec = { EditTransitionSpec }
                    ) { editMode ->
                        if (editMode) {
                            // 编辑模式 - 提醒设置可编辑
                            EditableReminderSection(
                                reminderEnabled = editedReminderEnabled,
                                reminderMinutes = editedReminderMinutes,
                                onReminderEnabledChange = { editedReminderEnabled = it },
                                onReminderMinutesChange = { editedReminderMinutes = it }
                            )
                        } else {
                            // 查看模式 - 显示只读提醒信息
                            ReminderSection(course = c)
                        }
                    }
                    
                    // 备注 - 支持编辑模式切换
                    AnimatedContent(
                        targetState = isEditMode,
                        label = "note_edit_animation",
                        transitionSpec = { EditTransitionSpec }
                    ) { editMode ->
                        if (editMode) {
                            // 编辑模式 - 备注可编辑
                            EditableNoteSection(
                                note = editedNote,
                                onNoteChange = { editedNote = it }
                            )
                        } else {
                            // 查看模式 - 显示只读备注
                            ReadOnlyNoteSection(note = c.note)
                        }
                    }
                    
                    // 快速操作卡片（仅查看模式显示）
                    if (!isEditMode) {
                        QuickActionsCard(
                            onAdjustment = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                // 优先使用上层回调（先关闭Sheet，由MainScreen弹Dialog）
                                if (onRequestAdjustment != null) {
                                    onDismiss()
                                    onRequestAdjustment(courseId)
                                } else {
                                    // 降级：在Sheet内部弹Dialog（向后兼容）
                                    viewModel.showAdjustmentDialog()
                                }
                            },
                            onAddExam = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                // 优先使用上层回调（先关闭Sheet，由MainScreen弹Dialog）
                                if (onRequestAddExam != null) {
                                    onDismiss()
                                    onRequestAddExam(courseId)
                                } else {
                                    // 降级：在Sheet内部弹Dialog（向后兼容）
                                    viewModel.showAddExamDialog()
                                }
                            }
                        )
                    }
                    
                    // 考试信息（如果有，仅查看模式显示）
                    if (!isEditMode && exams.isNotEmpty()) {
                        ExamSection(
                            exams = exams, 
                            onDeleteExam = { examId ->
                                viewModel.deleteExam(examId)
                            },
                            onDismiss = onDismiss
                        )
                    }
                }
            }
        } ?: run {
            // 加载中
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("删除课程") },
            text = { Text("确定要删除这门课程吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCourse()
                    onDelete(courseId)
                    onDismiss()  // ✅ 修复：删除课程后自动关闭底部卡片
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 临时调课对话框
    if (showAdjustmentDialog && course != null && currentSchedule != null) {
        val adjustmentViewModel: com.wind.ggbond.classtime.ui.screen.course.CourseAdjustmentViewModel = hiltViewModel()
        
        LaunchedEffect(Unit) {
            adjustmentViewModel.loadCourse(courseId, currentWeekNumber)
        }
        
        val adjustmentSaveState by adjustmentViewModel.saveState.collectAsState()
        
        LaunchedEffect(adjustmentSaveState) {
            when (val state = adjustmentSaveState) {
                is com.wind.ggbond.classtime.ui.screen.course.CourseAdjustmentViewModel.SaveState.Success -> {
                    snackbarHostState.showSnackbar(state.message)
                    viewModel.hideAdjustmentDialog()
                    adjustmentViewModel.resetSaveState()
                    onDismiss()
                }
                is com.wind.ggbond.classtime.ui.screen.course.CourseAdjustmentViewModel.SaveState.Error -> {
                    snackbarHostState.showSnackbar(state.message)
                    adjustmentViewModel.resetSaveState()
                }
                else -> {}
            }
        }
        
        com.wind.ggbond.classtime.ui.screen.course.components.CourseAdjustmentDialog(
            course = course!!,
            currentWeekNumber = adjustmentViewModel.originalWeekNumber.collectAsState().value,
            totalWeeks = currentSchedule!!.totalWeeks,
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
            onDismiss = { viewModel.hideAdjustmentDialog() }
        )
    }
    
    // 添加考试对话框
    if (showAddExamDialog && course != null && currentSchedule != null) {
        com.wind.ggbond.classtime.ui.screen.course.components.AddExamDialog(
            course = course!!,
            currentWeek = currentWeekNumber,
            totalWeeks = currentSchedule!!.totalWeeks,
            onDismiss = { viewModel.hideAddExamDialog() },
            onConfirm = { exam ->
                viewModel.saveExam(exam)
                coroutineScope.launch { snackbarHostState.showSnackbar("考试已添加") }
                viewModel.hideAddExamDialog()
            }
        )
    }
}

/**
 * 课程头部卡片 - 支持编辑/查看模式切换
 * 支持莫奈动态取色：通过 dynamicColor 参数传入外部动态颜色
 */
@Composable
private fun CourseHeaderCard(
    course: Course,
    isEditMode: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    dynamicColor: String? = null,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
) {
    // 优先使用动态颜色，否则回退到课程原始颜色
    val displayColor = dynamicColor ?: course.color
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(140.dp)
            .clip(MaterialTheme.shapes.large)
            .wallpaperAwareBackground(
                parseDisplayColor(displayColor),
                desktopLevel = DesktopTransparencyLevel.OPAQUE
            )
    ) {
        val bg = parseDisplayColor(displayColor)
        val contentColor = contentColorForBackground(bg)
        val overlayAlpha = topGradientOverlayAlpha(bg)
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = overlayAlpha * 0.3f)
                        )
                    )
                )
        ) {
            // 顶部操作按钮 - 根据模式切换
            AnimatedContent(
                targetState = isEditMode,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.md),
                label = "header_buttons_animation"
            ) { editMode ->
                if (editMode) {
                    // 编辑模式 - 显示保存和取消按钮
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilledIconButton(
                            onClick = onCancel,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = contentColor.copy(alpha = 0.2f),
                                contentColor = contentColor
                            )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "取消",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        FilledIconButton(
                            onClick = onSave,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = contentColor.copy(alpha = 0.3f),
                                contentColor = contentColor
                            )
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "保存",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    // 查看模式 - 显示编辑和删除按钮
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilledIconButton(
                            onClick = onEdit,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = contentColor.copy(alpha = 0.2f),
                                contentColor = contentColor
                            )
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "编辑",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        FilledIconButton(
                            onClick = onDelete,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = contentColor.copy(alpha = 0.2f),
                                contentColor = contentColor
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            
            // 课程名称
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 图标
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.2f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = "课程",
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(Spacing.md))
                
                Text(
                    text = course.courseName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

/**
 * 可编辑时间安排区块 - 用于编辑模式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableTimeSection(
    dayOfWeek: Int,
    startSection: Int,
    sectionCount: Int,
    weeks: List<Int>,
    onDayOfWeekChange: (Int) -> Unit,
    onStartSectionChange: (Int) -> Unit,
    onSectionCountChange: (Int) -> Unit,
    onWeeksChange: (List<Int>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "时间安排（编辑中）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Spacing.lg),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // 星期选择
                var expandedDay by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedDay,
                    onExpandedChange = { expandedDay = it }
                ) {
                    OutlinedTextField(
                        value = DateUtils.getDayOfWeekName(dayOfWeek),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("星期") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CalendarToday,
                                null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDay) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(Spacing.md),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
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
                                    onDayOfWeekChange(day)
                                    expandedDay = false
                                }
                            )
                        }
                    }
                }
                
                // 开始节次和持续节数（使用字符串状态管理，避免删除键光标跳动）
                var startSectionText by remember(startSection) { mutableStateOf(startSection.toString()) }
                var sectionCountText by remember(sectionCount) { mutableStateOf(sectionCount.toString()) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // 开始节次
                    OutlinedTextField(
                        value = startSectionText,
                        onValueChange = { newValue ->
                            startSectionText = newValue
                            newValue.toIntOrNull()?.let { num -> onStartSectionChange(num) }
                        },
                        label = { Text("开始节次") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.PlayArrow,
                                null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                    
                    // 持续节数
                    OutlinedTextField(
                        value = sectionCountText,
                        onValueChange = { newValue ->
                            sectionCountText = newValue
                            newValue.toIntOrNull()?.let { num -> onSectionCountChange(num) }
                        },
                        label = { Text("持续节数") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Timer,
                                null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                }
                
                // 周次选择
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.xs),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                
                var showWeekPicker by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWeekPicker = true }
                        .padding(vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "周次",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = if (weeks.isEmpty()) "未设置" else WeekParser.formatWeekList(weeks),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑周次",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                if (showWeekPicker) {
                    com.wind.ggbond.classtime.ui.screen.course.components.WeekSelectorDialog(
                        selectedWeeks = weeks,
                        onConfirm = { newWeeks ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onWeeksChange(newWeeks)
                            showWeekPicker = false
                        },
                        onDismiss = { showWeekPicker = false }
                    )
                }
            }
        }
    }
}

/**
 * 可编辑信息区块 - 全字段编辑模式
 */
@Composable
private fun EditableInfoSection(
    courseName: String,
    teacher: String,
    classroom: String,
    courseCode: String,
    credit: Float,
    onCourseNameChange: (String) -> Unit,
    onTeacherChange: (String) -> Unit,
    onClassroomChange: (String) -> Unit,
    onCourseCodeChange: (String) -> Unit,
    onCreditChange: (Float) -> Unit
) {
    // 学分使用字符串状态管理，避免删除键光标跳动
    var creditText by remember(credit) {
        mutableStateOf(if (credit > 0f) credit.toString() else "")
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "基本信息（编辑中）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Spacing.lg),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // 课程名称输入框
                OutlinedTextField(
                    value = courseName,
                    onValueChange = onCourseNameChange,
                    label = { Text("课程名称 *") },
                    placeholder = { Text("例如：高等数学") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Book,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 2,
                    shape = RoundedCornerShape(Spacing.md),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
                
                // 教师和教室并排
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // 教师输入框
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = onTeacherChange,
                        label = { Text("教师") },
                        placeholder = { Text("张老师") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    shape = RoundedCornerShape(Spacing.md),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                    
                    // 教室输入框
                    OutlinedTextField(
                        value = classroom,
                        onValueChange = onClassroomChange,
                        label = { Text("教室") },
                        placeholder = { Text("A101") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Place,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                }
                
                // 学分和课程代码并排
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 学分输入框
                    OutlinedTextField(
                        value = creditText,
                        onValueChange = { newValue ->
                            creditText = newValue
                            newValue.toFloatOrNull()?.let { num -> onCreditChange(num) }
                        },
                        label = { Text("学分") },
                        placeholder = { Text("3.0") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Star,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                    
                    // 课程代码输入框
                    OutlinedTextField(
                        value = courseCode,
                        onValueChange = onCourseCodeChange,
                        label = { Text("课程代码") },
                        placeholder = { Text("CS101") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Tag,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
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

/**
 * 信息区块
 */
@Composable
private fun InfoSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * 信息行
 */
@Composable
private fun CourseInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 快速操作卡片
 */
@Composable
private fun QuickActionsCard(
    onAdjustment: () -> Unit,
    onAddExam: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 临时调课
        Card(
            onClick = onAdjustment,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "临时调课",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // 添加考试
        Card(
            onClick = onAddExam,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "添加考试",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 考试区块
 */
@Composable
private fun ExamSection(
    exams: List<com.wind.ggbond.classtime.data.local.entity.Exam>,
    onDeleteExam: (Long) -> Unit,
    onDismiss: () -> Unit = {}  // ✅ 添加 onDismiss 参数
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "考试安排",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        exams.forEach { exam ->
            ExamInfoCard(exam = exam, onDelete = { onDeleteExam(exam.id) }, onDismiss = onDismiss)
        }
    }
}

/**
 * 考试信息卡片
 */
@Composable
private fun ExamInfoCard(
    exam: com.wind.ggbond.classtime.data.local.entity.Exam,
    onDelete: () -> Unit,
    onDismiss: () -> Unit = {}  // ✅ 添加 onDismiss 参数
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = exam.examType,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = exam.getTimeDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (exam.location.isNotEmpty()) {
                    Text(
                        text = exam.location + if (exam.seat.isNotEmpty()) " (${exam.seat})" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除考试") },
            text = { Text("确定要删除这场考试吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                    onDismiss()  // ✅ 修复：删除考试后自动关闭底部卡片
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 提醒设置区块
 */
@Composable
private fun ReminderSection(course: Course) {
    InfoSection(title = "提醒设置", icon = Icons.Default.Notifications) {
        CourseInfoRow(
            icon = if (course.reminderEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
            label = "状态",
            value = if (course.reminderEnabled) "已开启" else "已关闭"
        )
        if (course.reminderEnabled) {
            CourseInfoRow(
                icon = Icons.Default.Timer,
                label = "提前提醒",
                value = "${course.reminderMinutes}分钟"
            )
        }
    }
}

/**
 * 可编辑颜色选择区块 - 仅编辑模式显示
 */
@Composable
private fun EditableColorSection(
    selectedColor: String,
    onColorChange: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // 更多颜色弹窗状态
    var showColorPicker by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "课程颜色",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 快速颜色选择（6个常用颜色）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CourseColors.take(6).forEach { color ->
                        val hexStr = toHexString(color)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color)
                                .border(
                                    width = if (selectedColor == hexStr) 3.dp else 0.dp,
                                    color = if (selectedColor == hexStr)
                                        MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onColorChange(hexStr)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == hexStr) {
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
    }
    
    // 全部颜色选择器对话框
    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("选择颜色") },
            text = {
                Column {
                    CourseColors.chunked(5).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            row.forEach { color ->
                                val hexStr = toHexString(color)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selectedColor == hexStr) 3.dp else 0.dp,
                                            color = if (selectedColor == hexStr)
                                                MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onColorChange(hexStr)
                                            showColorPicker = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedColor == hexStr) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选中",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

/**
 * 可编辑提醒设置区块 - 编辑模式
 */
@Composable
private fun EditableReminderSection(
    reminderEnabled: Boolean,
    reminderMinutes: Int,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderMinutesChange: (Int) -> Unit
) {
    // 提醒分钟数使用字符串状态管理
    var minutesText by remember(reminderMinutes) { mutableStateOf(reminderMinutes.toString()) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "提醒设置（编辑中）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 提醒开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "上课提醒",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = onReminderEnabledChange,
                        modifier = Modifier.semantics {
                            stateDescription = if (reminderEnabled) "已开启" else "已关闭"
                        }
                    )
                }
                
                // 提前提醒时间（仅在开启时显示）
                AnimatedVisibility(visible = reminderEnabled) {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { newValue ->
                            minutesText = newValue
                            newValue.toIntOrNull()?.let { num ->
                                onReminderMinutesChange(num.coerceAtLeast(0))
                            }
                        },
                        label = { Text("提前提醒") },
                        suffix = { Text("分钟") },
                        placeholder = { Text("15") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Timer,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
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

/**
 * 可编辑备注区块 - 编辑模式
 */
@Composable
private fun EditableNoteSection(
    note: String,
    onNoteChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "备注（编辑中）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                placeholder = { Text("添加课程备注，如作业要求、考试范围等") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
        }
    }
}

/**
 * 只读备注区块 - 查看模式
 */
@Composable
private fun ReadOnlyNoteSection(note: String) {
    // 无内容时不显示
    if (note.isEmpty()) return
    
    InfoSection(title = "备注", icon = Icons.AutoMirrored.Filled.Notes) {
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 颜色值转16进制字符串
 */
private fun toHexString(color: Color): String {
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return "#%02X%02X%02X".format(red, green, blue)
}

@Composable
private fun parseDisplayColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    MaterialTheme.colorScheme.primaryContainer
}

